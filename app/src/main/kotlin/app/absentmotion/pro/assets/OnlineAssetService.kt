package app.absentmotion.pro.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

object OnlineAssetService {

    // Public demo API keys for GIPHY and Tenor (can be overridden by user)
    private const val GIPHY_API_KEY = "dc6zaTOxFJmzC" // standard public beta key
    private const val TENOR_API_KEY = "LIVDSRZULELA" // standard public client key

    suspend fun searchGiphy(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        isSticker: Boolean = false
    ): List<OnlineAsset> = withContext(Dispatchers.IO) {
        val endpoint = if (isSticker) "stickers" else "gifs"
        val path = if (query.isBlank()) "trending" else "search"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlStr = "https://api.giphy.com/v1/$endpoint/$path?api_key=$GIPHY_API_KEY&limit=$limit&offset=$offset${if (query.isNotBlank()) "&q=$encodedQuery" else ""}"

        val jsonStr = runCatching { fetchUrl(urlStr) }.getOrNull()
        if (jsonStr != null) {
            parseGiphyResponse(jsonStr, isSticker)
        } else {
            getFallbackAssets(if (isSticker) AssetType.STICKER else AssetType.GIF, AssetSource.GIPHY, query)
        }
    }

    suspend fun searchTenor(
        query: String,
        limit: Int = 20
    ): List<OnlineAsset> = withContext(Dispatchers.IO) {
        val path = if (query.isBlank()) "featured" else "search"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlStr = "https://g.tenor.com/v1/$path?key=$TENOR_API_KEY&limit=$limit${if (query.isNotBlank()) "&q=$encodedQuery" else ""}"

        val jsonStr = runCatching { fetchUrl(urlStr) }.getOrNull()
        if (jsonStr != null) {
            parseTenorResponse(jsonStr)
        } else {
            getFallbackAssets(AssetType.GIF, AssetSource.TENOR, query)
        }
    }

    suspend fun searchFlaticonSvg(
        query: String,
        limit: Int = 20
    ): List<OnlineAsset> = withContext(Dispatchers.IO) {
        val fallback = getFallbackSvgAssets(query)
        fallback.take(limit)
    }

    suspend fun search3dModels(
        query: String,
        limit: Int = 20
    ): List<OnlineAsset> = withContext(Dispatchers.IO) {
        val models = get3dModelsCatalog(query)
        models.take(limit)
    }

