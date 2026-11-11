package app.absentmotion.pro.assets

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class AssetStore(private val context: Context) {

    private val favoritesFile = File(context.filesDir, "pro_assets_favorites.json")
    private val recentsFile = File(context.filesDir, "pro_assets_recents.json")
    private val aiGenerationsFile = File(context.filesDir, "pro_assets_ai_generations.json")
    private val assetsCacheDir = File(context.cacheDir, "pro_assets").apply { mkdirs() }

    private val _favorites = MutableStateFlow<List<OnlineAsset>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private val _recents = MutableStateFlow<List<OnlineAsset>>(emptyList())
    val recents = _recents.asStateFlow()

    private val _aiGenerations = MutableStateFlow<List<OnlineAsset>>(emptyList())
    val aiGenerations = _aiGenerations.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        runCatching {
            if (favoritesFile.exists()) {
                val array = JSONArray(favoritesFile.readText())
                val list = mutableListOf<OnlineAsset>()
                for (i in 0 until array.length()) {
                    list.add(OnlineAsset.fromJson(array.getJSONObject(i)))
                }
                _favorites.value = list
            }
            if (recentsFile.exists()) {
                val array = JSONArray(recentsFile.readText())
                val list = mutableListOf<OnlineAsset>()
                for (i in 0 until array.length()) {
                    list.add(OnlineAsset.fromJson(array.getJSONObject(i)))
                }
                _recents.value = list
            }
            if (aiGenerationsFile.exists()) {
                val array = JSONArray(aiGenerationsFile.readText())
                val list = mutableListOf<OnlineAsset>()
                for (i in 0 until array.length()) {
                    list.add(OnlineAsset.fromJson(array.getJSONObject(i)))
                }
                _aiGenerations.value = list
            }
        }
    }

    suspend fun addAiGeneration(asset: OnlineAsset) = withContext(Dispatchers.IO) {
        val current = _aiGenerations.value.filter { it.id != asset.id }.toMutableList()
        current.add(0, asset)
        _aiGenerations.value = current
        runCatching {
            val array = JSONArray()
            current.take(60).forEach { array.put(it.toJson()) }
            aiGenerationsFile.writeText(array.toString())
        }
    }

    suspend fun registerAiResult(
        title: String,
        file: File,
        toolCategory: String,
        details: String = ""
    ): OnlineAsset = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase()
        val isAnimated = ext in setOf("mp4", "gif", "webm")
        val asset = OnlineAsset(
            id = "ai_${System.currentTimeMillis()}_${file.nameWithoutExtension}",
            title = title,
            type = if (ext in setOf("obj", "glb", "gltf")) AssetType.MODEL_3D
                   else if (ext == "svg") AssetType.SVG
                   else if (ext == "gif") AssetType.GIF
                   else AssetType.STICKER,
            source = AssetSource.AI_GENERATED,
            previewUrl = "file://${file.absolutePath}",
            contentUrl = file.absolutePath,
            width = 1280,
            height = 720,
            creator = "AI Engine ($toolCategory)",
            license = "Created by User",
            isAnimated = isAnimated,
            details = if (details.isNotBlank()) details else "Generated with $toolCategory (${file.length() / 1024} KB)",
            downloadUrl = file.absolutePath,
            hasAlpha = ext in setOf("png", "webp", "gif")
        )
        addAiGeneration(asset)
        asset
    }

    suspend fun toggleFavorite(asset: OnlineAsset) = withContext(Dispatchers.IO) {
        val current = _favorites.value.toMutableList()
        val index = current.indexOfFirst { it.id == asset.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, asset)
        }
        _favorites.value = current
        saveFavorites(current)
    }

    fun isFavorite(assetId: String): Boolean {
        return _favorites.value.any { it.id == assetId }
    }

    suspend fun addRecent(asset: OnlineAsset) = withContext(Dispatchers.IO) {
        val current = _recents.value.filterNot { it.id == asset.id }.toMutableList()
        current.add(0, asset)
        val capped = current.take(50)
        _recents.value = capped
        saveRecents(capped)
    }

    private fun saveFavorites(list: List<OnlineAsset>) {
        runCatching {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            favoritesFile.writeText(array.toString(2))
        }
    }

    private fun saveRecents(list: List<OnlineAsset>) {
        runCatching {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            recentsFile.writeText(array.toString(2))
        }
    }

    suspend fun downloadAsset(asset: OnlineAsset, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val directFile = File(asset.contentUrl.removePrefix("file://"))
        if (directFile.exists() && directFile.length() > 0) {
            addRecent(asset)
            return@withContext directFile
        }

        val ext = when (asset.type) {
            AssetType.SVG -> "svg"
            AssetType.MODEL_3D -> "obj"
            AssetType.GIF, AssetType.STICKER -> "gif"
        }
        val targetFile = File(assetsCacheDir, "${asset.source.name.lowercase()}_${asset.id}.$ext")
        if (targetFile.exists() && targetFile.length() > 0) {
            addRecent(asset)
            return@withContext targetFile
        }

        var success = false

        // Check if bundled in assets
        if (asset.contentUrl.startsWith("asset://")) {
            val assetPath = asset.contentUrl.removePrefix("asset://")
            runCatching {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                success = targetFile.exists() && targetFile.length() > 0
            }
        }

        // Otherwise try downloading via HTTP / HTTPS
        if (!success && (asset.contentUrl.startsWith("http://") || asset.contentUrl.startsWith("https://"))) {
            runCatching {
                val url = URL(asset.contentUrl)
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "AbsentMotionPro/4.4.1")
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L
                            val totalLength = conn.contentLength.toLong()
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (totalLength > 0) {
                                    onProgress(totalRead.toFloat() / totalLength.toFloat())
                                }
                            }
                        }
                    }
                    success = targetFile.exists() && targetFile.length() > 100
                }
            }
        }

        // If still not available (e.g. offline or remote content unavailable), fallback to bundled assets by type
        if (!success) {
            runCatching {
                val fallbackFolder = when (asset.type) {
                    AssetType.SVG -> "pro_assets/svgs"
                    AssetType.MODEL_3D -> "pro_assets/models"
                    AssetType.STICKER -> "pro_assets/stickers"
                    AssetType.GIF -> "pro_assets/gifs"
                }
                val list = context.assets.list(fallbackFolder)
                val firstAsset = list?.firstOrNull()
                if (firstAsset != null) {
                    context.assets.open("$fallbackFolder/$firstAsset").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    success = targetFile.exists() && targetFile.length() > 0
                }
            }
        }

        addRecent(asset)
        targetFile
    }
}
