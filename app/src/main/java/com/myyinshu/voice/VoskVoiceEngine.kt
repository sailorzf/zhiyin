package com.myyinshu.voice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class VoskVoiceEngine(
    private val context: Context
) : VoiceRecognitionEngine {

    override var onResult: (String) -> Unit = {}
    override var onPartialResult: (String) -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var onStateChanged: (Boolean) -> Unit = {}
    override var onProgress: (String) -> Unit = {}

    @Volatile
    override var isListening: Boolean = false
        private set

    @Volatile
    private var isPreparing: Boolean = false

    private val engineLock = Any()

    override val isModelReady: Boolean
        get() = model != null && recognizer != null

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recognitionThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hotWords: List<String> = emptyList()

    // Note: Vosk small-cn does not support true keyword boosting.
    // The Recognizer(model, sampleRate, grammar) constructor creates a CONSTRAINED recognizer
    // that only recognizes words in the grammar list. This is NOT hot word boosting.
    // Hot words are stored but not applied — kept for future upgrade to a larger model.

    private val sampleRate = 16000
    private val bufferSize = sampleRate / 3 // 300ms chunks for smooth updates

    companion object {
        private const val ASSETS_MODEL_DIR = "vosk-model-small-cn-0.22"
        private const val MODEL_DIR_NAME = "vosk-model"
    }

    fun getModelDir(): File {
        return File(context.filesDir, MODEL_DIR_NAME)
    }

    override fun prepareModel(onReady: () -> Unit, onError: (String) -> Unit) {
        if (isModelReady) return
        synchronized(this) {
            if (isPreparing) return
            isPreparing = true
        }

        Thread {
            try {
                val modelDir = getModelDir()

                // Validate model directory: if it exists but seems incomplete, clean and re-copy
                if (modelDir.exists()) {
                    val files = modelDir.listFiles()
                    if (files.isNullOrEmpty() || !File(modelDir, "am").exists() || !File(modelDir, "graph").exists()) {
                        Log.d("VoskEngine", "Model directory incomplete, cleaning up")
                        modelDir.deleteRecursively()
                    }
                }

                if (!modelDir.exists()) {
                    mainHandler.post { onProgress("正在复制语音模型...") }
                    copyAssetsToFilesDir(context, ASSETS_MODEL_DIR, modelDir) { fileName ->
                        mainHandler.post { onProgress("正在加载模型: $fileName") }
                    }
                }

                if (!modelDir.exists() || modelDir.listFiles().isNullOrEmpty()) {
                    mainHandler.post { onError("语音模型未安装") }
                    return@Thread
                }

                mainHandler.post { onProgress("正在初始化模型...") }
                Log.d("VoskEngine", "Loading model from ${modelDir.absolutePath}")

                val loadedModel: Model
                try {
                    loadedModel = Model(modelDir.absolutePath)
                } catch (modelE: Exception) {
                    Log.e("VoskEngine", "Model() constructor failed", modelE)
                    // Model might be corrupted, delete and retry next time
                    try { modelDir.deleteRecursively() } catch (_: Exception) {}
                    mainHandler.post { onError("模型加载失败: ${modelE.message}") }
                    return@Thread
                }
                Log.d("VoskEngine", "Model loaded, creating recognizer")

                val loadedRecognizer: Recognizer
                try {
                    loadedRecognizer = createRecognizer(loadedModel)
                } catch (recE: Exception) {
                    Log.e("VoskEngine", "Recognizer() constructor failed", recE)
                    try { loadedModel.close() } catch (_: Exception) {}
                    mainHandler.post { onError("识别器初始化失败: ${recE.message}") }
                    return@Thread
                }
                Log.d("VoskEngine", "Recognizer created")

                model = loadedModel
                recognizer = loadedRecognizer
                mainHandler.post {
                    onReady()
                }
            } catch (e: Exception) {
                Log.e("VoskEngine", "prepareModel error", e)
                mainHandler.post { onError("模型加载失败: ${e.message}") }
            } finally {
                isPreparing = false
            }
        }.start()
    }

    private fun copyAssetsToFilesDir(
        context: Context,
        assetPath: String,
        targetDir: File,
        onFileCopied: (String) -> Unit,
    ) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        for (fileName in files) {
            val fullAssetPath = if (assetPath.isEmpty()) fileName else "$assetPath/$fileName"
            val targetFile = File(targetDir, fileName)

            // Check if it's a directory by trying to list its contents
            val subFiles = try {
                assetManager.list(fullAssetPath)
            } catch (_: Exception) {
                null
            }
            if (!subFiles.isNullOrEmpty()) {
                // It's a directory, recurse
                copyAssetsToFilesDir(context, fullAssetPath, targetFile, onFileCopied)
            } else {
                // It's a file, copy it (skip if already exists)
                if (!targetFile.exists()) {
                    try {
                        assetManager.open(fullAssetPath).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VoskEngine", "Failed to copy $fullAssetPath", e)
                        // Clean up partial copy
                        if (targetFile.exists()) targetFile.delete()
                        throw e
                    }
                }
            }
            onFileCopied(fileName)
        }
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createRecognizer(model: Model): Recognizer {
        return Recognizer(model, 16000.0f)
    }

    override fun startListening() {
        synchronized(engineLock) {
            if (!isModelReady) {
                mainHandler.post { onError("模型未就绪") }
                return
            }
            if (isListening) return
            if (!hasRecordPermission()) {
                mainHandler.post { onError("缺少录音权限") }
                return
            }

            val record: AudioRecord
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
            } catch (e: Exception) {
                mainHandler.post { onError("启动录音失败: ${e.message}") }
                return
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                mainHandler.post { onError("录音初始化失败") }
                return
            }

            audioRecord = record
            recognizer?.reset()
            record.startRecording()
            isListening = true
            onStateChanged(true)

            recognitionThread = Thread {
                try {
                    val buffer = ShortArray(bufferSize)
                    while (isListening) {
                        val nread = record.read(buffer, 0, bufferSize)
                        if (nread > 0) {
                            val rec = synchronized(engineLock) { recognizer } ?: break
                            val result = rec.acceptWaveForm(buffer, nread)
                            if (result) {
                                val json = JSONObject(rec.result)
                                val text = json.optString("text", "").trim()
                                if (text.isNotEmpty()) {
                                    mainHandler.post { onResult(text) }
                                }
                            } else {
                                val partialJson = JSONObject(rec.partialResult)
                                val partialText = partialJson.optString("partial", "").trim()
                                if (partialText.isNotEmpty()) {
                                    mainHandler.post { onPartialResult(partialText) }
                                }
                            }
                        }
                    }
                    // Final partial result
                    val rec = synchronized(engineLock) { recognizer }
                    if (rec != null) {
                        val finalJson = JSONObject(rec.finalResult)
                        val finalText = finalJson.optString("text", "").trim()
                        if (finalText.isNotEmpty()) {
                            mainHandler.post { onResult(finalText) }
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post { onError("识别异常: ${e.message}") }
                }
            }
            recognitionThread?.start()
        }
    }

    override fun stopListening() {
        synchronized(engineLock) {
            if (!isListening) return
            isListening = false
            onStateChanged(false)

            try {
                audioRecord?.stop()
            } catch (_: Exception) {}
            audioRecord?.release()
            audioRecord = null
        }

        recognitionThread?.join(1000)
        recognitionThread = null
    }

    override fun setHotWords(words: List<String>) {
        // Store hot words for future use, but don't apply them.
        // Vosk small-cn grammar constrains vocabulary (bad), doesn't boost keywords.
        hotWords = words
    }

    override fun shutdown() {
        // Wait for prepareModel to finish
        while (isPreparing) {
            Thread.sleep(50)
        }

        synchronized(engineLock) {
            isListening = false
            try { audioRecord?.stop() } catch (_: Exception) {}
            audioRecord?.release()
            audioRecord = null
        }

        recognitionThread?.join(1000)
        recognitionThread = null

        synchronized(engineLock) {
            try { recognizer?.close() } catch (_: Exception) {}
            try { model?.close() } catch (_: Exception) {}
            recognizer = null
            model = null
        }
    }
}
