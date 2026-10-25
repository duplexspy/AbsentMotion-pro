package app.absentmotion.effectspack

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackZipSecurityTest {

    private fun createZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun rejectsPathTraversal() {
        val evilZip = createZip(listOf(
            "../escaped.xml" to "<effect id=\"test\"/>".toByteArray()
        ))
        try {
            var entryCount = 0
            java.util.zip.ZipInputStream(ByteArrayInputStream(evilZip)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.startsWith("/") || name.contains("..") || name.contains("\\")) {
                        throw PackSecurityException("Unsafe zip entry path: $name")
                    }
                    entryCount++
                    zip.closeEntry()
                }
            }
            fail("Expected PackSecurityException for path traversal")
        } catch (e: PackSecurityException) {
            assertTrue(e.message!!.contains("Unsafe zip entry path"))
        }
    }

    @Test
    fun rejectsExcessiveEntryCount() {
        val manyEntries = (1..505).map { "effects/effect_$it.xml" to "<effect id=\"eff$it\"/>".toByteArray() }
        val zipData = createZip(manyEntries)
        try {
            var entryCount = 0
            java.util.zip.ZipInputStream(ByteArrayInputStream(zipData)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > PackStore.MAX_ENTRIES) {
                        throw PackSecurityException("Zip archive contains more than ${PackStore.MAX_ENTRIES} entries")
                    }
                    zip.closeEntry()
                }
            }
            fail("Expected PackSecurityException for excessive entries")
        } catch (e: PackSecurityException) {
            assertTrue(e.message!!.contains("more than 500 entries"))
        }
    }

    @Test
    fun rejectsOverlargeXmlEntry() {
        val hugeXml = ByteArray(3 * 1024 * 1024)
        val zipData = createZip(listOf(
            "effects/huge.xml" to hugeXml
        ))
        try {
            java.util.zip.ZipInputStream(ByteArrayInputStream(zipData)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val isXml = entry.name.endsWith(".xml")
                    val limit = if (isXml) PackStore.MAX_ENTRY_XML_BYTES else PackStore.MAX_TOTAL_BYTES
                    var read = 0L
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        read += n
                        if (read > limit) {
                            throw PackSecurityException("Entry ${entry.name} exceeded size limit")
                        }
                    }
                    zip.closeEntry()
                }
            }
            fail("Expected PackSecurityException for overlarge XML")
        } catch (e: PackSecurityException) {
            assertTrue(e.message!!.contains("exceeded size limit"))
        }
    }
}
