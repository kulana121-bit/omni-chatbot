package com.ravi.omnichat.data

import com.ravi.omnichat.security.boundedString
import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Requests and tokens counted for one model or one API key in one day. */
data class Counter(val requests: Int = 0, val input: Long = 0, val output: Long = 0) {
    val tokens get() = input + output
    operator fun plus(o: Counter) = Counter(requests + o.requests, input + o.input, output + o.output)
}

/** A daily cap the user sets. 0 = no cap. */
data class DailyLimit(val requests: Int = 0, val tokens: Long = 0) {
    val isSet get() = requests > 0 || tokens > 0
}

/** The provider's own rate-limit numbers, read from its response headers. */
data class RateInfo(
    val reqLimit: Long? = null, val reqLeft: Long? = null, val reqReset: Long? = null,
    val tokLimit: Long? = null, val tokLeft: Long? = null, val tokReset: Long? = null,
    val at: Long = System.currentTimeMillis()
)

/** Balance or credit read from the provider's account API. */
data class Balance(val title: String, val lines: List<String>, val at: Long = System.currentTimeMillis())

object Scopes {
    fun model(providerId: String, model: String) = "m:$providerId/$model"
    fun key(providerId: String, key: String) = "k:$providerId:${key.takeLast(4)}"
    fun label(scope: String): String = when {
        scope.startsWith("m:") -> scope.removePrefix("m:").substringAfter('/')
        scope.startsWith("k:") -> {
            val p = scope.removePrefix("k:").substringBefore(':')
            Providers.byId(p).name + " ••••" + scope.substringAfterLast(':')
        }
        else -> scope
    }
    fun providerOf(scope: String) = scope.drop(2).substringBefore(if (scope.startsWith("m:")) '/' else ':')
}

/**
 * Local daily usage tracker. A "day" starts at the user's reset time (default midnight),
 * so a 1:30 PM reset matches Gemini's midnight-Pacific reset for people in India.
 */
class UsageStore(context: Context) {
    private val sp = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    fun wipe() { sp.edit().clear().apply() } // apply() is fine here: usage counters are not security data and ordering vs. other stores does not matter
    private val zone get() = ZoneId.systemDefault()

    var resetMinutes: Int
        get() = sp.getInt("reset_min", 0)
        set(v) = sp.edit().putInt("reset_min", ((v % 1440) + 1440) % 1440).apply()

    /** The usage day "now" belongs to. */
    fun today(now: ZonedDateTime = ZonedDateTime.now(zone)): LocalDate =
        now.minusMinutes(resetMinutes.toLong()).toLocalDate()

    /** When the current usage day ends. */
    fun nextReset(now: ZonedDateTime = ZonedDateTime.now(zone)): ZonedDateTime {
        val t = LocalTime.of(resetMinutes / 60, resetMinutes % 60)
        var r = ZonedDateTime.of(LocalDateTime.of(now.toLocalDate(), t), zone)
        if (!r.isAfter(now)) r = r.plusDays(1)
        return r
    }

    fun resetLabel(): String {
        val now = ZonedDateTime.now(zone)
        val r = nextReset(now)
        val d = Duration.between(now, r)
        val left = if (d.toHours() > 0) "${d.toHours()}h ${d.toMinutes() % 60}m" else "${d.toMinutes().coerceAtLeast(1)}m"
        return "Resets in $left · " + r.format(DateTimeFormatter.ofPattern("h:mm a, EEE d MMM"))
    }

    private fun day(date: LocalDate): MutableMap<String, Counter> {
        val o = runCatching { JSONObject(sp.getString("d_$date", "{}")!!) }.getOrDefault(JSONObject())
        val m = mutableMapOf<String, Counter>()
        o.keys().forEach { k -> val c = o.getJSONObject(k); m[k] = Counter(c.optInt("r"), c.optLong("i"), c.optLong("o")) }
        return m
    }

    fun todayCounts(): Map<String, Counter> = day(today())

    /** Last [n] days for one scope, oldest first. */
    fun history(scope: String, n: Int = 7): List<Pair<LocalDate, Counter>> {
        val t = today()
        return (n - 1 downTo 0).map { val d = t.minusDays(it.toLong()); d to (day(d)[scope] ?: Counter()) }
    }

