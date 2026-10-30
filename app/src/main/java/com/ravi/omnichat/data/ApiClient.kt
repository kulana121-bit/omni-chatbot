package com.ravi.omnichat.data

import com.ravi.omnichat.security.boundedString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0, val kind: ErrorKind = ProviderErrors.kindForStatus(code)) : Exception(message)

/** Streaming events: a piece of text, or the end of the reply with the provider's finish reason. */
sealed class StreamEvent {
    data class Text(val text: String) : StreamEvent()
    data class Thinking(val text: String) : StreamEvent()
    /** truncated = the provider stopped because of the output-token limit. */
    data class Done(val truncated: Boolean) : StreamEvent()
    /** Token counts the provider reported (cumulative for this reply). */
    data class Usage(val input: Long, val output: Long) : StreamEvent()
    /** Response headers, for rate-limit info. */
    data class Headers(val map: Map<String, String>) : StreamEvent()
}

/** A message as it will be sent to the API (text may include injected web context). */
data class ApiMessage(val role: String, val text: String, val attachments: List<Attachment>)

class ApiClient(
    /** Whether the user allowed plain HTTP to local servers (Ollama / LM Studio). */
    private val allowLocalHttp: () -> Boolean = { false }
) {
    // Redirects are never followed: API endpoints don't redirect, and following one could send the key
    // (x-api-key / x-goog-api-key headers are not stripped by OkHttp) to another host or downgrade to HTTP.
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Non-streaming calls fail fast; long streams use streamHttp's own, longer read timeout below,
        // so a slow provider cannot hold every request (model lists, warm-ups) for minutes.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .addInterceptor(com.ravi.omnichat.security.NetGuard(com.ravi.omnichat.security.NetGuard.Mode.AI, allowLocalHttp))
        .build()
    private val shortHttp = http.newBuilder().callTimeout(45, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    /** Streaming-only client: a long reply may pause between chunks, so its read timeout is far longer than the shared client's. */
    private val streamHttp = http.newBuilder().readTimeout(600, TimeUnit.SECONDS).build()

    /** Throws a CONFIG error before any request (and any key) goes to an endpoint the policy rejects. */
    private fun checkEndpoint(baseUrl: String) {
        com.ravi.omnichat.security.EndpointPolicy.checkAiEndpoint(baseUrl, allowLocalHttp())?.let { throw ApiException(it, kind = ErrorKind.CONFIG) }
    }

    /** Cheap request that leaves a warm (DNS + TLS done) connection in the pool. */
    fun warm(baseUrl: String) {
        if (com.ravi.omnichat.security.EndpointPolicy.checkAiEndpoint(baseUrl, allowLocalHttp()) != null) return
        runCatching {
            val url = baseUrl.trimEnd('/').toHttpUrlOrNull() ?: return
            val root = url.newBuilder().encodedPath("/").query(null).build()
            shortHttp.newCall(Request.Builder().url(root).head().build()).execute().close()
        }
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private companion object {
        const val MAX_MODEL_PAGES = 20
        const val MAX_JSON_BODY = 8_000_000L
        const val MAX_LINE = 4_000_000L
    }

    // ---------------------------------------------------------------- models

    suspend fun listModels(protocol: Protocol, baseUrl: String, key: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            checkEndpoint(baseUrl)
            val base = baseUrl.trimEnd('/')
            when (protocol) {
                Protocol.OPENAI -> {
                    val req = Request.Builder().url("$base/models").get().apply {
                        if (key.isNotBlank()) header("Authorization", "Bearer $key")
                    }.build()
                    val body = execute(req)
                    val trimmed = body.trim()
                    val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else {
                        val o = JSONObject(trimmed)
                        o.optJSONArray("data") ?: o.optJSONArray("models") ?: JSONArray()
                    }
                    (0 until arr.length()).mapNotNull { i ->
                        val m = arr.optJSONObject(i) ?: return@mapNotNull arr.optString(i).takeIf { it.isNotBlank() }?.let { ModelInfo(it) }
                        val id = m.optString("id").ifBlank { m.optString("name") }
                        if (id.isBlank()) null else ModelInfo(id, m.optString("name").ifBlank { id })
                    }.distinctBy { it.id }.sortedBy { it.id.lowercase() }
                }

                Protocol.GEMINI -> {
                    val out = mutableListOf<ModelInfo>()
                    var token: String? = null
                    // Bounded paging (P1-6): a misbehaving server can repeat the same nextPageToken
                    // forever, so cap the pages and stop as soon as a token repeats.
                    var pages = 0
                    val seenTokens = mutableSetOf<String>()
                    while (true) {
                        pages++
                        val url = "$base/models?pageSize=1000" + (token?.let { "&pageToken=$it" } ?: "")
                        val req = Request.Builder().url(url).header("x-goog-api-key", key).get().build()
                        val o = JSONObject(execute(req))
                        val arr = o.optJSONArray("models") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val m = arr.getJSONObject(i)
                            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                            if (!methods.contains("generateContent")) continue
                            val id = m.optString("name").removePrefix("models/")
                            out += ModelInfo(id, m.optString("displayName").ifBlank { id })
                        }
                        val next = o.optString("nextPageToken").ifBlank { null } ?: break
                        if (pages >= MAX_MODEL_PAGES || !seenTokens.add(next)) break
                        token = next
                    }
                    out.sortedBy { it.id }
                }

                Protocol.ANTHROPIC -> {
                    val req = Request.Builder().url("$base/models?limit=1000")
                        .header("x-api-key", key)
                        .header("anthropic-version", "2023-06-01")
                        .get().build()
                    val arr = JSONObject(execute(req)).optJSONArray("data") ?: JSONArray()
                    (0 until arr.length()).map {
                        val m = arr.getJSONObject(it)
                        ModelInfo(m.getString("id"), m.optString("display_name").ifBlank { m.getString("id") })
                    }
                }
            }
        }

    private fun execute(req: Request): String {
        shortHttp.newCall(req).execute().use { r ->
            val src = r.body?.source()
            if (src != null && src.request(MAX_JSON_BODY)) throw ApiException("Response too large", r.code, ErrorKind.SERVER)
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) throw ApiException(errorText(r.code, body), r.code, ProviderErrors.kindForStatus(r.code, body))
            return body
        }
    }

    private fun errorText(code: Int, body: String): String {
        val msg = runCatching {
            val t = body.trim()
            val o = if (t.startsWith("[")) JSONArray(t).getJSONObject(0) else JSONObject(t)
            val e = o.opt("error")
            when (e) {
                is JSONObject -> e.optString("message").ifBlank { e.toString() }
                is String -> e
                else -> o.optString("message").ifBlank { o.optString("detail") }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(300)
        val safe = com.ravi.omnichat.security.Redactor.redact(msg).take(300)
        val hint = when (code) {
            401, 403 -> " (check your API key)"
            404 -> " (check base URL / model)"
            in 300..399 -> " (the endpoint redirected; redirects aren't followed so your key stays with the URL you entered. Use the final URL)"
            429 -> " (rate limit or quota exceeded)"
            else -> ""
        }
        return "HTTP $code: $safe$hint"
    }

    // ---------------------------------------------------------------- chat

    fun streamChat(
        protocol: Protocol,
        baseUrl: String,
        key: String,
        model: String,
        system: String,
        messages: List<ApiMessage>,
        maxTokens: Int = 16000,
        think: ThinkMode = ThinkMode.AUTO
    ): Flow<StreamEvent> = flow {
        checkEndpoint(baseUrl)
        if (model.isBlank() || model.length > 200 || model.any { it.isWhitespace() || it.code < 0x20 })
            throw ApiException("Invalid model name", kind = ErrorKind.CONFIG)
        val base = baseUrl.trimEnd('/')
        // Try the full request first; if the model rejects the thinking options or a large
        // max_tokens, quietly retry with safer settings instead of failing.
        var curThink = think
        var curTokens = maxTokens
        var wantUsage = true
        var response: Response? = null
        var attempts = 0
        while (response == null) {
            attempts++
            val request = when (protocol) {
                Protocol.OPENAI -> openAiRequest(base, key, model, system, messages, curThink, wantUsage)
                Protocol.GEMINI -> geminiRequest(base, key, model, system, messages, curThink, curTokens)
                Protocol.ANTHROPIC -> anthropicRequest(base, key, model, system, messages, curTokens, curThink)
            }
            val call = streamHttp.newCall(request)
            currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            val r = call.execute()
            if (r.isSuccessful) { response = r; break }
            val body = runCatching { r.boundedString(200_000) }.getOrDefault("")
            r.close()
            val lower = body.lowercase()
            val badRequest = r.code == 400 || r.code == 422
            when {
                attempts < 5 && badRequest && wantUsage && protocol == Protocol.OPENAI &&
                    (lower.contains("stream_options") || lower.contains("include_usage")) -> wantUsage = false
                attempts < 5 && badRequest && curThink != ThinkMode.AUTO &&
                    (lower.contains("think") || lower.contains("reason") || lower.contains("budget") ||
                        lower.contains("unrecognized") || lower.contains("unknown") || lower.contains("not supported") ||
                        lower.contains("unsupported") || lower.contains("extra")) -> curThink = ThinkMode.AUTO
                attempts < 5 && badRequest && protocol == Protocol.ANTHROPIC && lower.contains("max_tokens") && curTokens > 4096 ->
                    curTokens = if (curTokens > 8192) 8192 else 4096
                else -> throw ApiException(errorText(r.code, body), r.code, ProviderErrors.kindForStatus(r.code, body))
            }
        }
        val res = response ?: throw ApiException("No response")
        res.use { r ->
            emit(StreamEvent.Headers(r.headers.associate { it.first.lowercase() to it.second }))
            val contentType = r.header("Content-Type") ?: ""
            val source = r.body!!.source()
            if (!contentType.contains("event-stream") && protocol == Protocol.OPENAI) {
                // Some servers ignore "stream": true and answer with a single JSON object.
                if (source.request(MAX_JSON_BODY)) throw ApiException("Response too large", kind = ErrorKind.SERVER)
                val t = source.readUtf8().trim()
                if (t.startsWith("{")) {
                    val o = JSONObject(t)
                    o.optJSONObject("error")?.let { throw ApiException(it.optString("message")) }
                    val ch = o.optJSONArray("choices")?.optJSONObject(0)
                    val msg = ch?.optJSONObject("message")
                    val rc = msg?.optString("reasoning_content")?.ifBlank { msg.optString("reasoning") }
                    if (!rc.isNullOrBlank() && rc != "null") emit(StreamEvent.Thinking(rc))
                    val c = msg?.optString("content")
                    if (!c.isNullOrEmpty()) emit(StreamEvent.Text(c))
                    o.optJSONObject("usage")?.let { u -> emit(StreamEvent.Usage(u.optLong("prompt_tokens"), u.optLong("completion_tokens"))) }
                    emit(StreamEvent.Done(ch?.optString("finish_reason") == "length" || ch?.optString("finish_reason") == "max_tokens"))
                    return@use
                }
                var fin: Boolean? = null
                t.lineSequence().forEach { line ->
                    parseLine(protocol, line)?.let { p ->
                        if (!p.text.isNullOrEmpty()) emit(StreamEvent.Text(p.text))
                        if (p.inTok != null || p.outTok != null) emit(StreamEvent.Usage(p.inTok ?: 0, p.outTok ?: 0))
                        if (p.fin != null) fin = p.fin
                    }
                }
                emit(StreamEvent.Done(fin ?: false))
                return@use
            }
            var finished: Boolean? = null   // null = provider never said it was done
            var sawDone = false
            while (true) {
                currentCoroutineContext().ensureActive()
                // Bounded line reads: a server that never sends a newline can't exhaust memory.
                if (source.exhausted()) break
                val nl = source.indexOf('\n'.code.toByte(), 0, MAX_LINE)
                val line = if (nl >= 0) source.readUtf8LineStrict() else {
                    if (source.request(MAX_LINE)) throw ApiException("Response line too large", kind = ErrorKind.SERVER)
                    source.readUtf8()
                }
                if (line.startsWith("data:") && line.removePrefix("data:").trim() == "[DONE]") { sawDone = true; break }
                val parsed = parseLine(protocol, line) ?: continue
                if (!parsed.thinking.isNullOrEmpty()) emit(StreamEvent.Thinking(parsed.thinking))
                if (!parsed.text.isNullOrEmpty()) emit(StreamEvent.Text(parsed.text))
                if (parsed.inTok != null || parsed.outTok != null) emit(StreamEvent.Usage(parsed.inTok ?: -1, parsed.outTok ?: -1))
                if (parsed.fin != null) finished = (finished == true) || parsed.fin
            }
            // Stream closed without a finish signal -> the connection dropped mid-reply.
            if (finished == null && !sawDone) throw java.io.IOException("Stream ended unexpectedly")
            emit(StreamEvent.Done(finished == true))
        }
    }.flowOn(Dispatchers.IO)

    private data class Parsed(val text: String? = null, val thinking: String? = null, val fin: Boolean? = null,
                              val inTok: Long? = null, val outTok: Long? = null)

    private fun parseLine(protocol: Protocol, raw: String): Parsed? {
        if (!raw.startsWith("data:")) return null
        val data = raw.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") return null
        val o = runCatching { JSONObject(data) }.getOrNull() ?: return null
        o.optJSONObject("error")?.let { throw ApiException(it.optString("message").ifBlank { it.toString() }, it.optInt("code", 0)) }
        return when (protocol) {
            Protocol.OPENAI -> {
                val u = o.optJSONObject("usage")
                val ui = u?.optLong("prompt_tokens")?.takeIf { u.has("prompt_tokens") }
                val uo = u?.optLong("completion_tokens")?.takeIf { u.has("completion_tokens") }
                val ch = o.optJSONArray("choices")?.optJSONObject(0)
                    ?: return if (ui != null || uo != null) Parsed(inTok = ui, outTok = uo) else null
                val delta = ch.optJSONObject("delta")
                fun str(k: String): String? = delta?.opt(k).let { if (it is String && it.isNotEmpty()) it else null }
                val fr = ch.opt("finish_reason")
                val fin = if (fr is String && fr.isNotBlank()) (fr == "length" || fr == "max_tokens") else null
                Parsed(str("content"), str("reasoning_content") ?: str("reasoning"), fin, ui, uo)
            }
            Protocol.GEMINI -> {
                val um = o.optJSONObject("usageMetadata")
                val gi = um?.optLong("promptTokenCount")?.takeIf { um.has("promptTokenCount") }
                val go = um?.let { (it.optLong("candidatesTokenCount") + it.optLong("thoughtsTokenCount")).takeIf { v -> v > 0 } }
                val cand = o.optJSONArray("candidates")?.optJSONObject(0)
                    ?: return if (gi != null || go != null) Parsed(inTok = gi, outTok = go) else null
                val parts = cand.optJSONObject("content")?.optJSONArray("parts")
                val text = StringBuilder(); val thought = StringBuilder()
                if (parts != null) for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.optBoolean("thought")) thought.append(p.optString("text")) else text.append(p.optString("text"))
                }
                val fr = cand.optString("finishReason")
                Parsed(text.toString().ifEmpty { null }, thought.toString().ifEmpty { null }, if (fr.isNotBlank()) (fr == "MAX_TOKENS" || fr.equals("length", true)) else null, gi, go)
            }
            Protocol.ANTHROPIC -> {
                when (o.optString("type")) {
                    "content_block_delta" -> {
                        val d = o.optJSONObject("delta")
                        when (d?.optString("type")) {
                            "thinking_delta" -> Parsed(thinking = d.optString("thinking").ifEmpty { null })
                            "text_delta" -> Parsed(text = d.optString("text").ifEmpty { null })
                            else -> null
                        }
                    }
                    "message_start" -> {
                        val u = o.optJSONObject("message")?.optJSONObject("usage")
                        if (u == null) null else Parsed(inTok = u.optLong("input_tokens") + u.optLong("cache_read_input_tokens") + u.optLong("cache_creation_input_tokens"), outTok = u.optLong("output_tokens"))
                    }
                    "message_delta" -> {
                        val sr = o.optJSONObject("delta")?.optString("stop_reason") ?: ""
                        val uo = o.optJSONObject("usage")?.let { if (it.has("output_tokens")) it.optLong("output_tokens") else null }
                        Parsed(fin = if (sr.isNotBlank()) sr == "max_tokens" else null, outTok = uo)
                    }
                    "message_stop" -> Parsed(fin = false)
                    "error" -> throw ApiException(o.optJSONObject("error")?.optString("message") ?: "Unknown error", 529)
                    else -> null
                }
            }
        }
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): Request =
        Request.Builder().url(url).post(body.toString().toRequestBody(jsonType)).apply {
            headers.forEach { (k, v) -> header(k, v) }
            header("Accept", "text/event-stream")
        }.build()

    private fun textWithFiles(m: ApiMessage): String {
        val files = m.attachments.filter { it.text != null }
        if (files.isEmpty()) return m.text
        return buildString {
            // File contents are untrusted data: the system prompt tells the model never to follow instructions inside them.
            files.forEach { append("<untrusted_content source=\"file: ${it.name.replace("\"", "'")}\">\n```\n${it.text}\n```\n</untrusted_content>\n\n") }
            append(m.text)
        }
    }

    private fun openAiRequest(base: String, key: String, model: String, system: String, msgs: List<ApiMessage>, think: ThinkMode, usage: Boolean = true): Request {
        val arr = JSONArray()
        if (system.isNotBlank()) arr.put(JSONObject().put("role", "system").put("content", system))
        msgs.forEach { m ->
            val binaries = m.attachments.filter { (it.base64 != null && !it.isVideo) || it.frames.isNotEmpty() }
            val text = textWithFiles(m)
            if (binaries.isEmpty() || m.role != "user") {
                arr.put(JSONObject().put("role", m.role).put("content", text))
            } else {
                val parts = JSONArray()
                parts.put(JSONObject().put("type", "text").put("text", text.ifBlank { "Describe this." }))
                binaries.forEach { a ->
                    if (a.isVideo) {
                        parts.put(JSONObject().put("type", "text").put("text", "[Video \"${a.name}\": ${a.frames.size} frames in order, evenly spaced]"))
                        a.frames.forEach { f ->
                            parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$f")))
                        }
                        return@forEach
                    }
                    val dataUri = "data:${a.mime};base64,${a.base64}"
                    if (a.isImage) parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUri)))
                    else parts.put(JSONObject().put("type", "file").put("file", JSONObject().put("filename", a.name).put("file_data", dataUri)))
                }
                arr.put(JSONObject().put("role", "user").put("content", parts))
            }
        }
        val body = JSONObject().put("model", model).put("messages", arr).put("stream", true)
        if (usage) body.put("stream_options", JSONObject().put("include_usage", true))
        if (think.effort.isNotEmpty()) {
            if (base.contains("openrouter")) body.put("reasoning", JSONObject().put("effort", think.effort))
            else body.put("reasoning_effort", think.effort)
        }
        val headers = mutableMapOf("HTTP-Referer" to "https://omnichat.app", "X-Title" to "OmniChat")
        if (key.isNotBlank()) headers["Authorization"] = "Bearer $key"
        return post("$base/chat/completions", body, headers)
    }

    private fun geminiRequest(base: String, key: String, model: String, system: String, msgs: List<ApiMessage>, think: ThinkMode, maxTokens: Int = 0): Request {
        val contents = JSONArray()
        msgs.forEach { m ->
            val parts = JSONArray()
            m.attachments.forEach { a ->
                when {
                    a.base64 != null -> parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", a.mime).put("data", a.base64)))
                    a.frames.isNotEmpty() -> {
                        parts.put(JSONObject().put("text", "[Video \"${a.name}\": ${a.frames.size} frames in order]"))
                        a.frames.forEach { f -> parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", f))) }
                    }
                }
            }
            parts.put(JSONObject().put("text", textWithFiles(m).ifBlank { "Describe this." }))
            contents.put(JSONObject().put("role", if (m.role == "assistant") "model" else "user").put("parts", parts))
        }
        val body = JSONObject().put("contents", contents)
        // generationConfig carries maxOutputTokens whenever a limit is set (Prefs.maxTokens),
        // even when no thinking config is needed — previously maxTokens was silently dropped here.
        val gen = JSONObject()
        when (think) {
            ThinkMode.AUTO -> {}
            ThinkMode.OFF -> gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            else -> gen.put("thinkingConfig", JSONObject().put("thinkingBudget", think.budget).put("includeThoughts", true))
        }
        if (maxTokens > 0) gen.put("maxOutputTokens", maxTokens)
        if (gen.length() > 0) body.put("generationConfig", gen)
        if (system.isNotBlank()) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        val id = model.removePrefix("models/")
        return post("$base/models/$id:streamGenerateContent?alt=sse", body, mapOf("x-goog-api-key" to key))
    }

    private fun anthropicRequest(base: String, key: String, model: String, system: String, msgs: List<ApiMessage>, maxTokens: Int, think: ThinkMode): Request {
        val arr = JSONArray()
        msgs.forEach { m ->
            val parts = JSONArray()
            if (m.role == "user") {
                m.attachments.filter { it.isVideo && it.frames.isNotEmpty() }.forEach { a ->
                    parts.put(JSONObject().put("type", "text").put("text", "[Video \"${a.name}\": ${a.frames.size} frames in order]"))
                    a.frames.forEach { f ->
                        val src = JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", f)
                        parts.put(JSONObject().put("type", "image").put("source", src))
                    }
                }
                m.attachments.filter { it.base64 != null && !it.isVideo }.forEach { a ->
                    val src = JSONObject().put("type", "base64").put("media_type", a.mime).put("data", a.base64)
                    parts.put(JSONObject().put("type", if (a.isImage) "image" else "document").put("source", src))
                }
            }
            parts.put(JSONObject().put("type", "text").put("text", textWithFiles(m).ifBlank { "..." }))
            arr.put(JSONObject().put("role", m.role).put("content", parts))
        }
        val thinking = think.budget > 0 && maxTokens > think.budget.coerceAtMost(maxTokens / 2)
        val budget = think.budget.coerceAtMost(maxTokens - 1024).coerceAtLeast(1024)
        val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("messages", arr).put("stream", true)
        if (thinking && maxTokens > 2048) body.put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", budget))
        if (system.isNotBlank()) body.put("system", system)
        return post("$base/messages", body, mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
    }
}
