package com.ravi.omnichat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ravi.omnichat.security.AesGcmCipher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
class PrefsMigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCipher { key }
    private val old get() = ctx.getSharedPreferences("omnichat", Context.MODE_PRIVATE)

    @Before fun reset() {
        old.edit().clear().commit()
        ctx.getSharedPreferences("omnichat_secrets", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun plaintextKeysMoveToEncryptedStoreAndSettingsSurvive() {
        old.edit().putString("key_openai", "sk-old-plaintext-key").putString("gh_token", "ghp_oldtokenvalue1")
            .putString("provider", "openai").putString("system", "Be brief").putString("model_openai", "gpt-4o-mini")
            .putString("fallbacks", """[{"id":"f1","provider":"groq","protocol":"OPENAI","url":"https://api.groq.com/openai/v1","key":"gsk_fallbackkey123","model":"llama","label":"","enabled":true}]""")
            .commit()
        val p = Prefs(ctx, cipher)
        assertEquals("sk-old-plaintext-key", p.apiKey("openai"))
        assertEquals("ghp_oldtokenvalue1", p.githubToken)
        assertEquals("gsk_fallbackkey123", p.fallbacks.single().key)
        // plaintext copies are gone, ordinary settings untouched
        assertFalse(old.contains("key_openai")); assertFalse(old.contains("gh_token"))
        assertFalse(old.getString("fallbacks", "")!!.contains("gsk_fallbackkey123"))
        assertEquals("Be brief", p.systemPrompt)
        assertEquals("gpt-4o-mini", p.model("openai"))
        // and a fresh instance (app restart) still reads them
        assertEquals("sk-old-plaintext-key", Prefs(ctx, cipher).apiKey("openai"))
    }

    @Test fun failedEncryptionKeepsOriginalValue() {
        old.edit().putString("key_gemini", "AIza-keep-me-123456789").commit()
        val broken = object : com.ravi.omnichat.security.SecretCipher {
            override fun encrypt(plain: ByteArray): ByteArray = throw IllegalStateException("keystore broken")
            override fun decrypt(blob: ByteArray): ByteArray = throw IllegalStateException("keystore broken")
        }
        val p = Prefs(ctx, broken)
        assertEquals("AIza-keep-me-123456789", p.apiKey("gemini"))
        assertTrue("key_gemini" in p.migrationProblems)
    }

    @Test fun clearingAKeyRemovesIt() {
        val p = Prefs(ctx, cipher)
        p.setApiKey("openai", "sk-to-be-removed-1")
        p.setApiKey("openai", "")
        assertEquals("", p.apiKey("openai"))
    }

    @Test fun existingOllamaUsersKeepLocalHttp() {
        old.edit().putString("provider", "ollama").putString("url_ollama", "http://192.168.1.10:11434/v1").commit()
        assertTrue(Prefs(ctx, cipher).allowLocalHttp)
    }

    @Test fun newInstallsHaveLocalHttpOff() {
        assertFalse(Prefs(ctx, cipher).allowLocalHttp)
    }

    @Test fun settingsPersist() {
        Prefs(ctx, cipher).apply { smartWeb = false; maxTokens = 1234 }
        val p = Prefs(ctx, cipher)
        assertFalse(p.smartWeb); assertEquals(1234, p.maxTokens)
    }
}
