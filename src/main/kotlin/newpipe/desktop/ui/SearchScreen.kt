package newpipe.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import newpipe.desktop.data.ListPage
import newpipe.desktop.data.SearchItem
import newpipe.desktop.data.SearchKind
import newpipe.desktop.data.Service
import newpipe.desktop.data.VideoResult
import newpipe.desktop.data.YouTubeRepo
import newpipe.desktop.update.YtDlp

/** Search state lives outside the screen so results survive navigating away and back. */
class SearchState(private val repo: YouTubeRepo, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    var service by mutableStateOf(Service.YOUTUBE); private set
    var kind by mutableStateOf(SearchKind.ITEMS); private set
    var lastQuery by mutableStateOf(""); private set
    var results by mutableStateOf(listOf<SearchItem>()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var hasMore by mutableStateOf(false); private set
    /** true when NewPipe's search failed and these results came from yt-dlp. */
    var fromBackup by mutableStateOf(false); private set
    private var page: ListPage? = null
    private var job: Job? = null

    fun changeService(s: Service) {
        if (s == service) return
        service = s
        if (lastQuery.isNotEmpty() && s != Service.LOCAL) search(lastQuery)
    }

    fun changeKind(k: SearchKind) {
        if (k == kind) return
        kind = k
        if (lastQuery.isNotEmpty()) search(lastQuery)
    }

    fun search(q: String = query.trim()) {
        if (q.isEmpty()) return
        job?.cancel()
        lastQuery = q
        loading = true; error = null; results = emptyList(); hasMore = false; page = null; fromBackup = false
        val svc = service
        val k = kind
        job = scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { repo.search(q, svc, k) }
                page = p; results = p.items; hasMore = p.hasMore
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                // NewPipeExtractor broken? Ask the yt-dlp backup instead (videos/tracks only).
                val backup = if (k == SearchKind.ITEMS && svc != Service.SQUIDIFY && YtDlp.ready) {
                    runCatching { withContext(Dispatchers.IO) { YtDlp.search(q, svc) } }.getOrNull()
                } else null
                if (!backup.isNullOrEmpty()) {
                    results = backup.map { VideoResult(it) }; fromBackup = true
                } else {
                    error = e.message ?: "Search failed"
                }
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val p = page ?: return
        if (loading || !p.hasMore) return
        loading = true
        job = scope.launch {
            try {
                val more = withContext(Dispatchers.IO) { p.more() }
                page = more
                val seen = results.map { it.url }.toHashSet()
                results = results + more.items.filter { seen.add(it.url) }
                hasMore = more.hasMore
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace(); hasMore = false
            } finally {
                loading = false
            }
        }
    }

    fun kindLabels(): List<String> = when (service) {
        Service.YOUTUBE -> listOf("Videos", "Channels", "Playlists")
        Service.SOUNDCLOUD -> listOf("Tracks", "Artists", "Playlists")
        Service.SQUIDIFY -> listOf("Songs", "Artists", "Albums")
        Service.LOCAL -> emptyList()
    }

    /** Text for the service bar, e.g. 24 results for “outrun”. */
    fun summary(): String = when {
        lastQuery.isEmpty() -> if (service == Service.SQUIDIFY) "Squidify: game, anime, movie & TV soundtracks. Type in the search box (top right)."
            else "Type in the search box (top right). Links to YouTube or SoundCloud play directly."
        loading && results.isEmpty() -> "Searching ${service.label} for “$lastQuery”…"
        error != null -> "Search failed"
        fromBackup -> "${results.size} results for “$lastQuery” (via yt-dlp backup — NewPipe search is having problems)"
        else -> "${results.size}${if (hasMore) "+" else ""} results for “$lastQuery”"
    }
}

@Composable
fun SearchScreen(state: SearchState, actions: VideoActions) {
    when {
        state.error != null -> CenterMessage("Error: ${state.error}")
        state.results.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        state.results.isEmpty() && state.lastQuery.isNotEmpty() -> CenterMessage("No results.")
        state.results.isEmpty() -> CenterMessage(
            "Search YouTube or SoundCloud with the box at the top right.\n" +
                "Switch between videos, channels and playlists above. Right-click anything for more options.",
        )
        else -> ResultGrid(state.results, actions, state.hasMore, state.loading, onLoadMore = { state.loadMore() })
    }
}
