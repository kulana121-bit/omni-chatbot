package com.ravi.omnichat.security

/**
 * Removes secrets from any text that may be shown, copied or written to disk (errors, crash reports).
 * Matches common provider key formats and any secret value the app registered at runtime.
 */
object Redactor {
    private val known = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun register(secret: String?) { val s = secret?.trim().orEmpty(); if (s.length >= 8) known += s }
    fun forget(secret: String?) { secret?.trim()?.let { known -= it } }

    private val patterns = listOf(
        Regex("""(?i)\b(bearer)\s+[A-Za-z0-9._\-~+/=]{8,}"""),
        Regex("""\bsk-(?:ant-|or-|proj-)?[A-Za-z0-9_\-]{8,}"""),
        Regex("""\bAIza[0-9A-Za-z_\-]{20,}"""),
        Regex("""\b(?:gsk|xai|nvapi|hf|pplx|ghp|gho|ghu|ghs|ghr)[_-][A-Za-z0-9_\-]{8,}"""),
        Regex("""\bgithub_pat_[A-Za-z0-9_]{20,}"""),
        Regex("""(?i)([?&](?:key|api_?key|token|access_token)=)[^&\s"']+"""),
        Regex("""(?i)("?(?:x-api-key|x-goog-api-key|authorization|api[_-]?key)"?\s*[:=]\s*"?)[^"\s,}]{6,}""")
    )

    fun redact(text: String?): String {
        var t = text ?: return ""
        known.forEach { if (it.isNotEmpty()) t = t.replace(it, "[REDACTED]") }
        patterns.forEachIndexed { i, rx ->
            t = rx.replace(t) { m ->
                when (i) {
                    0 -> m.groupValues[1] + " [REDACTED]"
                    5, 6 -> m.groupValues[1] + "[REDACTED]"
                    else -> "[REDACTED]"
                }
            }
        }
        return t
    }
}
