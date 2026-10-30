package com.ravi.omnichat.security

import java.net.URI

/**
 * Decides which URLs the app may talk to. Pure Kotlin (no Android types) so it is unit-tested on the JVM.
 *
 * - AI endpoints must be HTTPS. Plain HTTP is accepted only for local/private hosts (Ollama, LM Studio)
 *   and only when the user has turned on "Allow HTTP for local servers".
 * - No user:password@ in URLs, no API keys in query strings, ports must be valid.
 * - Links the assistant asks to open must be http(s) with a real host (no javascript:, intent:, file:, content:).
 */
object EndpointPolicy {

    /** Returns null when the URL is acceptable as an AI endpoint, otherwise a short user-facing reason. */
    fun checkAiEndpoint(raw: String, allowLocalHttp: Boolean): String? {
        val url = raw.trim()
        if (url.isEmpty()) return "Base URL is empty"
        if (url.length > 2048) return "Base URL is too long"
        val u = parse(url) ?: return "Base URL is not a valid URL"
        val scheme = u.scheme?.lowercase() ?: return "Base URL needs https://"
        val host = u.host?.lowercase()?.trim('[', ']') ?: return "Base URL has no host"
        if (host.isBlank()) return "Base URL has no host"
        if (u.rawUserInfo != null) return "Don't put a username or password in the URL"
        if (u.port != -1 && u.port !in 1..65535) return "Invalid port"
        if (u.rawFragment != null) return "Base URL must not contain #"
        val q = u.rawQuery
        if (q != null && Regex("(?i)(^|&)(key|api_?key|token|access_token)=").containsMatchIn(q)) return "Don't put the API key in the URL; enter it in the key field"
        return when (scheme) {
            "https" -> null
            "http" -> when {
                !isLocalHost(host) -> "Plain HTTP is only allowed for local servers. Use https://"
                !allowLocalHttp -> "Turn on \"Allow HTTP for local servers\" in Settings to use $host over HTTP"
                else -> null
            }
            else -> "Only https:// URLs are supported"
        }
    }

    /** For links the assistant opens in another app. Returns the normalized URL or null when unsafe. */
    fun safeLink(raw: String): String? {
        val url = raw.trim()
        if (url.length > 2048 || url.any { it.isWhitespace() || it.code < 0x20 }) return null
        val u = parse(url) ?: return null
        val scheme = u.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = u.host ?: return null
        if (host.isBlank() || u.rawUserInfo != null) return null
        return url
    }

    /** Loopback, RFC1918 private, link-local, unique-local IPv6, emulator host and mDNS/local names. */
    fun isLocalHost(hostIn: String): Boolean {
        val host = hostIn.lowercase().trim('[', ']').trimEnd('.')
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
        ipv4(host)?.let { (a, b, _, _) ->
            return a == 127 || a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) || (a == 100 && b in 64..127)
        }
        if (host.contains(':')) {
            if (host == "::1") return true
            val first = host.substringBefore(':').ifEmpty { "0" }.toIntOrNull(16) ?: return false
            return (first and 0xfe00) == 0xfc00 || (first and 0xffc0) == 0xfe80
        }
        return false
    }

    private fun ipv4(h: String): List<Int>? {
        val p = h.split('.')
        if (p.size != 4) return null
        val n = p.map { s -> if (s.isEmpty() || s.length > 3 || !s.all(Char::isDigit)) return null else s.toInt() }
        return if (n.all { it in 0..255 }) n else null
    }

    private fun parse(s: String): URI? = runCatching { URI(s) }.getOrNull()?.takeIf { it.isAbsolute }
}
