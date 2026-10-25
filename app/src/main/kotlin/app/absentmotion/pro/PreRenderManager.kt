package app.absentmotion.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.absentmotion.editor.EditorPanel
import app.absentmotion.editor.EditorState
import app.absentmotion.model.Layer
import app.absentmotion.model.Scene
import app.absentmotion.model.XmlNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class PreRenderProgress(
    val isRendering: Boolean = false,
    val isPaused: Boolean = false,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val layerLabel: String = "",
) {
    val fraction: Float get() = if (totalFrames > 0) currentFrame.toFloat() / totalFrames.toFloat() else 0f
}

object PreRenderKey {
    const val ENGINE_VERSION = "2.0-pro"

    fun computeFrameKey(
        scene: Scene,
        layer: Layer,
        frame: Int,
        width: Int = scene.width,
        height: Int = scene.height,
        fps: Int = scene.fps,
        quality: Int = 100,
    ): String {
        val timeMs = scene.frameStart(frame)
        val evaluated = layer.at(timeMs, fps)
        val sb = StringBuilder()
        sb.append("v=").append(ENGINE_VERSION).append(';')
        sb.append("res=").append(width).append('x').append(height).append('@').append(fps).append(';')
        sb.append("bg=").append(scene.bgColor).append(';')
        sb.append("id=").append(evaluated.id).append(';').append("kind=").append(evaluated.kind).append(';')
        sb.append("t=").append(timeMs).append(";range=").append(layer.startTime).append("..").append(layer.endTime).append(';')
        sb.append("node=").append(evaluated.node.toXml()).append(';')
        sb.append("q=").append(quality)

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object PreRenderStorage {
    const val DEFAULT_MAX_CACHE_BYTES = 1024L * 1024L * 1024L

    fun getProjectDir(context: Context, projectId: String): File {
        val dir = File(context.cacheDir, "prerender/$projectId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getFrameFile(context: Context, projectId: String, layerId: Long, frame: Int, key: String): File {
        val dir = getProjectDir(context, projectId)
        return File(dir, "frame_${layerId}_${frame}_${key.take(12)}.png")
    }

    fun hasFrame(context: Context, projectId: String, layerId: Long, frame: Int, key: String): Boolean {
        return getFrameFile(context, projectId, layerId, frame, key).exists()
    }

    fun saveFrame(context: Context, projectId: String, layerId: Long, frame: Int, key: String, bitmap: Bitmap, maxBytes: Long = DEFAULT_MAX_CACHE_BYTES): Boolean {
        val file = getFrameFile(context, projectId, layerId, frame, key)
        val temp = File(file.parentFile, "${file.name}.tmp")
        return try {
            FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (temp.renameTo(file)) {
                enforceCap(context, projectId, maxBytes)
                true
            } else {
                temp.delete()
                false
            }
        } catch (_: Throwable) {
            temp.delete()
            false
        }
    }

    fun loadFrame(context: Context, projectId: String, layerId: Long, frame: Int, key: String): Bitmap? {
        val file = getFrameFile(context, projectId, layerId, frame, key)
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun enforceCap(context: Context, projectId: String, maxBytes: Long) {
        val dir = getProjectDir(context, projectId)
        val files = dir.listFiles()?.filter { it.isFile && it.name.startsWith("frame_") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val sorted = files.sortedBy { it.lastModified() }
        for (f in sorted) {
            val len = f.length()
            if (f.delete()) {
                total -= len
                if (total <= maxBytes) break
            }
        }
    }

    fun projectCacheBytes(context: Context, projectId: String): Long {
        val dir = File(context.cacheDir, "prerender/$projectId")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun totalCacheBytes(context: Context): Long {
        val root = File(context.cacheDir, "prerender")
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearProject(context: Context, projectId: String) {
        val dir = File(context.cacheDir, "prerender/$projectId")
        if (dir.exists()) dir.deleteRecursively()
    }

    fun clearAll(context: Context) {
        val root = File(context.cacheDir, "prerender")
        if (root.exists()) root.deleteRecursively()
    }
}

class PreRenderValidityMap {
    private val frameKeys = ConcurrentHashMap<Long, ConcurrentHashMap<Int, String>>()

    fun recordValid(layerId: Long, frame: Int, key: String) {
        frameKeys.computeIfAbsent(layerId) { ConcurrentHashMap() }[frame] = key
    }

    fun isValid(layerId: Long, frame: Int, expectedKey: String): Boolean {
        return frameKeys[layerId]?.get(frame) == expectedKey
    }

    fun invalidate(layerId: Long, frame: Int) {
        frameKeys[layerId]?.remove(frame)
    }

    fun invalidateLayer(layerId: Long) {
        frameKeys.remove(layerId)
    }

    fun invalidateAll() {
        frameKeys.clear()
    }

    fun getValidFrames(layerId: Long): Set<Int> {
        return frameKeys[layerId]?.keys ?: emptySet()
    }

    fun validateScene(scene: Scene, layerId: Long) {
        val layer = scene.layer(layerId) ?: run {
            invalidateLayer(layerId)
            return
        }
        val map = frameKeys[layerId] ?: return
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val frame = entry.key
            val currentKey = PreRenderKey.computeFrameKey(scene, layer, frame)
            if (currentKey != entry.value) {
                iterator.remove()
            }
        }
    }
}

object PreRenderManager {
    const val MAX_IN_MEMORY_FRAMES = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var renderJob: Job? = null
    @Volatile private var pauseUntilTime: Long = 0L

    val validityMap = PreRenderValidityMap()

    private val memoryFrameCache = object : LruCache<String, Bitmap>(MAX_IN_MEMORY_FRAMES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val progressState = MutableStateFlow(PreRenderProgress())
    val progress = progressState.asStateFlow()

    fun onUserInteraction() {
        pauseUntilTime = System.currentTimeMillis() + 500L
    }

    fun isUserInteracting(): Boolean {
        return System.currentTimeMillis() < pauseUntilTime
    }

    fun getCachedFrame(
        context: Context,
        projectId: String,
        scene: Scene,
        layer: Layer,
        frame: Int,
        width: Int = scene.width,
        height: Int = scene.height,
    ): Bitmap? {
        if (!ProFeatures.isEnabled(ProFeatures.preRender)) return null
        val key = PreRenderKey.computeFrameKey(scene, layer, frame, width, height)
        val memKey = "${projectId}_${layer.id}_$frame"

        memoryFrameCache.get(memKey)?.let { return it }

        if (!PreRenderStorage.hasFrame(context, projectId, layer.id, frame, key)) {
            validityMap.invalidate(layer.id, frame)
            return null
        }

        val loaded = PreRenderStorage.loadFrame(context, projectId, layer.id, frame, key)
        if (loaded != null) {
            memoryFrameCache.put(memKey, loaded)
            validityMap.recordValid(layer.id, frame, key)
        }
        return loaded
    }

    fun hasCachedFrame(
        context: Context,
        projectId: String,
        scene: Scene,
        layer: Layer,
        frame: Int,
        width: Int = scene.width,
        height: Int = scene.height,
    ): Boolean {
        if (!ProFeatures.isEnabled(ProFeatures.preRender)) return false
        val key = PreRenderKey.computeFrameKey(scene, layer, frame, width, height)
        val has = PreRenderStorage.hasFrame(context, projectId, layer.id, frame, key)
        if (has) validityMap.recordValid(layer.id, frame, key)
        else validityMap.invalidate(layer.id, frame)
        return has
    }

    fun startPreRenderRange(
        context: Context,
        state: EditorState,
        layer: Layer,
        startFrame: Int,
        endFrame: Int,
    ) {
        if (!ProFeatures.isEnabled(ProFeatures.preRender)) return
        renderJob?.cancel()

        val total = (endFrame - startFrame + 1).coerceAtLeast(1)
        progressState.value = PreRenderProgress(
            isRendering = true,
            currentFrame = 0,
            totalFrames = total,
            layerLabel = layer.label,
        )

        renderJob = scope.launch {
            val scene = state.scene
            val projectId = state.projectId
            for (f in startFrame..endFrame) {
                while (isUserInteracting()) {
                    progressState.value = progressState.value.copy(isPaused = true)
                    delay(50)
                }
                progressState.value = progressState.value.copy(isPaused = false)

                val key = PreRenderKey.computeFrameKey(scene, layer, f)
                if (!PreRenderStorage.hasFrame(context, projectId, layer.id, f, key)) {
                    val timeMs = scene.frameStart(f)
                    val bmp = renderLayerStill(context, state, layer, timeMs)
                    if (bmp != null) {
                        PreRenderStorage.saveFrame(context, projectId, layer.id, f, key, bmp)
                        validityMap.recordValid(layer.id, f, key)
                    }
                } else {
                    validityMap.recordValid(layer.id, f, key)
                }

                progressState.value = progressState.value.copy(
                    currentFrame = f - startFrame + 1
                )
                delay(10)
            }
            progressState.value = PreRenderProgress()
        }
    }

    fun cancelPreRender() {
        renderJob?.cancel()
        renderJob = null
        progressState.value = PreRenderProgress()
    }

    fun renderAndReplace(
        context: Context,
        state: EditorState,
        layer: Layer,
    ) {
        if (!ProFeatures.isEnabled(ProFeatures.preRender)) return
        val scene = state.scene
        val startFrame = scene.firstShownFrame(layer.startTime)
        val endFrame = scene.lastShownFrame(layer.endTime)

        scope.launch {
            val framesDir = File(PreRenderStorage.getProjectDir(context, state.projectId), "baked_${layer.id}")
            if (!framesDir.exists()) framesDir.mkdirs()

            var firstFrameFile: File? = null
            for (f in startFrame..endFrame) {
                val timeMs = scene.frameStart(f)
                val bmp = renderLayerStill(context, state, layer, timeMs)
                if (bmp != null) {
                    val frameFile = File(framesDir, "f_%05d.png".format(f))
                    FileOutputStream(frameFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (firstFrameFile == null) firstFrameFile = frameFile
                }
            }

            val mediaUri = firstFrameFile?.absolutePath ?: return@launch
            val newId = Layer.newId()
            val bakedNode = XmlNode(
                "shape",
                linkedMapOf(
                    "id" to "$newId",
                    "label" to "${layer.label} (Baked)",
                    "startTime" to "${layer.startTime}",
                    "endTime" to "${layer.endTime}",
                    "fillType" to "media",
                    "fillImage" to mediaUri,
                    "mediaFillMode" to "Fill",
                    "s" to ".rect",
                ),
                listOf(
                    XmlNode(
                        "property",
                        mapOf("name" to "size", "type" to "vec2", "value" to "${scene.width},${scene.height}")
                    ),
                    XmlNode(
                        "transform",
                        emptyMap(),
                        listOf(
                            XmlNode("location", mapOf("value" to "${scene.width / 2f},${scene.height / 2f},0")),
                            XmlNode("scale", mapOf("value" to "1,1,1")),
                        )
                    )
                )
            )
            val bakedLayer = Layer(bakedNode)

            withContext(Dispatchers.Main) {
                state.edit { s ->
                    val hiddenOriginal = s.update(layer.id) { it.copy(node = it.node.with("hidden", "true")) }
                    hiddenOriginal.addAbove(bakedLayer, layer.id)
                }
                state.selectedId = newId
                state.panel = EditorPanel.Tools
            }
        }
    }

    private suspend fun renderLayerStill(
        context: Context,
        state: EditorState,
        layer: Layer,
        timeMs: Int,
    ): Bitmap? = withContext(Dispatchers.Default) {
        val isolatedScene = Scene.create(
            "prerender_sub",
            state.scene.width,
            state.scene.height,
            state.scene.fps,
            0
        ).add(layer.movedTo(layer.startTime))

        val tempFile = File(context.cacheDir, "prerender_temp_${System.currentTimeMillis()}.png")
        val success = state.engine.stillPng(
            isolatedScene.toXml(),
            timeMs,
            state.scene.width,
            state.scene.height,
            tempFile
        )
        if (success && tempFile.exists()) {
            val bmp = BitmapFactory.decodeFile(tempFile.absolutePath)
            tempFile.delete()
            bmp
        } else {
            tempFile.delete()
            null
        }
    }
}
