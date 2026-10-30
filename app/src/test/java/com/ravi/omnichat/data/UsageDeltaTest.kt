package com.ravi.omnichat.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** P1-5: a dropped stream flushed in the catch path must not be counted again after resume. */
class UsageDeltaTest {
    @Test fun resumeRecordsOnlyDelta() {
        var flushed = Counter()
        val partial = Counter(1, 100, 50) // flushed from the catch block when the stream dropped
        assertEquals(partial, UsageDelta.delta(flushed, partial))
        flushed = UsageDelta.flushed(flushed, partial)
        val resumedTotal = Counter(1, 120, 80) // same logical request, now finished
        assertEquals(Counter(0, 20, 30), UsageDelta.delta(flushed, resumedTotal))
        flushed = UsageDelta.flushed(flushed, resumedTotal)
        // Flushing the same totals again (e.g. a second publish) records nothing.
        assertEquals(Counter(0, 0, 0), UsageDelta.delta(flushed, resumedTotal))
    }

    @Test fun smallerResumeTotalNeverGoesNegative() {
        val flushed = Counter(1, 100, 50)
        assertEquals(Counter(0, 0, 0), UsageDelta.delta(flushed, Counter(1, 90, 40)))
    }

    @Test fun firstFlushCountsTheRequestOnce() {
        assertEquals(1, UsageDelta.delta(Counter(), Counter(1, 10, 5)).requests)
        assertEquals(0, UsageDelta.delta(Counter(1, 0, 0), Counter(1, 10, 5)).requests)
    }
}
