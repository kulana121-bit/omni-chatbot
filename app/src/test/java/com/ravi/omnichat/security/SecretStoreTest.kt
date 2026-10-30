package com.ravi.omnichat.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

/** Uses the real AES-GCM code path with an in-memory key (Android Keystore isn't available in unit tests). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
class SecretStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun store(name: String = "s", k: javax.crypto.SecretKey = key) =
        SecretStore(ctx.getSharedPreferences(name, Context.MODE_PRIVATE), AesGcmCipher { k })

    @Test fun roundTripAndCiphertextOnDisk() {
        val s = store()
        assertTrue(s.put("key_openai", "sk-proj-secretvalue123"))
        assertEquals("sk-proj-secretvalue123", s.get("key_openai"))
        val raw = ctx.getSharedPreferences("s", Context.MODE_PRIVATE).getString("key_openai", "")!!
        assertTrue(raw.startsWith("v1:"))
        assertFalse(raw.contains("secretvalue"))
    }

    @Test fun removeClears() {
        val s = store()
        s.put("gh_token", "ghp_abcdefghijklmnop")
        s.remove("gh_token")
        assertEquals("", s.get("gh_token"))
        assertFalse(s.has("gh_token"))
    }

    @Test fun wrongKeyDoesNotCrashAndIsReported() {
        store("w").put("key_x", "value-123456")
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val s2 = store("w", other)
        assertEquals("", s2.get("key_x"))
        assertTrue("key_x" in s2.unreadable)
    }

    @Test fun noCipherKeepsValueAndSaysSo() {
        val s = SecretStore(ctx.getSharedPreferences("p", Context.MODE_PRIVATE), null)
        assertFalse(s.encrypted)
        assertTrue(s.put("k", "plain-value-1"))
        assertEquals("plain-value-1", s.get("k"))
    }
}
