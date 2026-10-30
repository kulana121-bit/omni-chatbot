package com.ravi.omnichat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
class ChatStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val file get() = File(ctx.filesDir, "chats.json")

    @Before fun clean() { file.delete() }

    private fun conv(id: String, n: Int, updated: Long) = Conversation(id, "Chat $id", updated,
        (1..n).map { ChatMessage(id = "$id-$it", role = if (it % 2 == 1) "user" else "assistant", content = "m$it") })

    @Test fun roundTripKeepsOrder() {
        val s = ChatStore(ctx)
        s.save(listOf(conv("a", 6, 2), conv("b", 3, 5)))
        val loaded = ChatStore(ctx).load()
        assertEquals(listOf("b", "a"), loaded.map { it.id })
        assertEquals((1..6).map { "m$it" }, loaded.first { it.id == "a" }.messages.map { it.content })
    }

    @Test fun deletionIsPersisted() {
        val s = ChatStore(ctx)
        s.save(listOf(conv("a", 2, 1), conv("b", 2, 2)))
        s.save(ChatStore(ctx).load().filter { it.id != "a" })
        assertEquals(listOf("b"), ChatStore(ctx).load().map { it.id })
    }

    @Test fun malformedFileDoesNotCrash() {
        file.writeText("{not json")
        assertTrue(ChatStore(ctx).load().isEmpty())
        file.writeText("""[{"id":"ok","title":"t","updated":1,"messages":[{"role":"user","content":"hi"}]},{"broken":true}]""")
        val l = ChatStore(ctx).load()
        assertEquals(listOf("ok"), l.map { it.id })
    }

    @Test fun duplicateMessagesDropped() {
        val c = Conversation("d", "t", 1, listOf(ChatMessage(id = "x", role = "user", content = "1"), ChatMessage(id = "x", role = "user", content = "1")))
        ChatStore(ctx).save(listOf(c))
        assertEquals(1, ChatStore(ctx).load().single().messages.size)
    }

    @Test fun wipeRemovesFile() {
        ChatStore(ctx).save(listOf(conv("a", 1, 1)))
        ChatStore(ctx).wipe()
        assertFalse(file.exists())
        assertTrue(ChatStore(ctx).load().isEmpty())
    }
}
