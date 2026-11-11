package app.absentmotion.pro.ai.tile

enum class TileSizeMode(val size: Int, val label: String) {
    AUTO(0, "Auto (Profile Adaptive)"),
    SIZE_64(64, "64 × 64 (Ultra Low Mem)"),
    SIZE_128(128, "128 × 128 (Lite Fast)"),
    SIZE_256(256, "256 × 256 (Balanced)"),
    SIZE_384(384, "384 × 384 (Medium)"),
    SIZE_512(512, "512 × 512 (Pro Standard)"),
    SIZE_768(768, "768 × 768 (High Detail)"),
    SIZE_1024(1024, "1024 × 1024 (Ultra Pro)"),
    CUSTOM(-1, "Custom Tile Size")
}

enum class TileOverlapMode(val percent: Int, val label: String) {
    AUTO(-1, "Auto (Profile Adaptive)"),
    OVERLAP_0(0, "0% (Fastest / No Blending)"),
    OVERLAP_5(5, "5% (Minimal Seam Blend)"),
    OVERLAP_10(10, "10% (Recommended)"),
    OVERLAP_15(15, "15% (Smooth High Quality)"),
    OVERLAP_20(20, "20% (Ultra Smooth Edge)"),
    CUSTOM(-2, "Custom Overlap %")
}

enum class TileProcessingMode(val label: String) {
    SEQUENTIAL("Sequential (1 Tile at a time - Safe Low Memory)"),
    MULTI_TILE("Multi-Tile Concurrent (Faster - High Memory)")
}

enum class TileBatchSize(val batch: Int, val label: String) {
    AUTO(0, "Auto (Adaptive)"),
    BATCH_1(1, "1 Tile / Batch"),
    BATCH_2(2, "2 Tiles / Batch"),
    BATCH_4(4, "4 Tiles / Batch"),
    BATCH_8(8, "8 Tiles / Batch")
}

data class AITileSettings(
    val sizeMode: TileSizeMode = TileSizeMode.AUTO,
    val customSize: Int = 256,
    val overlapMode: TileOverlapMode = TileOverlapMode.AUTO,
    val customOverlapPercent: Int = 10,
    val processingMode: TileProcessingMode = TileProcessingMode.SEQUENTIAL,
    val batchSize: TileBatchSize = TileBatchSize.AUTO
) {
    fun resolveTileSize(defaultFromProfile: Int): Int {
        return when (sizeMode) {
            TileSizeMode.AUTO -> defaultFromProfile
            TileSizeMode.CUSTOM -> customSize.coerceIn(32, 2048)
            else -> sizeMode.size
        }
    }

    fun resolveOverlapPercent(defaultFromProfile: Int): Int {
        return when (overlapMode) {
            TileOverlapMode.AUTO -> defaultFromProfile
            TileOverlapMode.CUSTOM -> customOverlapPercent.coerceIn(0, 40)
            else -> overlapMode.percent
        }
    }

    fun resolveBatchSize(defaultFromProfile: Int): Int {
        return when (batchSize) {
            TileBatchSize.AUTO -> defaultFromProfile
            else -> batchSize.batch
        }
    }
}
