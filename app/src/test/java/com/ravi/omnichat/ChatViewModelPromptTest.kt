package com.ravi.omnichat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-4: a @mention of a disabled connector adds an "enable it" prompt line, never an enable. */
class ChatViewModelPromptTest {
    @Test fun disabledMentionGetsPromptLine() {
        val out = ChatViewModel.disabledMentionLines(listOf("contacts"), emptySet())
        assertEquals("\n@contacts is disabled; enable it in Apps & Skills", out)
    }

    @Test fun enabledMentionGetsNoLine() {
        assertEquals("", ChatViewModel.disabledMentionLines(listOf("contacts"), setOf("contacts")))
    }

    @Test fun mixedMentionsOnlyDisabledAreNamed() {
        val out = ChatViewModel.disabledMentionLines(listOf("contacts", "weather"), setOf("weather"))
        assertTrue(out.contains("@contacts is disabled"))
        assertTrue(!out.contains("@weather"))
    }
}
