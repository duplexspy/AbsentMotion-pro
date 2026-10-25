package app.absentmotion

import androidx.annotation.StringRes
import app.absentmotion.export.Choice
import app.absentmotion.i18n.UiText
import app.absentmotion.i18n.txt

enum class Feature(@StringRes val label: Int, val inCompatible: Boolean) {
    FlowHigh(R.string.feature_flow_high, inCompatible = false),
    AiSmooth(R.string.export_interpolation_ai, inCompatible = false),
    AiEnhance(R.string.feature_ai_enhance, inCompatible = false),
}

object Tier {
    val compatible: Boolean get() = android.os.Build.VERSION.SDK_INT < 33

    fun has(feature: Feature): Boolean {
        val maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        return when (feature) {
            Feature.FlowHigh -> maxMb >= 192 && cores >= 4
            Feature.AiSmooth -> cores >= 4
            Feature.AiEnhance -> cores >= 4
        }
    }

    val leftOut: List<Feature> get() = Feature.entries.filterNot(::has)

    fun reason(feature: Feature): UiText? = if (has(feature)) null else txt(R.string.tier_needs_latest)

    fun <T> choice(value: T, feature: Feature): Choice<T> = Choice(value, reason(feature), locked = false)
}
