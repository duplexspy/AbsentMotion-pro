package app.absentmotion.pro.ai.model

object AIModelRegistry {

    private const val RIFE_URL = "https://raw.githubusercontent.com/TNTwise/rife-ncnn-vulkan/13338e38debe2e400b3eeecf6792312d01a692f9/models/"
    private const val TOHRUSKY_URL = "https://raw.githubusercontent.com/Tohrusky/realesrgan-ncnn-py/main/src/realesrgan_ncnn_py/models/"
    private const val HF_REALESRGAN_URL = "https://huggingface.co/aigchacker/realesrgan-x4/resolve/main/"
    private const val HF_DEPTH_URL = "https://huggingface.co/onnx-community/Depth-Anything-V2-Small/resolve/main/onnx/"
    private const val HF_MODNET_URL = "https://huggingface.co/TheEeeeLin/HivisionIDPhotos_matting/resolve/main/"
    private const val RNNOISE_URL = "https://raw.githubusercontent.com/xiph/rnnoise/master/src/"

    val models: List<AIModelDef> = listOf(
        // 1 & 2: RIFE Shared Instance for Frame Interpolation AND Twixtor
        AIModelDef(
            id = AIModelId.RIFE_426,
            name = "RIFE 4.26 Large",
            category = AIModelCategory.FRAME_INTERPOLATION,
            version = "4.26",
            repoUrl = "https://github.com/hzwer/Practical-RIFE",
            license = "MIT License",
            files = listOf(
                AIModelFile(
                    "flownet.param",
                    "${RIFE_URL}rife-v4.26-large/flownet.param",
                    35_833,
                    fallbackUrls = listOf("https://github.com/nihui/rife-ncnn-vulkan/raw/master/models/rife-v4.26/flownet.param")
                ),
                AIModelFile(
                    "flownet.bin",
                    "${RIFE_URL}rife-v4.26-large/flownet.bin",
                    11_483_660,
                    fallbackUrls = listOf("https://github.com/nihui/rife-ncnn-vulkan/raw/master/models/rife-v4.26/flownet.bin")
                )
            ),
            memoryReqBytes = 750L * 1024 * 1024,
            description = "High-precision optical flow frame interpolation. Powers both 60/120 FPS generation and Twixtor slow motion using a single shared model instance.",
            isSharedInstance = true,
            sharedFeatureNote = "Shared between Frame Interpolation and Twixtor AI Slow Motion"
        ),

        AIModelDef(
            id = AIModelId.RIFE_LITE,
            name = "RIFE Lite v4.25",
            category = AIModelCategory.FRAME_INTERPOLATION,
            version = "4.25-lite",
            repoUrl = "https://github.com/hzwer/Practical-RIFE",
            license = "MIT License",
            files = listOf(
                AIModelFile(
                    "flownet.param",
                    "${RIFE_URL}rife-v4.25-lite/flownet.param",
                    35_830,
                    fallbackUrls = listOf("https://github.com/nihui/rife-ncnn-vulkan/raw/master/models/rife-v4.25-lite/flownet.param")
                ),
                AIModelFile(
                    "flownet.bin",
                    "${RIFE_URL}rife-v4.25-lite/flownet.bin",
                    11_276_252,
                    fallbackUrls = listOf("https://github.com/nihui/rife-ncnn-vulkan/raw/master/models/rife-v4.25-lite/flownet.bin")
                )
            ),
            memoryReqBytes = 400L * 1024 * 1024,
            description = "Ultra-fast low-memory optical flow model optimized for mid-range and low-end Android devices under the 3 GB AI budget.",
            isSharedInstance = true,
            sharedFeatureNote = "Shared between Frame Interpolation and Twixtor AI Slow Motion"
        ),

        // 3: Real-ESRGAN x4plus (Real-Life Photos & Camera Video)
        AIModelDef(
            id = AIModelId.REAL_ESRGAN_X4PLUS,
            name = "Real-ESRGAN x4plus",
            category = AIModelCategory.SUPER_RESOLUTION,
            version = "0.2.5",
            repoUrl = "https://github.com/xinntao/Real-ESRGAN",
            license = "BSD 3-Clause",
            files = listOf(
                AIModelFile(
                    "realesrgan-x4plus.param",
                    "${TOHRUSKY_URL}realesrgan-x4plus.param",
                    116_029,
                    fallbackUrls = listOf("${HF_REALESRGAN_URL}realesrgan-x4plus.param")
                ),
                AIModelFile(
                    "realesrgan-x4plus.bin",
                    "${TOHRUSKY_URL}realesrgan-x4plus.bin",
                    33_424_520,
                    fallbackUrls = listOf("${HF_REALESRGAN_URL}realesrgan-x4plus.bin")
                )
            ),
            memoryReqBytes = 1100L * 1024 * 1024,
            description = "Specialized 4x super-resolution model for real-life camera photos, real-world textures, portraits, and general video restoration."
        ),

        // 4: Real-ESRGAN x4plus-anime (Anime & Illustrations)
        AIModelDef(
            id = AIModelId.REAL_ESRGAN_ANIME,
            name = "Real-ESRGAN x4plus-anime",
            category = AIModelCategory.SUPER_RESOLUTION,
            version = "0.2.5",
            repoUrl = "https://github.com/xinntao/Real-ESRGAN",
            license = "BSD 3-Clause",
            files = listOf(
                AIModelFile(
                    "realesrgan-x4plus-anime.param",
                    "${TOHRUSKY_URL}realesrgan-x4plus-anime.param",
                    30_290,
                    fallbackUrls = listOf("${HF_REALESRGAN_URL}realesrgan-x4plus-anime.param")
                ),
                AIModelFile(
                    "realesrgan-x4plus-anime.bin",
                    "${TOHRUSKY_URL}realesrgan-x4plus-anime.bin",
                    8_943_500,
                    fallbackUrls = listOf("${HF_REALESRGAN_URL}realesrgan-x4plus-anime.bin")
                )
            ),
            memoryReqBytes = 600L * 1024 * 1024,
            description = "Specialized for manga illustrations, anime drawings, and vector-style artwork. Sharpens line art while preserving flat color fills."
        ),

        // 5: Real-ESRGAN AnimeVideo-v3 (Anime Video)
        AIModelDef(
            id = AIModelId.REAL_ESRGAN_ANIME_VIDEO,
            name = "Real-ESRGAN AnimeVideo-v3",
            category = AIModelCategory.SUPER_RESOLUTION,
            version = "v3",
            repoUrl = "https://github.com/xinntao/Real-ESRGAN",
            license = "BSD 3-Clause",
            files = listOf(
                AIModelFile(
                    "realesr-animevideov3-x4.param",
                    "${TOHRUSKY_URL}realesr-animevideov3-x4.param",
                    3_077,
                    fallbackUrls = listOf("https://raw.githubusercontent.com/xinntao/Real-ESRGAN-ncnn-vulkan/master/models/realesr-animevideov3-x4.param")
                ),
                AIModelFile(
                    "realesr-animevideov3-x4.bin",
                    "${TOHRUSKY_URL}realesr-animevideov3-x4.bin",
                    1_247_368,
                    fallbackUrls = listOf("https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-animevideov3.pth")
                )
            ),
            memoryReqBytes = 350L * 1024 * 1024,
            description = "Lightweight high-efficiency model for animated video series, maintaining clean animated lines without temporal flicker."
        ),

        // 6: Real-ESRGAN Compact 2x (Mobile Fast Upscaler)
        AIModelDef(
            id = AIModelId.REAL_ESRGAN_COMPACT,
            name = "Real-ESRGAN Compact 2x",
            category = AIModelCategory.SUPER_RESOLUTION,
            version = "v0.3.0",
            repoUrl = "https://github.com/xinntao/Real-ESRGAN",
            license = "BSD 3-Clause",
            files = listOf(
                AIModelFile(
                    "realesr-general-x4v3.param",
                    "${TOHRUSKY_URL}realesr-general-x4v3.param",
                    30_290,
                    fallbackUrls = listOf("https://raw.githubusercontent.com/xinntao/Real-ESRGAN-ncnn-vulkan/master/models/realesr-general-x4v3.param")
                ),
                AIModelFile(
                    "realesr-general-x4v3.bin",
                    "${TOHRUSKY_URL}realesr-general-x4v3.bin",
                    4_102_368,
                    fallbackUrls = listOf("https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth")
                )
            ),
            memoryReqBytes = 280L * 1024 * 1024,
            description = "Lightweight and ultra-fast compact super-resolution network optimized for real-time mobile upscaling and lower GPU memory devices."
        ),

        // 7: RNNoise Speech Denoising
        AIModelDef(
            id = AIModelId.RNNOISE,
            name = "RNNoise Deep Speech Denoiser",
            category = AIModelCategory.AUDIO_ENHANCE,
            version = "0.4.1",
            repoUrl = "https://github.com/xiph/rnnoise",
            license = "BSD 3-Clause",
            files = listOf(
                AIModelFile(
                    "rnnoise_weights.bin",
                    "${RNNOISE_URL}rnn_data.c",
                    425_119,
                    fallbackUrls = listOf("https://gitlab.xiph.org/xiph/rnnoise/-/raw/master/src/rnn_data.c")
                )
            ),
            memoryReqBytes = 50L * 1024 * 1024,
            description = "Recurrent Neural Network for speech noise reduction, filtering stationary background noise while keeping voices natural."
        ),

        // 8: Depth Anything V2 Small
        AIModelDef(
            id = AIModelId.DEPTH_ANYTHING_V2,
            name = "Depth Anything V2 Small",
            category = AIModelCategory.DEPTH_ESTIMATION,
            version = "v2.0-small",
            repoUrl = "https://github.com/DepthAnything/Depth-Anything-V2",
            license = "Apache 2.0",
            files = listOf(
                AIModelFile(
                    "depth_anything_v2_vits.onnx",
                    "${HF_DEPTH_URL}model.onnx",
                    99_060_839,
                    fallbackUrls = listOf("https://huggingface.co/fabio-sim/Depth-Anything-ONNX/resolve/main/depth_anything_vits14.onnx")
                )
            ),
            memoryReqBytes = 550L * 1024 * 1024,
            description = "Zero-shot monocular metric 3D depth estimation model producing detailed spatial disparity geometry and edge-crisp contours."
        ),

        // 9: MiDaS v2.1 Small Depth
        AIModelDef(
            id = AIModelId.MIDAS_V21,
            name = "MiDaS v2.1 Small Depth",
            category = AIModelCategory.DEPTH_ESTIMATION,
            version = "v2.1-small",
            repoUrl = "https://github.com/isl-org/MiDaS",
            license = "MIT License",
            files = listOf(
                AIModelFile(
                    "midas_v21_small.onnx",
                    "https://github.com/isl-org/MiDaS/releases/download/v2_1/model-small.onnx",
                    21_200_000,
                    fallbackUrls = listOf("https://huggingface.co/intel-isl/MiDaS/resolve/main/model-small.onnx")
                )
            ),
            memoryReqBytes = 300L * 1024 * 1024,
            description = "Fast lightweight monocular depth estimation network suitable for real-time 3D parallax generation on mobile devices."
        ),

        // 10: MODNet Background Matting
        AIModelDef(
            id = AIModelId.MODNET,
            name = "MODNet Portrait & Video Matting",
            category = AIModelCategory.MATTING,
            version = "v1.0",
            repoUrl = "https://github.com/ZHKKKe/MODNet",
            license = "Creative Commons Non-Commercial",
            files = listOf(
                AIModelFile(
                    "modnet_photographic_portrait_matting.onnx",
                    "${HF_MODNET_URL}modnet_photographic_portrait_matting.onnx",
                    25_888_640,
                    fallbackUrls = listOf("https://github.com/yakhyo/modnet/raw/main/weights/modnet_photographic_portrait_matting.onnx")
                )
            ),
            memoryReqBytes = 600L * 1024 * 1024,
            description = "Trimap-free real-time portrait matting network capable of extracting fine hair strands and transparent foreground elements."
        ),

        // 11: BRIA RMBG 1.4 Background Removal
        AIModelDef(
            id = AIModelId.RMBG_14,
            name = "BRIA RMBG 1.4 Matting",
            category = AIModelCategory.MATTING,
            version = "1.4",
            repoUrl = "https://huggingface.co/briaai/RMBG-1.4",
            license = "bria-rmbg-1.4",
            files = listOf(
                AIModelFile(
                    "rmbg_14.onnx",
                    "https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model.onnx",
                    43_820_000,
                    fallbackUrls = listOf("https://huggingface.co/silveroxides/RMBG-1.4-onnx/resolve/main/model.onnx")
                )
            ),
            memoryReqBytes = 450L * 1024 * 1024,
            description = "State-of-the-art salient object and background separation model trained on over 12,000 high-resolution studio images."
        ),

        // 12: DeepLabV3+ Semantic Segmentation
        AIModelDef(
            id = AIModelId.DEEPLAB_V3,
            name = "DeepLabV3+ Segmentation",
            category = AIModelCategory.MATTING,
            version = "v3+",
            repoUrl = "https://github.com/tensorflow/models/tree/master/research/deeplab",
            license = "Apache 2.0",
            files = listOf(
                AIModelFile(
                    "deeplabv3plus_mobilenet.onnx",
                    "https://huggingface.co/qualcomm/DeepLabV3-Plus-MobileNet/resolve/main/DeepLabV3Plus.onnx",
                    19_500_000,
                    fallbackUrls = listOf("https://raw.githubusercontent.com/bonndev/deeplabv3-onnx/master/deeplabv3.onnx")
                )
            ),
            memoryReqBytes = 350L * 1024 * 1024,
            description = "Multi-class semantic segmentation isolating people, objects, and backgrounds with atrous spatial pyramid pooling."
        ),

        // 9: OpenCV Optical Flow Video Stabilizer
        AIModelDef(
            id = AIModelId.OPENCV_OPTICAL_FLOW,
            name = "OpenCV Optical Flow Stabilizer",
            category = AIModelCategory.VIDEO_STABILIZATION,
            version = "4.10",
            repoUrl = "https://github.com/opencv/opencv",
            license = "Apache 2.0",
            files = emptyList(), // Built-in C++ native DSP engine
            memoryReqBytes = 250L * 1024 * 1024,
            description = "Sub-pixel feature tracking and multi-axis trajectory smoothing with auto-crop and boundary filling."
        ),

        // 10: OpenCV SSIM Dead Frame Remover
        AIModelDef(
            id = AIModelId.OPENCV_SSIM,
            name = "OpenCV SSIM Duplicate Detector",
            category = AIModelCategory.FRAME_OPTIMIZATION,
            version = "4.10",
            repoUrl = "https://github.com/opencv/opencv",
            license = "Apache 2.0",
            files = emptyList(), // Built-in C++ native DSP engine
            memoryReqBytes = 150L * 1024 * 1024,
            description = "Structural Similarity (SSIM) and perceptual hashing to detect and remove frozen or dead duplicate frames."
        )
    )

    fun get(id: AIModelId): AIModelDef = models.first { it.id == id }

    fun forCategory(category: AIModelCategory): List<AIModelDef> = models.filter { it.category == category }
}
