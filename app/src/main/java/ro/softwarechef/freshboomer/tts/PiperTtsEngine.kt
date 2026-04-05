package ro.softwarechef.freshboomer.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream

object PiperTtsEngine {

    private const val TAG = "PiperTTS"
    private const val ESPEAK_DATA_DIR = "espeak-ng-data"
    private const val TOKENS_FILE = "tokens.txt"

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var initialized = false
    private var loadedVoice: PiperVoice? = null

    var lengthScale: Float = 1.0f

    val isReady: Boolean get() = initialized && tts != null

    fun isReadyFor(voice: PiperVoice): Boolean = isReady && loadedVoice == voice

    /**
     * Initialize with a specific voice. If a different voice was loaded, releases it first.
     */
    fun initialize(context: Context, voice: PiperVoice): Boolean {
        if (initialized && loadedVoice == voice) return true
        if (initialized) release()
        return initializeInternal(context, voice.modelFilename).also {
            if (it) loadedVoice = voice
        }
    }

    /**
     * Initialize the Piper TTS engine and run a warmup generation.
     * Must be called on a background thread after model files are ready.
     */
    fun initialize(context: Context): Boolean {
        if (initialized) return true

        return initializeInternal(context, TtsModelManager.MODEL_FILENAME).also {
            if (it) loadedVoice = PiperVoice.LILI
        }
    }

    private fun initializeInternal(context: Context, modelFilename: String): Boolean {
        try {
            val modelDir = TtsModelManager.getModelDir(context)
            val modelFile = File(modelDir, modelFilename)
            val tokensFile = File(modelDir, TOKENS_FILE)
            val espeakDataDir = File(modelDir, ESPEAK_DATA_DIR)

            if (!modelFile.exists()) {
                Log.w(TAG, "Model file not found: ${modelFile.absolutePath}")
                return false
            }

            // Copy espeak-ng-data from assets if not already present
            if (!espeakDataDir.exists()) {
                copyEspeakData(context, modelDir)
            }

            // Copy tokens.txt from assets if not already present
            if (!tokensFile.exists()) {
                copyAssetFile(context, TOKENS_FILE, tokensFile)
            }

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = espeakDataDir.absolutePath,
                lengthScale = lengthScale,
            )

            val numCores = Runtime.getRuntime().availableProcessors()
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = maxOf(2, numCores - 1),
                debug = false,
            )

            val config = OfflineTtsConfig(
                model = modelConfig,
            )

            tts = OfflineTts(assetManager = null, config = config)
            initialized = true
            Log.i(TAG, "Piper TTS initialized (sampleRate=${tts?.sampleRate()}, speakers=${tts?.numSpeakers()})")

            // Warmup: first inference is slow due to ONNX Runtime JIT.
            // Generate a short silent phrase to prime the engine.
            val warmupStart = System.currentTimeMillis()
            tts?.generate(".", sid = 0, speed = 1.0f)
            Log.i(TAG, "Warmup completed in ${System.currentTimeMillis() - warmupStart}ms")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Piper TTS", e)
            initialized = false
            return false
        }
    }

    /**
     * Synthesize text and play audio with streaming playback.
     * Audio starts playing as soon as the first chunk is ready.
     * Runs on the calling thread — call from a coroutine or background thread.
     */
    fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            return
        }

        try {
            // Stop any currently playing audio
            stopAudio()

            // Split into sentences for faster first-word latency.
            // Generate and play each sentence sequentially so the user
            // hears the first sentence while later ones are still generating.
            val sentences = splitSentences(text)
            for (sentence in sentences) {
                if (audioTrack == null && sentence != sentences.first()) {
                    // stopAudio() was called externally, abort
                    break
                }
                val audio = engine.generate(sentence, sid = 0, speed = lengthScale)
                if (audio.samples.isEmpty()) continue
                playAudioAndWait(audio.samples, audio.sampleRate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
        }
    }

    fun stop() {
        stopAudio()
    }

    fun release() {
        stopAudio()
        tts?.release()
        tts = null
        initialized = false
    }

    /**
     * Play audio samples and block until playback completes.
     */
    private fun playAudioAndWait(samples: FloatArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track

        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()

        // Wait for playback to finish
        val durationMs = (samples.size * 1000L) / sampleRate
        Thread.sleep(durationMs)

        // Only clean up if we're still the active track
        if (audioTrack == track) {
            try {
                track.stop()
                track.flush()
                track.release()
            } catch (_: Exception) {}
        }
    }

    private fun splitSentences(text: String): List<String> {
        // Split on sentence-ending punctuation, keeping the delimiter
        return text.split(Regex("(?<=[.!?,])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(text) }
    }

    private fun stopAudio() {
        try {
            val track = audioTrack
            audioTrack = null
            track?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                    it.flush()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio: ${e.message}")
        }
    }

    private fun copyEspeakData(context: Context, destDir: File) {
        val espeakDir = File(destDir, ESPEAK_DATA_DIR)
        espeakDir.mkdirs()
        copyAssetDir(context, ESPEAK_DATA_DIR, espeakDir)
        Log.i(TAG, "Copied espeak-ng-data to ${espeakDir.absolutePath}")
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            copyAssetFile(context, assetPath, destDir)
        } else {
            destDir.mkdirs()
            for (asset in assets) {
                val srcPath = "$assetPath/$asset"
                val destFile = File(destDir, asset)
                val subAssets = context.assets.list(srcPath)
                if (subAssets != null && subAssets.isNotEmpty()) {
                    copyAssetDir(context, srcPath, destFile)
                } else {
                    copyAssetFile(context, srcPath, destFile)
                }
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, destFile: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
