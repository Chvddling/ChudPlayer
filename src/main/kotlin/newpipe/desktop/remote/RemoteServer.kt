package newpipe.desktop.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import newpipe.desktop.data.ChannelRef
import newpipe.desktop.data.HistoryStore
import newpipe.desktop.data.ListPage
import newpipe.desktop.data.LocalMusic
import newpipe.desktop.data.PlaylistRef
import newpipe.desktop.data.PlaylistStore
import newpipe.desktop.data.SearchItem
import newpipe.desktop.data.SearchKind
import newpipe.desktop.data.Service
import newpipe.desktop.data.VideoItem
import newpipe.desktop.data.VideoResult
import newpipe.desktop.data.YouTubeRepo
import newpipe.desktop.net.Http
import newpipe.desktop.player.PlayerController
import newpipe.desktop.update.AppInfo
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The phone remote's connection point: a tiny HTTP server built into Java (no extra library).
 * Off unless turned on in Settings. Every request must carry the pairing code (token).
 *
 *   GET  /api/ping, /api/state, /api/events (live state, Server-Sent Events), /api/queue
 *   GET  /api/search, /api/more, /api/channel, /api/onlineplaylist
 *   GET  /api/playlists, /api/playlist, /api/history, /api/local, /api/img
 *   POST /api/cmd   {"a": "toggle" | "next" | … }
 */
