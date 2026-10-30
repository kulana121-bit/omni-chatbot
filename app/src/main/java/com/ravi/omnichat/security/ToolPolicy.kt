package com.ravi.omnichat.security

import org.json.JSONObject

/** A tool request found in model output. [range] is where the tag sits in the text. */
data class ParsedTag(val tool: String, val json: String?, val range: IntRange)

/**
 * Finds [[app:tool {json}]] tags in model output. Unlike a regex it balances braces and respects JSON strings,
 * so a "}" or "]]" inside a message text can't cut a call short or smuggle a second one in.
 */
object ToolTagParser {
    private const val OPEN = "[[app:"
    const val MAX_TAGS = 12
    const val MAX_JSON = 4000

    fun parse(text: String): List<ParsedTag> {
        val out = mutableListOf<ParsedTag>()
        var i = 0
        while (out.size < MAX_TAGS) {
            val start = text.indexOf(OPEN, i, ignoreCase = true)
            if (start < 0) break
            var p = start + OPEN.length
            while (p < text.length && text[p] == ' ') p++
            val nameStart = p
            while (p < text.length && (text[p].isLetterOrDigit() || text[p] == '_')) p++
            val name = text.substring(nameStart, p).lowercase()
            while (p < text.length && text[p].isWhitespace()) p++
            var json: String? = null
            if (p < text.length && text[p] == '{') {
                val end = matchBrace(text, p)
                if (end < 0) { i = start + OPEN.length; continue }
                json = text.substring(p, end + 1)
                p = end + 1
                while (p < text.length && text[p].isWhitespace()) p++
            }
            if (name.isEmpty() || !text.startsWith("]]", p)) { i = start + OPEN.length; continue }
            out += ParsedTag(name, json, start until p + 2)
            i = p + 2
        }
        return out
    }

    /** Removes complete tags (for display). */
    fun strip(text: String): String {
        val tags = parse(text)
        if (tags.isEmpty()) return text
        val sb = StringBuilder(text)
        tags.asReversed().forEach { sb.delete(it.range.first, it.range.last + 1) }
        return sb.toString()
    }

    private fun matchBrace(s: String, from: Int): Int {
        var depth = 0
        var inStr = false
        var esc = false
        var i = from
        val limit = minOf(s.length, from + MAX_JSON + 1)
        while (i < limit) {
            val c = s[i]
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return -1
    }
}

/**
 * Allowlist and argument schema for every tool the assistant may call. Model output, web pages, files and
 * tool results are untrusted, so every call is checked here before anything runs.
 */
object ToolPolicy {
    enum class Risk {
        /** Reads data and sends it back to the model (the user enabled the app). */
        DATA,
        /** Opens a screen the user still controls (maps, settings, a search); harmless if unwanted. */
        OPEN,
        /** Sends or prepares something in the user's name, changes device state, or leaves the app: needs a tap. */
        CONFIRM
    }

    private sealed class F(val required: Boolean) {
        class Str(val max: Int, required: Boolean = false, val pattern: Regex? = null) : F(required)
        class Num(val min: Double, val max: Double, required: Boolean = false, val int: Boolean = true) : F(required)
        class Bool : F(false)
        class Url : F(true)
        class Choice(val options: Set<String>, required: Boolean = false) : F(required)
    }

    private val date = Regex("""^\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2})?$""")
    private val repo = Regex("""^[A-Za-z0-9_.\-]{1,100}/[A-Za-z0-9_.\-]{1,100}$""")
    private val code3 = Regex("""^[A-Za-z]{3}$""")

    private data class Spec(val risk: Risk, val fields: Map<String, F>)

