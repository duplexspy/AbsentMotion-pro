package app.absentmotion.effectspack

object GlslTranslator {
    const val MAX_SHADER_BYTES = 64 * 1024

    val AutomaticUniforms = setOf("size", "time", "layer", "fromLayer", "toLayer", "duration", "screen", "image")
    val ReservedKeywords = setOf(
        "main", "coord", "half", "half2", "half3", "half4",
        "float", "float2", "float3", "float4",
        "int", "int2", "int3", "int4",
        "bool", "bool2", "bool3", "bool4",
        "color", "shader"
    )

    data class TranslationResult(
        val sksl: String,
        val renamedParams: Map<String, String>,
        val error: String? = null,
    )

    fun translate(
        rawGlsl: String,
        params: List<PackEffectParam>,
    ): TranslationResult {
        if (rawGlsl.length > MAX_SHADER_BYTES) {
            return TranslationResult("", emptyMap(), "Shader exceeds maximum size of 64 KB")
        }

        val renamed = linkedMapOf<String, String>()
        for (p in params) {
            if (p.originalId in AutomaticUniforms || p.originalId in ReservedKeywords) {
                renamed[p.originalId] = "${p.originalId}_p"
            }
        }

        val uniforms = ArrayList<String>()
        val switches = ArrayList<String>()

        for (p in params) {
            val pid = renamed[p.originalId] ?: p.id
            when (p.kind) {
                "color" -> uniforms += "layout(color) uniform float4 $pid;"
                "switch" -> {
                    uniforms += "uniform float ${pid}_f;"
                    switches += "bool $pid = ${pid}_f > 0.5;"
                }
                else -> uniforms += "uniform float $pid;"
            }
        }

        var code = rawGlsl.trim()

        for ((orig, newId) in renamed) {
            code = code.replace(Regex("""\b${Regex.escape(orig)}\b"""), newId)
        }

        code = code.replace(Regex("""\bacScreenSize\b"""), "size")
        code = code.replace(Regex("""\bacTime\b"""), "time")
        code = code.replace(Regex("""texture2DCv\s*\(\s*inputImg\.texture\s*,\s*"""), "gmSample(")
        code = code.replace(Regex("""\bvec([234])\b"""), "float$1")
        code = code.replace(Regex("""\bivec([234])\b"""), "int$1")
        code = code.replace(Regex("""\bbvec([234])\b"""), "bool$1")
        code = code.replace(Regex("""\bmat([234])\b"""), "float$1x$1")
        code = code.replace(Regex("""\bgl_FragColor\b"""), "gmFragColor")

        val switchDeclarations = if (switches.isNotEmpty()) {
            "\n  " + switches.joinToString("\n  ")
        } else ""

        val mainRegex = Regex("""void\s+main\s*\(\s*\)\s*\{""")
        if (!mainRegex.containsMatchIn(code)) {
            return TranslationResult("", renamed, "Shader lacks void main() function")
        }
        code = code.replace(mainRegex, "void gmMain(float2 acScreenNorm, out float4 gmFragColor) {$switchDeclarations")

        val headerBuilder = StringBuilder()
        headerBuilder.append("uniform shader image;\n")
        headerBuilder.append("uniform float2 size;\n")
        headerBuilder.append("uniform float time;\n")
        for (u in uniforms) {
            headerBuilder.append(u).append("\n")
        }
        headerBuilder.append("""
float4 gmSample(float2 uv) {
  float2 clamped = clamp(uv, float2(0.0), float2(1.0));
  return float4(image.eval(float2(clamped.x, 1.0 - clamped.y) * size));
}
""".trimIndent()).append("\n")

        val footer = """
half4 main(float2 coord) {
  float2 acScreenNorm = float2(coord.x / size.x, 1.0 - coord.y / size.y);
  float4 gmFragColor = float4(0.0);
  gmMain(acScreenNorm, gmFragColor);
  return half4(gmFragColor);
}
""".trimIndent()

        val fullSksl = headerBuilder.toString() + code + "\n" + footer
        return TranslationResult(fullSksl, renamed, null)
    }
}
