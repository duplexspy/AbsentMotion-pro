package app.absentmotion.pro.labs

import androidx.compose.ui.graphics.Color
import java.io.File

enum class AmzLabCategory {
    AI,
    VIDEO,
    AUDIO,
    METADATA
}

enum class AmzLabTool(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: AmzLabCategory,
    val isAi: Boolean = false,
    val subfolder: String
) {
    DepthMap(
        "depth_map",
        "Depth Map Generator",
        "AI depth map from a picture or a video, made on this phone (MiDaS), with a live preview.",
        AmzLabCategory.AI,
        true,
        "AmzDepth"
    ),
    BackgroundRemover(
        "bg_remover",
        "Background Remover",
        "AI cut-out of a person or any object from a picture or a video. Transparent PNG, solid color video or matte.",
        AmzLabCategory.AI,
        true,
        "AmzBgRemove"
    ),
    AiUpscale(
        "ai_upscale",
        "AI Upscale",
        "Sharper and bigger pictures and videos (Real-ESRGAN 2× / 4×), with a Before / After preview.",
        AmzLabCategory.AI,
        true,
        "AmzUpscale"
    ),
    TikTokAntiCompress(
        "tiktok_anti_compress",
        "TikTok Anti Compress",
        "Rewrites only the metadata of an MP4 / MOV video, zero quality loss: color tags, clean tags + fast start, iPhone device tag, fresh date, resolution and bitrate tags.",
        AmzLabCategory.METADATA,
        false,
        "AmzAntiCompress"
    ),
    Twixtor(
        "twixtor",
        "Twixtor",
        "Optical flow slow motion (10–75%) or FPS Boost to 24 / 30 / 60 fps: Motion, Blend or Nearest frames, flow quality, audio pitch kept / down / mute.",
        AmzLabCategory.VIDEO,
        false,
        "AmzTwixtor"
    ),
    DeadFrameRemover(
        "dead_frame_remover",
        "Dead Frame Remover",
        "Finds duplicate frames in a video (lag, stutter, 24 fps inside 60 fps) and exports it with only the new frames. Same length, audio in sync.",
        AmzLabCategory.VIDEO,
        false,
        "AmzDeadFrame"
    ),
    Stabilizer(
        "stabilizer",
        "Stabilizer",
        "Removes camera shake from a video (like Warp Stabilizer): Smooth Motion or No Motion, smoothness, rolling shutter fix, auto crop. Audio kept.",
        AmzLabCategory.VIDEO,
        false,
        "AmzStabilizer"
    ),
    AudioConverter(
        "audio_converter",
        "Audio Converter",
        "Any audio file or the sound of a video to M4A (AAC), WAV or FLAC: bitrate, sample rate, channels, trim, volume and fades.",
        AmzLabCategory.AUDIO,
        false,
        "AmzAudio"
    ),
    AiFrameInterpolation(
        "ai_frame_interpolation",
        "AI Frame Interpolation",
        "AI (RIFE) makes new frames between the real ones: 2×, 3×, 4× or 60 / 120 fps, smoother motion with the same length. Model 10 MB downloaded once.",
        AmzLabCategory.AI,
        true,
        "AmzInterpolation"
    ),
    AudioDenoise(
        "audio_denoise",
        "Audio Denoise",
        "Removes background noise (fan, wind, traffic, hum, room noise) from voice in audio files and videos, made on this phone (RNNoise).",
        AmzLabCategory.AUDIO,
        true,
        "AmzDenoise"
    ),
    ReverseVideo(
        "reverse_video",
        "Reverse Video",
        "Plays any video backwards, the sound reversed too. Pick it from your files, the result is saved to Download/AfterMotionZ/AmzReverse.",
        AmzLabCategory.VIDEO,
        false,
        "AmzReverse"
    ),
    ConvertVideo(
        "convert_video",
        "Convert Video",
        "Any video to a new MP4: size, frame rate, H.264 / H.265 and bitrate, sound kept. Pick it from your files, the result is saved to Download/AfterMotionZ/AmzConvert.",
        AmzLabCategory.VIDEO,
        false,
        "AmzConvert"
    ),
    MorphStudio(
        "morph_studio",
        "Morph Studio (Shape & Image)",
        "Vector shape morphing & dual-mesh image morphing. Seamlessly transition between vector shapes or morph two photos/faces with interactive control grids.",
        AmzLabCategory.VIDEO,
        false,
        "AmzMorph"
    );

    fun destinationFolder(): File {
        val baseDir = File("/storage/emulated/0/Download/AfterMotionZ", subfolder)
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }
}

data class AntiCompressConfig(
    val iphoneTag: Boolean = true,
    val fastStart: Boolean = true,
    val colorBt709: Boolean = true,
    val cleanTags: Boolean = true,
    val freshDate: Boolean = true
)

data class TwixtorConfig(
    val speedPercent: Int = 50,
    val fpsBoost: Int = 60,
    val motionMode: String = "Motion",
    val flowQuality: String = "High",
    val audioPitch: String = "Kept",
    val engine: String = "OpenCV DIS Optical Flow"
)

data class MorphStudioConfig(
    val mode: String = "Shape Morph",
    val durationMs: Long = 1500L,
    val easing: String = "EaseInOut",
    val meshGrid: Int = 8,
    val loop: Boolean = true
)

data class DeadFrameConfig(
    val thresholdPercent: Float = 0.98f,
    val syncAudio: Boolean = true
)

data class StabilizerConfig(
    val mode: String = "Smooth Motion",
    val smoothness: Int = 50,
    val rollingShutterFix: Boolean = true,
    val autoCrop: Boolean = true
)

data class AudioConvertConfig(
    val format: String = "M4A",
    val bitrateKbps: Int = 256,
    val sampleRateHz: Int = 44100,
    val channels: Int = 2,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val volumePercent: Int = 100,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

data class DepthMapConfig(
    val model: String = "MiDaS",
    val colormap: String = "Greyscale",
    val livePreview: Boolean = true
) {
    val palette: String get() = colormap
}

data class BgRemoverConfig(
    val outputFormat: String = "Transparent PNG",
    val solidColor: Color = Color.Green,
    val edgeFeather: Int = 2
)

data class AiUpscaleConfig(
    val scaleFactor: Int = 4,
    val modelName: String = "Real-ESRGAN",
    val denoiseStrength: Float = 0.5f
)

data class FrameInterpolationConfig(
    val targetMultiplier: Int = 2,
    val targetFps: Int = 60,
    val model: String = "RIFE"
)

data class AudioDenoiseConfig(
    val model: String = "RNNoise",
    val noiseType: String = "Voice",
    val strength: Int = 85
) {
    val profile: String get() = noiseType
}

data class ReverseConfig(
    val reverseAudio: Boolean = true
)

data class ConvertConfig(
    val resolution: String = "1080p",
    val fps: Int = 30,
    val codec: String = "H.264",
    val bitrateMbps: Int = 12
)

data class AmzToolResult(
    val success: Boolean,
    val outputFile: File?,
    val durationMs: Long = 0L,
    val message: String = "",
    val error: String? = null
)
