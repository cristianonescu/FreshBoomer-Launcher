package ro.softwarechef.freshboomer.tts

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

enum class PiperVoice(val modelFilename: String, val modelUrl: String) {
    LILI(
        "ro_RO-lili-medium.onnx",
        "https://huggingface.co/eduardem/piper-tts-romanian/resolve/main/voices/lili/ro_RO-lili-medium.onnx"
    ),
    SANDA(
        "ro_RO-sanda-medium.onnx",
        "https://huggingface.co/eduardem/piper-tts-romanian/resolve/main/voices/sanda/ro_RO-sanda-medium.onnx"
    )
}

object TtsModelManager {

    private const val TAG = "TtsModelManager"
    private const val MODEL_DIR_NAME = "piper-tts"
    const val MODEL_FILENAME = "ro_RO-lili-medium.onnx"
    private const val MODEL_URL =
        "https://huggingface.co/eduardem/piper-tts-romanian/resolve/main/voices/lili/ro_RO-lili-medium.onnx"

    private const val PREFS_NAME = "piper_tts_prefs"
    private const val KEY_ETAG = "model_etag"
    private const val KEY_LAST_MODIFIED = "model_last_modified"
    private const val KEY_FILE_SIZE = "model_file_size"
    private const val KEY_METADATA_INJECTED = "metadata_injected_v4"

    fun getModelDir(context: Context): File {
        return File(context.filesDir, MODEL_DIR_NAME).also { it.mkdirs() }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Voice-prefixed pref keys ---
    private fun keyEtag(voice: PiperVoice) = "${voice.name}_etag"
    private fun keyLastModified(voice: PiperVoice) = "${voice.name}_last_modified"
    private fun keyFileSize(voice: PiperVoice) = "${voice.name}_file_size"
    private fun keyMetadataInjected(voice: PiperVoice) = "${voice.name}_metadata_injected_v4"

    fun getModelFilename(voice: PiperVoice): String = voice.modelFilename

    /**
     * Check if the model file exists locally.
     */
    fun isModelDownloaded(context: Context): Boolean {
        val modelFile = File(getModelDir(context), MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun isModelDownloaded(context: Context, voice: PiperVoice): Boolean {
        val modelFile = File(getModelDir(context), voice.modelFilename)
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Ensure ONNX metadata is injected into the model file.
     * If the metadata version changed, deletes the model to force re-download
     * (re-injecting on an already-patched file would corrupt it).
     */
    fun ensureMetadataInjected(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.getBoolean(KEY_METADATA_INJECTED, false)) return

        val modelFile = File(getModelDir(context), MODEL_FILENAME)
        if (modelFile.exists()) {
            // Delete and force re-download to avoid double-injection
            modelFile.delete()
            prefs.edit()
                .remove(KEY_ETAG)
                .remove(KEY_LAST_MODIFIED)
                .remove(KEY_FILE_SIZE)
                .putBoolean(KEY_METADATA_INJECTED, false)
                .apply()
            Log.i(TAG, "Deleted model for metadata version upgrade, will re-download")
        }
    }

    fun ensureMetadataInjected(context: Context, voice: PiperVoice) {
        val prefs = getPrefs(context)
        if (prefs.getBoolean(keyMetadataInjected(voice), false)) return

        val modelFile = File(getModelDir(context), voice.modelFilename)
        if (modelFile.exists()) {
            modelFile.delete()
            prefs.edit()
                .remove(keyEtag(voice))
                .remove(keyLastModified(voice))
                .remove(keyFileSize(voice))
                .putBoolean(keyMetadataInjected(voice), false)
                .apply()
            Log.i(TAG, "Deleted ${voice.name} model for metadata version upgrade, will re-download")
        }
    }

    /**
     * Check if the remote model is newer than the local one.
     * Returns true if an update is available, false if up-to-date.
     * Returns true on any error (to be safe, attempt download).
     */
    suspend fun isUpdateAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = getPrefs(context)
            val savedEtag = prefs.getString(KEY_ETAG, null)
            val savedLastModified = prefs.getString(KEY_LAST_MODIFIED, null)

            if (savedEtag == null && savedLastModified == null) {
                return@withContext true
            }

            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HEAD request returned $responseCode")
                connection.disconnect()
                return@withContext false // Can't check, assume no update
            }

            val remoteEtag = connection.getHeaderField("ETag")
            val remoteLastModified = connection.getHeaderField("Last-Modified")
            val remoteSize = connection.getHeaderField("Content-Length")?.toLongOrNull()
            connection.disconnect()

            val etagChanged = remoteEtag != null && remoteEtag != savedEtag
            val lastModifiedChanged = remoteLastModified != null && remoteLastModified != savedLastModified
            val sizeChanged = remoteSize != null && remoteSize != prefs.getLong(KEY_FILE_SIZE, -1)

            val updateAvailable = etagChanged || lastModifiedChanged || sizeChanged
            Log.d(TAG, "Update check: etag=$etagChanged, lastModified=$lastModifiedChanged, size=$sizeChanged -> $updateAvailable")
            updateAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            false // Network error, don't force download
        }
    }

    suspend fun isUpdateAvailable(context: Context, voice: PiperVoice): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = getPrefs(context)
            val savedEtag = prefs.getString(keyEtag(voice), null)
            val savedLastModified = prefs.getString(keyLastModified(voice), null)

            if (savedEtag == null && savedLastModified == null) {
                return@withContext true
            }

            val connection = URL(voice.modelUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HEAD request for ${voice.name} returned $responseCode")
                connection.disconnect()
                return@withContext false
            }

