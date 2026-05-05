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

private val Context.phraseDataStore: DataStore<Preferences> by preferencesDataStore(name = "common_phrases")

@Serializable
data class CommonPhrase(
    val id: String,
    val text: String,
)

class CommonPhrasesRepository(context: Context) {

    private val dataStore = context.phraseDataStore

    val phrases: Flow<List<CommonPhrase>> = dataStore.data.map { prefs ->
        val json = prefs[PhrasePrefsKeys.PHRASES_JSON] ?: ""
        if (json.isEmpty()) emptyList()
        else Json.decodeFromString<List<CommonPhrase>>(json)
    }

    suspend fun addPhrase(text: String) {
        val current = phrases.first()
        val newId = System.currentTimeMillis().toString()
        val updated = listOf(CommonPhrase(newId, text)) + current
        savePhrases(updated)
    }

    suspend fun movePhraseToFront(id: String) {
        val current = phrases.first()
        val index = current.indexOfFirst { it.id == id }
        if (index <= 0) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(0, item)
        savePhrases(mutable)
    }

    suspend fun updatePhrase(id: String, newText: String) {
        val current = phrases.first()
        val updated = current.map { if (it.id == id) it.copy(text = newText) else it }
        savePhrases(updated)
    }

    suspend fun deletePhrase(id: String) {
        val current = phrases.first()
        savePhrases(current.filter { it.id != id })
    }

    suspend fun reorderPhrase(fromIndex: Int, toIndex: Int) {
        val current = phrases.first()
        if (fromIndex < 0 || fromIndex >= current.size || toIndex < 0 || toIndex >= current.size) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        savePhrases(mutable)
    }

    private suspend fun savePhrases(phrases: List<CommonPhrase>) {
        dataStore.edit { prefs ->
            prefs[PhrasePrefsKeys.PHRASES_JSON] = Json.encodeToString(phrases)
        }
    }

    companion object {
        private val DEFAULT_PHRASES = listOf(
            CommonPhrase("1", "你好"),
            CommonPhrase("2", "谢谢"),
            CommonPhrase("3", "再见"),
            CommonPhrase("4", "请稍等"),
            CommonPhrase("5", "请再说一遍"),
            CommonPhrase("6", "我听不清"),
            CommonPhrase("7", "明白了"),
            CommonPhrase("8", "需要帮忙吗"),
        )

        suspend fun initialize(context: Context, repository: CommonPhrasesRepository) {
            val existing = repository.phrases.first()
            if (existing.isEmpty()) {
                repository.savePhrases(DEFAULT_PHRASES)
            }
        }
    }
}

private object PhrasePrefsKeys {
    val PHRASES_JSON = stringPreferencesKey("phrases_json")
}
