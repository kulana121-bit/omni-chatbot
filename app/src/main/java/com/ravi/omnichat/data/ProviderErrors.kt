package com.ravi.omnichat.data

import com.ravi.omnichat.security.Redactor
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Why a provider call failed. Drives retry, fallback and the message the user sees. */
enum class ErrorKind(val label: String, val retrySameEndpoint: Boolean, val tryFallback: Boolean) {
    CONFIG("setup problem", false, true),
    AUTH("key rejected", false, true),
    QUOTA("quota or credit used up", false, true),
    RATE_LIMIT("rate limited", false, true),
    TIMEOUT("timed out", true, true),
    DNS("server not found", false, true),
    TLS("secure connection failed", false, true),
    NETWORK("network error", true, true),
    SERVER("provider error", false, true),
    BAD_REQUEST("request rejected", false, true),
    NOT_FOUND("model or URL not found", false, true),
    REDIRECT("endpoint redirected", false, true),
    EMPTY("empty response", false, true),
    UNKNOWN("error", false, true)
}

object ProviderErrors {
    fun kindForStatus(code: Int, body: String = ""): ErrorKind = when {
        code == 401 || code == 403 -> ErrorKind.AUTH
        code == 402 -> ErrorKind.QUOTA
        code == 429 && Regex("(?i)quota|insufficient|billing|credit|exhausted").containsMatchIn(body) -> ErrorKind.QUOTA
        code == 429 -> ErrorKind.RATE_LIMIT
        code == 404 -> ErrorKind.NOT_FOUND
        code == 408 || code == 504 -> ErrorKind.TIMEOUT
        code in 300..399 -> ErrorKind.REDIRECT
        code in 400..499 -> ErrorKind.BAD_REQUEST
        code >= 500 -> ErrorKind.SERVER
        else -> ErrorKind.UNKNOWN
    }

    fun classify(e: Throwable): ErrorKind = when (e) {
        is ApiException -> e.kind
        is UnknownHostException -> ErrorKind.DNS
        is SSLHandshakeException, is SSLPeerUnverifiedException -> ErrorKind.TLS
        is SSLException -> ErrorKind.NETWORK
        is SocketTimeoutException -> ErrorKind.TIMEOUT
        is InterruptedIOException -> if (e.message?.contains("timeout", true) == true) ErrorKind.TIMEOUT else ErrorKind.NETWORK
        is ConnectException -> ErrorKind.NETWORK
        is IOException -> ErrorKind.NETWORK
        else -> ErrorKind.UNKNOWN
    }

    /** Short, redacted text for the UI. Raw provider bodies are cut to 240 chars and scrubbed of keys. */
    fun userMessage(e: Throwable): String {
        val kind = classify(e)
        val raw = Redactor.redact(e.message ?: e.javaClass.simpleName).take(240)
        return when (kind) {
            ErrorKind.DNS -> "Can't reach the server (no internet or wrong URL)."
            ErrorKind.TLS -> "Secure connection failed. Check the URL and your network (public Wi-Fi login pages cause this)."
            ErrorKind.TIMEOUT -> "The provider took too long to answer."
            ErrorKind.NETWORK -> "Network error: connection dropped."
            else -> raw
        }
    }

    /** Exponential backoff with jitter: ~1s, ~2s, ~4s… capped at 8s. [rand] in 0..1. */
    fun backoffMillis(attempt: Int, rand: Double = Math.random()): Long {
        val base = 1000L shl attempt.coerceIn(0, 3)
        return (base * (0.75 + rand * 0.5)).toLong().coerceAtMost(8000L)
    }
}
