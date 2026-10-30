package com.ravi.omnichat.security

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class FileTooLargeException(val limit: Long) : IllegalArgumentException("File is larger than ${limit / 1_000_000} MB")
class ReadCancelledException : IllegalStateException("Reading was cancelled")

/** Reads streams with limits enforced while reading, never trusting reported sizes. Pure JVM for unit tests. */
object SafeIO {
    const val MAX_IMAGE = 25_000_000L
    const val MAX_PDF = 20_000_000L
    const val MAX_VIDEO_NATIVE = 20_000_000L
    const val MAX_TEXT_READ = 1_000_000L
    const val MAX_SKILL_FILE = 200_000L
    /** Total size of attachments waiting to be sent in one message (encoded). */
    const val MAX_PENDING_TOTAL = 45_000_000L

    /** Reads everything, throwing [FileTooLargeException] as soon as more than [max] bytes arrive. */
    fun readBounded(input: InputStream, max: Long, isActive: () -> Boolean = { true }): ByteArray {
        val out = ByteArrayOutputStream(minOf(max, 64 * 1024L).toInt())
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            if (!isActive()) throw ReadCancelledException()
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw FileTooLargeException(max)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Reads at most [max] bytes; the flag says whether the stream had more. */
    fun readPrefix(input: InputStream, max: Int, isActive: () -> Boolean = { true }): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            if (!isActive()) throw ReadCancelledException()
            val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) return out.toByteArray() to false
            out.write(buf, 0, n)
        }
        val more = input.read() >= 0
        return out.toByteArray() to more
    }

    fun isPdf(b: ByteArray) = b.size >= 5 && String(b, 0, 5, Charsets.ISO_8859_1) == "%PDF-"

    /** Text if it has no NUL bytes in the first 8 KB and decodes as UTF-8 without too many replacement chars. */
    fun looksLikeText(b: ByteArray): Boolean {
        val head = b.copyOf(minOf(b.size, 8192))
        if (head.any { it == 0.toByte() }) return false
        val s = String(head, Charsets.UTF_8)
        return s.count { it == '\uFFFD' } <= s.length / 50 + 2
    }

    /** True for archive entry names that would escape a target folder or are absolute. */
    fun isUnsafeEntryName(name: String): Boolean {
        val n = name.replace('\\', '/')
        return n.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(n) || n.split('/').any { it == ".." } || n.any { it.code < 0x20 }
    }

    data class ZipLimits(
        val maxEntries: Int = 3000,
        val maxTotalInflated: Long = 60_000_000,
        val maxEntryRead: Int = 400_000,
        val maxTextTotal: Int = 150_000,
        val maxRatio: Long = 150,
        val maxMillis: Long = 20_000
    )

    data class ZipSummary(val text: String, val files: Int, val included: Int, val skipped: Int, val unsafe: Int, val truncated: Boolean)

    private val textExt = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "kt", "java", "py", "js", "ts", "c", "cpp", "h", "hpp",
        "cs", "go", "rs", "rb", "php", "swift", "dart", "sql", "yaml", "yml", "toml", "ini", "log", "sh", "bat", "css", "scss", "gradle", "kts",
        "tsx", "jsx", "vue", "svelte", "properties", "cfg", "conf", "env", "gitignore", "pro", "r", "lua", "pl", "m", "mm", "srt", "tex", "rst")

    /**
     * Streams a ZIP and builds a text overview (file tree + text file contents) without writing anything to disk.
     * Every inflated byte, including skipped entries, counts towards [ZipLimits.maxTotalInflated], so ZIP bombs stop early.
     */
    fun summarizeZip(input: InputStream, name: String, limits: ZipLimits = ZipLimits(), now: () -> Long = System::currentTimeMillis, isActive: () -> Boolean = { true }): ZipSummary {
        val started = now()
        val tree = StringBuilder()
        val body = StringBuilder()
        var files = 0; var included = 0; var skipped = 0; var unsafe = 0; var inflated = 0L
        var truncated = false
        val buf = ByteArray(16 * 1024)
        ZipInputStream(input.buffered()).use { zin ->
            while (true) {
                if (!isActive()) throw ReadCancelledException()
                if (now() - started > limits.maxMillis) throw IllegalArgumentException("$name took too long to read")
                val e = try { zin.nextEntry } catch (ex: Exception) { throw IllegalArgumentException("$name is not a valid ZIP (or is password-protected)") } ?: break
                if (e.isDirectory) continue
                val path = e.name
                if (path.startsWith("__MACOSX/") || path.substringAfterLast('/').startsWith("._")) { drainOrBomb(zin, buf, name) { inflated += it; inflated <= limits.maxTotalInflated }; continue }
                files++
                if (files > limits.maxEntries) throw IllegalArgumentException("$name has too many files (over ${limits.maxEntries})")
                if (isUnsafeEntryName(path)) { unsafe++; tree.append("- [unsafe path skipped]\n"); drainOrBomb(zin, buf, name) { inflated += it; inflated <= limits.maxTotalInflated }; continue }
                if (files <= 400) tree.append("- ").append(path).append(if (e.size > 0) " (${e.size / 1024} KB)" else "").append('\n')
                val base = path.substringAfterLast('/')
                val fext = (if (base.startsWith(".")) base.drop(1) else base.substringAfterLast('.', "")).lowercase()
                val textual = fext in textExt || base.lowercase() in setOf("dockerfile", "makefile", "readme", "license")
                if (textual && body.length < limits.maxTextTotal && (e.size < 0 || e.size < limits.maxEntryRead)) {
                    val out = ByteArrayOutputStream()
                    var entryBytes = 0L
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        entryBytes += n; inflated += n
                        if (inflated > limits.maxTotalInflated) throw IllegalArgumentException("$name expands to more than ${limits.maxTotalInflated / 1_000_000} MB; not opened")
                        if (out.size() < limits.maxEntryRead) out.write(buf, 0, minOf(n, limits.maxEntryRead - out.size()))
                    }
                    if (e.compressedSize > 0 && entryBytes / e.compressedSize > limits.maxRatio && entryBytes > 1_000_000)
                        throw IllegalArgumentException("$name looks like a ZIP bomb; not opened")
                    val bytes = out.toByteArray()
                    if (looksLikeText(bytes)) {
                        val room = limits.maxTextTotal - body.length
                        val txt = String(bytes, Charsets.UTF_8)
                        if (txt.length > room) truncated = true
                        body.append("\n===== ").append(path).append(" =====\n").append(txt.take(room)).append('\n')
                        included++
                    } else skipped++
                } else {
                    skipped++
                    drainOrBomb(zin, buf, name) { inflated += it; inflated <= limits.maxTotalInflated }
                }
            }
        }
        if (files == 0) throw IllegalArgumentException("$name is empty")
        if (files > 400) tree.append("… and ${files - 400} more files\n")
        val text = "ZIP archive: $name\n$files files ($included text files included" +
            (if (skipped > 0) ", $skipped binary/large files listed only" else "") +
            (if (unsafe > 0) ", $unsafe entries with unsafe paths ignored" else "") + ")" +
            (if (truncated || body.length >= limits.maxTextTotal) ", contents truncated to fit" else "") + "\n\nFile tree:\n$tree\nContents:$body"
        return ZipSummary(text, files, included, skipped, unsafe, truncated)
    }

    private inline fun drainOrBomb(zin: ZipInputStream, buf: ByteArray, name: String, ok: (Int) -> Boolean) {
        while (true) {
            val n = zin.read(buf); if (n < 0) break
            if (!ok(n)) throw IllegalArgumentException("$name expands to too much data; not opened")
        }
    }
}
