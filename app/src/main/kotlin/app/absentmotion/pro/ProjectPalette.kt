package app.absentmotion.pro

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class PaletteSwatch(
    val id: String,
    val name: String,
    val color: Color,
) {
    val hex: String
        get() {
            val a = (color.alpha * 255).toInt() and 0xff
            val r = (color.red * 255).toInt() and 0xff
            val g = (color.green * 255).toInt() and 0xff
            val b = (color.blue * 255).toInt() and 0xff
            return String.format("#%02x%02x%02x%02x", a, r, g, b)
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("hex", hex)

    companion object {
        fun fromJson(json: JSONObject): PaletteSwatch {
            val id = json.getString("id")
            val name = json.getString("name")
            val hex = json.getString("hex")
            val parsedColor = runCatching {
                val clean = hex.removePrefix("#")
                val num = clean.toLong(16)
                if (clean.length == 6) Color(num or 0xFF000000L)
                else Color(num)
            }.getOrDefault(Color.White)
            return PaletteSwatch(id, name, parsedColor)
        }
    }
}

data class ProjectPalette(
    val id: String = "palette_main",
    val name: String = "Project Swatches",
    val swatches: List<PaletteSwatch> = defaultSwatches(),
) {
    fun withSwatch(swatch: PaletteSwatch): ProjectPalette {
        val updated = swatches.filterNot { it.id == swatch.id } + swatch
        return copy(swatches = updated)
    }

    fun withoutSwatch(swatchId: String): ProjectPalette {
        return copy(swatches = swatches.filterNot { it.id == swatchId })
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("swatches", JSONArray().apply { swatches.forEach { put(it.toJson()) } })

    companion object {
        fun defaultSwatches(): List<PaletteSwatch> = listOf(
            PaletteSwatch("sw_acc", "Accent", Color(0xFF54C395)),
            PaletteSwatch("sw_pri", "Primary", Color(0xFF1E232E)),
            PaletteSwatch("sw_sec", "Secondary", Color(0xFF282E3D)),
            PaletteSwatch("sw_light", "Light", Color(0xFFE8EEF5)),
            PaletteSwatch("sw_warn", "Warning", Color(0xFFFF5252)),
        )

        fun fromJson(json: JSONObject): ProjectPalette {
            val id = json.optString("id", "palette_main")
            val name = json.optString("name", "Project Swatches")
            val array = json.optJSONArray("swatches")
            val swatches = if (array != null) {
                (0 until array.length()).mapNotNull {
                    runCatching { PaletteSwatch.fromJson(array.getJSONObject(it)) }.getOrNull()
                }
            } else defaultSwatches()
            return ProjectPalette(id, name, swatches)
        }
    }
}
