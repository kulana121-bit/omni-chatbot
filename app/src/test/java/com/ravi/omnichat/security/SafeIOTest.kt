package com.ravi.omnichat.security

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SafeIOTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return bos.toByteArray()
    }

    /** A stream that claims nothing about its size and is endless. */
    private class Endless : InputStream() { override fun read() = 'a'.code; override fun read(b: ByteArray, o: Int, l: Int): Int { b.fill('a'.code.toByte(), o, o + l); return l } }

    @Test fun boundedReadStopsOnOversizedStream() {
        try { SafeIO.readBounded(Endless(), 1_000_000); fail() } catch (e: FileTooLargeException) { }
    }

    @Test fun zeroByteFile() {
        assertEquals(0, SafeIO.readBounded(ByteArrayInputStream(ByteArray(0)), 10).size)
    }

    @Test fun prefixReadReportsTruncation() {
        val (b, more) = SafeIO.readPrefix(ByteArrayInputStream(ByteArray(100) { 1 }), 10)
        assertEquals(10, b.size); assertTrue(more)
        val (b2, more2) = SafeIO.readPrefix(ByteArrayInputStream(ByteArray(5) { 1 }), 10)
        assertEquals(5, b2.size); assertFalse(more2)
    }

    @Test fun cancellationDuringRead() {
        var calls = 0
        try { SafeIO.readBounded(Endless(), Long.MAX_VALUE) { ++calls < 3 }; fail() } catch (e: ReadCancelledException) { }
    }

    @Test fun contentSniffing() {
        assertTrue(SafeIO.isPdf("%PDF-1.7\n".toByteArray()))
        assertFalse(SafeIO.isPdf("<html>".toByteArray()))
        assertTrue(SafeIO.looksLikeText("hello world ✓".toByteArray()))
        assertFalse(SafeIO.looksLikeText(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0, 0, 1)))
    }

    @Test fun normalZip() {
        val s = SafeIO.summarizeZip(ByteArrayInputStream(zip("src/a.kt" to "fun a() = 1".toByteArray(), "img.png" to ByteArray(50) { 7 })), "p.zip")
        assertEquals(2, s.files); assertEquals(1, s.included)
        assertTrue(s.text.contains("fun a() = 1"))
    }

    @Test fun traversalEntriesIgnored() {
        val s = SafeIO.summarizeZip(ByteArrayInputStream(zip("../../evil.sh" to "rm -rf".toByteArray(), "/abs.txt" to "x".toByteArray(), "ok.txt" to "fine".toByteArray())), "t.zip")
        assertEquals(2, s.unsafe)
        assertFalse(s.text.contains("rm -rf"))
        assertTrue(SafeIO.isUnsafeEntryName("a\\..\\..\\b"))
        assertTrue(SafeIO.isUnsafeEntryName("C:/Windows/x"))
        assertFalse(SafeIO.isUnsafeEntryName("dir/file..txt"))
    }

    @Test fun zipBombStopsEarly() {
        // ~5 MB of zeros per entry compresses to a few KB; the inflated-total limit must stop it.
        val big = ByteArray(5_000_000)
        val bomb = zip(*(1..10).map { "z$it.bin" to big }.toTypedArray())
        assertTrue(bomb.size < 200_000)
        try {
            SafeIO.summarizeZip(ByteArrayInputStream(bomb), "bomb.zip", SafeIO.ZipLimits(maxTotalInflated = 20_000_000)); fail()
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("bomb.zip")) }
    }

    @Test fun tooManyEntries() {
        val many = zip(*(1..50).map { "f$it.txt" to "x".toByteArray() }.toTypedArray())
        try { SafeIO.summarizeZip(ByteArrayInputStream(many), "m.zip", SafeIO.ZipLimits(maxEntries = 10)); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun corruptedZip() {
        try { SafeIO.summarizeZip(ByteArrayInputStream("PK\u0003\u0004garbage-garbage".toByteArray()), "c.zip"); fail() } catch (e: IllegalArgumentException) { }
        try { SafeIO.summarizeZip(ByteArrayInputStream(ByteArray(0)), "e.zip"); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun slowZipTimesOut() {
        var t = 0L
        val z = zip(*(1..5).map { "f$it.txt" to "x".toByteArray() }.toTypedArray())
        try { SafeIO.summarizeZip(ByteArrayInputStream(z), "s.zip", SafeIO.ZipLimits(maxMillis = 10), now = { t += 6; t }); fail() } catch (e: IllegalArgumentException) { }
    }
}
