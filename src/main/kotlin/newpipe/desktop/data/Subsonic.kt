package newpipe.desktop.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import newpipe.desktop.net.Http
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Client for Subsonic / Navidrome servers, used for Squidify (www.squidify.net, login Guest/Guest).
 * Items use their own link format so the rest of the app can tell them apart:
 *   squidify://song/<id>   squidify://album/<id>   squidify://artist/<id>
 */
class SubsonicClient(
    baseUrl: String,
    private val user: String,
    private val password: String,
    private val serviceId: Int = Service.SQUIDIFY.id,
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    companion object {
        const val SCHEME = "squidify://"
        fun isOurs(url: String) = url.startsWith(SCHEME)
        fun songUrl(id: String) = "${SCHEME}song/$id"
        fun albumUrl(id: String) = "${SCHEME}album/$id"
        fun artistUrl(id: String) = "${SCHEME}artist/$id"

        /** ("song", "abc") from squidify://song/abc */
        fun parse(url: String): Pair<String, String>? {
            if (!isOurs(url)) return null
            val rest = url.removePrefix(SCHEME)
            val slash = rest.indexOf('/')
            if (slash <= 0) return null
            return rest.substring(0, slash) to rest.substring(slash + 1)
        }
    }

    // ---------------- auth + requests ----------------

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Subsonic "token + salt" authentication. */
    private fun authParams(): String {
        val salt = (1..10).map { "abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)] }.joinToString("")
        return "u=${enc(user)}&t=${md5(password + salt)}&s=$salt&v=1.16.1&c=ChudPlayer&f=json"
    }

    fun url(method: String, params: Map<String, String> = emptyMap()): String =
        "$base/rest/$method?" + authParams() + params.entries.joinToString("") { "&${it.key}=${enc(it.value)}" }

    private fun call(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        val req = Request.Builder().url(url(method, params)).header("User-Agent", "ChudPlayer").build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Squidify answered ${resp.code}")
            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val r = root["subsonic-response"]?.jsonObject ?: throw IllegalStateException("Not a Subsonic server")
            if (r.str("status") != "ok") {
                val msg = r["error"]?.jsonObject?.str("message") ?: "unknown error"
                throw IllegalStateException("Squidify: $msg")
            }
            return r
        }
    }

    fun streamUrl(songId: String) = url("stream", mapOf("id" to songId))
    private fun coverUrl(coverId: String?, size: Int = 300) = coverId?.let { url("getCoverArt", mapOf("id" to it, "size" to "$size")) }

    // ---------------- conversion ----------------

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        runCatching { this[key]?.jsonPrimitive?.longOrNull ?: this[key]?.jsonPrimitive?.doubleOrNull?.toLong() }.getOrNull()

    /** A JSON field that may be an array or (in old servers) a single object. */
    private fun JsonObject.objects(key: String): List<JsonObject> = when (val v: JsonElement? = this[key]) {
        is JsonArray -> v.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(v)
        else -> emptyList()
    }

    private fun song(o: JsonObject): VideoItem? {
        val id = o.str("id") ?: return null
        val album = o.str("album")
        val year = o.long("year")?.takeIf { it > 0 }
        return VideoItem(
            url = songUrl(id),
            title = o.str("title") ?: "Untitled",
            uploader = o.str("artist") ?: o.str("displayArtist") ?: album.orEmpty(),
            uploaderUrl = o.str("artistId")?.let { artistUrl(it) },
            durationSec = o.long("duration") ?: -1,
            thumbnail = coverUrl(o.str("coverArt") ?: o.str("albumId")),
            viewCount = o.long("playCount") ?: -1,
            uploadDate = listOfNotNull(album, year?.toString()).joinToString(" · ").ifEmpty { null },
            serviceId = serviceId,
        )
    }

    private fun album(o: JsonObject): PlaylistRef? {
        val id = o.str("id") ?: return null
        val year = o.long("year")?.takeIf { it > 0 }
        return PlaylistRef(
            url = albumUrl(id),
            name = (o.str("name") ?: o.str("title") ?: "Album") + (year?.let { " ($it)" } ?: ""),
            uploader = o.str("artist").orEmpty(),
            uploaderUrl = o.str("artistId")?.let { artistUrl(it) },
            thumbnail = coverUrl(o.str("coverArt") ?: id),
            count = o.long("songCount") ?: -1,
            serviceId = serviceId,
        )
    }

    private fun artist(o: JsonObject): ChannelRef? {
        val id = o.str("id") ?: return null
        return ChannelRef(
            url = artistUrl(id),
            name = o.str("name") ?: "Artist",
            avatar = coverUrl(o.str("coverArt") ?: id) ?: o.str("artistImageUrl"),
            streamCount = o.long("albumCount") ?: -1,
            serviceId = serviceId,
        )
    }

    // ---------------- API ----------------

    /** search3, page by page (40 per page). */
    fun search(query: String, kind: SearchKind, offset: Int = 0): ListPage {
        val n = 40
        val params = mutableMapOf("query" to query, "songCount" to "0", "albumCount" to "0", "artistCount" to "0")
        when (kind) {
            SearchKind.ITEMS -> { params["songCount"] = "$n"; params["songOffset"] = "$offset" }
            SearchKind.PLAYLISTS -> { params["albumCount"] = "$n"; params["albumOffset"] = "$offset" }
            SearchKind.CHANNELS -> { params["artistCount"] = "$n"; params["artistOffset"] = "$offset" }
        }
        val r = call("search3", params)["searchResult3"]?.jsonObject ?: JsonObject(emptyMap())
        val items: List<SearchItem> = when (kind) {
            SearchKind.ITEMS -> r.objects("song").mapNotNull { song(it) }.map { VideoResult(it) }
            SearchKind.PLAYLISTS -> r.objects("album").mapNotNull { album(it) }
            SearchKind.CHANNELS -> r.objects("artist").mapNotNull { artist(it) }
        }
        return pageOf(items, full = items.size >= n) { search(query, kind, offset + n) }
    }

    private fun pageOf(items: List<SearchItem>, full: Boolean, next: () -> ListPage): ListPage =
        if (full) ListPage(items, org.schabi.newpipe.extractor.Page("next")) { next() } else ListPage(items, null, null)

    /** An album: its info and all of its songs. */
    fun album(id: String): Pair<PlaylistRef, List<VideoItem>> {
        val a = call("getAlbum", mapOf("id" to id))["album"]?.jsonObject ?: throw IllegalStateException("Album not found")
        val ref = album(a) ?: throw IllegalStateException("Album not found")
        return ref to a.objects("song").mapNotNull { song(it) }
    }

    /** One song plus the album it belongs to (used as "related" for autoplay). */
    fun song(id: String): Pair<VideoItem, List<VideoItem>> {
        val s = call("getSong", mapOf("id" to id))["song"]?.jsonObject ?: throw IllegalStateException("Song not found")
        val item = song(s) ?: throw IllegalStateException("Song not found")
        val albumSongs = s.str("albumId")?.let { aid -> runCatching { album(aid).second }.getOrNull() }.orEmpty()
        // Related = the rest of the album after this song, then the songs before it
        val i = albumSongs.indexOfFirst { it.url == item.url }
        val related = if (i >= 0) albumSongs.drop(i + 1) + albumSongs.take(i) else albumSongs.filter { it.url != item.url }
        return item.copy(thumbnail = coverUrl(s.str("coverArt") ?: s.str("albumId"), 600) ?: item.thumbnail) to related
    }

    /** An artist page: info, albums, and the songs of their albums (newest album first). */
    fun artist(id: String): ChannelDetails {
        val a = call("getArtist", mapOf("id" to id))["artist"]?.jsonObject ?: throw IllegalStateException("Artist not found")
        val ref = artist(a) ?: throw IllegalStateException("Artist not found")
        val albums = a.objects("album")
            .sortedByDescending { it.long("year") ?: 0 }
            .mapNotNull { album(it) }
        val albumIds = albums.mapNotNull { parse(it.url)?.second }

        // songs: one album per page, newest first
        fun songsFrom(i: Int): ListPage {
            if (i >= albumIds.size) return ListPage(emptyList(), null, null)
            val songs = runCatching { album(albumIds[i]).second }.getOrDefault(emptyList()).map { VideoResult(it) }
            return pageOf(songs, full = i + 1 < albumIds.size) { songsFrom(i + 1) }
        }

        return ChannelDetails(
            ref = ref.copy(streamCount = albums.size.toLong()),
            banner = null,
            videos = if (albumIds.isEmpty()) null else ({ songsFrom(0) }),
            playlists = if (albums.isEmpty()) null else ({ ListPage(albums, null, null) }),
        )
    }
}
