package chud.remote

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

enum class Tab { PLAYING, QUEUE, SEARCH, LIBRARY }

/** A pushed-on-top list page (channel, online playlist, local playlist, history results…). */
class DetailPage(
    val title: String,
    val image: String? = null,
    val entries: List<Entry> = emptyList(),
    val next: String? = null,
    val playlistId: String? = null,
    val channelUrl: String? = null,
    val channelTab: String = "videos",
    val hasPlaylists: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("remote", Context.MODE_PRIVATE)

    // ---------------- connection ----------------
    var connection by mutableStateOf(loadConnection()); private set
    var api: Api? by mutableStateOf(connection?.let { Api(it) }); private set
    var connected by mutableStateOf(false); private set
    var connectError by mutableStateOf<String?>(null); private set
    var pairing by mutableStateOf(false); private set
    var volumeKeys by mutableStateOf(prefs.getBoolean("volumeKeys", true)); private set

    /** "pc" = follow the PC's theme, else a theme id. */
    var themeChoice by mutableStateOf(prefs.getString("theme", "pc") ?: "pc"); private set

    fun changeTheme(id: String) {
        themeChoice = id
        prefs.edit().putString("theme", id).apply()
    }
    private var liveJob: Job? = null

    // ---------------- player ----------------
    var state by mutableStateOf(PlayerState()); private set
    var queue by mutableStateOf<List<Item>>(emptyList()); private set
    private var queueRev = Int.MIN_VALUE
    var tab by mutableStateOf(Tab.PLAYING)
    var message by mutableStateOf<String?>(null)

    // ---------------- search ----------------
    var query by mutableStateOf("")
    var service by mutableStateOf(0)
    var kind by mutableStateOf("ITEMS")
    var results by mutableStateOf<List<Entry>>(emptyList()); private set
    var resultsNext by mutableStateOf<String?>(null); private set
    var searching by mutableStateOf(false); private set

    // ---------------- library ----------------
    var librarySection by mutableStateOf(0)        // 0 playlists, 1 history, 2 my music
    var playlists by mutableStateOf<List<PlaylistInfo>>(emptyList()); private set
    var historyFilter by mutableStateOf("")
    var history by mutableStateOf<List<Entry>>(emptyList()); private set
    var localDir by mutableStateOf<LocalDir?>(null); private set
    var libraryLoading by mutableStateOf(false); private set

    // ---------------- overlays ----------------
    var detail by mutableStateOf<DetailPage?>(null); private set
    private val detailStack = ArrayDeque<DetailPage>()
    var sheetItem by mutableStateOf<Item?>(null)          // action sheet for this song
    var pickPlaylistFor by mutableStateOf<List<Item>?>(null) // "add to playlist" chooser

    init {
        if (connection != null) startLive()
    }

    // ======================= pairing =======================

    private fun loadConnection(): Connection? {
        val host = prefs.getString("host", null) ?: return null
        return Connection(host, prefs.getInt("port", 8765), prefs.getString("token", "") ?: "", prefs.getString("name", "") ?: "")
    }

    /** chudplayer://pair?h=192.168.1.5,10.0.0.3&p=8765&t=ABCD1234&n=DESKTOP */
    fun pairFromQr(text: String) {
        val uri = runCatching { Uri.parse(text) }.getOrNull()
        if (uri == null || uri.scheme != "chudplayer") { connectError = "That's not a ChudPlayer QR code."; return }
        val hosts = uri.getQueryParameter("h")?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val port = uri.getQueryParameter("p")?.toIntOrNull() ?: 8765
        val token = uri.getQueryParameter("t").orEmpty()
        val name = uri.getQueryParameter("n").orEmpty()
        pair(hosts, port, token, name)
    }

    fun pairManual(host: String, port: String, token: String) =
        pair(listOf(host.trim()), port.trim().toIntOrNull() ?: 8765, token.trim().uppercase(), "")

    private fun pair(hosts: List<String>, port: Int, token: String, name: String) {
        if (hosts.isEmpty()) { connectError = "No address in the code."; return }
        pairing = true
        connectError = null
        viewModelScope.launch {
            var ok: Connection? = null
            var lastError: String? = null
            for (h in hosts) {
                val c = Connection(h, port, token, name)
                try {
                    val p = Api(c).ping()
                    ok = c.copy(name = p.pc.ifEmpty { name })
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message
                }
            }
            pairing = false
            if (ok == null) {
                connectError = "Couldn't reach ChudPlayer (${lastError ?: "no answer"}). Same Wi-Fi? Phone remote turned on?"
                return@launch
            }
            prefs.edit().putString("host", ok.host).putInt("port", ok.port).putString("token", ok.token).putString("name", ok.name).apply()
            connection = ok
            api = Api(ok)
            startLive()
        }
    }

    fun forget() {
        liveJob?.cancel()
        prefs.edit().remove("host").remove("token").apply()
        connection = null
        api = null
        connected = false
        state = PlayerState()
        queue = emptyList()
    }

    fun changeVolumeKeys(on: Boolean) {
        volumeKeys = on
        prefs.edit().putBoolean("volumeKeys", on).apply()
    }

    /** Keeps a live connection, reconnecting every couple of seconds if it drops. */
    private fun startLive() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            while (true) {
                val a = api ?: return@launch
                try {
                    a.events().collect { s ->
                        connected = true
                        connectError = null
                        state = s
                        if (s.queueRev != queueRev) {
                            queueRev = s.queueRev
                            launch { runCatching { queue = a.queue().items } }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    connectError = e.message
                }
                connected = false
                delay(2000)
            }
        }
    }

    // ======================= commands =======================

    private fun send(action: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) {
        val a = api ?: return
        viewModelScope.launch {
            try { a.cmd(action, extra) } catch (e: CancellationException) { throw e } catch (e: Exception) { message = "Couldn't send: ${e.message}" }
        }
    }

    fun toggle() = send("toggle")
    fun next() = send("next")
    fun prev() = send("prev")
    fun seek(sec: Double) { state = state.copy(position = sec); send("seek") { put("v", sec) } }
    fun volume(v: Float) { state = state.copy(volume = v); send("volume") { put("v", v) } }
    fun volumeStep(delta: Int) = volume((state.volume + delta).coerceIn(0f, 100f))
    fun setMode(name: String, on: Boolean) = send(name) { put("on", on) }
    fun audioOnly(on: Boolean) = send("audio") { put("on", on) }

    fun play(i: Item) { send("play") { put("item", api!!.itemJson(i)) }; message = "Playing “${i.title}”" }
    fun playNext(i: Item) { send("playNext") { put("item", api!!.itemJson(i)) }; message = "Plays next: ${i.title}" }
    fun enqueue(i: Item) { send("enqueue") { put("item", api!!.itemJson(i)) }; message = "Added to queue: ${i.title}" }
    fun background(i: Item) { send("background") { put("item", api!!.itemJson(i)) }; message = "Playing audio only" }
    fun playAll(items: List<Item>, start: Int = 0, shuffle: Boolean = false, audio: Boolean = false) {
        if (items.isEmpty()) return
        send("playAll") { put("items", api!!.itemsJson(items)); put("start", start); put("shuffle", shuffle); put("audio", audio) }
        tab = Tab.PLAYING
    }

    fun jump(i: Int) = send("jump") { put("i", i) }
    fun remove(i: Int) {
        queue = queue.toMutableList().also { if (i in it.indices) it.removeAt(i) }
        send("remove") { put("i", i) }
    }
    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        send("move") { put("from", from); put("to", to) }
    }
    fun clearQueue() = send("clear")

    fun playPlaylist(id: String, shuffle: Boolean = false, audio: Boolean = false) {
        send("playPlaylist") { put("id", id); put("shuffle", shuffle); put("audio", audio) }
        tab = Tab.PLAYING
    }

    fun addToPlaylist(id: String, items: List<Item>) {
        send("addToPlaylist") { put("id", id); put("items", api!!.itemsJson(items)) }
        message = "Added to playlist"
        pickPlaylistFor = null
    }

    fun createPlaylist(name: String, items: List<Item>?, fromQueue: Boolean = false) {
        send("createPlaylist") {
            put("name", name)
            if (fromQueue) put("fromQueue", true) else put("items", api!!.itemsJson(items.orEmpty()))
        }
        message = "Playlist “$name” created"
        pickPlaylistFor = null
        viewModelScope.launch { delay(400); loadPlaylists() }
    }

    // ======================= search =======================

    fun search() {
        val a = api ?: return
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        results = emptyList()
        resultsNext = null
        viewModelScope.launch {
            try {
                val p = a.search(q, service, kind)
                results = p.items
                resultsNext = p.next
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                message = "Search failed: ${e.message}"
            } finally { searching = false }
        }
    }

    fun searchMore() {
        val a = api ?: return
        val id = resultsNext ?: return
        if (searching) return
        searching = true
        resultsNext = null
        viewModelScope.launch {
            try {
                val p = a.more(id)
                results = results + p.items.filter { n -> results.none { it.url == n.url } }
                resultsNext = p.next
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
            } finally { searching = false }
        }
    }

    // ======================= library =======================

    fun loadPlaylists() {
        val a = api ?: return
        viewModelScope.launch { runCatching { playlists = a.playlists() } }
    }

    fun loadHistory() {
        val a = api ?: return
        viewModelScope.launch { runCatching { history = a.history(historyFilter).items } }
    }

    fun openLocal(path: String?) {
        val a = api ?: return
        libraryLoading = true
        viewModelScope.launch {
            try { localDir = a.local(path) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                message = "Couldn't open folder: ${e.message}"
            } finally { libraryLoading = false }
        }
    }

    // ======================= detail pages =======================

    private fun pushDetail(p: DetailPage) {
        detail?.let { detailStack.addLast(it) }
        detail = p
    }

    fun closeDetail() {
        detail = detailStack.removeLastOrNull()
    }

    fun closeAllDetails() {
        detailStack.clear()
        detail = null
    }

    private fun loadDetail(placeholder: DetailPage, load: suspend (Api) -> EntryPage) {
        val a = api ?: return
        pushDetail(placeholder.let { DetailPage(it.title, it.image, playlistId = it.playlistId, channelUrl = it.channelUrl, channelTab = it.channelTab, loading = true) })
        val mine = detail
        viewModelScope.launch {
            val result = try {
                val p = load(a)
                DetailPage(
                    title = p.title ?: placeholder.title,
                    image = p.image ?: placeholder.image,
                    entries = p.items,
                    next = p.next,
                    playlistId = placeholder.playlistId,
                    channelUrl = placeholder.channelUrl,
                    channelTab = placeholder.channelTab,
                    hasPlaylists = p.hasPlaylists,
                )
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                DetailPage(placeholder.title, error = e.message ?: "Error", channelUrl = placeholder.channelUrl)
            }
            if (detail === mine) detail = result
        }
    }

    fun openChannel(url: String, name: String = "Channel", tab: String = "videos") {
        if (url.startsWith("folder:")) {
            this.tab = Tab.LIBRARY; librarySection = 2; openLocal(url.removePrefix("folder:")); return
        }
        loadDetail(DetailPage(name, channelUrl = url, channelTab = tab)) { it.channel(url, tab) }
    }

    fun switchChannelTab(tabName: String) {
        val d = detail ?: return
        val url = d.channelUrl ?: return
        closeDetail()
        openChannel(url, d.title, tabName)
    }

    fun openOnlinePlaylist(url: String, name: String) = loadDetail(DetailPage(name)) { it.onlinePlaylist(url) }
    fun openPlaylist(p: PlaylistInfo) = loadDetail(DetailPage(p.name, playlistId = p.id)) { it.playlist(p.id) }

    fun detailMore() {
        val a = api ?: return
        val d = detail ?: return
        val id = d.next ?: return
        detail = DetailPage(d.title, d.image, d.entries, null, d.playlistId, d.channelUrl, d.channelTab, d.hasPlaylists, loading = true)
        viewModelScope.launch {
            val more = runCatching { a.more(id) }.getOrNull()
            val cur = detail ?: return@launch
            detail = DetailPage(cur.title, cur.image, cur.entries + more?.items.orEmpty(), more?.next, cur.playlistId, cur.channelUrl, cur.channelTab, cur.hasPlaylists)
        }
    }

}
