package app.absentmotion.effectspack

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class InstalledPack(
    val manifest: PackManifest,
    val dir: File,
    val enabled: Boolean = true,
    val effects: List<PackEffect> = emptyList(),
)

data class ImportResult(
    val packId: String,
    val name: String,
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String> = emptyList(),
)

class PackSecurityException(message: String) : SecurityException(message)

object PackStore {
    const val MAX_ENTRIES = 500
    const val MAX_ENTRY_XML_BYTES = 2L * 1024 * 1024
    const val MAX_ENTRY_IMAGE_BYTES = 5L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 100L * 1024 * 1024

    fun packsDir(context: Context): File = File(context.filesDir, "packs").apply { mkdirs() }

    fun installBundledPacks(context: Context) {
        val prefs = context.getSharedPreferences("packs_meta", Context.MODE_PRIVATE)
        val packs = listPacks(context)
        if (packs.isNotEmpty() && prefs.getBoolean("bundled_installed", false)) return
        val assetManager = context.assets
        val list = runCatching { assetManager.list("packs") }.getOrNull() ?: return
        var changed = false
        for (item in list) {
            if (!item.endsWith(".effectspack") && !item.endsWith(".zip")) continue
            runCatching {
                assetManager.open("packs/$item").use { input ->
                    importZip(context, input)
                    changed = true
                }
            }
        }
        prefs.edit().putBoolean("bundled_installed", true).apply()
        if (changed) {
            PackCatalog.refresh(context)
        }
    }

    fun listPacks(context: Context): List<InstalledPack> {
        val root = packsDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        val result = ArrayList<InstalledPack>()
        for (d in dirs) {
            val manifestFile = File(d, "manifest.json")
            if (!manifestFile.isFile) continue
            try {
                val manifest = PackManifest.parse(manifestFile.readText())
                val metaFile = File(d, "meta.json")
                val enabled = if (metaFile.isFile) {
                    runCatching { JSONObject(metaFile.readText()).optBoolean("enabled", true) }.getOrDefault(true)
                } else true

                val effectsDir = File(d, "effects")
                val effects = ArrayList<PackEffect>()
                val manifestByFile = manifest.effects.associateBy { it.file }
                val manifestById = manifest.effects.associateBy { it.id }

                val xmlFiles = effectsDir.listFiles { f -> f.isFile && f.extension == "xml" } ?: emptyArray()
                for (xmlFile in xmlFiles) {
                    val relativeFile = "effects/${xmlFile.name}"
                    val entry = manifestByFile[relativeFile]
                    val group = entry?.group ?: "Custom"
                    try {
                        val parsed = PackParser.parse(xmlFile.readText(), group)
                        val finalEntry = entry ?: manifestById[parsed.id]
                        val finalGroup = finalEntry?.group ?: group
                        val finalName = finalEntry?.name ?: parsed.name
                        val thumbPath = finalEntry?.thumb?.let { File(d, it).path } ?: findThumbFor(d, xmlFile.nameWithoutExtension)?.path
                        effects += parsed.copy(
                            group = finalGroup,
                            name = finalName,
                            thumbRef = thumbPath,
                            packId = manifest.id,
                        )
                    } catch (e: Exception) {
                        effects += PackEffect(
                            id = "unknown.${xmlFile.nameWithoutExtension}",
                            name = xmlFile.nameWithoutExtension,
                            description = e.message ?: "Failed to parse",
                            group = group,
                            tags = emptyList(),
                            params = emptyList(),
                            glsl = "",
                            sksl = "",
                            rawXml = xmlFile.readText(),
                            status = "error",
                            error = e.message,
                            packId = manifest.id,
                        )
                    }
                }
                result += InstalledPack(manifest, d, enabled, effects)
            } catch (_: Exception) {}
        }
        return result
    }

    private fun findThumbFor(packDir: File, baseName: String): File? {
        val thumbsDir = File(packDir, "thumbs")
        if (!thumbsDir.isDirectory) return null
        val exts = listOf("png", "webp", "jpg", "jpeg")
        for (ext in exts) {
            val f = File(thumbsDir, "$baseName.$ext")
            if (f.isFile) return f
        }
        return null
    }

    fun setPackEnabled(context: Context, packId: String, enabled: Boolean) {
        val dir = File(packsDir(context), packId)
        if (!dir.isDirectory) return
        val meta = JSONObject().put("enabled", enabled)
        File(dir, "meta.json").writeText(meta.toString())
    }

    fun deletePack(context: Context, packId: String): Boolean {
        val dir = File(packsDir(context), packId)
        return dir.deleteRecursively()
    }

