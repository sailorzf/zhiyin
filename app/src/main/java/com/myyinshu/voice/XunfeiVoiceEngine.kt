package com.myyinshu.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.iflytek.cloud.*
import com.iflytek.cloud.util.ResourceUtil
import org.json.JSONObject
import org.json.JSONTokener

class XunfeiVoiceEngine(
    private val context: Context,
    private val appId: String = "4e4c607f",
) : VoiceRecognitionEngine {

    companion object {
        private const val TAG = "XunfeiEngine"
        @Volatile
        private var sdkInitialized = false
    }

    override var onResult: (String) -> Unit = {}
    override var onPartialResult: (String) -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onStateChanged: (Boolean) -> Unit = {}
    override var onProgress: (String) -> Unit = {}

    @Volatile
    override var isListening: Boolean = false
        private set

    @Volatile
    override var isModelReady: Boolean = false
        private set

    @Volatile
    private var continuousListening = false
    private var recognizer: SpeechRecognizer? = null
    private var initialized = false
    private lateinit var recognizerListener: RecognizerListener
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var hotWords: List<String> = emptyList()

    override fun prepareModel(onReady: () -> Unit, onError: (String) -> Unit) {
        if (initialized) {
            onReady()
            return
        }

        onProgress("正在初始化讯飞引擎...")
        Log.d(TAG, "prepareModel called")

        try {
            if (!sdkInitialized) {
                SpeechUtility.createUtility(
                    context,
                    "${SpeechConstant.APPID}=$appId,${SpeechConstant.ENGINE_MODE}=${SpeechConstant.MODE_MSC}"
                )
                sdkInitialized = true
                Log.d(TAG, "SpeechUtility created")
            }

            recognizer = SpeechRecognizer.createRecognizer(context, object : InitListener {
                override fun onInit(code: Int) {
                    Log.d(TAG, "SpeechRecognizer init() code = $code")
                    if (code != ErrorCode.SUCCESS) {
                        mainHandler.post {
                            initialized = false
                            isModelReady = false
                            onError("讯飞引擎初始化失败，错误码：$code")
                        }
                    } else {
                        mainHandler.post {
                            initialized = true
                            isModelReady = true
                            initListener()
                            onProgress("讯飞引擎就绪")
                            onReady()
                        }
                    }
                }
            })

            if (recognizer == null) {
                onError("创建讯飞识别对象失败，请确认 libmsc.so 放置正确")
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepareModel error", e)
            onError("讯飞引擎初始化异常：${e.message}")
        }
    }

    override fun startListening() {
        if (!initialized || recognizer == null) {
            onError("讯飞引擎未初始化")
            return
        }

        setParam()

        val ret = recognizer?.startListening(recognizerListener)
        if (ret != ErrorCode.SUCCESS) {
            onError("开始听写失败，错误码：$ret")
        } else {
            isListening = true
            continuousListening = true
            onStateChanged(true)
        }
    }

    override fun stopListening() {
        if (!initialized || recognizer == null) {
            isListening = false
            continuousListening = false
            return
        }
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
        isListening = false
        continuousListening = false
        onStateChanged(false)
    }

    override fun shutdown() {
        Log.d(TAG, "shutdown called, initialized=$initialized")
        mainHandler.removeCallbacksAndMessages(null)
        continuousListening = false
        if (initialized && recognizer != null) {
            try { recognizer?.stopListening() } catch (_: Exception) {}
            try { recognizer?.cancel() } catch (_: Exception) {}
            // Give the native audio thread time to stop before nulling out
            Thread.sleep(200)
            initialized = false
            recognizer = null
        }
        isListening = false
        isModelReady = false
        Log.d(TAG, "shutdown complete")
    }

    override fun setHotWords(words: List<String>) {
        hotWords = words
        // Note: iFlytek offline IAT does not support runtime hot words like cloud mode.
        // Hot words would require a custom grammar file built into the offline package.
        Log.d(TAG, "setHotWords called (${words.size} words) — offline mode does not support runtime hot words")
    }

    private fun setParam() {
        recognizer?.let { recog ->
            // Clear params
            recog.setParameter(SpeechConstant.PARAMS, null)

            // Offline engine
            recog.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL)

            // Result format
            recog.setParameter(SpeechConstant.RESULT_TYPE, "json")

            // Offline resource path
            recog.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath())

            // Language - mandarin
            recog.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            recog.setParameter(SpeechConstant.ACCENT, "mandarin")

            // VAD - frontend point (shorter for faster response to speech start)
            recog.setParameter(SpeechConstant.VAD_BOS, "2000")

            // VAD - backend point (shorter = faster result after speech stops, similar to Vosk 300ms buffer)
            recog.setParameter(SpeechConstant.VAD_EOS, "500")

            // Punctuation
            recog.setParameter(SpeechConstant.ASR_PTT, "1")
        }
    }

    private fun getResourcePath(): String {
        val commonPath = ResourceUtil.generateResourcePath(
            context,
            ResourceUtil.RESOURCE_TYPE.assets,
            "iat/common.jet"
        )
        val smsPath = ResourceUtil.generateResourcePath(
            context,
            ResourceUtil.RESOURCE_TYPE.assets,
            "iat/sms_16k.jet"
        )
        return "$commonPath;$smsPath"
    }

    private fun initListener() {
        recognizerListener = object : RecognizerListener {
            override fun onBeginOfSpeech() {
                Log.d(TAG, "onBeginOfSpeech")
            }

            override fun onError(error: SpeechError) {
                Log.e(TAG, "onError: code=${error.errorCode} - ${error.getPlainDescription(true)}")
                if (error.errorCode == 10118 && continuousListening && recognizer != null) {
                    mainHandler.postDelayed(Runnable {
                        try {
                            setParam()
                            recognizer!!.startListening(recognizerListener)
                            Log.d(TAG, "auto-restarted after no-speech error")
                        } catch (e: Exception) {
                            Log.e(TAG, "auto-restart after error failed", e)
                        }
                    }, 100L)
                    return
                }

                mainHandler.post {
                    isListening = false
                    continuousListening = false
                    onStateChanged(false)
                    onError("识别错误：${error.getPlainDescription(true)}")
                }
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
            }

            override fun onResult(results: RecognizerResult, isLast: Boolean) {
                val text = parseIatResult(results.resultString)
                Log.d(TAG, "onResult: isLast=$isLast, text=$text")
                if (text.isNotEmpty()) {
                    mainHandler.post {
                        if (isLast) {
                            onResult(text)
                            if (continuousListening && recognizer != null) {
                                mainHandler.postDelayed(Runnable {
                                    try {
                                        setParam()
                                        recognizer!!.startListening(recognizerListener)
                                        Log.d(TAG, "auto-restarted listening")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "auto-restart failed", e)
                                    }
                                }, 100L)
                            }
                        } else {
                            onPartialResult(text)
                        }
                    }
                }
            }

            override fun onVolumeChanged(volume: Int, data: ByteArray) {}

            override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: android.os.Bundle?) {}
        }
    }

    /**
     * Parse iFlytek recognition result JSON.
     * Format: {"sn":1,"ls":true,"bg":0,"ed":0,"ws":[{"bg":0,"cw":[{"w":"你好","sc":0}]}]}
     */
    private fun parseIatResult(json: String): String {
        val ret = StringBuilder()
        try {
            val tokener = JSONTokener(json)
            val joResult = JSONObject(tokener)
            val words = joResult.getJSONArray("ws")
            for (i in 0 until words.length()) {
                val items = words.getJSONObject(i).getJSONArray("cw")
                val obj = items.getJSONObject(0)
                ret.append(obj.getString("w"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseIatResult error", e)
        }
        return ret.toString()
    }
}
