package app.absentmotion.pro

import java.net.URI
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class FontTableInfo(
    val tag: String,
    val offset: Long,
    val length: Long,
)

data class ParsedFontHeader(
    val format: String,
    val numTables: Int,
    val tables: Map<String, FontTableInfo>,
)

data class ParsedFontMetadata(
    val family: String,
    val style: String,
    val version: String,
    val copyright: String,
    val license: String,
    val postScriptName: String,
    val sha256: String,
    val isValid: Boolean,
    val errorReason: String? = null,
)

object FontSecurity {
    const val MAX_ZIP_ENTRIES = 100
    const val MAX_ZIP_BYTES = 50L * 1024L * 1024L
    const val MAX_ZIP_RATIO = 25

    private val ALLOWED_EXTENSIONS = setOf("ttf", "otf", "ttc", "txt", "pdf", "rtf", "md", "htm", "html", "png", "jpg")
    val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
    val DOC_EXTENSIONS = setOf("txt", "pdf", "rtf", "md", "htm", "html")

    private val DAFONT_HOSTS = setOf(
        "dafont.com",
        "www.dafont.com",
        "dl.dafont.com",
        "img.dafont.com",
    )

    fun isDafontUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = runCatching { URI(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase() ?: url.substringBefore("://", "").lowercase()
        if (scheme != "https" && scheme != "http") return false
        val host = uri?.host?.lowercase() ?: runCatching {
            url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
        }.getOrNull() ?: return false
        if (host in DAFONT_HOSTS) return true
        if (host.endsWith(".dafont.com")) return true
        return false
    }

    fun cleanFontFileName(rawName: String): String {
        val base = File(rawName).name
        val ext = base.substringAfterLast('.', "")
        val nameWithoutExt = base.substringBeforeLast('.')
        val cleanedName = nameWithoutExt.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
        val finalBase = if (cleanedName.isBlank()) "font" else cleanedName
        return if (ext.isBlank()) finalBase else "$finalBase.${ext.lowercase()}"
    }

    fun safeExtractZip(zipFile: File, targetDir: File): List<File> {
        require(zipFile.isFile) { "Zip file does not exist: ${zipFile.path}" }
        if (!targetDir.exists()) targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalFile

        val extracted = ArrayList<File>()
        var entryCount = 0
        var totalBytes = 0L

        FileInputStream(zipFile).use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        throw SecurityException("Zip archive contains too many entries (> $MAX_ZIP_ENTRIES)")
                    }

                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        throw SecurityException("Zip-slip path traversal attempt: $name")
                    }

