package chud.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// ======================= data (mirrors ChudPlayer's JSON) =======================

/** A video / song. Same fields as ChudPlayer's VideoItem. */
@Serializable
data class Item(
    val url: String,
    val title: String,
    val uploader: String = "",
    val uploaderUrl: String? = null,
    val durationSec: Long = -1,
    val thumbnail: String? = null,
    val viewCount: Long = -1,
    val uploadDate: String? = null,
    val isLive: Boolean = false,
    val serviceId: Int = 0,
)

@Serializable
data class PlayerState(
    val version: String = "",
    val pc: String = "",
    val current: Item? = null,
    val index: Int = -1,
    val paused: Boolean = true,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val loading: Boolean = false,
    val error: String? = null,
    val volume: Float = 100f,
    val mode: String = "VIDEO",
    val hasVideo: Boolean = true,
    val repeatOne: Boolean = false,
    val shuffle: Boolean = false,
    val loopQueue: Boolean = false,
    val autoplay: Boolean = true,
    val queueRev: Int = 0,
    val queueSize: Int = 0,
    val queueSeconds: Long = 0,
)

@Serializable
data class QueueData(val index: Int = -1, val items: List<Item> = emptyList())

/** A search / list result: a video, a channel/artist or a playlist/album. */
@Serializable
data class Entry(
    val type: String,
    val url: String = "",
    val item: Item? = null,
    val name: String = "",
    val image: String? = null,
    val sub: String = "",
    val serviceId: Int = 0,
)

@Serializable
data class EntryPage(
    val title: String? = null,
    val image: String? = null,
    val items: List<Entry> = emptyList(),
    val next: String? = null,
    val hasPlaylists: Boolean = false,
)

@Serializable
data class PlaylistInfo(val id: String, val name: String, val count: Int = 0, val seconds: Long = 0, val thumb: String? = null)

@Serializable
data class Folder(val path: String, val name: String)

@Serializable
data class LocalDir(
    val path: String? = null,
    val name: String = "My Music",
    val parent: String? = null,
    val folders: List<Folder> = emptyList(),
    val tracks: List<Item> = emptyList(),
    val error: String? = null,
)

@Serializable
data class Ping(val ok: Boolean = false, val pc: String = "", val version: String = "")

/** Where ChudPlayer is and the pairing code. */
data class Connection(val host: String, val port: Int, val token: String, val name: String)

fun serviceName(id: Int) = when (id) { 1 -> "SoundCloud"; 100 -> "Squidify"; 200 -> "My Music"; else -> "YouTube" }
fun serviceShort(id: Int) = when (id) { 1 -> "SC"; 100 -> "SQ"; 200 -> "PC"; else -> "YT" }

// ======================= client =======================

class Api(val conn: Connection) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val streamClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    val base: String get() = "http://${conn.host}:${conn.port}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(path: String) = Request.Builder().url(base + path).header("X-Token", conn.token)

    private suspend fun <T> get(path: String, ser: KSerializer<T>): T = withContext(Dispatchers.IO) {
        client.newCall(request(path).build()).execute().use { r ->
            if (r.code == 401) throw IllegalStateException("Wrong pairing code")
            if (!r.isSuccessful) throw IllegalStateException("ChudPlayer answered ${r.code}")
            json.decodeFromString(ser, r.body!!.string())
        }
    }

    /** Thumbnail through ChudPlayer (works for YouTube, SoundCloud, Squidify and local album art). */
    fun img(url: String?): String? = url?.let { "$base/api/img?u=${enc(it)}&t=${enc(conn.token)}" }

    suspend fun ping(): Ping = get("/api/ping", Ping.serializer())
    suspend fun queue(): QueueData = get("/api/queue", QueueData.serializer())
    suspend fun search(q: String, service: Int, kind: String): EntryPage =
        get("/api/search?q=${enc(q)}&s=$service&k=$kind", EntryPage.serializer())
    suspend fun more(id: String): EntryPage = get("/api/more?id=${enc(id)}", EntryPage.serializer())
    suspend fun channel(url: String, tab: String): EntryPage = get("/api/channel?url=${enc(url)}&tab=$tab", EntryPage.serializer())
    suspend fun onlinePlaylist(url: String): EntryPage = get("/api/onlineplaylist?url=${enc(url)}", EntryPage.serializer())
    suspend fun playlists(): List<PlaylistInfo> = get("/api/playlists", ListSerializer(PlaylistInfo.serializer()))
    suspend fun playlist(id: String): EntryPage = get("/api/playlist?id=${enc(id)}", EntryPage.serializer())
    suspend fun history(q: String): EntryPage = get("/api/history?q=${enc(q)}", EntryPage.serializer())
    suspend fun local(path: String?): LocalDir = get("/api/local" + (path?.let { "?path=${enc(it)}" } ?: ""), LocalDir.serializer())

    /** Sends a command, e.g. cmd("next") or cmd("volume") { put("v", 50) }. */
    suspend fun cmd(action: String, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("a", action); extra() }.toString()
            .toRequestBody("application/json".toMediaType())
        client.newCall(request("/api/cmd").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("ChudPlayer answered ${r.code}")
            json.parseToJsonElement(r.body!!.string()) as JsonObject
        }
    }

    fun itemJson(i: Item): JsonElement = json.encodeToJsonElement(Item.serializer(), i)
    fun itemsJson(list: List<Item>): JsonElement = json.encodeToJsonElement(ListSerializer(Item.serializer()), list)

    /** Live player state pushed by ChudPlayer (Server-Sent Events). Ends when the connection drops. */
    fun events(): Flow<PlayerState> = flow {
        streamClient.newCall(request("/api/events").build()).execute().use { r ->
            if (r.code == 401) throw IllegalStateException("Wrong pairing code")
            if (!r.isSuccessful) throw IllegalStateException("ChudPlayer answered ${r.code}")
            val source = r.body!!.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) emit(json.decodeFromString(PlayerState.serializer(), line.removePrefix("data: ")))
            }
        }
    }.flowOn(Dispatchers.IO)
}
