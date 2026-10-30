package com.ravi.omnichat.data

import android.annotation.SuppressLint
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.ravi.omnichat.security.KeystoreCipher
import com.ravi.omnichat.security.SecretCipher
import com.ravi.omnichat.security.SecretStore

/** The Keystore cipher, or null if this phone's Keystore is broken (then secrets are stored unencrypted and the UI says so). */
fun defaultCipher(): SecretCipher? = runCatching {
    KeystoreCipher().also { c -> check(String(c.decrypt(c.encrypt("probe".toByteArray()))) == "probe") }
}.getOrNull()

/**
 * Settings. Credentials (provider keys, fallback keys, GitHub token) live in [secrets], a separate file
 * encrypted with an Android Keystore key; ordinary settings stay in "omnichat" prefs.
 */
class Prefs(context: Context, cipher: SecretCipher? = defaultCipher()) {
    private val sp = context.getSharedPreferences("omnichat", Context.MODE_PRIVATE)
    val secrets = SecretStore(context.getSharedPreferences("omnichat_secrets", Context.MODE_PRIVATE), cipher)

    /** Names of credentials that could not be moved into encrypted storage (kept as they were, not erased). */
    val migrationProblems = mutableListOf<String>()

    init { migrateSecrets() }

    /**
     * One-time move of plaintext credentials into [secrets]. Each value is written, read back and compared before the
     * plaintext copy is removed, so a failure leaves the old value in place and the key keeps working.
     */
    fun migrateSecrets() {
        if (sp.getBoolean("secrets_v1_done", false)) return
        val names = sp.all.keys.filter { it.startsWith("key_") || it == "gh_token" }
        // apply(), not commit(), is enough for every edit in this migration: reads in this process see
        // applied values immediately, each value is verified in encrypted storage BEFORE its plaintext
        // copy is removed, and if the process dies before an async flush the migration simply re-runs
        // next launch (it is idempotent and the done flag below is only set once nothing failed).
        for (n in names) {
            val v = sp.getString(n, "") ?: ""
            if (v.isBlank()) { sp.edit().remove(n).apply(); continue }
            if (secrets.put(n, v) && secrets.get(n) == v) sp.edit().remove(n).apply() else migrationProblems += n
        }
        // Fallback keys were stored inside the fallbacks JSON.
        val rawFb = sp.getString("fallbacks", null)
        if (rawFb != null) runCatching {
            val arr = JSONArray(rawFb)
            var changed = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val k = o.optString("key")
                if (k.isBlank()) continue
                val name = "fb_" + o.optString("id")
                if (secrets.put(name, k) && secrets.get(name) == k) { o.put("key", ""); changed = true } else migrationProblems += name
            }
            if (changed) sp.edit().putString("fallbacks", arr.toString()).apply() // see apply() note above
        }
        // Keep existing local HTTP setups (Ollama on the LAN) working after cleartext became opt-in.
        if (!sp.contains("allow_local_http")) {
            val usesHttp = sp.all.any { (k, v) -> k.startsWith("url_") && (v as? String)?.startsWith("http://") == true } ||
                sp.getString("provider", "") == "ollama"
            sp.edit().putBoolean("allow_local_http", usesHttp).apply()
        }
        if (migrationProblems.isEmpty()) sp.edit().putBoolean("secrets_v1_done", true).apply() // see apply() note above
    }

    /** Plain HTTP to local/private servers (Ollama, LM Studio). Off by default for new installs. */
    var allowLocalHttp: Boolean
        get() = sp.getBoolean("allow_local_http", false)
        set(v) = sp.edit().putBoolean("allow_local_http", v).apply()

    /** Deletes every setting, credential and the Keystore key. */
    @SuppressLint("ApplySharedPref") // commit() on purpose: the wipe must be durable before the Keystore key is deleted, not queued.
    fun wipeAll() {
        sp.edit().clear().commit()
        secrets.clear()
        KeystoreCipher.deleteKey()
    }

    var providerId: String
        get() = sp.getString("provider", "openai") ?: "openai"
        set(v) = sp.edit().putString("provider", v).apply()

    fun apiKey(p: String): String = secrets.get("key_$p").ifEmpty { sp.getString("key_$p", "") ?: "" }
    fun setApiKey(p: String, v: String) { setSecret("key_$p", v) }

    /** Stores a credential encrypted; if that fails the value is kept in the old location so nothing is lost. */
    private fun setSecret(name: String, v: String) {
        val t = v.trim()
        if (t.isEmpty()) { secrets.remove(name); sp.edit().remove(name).apply(); return }
        if (secrets.put(name, t)) sp.edit().remove(name).apply() else sp.edit().putString(name, t).apply()
    }

    fun baseUrl(p: String) = sp.getString("url_$p", null) ?: Providers.byId(p).defaultBaseUrl
    fun setBaseUrl(p: String, v: String) = sp.edit().putString("url_$p", v.trim().trimEnd('/')).apply()

    fun model(p: String) = sp.getString("model_$p", "") ?: ""
    fun setModel(p: String, v: String) = sp.edit().putString("model_$p", v).apply()

    var customProtocol: Protocol
        get() = runCatching { Protocol.valueOf(sp.getString("custom_protocol", "OPENAI")!!) }.getOrDefault(Protocol.OPENAI)
        set(v) = sp.edit().putString("custom_protocol", v.name).apply()

    var systemPrompt: String
        get() = sp.getString("system", "You are a helpful, friendly assistant.") ?: ""
        set(v) = sp.edit().putString("system", v).apply()

    fun connectorOn(id: String) = sp.getBoolean("conn_$id", false)
    fun setConnector(id: String, v: Boolean) = sp.edit().putBoolean("conn_$id", v).apply()

    var skills: List<Skill>
        get() = runCatching {
            val a = JSONArray(sp.getString("skills", "[]"))
            (0 until a.length()).map { val o = a.getJSONObject(it); Skill(o.getString("id"), o.getString("name"), o.optString("desc"), o.optString("ins")) }
        }.getOrDefault(emptyList())
        set(v) = sp.edit().putString("skills", JSONArray().apply {
            v.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("desc", it.description).put("ins", it.instructions)) }
        }.toString()).apply()

    var githubToken: String
        get() = secrets.get("gh_token").ifEmpty { sp.getString("gh_token", "") ?: "" }
        set(v) = setSecret("gh_token", v)

    var smartWeb: Boolean
        get() = sp.getBoolean("smart_web", true)
        set(v) = sp.edit().putBoolean("smart_web", v).apply()

    var webDefault: Boolean
        get() = sp.getBoolean("web_default", false)
        set(v) = sp.edit().putBoolean("web_default", v).apply()

    var autoFallback: Boolean
        get() = sp.getBoolean("auto_fallback", true)
        set(v) = sp.edit().putBoolean("auto_fallback", v).apply()

    var autoContinue: Boolean
        get() = sp.getBoolean("auto_continue", true)
        set(v) = sp.edit().putBoolean("auto_continue", v).apply()

    /** Anthropic requires max_tokens; other providers default to the model's maximum. */
    var maxTokens: Int
        get() = sp.getInt("max_tokens", 16000)
        set(v) = sp.edit().putInt("max_tokens", v).apply()

    var thinkMode: ThinkMode
        get() = runCatching { ThinkMode.valueOf(sp.getString("think", "AUTO")!!) }.getOrDefault(ThinkMode.AUTO)
        set(v) = sp.edit().putString("think", v.name).apply()

    var memoryOn: Boolean
        get() = sp.getBoolean("memory_on", true)
        set(v) = sp.edit().putBoolean("memory_on", v).apply()

    var memories: List<Memory>
        get() = runCatching {
            val arr = JSONArray(sp.getString("memories", "[]"))
            (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Memory(o.optString("id"), o.optString("t"), o.optLong("c")) }
                .filter { it.text.isNotBlank() }
        }.getOrDefault(emptyList())
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.text).put("c", it.created)) }
            sp.edit().putString("memories", arr.toString()).apply()
        }

    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic_color", false)
        set(v) = sp.edit().putBoolean("dynamic_color", v).apply()

    var fallbacks: List<Endpoint>
        get() = runCatching {
            val arr = JSONArray(sp.getString("fallbacks", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Endpoint(
                    id = o.getString("id"),
                    providerId = o.optString("provider"),
                    protocol = runCatching { Protocol.valueOf(o.optString("protocol")) }.getOrDefault(Protocol.OPENAI),
                    baseUrl = o.optString("url"),
                    key = secrets.get("fb_" + o.getString("id")).ifEmpty { o.optString("key") },
                    model = o.optString("model"),
                    label = o.optString("label"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        }.getOrDefault(emptyList())
        set(list) {
            val arr = JSONArray()
            val keep = list.map { "fb_" + it.id }.toSet()
            secrets.names().filter { it.startsWith("fb_") && it !in keep }.forEach { secrets.remove(it) }
            list.forEach { e ->
                val stored = e.key.isBlank() || secrets.put("fb_" + e.id, e.key)
                if (e.key.isBlank()) secrets.remove("fb_" + e.id)
                arr.put(JSONObject().put("id", e.id).put("provider", e.providerId).put("protocol", e.protocol.name)
                    .put("url", e.baseUrl).put("key", if (stored) "" else e.key).put("model", e.model).put("label", e.label).put("enabled", e.enabled))
            }
            sp.edit().putString("fallbacks", arr.toString()).apply()
        }

    fun protocolFor(p: String): Protocol =
        if (p == "custom") customProtocol else Providers.byId(p).protocol
}

/** Saves conversations (text only; attachment bytes are kept in memory) to a JSON file. */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chats.json")

    fun load(): List<Conversation> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i -> runCatching {
            val o = arr.getJSONObject(i)
            val msgs = o.getJSONArray("messages")
            Conversation(
                id = o.getString("id"),
                title = o.optString("title", "Chat"),
                updated = o.optLong("updated"),
                messages = (0 until msgs.length()).map { j ->
                    val m = msgs.getJSONObject(j)
                    val att = m.optJSONArray("att") ?: JSONArray()
                    val src = m.optJSONArray("src") ?: JSONArray()
                    ChatMessage(
                        id = m.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        role = m.getString("role"),
                        content = m.optString("content"),
                        isError = m.optBoolean("err"),
                        model = m.optString("model"),
                        thinking = m.optString("think"),
                        memoryNote = m.optString("mem"),
                        thinkSecs = m.optInt("ts"),
                        appNote = m.optString("apps"),
                        attachments = (0 until att.length()).map { k ->
                            val a = att.getJSONObject(k)
                            Attachment(a.optString("name"), a.optString("mime"), text = a.optString("text").ifEmpty { null })
                        },
                        sources = (0 until src.length()).map { k ->
                            val s = src.getJSONObject(k); Source(s.optString("t"), s.optString("u"))
                        }
                    )
                }.distinctBy { it.id.ifBlank { java.util.UUID.randomUUID().toString() } }
            )
        }.getOrNull() }.distinctBy { it.id }.sortedByDescending { it.updated }
    }.getOrDefault(emptyList())

    @Synchronized
    fun wipe() {
        file.delete(); File(file.parentFile, "chats.json.tmp").delete()
    }

    @Synchronized
    fun save(list: List<Conversation>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { c ->
                val msgs = JSONArray()
                c.messages.forEach { m ->
                    msgs.put(JSONObject().apply {
                        put("id", m.id); put("role", m.role); put("content", m.content)
                        put("err", m.isError); put("model", m.model); put("think", m.thinking); put("mem", m.memoryNote); put("ts", m.thinkSecs); put("apps", m.appNote)
                        put("att", JSONArray().apply {
                            m.attachments.forEach { a ->
                                put(JSONObject().put("name", a.name).put("mime", a.mime).put("text", a.text ?: ""))
                            }
                        })
                        put("src", JSONArray().apply { m.sources.forEach { put(JSONObject().put("t", it.title).put("u", it.url)) } })
                    })
                }
                arr.put(JSONObject().put("id", c.id).put("title", c.title).put("updated", c.updated).put("messages", msgs))
            }
            // Atomic write: a kill mid-save can never leave a half-written (unreadable) file.
            val tmp = File(file.parentFile, "chats.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
