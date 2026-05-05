package com.myyinshu.voice

interface VoiceRecognitionEngine {
    var onResult: (String) -> Unit
    var onPartialResult: (String) -> Unit
    var onError: (String) -> Unit
    var onStateChanged: (Boolean) -> Unit
    var onProgress: (String) -> Unit

    val isListening: Boolean
    val isModelReady: Boolean

    fun startListening()
    fun stopListening()
    fun shutdown()
    fun prepareModel(onReady: () -> Unit, onError: (String) -> Unit)
    fun setHotWords(words: List<String>) {
        // Default: no-op. Override in engine implementation if hot words are supported.
    }
}