    fun importZip(context: Context, input: InputStream): ImportResult {
        val tempDir = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "pack-import-").toFile()
        try {
            var entryCount = 0
            var totalBytes = 0L

            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.startsWith("/") || name.contains("..") || name.contains("\\")) {
                        throw PackSecurityException("Unsafe zip entry path: $name")
                    }
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        throw PackSecurityException("Zip archive contains more than $MAX_ENTRIES entries")
                    }

                    val dest = File(tempDir, name)
                    if (entry.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile?.mkdirs()
                        val isXml = name.endsWith(".xml")
                        val isImg = name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                        val maxEntryAllowed = if (isXml) MAX_ENTRY_XML_BYTES else if (isImg) MAX_ENTRY_IMAGE_BYTES else MAX_TOTAL_BYTES

                        var entryRead = 0L
                        val buf = ByteArray(8192)
                        dest.outputStream().use { out ->
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                entryRead += n
                                totalBytes += n
                                if (entryRead > maxEntryAllowed) {
                                    throw PackSecurityException("Entry $name exceeded size limit")
                                }
                                if (totalBytes > MAX_TOTAL_BYTES) {
                                    throw PackSecurityException("Total archive uncompressed size exceeded 100 MB")
                                }
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }

            return installExtractedFolder(context, tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun importSingleXml(context: Context, xmlString: String, thumbBytes: ByteArray? = null): ImportResult {
        val effect = PackParser.parse(xmlString)
        val packId = "user.custom"
        val targetPackDir = File(packsDir(context), packId).apply { mkdirs() }
        val effectsDir = File(targetPackDir, "effects").apply { mkdirs() }
        val thumbsDir = File(targetPackDir, "thumbs").apply { mkdirs() }

        val baseName = effect.id.removePrefix("gm.custom.")
        val xmlFile = File(effectsDir, "$baseName.xml")
        xmlFile.writeText(xmlString)

        var thumbPath: String? = null
        if (thumbBytes != null) {
            val thumbFile = File(thumbsDir, "$baseName.png")
            thumbFile.writeBytes(thumbBytes)
            thumbPath = "thumbs/$baseName.png"
        }

        val manifestFile = File(targetPackDir, "manifest.json")
        val manifest = if (manifestFile.isFile) {
            runCatching { PackManifest.parse(manifestFile.readText()) }.getOrNull()
        } else null

        val updatedEffects = (manifest?.effects.orEmpty().filterNot { it.id == effect.id } +
            ManifestEffectEntry(effect.id, "effects/$baseName.xml", thumbPath, effect.name, effect.group, effect.tags))

        val newManifest = (manifest ?: PackManifest(id = packId, name = "Custom Effects", author = "User"))
            .copy(effects = updatedEffects)
        manifestFile.writeText(newManifest.toJson().toString(2))

        return ImportResult(packId, newManifest.name, 1, 0)
    }

    private fun installExtractedFolder(context: Context, folder: File): ImportResult {
        val manifestFile = File(folder, "manifest.json")
        val manifest = if (manifestFile.isFile) {
            runCatching { PackManifest.parse(manifestFile.readText()) }.getOrNull()
        } else null

        val effectsInFolder = folder.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        if (effectsInFolder.isEmpty()) {
            throw IllegalArgumentException("No XML effects found in archive")
        }

        val packId = manifest?.id?.takeIf { it.isNotBlank() } ?: "pack.${effectsInFolder.first().nameWithoutExtension}"
        val packName = manifest?.name?.takeIf { it.isNotBlank() } ?: "Imported Pack"
        val targetPackDir = File(packsDir(context), packId).apply { mkdirs() }
        val targetEffectsDir = File(targetPackDir, "effects").apply { mkdirs() }
        val targetThumbsDir = File(targetPackDir, "thumbs").apply { mkdirs() }

        val thumbsInFolder = folder.walkTopDown().filter { it.isFile && it.extension in setOf("png", "webp", "jpg", "jpeg") }.toList()
        for (th in thumbsInFolder) {
            th.copyTo(File(targetThumbsDir, th.name), overwrite = true)
        }

        val imported = ArrayList<ManifestEffectEntry>()
        val errors = ArrayList<String>()
        var skipped = 0

        for (xmlFile in effectsInFolder) {
            try {
                val text = xmlFile.readText()
                val parsed = PackParser.parse(text)
                val base = "${xmlFile.nameWithoutExtension}.xml"
                xmlFile.copyTo(File(targetEffectsDir, base), overwrite = true)
                val thumbFile = findThumbFor(targetPackDir, xmlFile.nameWithoutExtension)
                val thumbRel = thumbFile?.let { "thumbs/${it.name}" }
                val manifestEntry = manifest?.effects?.firstOrNull { it.id == parsed.id || it.file.endsWith(base) }

                imported += ManifestEffectEntry(
                    id = parsed.id,
                    file = "effects/$base",
                    thumb = manifestEntry?.thumb ?: thumbRel,
                    name = manifestEntry?.name ?: parsed.name,
                    group = manifestEntry?.group ?: parsed.group,
                    tags = manifestEntry?.tags ?: parsed.tags,
                )
            } catch (e: Exception) {
                skipped++
                errors += "${xmlFile.name}: ${e.message}"
            }
        }

        val finalManifest = (manifest ?: PackManifest(id = packId, name = packName))
            .copy(effects = imported)
        File(targetPackDir, "manifest.json").writeText(finalManifest.toJson().toString(2))

        return ImportResult(packId, packName, imported.size, skipped, errors)
    }

    fun exportPackZip(context: Context, packId: String, out: OutputStream) {
        val dir = File(packsDir(context), packId)
        require(dir.isDirectory) { "Pack $packId not found" }
        ZipOutputStream(out.buffered()).use { zip ->
            val files = dir.walkTopDown().filter { it.isFile && it.name != "meta.json" }.toList()
            val buf = ByteArray(8192)
            for (file in files) {
                val rel = file.relativeTo(dir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(rel))
                file.inputStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        zip.write(buf, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
    }
}