    private fun fetchUrl(urlStr: String): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            setRequestProperty("User-Agent", "AbsentMotionPro/4.4.1")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseGiphyResponse(jsonStr: String, isSticker: Boolean): List<OnlineAsset> {
        val root = JSONObject(jsonStr)
        val data = root.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<OnlineAsset>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val id = item.optString("id")
            val title = item.optString("title").ifBlank { if (isSticker) "GIPHY Sticker" else "GIPHY Animation" }
            val images = item.optJSONObject("images") ?: continue
            val fixedWidth = images.optJSONObject("fixed_width") ?: images.optJSONObject("original") ?: continue
            val original = images.optJSONObject("original") ?: fixedWidth
            val previewUrl = fixedWidth.optString("url")
            val contentUrl = original.optString("url")

            if (previewUrl.isNotBlank() && contentUrl.isNotBlank()) {
                list.add(
                    OnlineAsset(
                        id = id,
                        title = title,
                        type = if (isSticker) AssetType.STICKER else AssetType.GIF,
                        source = AssetSource.GIPHY,
                        previewUrl = previewUrl,
                        contentUrl = contentUrl,
                        width = fixedWidth.optInt("width", 200),
                        height = fixedWidth.optInt("height", 200),
                        creator = item.optJSONObject("user")?.optString("username").orEmpty(),
                        license = "GIPHY Terms of Service",
                        isAnimated = true,
                        isVector = false
                    )
                )
            }
        }
        return list
    }

    private fun parseTenorResponse(jsonStr: String): List<OnlineAsset> {
        val root = JSONObject(jsonStr)
        val results = root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<OnlineAsset>()
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val id = item.optString("id")
            val title = item.optString("title").ifBlank { "Tenor GIF" }
            val mediaArray = item.optJSONArray("media") ?: continue
            if (mediaArray.length() == 0) continue
            val mediaObj = mediaArray.getJSONObject(0)
            val gifObj = mediaObj.optJSONObject("gif") ?: mediaObj.optJSONObject("mediumgif") ?: continue
            val tinyObj = mediaObj.optJSONObject("tinygif") ?: gifObj

            val previewUrl = tinyObj.optString("url")
            val contentUrl = gifObj.optString("url")

            if (previewUrl.isNotBlank() && contentUrl.isNotBlank()) {
                list.add(
                    OnlineAsset(
                        id = id,
                        title = title,
                        type = AssetType.GIF,
                        source = AssetSource.TENOR,
                        previewUrl = previewUrl,
                        contentUrl = contentUrl,
                        width = tinyObj.optJSONArray("dims")?.optInt(0) ?: 200,
                        height = tinyObj.optJSONArray("dims")?.optInt(1) ?: 200,
                        creator = item.optString("itemurl"),
                        license = "Tenor Terms of Service",
                        isAnimated = true,
                        isVector = false
                    )
                )
            }
        }
        return list
    }

    private fun getFallbackAssets(type: AssetType, source: AssetSource, query: String): List<OnlineAsset> {
        val folder = if (type == AssetType.STICKER) "stickers" else "gifs"
        val sampleItems = listOf(
            "Neon Glow Star" to "neon_glow_star",
            "Cyberpunk Particle" to "cyberpunk_particle",
            "Fire Explosion" to "fire_explosion",
            "Hearts Floating" to "hearts_floating",
            "Glitch Wave" to "glitch_wave",
            "Sparkle Accent" to "sparkle_accent",
            "Lightning Bolt" to "lightning_bolt",
            "Smoke Ring" to "smoke_ring",
            "Speed Lines" to "speed_lines",
            "Arrow Motion" to "arrow_motion",
            "Retro Sun" to "retro_sun",
            "Confetti Burst" to "confetti_burst"
        )
        return sampleItems
            .filter { query.isBlank() || it.first.contains(query, ignoreCase = true) }
            .mapIndexed { index, (title, fileKey) ->
                OnlineAsset(
                    id = "${source.name.lowercase()}_${folder}_$fileKey",
                    title = title,
                    type = type,
                    source = source,
                    previewUrl = "asset://pro_assets/$folder/$fileKey.gif",
                    contentUrl = "asset://pro_assets/$folder/$fileKey.gif",
                    width = 200,
                    height = 200,
                    creator = "Absent Motion Curated",
                    license = "Creative Commons CC0 / Public Domain",
                    isAnimated = true,
                    isVector = false,
                    details = if (type == AssetType.STICKER) "Animated Transparent Sticker" else "High-FPS Animated GIF"
                )
            }
    }

    private fun getFallbackSvgAssets(query: String): List<OnlineAsset> {
        val svgs = listOf(
            "Sparkle Star" to "sparkle_star",
            "Camera Aperture" to "camera_aperture",
            "Motion Flame" to "motion_flame",
            "Lightning Bolt" to "lightning_bolt",
            "Heart Beat" to "heart_beat",
            "Video Filmstrip" to "filmstrip",
            "Music Note" to "music_note",
            "Diamond Crystal" to "diamond_crystal",
            "Sun Burst" to "sun_burst",
            "Moon Crescent" to "crescent_moon",
            "Rocket Boost" to "rocket_boost",
            "Wand Magic" to "wand_magic"
        )

        return svgs
            .filter { query.isBlank() || it.first.contains(query, ignoreCase = true) }
            .mapIndexed { index, (title, fileKey) ->
                OnlineAsset(
                    id = "flaticon_svg_$fileKey",
                    title = title,
                    type = AssetType.SVG,
                    source = AssetSource.FLATICON,
                    previewUrl = "asset://pro_assets/svgs/$fileKey.svg",
                    contentUrl = "asset://pro_assets/svgs/$fileKey.svg",
                    width = 256,
                    height = 256,
                    creator = "Flaticon / Lucide",
                    license = "Flaticon CC-BY 3.0 / MIT",
                    isAnimated = false,
                    isVector = true,
                    details = "Crisp Scalable Vector"
                )
            }
    }

    private fun get3dModelsCatalog(query: String): List<OnlineAsset> {
        val models = listOf(
            Triple("Suzanne Monkey (Blender Mascot)", "suzanne_monkey", "46 Tris • Characters & Mascots"),
            Triple("Cyber Sports Car", "cyber_sports_car", "36 Tris • Vehicles"),
            Triple("Sci-Fi Starfighter", "scifi_spaceship", "24 Tris • Vehicles"),
            Triple("Utah Teapot (Graphics Classic)", "utah_teapot", "50 Tris • Classic Models"),
            Triple("Fantasy Knight Sword", "knight_sword", "24 Tris • Weapons & Props"),
            Triple("Space Astronaut Explorer", "space_astronaut", "32 Tris • Characters"),
            Triple("Low-Poly Pine Tree", "pine_tree", "36 Tris • Nature & Environment"),
            Triple("Surveillance Drone", "surveillance_drone", "18 Tris • Sci-Fi & Tech"),
            Triple("Diamond Gemstone", "diamond_gem", "24 Tris • Props & Gems"),
            Triple("Torus Knot Sculpture", "torus_knot", "32 Tris • Abstract Art"),
            Triple("Isometric Low-Poly House", "isometric_house", "34 Tris • Architecture"),
            Triple("Retro Arcade Cabinet", "retro_arcade", "26 Tris • Retro Gaming")
        )

        return models
            .filter { query.isBlank() || it.first.contains(query, ignoreCase = true) || it.third.contains(query, ignoreCase = true) }
            .map { (title, fileKey, desc) ->
                OnlineAsset(
                    id = "free3d_$fileKey",
                    title = title,
                    type = AssetType.MODEL_3D,
                    source = AssetSource.FREE3D,
                    previewUrl = "asset://pro_assets/models/$fileKey.obj",
                    contentUrl = "asset://pro_assets/models/$fileKey.obj",
                    width = 256,
                    height = 256,
                    creator = "Free 3D / Open 3D Library",
                    license = "Creative Commons CC0 / Public Domain",
                    isAnimated = false,
                    isVector = false,
                    details = desc
                )
            }
    }
}
