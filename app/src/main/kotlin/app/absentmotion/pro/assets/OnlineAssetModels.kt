package app.absentmotion.pro.assets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AssetType {
    GIF,
    STICKER,
    SVG,
    MODEL_3D
}

enum class AssetSource(val displayName: String, val attributionText: String) {
    GIPHY("GIPHY", "Powered by GIPHY"),
    TENOR("Tenor", "Powered by Tenor"),
    FLATICON("Flaticon", "Icons by Flaticon"),
    FREE3D("Free3D", "Free 3D Models on the Internet"),
    POLY_PIZZA("Poly Pizza", "Free Low Poly 3D (CC0)"),
    OPEN_3D("Open 3D", "Open Source 3D Library"),
    AI_GENERATED("AI Studio", "Created with Absent Motion Pro AI Engine")
}

data class OnlineAsset(
    val id: String,
    val title: String,
    val type: AssetType,
    val source: AssetSource,
    val previewUrl: String,
    val contentUrl: String = "",
    val width: Int = 200,
    val height: Int = 200,
    val creator: String = "",
    val license: String = "CC-BY / Editorial",
    val tags: List<String> = emptyList(),
    val isAnimated: Boolean = true,
    val isVector: Boolean = false,
    val details: String = "",
    val downloadUrl: String = contentUrl,
    val hasAlpha: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("type", type.name)
        put("source", source.name)
        put("previewUrl", previewUrl)
        put("contentUrl", contentUrl)
        put("width", width)
        put("height", height)
        put("creator", creator)
        put("license", license)
        put("tags", JSONArray(tags))
        put("isAnimated", isAnimated)
        put("isVector", isVector)
        put("details", details)
    }

    companion object {
        fun fromJson(json: JSONObject): OnlineAsset {
            val tagsArr = json.optJSONArray("tags") ?: JSONArray()
            val tagList = mutableListOf<String>()
            for (i in 0 until tagsArr.length()) {
                tagList.add(tagsArr.optString(i))
            }
            return OnlineAsset(
                id = json.getString("id"),
                title = json.optString("title", "Untitled Asset"),
                type = runCatching { AssetType.valueOf(json.getString("type")) }.getOrDefault(AssetType.GIF),
                source = runCatching { AssetSource.valueOf(json.getString("source")) }.getOrDefault(AssetSource.GIPHY),
                previewUrl = json.getString("previewUrl"),
                contentUrl = json.getString("contentUrl"),
                width = json.optInt("width", 200),
                height = json.optInt("height", 200),
                creator = json.optString("creator", ""),
                license = json.optString("license", "Standard License"),
                tags = tagList,
                isAnimated = json.optBoolean("isAnimated", true),
                isVector = json.optBoolean("isVector", false),
                details = json.optString("details", "")
            )
        }
    }
}
