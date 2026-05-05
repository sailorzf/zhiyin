package com.myyinshu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.hotWordsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hot_words")

@Serializable
data class HotWord(
    val id: String,
    val word: String,
)

class HotWordsRepository(context: Context) {

    private val dataStore = context.hotWordsDataStore

    val hotWords: Flow<List<HotWord>> = dataStore.data.map { prefs ->
        val json = prefs[HotWordsPrefsKeys.WORDS_JSON] ?: ""
        if (json.isEmpty()) emptyList()
        else Json.decodeFromString<List<HotWord>>(json)
    }

    suspend fun addWord(word: String) {
        val current = hotWords.first()
        // Prevent duplicates
        if (current.any { it.word.equals(word, ignoreCase = true) }) return
        val newId = System.currentTimeMillis().toString()
        saveWords(current + HotWord(newId, word))
    }

    suspend fun updateWord(id: String, newWord: String) {
        val current = hotWords.first()
        saveWords(current.map { if (it.id == id) it.copy(word = newWord) else it })
    }

    suspend fun deleteWord(id: String) {
        val current = hotWords.first()
        saveWords(current.filter { it.id != id })
    }

    suspend fun reorderWord(fromIndex: Int, toIndex: Int) {
        val current = hotWords.first()
        if (fromIndex < 0 || fromIndex >= current.size || toIndex < 0 || toIndex >= current.size) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        saveWords(mutable)
    }

    suspend fun getAllWords(): List<String> {
        return hotWords.first().map { it.word }
    }

    private suspend fun saveWords(words: List<HotWord>) {
        dataStore.edit { prefs ->
            prefs[HotWordsPrefsKeys.WORDS_JSON] = Json.encodeToString(words)
        }
    }

    companion object {
        private val DEFAULT_WORDS = listOf(
            HotWord("1", "你好"),
            HotWord("2", "谢谢"),
            HotWord("3", "再见"),
            HotWord("4", "请稍等"),
            HotWord("5", "请再说一遍"),
            HotWord("6", "我听不清"),
            HotWord("7", "明白了"),
            HotWord("8", "需要帮忙吗"),
        )

        suspend fun initialize(context: Context, repository: HotWordsRepository) {
            val existing = repository.hotWords.first()
            if (existing.isEmpty()) {
                repository.saveWords(DEFAULT_WORDS)
            }
        }
    }
}

private object HotWordsPrefsKeys {
    val WORDS_JSON = stringPreferencesKey("hot_words_json")
}
