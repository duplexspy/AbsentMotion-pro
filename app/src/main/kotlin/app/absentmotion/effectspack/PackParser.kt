package app.absentmotion.effectspack

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.Locale

object PackParser {
    fun parse(xmlString: String, defaultGroup: String = "Custom"): PackEffect {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))

        var id = ""
        var name = ""
        var desc = ""
        var thumbRef: String? = null
        var tags = emptyList<String>()
        var unsupportedReason: String? = null

        val params = ArrayList<PackEffectParam>()
        val sectionHeaders = linkedMapOf<Int, String>()
        var currentSection: String? = null
        var currentSelectorId: String? = null
        var currentSelectorLabel: String? = null
        var currentSelectorDefault: String? = null
        var currentSelectorChoices = ArrayList<Pair<String, String>>()
        var rawGlsl = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "effect" -> {
                            id = parser.getAttributeValue(null, "id") ?: ""
                            name = parser.getAttributeValue(null, "name") ?: id
                            desc = parser.getAttributeValue(null, "desc") ?: ""
                            thumbRef = parser.getAttributeValue(null, "thumb")
                            tags = parser.getAttributeValue(null, "tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                        }
                        "texture" -> {
                            val texId = parser.getAttributeValue(null, "id") ?: ""
                            if (texId != "inputImg") {
                                unsupportedReason = "Unsupported texture input: $texId"
                            }
                        }
                        "section" -> {
                            val text = parser.getAttributeValue(null, "text") ?: ""
                            currentSection = text
                            sectionHeaders[params.size] = text
                        }
                        "spinner" -> {
                            val pid = parser.getAttributeValue(null, "id") ?: ""
                            val label = parser.getAttributeValue(null, "label") ?: pid
                            val defStr = parser.getAttributeValue(null, "default") ?: "0.0"
                            val minStr = parser.getAttributeValue(null, "min") ?: "-100000.0"
                            val maxStr = parser.getAttributeValue(null, "max") ?: "100000.0"
                            val stepStr = parser.getAttributeValue(null, "step") ?: "0.01"
                            val isInt = parser.getAttributeValue(null, "type") == "integer"

                            var step = stepStr.toFloatOrNull() ?: 0.01f
                            if (isInt && step < 1f) step = 1f

                            params += PackEffectParam(
                                id = pid,
                                originalId = pid,
                                label = label,
                                kind = "float",
                                default = defStr,
                                min = minStr.toFloatOrNull() ?: -Float.MAX_VALUE,
                                max = maxStr.toFloatOrNull() ?: Float.MAX_VALUE,
                                step = step,
                            )
                        }
                        "color" -> {
                            val pid = parser.getAttributeValue(null, "id") ?: ""
                            val label = parser.getAttributeValue(null, "label") ?: pid
                            var rawColor = parser.getAttributeValue(null, "default") ?: "#ffffffff"
                            rawColor = rawColor.lowercase(Locale.US)
                            if (rawColor.length == 7 && rawColor.startsWith("#")) {
                                rawColor = "#ff" + rawColor.drop(1)
                            }
                            params += PackEffectParam(
                                id = pid,
                                originalId = pid,
                                label = label,
                                kind = "color",
                                default = rawColor,
                            )
                        }
                        "switch" -> {
                            val pid = parser.getAttributeValue(null, "id") ?: ""
                            val label = parser.getAttributeValue(null, "label") ?: pid
                            val rawDef = parser.getAttributeValue(null, "default") ?: "false"
                            val boolVal = rawDef.equals("true", ignoreCase = true) || rawDef == "1"
                            params += PackEffectParam(
                                id = pid,
                                originalId = pid,
                                label = label,
                                kind = "switch",
                                default = boolVal.toString(),
                            )
                        }
                        "selector" -> {
                            currentSelectorId = parser.getAttributeValue(null, "id") ?: ""
                            currentSelectorLabel = parser.getAttributeValue(null, "label") ?: currentSelectorId
                            currentSelectorDefault = parser.getAttributeValue(null, "default") ?: "0"
                            currentSelectorChoices = ArrayList()
                        }
                        "choice" -> {
                            val value = parser.getAttributeValue(null, "value") ?: "0"
                            val label = parser.getAttributeValue(null, "label") ?: value
                            currentSelectorChoices.add(value to label)
                        }
                        "shader" -> {
                            rawGlsl = parser.nextText() ?: ""
                        }
                        else -> {
                            val pid = parser.getAttributeValue(null, "id")
                            val defVal = parser.getAttributeValue(null, "default")
                            if (pid != null && defVal != null) {
                                val minVal = parser.getAttributeValue(null, "min")?.toFloatOrNull() ?: -Float.MAX_VALUE
                                val maxVal = parser.getAttributeValue(null, "max")?.toFloatOrNull() ?: Float.MAX_VALUE
                                val stepVal = parser.getAttributeValue(null, "step")?.toFloatOrNull() ?: 0.01f
                                val label = parser.getAttributeValue(null, "label") ?: pid
                                params += PackEffectParam(
                                    id = pid,
                                    originalId = pid,
                                    label = label,
                                    kind = "float",
                                    default = defVal,
                                    min = minVal,
                                    max = maxVal,
                                    step = stepVal,
                                )
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "selector" && currentSelectorId != null) {
                        val selId = currentSelectorId!!
                        val selLabel = currentSelectorLabel ?: selId
                        val choices = currentSelectorChoices
                        val options = choices.map { it.second }
                        val defIdx = choices.indexOfFirst { it.first == currentSelectorDefault }.let { if (it >= 0) it else 0 }
                        params += PackEffectParam(
                            id = selId,
                            originalId = selId,
                            label = selLabel,
                            kind = "choice",
                            default = defIdx.toString(),
                            min = 0f,
                            max = (options.size - 1).coerceAtLeast(0).toFloat(),
                            step = 1f,
                            options = options,
                        )
                        currentSelectorId = null
                    }
                }
            }
            event = parser.next()
        }

        require(id.isNotBlank()) { "Effect missing id attribute" }

        val translation = GlslTranslator.translate(rawGlsl, params)
        val finalParams = params.map { p ->
            val renamed = translation.renamedParams[p.originalId]
            if (renamed != null) p.copy(id = renamed) else p
        }

        val err = unsupportedReason ?: translation.error
        val status = if (err == null) "ok" else "error"

        return PackEffect(
            id = id,
            name = name,
            description = desc,
            group = defaultGroup,
            tags = tags,
            params = finalParams,
            sectionHeaders = sectionHeaders,
            glsl = rawGlsl,
            thumbRef = thumbRef,
            sksl = translation.sksl,
            rawXml = xmlString,
            status = status,
            error = err,
        )
    }

    fun parse(input: InputStream, defaultGroup: String = "Custom"): PackEffect =
        parse(input.bufferedReader().use { it.readText() }, defaultGroup)
}
