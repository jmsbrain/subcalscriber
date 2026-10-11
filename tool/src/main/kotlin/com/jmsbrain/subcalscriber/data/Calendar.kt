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

    /** Downloads one feed. Keeps the old copy if anything goes wrong. */
    suspend fun refresh(id: String) {
        val feed = feed(id) ?: return
        val result = runCatching {
            val response = client.get(httpUrl(feed.url)) { header("Accept", "text/calendar, */*") }
            if (!response.status.isSuccess()) error("Server said ${response.status.value}")
            val body = response.bodyAsText()
            if (!body.contains("BEGIN:VCALENDAR")) error("Not a calendar feed")
            withContext(Dispatchers.IO) {
                icsFile(id).parentFile?.mkdirs()
                icsFile(id).writeText(body)
            }
            lock.withLock { parsed[id] = withContext(Dispatchers.Default) { Ics.parse(body) } }
        }
        lock.withLock {
            val current = feed(id) ?: return@withLock
            val updated = result.fold(
                onSuccess = { current.copy(updatedAt = System.currentTimeMillis(), error = null) },
                onFailure = { current.copy(error = it.message ?: "Couldn't download") },
            )
            write(_feeds.value.map { if (it.id == id) updated else it })
        }
    }

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

    /** webcal:// links are just https. */
    fun httpUrl(url: String): String {
        val u = url.trim()
        return when {
            u.startsWith("webcals://", true) -> "https://" + u.substring(10)
            u.startsWith("webcal://", true) -> "https://" + u.substring(9)
            u.contains("://") -> u
            else -> "https://$u"
        }
    }
}

/** One letter or digit, uppercased. */
fun tagFrom(text: String): String = text.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()

/** First letter of [name] no other feed uses, else any free letter. */
fun freeTag(name: String, used: Set<String>): String =
    name.filter { it.isLetterOrDigit() }.map { it.uppercase() }.firstOrNull { it !in used }
        ?: ('A'..'Z').map { it.toString() }.firstOrNull { it !in used }
        ?: tagFrom(name)