class RemoteServer(
    private val player: PlayerController,
    private val playlists: PlaylistStore,
    private val history: HistoryStore,
    private val repo: YouTubeRepo,
    private val musicRoot: () -> File?,
) {
    var running by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var port by mutableStateOf(0); private set
    var token by mutableStateOf(""); private set

    private var server: HttpServer? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val itemSer = VideoItem.serializer()
    private val itemsSer = ListSerializer(VideoItem.serializer())

    /** Search/channel pages the phone can continue ("load more"). */
    private val pages = object : LinkedHashMap<String, ListPage>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ListPage>?) = size > 60
    }

    companion object {
        fun newToken(): String {
            val r = SecureRandom()
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..8).map { chars[r.nextInt(chars.length)] }.joinToString("")
        }

        val pcName: String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("ChudPlayer PC")

        /** This PC's addresses on the home network, most likely first. */
        fun lanAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .filterNot { n -> listOf("vmware", "virtualbox", "hyper-v", "vethernet", "wsl", "docker").any { n.displayName.lowercase().contains(it) } }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .map { it.hostAddress }
                .sortedBy { if (it.startsWith("192.168.")) 0 else if (it.startsWith("10.")) 1 else 2 }
                .distinct()
        }.getOrDefault(emptyList())
    }

    /** What the QR code contains. */
    fun pairingLink(): String =
        "chudplayer://pair?h=${lanAddresses().joinToString(",")}&p=$port&t=$token&n=${java.net.URLEncoder.encode(pcName, Charsets.UTF_8)}"

    fun start(port: Int, token: String) {
        stop()
        try {
            val s = HttpServer.create(InetSocketAddress(port), 16)
            s.executor = Executors.newCachedThreadPool { r -> Thread(r, "remote-http").apply { isDaemon = true } }
            s.createContext("/") { ex -> handle(ex) }
            s.start()
            server = s
            this.port = port
            this.token = token
            running = true
            error = null
        } catch (e: Exception) {
            running = false
            error = "Couldn't start on port $port: ${e.message}"
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        running = false
    }

    // ======================= request handling =======================

    private fun <T> ui(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }

    private fun query(ex: HttpExchange): Map<String, String> =
        ex.requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
        }

    private fun send(ex: HttpExchange, code: Int, body: String, type: String = "application/json; charset=utf-8") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", type)
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun sendJson(ex: HttpExchange, el: JsonElement) = send(ex, 200, el.toString())

    private fun handle(ex: HttpExchange) {
        try {
            val q = query(ex)
            val given = ex.requestHeaders.getFirst("X-Token") ?: q["t"]
            if (given != token) {
                send(ex, 401, """{"error":"wrong pairing code"}""")
                return
            }
            val path = ex.requestURI.path
            when {
                path == "/api/ping" -> sendJson(ex, buildJsonObject { put("ok", true); put("pc", pcName); put("version", AppInfo.version) })
                path == "/api/state" -> sendJson(ex, stateJson())
                path == "/api/events" -> events(ex)
                path == "/api/queue" -> sendJson(ex, queueJson())
                path == "/api/cmd" && ex.requestMethod == "POST" -> {
                    val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                    val o = json.parseToJsonElement(body).jsonObject
                    val result = command(o)
                    sendJson(ex, result ?: buildJsonObject { put("ok", true) })
                }
                path == "/api/search" -> sendJson(ex, search(q))
                path == "/api/more" -> sendJson(ex, more(q["id"].orEmpty()))
                path == "/api/channel" -> sendJson(ex, channel(q["url"].orEmpty(), q["tab"] ?: "videos"))
                path == "/api/onlineplaylist" -> sendJson(ex, onlinePlaylist(q["url"].orEmpty()))
                path == "/api/playlists" -> sendJson(ex, playlistsJson())
                path == "/api/playlist" -> sendJson(ex, playlistJson(q["id"].orEmpty()))
                path == "/api/history" -> sendJson(ex, historyJson(q["q"].orEmpty()))
                path == "/api/local" -> sendJson(ex, localJson(q["path"]))
                path == "/api/img" -> image(ex, q["u"].orEmpty())
                else -> send(ex, 404, """{"error":"not found"}""")
            }
        } catch (e: IOException) {
            // phone went away
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching { send(ex, 500, buildJsonObject { put("error", e.message ?: e::class.simpleName ?: "error") }.toString()) }
        } finally {
            runCatching { ex.close() }
        }
    }

    // ======================= state =======================

    private fun item(v: VideoItem): JsonElement = json.encodeToJsonElement(itemSer, v)

    private fun stateJson(): JsonObject = ui {
        val cur = player.details?.item ?: player.current
        buildJsonObject {
            put("version", AppInfo.version)
            put("pc", pcName)
            put("current", cur?.let { item(it) } ?: JsonNull)
            put("index", player.index)
            put("paused", player.paused)
            put("position", player.position)
            put("duration", player.duration)
            put("loading", player.loading)
            player.error?.let { put("error", it) }
            put("volume", player.volume)
            put("mode", player.mode.name)
            put("hasVideo", player.hasVideo)
            put("repeatOne", player.repeatOne)
            put("shuffle", player.shuffle)
            put("loopQueue", player.loopQueue)
            put("autoplay", player.autoplay)
            put("queueRev", player.queue.map { it.url }.hashCode() * 31 + player.index)
            put("queueSize", player.queue.size)
            put("queueSeconds", player.queueSeconds)
        }
    }

    private fun queueJson(): JsonObject = ui {
        buildJsonObject {
            put("index", player.index)
            put("items", json.encodeToJsonElement(itemsSer, player.queue))
        }
    }

    /** Server-Sent Events: pushes the state whenever it changes (and a heartbeat every few seconds). */
    private fun events(ex: HttpExchange) {
        ex.responseHeaders.add("Content-Type", "text/event-stream")
        ex.responseHeaders.add("Cache-Control", "no-cache")
        ex.sendResponseHeaders(200, 0)
        val out = ex.responseBody
        var last = ""
        var quiet = 0
        while (running) {
            val s = stateJson().toString()
            if (s != last || quiet >= 10) {
                out.write("data: $s\n\n".toByteArray(Charsets.UTF_8))
                out.flush()
                last = s
                quiet = 0
            } else quiet++
            Thread.sleep(500)
        }
    }

    // ======================= commands =======================

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
    private fun JsonObject.dbl(k: String) = this[k]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.bool(k: String) = this[k]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.video(k: String = "item"): VideoItem? = this[k]?.let { json.decodeFromJsonElement(itemSer, it) }
    private fun JsonObject.videos(k: String = "items"): List<VideoItem> = this[k]?.let { json.decodeFromJsonElement(itemsSer, it) }.orEmpty()

    private fun command(o: JsonObject): JsonObject? = ui {
        when (o.str("a")) {
            "toggle" -> player.togglePause()
            "next" -> player.next()
            "prev" -> player.previous()
            "seek" -> o.dbl("v")?.let { player.seek(it) }
            "seekRel" -> o.int("v")?.let { player.seekRelative(it) }
            "volume" -> o.dbl("v")?.let { player.changeVolume(it.toFloat().coerceIn(0f, 150f), save = true) }
            "repeatOne" -> player.changeRepeatOne(o.bool("on") ?: !player.repeatOne)
            "shuffle" -> player.changeShuffle(o.bool("on") ?: !player.shuffle)
            "loopQueue" -> player.changeLoopQueue(o.bool("on") ?: !player.loopQueue)
            "autoplay" -> player.changeAutoplay(o.bool("on") ?: !player.autoplay)
            "audio" -> Unit // always audio only now
            "play" -> o.video()?.let { player.addAndPlay(it) }
            "playNext" -> o.video()?.let { player.playNext(it) }
            "enqueue" -> o.video()?.let { player.enqueue(it) }
            "background" -> o.video()?.let { player.playInBackground(it) }
            "jump" -> o.int("i")?.let { player.jumpTo(it) }
            "remove" -> o.int("i")?.let { player.removeAt(it) }
            "move" -> { val f = o.int("from"); val t = o.int("to"); if (f != null && t != null) player.moveInQueue(f, t) }
            "clear" -> player.stop()
            "playAll" -> player.playCollection(o.videos(), start = o.int("start") ?: 0, shuffle = o.bool("shuffle") == true, audioOnly = o.bool("audio") == true)
            "playPlaylist" -> playlists.get(o.str("id").orEmpty())?.let {
                player.playCollection(it.items, shuffle = o.bool("shuffle") == true, audioOnly = o.bool("audio") == true)
            }
            "addToPlaylist" -> playlists.add(o.str("id").orEmpty(), o.videos().ifEmpty { listOfNotNull(o.video()) })
            "createPlaylist" -> {
                val items = if (o.bool("fromQueue") == true) player.queue else o.videos()
                val p = playlists.create(o.str("name").orEmpty(), items)
                return@ui buildJsonObject { put("ok", true); put("id", p.id) }
            }
            else -> return@ui buildJsonObject { put("ok", false); put("error", "unknown command") }
        }
        null
    }

    // ======================= search, channels, online playlists =======================

    private fun entry(s: SearchItem): JsonObject = when (s) {
        is VideoResult -> buildJsonObject { put("type", "video"); put("item", item(s.item)); put("url", s.url) }
        is ChannelRef -> buildJsonObject {
            put("type", "channel"); put("url", s.url); put("name", s.name); s.avatar?.let { put("image", it) }
            put("serviceId", s.serviceId)
            put("sub", listOfNotNull(s.subscribers.takeIf { it >= 0 }?.let { "$it subscribers" }).joinToString())
        }
        is PlaylistRef -> buildJsonObject {
            put("type", "playlist"); put("url", s.url); put("name", s.name); s.thumbnail?.let { put("image", it) }
            put("serviceId", s.serviceId)
            put("sub", listOf(s.uploader, if (s.count >= 0) "${s.count} items" else "").filter { it.isNotEmpty() }.joinToString(" · "))
        }
    }

    private fun page(p: ListPage, title: String? = null): JsonObject {
        val id = if (p.hasMore) UUID.randomUUID().toString().also { synchronized(pages) { pages[it] = p } } else null
        return buildJsonObject {
            title?.let { put("title", it) }
            put("items", JsonArray(p.items.map { entry(it) }))
            id?.let { put("next", it) }
        }
    }

    private fun search(q: Map<String, String>): JsonObject {
        val service = Service.of(q["s"]?.toIntOrNull() ?: 0)
        val kind = runCatching { SearchKind.valueOf(q["k"] ?: "ITEMS") }.getOrDefault(SearchKind.ITEMS)
        return page(repo.search(q["q"].orEmpty(), service, kind))
    }

    private fun more(id: String): JsonObject {
        val p = synchronized(pages) { pages.remove(id) } ?: return buildJsonObject { put("items", JsonArray(emptyList())) }
        return page(p.more())
    }

    private fun channel(url: String, tab: String): JsonObject {
        val d = repo.channel(url)
        val loader = if (tab == "playlists") d.playlists else d.videos
        val p = loader?.invoke() ?: ListPage(emptyList(), null, null)
        return buildJsonObject {
            put("title", d.ref.name)
            d.ref.avatar?.let { put("image", it) }
            put("hasPlaylists", d.playlists != null)
            val inner = page(p)
            inner.forEach { (k, v) -> put(k, v) }
        }
    }

    private fun onlinePlaylist(url: String): JsonObject {
        val (ref, first) = repo.playlist(url)
        // load everything (up to 500) so the phone can "play all"
        val items = first.items.toMutableList()
        var p = first
        while (p.hasMore && items.size < 500) {
            p = p.more()
            items += p.items
        }
        return buildJsonObject {
            put("title", ref.name)
            ref.thumbnail?.let { put("image", it) }
            put("items", JsonArray(items.map { entry(it) }))
        }
    }

    // ======================= library =======================

    private fun playlistsJson(): JsonArray = ui {
        buildJsonArray {
            playlists.playlists.forEach { p ->
                add(buildJsonObject {
                    put("id", p.id)
                    put("name", p.name)
                    put("count", p.items.size)
                    put("seconds", p.items.sumOf { it.durationSec.coerceAtLeast(0) })
                    p.items.firstOrNull()?.thumbnail?.let { put("thumb", it) }
                })
            }
        }
    }

    private fun playlistJson(id: String): JsonObject = ui {
        val p = playlists.get(id)
        buildJsonObject {
            put("title", p?.name ?: "Playlist")
            put("items", JsonArray(p?.items.orEmpty().map { buildJsonObject { put("type", "video"); put("item", item(it)); put("url", it.url) } }))
        }
    }

    private fun historyJson(filter: String): JsonObject = ui {
        val words = filter.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val list = history.entries.map { it.item }.filter { v ->
            val t = (v.title + " " + v.uploader).lowercase()
            words.all { it in t }
        }.take(300)
        buildJsonObject {
            put("title", "History")
            put("items", JsonArray(list.map { buildJsonObject { put("type", "video"); put("item", item(it)); put("url", it.url) } }))
        }
    }

    private fun localJson(path: String?): JsonObject {
        val root = ui { musicRoot() }
        val dir = path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory } ?: root
            ?: return buildJsonObject { put("name", "My Music"); put("error", "Choose a music folder in ChudPlayer's Settings") }
        val insideRoot = root != null && dir.absolutePath != root.absolutePath && dir.absolutePath.startsWith(root.absolutePath)
        return buildJsonObject {
            put("path", dir.absolutePath)
            put("name", dir.name.ifEmpty { dir.absolutePath })
            if (insideRoot) dir.parentFile?.let { put("parent", it.absolutePath) }
            put("folders", buildJsonArray {
                LocalMusic.subfolders(dir).forEach { f -> add(buildJsonObject { put("path", f.absolutePath); put("name", f.name) }) }
            })
            put("tracks", JsonArray(LocalMusic.audioFiles(dir).map { item(LocalMusic.item(it, LocalMusic.tags(it))) }))
        }
    }

    /** Thumbnails for the phone (online images, or album art from local files). */
    private fun image(ex: HttpExchange, u: String) {
        val bytes: ByteArray? = when {
            u.startsWith(LocalMusic.COVER_PREFIX) -> LocalMusic.cover(File(u.removePrefix(LocalMusic.COVER_PREFIX)))
            u.startsWith("https://") || u.startsWith("http://") -> runCatching {
                Http.client.newCall(Request.Builder().url(u).header("User-Agent", Http.USER_AGENT).build()).execute().use {
                    if (it.isSuccessful) it.body?.bytes() else null
                }
            }.getOrNull()
            else -> null
        }
        if (bytes == null) {
            ex.sendResponseHeaders(404, -1)
            return
        }
        ex.responseHeaders.add("Content-Type", "image/jpeg")
        ex.responseHeaders.add("Cache-Control", "max-age=86400")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    @Suppress("unused")
    private fun prim(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }
}