    @Synchronized
    fun record(scopes: List<String>, add: Counter) {
        val t = today()
        val m = day(t)
        scopes.forEach { m[it] = (m[it] ?: Counter()) + add }
        val o = JSONObject()
        m.forEach { (k, c) -> o.put(k, JSONObject().put("r", c.requests).put("i", c.input).put("o", c.output)) }
        val e = sp.edit().putString("d_$t", o.toString())
        // keep 30 days
        sp.all.keys.filter { it.startsWith("d_") }.forEach { k ->
            runCatching { if (LocalDate.parse(k.removePrefix("d_")).isBefore(t.minusDays(30))) e.remove(k) }
        }
        e.apply()
    }

    fun limit(scope: String): DailyLimit {
        val o = runCatching { JSONObject(sp.getString("lim_$scope", "{}")!!) }.getOrDefault(JSONObject())
        return DailyLimit(o.optInt("r"), o.optLong("t"))
    }

    fun setLimit(scope: String, l: DailyLimit) {
        if (l.isSet) sp.edit().putString("lim_$scope", JSONObject().put("r", l.requests).put("t", l.tokens).toString()).apply()
        else sp.edit().remove("lim_$scope").apply()
    }

    fun limitedScopes(): List<String> = sp.all.keys.filter { it.startsWith("lim_") }.map { it.removePrefix("lim_") }

    /** Null when within the limit, else a short reason. */
    fun overLimit(scope: String): String? {
        val l = limit(scope)
        if (!l.isSet) return null
        val c = todayCounts()[scope] ?: Counter()
        if (l.requests > 0 && c.requests >= l.requests) return "${Scopes.label(scope)}: ${c.requests}/${l.requests} requests"
        if (l.tokens > 0 && c.tokens >= l.tokens) return "${Scopes.label(scope)}: ${fmt(c.tokens)}/${fmt(l.tokens)} tokens"
        return null
    }

    /** Highest fraction used of any cap on this scope (0..1+), or null without a cap. */
    fun fraction(scope: String): Float? {
        val l = limit(scope)
        if (!l.isSet) return null
        val c = todayCounts()[scope] ?: Counter()
        val a = if (l.requests > 0) c.requests.toFloat() / l.requests else 0f
        val b = if (l.tokens > 0) c.tokens.toFloat() / l.tokens else 0f
        return maxOf(a, b)
    }

    // ---- provider rate-limit headers (latest per key)
    fun saveRate(keyScope: String, r: RateInfo) {
        sp.edit().putString("rate_$keyScope", JSONObject().apply {
            r.reqLimit?.let { put("rl", it) }; r.reqLeft?.let { put("rr", it) }; r.reqReset?.let { put("rt", it) }
            r.tokLimit?.let { put("tl", it) }; r.tokLeft?.let { put("tr", it) }; r.tokReset?.let { put("tt", it) }
            put("at", r.at)
        }.toString()).apply()
    }

    fun rate(keyScope: String): RateInfo? = runCatching {
        val o = JSONObject(sp.getString("rate_$keyScope", null) ?: return null)
        fun l(k: String) = if (o.has(k)) o.getLong(k) else null
        RateInfo(l("rl"), l("rr"), l("rt"), l("tl"), l("tr"), l("tt"), o.optLong("at"))
    }.getOrNull()

    companion object {
        fun fmt(n: Long): String = when {
            n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
            n >= 10_000 -> "${n / 1000}K"
            n >= 1000 -> String.format(Locale.US, "%.1fK", n / 1000.0)
            else -> n.toString()
        }

        /** Reads x-ratelimit-* (OpenAI, Groq, xAI, Together…) and anthropic-ratelimit-* headers. */
        fun parseRate(h: Map<String, String>): RateInfo? {
            val now = System.currentTimeMillis()
            fun num(vararg k: String) = k.firstNotNullOfOrNull { h[it]?.trim()?.toDoubleOrNull()?.toLong() }
            fun reset(vararg k: String): Long? = k.firstNotNullOfOrNull { key ->
                val v = h[key]?.trim() ?: return@firstNotNullOfOrNull null
                runCatching { java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli() }.getOrNull()
                    ?: durationMs(v)?.let { now + it }
                    ?: v.toDoubleOrNull()?.let { if (it > 1e12) it.toLong() else if (it > 1e9) (it * 1000).toLong() else now + (it * 1000).toLong() }
            }
            val r = RateInfo(
                num("x-ratelimit-limit-requests", "anthropic-ratelimit-requests-limit"),
                num("x-ratelimit-remaining-requests", "anthropic-ratelimit-requests-remaining"),
                reset("x-ratelimit-reset-requests", "anthropic-ratelimit-requests-reset"),
                num("x-ratelimit-limit-tokens", "anthropic-ratelimit-tokens-limit"),
                num("x-ratelimit-remaining-tokens", "anthropic-ratelimit-tokens-remaining"),
                reset("x-ratelimit-reset-tokens", "anthropic-ratelimit-tokens-reset"),
                now
            )
            return if (r.reqLimit == null && r.reqLeft == null && r.tokLimit == null && r.tokLeft == null) null else r
        }

        /** "6m0s", "1.5s", "250ms", "2h3m" -> milliseconds. */
        private fun durationMs(v: String): Long? {
            val m = Regex("(\\d+(?:\\.\\d+)?)(ms|h|m|s)").findAll(v).toList()
            if (m.isEmpty() || m.joinToString("") { it.value } != v) return null
            return m.sumOf { val n = it.groupValues[1].toDouble(); when (it.groupValues[2]) { "h" -> n * 3_600_000; "m" -> n * 60_000; "s" -> n * 1000; else -> n }.toLong() }
        }
    }
}

