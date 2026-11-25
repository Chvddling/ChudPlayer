package newpipe.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import newpipe.desktop.data.ChannelDetails
import newpipe.desktop.data.ListPage
import newpipe.desktop.data.PlaylistRef
import newpipe.desktop.data.SearchItem
import newpipe.desktop.data.Service
import newpipe.desktop.data.VideoItem
import newpipe.desktop.data.VideoResult
import newpipe.desktop.data.YouTubeRepo
import newpipe.desktop.update.YtDlp

/** A list that loads page by page (infinite scroll). */
class Pager(private val scope: CoroutineScope, private val first: () -> ListPage) {
    var items by mutableStateOf(listOf<SearchItem>()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var hasMore by mutableStateOf(false); private set
    var started = false; private set
    private var last: ListPage? = null

    fun startIfNeeded() {
        if (started) return
        started = true
        load { first() }
    }

    fun loadMore() {
        val l = last ?: return
        if (loading || !l.hasMore) return
        load { l.more() }
    }

    private fun load(block: () -> ListPage) {
        loading = true
        scope.launch {
            try {
                val page = withContext(Dispatchers.IO) { block() }
                append(page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                if (items.isEmpty()) error = e.message ?: e::class.simpleName
                hasMore = false
            } finally {
                loading = false
            }
        }
    }

    private fun append(page: ListPage) {
        last = page
        val seen = items.map { it.url }.toHashSet()
        items = items + page.items.filter { seen.add(it.url) }
        hasMore = page.hasMore
    }

    /** Loads further pages (up to [maxItems]) and returns every video loaded. */
    suspend fun loadAllVideos(maxItems: Int = 1000): List<VideoItem> {
        while (loading) kotlinx.coroutines.delay(50)
        loading = true
        try {
            while (true) {
                val l = last ?: break
                if (!l.hasMore || items.size >= maxItems) break
                val page = withContext(Dispatchers.IO) { l.more() }
                append(page)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            loading = false
        }
        return videos()
    }

    fun videos(): List<VideoItem> = items.filterIsInstance<VideoResult>().map { it.item }
}

/** Everything shown on a channel / artist page. */
class ChannelState(val url: String, private val repo: YouTubeRepo, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var tab by mutableStateOf(0)

    var videos by mutableStateOf<Pager?>(null); private set
    var playlists by mutableStateOf<Pager?>(null); private set

    // in-channel search
    var query by mutableStateOf("")
    var searchResults by mutableStateOf<List<VideoItem>?>(null); private set
    var searching by mutableStateOf(false); private set
    var searchNote by mutableStateOf(""); private set
    private var searchJob: Job? = null

    init {
        scope.launch {
            try {
                val d = withContext(Dispatchers.IO) { repo.channel(url) }
                details = d
                d.videos?.let { load -> videos = Pager(scope, load).also { it.startIfNeeded() } }
                d.playlists?.let { load -> playlists = Pager(scope, load) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                error = e.message ?: e::class.simpleName
            }
        }
    }

    fun selectTab(i: Int) {
        tab = i
        if (i == 1) playlists?.startIfNeeded()
    }

    /** Quick filter over what's loaded already (as you type). */
    fun localMatches(): List<VideoItem> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return (videos?.videos() ?: emptyList()).filter { v ->
            val t = v.title.lowercase()
            words.all { it in t }
        }
    }

    /** Full search of the channel (Enter in the channel search box). */
    fun deepSearch() {
        val q = query.trim()
        if (q.isEmpty()) { clearSearch(); return }
        val d = details ?: return
        searchJob?.cancel()
        searching = true
        searchResults = localMatches()
        searchNote = "Searching all of ${d.ref.name}…"
        searchJob = scope.launch {
            try {
                val viaYtDlp = d.ref.serviceId == Service.YOUTUBE.id && YtDlp.ready
                val found = if (viaYtDlp) {
                    withContext(Dispatchers.IO) { YtDlp.searchChannel(d.ref.url, q) }
                } else {
                    // Load more of the channel's uploads, then filter by title.
                    videos?.loadAllVideos(maxItems = 600)
                    localMatches()
                }
                searchResults = found
                searchNote = "${found.size} results for “$q” in ${d.ref.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                searchResults = localMatches()
                searchNote = "Full search failed — showing matches from loaded uploads"
            } finally {
                searching = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        query = ""
        searchResults = null
        searching = false
        searchNote = ""
    }
}

/** A YouTube/SoundCloud playlist opened from search or a channel. */
class RemotePlaylistState(val url: String, private val repo: YouTubeRepo, private val scope: CoroutineScope) {
    var ref by mutableStateOf<PlaylistRef?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var pager by mutableStateOf<Pager?>(null); private set
    var busy by mutableStateOf(false); private set

    init {
        scope.launch {
            try {
                val (r, first) = withContext(Dispatchers.IO) { repo.playlist(url) }
                ref = r
                pager = Pager(scope) { first }.also { it.startIfNeeded() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                error = e.message ?: e::class.simpleName
            }
        }
    }

    /** Loads the whole playlist, then hands the videos to [use]. */
    fun withAllVideos(use: (List<VideoItem>) -> Unit) {
        val p = pager ?: return
        scope.launch {
            busy = true
            try { use(p.loadAllVideos()) } finally { busy = false }
        }
    }
}
