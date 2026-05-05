package com.myyinshu.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myyinshu.voice.VoiceRecognitionEngine

/**
 * Holds communication screen state outside Compose lifecycle.
 * Survives navigation (settings -> communication) by living in Activity scope.
 */
class CommunicationState {

    private val _textSegments = mutableStateListOf<String>()
    val textSegments: List<String> get() = _textSegments

    private val _partialText = mutableStateOf<String?>(null)
    var partialText: String?
        get() = _partialText.value
        set(value) { _partialText.value = value }

    private val _isListening = mutableStateOf(false)
    var isListening: Boolean
        get() = _isListening.value
        set(value) { _isListening.value = value }

    private val _modelReady = mutableStateOf(false)
    var modelReady: Boolean
        get() = _modelReady.value
        set(value) { _modelReady.value = value }

    private val _modelProgress = mutableStateOf("正在加载模型...")
    var modelProgress: String
        get() = _modelProgress.value
        set(value) { _modelProgress.value = value }

    private val _errorMessage = mutableStateOf<String?>(null)
    var errorMessage: String?
        get() = _errorMessage.value
        set(value) { _errorMessage.value = value }

    private var attachedEngine: VoiceRecognitionEngine? = null
    private var pendingHotWords: List<String> = emptyList()

    /** Register callbacks on the given engine. Call when Composable enters composition. */
    fun attach(engine: VoiceRecognitionEngine) {
        attachedEngine = engine
        // Reset state for new engine
        modelReady = false
        modelProgress = "正在加载模型..."
        errorMessage = null

        engine.onResult = { text ->
            _textSegments.add(text)
            partialText = null
        }
        engine.onPartialResult = { text -> partialText = text }
        engine.onError = { err -> errorMessage = err }
        engine.onStateChanged = { listening -> isListening = listening }
        engine.onProgress = { msg -> modelProgress = msg }

        // Don't call prepareModel on an engine that's already being prepared
        // Check isModelReady AFTER setting up callbacks, to avoid missing the ready callback
        if (engine.isModelReady) {
            modelReady = true
            // Apply pending hot words immediately if model is already ready
            applyHotWordsToEngine()
        } else {
            engine.prepareModel(
                onReady = {
                    // Only set modelReady if this engine is still attached
                    if (attachedEngine === engine) {
                        modelReady = true
                        // Apply hot words now that model is ready
                        applyHotWordsToEngine()
                    }
                },
                onError = { msg ->
                    if (attachedEngine === engine) {
                        errorMessage = msg
                    }
                },
            )
        }
    }

    /** Queue hot words to be applied. If model is already ready, apply immediately. */
    fun setHotWords(words: List<String>) {
        pendingHotWords = words
        if (modelReady) {
            applyHotWordsToEngine()
        }
    }

    private fun applyHotWordsToEngine() {
        try {
            attachedEngine?.setHotWords(pendingHotWords)
        } catch (e: Exception) {
            // Ignore - hot words are a best-effort feature
        }
    }

    /** Clear engine callbacks. Call when Composable leaves composition. */
    fun detach() {
        attachedEngine?.let { engine ->
            engine.onResult = {}
            engine.onPartialResult = {}
            engine.onError = {}
            engine.onStateChanged = {}
            engine.onProgress = {}
        }
        attachedEngine = null
    }

    fun addText(text: String) {
        _textSegments.add(text)
    }

    fun clear() {
        _textSegments.clear()
        partialText = null
        errorMessage = null
    }
}
