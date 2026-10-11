package com.jmsbrain.subcalscriber.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Feed(
    val id: String = "",
    val name: String = "",
    val tag: String = "",
    val url: String = "",
    val updatedAt: Long? = null,
    val error: String? = null,
)

/** An occurrence plus the feed it came from. */
data class AgendaItem(val feed: Feed, val event: Occurrence)

/** Feeds and their downloaded .ics files, all inside the tool. Only the feed URLs are ever fetched. */
object Calendar {
    private lateinit var dir: File
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val parsed = mutableMapOf<String, List<IcsEvent>>()
    private val client by lazy { HttpClient(OkHttp) { expectSuccess = false } }

    private val _feeds = MutableStateFlow<List<Feed>>(emptyList())
    val feeds: StateFlow<List<Feed>> = _feeds

    private val feedsFile get() = File(dir, "feeds.json")
    private fun icsFile(id: String) = File(dir, "feeds/$id.ics")

    fun init(filesDir: File) {
        if (::dir.isInitialized) return
        dir = filesDir
        _feeds.value = if (feedsFile.isFile) {
            runCatching { json.decodeFromString<List<Feed>>(feedsFile.readText()) }.getOrDefault(emptyList())
        } else emptyList()
    }

    fun feed(id: String) = _feeds.value.firstOrNull { it.id == id }

