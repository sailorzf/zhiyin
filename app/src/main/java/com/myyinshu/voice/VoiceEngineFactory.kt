package com.myyinshu.voice

import android.content.Context

enum class VoiceEngineType(val code: String, val label: String) {
    VOSK("vosk", "Vosk 离线引擎"),
    XUNFEI("xunfei", "讯飞离线引擎");

    companion object {
        fun fromCode(code: String): VoiceEngineType =
            entries.find { it.code == code } ?: XUNFEI
    }
}

enum class HybridMode(val code: String, val label: String) {
    AUTO("auto", "自动"),
    ONLINE("online", "在线"),
    OFFLINE("offline", "离线");

    companion object {
        fun fromCode(code: String): HybridMode =
            entries.find { it.code == code } ?: AUTO
    }
}

object VoiceEngineFactory {
    fun createEngine(
        type: VoiceEngineType,
        context: Context,
        appId: String = "4e4c607f",
    ): VoiceRecognitionEngine {
        return when (type) {
            VoiceEngineType.VOSK -> VoskVoiceEngine(context)
            VoiceEngineType.XUNFEI -> XunfeiVoiceEngine(context, appId)
        }
    }

    fun createHybridEngine(
        offlineEngineType: VoiceEngineType,
        context: Context,
        appId: String,
        apiKey: String,
        apiSecret: String,
    ): HybridVoiceEngine {
        return HybridVoiceEngine(context, offlineEngineType, appId, apiKey, apiSecret)
    }
}
