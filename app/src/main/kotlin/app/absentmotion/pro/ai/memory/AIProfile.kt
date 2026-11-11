package app.absentmotion.pro.ai.memory

enum class AIProfileType(val displayName: String, val defaultBudgetBytes: Long) {
    LITE("Lite (3 GB Budget)", 3L * 1024 * 1024 * 1024),
    PRO("Pro (5 GB Budget)", 5L * 1024 * 1024 * 1024),
    CUSTOM("Custom AI Profile", 4L * 1024 * 1024 * 1024)
}

data class AIProfileConfig(
    val profileType: AIProfileType = AIProfileType.LITE,
    val customBudgetBytes: Long = 3L * 1024 * 1024 * 1024,
    val customTileSize: Int = 256,
    val customOverlapPercent: Int = 10,
    val customBatchSize: Int = 1,
    val gpuAcceleration: Boolean = false,
    val thermalThrottlingProtection: Boolean = true
) {
    val activeBudgetBytes: Long
        get() = when (profileType) {
            AIProfileType.LITE -> AIProfileType.LITE.defaultBudgetBytes
            AIProfileType.PRO -> AIProfileType.PRO.defaultBudgetBytes
            AIProfileType.CUSTOM -> customBudgetBytes
        }

    val activeTileSize: Int
        get() = when (profileType) {
            AIProfileType.LITE -> 256
            AIProfileType.PRO -> 512
            AIProfileType.CUSTOM -> customTileSize
        }

    val activeOverlapPercent: Int
        get() = when (profileType) {
            AIProfileType.LITE -> 10
            AIProfileType.PRO -> 15
            AIProfileType.CUSTOM -> customOverlapPercent
        }

    val activeBatchSize: Int
        get() = when (profileType) {
            AIProfileType.LITE -> 1
            AIProfileType.PRO -> 4
            AIProfileType.CUSTOM -> customBatchSize
        }

    val useGpu: Boolean
        get() = when (profileType) {
            AIProfileType.LITE -> false
            AIProfileType.PRO -> true
            AIProfileType.CUSTOM -> gpuAcceleration
        }
}
