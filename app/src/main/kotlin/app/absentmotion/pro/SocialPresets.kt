package app.absentmotion.pro

import app.absentmotion.export.ExportSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SocialPreset(
    val name: String,
    val aspectLabel: String,
    val width: Int,
    val height: Int,
    val defaultFps: Int = 30,
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

enum class QueueItemStatus {
    Pending,
    Exporting,
    Completed,
    Failed,
}

data class QueuedExportItem(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val projectTitle: String,
    val settings: ExportSettings,
    val status: QueueItemStatus = QueueItemStatus.Pending,
    val progress: Float = 0f,
    val error: String? = null,
)

object SocialPresets {
    val presets = listOf(
        SocialPreset("TikTok / Reels", "9:16", 1080, 1920, 30),
        SocialPreset("Instagram Square", "1:1", 1080, 1080, 30),
        SocialPreset("Instagram Portrait", "4:5", 1080, 1350, 30),
        SocialPreset("YouTube / Widescreen", "16:9", 1920, 1080, 60),
        SocialPreset("Cinema Ultrawide", "21:9", 2560, 1080, 24),
    )
}

object ExportQueue {
    private val _items = MutableStateFlow<List<QueuedExportItem>>(emptyList())
    val items: StateFlow<List<QueuedExportItem>> = _items.asStateFlow()

    fun enqueue(projectId: String, projectTitle: String, settings: ExportSettings): QueuedExportItem {
        val item = QueuedExportItem(
            projectId = projectId,
            projectTitle = projectTitle,
            settings = settings
        )
        _items.value = _items.value + item
        return item
    }

    fun updateStatus(id: String, status: QueueItemStatus, progress: Float = 0f, error: String? = null) {
        _items.value = _items.value.map { item ->
            if (item.id == id) item.copy(status = status, progress = progress, error = error)
            else item
        }
    }

    fun remove(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
    }

    fun clearCompleted() {
        _items.value = _items.value.filterNot { it.status == QueueItemStatus.Completed }
    }
}