                    val destFile = File(targetDir, name).canonicalFile
                    if (!destFile.path.startsWith(canonicalTarget.path + File.separator) && destFile != canonicalTarget) {
                        throw SecurityException("Zip entry escapes destination directory: $name")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        val ext = destFile.extension.lowercase()
                        if (ext in ALLOWED_EXTENSIONS) {
                            destFile.parentFile?.mkdirs()
                            var entryBytes = 0L
                            FileOutputStream(destFile).use { fos ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (zis.read(buffer).also { read = it } != -1) {
                                    fos.write(buffer, 0, read)
                                    entryBytes += read
                                    totalBytes += read
                                    if (totalBytes > MAX_ZIP_BYTES) {
                                        throw SecurityException("Zip archive exceeds size limit of 50 MB")
                                    }
                                }
                            }
                            if (entry.compressedSize > 0 && entryBytes / entry.compressedSize > MAX_ZIP_RATIO) {
                                destFile.delete()
                                throw SecurityException("Suspicious compression ratio detected in entry: $name")
                            }
                            extracted.add(destFile)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return extracted
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(16384)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun parseAndVerifyFontHeader(file: File): ParsedFontHeader {
        require(file.isFile) { "Font file does not exist: ${file.path}" }
        val len = file.length()
        require(len >= 12L) { "Font file too small for SFNT header: $len bytes" }

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            val magic = raf.readInt()
            val format = when (magic) {
                0x00010000 -> "TrueType"
                0x4F54544F -> "OpenTypeCFF"
                0x74746366 -> "TrueTypeCollection"
                0x74727565 -> "TrueTypeApple"
                else -> throw IllegalArgumentException("Invalid SFNT magic header: 0x%08X".format(magic))
            }

            if (format == "TrueTypeCollection") {
                val ttcVersion = raf.readInt()
                val numFonts = raf.readInt()
                require(numFonts in 1..64) { "Invalid TTC font count: $numFonts" }
                val firstFontOffset = raf.readInt().toLong() and 0xFFFFFFFFL
                require(firstFontOffset < len) { "TTC first font offset out of bounds: $firstFontOffset" }
                raf.seek(firstFontOffset)
                val subMagic = raf.readInt()
                require(subMagic == 0x00010000 || subMagic == 0x4F54544F) { "Invalid TTC subfont magic: 0x%08X".format(subMagic) }
            }

            val numTables = raf.readUnsignedShort()
            require(numTables in 1..256) { "Invalid SFNT table count: $numTables" }
            val searchRange = raf.readUnsignedShort()
            val entrySelector = raf.readUnsignedShort()
            val rangeShift = raf.readUnsignedShort()

            val tables = HashMap<String, FontTableInfo>()
            val tagBuf = ByteArray(4)
            for (i in 0 until numTables) {
                raf.readFully(tagBuf)
                val tag = String(tagBuf, StandardCharsets.US_ASCII)
                val checkSum = raf.readInt().toLong() and 0xFFFFFFFFL
                val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                val tableLen = raf.readInt().toLong() and 0xFFFFFFFFL

                if (offset + tableLen > len) {
                    throw IllegalArgumentException("Table $tag bounds out of file range: $offset + $tableLen > $len")
                }
                tables[tag] = FontTableInfo(tag, offset, tableLen)
            }

            val required = listOf("head", "hhea", "maxp", "cmap", "name")
            val missing = required.filter { it !in tables }
            if (missing.isNotEmpty()) {
                throw IllegalArgumentException("Missing required SFNT tables: ${missing.joinToString(", ")}")
            }

            return ParsedFontHeader(format, numTables, tables)
        }
    }

    fun parseFontNameTable(file: File, nameTableInfo: FontTableInfo): Map<Int, String> {
        val result = HashMap<Int, String>()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(nameTableInfo.offset)
            val format = raf.readUnsignedShort()
            val count = raf.readUnsignedShort()
            val stringOffset = raf.readUnsignedShort()
            val storageBase = nameTableInfo.offset + stringOffset

            data class NameRec(
                val platformId: Int,
                val encodingId: Int,
                val languageId: Int,
                val nameId: Int,
                val length: Int,
                val offset: Int,
            )

            val records = ArrayList<NameRec>()
            for (i in 0 until count) {
                val pId = raf.readUnsignedShort()
                val eId = raf.readUnsignedShort()
                val lId = raf.readUnsignedShort()
                val nId = raf.readUnsignedShort()
                val len = raf.readUnsignedShort()
                val off = raf.readUnsignedShort()
                records.add(NameRec(pId, eId, lId, nId, len, off))
            }

            for (rec in records) {
                if (rec.nameId in result && rec.platformId != 3 && rec.platformId != 0) continue
                val strOffset = storageBase + rec.offset
                if (strOffset + rec.length > file.length()) continue
                raf.seek(strOffset)
                val strBytes = ByteArray(rec.length)
                raf.readFully(strBytes)

                val str = when {
                    rec.platformId == 3 || rec.platformId == 0 -> {
                        String(strBytes, StandardCharsets.UTF_16BE).trim { it <= ' ' || it == '\u0000' }
                    }
                    rec.platformId == 1 -> {
                        String(strBytes, StandardCharsets.ISO_8859_1).trim { it <= ' ' || it == '\u0000' }
                    }
                    else -> {
                        String(strBytes, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    }
                }
                if (str.isNotBlank()) {
                    result[rec.nameId] = str
                }
            }
        }
        return result
    }

    fun inspectFontFile(file: File): ParsedFontMetadata {
        val sha256 = runCatching { computeSha256(file) }.getOrDefault("")
        return try {
            val header = parseAndVerifyFontHeader(file)
            val nameInfo = header.tables["name"] ?: error("No name table")
            val nameMap = parseFontNameTable(file, nameInfo)

            val family = nameMap[1] ?: nameMap[4] ?: file.nameWithoutExtension
            val style = nameMap[2] ?: "Regular"
            val version = nameMap[5] ?: "1.0"
            val copyright = nameMap[0] ?: ""
            val license = nameMap[13] ?: nameMap[14] ?: ""
            val postScript = nameMap[6] ?: family.replace(" ", "")

            ParsedFontMetadata(
                family = family,
                style = style,
                version = version,
                copyright = copyright,
                license = license,
                postScriptName = postScript,
                sha256 = sha256,
                isValid = true,
                errorReason = null,
            )
        } catch (e: Exception) {
            ParsedFontMetadata(
                family = file.nameWithoutExtension,
                style = "Regular",
                version = "",
                copyright = "",
                license = "",
                postScriptName = file.nameWithoutExtension,
                sha256 = sha256,
                isValid = false,
                errorReason = e.message ?: "Unknown font parsing error",
            )
        }
    }
}
