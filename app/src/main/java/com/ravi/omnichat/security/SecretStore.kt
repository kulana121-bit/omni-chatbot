package com.ravi.omnichat.security

import android.annotation.SuppressLint
import android.content.SharedPreferences
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption for small secrets. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/** AES-256-GCM with a random 12-byte IV prepended to the ciphertext. The key comes from [key]. */
open class AesGcmCipher(private val key: () -> SecretKey) : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        require(iv.size == 12)
        return iv + c.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 12 + 16) { "ciphertext too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }
}

/** The key lives in Android Keystore (hardware-backed where the phone supports it) and never leaves it. */
class KeystoreCipher(private val alias: String = "omnichat_secrets_v1") : AesGcmCipher({ KeystoreCipher.keyFor(alias) }) {
    companion object {
        fun keyFor(alias: String): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            return KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
        }

        fun deleteKey(alias: String = "omnichat_secrets_v1") = runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}

/**
 * Stores credentials in their own preferences file, encrypted with [cipher].
 * Values are "v1:" + base64(iv + ciphertext). When the cipher is unavailable (very rare Keystore failures)
 * values are kept as "p1:" + plain text and [encrypted] is false so the UI can warn; nothing is silently dropped.
 */
class SecretStore(private val sp: SharedPreferences, private val cipher: SecretCipher?) {
    /** Names whose stored value could not be decrypted (e.g. Keystore key invalidated). The user must re-enter them. */
    val unreadable: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    val encrypted: Boolean get() = cipher != null

    /** Decrypted values cached in memory for this process, so UI reads don't hit the Keystore on every frame. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun get(name: String): String {
        cache[name]?.let { return it }
        return read(name).also { if (it.isNotEmpty()) cache[name] = it }
    }

    private fun read(name: String): String {
        val v = sp.getString(name, null) ?: return ""
        return when {
            v.startsWith("v1:") -> runCatching {
                String(cipher!!.decrypt(Base64.getDecoder().decode(v.substring(3))), Charsets.UTF_8)
            }.onSuccess { unreadable -= name; Redactor.register(it) }.getOrElse { unreadable += name; "" }
            v.startsWith("p1:") -> v.substring(3).also { Redactor.register(it) }
            else -> ""
        }
    }

    fun has(name: String) = sp.contains(name)

    /** Returns true when the value was stored (and, if encrypted, verified by decrypting it again). */
    @SuppressLint("ApplySharedPref") // commit() on purpose: put() must report whether the write actually landed.
    fun put(name: String, value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) { remove(name); return true }
        val c = cipher
        val stored = if (c != null) {
            runCatching {
                val blob = c.encrypt(v.toByteArray(Charsets.UTF_8))
                check(String(c.decrypt(blob), Charsets.UTF_8) == v)
                "v1:" + Base64.getEncoder().encodeToString(blob)
            }.getOrNull() ?: return false
        } else "p1:$v"
        val ok = sp.edit().putString(name, stored).commit()
        if (ok) { unreadable -= name; Redactor.register(v); cache[name] = v } else cache.remove(name)
        return ok
    }

    @SuppressLint("ApplySharedPref") // commit() on purpose: a removed secret must not reappear from an unflushed apply() after process death.
    fun remove(name: String) {
        sp.getString(name, null)?.let { if (it.startsWith("p1:")) Redactor.forget(it.substring(3)) }
        cache.remove(name)?.let { Redactor.forget(it) }
        sp.edit().remove(name).commit()
        unreadable -= name
    }

    fun names(): Set<String> = sp.all.keys

    @SuppressLint("ApplySharedPref") // commit() on purpose: same durability reason as remove().
    fun clear() { cache.values.forEach { Redactor.forget(it) }; cache.clear(); sp.edit().clear().commit(); unreadable.clear() }
}
