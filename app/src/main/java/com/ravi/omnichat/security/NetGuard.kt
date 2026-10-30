package com.ravi.omnichat.security

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class InsecureRequestException(msg: String) : IOException(msg)

/**
 * App-level transport policy for every OkHttp client (Android's network security config can't express
 * "private IP ranges only", which local Ollama servers need, so the precise rule lives here):
 *
 * - [Mode.AI]: http:// only to local/private hosts and only when the user allowed it; anything else is refused.
 * - [Mode.PUBLIC]: http:// to public hosts is upgraded to https://; local/private hosts are refused (no SSRF into the LAN
 *   from web search or connectors).
 */
class NetGuard(
    private val mode: Mode,
    private val allowLocalHttp: () -> Boolean = { false },
    /** As a network interceptor it sees every redirect hop; an http hop there is a downgrade and is blocked. */
    private val network: Boolean = false
) : Interceptor {
    enum class Mode { AI, PUBLIC }

    sealed class Decision {
        object Allow : Decision()
        data class Upgrade(val httpsUrl: String) : Decision()
        data class Block(val reason: String) : Decision()
    }

    companion object {
        fun decide(mode: Mode, scheme: String, host: String, url: String, allowLocal: Boolean): Decision {
            val local = EndpointPolicy.isLocalHost(host)
            return when (mode) {
                Mode.AI -> when {
                    scheme == "https" -> Decision.Allow
                    scheme == "http" && local && allowLocal -> Decision.Allow
                    scheme == "http" && local -> Decision.Block("HTTP to local servers is turned off in Settings")
                    else -> Decision.Block("Plain HTTP is not allowed for $host")
                }
                Mode.PUBLIC -> when {
                    local -> Decision.Block("Requests to local network addresses are not allowed")
                    scheme == "https" -> Decision.Allow
                    scheme == "http" -> Decision.Upgrade("https" + url.removePrefix("http"))
                    else -> Decision.Block("Unsupported scheme")
                }
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        return when (val d = decide(mode, req.url.scheme, req.url.host, req.url.toString(), allowLocalHttp())) {
            Decision.Allow -> chain.proceed(req)
            is Decision.Upgrade -> if (network) throw InsecureRequestException("Blocked an insecure (HTTP) redirect")
                else chain.proceed(req.newBuilder().url(d.httpsUrl).build())
            is Decision.Block -> throw InsecureRequestException(d.reason)
        }
    }
}

/** Reads a response body as text, refusing bodies larger than [max] bytes (default 3 MB). */
fun Response.boundedString(max: Long = 3_000_000): String {
    val src = body?.source() ?: return ""
    if (src.request(max + 1)) throw IOException("Response too large")
    return src.buffer.readUtf8()
}
