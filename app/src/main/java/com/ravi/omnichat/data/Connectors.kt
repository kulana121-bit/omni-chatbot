package com.ravi.omnichat.data

import com.ravi.omnichat.security.boundedString
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One app the assistant can use. Tools marked as data send their result back to the model. */
data class Connector(
    val id: String,
    val name: String,
    val desc: String,
    val permissions: List<String> = emptyList(),
    val tools: String
)

data class ToolCall(val tool: String, val args: JSONObject, val raw: String)

object Connectors {
    val all = listOf(
        Connector("calendar", "Calendar", "See upcoming events and add new ones",
            listOf(Manifest.permission.READ_CALENDAR),
            """calendar_events {"days":7,"query":"optional word to filter"} → lists your events (data)
calendar_add {"title":"...","start":"YYYY-MM-DD HH:mm","end":"YYYY-MM-DD HH:mm","location":"..."} → opens the calendar with the event filled in"""),
        Connector("contacts", "Contacts", "Find people and their numbers",
            listOf(Manifest.permission.READ_CONTACTS),
            """contacts_search {"name":"..."} → names, numbers and emails (data)"""),
        Connector("phone", "Phone & messages", "Call, SMS, WhatsApp and email (you tap send)",
            emptyList(),
            """call {"to":"number or contact name"} → opens the dialer
sms {"to":"number or contact name","text":"..."} → opens SMS ready to send
whatsapp {"to":"number with country code or contact name (optional)","text":"..."} → opens WhatsApp ready to send
email {"to":"...","subject":"...","body":"..."} → opens the mail app ready to send"""),
        Connector("alarms", "Alarms & timers", "Set alarms and timers",
            emptyList(),
            """alarm {"hour":7,"minute":30,"label":"..."} → sets an alarm (24h clock)
timer {"seconds":300,"label":"..."} → starts a timer"""),
        Connector("maps", "Maps", "Search places and start navigation",
            emptyList(),
            """maps {"query":"place or address","navigate":false} → opens Maps"""),
        Connector("youtube", "YouTube & links", "Open YouTube searches, Spotify and web links",
            emptyList(),
            """youtube {"query":"..."} → opens YouTube search
spotify {"query":"..."} → opens Spotify search
open_url {"url":"https://..."} → opens a link"""),
        Connector("weather", "Weather", "Live weather and 3-day forecast (Open-Meteo)",
            emptyList(),
            """weather {"city":"..."} → current weather and forecast (data)"""),
        Connector("github", "GitHub", "Repos, issues and code search",
            emptyList(),
            """github {"action":"repos","user":"..."} | {"action":"search","query":"..."} | {"action":"issues","repo":"owner/name"} | {"action":"repo","repo":"owner/name"} (data)"""),
        Connector("news", "News", "Latest headlines on any topic (Google News)",
            emptyList(),
            """news {"query":"topic, or empty for top stories","lang":"en or hi"} → latest headlines with sources and time (data)"""),
        Connector("finance", "Money & crypto", "Live currency rates and crypto prices",
            emptyList(),
            """currency {"amount":100,"from":"USD","to":"INR"} → live exchange rate (data)
crypto {"coins":"bitcoin,ethereum","vs":"inr"} → live prices and 24h change (data)"""),
        Connector("wiki", "Wikipedia", "Quick facts and summaries",
            emptyList(),
            """wiki {"query":"...","lang":"en"} → summary of the best matching article (data)"""),
        Connector("location", "Location", "Your approximate city (from your internet connection)",
            emptyList(),
            """location {} → approximate city, region, country and timezone (data)"""),
        Connector("device", "Device", "Battery, storage, flashlight, open apps and settings",
            emptyList(),
            """device_info {} → battery, storage, network, phone model, date/time (data)
apps_list {"query":"optional filter"} → installed apps (data)
open_app {"name":"Instagram"} → opens an installed app by name
flashlight {"on":true} → turns the torch on or off
settings {"page":"wifi|bluetooth|display|sound|battery|location|apps|notifications|data"} → opens that settings page
share {"text":"..."} → opens the share sheet
copy {"text":"..."} → copies text to the clipboard""")
    )