    private suspend fun write(list: List<Feed>) {
        _feeds.value = list
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "feeds.json.tmp")
            tmp.writeText(json.encodeToString(list))
            tmp.renameTo(feedsFile)
        }
    }

    suspend fun save(feed: Feed): Feed = lock.withLock {
        val saved = if (feed.id.isBlank()) feed.copy(id = UUID.randomUUID().toString()) else feed
        val old = feed(saved.id)
        write(_feeds.value.map { if (it.id == saved.id) saved else it }.let { if (old == null) it + saved else it })
        if (old != null && old.url != saved.url) {
            parsed.remove(saved.id)
            withContext(Dispatchers.IO) { icsFile(saved.id).delete() }
        }
        saved
    }

    suspend fun delete(id: String) = lock.withLock {
        parsed.remove(id)
        withContext(Dispatchers.IO) { icsFile(id).delete() }
        write(_feeds.value.filterNot { it.id == id })
    }

    private class FeedError(message: String) : Exception(message)

    private class Download(val url: String, val status: Int, val body: String)

    private suspend fun get(url: String): Download {
        val host = hostOf(url)
        try {
            val response = client.get(url) { header("Accept", "text/calendar, */*") }
            return Download(response.call.request.url.toString(), response.status.value, response.bodyAsText())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FeedError(
                when (e) {
                    is javax.net.ssl.SSLException, is java.security.cert.CertificateException ->
                        "The phone doesn't trust the security certificate of $host."
                    is java.net.UnknownHostException -> "Couldn't reach $host. Check the address and that the phone is online."
                    is java.net.SocketTimeoutException -> "$host took too long to answer."
                    else -> "Couldn't connect to $host."
                },
            )
        }
    }

    /**
     * Fetches the calendar behind [link]: follows short links and turns Google
     * share links into the calendar's .ics address. Returns the final address and the file.
     */
    private suspend fun fetchCalendar(link: String): Pair<String, String> {
        var d = get(googleIcsUrl(httpUrl(link)) ?: httpUrl(link))
        if (!d.body.contains("BEGIN:VCALENDAR")) {
            googleIcsUrl(d.url)?.let { ics ->
                d = get(ics)
                if (d.status == 404 || d.status == 403) throw FeedError(GOOGLE_PRIVATE)
            }
        }
        if (d.status !in 200..299) {
            throw FeedError(
                if (hostOf(d.url).endsWith("google.com") && (d.status == 404 || d.status == 403)) GOOGLE_PRIVATE
                else "${hostOf(d.url)} said ${d.status}. Check the address."
            )
        }
        if (!d.body.contains("BEGIN:VCALENDAR")) {
            throw FeedError("That link opens a web page, not a calendar feed. Look for an address ending in .ics or starting with webcal://.")
        }
        return d.url to d.body
    }

    /** Downloads one feed. Keeps the old copy if anything goes wrong. */
    suspend fun refresh(id: String) {
        val feed = feed(id) ?: return
        val result = runCatching {
            val (finalUrl, body) = fetchCalendar(feed.url)
            withContext(Dispatchers.IO) {
                icsFile(id).parentFile?.mkdirs()
                icsFile(id).writeText(body)
            }
            lock.withLock { parsed[id] = withContext(Dispatchers.Default) { Ics.parse(body) } }
            finalUrl
        }
        lock.withLock {
            val current = feed(id) ?: return@withLock
            val updated = result.fold(
                // Keep the real address so short links aren't needed again.
                onSuccess = { current.copy(url = if (resolvedIsBetter(current.url, it)) it else current.url, updatedAt = System.currentTimeMillis(), error = null) },
                onFailure = { current.copy(error = if (it is FeedError) it.message else "Couldn't download this feed.") },
            )
            write(_feeds.value.map { if (it.id == id) updated else it })
        }
    }

    // Google share links and short links that land on a calendar file; not other redirects,
    // which may be temporary.
    private fun resolvedIsBetter(original: String, resolved: String): Boolean {
        val o = httpUrl(original)
        if (o == resolved) return false
        if (googleIcsUrl(o) != null) return true
        return hostOf(o) != hostOf(resolved) && (".ics" in resolved || "/ical/" in resolved)
    }

    /**
     * Adds each new link as a feed, named after the calendar (or its web address)
     * with a free letter. Returns how many were added.
     */
    suspend fun addLinks(links: List<Link>): Int {
        val known = _feeds.value.map { httpUrl(it.url) }.toMutableSet()
        var added = 0
        for (link in links) {
            if (!known.add(httpUrl(link.url))) continue
            val host = runCatching { java.net.URI(httpUrl(link.url)).host?.removePrefix("www.") }.getOrNull() ?: "Calendar"
            val first = link.name ?: host
            val feed = save(Feed(name = first, tag = freeTag(first, usedTags()), url = link.url))
            refresh(feed.id)
            val name = if (link.name != null) null else withContext(Dispatchers.IO) {
                icsFile(feed.id).takeIf { it.isFile }?.readText()?.let(Ics::calendarName)
            }
            if (name != null) {
                lock.withLock {
                    val current = feed(feed.id) ?: return@withLock
                    val used = _feeds.value.filter { it.id != feed.id }.map { it.tag }.toSet()
                    write(_feeds.value.map { if (it.id == feed.id) current.copy(name = name, tag = freeTag(name, used)) else it })
                }
            }
            added++
        }
        return added
    }

    private fun usedTags() = _feeds.value.map { it.tag }.toSet()

    suspend fun agenda(from: ZonedDateTime, to: ZonedDateTime, zone: ZoneId): List<AgendaItem> {
        val feeds = _feeds.value
        val events = lock.withLock {
            feeds.associate { f ->
                f.id to parsed.getOrPut(f.id) {
                    withContext(Dispatchers.IO) {
                        icsFile(f.id).takeIf { it.isFile }?.readText()?.let(Ics::parse).orEmpty()
                    }
                }
            }
        }
        return withContext(Dispatchers.Default) {
            feeds.flatMap { f -> Ics.expand(events[f.id].orEmpty(), from, to, zone).map { AgendaItem(f, it) } }
                .sortedWith(compareBy({ it.event.start }, { !it.event.allDay }, { it.event.title }))
        }
    }

    /** webcal:// links are just https, and plain http is tried as https (Android blocks http). */
    fun httpUrl(url: String): String {
        val u = url.trim()
        return when {
            u.startsWith("webcals://", true) -> "https://" + u.substring(10)
            u.startsWith("webcal://", true) -> "https://" + u.substring(9)
            u.startsWith("http://", true) -> "https://" + u.substring(7)
            u.contains("://") -> u
            else -> "https://$u"
        }
    }

    fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: "the server"

    /**
     * Google share and embed links (…?cid=… or …?src=…) point at a web page.
     * The calendar itself is at /calendar/ical/<id>/public/basic.ics, if it's public.
     */
    fun googleIcsUrl(url: String): String? {
        if (!hostOf(url).endsWith("google.com")) return null
        val decoded = generateSequence(url) { java.net.URLDecoder.decode(it, "UTF-8").takeIf { d -> d != it } }.last()
        val raw = Regex("""[?&](?:cid|src)=([^&\s]+)""").find(decoded)?.groupValues?.get(1) ?: return null
        val id = if ('@' in raw) raw else runCatching {
            String(java.util.Base64.getUrlDecoder().decode(raw.replace('+', '-').replace('/', '_').trimEnd('=')))
        }.getOrNull()
        if (id == null || '@' !in id) return null
        return "https://calendar.google.com/calendar/ical/" + java.net.URLEncoder.encode(id, "UTF-8") + "/public/basic.ics"
    }

    const val GOOGLE_PRIVATE =
        "This Google calendar isn't public, so it needs its secret address. On a computer, open Google Calendar → " +
            "Settings → click the calendar → Integrate calendar → copy \"Secret address in iCal format\"."
}

/** Calendar links in any text: https://, http:// or webcal:// (also finds them in .url shortcut files). */
/** A link found in uploaded text, with the name written before it on the same line ("Work: https://…"). */
data class Link(val url: String, val name: String?)

fun findLinks(text: String): List<Link> {
    val pattern = Regex("""(?i)\b(?:https?|webcals?)://[^\s"'<>]+""")
    return text.lines().flatMap { line ->
        pattern.findAll(line).mapIndexed { i, m ->
            val before = if (i == 0) line.substring(0, m.range.first).trim().trim(':', '|', '-', '=', ',', '(', '"').trim() else ""
            val name = before.takeIf { it.isNotEmpty() && !it.equals("URL", true) }
            Link(m.value.trimEnd('.', ',', ')', ';'), name)
        }.toList()
    }.distinctBy { it.url }
}

/** One letter or digit, uppercased. */
fun tagFrom(text: String): String = text.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()

/** First letter of [name] no other feed uses, else any free letter. */
fun freeTag(name: String, used: Set<String>): String =
    name.filter { it.isLetterOrDigit() }.map { it.uppercase() }.firstOrNull { it !in used }
        ?: ('A'..'Z').map { it.toString() }.firstOrNull { it !in used }
        ?: tagFrom(name)
