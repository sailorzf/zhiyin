package com.myyinshu.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Hybrid voice engine that wraps both online and offline engines.
 * Supports AUTO (prefer online, fall back to offline), ONLINE, and OFFLINE modes.
 */
class HybridVoiceEngine(
    private val context: Context,
    private val offlineEngineType: VoiceEngineType,
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
) : VoiceRecognitionEngine {

    companion object {
        private const val TAG = "HybridEngine"
    }

    private val networkMonitor = NetworkMonitor(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineLock = Any()

    @Volatile
    var hybridMode: HybridMode = HybridMode.AUTO

    private val offlineEngine: VoiceRecognitionEngine by lazy {
        VoiceEngineFactory.createEngine(offlineEngineType, context, appId)
    }

    private var onlineEngine: XunfeiOnlineEngine? = null
    private var activeEngine: VoiceRecognitionEngine? = null

    // Track user's intent to listen (separate from engine's actual isListening)
    private var _userListening = false

    // Callback passthrough
    override var onResult: (String) -> Unit = {}
    override var onPartialResult: (String) -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onStateChanged: (Boolean) -> Unit = {}
    override var onProgress: (String) -> Unit = {}

    @Volatile
    override var isListening: Boolean = false
        private set

    override val isModelReady: Boolean
        get() {
            synchronized(engineLock) {
                return activeEngine?.isModelReady == true
            }
        }

    private var _currentMode: String = ""
    private var _switchingEngine = false
    private var _switchedToOffline = false

    init {
        networkMonitor.onNetworkChanged = { isOnline ->
            Log.d(TAG, "Network changed: online=$isOnline, mode=$hybridMode, userListening=$_userListening, switchedToOffline=$_switchedToOffline")
            if (hybridMode == HybridMode.AUTO && !_switchingEngine && _userListening) {
                if (!isOnline) {
                    Log.d(TAG, "Network lost during listening, switching to offline")
                    switchToOfflineDuringListening()
                } else if (_switchedToOffline) {
                    Log.d(TAG, "Network restored during listening, switching back to online")
                    switchToOnlineDuringListening()
                }
            }
        }
    }

    private fun getActiveEngine(): VoiceRecognitionEngine? {
        return when (hybridMode) {
            HybridMode.AUTO -> {
                if (networkMonitor.isOnline) onlineEngine else offlineEngine
            }
            HybridMode.ONLINE -> onlineEngine
            HybridMode.OFFLINE -> offlineEngine
        }
    }

    private fun getModeLabel(): String {
        return when (hybridMode) {
            HybridMode.AUTO -> if (networkMonitor.isOnline) "在线识别" else "离线识别"
            HybridMode.ONLINE -> "在线识别"
            HybridMode.OFFLINE -> "离线识别"
        }
    }

    override fun prepareModel(onReady: () -> Unit, onError: (String) -> Unit) {
        // Initialize online engine (no model to load, just instantiate)
        onlineEngine = XunfeiOnlineEngine(context, appId, apiKey, apiSecret)

        // Initialize offline engine
        offlineEngine.prepareModel(
            onReady = {
                activeEngine = getActiveEngine()
                mainHandler.post {
                    onProgress(getModeLabel())
                    onReady()
                }
            },
            onError = { msg ->
                // If offline engine fails, still allow online
                activeEngine = getActiveEngine()
                if (activeEngine == offlineEngine && activeEngine == null) {
                    mainHandler.post { onError("引擎初始化失败: $msg") }
                } else {
                    mainHandler.post {
                        onProgress("离线引擎不可用，使用在线模式")
                        onReady()
                    }
                }
            },
        )
    }

    override fun startListening() {
        _userListening = true
        synchronized(engineLock) {
            val engine = getActiveEngine()
            if (engine == null) {
                _userListening = false
                mainHandler.post { onError("无可用识别引擎") }
                return
            }

            if (hybridMode == HybridMode.ONLINE && !networkMonitor.isOnline) {
                _userListening = false
                mainHandler.post { onError("当前为在线模式，但网络不可用") }
                return
            }

            activeEngine = engine

            // Set callbacks
            engine.onResult = { text -> mainHandler.post { onResult(text) } }
            engine.onPartialResult = { text -> mainHandler.post { onPartialResult(text) } }
            engine.onError = { err ->
                if (hybridMode == HybridMode.AUTO && !networkMonitor.isOnline && _userListening && !_switchingEngine) {
                    Log.d(TAG, "Online engine error with no network, switching to offline: $err")
                    switchToOfflineDuringListening()
                } else {
                    mainHandler.post { onError(err) }
                }
            }
            engine.onStateChanged = { listening ->
                isListening = listening
                mainHandler.post { onStateChanged(listening) }
            }
            engine.onProgress = { msg ->
                mainHandler.post { onProgress("${getModeLabel()} - $msg") }
            }

            // Hook up connection loss detection for the online engine
            if (hybridMode == HybridMode.AUTO && _userListening) {
                onlineEngine?.onConnectionLost = {
                    Log.d(TAG, "Online engine connection lost, switching to offline")
                    switchToOfflineDuringListening()
                }
            }

            mainHandler.post { onProgress(getModeLabel()) }
            engine.startListening()
        }
    }

    override fun stopListening() {
        _userListening = false
        _switchedToOffline = false
        // Clear connection lost callback to prevent late firing
        onlineEngine?.onConnectionLost = {}
        synchronized(engineLock) {
            activeEngine?.stopListening()
        }
        isListening = false
    }

    override fun shutdown() {
        _userListening = false
        synchronized(engineLock) {
            isListening = false
            onlineEngine?.shutdown()
            onlineEngine = null
            offlineEngine.shutdown()
            activeEngine = null
        }
        networkMonitor.unregister()
    }

    override fun setHotWords(words: List<String>) {
        synchronized(engineLock) {
            offlineEngine.setHotWords(words)
            onlineEngine?.setHotWords(words)
        }
    }

    private fun switchToOfflineDuringListening() {
        if (_switchingEngine) return
        _switchingEngine = true

        Log.d(TAG, "Switching to offline...")

        // Fully suppress the online engine — prevent reconnects and errors
        try {
            onlineEngine?.let {
                it.onError = {}
                it.onStateChanged = {}
                it.onResult = {}
                it.onPartialResult = {}
                it.onConnectionLost = {}
                it.stopListening()
            }
        } catch (_: Exception) {
        }

        // Switch to offline
        activeEngine = offlineEngine

        // Re-set callbacks and restart
        offlineEngine.onResult = { text -> mainHandler.post { onResult(text) } }
        offlineEngine.onPartialResult = { text -> mainHandler.post { onPartialResult(text) } }
        offlineEngine.onError = { err -> mainHandler.post { onError(err) } }
        offlineEngine.onStateChanged = { listening ->
            isListening = listening
            mainHandler.post { onStateChanged(listening) }
        }
        offlineEngine.onProgress = { msg ->
            mainHandler.post { onProgress("离线识别 - $msg") }
        }

        offlineEngine.startListening()
        mainHandler.post { onProgress("网络断开，已切换至离线识别") }
        _switchedToOffline = true
        _switchingEngine = false
    }

    private fun switchToOnlineDuringListening() {
        val online = onlineEngine ?: return
        if (_switchingEngine) return
        _switchingEngine = true

        Log.d(TAG, "Switching to online...")

        // Stop offline engine
        try {
            offlineEngine.onResult = {}
            offlineEngine.onPartialResult = {}
            offlineEngine.onError = {}
            offlineEngine.onStateChanged = {}
            offlineEngine.stopListening()
        } catch (_: Exception) {
        }

        // Re-set callbacks on online engine and restart
        online.onResult = { text -> mainHandler.post { onResult(text) } }
        online.onPartialResult = { text -> mainHandler.post { onPartialResult(text) } }
        online.onError = { err ->
            if (hybridMode == HybridMode.AUTO && !networkMonitor.isOnline && _userListening && !_switchingEngine) {
                Log.d(TAG, "Online engine error with no network, switching to offline: $err")
                switchToOfflineDuringListening()
            } else {
                mainHandler.post { onError(err) }
            }
        }
        online.onStateChanged = { listening ->
            isListening = listening
            mainHandler.post { onStateChanged(listening) }
        }
        online.onProgress = { msg ->
            mainHandler.post { onProgress("在线识别 - $msg") }
        }
        online.onConnectionLost = {
            Log.d(TAG, "Online engine connection lost, switching to offline")
            switchToOfflineDuringListening()
        }

        activeEngine = online
        online.startListening()
        mainHandler.post { onProgress("网络已恢复，已切换至在线识别") }
        _switchedToOffline = false
        _switchingEngine = false
    }
}
