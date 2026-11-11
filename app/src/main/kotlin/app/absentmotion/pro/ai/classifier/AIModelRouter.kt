package app.absentmotion.pro.ai.classifier

import app.absentmotion.pro.ai.model.AIModelId

enum class ModelRoutingMode(val label: String) {
    AUTO("Intelligent Auto-Detect"),
    FORCE_REAL_LIFE("Force Real-Life (x4plus)"),
    FORCE_ANIME_IMAGE("Force Anime Art (x4plus-anime)"),
    FORCE_ANIME_VIDEO("Force Anime Video (AnimeVideo-v3)")
}

data class RoutingDecision(
    val selectedModel: AIModelId,
    val explanation: String,
    val isAutoRouted: Boolean,
    val classification: ClassificationResult?
)

object AIModelRouter {

    fun routeUpscalingModel(
        mode: ModelRoutingMode = ModelRoutingMode.AUTO,
        classification: ClassificationResult? = null,
        isVideo: Boolean = false
    ): RoutingDecision {
        return when (mode) {
            ModelRoutingMode.FORCE_REAL_LIFE -> RoutingDecision(
                selectedModel = AIModelId.REAL_ESRGAN_X4PLUS,
                explanation = "Manually set to Real-ESRGAN x4plus (Optimized for real-life camera photos and textures)",
                isAutoRouted = false,
                classification = classification
            )
            ModelRoutingMode.FORCE_ANIME_IMAGE -> RoutingDecision(
                selectedModel = AIModelId.REAL_ESRGAN_ANIME,
                explanation = "Manually set to Real-ESRGAN x4plus-anime (Preserves clean line art and flat cel shading)",
                isAutoRouted = false,
                classification = classification
            )
            ModelRoutingMode.FORCE_ANIME_VIDEO -> RoutingDecision(
                selectedModel = AIModelId.REAL_ESRGAN_ANIME_VIDEO,
                explanation = "Manually set to Real-ESRGAN AnimeVideo-v3 (Optimized for temporal anime video consistency)",
                isAutoRouted = false,
                classification = classification
            )
            ModelRoutingMode.AUTO -> {
                if (classification == null) {
                    // Default fallback
                    val defaultModel = if (isVideo) AIModelId.REAL_ESRGAN_ANIME_VIDEO else AIModelId.REAL_ESRGAN_X4PLUS
                    RoutingDecision(
                        selectedModel = defaultModel,
                        explanation = "Default automatic model selected",
                        isAutoRouted = true,
                        classification = null
                    )
                } else {
                    when (classification.detectedType) {
                        ContentType.ANIME_ILLUSTRATION -> RoutingDecision(
                            selectedModel = AIModelId.REAL_ESRGAN_ANIME,
                            explanation = "Content-Aware Router: Detected Anime/Illustration (${(classification.animeConfidence * 100).toInt()}% conf). Routing to Real-ESRGAN x4plus-anime.",
                            isAutoRouted = true,
                            classification = classification
                        )
                        ContentType.ANIME_VIDEO -> RoutingDecision(
                            selectedModel = AIModelId.REAL_ESRGAN_ANIME_VIDEO,
                            explanation = "Content-Aware Router: Detected Animated Video (${(classification.animeConfidence * 100).toInt()}% conf). Routing to Real-ESRGAN AnimeVideo-v3.",
                            isAutoRouted = true,
                            classification = classification
                        )
                        ContentType.REAL_LIFE_PHOTO, ContentType.REAL_LIFE_VIDEO -> RoutingDecision(
                            selectedModel = AIModelId.REAL_ESRGAN_X4PLUS,
                            explanation = "Content-Aware Router: Detected Real-Life imagery (${((1f - classification.animeConfidence) * 100).toInt()}% conf). Routing to Real-ESRGAN x4plus.",
                            isAutoRouted = true,
                            classification = classification
                        )
                        ContentType.GENERAL_MIXED -> RoutingDecision(
                            selectedModel = if (isVideo) AIModelId.REAL_ESRGAN_ANIME_VIDEO else AIModelId.REAL_ESRGAN_X4PLUS,
                            explanation = "Content-Aware Router: Mixed/Hybrid content. Selected balanced general model.",
                            isAutoRouted = true,
                            classification = classification
                        )
                    }
                }
            }
        }
    }
}
