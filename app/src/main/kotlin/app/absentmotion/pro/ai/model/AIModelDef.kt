package app.absentmotion.pro.ai.model

enum class AIModelId(val displayName: String) {
    RIFE_426("RIFE 4.26 Large"),
    RIFE_LITE("RIFE Lite v4.25"),
    REAL_ESRGAN_X4PLUS("Real-ESRGAN x4plus"),
    REAL_ESRGAN_ANIME("Real-ESRGAN x4plus-anime"),
    REAL_ESRGAN_ANIME_VIDEO("Real-ESRGAN AnimeVideo-v3"),
    REAL_ESRGAN_COMPACT("Real-ESRGAN Compact 2x"),
    RNNOISE("RNNoise Deep Speech"),
    DEPTH_ANYTHING_V2("Depth Anything V2 Small"),
    MIDAS_V21("MiDaS v2.1 Small Depth"),
    MODNET("MODNet Matting"),
    RMBG_14("BRIA RMBG 1.4 Matting"),
    DEEPLAB_V3("DeepLabV3+ Segmentation"),
    OPENCV_OPTICAL_FLOW("OpenCV Optical Flow"),
    OPENCV_SSIM("OpenCV SSIM Duplicate Detector")
}

enum class AIModelCategory(val displayName: String) {
    FRAME_INTERPOLATION("Frame Interpolation & Slow Motion"),
    SUPER_RESOLUTION("Intelligent Upscaling"),
    AUDIO_ENHANCE("Audio Denoising"),
    DEPTH_ESTIMATION("3D Depth Generation"),
    MATTING("Background Removal"),
    VIDEO_STABILIZATION("Video Stabilization"),
    FRAME_OPTIMIZATION("Dead Frame Removal")
}

data class AIModelFile(
    val filename: String,
    val downloadUrl: String,
    val bytes: Long,
    val sha256: String = "",
    val fallbackUrls: List<String> = emptyList()
)

data class AIModelDef(
    val id: AIModelId,
    val name: String,
    val category: AIModelCategory,
    val version: String,
    val repoUrl: String,
    val license: String,
    val files: List<AIModelFile>,
    val memoryReqBytes: Long,
    val description: String,
    val supportedDevices: String = "All Android 9.0+ devices (CPU / Vulkan GPU)",
    val isSharedInstance: Boolean = false,
    val sharedFeatureNote: String = ""
) {
    val totalBytes: Long get() = files.sumOf { it.bytes }
    val sizeFormatted: String get() {
        val mb = totalBytes.toDouble() / (1024 * 1024)
        return if (mb >= 1.0) String.format("%.1f MB", mb) else "${totalBytes / 1024} KB"
    }
}
