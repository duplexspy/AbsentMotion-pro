package app.absentmotion.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FontSecurityAndImportTest {

    @Test
    fun testZipSlipPathTraversalRejection() {
        val tempZip = File.createTempFile("slip-", ".zip")
        val extractDir = File(tempZip.parentFile, "slip_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val evilEntry = ZipEntry("../evil.ttf")
                    zos.putNextEntry(evilEntry)
                    zos.write("malicious payload".toByteArray())
                    zos.closeEntry()
                }
            }
            try {
                FontSecurity.safeExtractZip(tempZip, extractDir)
                fail("Expected SecurityException for zip-slip traversal")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("Zip-slip") == true)
            }
        } finally {
            tempZip.delete()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun testZipEntryCountLimitRejection() {
        val tempZip = File.createTempFile("entries-", ".zip")
        val extractDir = File(tempZip.parentFile, "entries_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    for (i in 0..105) {
                        val entry = ZipEntry("font_$i.txt")
                        zos.putNextEntry(entry)
                        zos.write("test".toByteArray())
                        zos.closeEntry()
                    }
                }
            }
            try {
                FontSecurity.safeExtractZip(tempZip, extractDir)
                fail("Expected SecurityException for entry count exceeding limit")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("too many entries") == true)
            }
        } finally {
            tempZip.delete()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun testZipBombCompressionRatioRejection() {
        val tempZip = File.createTempFile("bomb-", ".zip")
        val extractDir = File(tempZip.parentFile, "bomb_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.setLevel(Deflater.BEST_COMPRESSION)
                    val entry = ZipEntry("huge.txt")
                    zos.putNextEntry(entry)
                    val zeros = ByteArray(50_000)
                    zos.write(zeros)
                    zos.closeEntry()
                }
            }
            try {
                FontSecurity.safeExtractZip(tempZip, extractDir)
                fail("Expected SecurityException for suspicious compression ratio")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("compression ratio") == true)
            }
        } finally {
            tempZip.delete()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun testSafeZipExtractionAllowedFilesOnly() {
        val tempZip = File.createTempFile("safe-", ".zip")
        val extractDir = File(tempZip.parentFile, "safe_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val ttf = ZipEntry("font.ttf")
                    zos.putNextEntry(ttf)
                    zos.write("ttf data".toByteArray())
                    zos.closeEntry()

                    val readme = ZipEntry("readme.txt")
                    zos.putNextEntry(readme)
                    zos.write("readme data".toByteArray())
                    zos.closeEntry()

                    val exe = ZipEntry("payload.exe")
                    zos.putNextEntry(exe)
                    zos.write("exe data".toByteArray())
                    zos.closeEntry()
                }
            }

            val extracted = FontSecurity.safeExtractZip(tempZip, extractDir)
            assertEquals(2, extracted.size)
            assertTrue(extracted.any { it.name == "font.ttf" })
            assertTrue(extracted.any { it.name == "readme.txt" })
            assertFalse(extracted.any { it.name == "payload.exe" })
        } finally {
            tempZip.delete()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun testDafontUrlAllowlist() {
        assertTrue(FontSecurity.isDafontUrl("https://www.dafont.com/"))
        assertTrue(FontSecurity.isDafontUrl("https://dafont.com/theme.php?cat=101"))
        assertTrue(FontSecurity.isDafontUrl("https://dl.dafont.com/dl/?f=test_font"))
        assertTrue(FontSecurity.isDafontUrl("https://img.dafont.com/preview.png"))
        assertTrue(FontSecurity.isDafontUrl("http://www.dafont.com/"))

        assertFalse(FontSecurity.isDafontUrl("https://google.com"))
        assertFalse(FontSecurity.isDafontUrl("https://evil-dafont.com"))
        assertFalse(FontSecurity.isDafontUrl("https://dafont.com.attacker.com"))
        assertFalse(FontSecurity.isDafontUrl("javascript:alert(1)"))
        assertFalse(FontSecurity.isDafontUrl("file:///data/data/app/files"))
        assertFalse(FontSecurity.isDafontUrl(""))
    }

    @Test
    fun testCleanFontFileName() {
        assertEquals("my_custom_font.ttf", FontSecurity.cleanFontFileName("my custom font.ttf"))
        assertEquals("cool_font.otf", FontSecurity.cleanFontFileName("../../cool@font!.otf"))
        assertEquals("font.ttf", FontSecurity.cleanFontFileName("$$$###.ttf"))
        assertEquals("simple.ttf", FontSecurity.cleanFontFileName("simple.TTF"))
    }

    @Test
    fun testFontHeaderValidationRealAsset() {
        val assetFont = File("app/src/main/assets/fonts/AbrilFatface/AbrilFatface-Regular.ttf")
        if (assetFont.isFile) {
            val header = FontSecurity.parseAndVerifyFontHeader(assetFont)
            assertEquals("TrueType", header.format)
            assertTrue(header.numTables > 5)
            assertTrue("head" in header.tables)
            assertTrue("name" in header.tables)
            assertTrue("cmap" in header.tables)

            val inspect = FontSecurity.inspectFontFile(assetFont)
            assertTrue(inspect.isValid)
            assertEquals("Abril Fatface", inspect.family)
            assertEquals("Regular", inspect.style)
            assertTrue(inspect.sha256.isNotBlank())
        }
    }

    @Test
    fun testCorruptedFontHeaderRejection() {
        val corrupt = File.createTempFile("corrupt-", ".ttf")
        try {
            corrupt.writeBytes(ByteArray(64) { 0 })
            val inspect = FontSecurity.inspectFontFile(corrupt)
            assertFalse(inspect.isValid)
            assertNotNull(inspect.errorReason)
        } finally {
            corrupt.delete()
        }
    }

    @Test
    fun testSafeModeCleanReversion() {
        assertTrue(ProFeatures.isEnabled(ProFeatures.dafontBrowser))
        ProFeatures.dafontBrowser = false
        assertFalse(ProFeatures.isEnabled(ProFeatures.dafontBrowser))
        ProFeatures.dafontBrowser = true
        assertTrue(ProFeatures.isEnabled(ProFeatures.dafontBrowser))
    }
}