/** Account balance or credit, where the provider exposes it to a normal API key. */
object BalanceApi {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor(com.ravi.omnichat.security.NetGuard(com.ravi.omnichat.security.NetGuard.Mode.AI))
        .build()

    fun supported(providerId: String) = providerId in setOf("openrouter", "deepseek")

    fun fetch(providerId: String, baseUrl: String, key: String): Balance {
        fun get(url: String): JSONObject {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
            http.newCall(req).execute().use { r ->
                val b = r.body?.string() ?: ""
                if (!r.isSuccessful) throw ApiException("HTTP ${r.code}: ${b.take(160)}", r.code)
                return JSONObject(b)
            }
        }
        return when (providerId) {
            "openrouter" -> {
                val d = get(baseUrl.trimEnd('/') + "/key").getJSONObject("data")
                fun money(k: String) = if (d.isNull(k)) null else "$" + String.format(Locale.US, "%.4f", d.optDouble(k)).trimEnd('0').trimEnd('.')
                val lines = mutableListOf<String>()
                lines += "Used total: " + (money("usage") ?: "$0")
                money("usage_daily")?.let { lines += "Used today (UTC): $it" }
                lines += "Credit limit: " + (money("limit") ?: "No limit")
                money("limit_remaining")?.let { lines += "Remaining: $it" }
                d.optString("limit_reset").takeIf { it.isNotBlank() && it != "null" }?.let { lines += "Limit resets: $it" }
                if (d.optBoolean("is_free_tier")) lines += "Free tier: free models capped per day (about 50 requests without credits)"
                Balance("OpenRouter account", lines)
            }
            "deepseek" -> {
                val o = get("https://api.deepseek.com/user/balance")
                val arr = o.optJSONArray("balance_infos")
                val lines = mutableListOf("Available: " + if (o.optBoolean("is_available")) "Yes" else "No")
                if (arr != null) for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    lines += "Balance: ${b.optString("total_balance")} ${b.optString("currency")}" +
                        " (topped up ${b.optString("topped_up_balance")}, granted ${b.optString("granted_balance")})"
                }
                Balance("DeepSeek account", lines)
            }
            else -> throw ApiException("This provider doesn't share balance with an API key")
        }
    }
}

/**
 * Delta accounting for one logical request (P1-5). When a stream drops mid-reply, the partial
 * usage is flushed in the error path; when the same request resumes and finishes, flushing the
 * full totals again would count the request (and the already-counted tokens) twice. Track what
 * was already flushed for the request and record only the delta; the request itself counts once.
 */
object UsageDelta {
    fun delta(alreadyFlushed: Counter, newTotal: Counter): Counter = Counter(
        requests = if (alreadyFlushed.requests > 0) 0 else newTotal.requests,
        input = (newTotal.input - alreadyFlushed.input).coerceAtLeast(0),
        output = (newTotal.output - alreadyFlushed.output).coerceAtLeast(0)
    )

    /** Running "already flushed" total after recording [newTotal]. */
    fun flushed(alreadyFlushed: Counter, newTotal: Counter): Counter = Counter(
        requests = maxOf(alreadyFlushed.requests, newTotal.requests),
        input = maxOf(alreadyFlushed.input, newTotal.input),
        output = maxOf(alreadyFlushed.output, newTotal.output)
    )
}