    val dataTools = setOf("calendar_events", "contacts_search", "weather", "github", "device_info", "news", "currency", "crypto", "wiki", "location", "apps_list")

    private val owner = mapOf(
        "calendar_events" to "calendar", "calendar_add" to "calendar", "contacts_search" to "contacts",
        "call" to "phone", "sms" to "phone", "whatsapp" to "phone", "email" to "phone",
        "alarm" to "alarms", "timer" to "alarms", "maps" to "maps",
        "youtube" to "youtube", "spotify" to "youtube", "open_url" to "youtube",
        "weather" to "weather", "github" to "github", "device_info" to "device", "copy" to "device",
        "apps_list" to "device", "open_app" to "device", "flashlight" to "device", "settings" to "device", "share" to "device",
        "news" to "news", "currency" to "finance", "crypto" to "finance", "wiki" to "wiki", "location" to "location"
    )
    fun ownerOf(tool: String) = owner[tool]

    /**
     * Settings screen action for a [page]. ACTION_DATA_USAGE_SETTINGS only exists on API 28+;
     * on API 26/27 phones the intent would resolve to nothing, so fall back to the main Settings
     * screen instead of pretending the data-usage page opened.
     */
    fun settingsAction(page: String, sdkInt: Int = android.os.Build.VERSION.SDK_INT): String = when (page) {
        "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
        "display" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
        "sound" -> android.provider.Settings.ACTION_SOUND_SETTINGS
        "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
        "location" -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "apps" -> android.provider.Settings.ACTION_APPLICATION_SETTINGS
        "notifications" -> "android.settings.NOTIFICATION_SETTINGS"
        // Literal value of Settings.ACTION_DATA_USAGE_SETTINGS (an API 28 constant): referencing the
        // constant directly trips InlinedApi because lint cannot prove this parameter-based guard.
        "data" -> if (sdkInt >= 28) "android.settings.DATA_USAGE_SETTINGS" else android.provider.Settings.ACTION_SETTINGS
        else -> android.provider.Settings.ACTION_SETTINGS
    }

