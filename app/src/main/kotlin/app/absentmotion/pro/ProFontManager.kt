package app.absentmotion.pro

import android.content.Context
import app.absentmotion.fonts.FontLibrary
import app.absentmotion.fonts.FontName
import app.absentmotion.model.Scene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FontMeta(
    val family: String,
    val style: String,
    val version: String,
    val copyright: String,
    val license: String,
    val sha256: String,
    val fileSize: Long,
    val sourceUrl: String,
    val importDate: Long,
    val readmeText: String,
    val fileName: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("family", family)
        put("style", style)
        put("version", version)
        put("copyright", copyright)
        put("license", license)
        put("sha256", sha256)
        put("fileSize", fileSize)
        put("sourceUrl", sourceUrl)
        put("importDate", importDate)
        put("readmeText", readmeText)
        put("fileName", fileName)
    }

    companion object {
        fun fromJson(json: JSONObject): FontMeta = FontMeta(
            family = json.optString("family", "Unknown"),
            style = json.optString("style", "Regular"),
            version = json.optString("version", ""),
            copyright = json.optString("copyright", ""),
            license = json.optString("license", ""),
            sha256 = json.optString("sha256", ""),
            fileSize = json.optLong("fileSize", 0L),
            sourceUrl = json.optString("sourceUrl", ""),
            importDate = json.optLong("importDate", 0L),
            readmeText = json.optString("readmeText", ""),
            fileName = json.optString("fileName", ""),
        )
    }
}

class ProFontManager(private val context: Context) {
    private val importedDir = File(context.filesDir, "fonts/imported").apply { mkdirs() }
    private val metaFile = File(context.filesDir, "fonts/imported_meta.json")

    @Synchronized
    fun loadAll(): List<FontMeta> {
        if (!metaFile.isFile) return emptyList()
        return runCatching {
            val text = metaFile.readText()
            val array = JSONArray(text)
            List(array.length()) { FontMeta.fromJson(array.getJSONObject(it)) }
                .filter { File(importedDir, it.fileName).isFile }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun saveAll(list: List<FontMeta>) {
        runCatching {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            metaFile.writeText(array.toString(2))
        }
    }

    @Synchronized
    fun findByHash(sha256: String): FontMeta? {
        if (sha256.isBlank()) return null
        return loadAll().firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
    }

    @Synchronized
    fun findByFileName(fileName: String): FontMeta? =
        loadAll().firstOrNull { it.fileName == fileName }

    @Synchronized
    fun findByFamily(family: String): FontMeta? =
        loadAll().firstOrNull { it.family.equals(family, ignoreCase = true) }

    @Synchronized
    fun deleteFont(fileName: String): Boolean {
        val file = File(importedDir, fileName)
        val deleted = file.delete()
        val remaining = loadAll().filter { it.fileName != fileName }
        saveAll(remaining)
        return deleted
    }

    suspend fun importFontFile(
        file: File,
        sourceUrl: String = "",
        readme: String = "",
    ): FontMeta = withContext(Dispatchers.IO) {
        val validated = FontCheckService.validateFont(context, file)
        require(validated.isValid) { validated.errorReason ?: "Font validation failed" }

        val existing = findByHash(validated.sha256)
        if (existing != null) {
            return@withContext existing
        }

        val baseName = FontSecurity.cleanFontFileName(file.name)
        var targetFile = File(importedDir, baseName)
        var counter = 1
        while (targetFile.exists()) {
            val namePart = baseName.substringBeforeLast('.')
            val extPart = baseName.substringAfterLast('.', "")
            val alt = if (extPart.isBlank()) "${namePart}_$counter" else "${namePart}_$counter.$extPart"
            targetFile = File(importedDir, alt)
            counter++
        }

        if (file.parentFile == importedDir && file.name == targetFile.name) {
        } else {
            file.copyTo(targetFile, overwrite = true)
        }

        val meta = FontMeta(
            family = validated.family,
            style = validated.style,
            version = validated.version,
            copyright = validated.copyright,
            license = validated.license,
            sha256 = validated.sha256,
            fileSize = targetFile.length(),
            sourceUrl = sourceUrl,
            importDate = System.currentTimeMillis(),
            readmeText = readme.take(32768),
            fileName = targetFile.name,
        )

        synchronized(this@ProFontManager) {
            val list = loadAll().filter { it.fileName != meta.fileName } + meta
            saveAll(list)
        }
        meta
    }

    suspend fun importArchive(
        zipFile: File,
        sourceUrl: String = "",
    ): List<FontMeta> = withContext(Dispatchers.IO) {
        val tempExtractDir = File(context.cacheDir, "zip_extract_${System.currentTimeMillis()}")
        tempExtractDir.mkdirs()
        try {
            val extracted = FontSecurity.safeExtractZip(zipFile, tempExtractDir)
            val docFiles = extracted.filter { it.extension.lowercase() in FontSecurity.DOC_EXTENSIONS }
            val fontFiles = extracted.filter { it.extension.lowercase() in FontSecurity.FONT_EXTENSIONS }

            val readmeBuilder = StringBuilder()
            docFiles.take(3).forEach { doc ->
                if (doc.length() < 65536L) {
                    runCatching {
                        val text = doc.readText().trim()
                        if (text.isNotBlank()) {
                            readmeBuilder.append("--- ${doc.name} ---\n").append(text).append("\n\n")
                        }
                    }
                }
            }
            val aggregatedReadme = readmeBuilder.toString().trim()

            val importedList = ArrayList<FontMeta>()
            for (f in fontFiles) {
                runCatching {
                    val meta = importFontFile(f, sourceUrl, aggregatedReadme)
                    importedList.add(meta)
                }
            }
            importedList
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }

    companion object {
        fun findMissingFonts(scene: Scene, library: FontLibrary): List<FontName> {
            val missing = ArrayList<FontName>()
            for (layer in scene.layers) {
                if (layer.kind == "text") {
                    val fontAttr = layer.node["font"].orEmpty()
                    if (fontAttr.isNotBlank()) {
                        val font = FontName.parse(fontAttr)
                        if (library.localFile(font.xml) == null) {
                            missing.add(font)
                        }
                    }
                }
            }
            return missing.distinctBy { it.xml }
        }
    }
}
