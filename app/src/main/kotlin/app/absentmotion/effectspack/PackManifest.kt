package app.absentmotion.effectspack

import org.json.JSONArray
import org.json.JSONObject

data class ManifestEffectEntry(
    val id: String,
    val file: String,
    val thumb: String? = null,
    val name: String,
    val group: String,
    val tags: List<String> = emptyList(),
)

data class PackManifest(
    val format: String = "effectspack",
    val formatVersion: Int = 1,
    val id: String,
    val name: String,
    val author: String = "",
    val version: String = "1.0.0",
    val description: String = "",
    val effects: List<ManifestEffectEntry> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", format)
        put("formatVersion", formatVersion)
        put("id", id)
        put("name", name)
        put("author", author)
        put("version", version)
        put("description", description)
        val arr = JSONArray()
        for (e in effects) {
            val obj = JSONObject().apply {
                put("id", e.id)
                put("file", e.file)
                if (e.thumb != null) put("thumb", e.thumb)
                put("name", e.name)
                put("group", e.group)
                if (e.tags.isNotEmpty()) {
                    val tagArr = JSONArray()
                    e.tags.forEach { tagArr.put(it) }
                    put("tags", tagArr)
                }
            }
            arr.put(obj)
        }
        put("effects", arr)
    }

    companion object {
        fun parse(jsonStr: String): PackManifest {
            val json = JSONObject(jsonStr)
            val effectsArr = json.optJSONArray("effects") ?: JSONArray()
            val effects = ArrayList<ManifestEffectEntry>()
            for (i in 0 until effectsArr.length()) {
                val obj = effectsArr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val file = obj.optString("file")
                val name = obj.optString("name", id)
                val group = obj.optString("group", "Custom")
                val thumb = obj.optString("thumb").takeIf { it.isNotBlank() }
                val tagArr = obj.optJSONArray("tags")
                val tags = tagArr?.let { arr -> List(arr.length()) { arr.optString(it) } } ?: emptyList()
                if (id.isNotBlank() && file.isNotBlank()) {
                    effects += ManifestEffectEntry(id, file, thumb, name, group, tags)
                }
            }
            return PackManifest(
                format = json.optString("format", "effectspack"),
                formatVersion = json.optInt("formatVersion", 1),
                id = json.optString("id"),
                name = json.optString("name"),
                author = json.optString("author"),
                version = json.optString("version", "1.0.0"),
                description = json.optString("description"),
                effects = effects,
            )
        }
    }
}