    private val specs: Map<String, Spec> = mapOf(
        "calendar_events" to Spec(Risk.DATA, mapOf("days" to F.Num(1.0, 60.0), "query" to F.Str(100))),
        "calendar_add" to Spec(Risk.OPEN, mapOf("title" to F.Str(200, true), "start" to F.Str(20, true, date), "end" to F.Str(20, false, date), "location" to F.Str(200))),
        "contacts_search" to Spec(Risk.DATA, mapOf("name" to F.Str(80, true))),
        "call" to Spec(Risk.CONFIRM, mapOf("to" to F.Str(80, true))),
        "sms" to Spec(Risk.CONFIRM, mapOf("to" to F.Str(80, true), "text" to F.Str(2000))),
        "whatsapp" to Spec(Risk.CONFIRM, mapOf("to" to F.Str(80), "text" to F.Str(2000))),
        "email" to Spec(Risk.CONFIRM, mapOf("to" to F.Str(320, true), "subject" to F.Str(300), "body" to F.Str(5000))),
        "alarm" to Spec(Risk.CONFIRM, mapOf("hour" to F.Num(0.0, 23.0, true), "minute" to F.Num(0.0, 59.0), "label" to F.Str(80))),
        "timer" to Spec(Risk.CONFIRM, mapOf("seconds" to F.Num(1.0, 86_400.0, true), "label" to F.Str(80))),
        "maps" to Spec(Risk.OPEN, mapOf("query" to F.Str(300, true), "navigate" to F.Bool())),
        "youtube" to Spec(Risk.OPEN, mapOf("query" to F.Str(300, true))),
        "spotify" to Spec(Risk.OPEN, mapOf("query" to F.Str(300, true))),
        "open_url" to Spec(Risk.CONFIRM, mapOf("url" to F.Url())),
        "weather" to Spec(Risk.DATA, mapOf("city" to F.Str(100, true))),
        "github" to Spec(Risk.DATA, mapOf("action" to F.Choice(setOf("repos", "search", "issues", "repo"), true),
            "user" to F.Str(39, false, Regex("^[A-Za-z0-9\\-]{1,39}$")), "query" to F.Str(200), "repo" to F.Str(201, false, repo))),
        "news" to Spec(Risk.DATA, mapOf("query" to F.Str(200), "lang" to F.Choice(setOf("en", "hi")))),
        "currency" to Spec(Risk.DATA, mapOf("amount" to F.Num(0.0, 1e12, int = false), "from" to F.Str(3, false, code3), "to" to F.Str(3, false, code3))),
        "crypto" to Spec(Risk.DATA, mapOf("coins" to F.Str(200, false, Regex("^[a-z0-9,\\- ]+$")), "vs" to F.Str(10, false, Regex("^[a-z]{3,5}$")))),
        "wiki" to Spec(Risk.DATA, mapOf("query" to F.Str(200, true), "lang" to F.Str(5, false, Regex("^[a-z]{2,3}$")))),
        "location" to Spec(Risk.DATA, emptyMap()),
        "device_info" to Spec(Risk.DATA, emptyMap()),
        "apps_list" to Spec(Risk.DATA, mapOf("query" to F.Str(60))),
        "open_app" to Spec(Risk.OPEN, mapOf("name" to F.Str(60, true))),
        "flashlight" to Spec(Risk.OPEN, mapOf("on" to F.Bool())),
        "settings" to Spec(Risk.OPEN, mapOf("page" to F.Choice(setOf("wifi", "bluetooth", "display", "sound", "battery", "location", "apps", "notifications", "data")))),
        "share" to Spec(Risk.OPEN, mapOf("text" to F.Str(5000, true))),
        "copy" to Spec(Risk.CONFIRM, mapOf("text" to F.Str(5000, true)))
    )

    val tools: Set<String> get() = specs.keys

    /**
     * Data tools that read private on-device data. On their own they run as [Risk.DATA] (the user
     * enabled the app), but if untrusted content (a web page, a file, or an earlier app result)
     * arrived in the SAME reply, the request may have been steered by that content — so these are
     * escalated to [Risk.CONFIRM] and wait for the user's tap like any other consequential action.
     */
    val SENSITIVE: Set<String> = setOf("contacts_search", "location", "device_info", "apps_list")
    fun risk(tool: String): Risk? = specs[tool]?.risk

    /** Calls one reply may make; extra calls are dropped. */
    const val MAX_CALLS_PER_REPLY = 6
    const val MAX_ACTIONS_PER_REPLY = 4
    /** Characters of tool output sent back to the model per call. */
    const val MAX_RESULT_CHARS = 8000

    sealed class Verdict {
        data class Ok(val tool: String, val args: JSONObject, val risk: Risk) : Verdict()
        data class Rejected(val tool: String, val reason: String) : Verdict()
    }

