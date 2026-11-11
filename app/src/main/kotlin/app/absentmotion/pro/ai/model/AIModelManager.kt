@file:Suppress("DEPRECATION")
package app.absentmotion.pro.ai.model

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

sealed class AIModelState {
    object NotInstalled : AIModelState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : AIModelState()
    data class Paused(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AIModelState()
    object Installed : AIModelState()
    data class Error(val message: String) : AIModelState()
}

class AIModelManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<AIModelId, Job>()
    private val pausedFlags = ConcurrentHashMap<AIModelId, Boolean>()

    private val _modelStates = MutableStateFlow<Map<AIModelId, AIModelState>>(emptyMap())
    val modelStates: StateFlow<Map<AIModelId, AIModelState>> = _modelStates.asStateFlow()

    private val _totalStorageBytes = MutableStateFlow(0L)
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    private val diskBytesCache = ConcurrentHashMap<AIModelId, Long>()

    init {
        refreshAllStates()
    }

    fun getModelDir(model: AIModelDef): File {
        val dir = File(context.filesDir, "models/ai/${model.id.name.lowercase()}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isInstalled(id: AIModelId): Boolean {
        val def = AIModelRegistry.get(id)
        if (def.files.isEmpty()) return true // Built-in DSP / OpenCV
        val dir = getModelDir(def)
        val onDisk = def.files.all { file ->
            val target = File(dir, file.filename)
            target.exists() && (file.bytes <= 0L || target.length() == file.bytes)
        }
        if (onDisk) return true

        // Check if bundled in APK assets and extract if available
        val hasBundled = def.files.all { file ->
            val assetPaths = listOf(
                "models/${file.filename}",
                "models/${def.id.name.lowercase()}/${file.filename}",
                file.filename
            )
            assetPaths.any { p ->
                try { context.assets.open(p).close(); true } catch (_: Throwable) { false }
            }
        }
        if (hasBundled) {
            return unpackBundledAssets(def)
        }
        return false
    }

    fun unpackBundledAssets(def: AIModelDef): Boolean {
        val dir = getModelDir(def)
        dir.mkdirs()
        var allOk = true
        for (file in def.files) {
            val target = File(dir, file.filename)
            if (target.exists() && (file.bytes <= 0L || target.length() == file.bytes)) continue
            val assetPaths = listOf(
                "models/${file.filename}",
                "models/${def.id.name.lowercase()}/${file.filename}",
                file.filename
            )
            val found = assetPaths.firstOrNull { p ->
                try { context.assets.open(p).close(); true } catch (_: Throwable) { false }
            }
            if (found != null) {
                try {
                    context.assets.open(found).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    if (file.sha256.isNotEmpty() && file.bytes > 0 && target.length() == file.bytes) {
                        val sha = computeSha256(target)
                        if (!sha.equals(file.sha256, ignoreCase = true)) {
                            target.delete()
                            allOk = false
                        }
                    }
                } catch (_: Exception) {
                    target.delete()
                    allOk = false
                }
            } else {
                allOk = false
            }
        }
        return allOk && def.files.all { f ->
            val t = File(dir, f.filename)
            t.exists() && (f.bytes <= 0L || t.length() == f.bytes)
        }
    }

    fun refreshAllStates() {
        scope.launch {
            val newMap = mutableMapOf<AIModelId, AIModelState>()
            var totalBytes = 0L
            for (def in AIModelRegistry.models) {
                val current = _modelStates.value[def.id]
                if (current is AIModelState.Downloading) {
                    newMap[def.id] = current
                } else if (isInstalled(def.id)) {
                    newMap[def.id] = AIModelState.Installed
                } else if (current is AIModelState.Paused) {
                    newMap[def.id] = current
                } else {
                    newMap[def.id] = AIModelState.NotInstalled
                }
                val bytes = calculateDiskBytes(def.id)
                diskBytesCache[def.id] = bytes
                totalBytes += bytes
            }
            _modelStates.value = newMap
            _totalStorageBytes.value = totalBytes
        }
    }

    private fun calculateDiskBytes(id: AIModelId): Long {
        val def = AIModelRegistry.get(id)
        if (def.files.isEmpty()) return 0L
        val dir = getModelDir(def)
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getInstalledDiskBytes(id: AIModelId): Long {
        return diskBytesCache[id] ?: calculateDiskBytes(id)
    }

    fun getTotalInstalledStorageBytes(): Long {
        return _totalStorageBytes.value
    }

    fun downloadModel(id: AIModelId) {
        val def = AIModelRegistry.get(id)
        if (def.files.isEmpty()) {
            _modelStates.update { it + (id to AIModelState.Installed) }
            return
        }

        activeJobs[id]?.cancel()
        pausedFlags[id] = false

        val job = scope.launch {
            try {
                executeDownload(def)
                _modelStates.update { it + (id to AIModelState.Installed) }
            } catch (e: CancellationException) {
                val isPaused = pausedFlags[id] == true
                if (isPaused) {
                    val downloaded = calculatePartialBytes(def)
                    val prog = if (def.totalBytes > 0) downloaded.toFloat() / def.totalBytes else 0f
                    _modelStates.update { it + (id to AIModelState.Paused(prog, downloaded, def.totalBytes)) }
                } else {
                    _modelStates.update { it + (id to AIModelState.NotInstalled) }
                }
            } catch (e: Exception) {
                _modelStates.update { it + (id to AIModelState.Error(e.message ?: "Download failed")) }
            } finally {
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
    }

    fun pauseDownload(id: AIModelId) {
        pausedFlags[id] = true
        activeJobs[id]?.cancel()
    }

    fun cancelDownload(id: AIModelId) {
        pausedFlags[id] = false
        activeJobs[id]?.cancel()
        val def = AIModelRegistry.get(id)
        val dir = getModelDir(def)
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        _modelStates.update { it + (id to AIModelState.NotInstalled) }
    }

    fun deleteModel(id: AIModelId) {
        cancelDownload(id)
        val def = AIModelRegistry.get(id)
        val dir = getModelDir(def)
        dir.deleteRecursively()
        refreshAllStates()
    }

    fun hasBundledWeights(id: AIModelId): Boolean {
        val def = AIModelRegistry.get(id)
        if (def.files.isEmpty()) return true
        return def.files.all { file ->
            val assetPaths = listOf(
                "models/${file.filename}",
                "models/${def.id.name.lowercase()}/${file.filename}",
                file.filename
            )
            assetPaths.any { p ->
                try { context.assets.open(p).close(); true } catch (_: Throwable) { false }
            }
        }
    }

    fun unpackBundledOrRetry(id: AIModelId): Boolean {
        val def = AIModelRegistry.get(id)
        val unpacked = unpackBundledAssets(def)
        if (unpacked) {
            refreshAllStates()
            return true
        }
        downloadModel(id)
        return false
    }

    private fun calculatePartialBytes(def: AIModelDef): Long {
        val dir = getModelDir(def)
        var sum = 0L
        for (file in def.files) {
            val target = File(dir, file.filename)
            val part = File(dir, "${file.filename}.part")
            if (target.exists()) sum += target.length()
            else if (part.exists()) sum += part.length()
        }
        return sum
    }

    private fun openConnectionWithRedirects(initialUrl: String, downloaded: Long): Pair<HttpURLConnection, Long> {
        var currentUrl = initialUrl
        var redirectCount = 0
        while (redirectCount < 5) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AbsentMotion/4.4")
            if (downloaded > 0L) {
                conn.setRequestProperty("Range", "bytes=$downloaded-")
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) {
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                    redirectCount++
                    continue
                }
            }
            return Pair(conn, conn.contentLengthLong)
        }
        throw java.io.IOException("Too many redirects: $initialUrl")
    }

    private suspend fun executeDownload(def: AIModelDef) = withContext(Dispatchers.IO) {
        val dir = getModelDir(def)
        val totalBytes = def.totalBytes
        var completedFilesBytes = 0L

        for (file in def.files) {
            val target = File(dir, file.filename)
            if (target.exists() && target.length() > 0) {
                completedFilesBytes += file.bytes
                continue
            }

            val partFile = File(dir, "${file.filename}.part")
            var downloaded = if (partFile.exists()) partFile.length() else 0L
            if (downloaded >= file.bytes && file.bytes > 0) {
                partFile.delete()
                downloaded = 0L
            }

            val urlsToTry = listOf(file.downloadUrl) + file.fallbackUrls
            var connection: HttpURLConnection? = null
            var lastError: Exception? = null
            var success = false

            for (attemptUrl in urlsToTry) {
                try {
                    val (conn, _) = openConnectionWithRedirects(attemptUrl, downloaded)
                    connection = conn
                    val code = conn.responseCode
                    val isResume = downloaded > 0 && code == HttpURLConnection.HTTP_PARTIAL
                    if (code != HttpURLConnection.HTTP_OK && !isResume) {
                        conn.disconnect()
                        throw IllegalStateException("HTTP error $code while downloading ${file.filename} from $attemptUrl")
                    }
                    success = true
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (!success || connection == null) {
                throw lastError ?: IllegalStateException("Failed to download ${file.filename} from available mirrors")
            }

            var startTime = System.currentTimeMillis()
            var speedCalcBytes = 0L
            var currentSpeed = 0L

            try {
                val isResume = downloaded > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
                val append = isResume
                val outputStream = FileOutputStream(partFile, append)
                val inputStream: InputStream = connection.inputStream

                outputStream.use { out ->
                    inputStream.use { inp ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        while (inp.read(buffer).also { bytesRead = it } != -1) {
                            if (pausedFlags[def.id] == true) {
                                throw CancellationException("Download paused by user")
                            }
                            out.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            speedCalcBytes += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - startTime
                            if (elapsed >= 1000) {
                                currentSpeed = (speedCalcBytes * 1000L) / elapsed
                                speedCalcBytes = 0L
                                startTime = now
                            }

                            val overall = completedFilesBytes + downloaded
                            val progress = if (totalBytes > 0) overall.toFloat() / totalBytes else 0f
                            _modelStates.update {
                                it + (def.id to AIModelState.Downloading(
                                    progress = progress.coerceIn(0f, 1f),
                                    downloadedBytes = overall,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = currentSpeed
                                ))
                            }
                        }
                    }
                }

                if (partFile.length() <= 0) {
                    throw IllegalStateException("Downloaded file is empty: ${file.filename}")
                }

                // If SHA256 is present and matches length, verify checksum
                if (file.sha256.isNotEmpty() && partFile.length() == file.bytes) {
                    val actualSha = computeSha256(partFile)
                    if (!actualSha.equals(file.sha256, ignoreCase = true)) {
                        partFile.delete()
                        throw IllegalStateException("SHA-256 verification failed for ${file.filename}")
                    }
                }

                if (target.exists()) target.delete()
                partFile.renameTo(target)
                completedFilesBytes += file.bytes

            } finally {
                connection.disconnect()
            }
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            var len: Int
            while (inp.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Volatile
        private var instance: AIModelManager? = null

        fun getInstance(context: Context): AIModelManager {
            return instance ?: synchronized(this) {
                instance ?: AIModelManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