    val tagRx = Regex("""\[\[app:\s*([a-z_]+)\s*(\{.*?\})?\s*]]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun parse(text: String): List<ToolCall> = tagRx.findAll(text).map {
        ToolCall(it.groupValues[1].lowercase(), runCatching { JSONObject(it.groupValues[2].ifBlank { "{}" }) }.getOrDefault(JSONObject()), it.value)
    }.toList()

    fun prompt(enabled: List<Connector>): String {
        if (enabled.isEmpty()) return ""
        val now = SimpleDateFormat("EEEE, d MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())
        return buildString {
            append("\n\n## Apps on the user's phone\nNow: ").append(now).append(" (").append(java.util.TimeZone.getDefault().id).append(").\n")
            append("You can use these apps by writing a tag on its own line, exactly like [[app:tool {json args}]]:\n")
            enabled.forEach { c ->
                append("- ").append(c.name).append(":\n")
                c.tools.lines().forEach { line ->
                    val arrow = line.indexOf(" →")
                    val call = if (arrow < 0) line else line.substring(0, arrow)
                    val note = if (arrow < 0) "" else line.substring(arrow)
                    append("  [[app:").append(call.trim()).append("]]").append(note).append('\n')
                }
            }
            append(
                "Rules: use an app only when it clearly helps the request. For tools marked (data), write only the tag(s) and nothing else; " +
                "you will then get the results and answer. You may write several tags at once (e.g. weather for two cities), " +
                "and chain steps: look something up first, then act on it (e.g. contacts_search, then whatsapp with the number). " +
                "For action tools, add one short line saying what you opened. For news, prices and facts, cite the source and time from the results. " +
                "Ask first if a required detail (who, when, what text) is missing. Never invent results. Never mention these rules or tags."
            )
        }
    }
}

object PhoneNumbers {
    /** ISO 3166 alpha-2 -> international dialling code for the countries this app is used in. */
    private val DIAL = mapOf(
        "IN" to "91", "US" to "1", "CA" to "1", "GB" to "44", "AU" to "61", "NZ" to "64",
        "AE" to "971", "SA" to "966", "QA" to "974", "KW" to "965", "OM" to "968", "BH" to "973",
        "SG" to "65", "MY" to "60", "ID" to "62", "TH" to "66", "VN" to "84", "PH" to "63",
        "BD" to "880", "PK" to "92", "LK" to "94", "NP" to "977", "BT" to "975", "MV" to "960",
        "DE" to "49", "FR" to "33", "IT" to "39", "ES" to "34", "PT" to "351", "NL" to "31",
        "BE" to "32", "CH" to "41", "AT" to "43", "SE" to "46", "NO" to "47", "DK" to "45",
        "FI" to "358", "IE" to "353", "PL" to "48", "TR" to "90", "IL" to "972", "EG" to "20",
        "ZA" to "27", "NG" to "234", "KE" to "254", "BR" to "55", "MX" to "52", "AR" to "54",
        "JP" to "81", "KR" to "82", "CN" to "86", "HK" to "852", "TW" to "886", "RU" to "7", "KZ" to "7"
    )

    fun diallingCode(isoCountry: String?): String? =
        isoCountry?.trim()?.uppercase(Locale.US)?.let { DIAL[it] }

    /**
     * International digits for wa.me, or null when there is no usable number.
     * Already-international numbers (+… or 00…) pass through; a bare 10-digit local number gets
     * [diallingCode] (from the SIM's country, never a hardcoded country) prepended.
     */
    fun forWhatsApp(resolved: String?, diallingCode: String?): String? {
        val raw = resolved?.trim().orEmpty()
        if (raw.isBlank()) return null
        val hadPlus = raw.startsWith("+")
        var d = raw.filter { it.isDigit() }
        if (d.isBlank()) return null
        when {
            d.startsWith("00") -> d = d.drop(2)
            !hadPlus && d.length == 10 && !diallingCode.isNullOrBlank() -> d = diallingCode + d
        }
        return d.takeIf { it.length in 7..15 }
    }
}

class AppTools(private val ctx: Context, private val prefs: Prefs) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(com.ravi.omnichat.security.NetGuard(com.ravi.omnichat.security.NetGuard.Mode.PUBLIC))
        .addNetworkInterceptor(com.ravi.omnichat.security.NetGuard(com.ravi.omnichat.security.NetGuard.Mode.PUBLIC, network = true))
        .build()

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(tool: String): Boolean = Connectors.ownerOf(tool)?.let { prefs.connectorOn(it) } ?: false

    /** Runs a data tool and returns text for the model. */
    suspend fun runData(call: ToolCall): String = withContext(Dispatchers.IO) {
        if (!isEnabled(call.tool)) return@withContext "${call.tool}: this app is turned off in Apps & Skills in the side panel."
        runCatching {
            when (call.tool) {
                "calendar_events" -> calendarEvents(call.args.optInt("days", 7).coerceIn(1, 60), call.args.optString("query"))
                "contacts_search" -> contacts(call.args.optString("name")).ifBlank { "No matching contacts." }
                "weather" -> weather(call.args.optString("city"))
                "github" -> github(call.args)
                "device_info" -> deviceInfo()
                "news" -> news(call.args.optString("query"), call.args.optString("lang", "en"))
                "currency" -> currency(call.args.optDouble("amount", 1.0), call.args.optString("from", "USD"), call.args.optString("to", "INR"))
                "crypto" -> crypto(call.args.optString("coins", "bitcoin"), call.args.optString("vs", "inr"))
                "wiki" -> wiki(call.args.optString("query"), call.args.optString("lang", "en"))
                "location" -> location()
                "apps_list" -> appsList(call.args.optString("query"))
                else -> "Unknown tool"
            }
        }.getOrElse { "${call.tool} failed: ${it.message}" }
    }

    /** Runs an action tool on the main thread; returns a short note for the UI, or null if it failed. */
    fun runAction(call: ToolCall): String? {
        if (!isEnabled(call.tool)) return null
        val a = call.args
        return runCatching {
            when (call.tool) {
                "calendar_add" -> {
                    val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, a.optString("title"))
                    parseTime(a.optString("start"))?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                    parseTime(a.optString("end"))?.let { i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                    if (a.optString("location").isNotBlank()) i.putExtra(CalendarContract.Events.EVENT_LOCATION, a.optString("location"))
                    start(i); "Calendar event"
                }
                "call" -> { val n = number(a.optString("to")); if (n.isBlank()) return null; start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + n))); "Dialer" }
                "sms" -> {
                    val n = number(a.optString("to")); if (n.isBlank()) return null
                    start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + n)).putExtra("sms_body", a.optString("text"))); "SMS"
                }
                "whatsapp" -> {
                    val n = PhoneNumbers.forWhatsApp(number(a.optString("to")), localDiallingCode()) ?: return null
                    val url = "https://wa.me/$n?text=" + enc(a.optString("text"))
                    start(Intent(Intent.ACTION_VIEW, Uri.parse(url))); "WhatsApp"
                }
                "email" -> {
                    start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + a.optString("to")))
                        .putExtra(Intent.EXTRA_SUBJECT, a.optString("subject")).putExtra(Intent.EXTRA_TEXT, a.optString("body"))); "Email"
                }
                "alarm" -> {
                    start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour"))
                        .putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute")).putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "OmniChat"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)); "Alarm %02d:%02d".format(a.optInt("hour"), a.optInt("minute"))
                }
                "timer" -> {
                    start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds", 60))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "OmniChat")).putExtra(AlarmClock.EXTRA_SKIP_UI, true)); "Timer"
                }
                "maps" -> {
                    val q = enc(a.optString("query"))
                    val uri = if (a.optBoolean("navigate")) "google.navigation:q=$q" else "geo:0,0?q=$q"
                    runCatching { start(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                        .onFailure { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q"))) }
                    "Maps"
                }
                "youtube" -> { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc(a.optString("query"))))); "YouTube" }
                "spotify" -> {
                    val q = enc(a.optString("query"))
                    runCatching { start(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q"))) }
                        .onFailure { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$q"))) }
                    "Spotify"
                }
                "open_url" -> {
                    val u = a.optString("url"); if (!u.startsWith("http")) return null
                    start(Intent(Intent.ACTION_VIEW, Uri.parse(u))); "Link"
                }
                "open_app" -> {
                    val pm = ctx.packageManager
                    val name = a.optString("name")
                    val hit = launchables().minByOrNull { (label, _) ->
                        when { label.equals(name, true) -> 0; label.startsWith(name, true) -> 1; label.contains(name, true) -> 2; else -> 9 }
                    }?.takeIf { it.first.contains(name, true) } ?: return null
                    start(pm.getLaunchIntentForPackage(hit.second) ?: return null); hit.first
                }
                "flashlight" -> {
                    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return null
                    val on = a.optBoolean("on", true)
                    cm.setTorchMode(id, on); if (on) "Flashlight on" else "Flashlight off"
                }
                "settings" -> {
                    // If no activity can handle the action, start() throws and runCatching turns
                    // this into null — the UI then says "Couldn't do that", not a fake "Settings".
                    start(Intent(Connectors.settingsAction(a.optString("page")))); "Settings"
                }
                "share" -> {
                    start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.optString("text")), "Share")); "Share"
                }
                "copy" -> {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OmniChat", a.optString("text"))); "Copied"
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun start(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Dialling code for local numbers: SIM country first, Locale country as fallback. Never hardcoded. */
    private fun localDiallingCode(): String? {
        val tm = runCatching { ctx.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager }.getOrNull()
        val iso = tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country
        return PhoneNumbers.diallingCode(iso)
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun parseTime(s: String): Long? {
        if (s.isBlank()) return null
        listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd").forEach { f ->
            runCatching { return SimpleDateFormat(f, Locale.US).parse(s)!!.time }
        }
        return null
    }

    /** A name becomes the contact's first number when Contacts is allowed; numbers pass through. */
    private fun number(to: String): String {
        if (to.isBlank() || to.any { it.isDigit() } && to.count { it.isDigit() } >= 5) return to.replace(" ", "")
        if (!granted(Manifest.permission.READ_CONTACTS)) return ""
        return runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$to%"), null
            )?.use { if (it.moveToFirst()) it.getString(0).replace(" ", "") else "" } ?: ""
        }.getOrDefault("")
    }

    private fun calendarEvents(days: Int, query: String = ""): String {
        if (!granted(Manifest.permission.READ_CALENDAR)) return "Calendar permission not granted. Ask the user to allow it in Apps & Skills in the side panel."
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath((now + days * 86_400_000L).toString()).build()
        val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH)
        val out = StringBuilder()
        ctx.contentResolver.query(uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            var n = 0
            while (c.moveToNext() && n < 40) {
                if (query.isNotBlank() && !("${c.getString(0)} ${c.getString(3)}").contains(query, true)) continue
                n++
                out.append("- ").append(c.getString(0) ?: "(no title)").append(" — ")
                out.append(if (c.getInt(4) == 1) fmt.format(Date(c.getLong(1))).substringBefore(",") + " (all day)" else fmt.format(Date(c.getLong(1))) + "–" + SimpleDateFormat("HH:mm", Locale.US).format(Date(c.getLong(2))))
                c.getString(3)?.takeIf { it.isNotBlank() }?.let { out.append(" @ ").append(it) }
                out.append('\n')
            }
        }
        return if (out.isEmpty()) "No events in the next $days days." else "Events in the next $days days:\n$out"
    }

    private fun contacts(name: String): String {
        if (!granted(Manifest.permission.READ_CONTACTS)) return "Contacts permission not granted. Ask the user to allow it in Apps & Skills in the side panel."
        val out = LinkedHashMap<String, MutableSet<String>>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null
        )?.use { c -> while (c.moveToNext() && out.size < 10) out.getOrPut(c.getString(0) ?: "?") { mutableSetOf() }.add(c.getString(1).replace(" ", "")) }
        return out.entries.joinToString("\n") { "- ${it.key}: ${it.value.joinToString(", ")}" }
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url).header("User-Agent", "OmniChat-Android")
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            val body = r.boundedString()
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            return body
        }
    }

    private fun weather(city: String): String {
        if (city.isBlank()) return "Which city? Ask the user."
        val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + enc(city))).optJSONArray("results")
            ?.optJSONObject(0) ?: return "City not found: $city"
        val lat = g.getDouble("latitude"); val lon = g.getDouble("longitude")
        val w = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto&forecast_days=3" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,precipitation" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code"))
        val c = w.getJSONObject("current")
        val d = w.getJSONObject("daily")
        return buildString {
            append("Weather in ${g.optString("name")}, ${g.optString("admin1")}, ${g.optString("country")} (${c.optString("time")} local):\n")
            append("Now ${c.optDouble("temperature_2m")}°C (feels ${c.optDouble("apparent_temperature")}°C), ${code(c.optInt("weather_code"))}, ")
            append("humidity ${c.optInt("relative_humidity_2m")}%, wind ${c.optDouble("wind_speed_10m")} km/h\n")
            val days = d.getJSONArray("time")
            for (i in 0 until days.length()) {
                append("${days.getString(i)}: ${d.getJSONArray("temperature_2m_min").optDouble(i)}–${d.getJSONArray("temperature_2m_max").optDouble(i)}°C, ")
                append("${code(d.getJSONArray("weather_code").optInt(i))}, rain chance ${d.getJSONArray("precipitation_probability_max").optInt(i)}%\n")
            }
            append("Source: Open-Meteo")
        }
    }

    private fun code(c: Int) = when (c) {
        0 -> "clear sky"; 1, 2 -> "partly cloudy"; 3 -> "overcast"; 45, 48 -> "fog/haze"
        in 51..57 -> "drizzle"; in 61..67 -> "rain"; in 71..77 -> "snow"; in 80..82 -> "rain showers"
        in 95..99 -> "thunderstorm"; else -> "code $c"
    }

    private fun github(a: JSONObject): String {
        val h = if (prefs.githubToken.isNotBlank()) mapOf("Authorization" to "Bearer ${prefs.githubToken}") else emptyMap()
        val api = "https://api.github.com"
        return when (a.optString("action")) {
            "repos" -> {
                val user = a.optString("user")
                val arr = JSONArray(get(if (user.isBlank() && h.isNotEmpty()) "$api/user/repos?sort=updated&per_page=15" else "$api/users/${enc(user)}/repos?sort=updated&per_page=15", h))
                (0 until arr.length()).joinToString("\n") { val r = arr.getJSONObject(it); "- ${r.optString("full_name")} ★${r.optInt("stargazers_count")} ${r.optString("language")} — ${r.optString("description").take(100)}" }
                    .ifBlank { "No repos." }
            }
            "search" -> {
                val items = JSONObject(get("$api/search/repositories?per_page=8&q=" + enc(a.optString("query")), h)).getJSONArray("items")
                (0 until items.length()).joinToString("\n") { val r = items.getJSONObject(it); "- ${r.optString("full_name")} ★${r.optInt("stargazers_count")} — ${r.optString("description").take(120)} (${r.optString("html_url")})" }
                    .ifBlank { "No results." }
            }
            "issues" -> {
                val arr = JSONArray(get("$api/repos/${a.optString("repo")}/issues?state=open&per_page=10", h))
                (0 until arr.length()).joinToString("\n") { val r = arr.getJSONObject(it); "- #${r.optInt("number")} ${r.optString("title")} (${r.optJSONObject("user")?.optString("login")})" }
                    .ifBlank { "No open issues." }
            }
            else -> {
                val r = JSONObject(get("$api/repos/${a.optString("repo")}", h))
                "${r.optString("full_name")}: ${r.optString("description")}\n★${r.optInt("stargazers_count")} forks ${r.optInt("forks_count")} open issues ${r.optInt("open_issues_count")}, " +
                    "language ${r.optString("language")}, updated ${r.optString("pushed_at")}\n${r.optString("html_url")}"
            }
        }
    }

    private fun launchables(): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.distinctBy { it.second }
    }

    private fun appsList(q: String): String =
        launchables().filter { q.isBlank() || it.first.contains(q, true) }.map { it.first }.sorted().take(80).joinToString(", ").ifBlank { "No matching apps." }

    private fun tag(xml: String, t: String) = Regex("(?s)<$t[^>]*>(.*?)</$t>").find(xml)?.groupValues?.get(1)
        ?.replace("<![CDATA[", "")?.replace("]]>", "")?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")?.replace("&lt;", "<")?.replace("&gt;", ">")?.trim() ?: ""

    private fun news(q: String, lang: String): String {
        val hl = if (lang.startsWith("hi")) "hl=hi&gl=IN&ceid=IN:hi" else "hl=en-IN&gl=IN&ceid=IN:en"
        val url = if (q.isBlank()) "https://news.google.com/rss?$hl" else "https://news.google.com/rss/search?q=${enc(q)}&$hl"
        val xml = get(url)
        val items = Regex("(?s)<item>(.*?)</item>").findAll(xml).take(10).map { it.groupValues[1] }.toList()
        if (items.isEmpty()) return "No news found for \"$q\"."
        return "Latest news" + (if (q.isBlank()) "" else " on \"$q\"") + " (Google News):\n" + items.joinToString("\n") { i ->
            "- ${tag(i, "title")} (${tag(i, "source")}, ${tag(i, "pubDate").substringBeforeLast(":")}) ${tag(i, "link")}"
        }
    }

    private fun currency(amount: Double, from: String, to: String): String {
        val f = from.uppercase().trim(); val t = to.uppercase().trim()
        val o = JSONObject(get("https://open.er-api.com/v6/latest/$f"))
        if (o.optString("result") != "success") return "Currency $f not supported."
        val rate = o.getJSONObject("rates").optDouble(t, Double.NaN)
        if (rate.isNaN()) return "Currency $t not supported."
        return "%s %s = %.2f %s (rate %.4f, updated %s, source: ExchangeRate-API)".format(
            amount.toBigDecimal().stripTrailingZeros().toPlainString(), f, amount * rate, t, rate, o.optString("time_last_update_utc"))
    }

    private fun crypto(coins: String, vs: String): String {
        val ids = coins.lowercase().split(',').map { it.trim() }.filter { it.isNotBlank() }.map {
            mapOf("btc" to "bitcoin", "eth" to "ethereum", "sol" to "solana", "doge" to "dogecoin", "xrp" to "ripple", "bnb" to "binancecoin",
                "ada" to "cardano", "usdt" to "tether", "ton" to "the-open-network", "matic" to "matic-network", "pepe" to "pepe", "shib" to "shiba-inu")[it] ?: it
        }
        val v = vs.lowercase().trim()
        val o = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=${enc(ids.joinToString(","))}&vs_currencies=$v&include_24hr_change=true&include_last_updated_at=true"))
        if (o.length() == 0) return "Coin not found: $coins (use CoinGecko ids like bitcoin, ethereum)."
        return o.keys().asSequence().joinToString("\n") { k ->
            val c = o.getJSONObject(k)
            "- $k: ${c.optDouble(v)} ${v.uppercase()} (24h %+.2f%%)".format(c.optDouble("${v}_24h_change")) +
                ", updated " + SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH).format(Date(c.optLong("last_updated_at") * 1000))
        } + "\nSource: CoinGecko"
    }

    private fun wiki(q: String, lang: String): String {
        val l = lang.ifBlank { "en" }.take(3)
        val arr = JSONArray(get("https://$l.wikipedia.org/w/api.php?action=opensearch&limit=1&format=json&search=" + enc(q)))
        val title = arr.optJSONArray(1)?.optString(0)?.takeIf { it.isNotBlank() } ?: return "No Wikipedia article for \"$q\"."
        val s = JSONObject(get("https://$l.wikipedia.org/api/rest_v1/page/summary/" + enc(title.replace(' ', '_')).replace("+", "%20")))
        return "${s.optString("title")} (Wikipedia): ${s.optString("extract")}\n${s.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page") ?: ""}"
    }

    private fun location(): String {
        val o = JSONObject(get("https://ipwho.is/"))
        if (!o.optBoolean("success", true)) return "Location unavailable."
        return "Approximate location (from IP, may be off by a city): ${o.optString("city")}, ${o.optString("region")}, ${o.optString("country")}; " +
            "timezone ${o.optJSONObject("timezone")?.optString("id")}; coords ${o.optDouble("latitude")}, ${o.optDouble("longitude")}"
    }

    private fun deviceInfo(): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val now = SimpleDateFormat("EEEE d MMM yyyy, HH:mm", Locale.ENGLISH).format(Calendar.getInstance().time)
        return "Phone: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n" +
            "Battery: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%${if (bm.isCharging) " (charging)" else ""}\n" +
            run {
                val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                "Storage: %.1f GB free of %.1f GB\n".format(st.availableBytes / 1e9, st.totalBytes / 1e9)
            } +
            run {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                "Network: " + when {
                    caps == null -> "offline"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                    else -> "connected"
                } + "\n"
            } + "Now: $now"
    }
}