            val remoteEtag = connection.getHeaderField("ETag")
            val remoteLastModified = connection.getHeaderField("Last-Modified")
            val remoteSize = connection.getHeaderField("Content-Length")?.toLongOrNull()
            connection.disconnect()

            val etagChanged = remoteEtag != null && remoteEtag != savedEtag
            val lastModifiedChanged = remoteLastModified != null && remoteLastModified != savedLastModified
            val sizeChanged = remoteSize != null && remoteSize != prefs.getLong(keyFileSize(voice), -1)

            val updateAvailable = etagChanged || lastModifiedChanged || sizeChanged
            Log.d(TAG, "${voice.name} update check: etag=$etagChanged, lastModified=$lastModifiedChanged, size=$sizeChanged -> $updateAvailable")
            updateAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for ${voice.name} updates", e)
            false
        }
    }

    /**
     * Download the model file with progress reporting.
     * @param onProgress callback with (bytesDownloaded, totalBytes) — totalBytes may be -1 if unknown
     */
    suspend fun downloadModel(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDir(context)
            val modelFile = File(modelDir, MODEL_FILENAME)
            val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")

            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed with HTTP $responseCode")
                connection.disconnect()
                return@withContext false
            }

            val totalBytes = connection.contentLengthLong
            val etag = connection.getHeaderField("ETag")
            val lastModified = connection.getHeaderField("Last-Modified")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress(totalRead, totalBytes)
                    }
                }
            }
            connection.disconnect()

            // Atomic rename
            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                Log.e(TAG, "Failed to rename temp file")
                tempFile.delete()
                return@withContext false
            }

            // Save metadata for future update checks
            getPrefs(context).edit().apply {
                if (etag != null) putString(KEY_ETAG, etag)
                if (lastModified != null) putString(KEY_LAST_MODIFIED, lastModified)
                putLong(KEY_FILE_SIZE, modelFile.length())
                apply()
            }

            // Inject sherpa-onnx required metadata into the ONNX file
            injectOnnxMetadata(modelFile)
            getPrefs(context).edit().putBoolean(KEY_METADATA_INJECTED, true).apply()

            Log.i(TAG, "Model downloaded successfully: ${modelFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            // Clean up temp file
            File(getModelDir(context), "$MODEL_FILENAME.tmp").delete()
            false
        }
    }

    suspend fun downloadModel(
        context: Context,
        voice: PiperVoice,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDir(context)
            val modelFile = File(modelDir, voice.modelFilename)
            val tempFile = File(modelDir, "${voice.modelFilename}.tmp")

            val connection = URL(voice.modelUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download of ${voice.name} failed with HTTP $responseCode")
                connection.disconnect()
                return@withContext false
            }

            val totalBytes = connection.contentLengthLong
            val etag = connection.getHeaderField("ETag")
            val lastModified = connection.getHeaderField("Last-Modified")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress(totalRead, totalBytes)
                    }
                }
            }
            connection.disconnect()

            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                Log.e(TAG, "Failed to rename temp file for ${voice.name}")
                tempFile.delete()
                return@withContext false
            }

            getPrefs(context).edit().apply {
                if (etag != null) putString(keyEtag(voice), etag)
                if (lastModified != null) putString(keyLastModified(voice), lastModified)
                putLong(keyFileSize(voice), modelFile.length())
                apply()
            }

            injectOnnxMetadata(modelFile)
            getPrefs(context).edit().putBoolean(keyMetadataInjected(voice), true).apply()

            Log.i(TAG, "${voice.name} model downloaded successfully: ${modelFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download of ${voice.name} failed", e)
            File(getModelDir(context), "${voice.modelFilename}.tmp").delete()
            false
        }
    }

    /**
     * Inject metadata that sherpa-onnx requires into the ONNX model file.
     *
     * Raw Piper models from HuggingFace don't have sample_rate etc. in the ONNX
     * metadata — sherpa-onnx calls exit() if these are missing. We append protobuf-
     * encoded metadata_props entries (field 14 of ModelProto) to the file. Protobuf
     * merge semantics mean repeated fields are concatenated, so this is safe.
     */
    private fun injectOnnxMetadata(modelFile: File) {
        val metadata = mapOf(
            "sample_rate" to "22050",
            "n_speakers" to "1",
            "language" to "Romanian",
            "voice" to "ro",
            "comment" to "piper",
            "add_blank" to "1",
            "blank_id" to "0",
        )

        val extraBytes = ByteArrayOutputStream()
        for ((key, value) in metadata) {
            // Build StringStringEntryProto: field1=key, field2=value
            val entry = ByteArrayOutputStream()
            // field 1 (key), wire type 2 (length-delimited) → tag = 0x0A
            entry.write(0x0A)
            writeVarint(entry, key.toByteArray().size)
            entry.write(key.toByteArray())
            // field 2 (value), wire type 2 → tag = 0x12
            entry.write(0x12)
            writeVarint(entry, value.toByteArray().size)
            entry.write(value.toByteArray())

            val entryBytes = entry.toByteArray()

            // Wrap in ModelProto.metadata_props (field 14, wire type 2) → tag = 0x72
            extraBytes.write(0x72)
            writeVarint(extraBytes, entryBytes.size)
            extraBytes.write(entryBytes)
        }

        // Append to file
        RandomAccessFile(modelFile, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(extraBytes.toByteArray())
        }

        Log.i(TAG, "Injected ${metadata.size} metadata entries into ONNX model")
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v > 0x7F) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }
}
