package app.absentmotion.effectspack

import app.absentmotion.effects.EffectDefinition
import app.absentmotion.effects.EffectParam
import app.absentmotion.model.XmlNode
import org.json.JSONArray
import org.json.JSONObject

data class PackEffectParam(
    val id: String,
    val originalId: String,
    val label: String,
    val kind: String,
    val default: String,
    val min: Float = -Float.MAX_VALUE,
    val max: Float = Float.MAX_VALUE,
    val step: Float = 0.01f,
    val options: List<String> = emptyList(),
) {
    val type: String get() = when (kind) {
        "color" -> "color"
        "switch" -> "bool"
        "choice" -> "float"
        else -> "float"
    }

    fun toEffectParam(sectionHeader: String? = null): EffectParam = EffectParam(
        id = id,
        label = label,
        kind = kind,
        default = default,
        min = min,
        max = max,
        step = step,
        options = options,
        sectionHeader = sectionHeader,
    )
}

data class PackEffect(
    val id: String,
    val name: String,
    val description: String,
    val group: String,
    val tags: List<String>,
    val params: List<PackEffectParam>,
    val sectionHeaders: Map<Int, String> = emptyMap(),
    val glsl: String,
    val thumbRef: String? = null,
    val sksl: String,
    val rawXml: String,
    val status: String = "ok",
    val error: String? = null,
    val packId: String? = null,
) {
    val available: Boolean get() = status == "ok" && error == null
    val key: String get() = id.removePrefix("gm.custom.")

    fun toEffectDefinition(thumbPath: String? = null): EffectDefinition = EffectDefinition(
        id = id,
        key = key,
        name = name,
        category = "packs",
        description = description,
        params = params.mapIndexed { idx, p -> p.toEffectParam(sectionHeaders[idx]) },
        sksl = sksl,
        native = false,
        status = if (available) "ok" else (error ?: "error"),
        presets = emptyList(),
        customThumb = thumbPath ?: thumbRef,
        group = group,
    )

    fun instance(): XmlNode = XmlNode(
        "effect",
        mapOf("id" to id, "hidden" to "false"),
        params.map { XmlNode("property", mapOf("name" to it.id, "type" to it.type, "value" to it.default)) }
    )

    fun runtimeXml(): String = XmlNode(
        "effect",
        mapOf("id" to id, "sksl" to sksl),
        params.map { XmlNode("param", mapOf("name" to it.id, "value" to it.default)) }
    ).toXml()
}
