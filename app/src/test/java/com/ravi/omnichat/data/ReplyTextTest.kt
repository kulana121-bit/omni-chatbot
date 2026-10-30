package com.ravi.omnichat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-1 regression: streaming must never run the full-string strip; the final pass runs once. */
class ReplyTextTest {
    @Test fun streamingTailScanHidesOnlyUnterminatedTag() {
        val raw = "answer ".repeat(100) + "[[rem"
        assertEquals("answer ".repeat(100), ReplyText.streamingText(raw))
        val done = "answer ".repeat(100) + "[[app:maps {\"query\":\"x\"}]]"
        assertEquals(done, ReplyText.streamingText(done))
    }

    @Test fun longStreamFinalEqualsOldVisibleAndFullStripRunsOnce() {
        // ~300 KB synthetic stream: text + a memory tag + an app tag + a 3-blank-line run.
        val chunk = "Hello world, this is a streamed reply. "
        val raw = buildString {
            repeat(7_500) { append(chunk) }
            append("[[remember: user likes tea]]")
            append("\n\n\n\n")
            append("[[app:weather {\"city\":\"Delhi\"}]]")
            append("Done.")
        }
        assertTrue(raw.length > 200_000)
        ReplyText.fullStripCalls = 0
        // Simulate the ~70 ms publish loop over the growing buffer.
        var i = 0
        while (i < raw.length) { ReplyText.streamingText(raw.substring(0, i)); i += 4096 }
        ReplyText.streamingText(raw)
        assertEquals("streaming must not run the full strip", 0, ReplyText.fullStripCalls)
        val expected = (chunk.repeat(7_500) + "\n\n" + "Done.")
        assertEquals(expected, ReplyText.finalText(raw))
        assertEquals("full-string regex/strip must run exactly once, at Done", 1, ReplyText.fullStripCalls)
    }

    @Test fun continuationPolicyBranches() {
        assertTrue(ContinuationPolicy.shouldAutoContinue(truncated = true, autoContinue = true, continues = 0, maxContinues = 30))
        assertFalse(ContinuationPolicy.shouldAutoContinue(truncated = true, autoContinue = false, continues = 0, maxContinues = 30))
        assertTrue(ContinuationPolicy.needsManualContinue(truncated = true, autoContinue = false, continues = 0, maxContinues = 30))
        assertFalse(ContinuationPolicy.needsManualContinue(truncated = false, autoContinue = false, continues = 0, maxContinues = 30))
        assertFalse(ContinuationPolicy.shouldAutoContinue(truncated = true, autoContinue = true, continues = 30, maxContinues = 30))
    }
}