    /**
     * Validates a parsed tag. [enabled] says whether the app owning the tool is switched on by the user.
     * Unknown argument keys reject the call, as do wrong types, out-of-range numbers and oversized strings.
     */
    fun validate(tag: ParsedTag, enabled: (String) -> Boolean, untrustedSeen: Boolean = false): Verdict {
        val spec = specs[tag.tool] ?: return Verdict.Rejected(tag.tool, "unknown tool")
        if (!enabled(tag.tool)) return Verdict.Rejected(tag.tool, "this app is turned off")
        val json = tag.json ?: "{}"
        if (json.length > ToolTagParser.MAX_JSON) return Verdict.Rejected(tag.tool, "arguments too large")
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return Verdict.Rejected(tag.tool, "arguments are not valid JSON")
        val out = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!spec.fields.containsKey(key)) {
                return Verdict.Rejected(tag.tool, "unknown argument \"$key\"")
            }
        }
        for ((k, f) in spec.fields) {
            if (!obj.has(k) || obj.isNull(k)) {
                if (f.required) return Verdict.Rejected(tag.tool, "missing \"$k\"")
                continue
            }
            val v = obj.get(k)
            when (f) {
                is F.Str -> {
                    val s = (v as? String) ?: (v as? Number)?.takeIf { it.toDouble().isFinite() }?.toString() ?: return Verdict.Rejected(tag.tool, "\"$k\" must be text")
                    if (s.length > f.max) return Verdict.Rejected(tag.tool, "\"$k\" is too long")
                    if (f.required && s.isBlank()) return Verdict.Rejected(tag.tool, "\"$k\" is empty")
                    if (s.isNotEmpty() && f.pattern != null && !f.pattern.matches(s)) return Verdict.Rejected(tag.tool, "\"$k\" has an invalid format")
                    if (s.any { it.code < 0x20 && it != '\n' && it != '\t' }) return Verdict.Rejected(tag.tool, "\"$k\" contains control characters")
                    out.put(k, s)
                }
                is F.Num -> {
                    val n = when (v) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null }
                        ?: return Verdict.Rejected(tag.tool, "\"$k\" must be a number")
                    if (n.isNaN() || n < f.min || n > f.max) return Verdict.Rejected(tag.tool, "\"$k\" is out of range")
                    if (f.int) out.put(k, n.toLong()) else out.put(k, n)
                }
                is F.Bool -> out.put(k, when (v) { is Boolean -> v; is String -> v.equals("true", true); else -> return Verdict.Rejected(tag.tool, "\"$k\" must be true/false") })
                is F.Url -> {
                    val s = v as? String ?: return Verdict.Rejected(tag.tool, "\"$k\" must be a URL")
                    val safe = EndpointPolicy.safeLink(s) ?: return Verdict.Rejected(tag.tool, "unsafe or invalid link")
                    out.put(k, safe)
                }
                is F.Choice -> {
                    val s = (v as? String)?.lowercase() ?: return Verdict.Rejected(tag.tool, "\"$k\" must be text")
                    if (s !in f.options) return Verdict.Rejected(tag.tool, "\"$k\" must be one of ${f.options.joinToString()}")
                    out.put(k, s)
                }
            }
        }
        if (tag.tool == "github") {
            val need = when (out.optString("action")) { "repos" -> "user"; "search" -> "query"; else -> "repo" }
            if (out.optString(need).isBlank()) return Verdict.Rejected(tag.tool, "missing \"$need\"")
        }
        val risk = if (untrustedSeen && tag.tool in SENSITIVE) Risk.CONFIRM else spec.risk
        return Verdict.Ok(tag.tool, out, risk)
    }

    /** A plain sentence for the confirmation card: what will run and what data it uses. */
    fun describe(tool: String, a: JSONObject): String {
        fun s(k: String) = a.optString(k).let { if (it.length > 160) it.take(160) + "…" else it }
        return when (tool) {
            "call" -> "Open the dialer with ${s("to")}"
            "sms" -> "Open SMS to ${s("to")} with: \"${s("text")}\""
            "whatsapp" -> "Open WhatsApp" + (if (s("to").isNotBlank()) " to ${s("to")}" else "") + " with: \"${s("text")}\""
            "email" -> "Open email to ${s("to")}, subject \"${s("subject")}\""
            "alarm" -> "Set an alarm for %02d:%02d".format(a.optInt("hour"), a.optInt("minute")) + (if (s("label").isNotBlank()) " (${s("label")})" else "")
            "timer" -> "Start a ${a.optInt("seconds")} second timer"
            "open_url" -> "Open this link: ${s("url")}"
            "copy" -> "Replace your clipboard with: \"${s("text")}\""
            "calendar_add" -> "Open your calendar with a new event \"${s("title")}\" at ${s("start")}"
            "contacts_search" -> "Read contacts matching \"${s("name")}\""
            "location" -> "Read your approximate location"
            "device_info" -> "Read device info (battery, storage, model)"
            "apps_list" -> "Read your installed apps" + (if (s("query").isNotBlank()) " matching \"${s("query")}\"" else "")
            else -> "Run $tool"
        }
    }
}
