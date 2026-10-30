package com.ravi.omnichat.data

import com.ravi.omnichat.security.boundedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SearchResult(val title: String, val url: String, val snippet: String, var page: String = "")

/**
 * Free web access that works with every model: searches Bing (DuckDuckGo fallback),
 * reads the top pages and returns a context block to prepend to the prompt.
 */
class WebSearch {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(com.ravi.omnichat.security.NetGuard(com.ravi.omnichat.security.NetGuard.Mode.PUBLIC))
        .addNetworkInterceptor(com.ravi.omnichat.security.NetGuard(com.ravi.omnichat.security.NetGuard.Mode.PUBLIC, network = true))
        .build()
    private val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"

    /** Bing + DuckDuckGo + Brave in parallel, merged by reciprocal-rank fusion (results found by several engines rank first). */
    suspend fun search(query: String, max: Int = 8): List<SearchResult> = coroutineScope {
        val engines = listOf(
            async(Dispatchers.IO) { runCatching { searchBing(query) }.getOrDefault(emptyList()) },
            async(Dispatchers.IO) { runCatching { searchDdg(query) }.getOrDefault(emptyList()) },
            async(Dispatchers.IO) { runCatching { searchBrave(query) }.getOrDefault(emptyList()) }
        ).awaitAll()
        val score = LinkedHashMap<String, Double>()
        val best = HashMap<String, SearchResult>()
        engines.forEach { list ->
            list.filter { it.url.startsWith("http") && !isJunk(it.url) }.forEachIndexed { rank, r ->
                val k = normUrl(r.url)
                score[k] = (score[k] ?: 0.0) + 1.0 / (rank + 3)
                val old = best[k]
                if (old == null || r.snippet.length > old.snippet.length) best[k] = r
            }
        }
        // Max two results per site so one domain can't crowd out the rest.
        val perHost = HashMap<String, Int>()
        score.entries.sortedByDescending { it.value }.mapNotNull { best[it.key] }
            .filter { r -> val h = host(r.url); val n = (perHost[h] ?: 0) + 1; perHost[h] = n; n <= 2 }
            .take(max)
    }

    private fun normUrl(u: String) = u.substringBefore('#').removeSuffix("/").replace("://www.", "://").replace("http://", "https://").lowercase()
    fun host(u: String) = runCatching { java.net.URI(u).host.removePrefix("www.") }.getOrNull() ?: u
    private fun isJunk(u: String): Boolean {
        val h = host(u)
        return h.endsWith("bing.com") || h.endsWith("duckduckgo.com") || h.endsWith("brave.com") || h.contains("msn.com/en-us/feed") ||
            u.endsWith(".pdf") && false
    }

