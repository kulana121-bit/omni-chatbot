package com.ravi.omnichat.data

import com.ravi.omnichat.security.ToolTagParser

/**
 * Display text for a model reply.
 *
 * Streaming updates happen every ~70 ms. Running the full strip (tag regex + tool-tag parse +
 * blank-line collapse) on the whole buffer for every update is O(n^2): at the 600k-char reply cap
 * it janks, times out and can cut replies. So:
 *  - [streamingText] does NO full-string work. It only tail-scans the last [TAIL] chars for an
 *    unterminated "[[", so a half-typed tag never flashes on screen.
 *  - [finalText] is the only full-string pass, and it runs once, on StreamEvent.Done / the final
 *    message. Its result for a complete reply is exactly what the old per-update visible() produced.
 */
object ReplyText {
    const val TAIL = 256

    /** Test hook: how many full-string strip passes have run. Streaming must never increase this. */
    var fullStripCalls = 0

    private val tagRx = Regex("""\[\[(remember|forget):\s*(.+?)]]""", RegexOption.IGNORE_CASE)
    private val blankLines = Regex("\n{3,}")
    private val tagPrefixes = listOf("[[remember:", "[[forget:", "[[app:")

    private fun hideUnterminated(t: String): String {
        val open = t.lastIndexOf("[[")
        if (open < 0 || t.indexOf("]]", open) >= 0) return t
        val tail = t.substring(open).lowercase()
        return if (tagPrefixes.any { it.startsWith(tail) || tail.startsWith(it.dropLast(1)) }) t.substring(0, open) else t
    }

    /** Cheap streaming view: no regex over the buffer, only the last [TAIL] chars are inspected. */
    fun streamingText(raw: String): String {
        if (raw.length <= TAIL) return hideUnterminated(raw)
        val tailStart = raw.length - TAIL
        val tail = raw.substring(tailStart)
        val open = tail.lastIndexOf("[[")
        if (open < 0 || tail.indexOf("]]", open) >= 0) return raw
        val t = tail.substring(open).lowercase()
        return if (tagPrefixes.any { it.startsWith(t) || t.startsWith(it.dropLast(1)) }) raw.substring(0, tailStart + open) else raw
    }

    /** The one full-string pass: strip memory/app tags, hide an unterminated tag, collapse 3+ blank lines. */
    fun finalText(raw: String): String {
        fullStripCalls++
        val t = hideUnterminated(ToolTagParser.strip(tagRx.replace(raw, "")))
        return t.replace(blankLines, "\n\n").trim()
    }
}

/** Whether a reply the provider cut off for length should be continued automatically. */
object ContinuationPolicy {
    fun shouldAutoContinue(truncated: Boolean, autoContinue: Boolean, continues: Int, maxContinues: Int): Boolean =
        truncated && autoContinue && continues < maxContinues

    /** A truncated reply with auto-continue off must surface a manual Continue affordance, never just stop silently. */
    fun needsManualContinue(truncated: Boolean, autoContinue: Boolean, continues: Int, maxContinues: Int): Boolean =
        truncated && !autoContinue && continues < maxContinues
}
