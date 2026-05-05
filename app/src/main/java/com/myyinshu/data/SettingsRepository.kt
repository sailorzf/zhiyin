package com.myyinshu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val fontSize: FontSize = FontSize.EXTRA_LARGE,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val background: AppBackground = AppBackground.WHITE,
    val language: String = "mandarin",
    val engineType: String = "xunfei",
    val hybridMode: String = "auto",
    val dayStartHour: Int = 7,
    val dayEndHour: Int = 19,
    val xunfeiAppId: String = "4e4c607f",
    val xunfeiApiKey: String = "40272feeb32aea60f6c2a25da89cbaaf",
    val xunfeiApiSecret: String = "NmM2YjNhZjc5YzY2ZWJhYjFiYzAyZDBh",
)

enum class FontSize(val label: String, val value: Float) {
    LARGE("大", 40f),
    EXTRA_LARGE("特大", 52f),
    SUPER_LARGE("超大", 64f);
}

enum class ThemeMode(val label: String) {
    AUTO("自动"),
    DAY("白天"),
    NIGHT("夜间");
}

enum class AppBackground(val label: String) {
    WHITE("白底黑字"),
    BLACK("黑底白字"),
    YELLOW("黄底黑字"),
    DARK_BLUE("深蓝底白字");
}

class SettingsRepository(context: Context) {

    private val dataStore = context.dataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            fontSize = prefs[PreferencesKeys.FONT_SIZE]?.let { FontSize.valueOf(it) } ?: FontSize.EXTRA_LARGE,
            themeMode = prefs[PreferencesKeys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.AUTO,
            background = prefs[PreferencesKeys.BACKGROUND]?.let { AppBackground.valueOf(it) } ?: AppBackground.WHITE,
            language = prefs[PreferencesKeys.LANGUAGE] ?: "mandarin",
            engineType = prefs[PreferencesKeys.ENGINE_TYPE] ?: "xunfei",
            hybridMode = prefs[PreferencesKeys.HYBRID_MODE] ?: "auto",
            dayStartHour = prefs[PreferencesKeys.DAY_START_HOUR] ?: 7,
            dayEndHour = prefs[PreferencesKeys.DAY_END_HOUR] ?: 19,
            xunfeiAppId = prefs[PreferencesKeys.XUNFEI_APP_ID] ?: "4e4c607f",
            xunfeiApiKey = prefs[PreferencesKeys.XUNFEI_API_KEY] ?: "40272feeb32aea60f6c2a25da89cbaaf",
            xunfeiApiSecret = prefs[PreferencesKeys.XUNFEI_API_SECRET] ?: "NmM2YjNhZjc5YzY2ZWJhYjFiYzAyZDBh",
        )
    }

    suspend fun setFontSize(size: FontSize) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.FONT_SIZE] = size.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun setBackground(bg: AppBackground) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.BACKGROUND] = bg.name }
    }

    suspend fun setLanguage(lang: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.LANGUAGE] = lang }
    }

    suspend fun setEngineType(type: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.ENGINE_TYPE] = type }
    }

    suspend fun setHybridMode(mode: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.HYBRID_MODE] = mode }
    }

    suspend fun setDayHours(start: Int, end: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.DAY_START_HOUR] = start
            prefs[PreferencesKeys.DAY_END_HOUR] = end
        }
    }

    suspend fun setXunfeiConfig(appId: String, apiKey: String, apiSecret: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.XUNFEI_APP_ID] = appId
            prefs[PreferencesKeys.XUNFEI_API_KEY] = apiKey
            prefs[PreferencesKeys.XUNFEI_API_SECRET] = apiSecret
        }
    }
}

private object PreferencesKeys {
    val FONT_SIZE = stringPreferencesKey("font_size")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val BACKGROUND = stringPreferencesKey("background")
    val LANGUAGE = stringPreferencesKey("language")
    val ENGINE_TYPE = stringPreferencesKey("engine_type")
    val HYBRID_MODE = stringPreferencesKey("hybrid_mode")
    val DAY_START_HOUR = intPreferencesKey("day_start_hour")
    val DAY_END_HOUR = intPreferencesKey("day_end_hour")
    val XUNFEI_APP_ID = stringPreferencesKey("xunfei_app_id")
    val XUNFEI_API_KEY = stringPreferencesKey("xunfei_api_key")
    val XUNFEI_API_SECRET = stringPreferencesKey("xunfei_api_secret")
}