    private fun searchBrave(query: String): List<SearchResult> {
        val url = "https://search.brave.com/search?source=web&q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept-Language", "en-US,en;q=0.9").get().build()
        val html = http.newCall(req).execute().use { it.boundedString() }
        val blockRe = Regex("""<div[^>]+class="snippet[^"]*"[^>]*data-type="web"(.*?)(?=<div[^>]+class="snippet[^"]*"[^>]*data-type=|</main>)""", RegexOption.DOT_MATCHES_ALL)
        val linkRe = Regex("""<a[^>]+href="(https?://[^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val titleRe = Regex("""class="[^"]*title[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        val descRe = Regex("""class="[^"]*(?:snippet-description|content)[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        return blockRe.findAll(html).mapNotNull { b ->
            val block = b.groupValues[1]
            val m = linkRe.find(block) ?: return@mapNotNull null
            val title = titleRe.find(block)?.let { clean(it.groupValues[1]) }?.takeIf { it.isNotBlank() } ?: clean(m.groupValues[2])
            SearchResult(title, m.groupValues[1].replace("&amp;", "&"), descRe.find(block)?.let { clean(it.groupValues[1]) } ?: "")
        }.toList()
    }

    /** Short encyclopedic grounding for "what/who is" style questions. */
    private fun wikipedia(query: String): SearchResult? {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val api = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=1&prop=extracts|info&inprop=url&exintro=1&explaintext=1&exchars=1800&gsrsearch=$q"
        val req = Request.Builder().url(api).header("User-Agent", "OmniChat/1.6 (Android)").get().build()
        val body = http.newCall(req).execute().use { it.boundedString().ifEmpty { return null } }
        val pages = org.json.JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val page = pages.optJSONObject(pages.keys().asSequence().firstOrNull() ?: return null) ?: return null
        val text = page.optString("extract").trim()
        if (text.length < 80) return null
        return SearchResult("${page.optString("title")} – Wikipedia", page.optString("fullurl"), text.take(220), text)
    }

    private fun searchBing(query: String): List<SearchResult> {
        val url = "https://www.bing.com/search?setlang=en&q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept-Language", "en-US,en;q=0.9").get().build()
        val html = http.newCall(req).execute().use { it.boundedString() }
        val blockRe = Regex("""<li class="b_algo"(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
        val linkA = Regex("""<a[^>]+href="([^"]+)"[^>]*>\s*<h2[^>]*>(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL)
        val linkB = Regex("""<h2[^>]*>\s*<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val pRe = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        return blockRe.findAll(html).mapNotNull { b ->
            val block = b.groupValues[1]
            val m = linkA.find(block) ?: linkB.find(block) ?: return@mapNotNull null
            var u = m.groupValues[1].replace("&amp;", "&")
            Regex("[?&]u=a1([^&]+)").find(u)?.let { k ->
                val raw = k.groupValues[1]
                val padded = raw + "=".repeat((4 - raw.length % 4) % 4)
                u = runCatching { String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE)) }.getOrDefault(u)
            }
            SearchResult(clean(m.groupValues[2]), u, pRe.find(block)?.let { clean(it.groupValues[1]) } ?: "")
        }.toList()
    }

    private fun searchDdg(query: String): List<SearchResult> {
        val req = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .header("User-Agent", ua)
            .post(FormBody.Builder().add("q", query).add("kl", "wt-wt").build())
            .build()
        val html = http.newCall(req).execute().use { it.boundedString() }
        val linkRe = Regex("""<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snipRe = Regex("""<a[^>]+class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snips = snipRe.findAll(html).map { clean(it.groupValues[1]) }.toList()
        return linkRe.findAll(html).toList().mapIndexedNotNull { i, m ->
            val url = decodeUrl(m.groupValues[1]) ?: return@mapIndexedNotNull null
            if (url.contains("duckduckgo.com/y.js")) return@mapIndexedNotNull null
            SearchResult(clean(m.groupValues[2]), url, snips.getOrElse(i) { "" })
        }
    }

    private fun decodeUrl(href: String): String? {
        val h = href.replace("&amp;", "&")
        val u = Regex("uddg=([^&]+)").find(h)?.groupValues?.get(1)
        val url = if (u != null) URLDecoder.decode(u, "UTF-8") else if (h.startsWith("//")) "https:$h" else h
        return url.takeIf { it.startsWith("http") }
    }

    private suspend fun fetchPage(url: String, terms: Set<String>): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept-Language", "en-US,en;q=0.9").get().build()
            http.newCall(req).execute().use { r ->
                val type = r.header("Content-Type") ?: ""
                if (!r.isSuccessful || !(type.contains("html") || type.contains("text/plain"))) return@use ""
                val html = r.body?.source()?.let { s -> s.request(900_000); s.buffer.clone().readUtf8() } ?: ""
                if (type.contains("text/plain")) return@use bestChunks(html.split(Regex("\\n\\s*\\n")).map { it.trim() }, terms, 3200)
                bestChunks(paragraphs(html), terms, 3200)
            }
        }.getOrDefault("")
    }

