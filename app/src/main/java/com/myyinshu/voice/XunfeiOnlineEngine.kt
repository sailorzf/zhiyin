package com.myyinshu.voice

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Xunfei streaming online voice recognition via WebSocket IAT API.
 * Uses wss://iat-api.xfyun.cn/v2/iat with real-time audio streaming.
 * VAD EOS is set to 2 seconds; auto-reconnects after each utterance.
 */
class XunfeiOnlineEngine(
    private val context: Context,
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
) : VoiceRecognitionEngine {

    companion object {
        private const val TAG = "XunfeiOnline"
        private const val IAT_URL = "wss://iat-api.xfyun.cn/v2/iat"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = SAMPLE_RATE / 5 // 200ms chunks
    }

    override var onResult: (String) -> Unit = {}
    override var onPartialResult: (String) -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onStateChanged: (Boolean) -> Unit = {}
    override var onProgress: (String) -> Unit = {}

    // Called when the WebSocket connection fails (not just an API error)
    var onConnectionLost: () -> Unit = {}

    @Volatile
    override var isListening: Boolean = false
        private set

    @Volatile
    override var isModelReady: Boolean = false
        private set

    private var audioRecord: AudioRecord? = null
    private var recognitionThread: Thread? = null
    private var webSocket: WebSocket? = null
    private var mainHandler = Handler(Looper.getMainLooper())
    private var readyForAudio = false
    private var pendingAudioBuffers = mutableListOf<ByteArray>()

    // Session state
    private var isFirstFrame = true
    private var finalResultDelivered = false
    private var lastPartialResult = ""
    private var serverSessionEnded = false
    private var socketClosed = false
    private var reconnectScheduled = false
    private var engineStopped = false

    init {
        isModelReady = true
    }

    override fun prepareModel(onReady: () -> Unit, onError: (String) -> Unit) {
        isModelReady = true
        onProgress("在线引擎就绪")
        mainHandler.post { onReady() }
    }

    override fun startListening() {
        if (!hasRecordPermission()) {
            mainHandler.post { onError("缺少录音权限") }
            return
        }
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            mainHandler.post { onError("未配置讯飞在线API密钥，请在代码中设置") }
            return
        }

        resetSessionState()

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            mainHandler.post { onError("录音初始化失败") }
            return
        }

        audioRecord = record
        record.startRecording()

        connectWebSocket()

        isListening = true
        onStateChanged(true)
    }

    override fun stopListening() {
        isListening = false
        engineStopped = true
        onStateChanged(false)

        if (readyForAudio && !serverSessionEnded && !socketClosed) {
            sendEndFrame()
        }

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord = null

        recognitionThread?.join(1000)
        recognitionThread = null

        socketClosed = true
        webSocket?.close(1000, "User stopped")
        webSocket = null
    }

    override fun shutdown() {
        isListening = false
        isModelReady = false
        engineStopped = true

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord = null

        recognitionThread?.join(1000)
        recognitionThread = null

        socketClosed = true
        webSocket?.close(1000, "Shutdown")
        webSocket = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun resetSessionState() {
        readyForAudio = false
        pendingAudioBuffers.clear()
        isFirstFrame = true
        finalResultDelivered = false
        lastPartialResult = ""
        serverSessionEnded = false
        socketClosed = false
        reconnectScheduled = false
        engineStopped = false
    }

    private fun connectWebSocket() {
        val authUrl = buildAuthUrl()
        val request = Request.Builder()
            .url(authUrl)
            .build()

        webSocket = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newWebSocket(request, createWebSocketListener())
    }

    private fun scheduleReconnect() {
        if (reconnectScheduled || !isListening || engineStopped) return
        reconnectScheduled = true
        mainHandler.postDelayed({
            if (!isListening || engineStopped) return@postDelayed
            socketClosed = true
            webSocket = null
            reconnectScheduled = false
            pendingAudioBuffers.clear()
            isFirstFrame = true
            finalResultDelivered = false
            lastPartialResult = ""
            serverSessionEnded = false
            socketClosed = false
            connectWebSocket()
        }, 200)
    }

    private fun buildAuthUrl(): String {
        val host = "iat-api.xfyun.cn"
        val path = "/v2/iat"
        val date = formatDate(Date())

        val signatureOrigin = "host: $host\ndate: $date\nGET $path HTTP/1.1"
        val hmacSha256 = hmacSha256(signatureOrigin, apiSecret)
        val signature = java.util.Base64.getEncoder().encodeToString(hmacSha256)

        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = java.util.Base64.getEncoder().encodeToString(authorizationOrigin.toByteArray(Charsets.UTF_8))

        return "$IAT_URL?authorization=${encode(authorization)}&date=${encode(date)}&host=${encode(host)}"
    }

    private fun formatDate(date: Date): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        return format.format(date)
    }

    private fun hmacSha256(data: String, key: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                readyForAudio = true

                if (recognitionThread == null || !recognitionThread!!.isAlive) {
                    recognitionThread = Thread {
                        try {
                            val buffer = ShortArray(BUFFER_SIZE)
                            while (isListening) {
                                val nread = audioRecord?.read(buffer, 0, BUFFER_SIZE) ?: break
                                if (nread > 0) {
                                    val bytes = shortArrayToByteArray(buffer, nread)
                                    if (readyForAudio && !serverSessionEnded && !socketClosed) {
                                        val sendFirst = isFirstFrame
                                        if (sendFirst) isFirstFrame = false
                                        sendAudioFrame(bytes, isFirstFrame = sendFirst)
                                    } else {
                                        synchronized(pendingAudioBuffers) {
                                            pendingAudioBuffers.add(bytes)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Audio recording error", e)
                            mainHandler.post { onError("录音异常: ${e.message}") }
                        }
                    }
                    recognitionThread?.start()
                }

                // Flush buffered audio
                synchronized(pendingAudioBuffers) {
                    for (bytes in pendingAudioBuffers) {
                        sendAudioFrame(bytes, isFirstFrame = isFirstFrame)
                    }
                    pendingAudioBuffers.clear()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                try {
                    val json = JSONObject(text)
                    val code = json.optInt("code", -1)
                    if (code != 0) {
                        val message = json.optString("message", "")
                        mainHandler.post { onError("在线识别错误: $message (code=$code)") }
                        return
                    }

                    val dataObj = json.optJSONObject("data")
                    if (dataObj == null) {
                        Log.w(TAG, "No data field in response")
                        return
                    }

                    val status = dataObj.optInt("status", 0)
                    val result = dataObj.optJSONObject("result")

                    if (result == null) {
                        return
                    }

                    val ws = result.optJSONArray("ws")
                    if (ws != null && ws.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until ws.length()) {
                            val cw = ws.getJSONObject(i).optJSONArray("cw")
                            if (cw != null && cw.length() > 0) {
                                sb.append(cw.getJSONObject(0).optString("w", ""))
                            }
                        }
                        val recognizedText = sb.toString()
                        if (recognizedText.isNotEmpty()) {
                            val ls = result.optBoolean("ls", false)
                            val isFinal = ls || status == 2
                            if (isFinal) {
                                // Final frame: append its text (usually trailing punctuation) to partial
                                val finalText = lastPartialResult + recognizedText
                                if (finalText.isNotEmpty() && !finalResultDelivered) {
                                    finalResultDelivered = true
                                    onResult(finalText)
                                }
                            } else {
                                lastPartialResult = recognizedText
                                mainHandler.post { onPartialResult(recognizedText) }
                            }
                        }

                        // Server signals utterance complete (VAD EOS triggered)
                        if (status == 2) {
                            serverSessionEnded = true
                            // If partial had content but final frame was empty, deliver partial as final
                            if (!finalResultDelivered && lastPartialResult.isNotEmpty()) {
                                finalResultDelivered = true
                                onResult(lastPartialResult)
                            }
                            // Immediately close and schedule reconnect
                            if (isListening && !reconnectScheduled) {
                                socketClosed = true
                                scheduleReconnect()
                                try {
                                    webSocket?.close(1000, "Session complete")
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse result error", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                readyForAudio = false
                socketClosed = true

                if (isListening && !reconnectScheduled) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                socketClosed = true
                // Notify HybridVoiceEngine when connection drops after being established
                if (isListening && !engineStopped) {
                    mainHandler.post { onConnectionLost() }
                }
                // Don't show error to user if we're in the middle of a reconnect
                if (!reconnectScheduled) {
                    mainHandler.post {
                        onError("在线识别连接失败: ${t.message}")
                    }
                }
            }
        }
    }

    private fun sendAudioFrame(bytes: ByteArray, isFirstFrame: Boolean) {
        val ws = webSocket ?: return
        if (socketClosed) return
        val frame = JSONObject().apply {
            if (isFirstFrame) {
                put("common", JSONObject().apply { put("app_id", appId) })
                put("business", JSONObject().apply {
                    put("language", "zh_cn")
                    put("domain", "iat")
                    put("accent", "mandarin")
                    put("dwa", "wpgs")
                    put("vad_eos", 2000)
                })
            }
            put("data", JSONObject().apply {
                if (isFirstFrame) put("status", 0) else put("status", 1)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", java.util.Base64.getEncoder().encodeToString(bytes))
            })
        }
        try {
            ws.send(frame.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send audio frame (socket may be closing)")
            socketClosed = true
        }
    }

    private fun sendEndFrame() {
        val ws = webSocket ?: return
        if (socketClosed) return
        val frame = JSONObject().apply {
            put("data", JSONObject().apply {
                put("status", 2)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", "")
            })
        }
        try {
            ws.send(frame.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send end frame")
        }
    }

    private fun shortArrayToByteArray(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