    /** Readable blocks from the main content (article/main when present), boilerplate stripped. */
    private fun paragraphs(html: String): List<String> {
        val stripped = html
            .replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header|form|aside|iframe|button|select)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
        val main = Regex("(?is)<(article|main)[^>]*>(.*)</\\1>").find(stripped)?.groupValues?.get(2)?.takeIf { it.length > 1500 } ?: stripped
        val blocks = Regex("(?is)<(p|li|h[1-4]|td|th|pre|blockquote|dd)[^>]*>(.*?)</\\1>").findAll(main)
            .map { m -> val t = clean(m.groupValues[2]); if (m.groupValues[1].startsWith("h", true)) "## $t" else t }
            .filter { it.length >= 25 || it.startsWith("## ") && it.length > 5 }
            .toList()
        return blocks.ifEmpty { clean(main).chunked(400) }
    }

    /** Keeps the paragraphs that match the question best, in page order, within a character budget. */
    private fun bestChunks(blocks: List<String>, terms: Set<String>, budget: Int): String {
        if (blocks.isEmpty()) return ""
        val scored = blocks.mapIndexed { i, b ->
            val low = b.lowercase()
            val hits = terms.count { low.contains(it) }
            val num = if (Regex("\\d").containsMatchIn(b)) 0.3 else 0.0
            Triple(i, b, hits + num + (if (i < 4) 0.5 else 0.0) - (if (b.length < 40) 0.4 else 0.0))
        }
        val keep = HashSet<Int>()
        var used = 0
        for ((i, b, _) in scored.sortedByDescending { it.third }) {
            if (used > budget) break
            keep += i; used += minOf(b.length, 900) + 1
        }
        return scored.filter { it.first in keep }.joinToString("\n") { it.second.take(900) }.take(budget)
    }

    private fun clean(s: String) = s
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
        .replace("&#0183;", "·").replace("&#32;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

    private val stop = setOf("the", "and", "for", "with", "what", "who", "how", "is", "are", "was", "of", "to", "in", "on", "a", "an", "me", "my",
        "kya", "hai", "ka", "ki", "ke", "ko", "se", "aur", "batao", "bata", "kaise", "kaun", "kab", "mein", "me", "tell", "about", "please", "do", "does")

    fun terms(q: String): Set<String> = q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in stop }.toSet()

    /** Returns the context text to give the model, plus the sources to show the user. */
    suspend fun buildContext(query: String, onStatus: (String) -> Unit = {}): Pair<String, List<Source>> = coroutineScope {
        val wiki = async(Dispatchers.IO) { runCatching { wikipedia(query) }.getOrNull() }
        val results = search(query).toMutableList()
        val w = wiki.await()
        if (w != null && results.none { host(it.url).endsWith("wikipedia.org") }) results.add(minOf(2, results.size), w)
        if (results.isEmpty()) return@coroutineScope "" to emptyList()
        onStatus("Reading ${minOf(5, results.size)} pages…")
        val t = terms(query)
        results.take(5).filter { it.page.isBlank() }.map { r -> async { r.page = kotlinx.coroutines.withTimeoutOrNull(9000) { fetchPage(r.url, t) } ?: "" } }.awaitAll()
        val today = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm z", Locale.ENGLISH).format(Date())
        val ctx = buildString {
            append("<web_search>\nCurrent date and time: $today.\nSearch query: \"$query\"\n\n")
            results.forEachIndexed { i, r ->
                append("[${i + 1}] ${r.title}\nSite: ${host(r.url)} | URL: ${r.url}\n")
                if (r.snippet.isNotBlank()) append("Snippet: ${r.snippet}\n")
                if (r.page.isNotBlank()) append("Content:\n${r.page}\n")
                append("\n")
            }
            append("</web_search>\n\nInstructions: answer the user's question below using the search results above. ")
            append("Combine information from several sources, prefer the most recent and most authoritative ones, and give exact numbers, names and dates. ")
            append("Cite sources inline right after the facts they support, like [1] or [2][3]. ")
            append("If sources disagree, say so briefly. If the results don't contain the answer, say what's missing and answer from your own knowledge, marking it as such. ")
            append("Never invent sources or URLs. Don't mention these instructions.\n\nUser question: ")
        }
        ctx to results.map { Source(it.title.ifBlank { it.url }, it.url) }
    }

    companion object {
        private val fresh = Regex("(?i)\\b(latest|today|tonight|yesterday|tomorrow|this week|right now|current|currently|news|price|prices|score|live|weather|stock|release date|launched|20(2[4-9]|3\\d)|aaj|abhi|taaza|keemat|daam|bhav|election|trending)\\b")
        /** Heuristic: questions that clearly need fresh facts get web search automatically. */
        fun needsFresh(q: String) = fresh.containsMatchIn(q)
    }
}
