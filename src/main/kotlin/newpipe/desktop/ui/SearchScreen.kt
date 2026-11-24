package newpipe.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import newpipe.desktop.data.SearchPage
import newpipe.desktop.data.Service
import newpipe.desktop.data.VideoItem
import newpipe.desktop.data.YouTubeRepo
import newpipe.desktop.update.YtDlp

/** Search state lives outside the screen so results survive navigating away and back. */
class SearchState(private val repo: YouTubeRepo, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    var service by mutableStateOf(Service.YOUTUBE); private set
    var lastQuery by mutableStateOf(""); private set
    var results by mutableStateOf(listOf<VideoItem>()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var hasMore by mutableStateOf(false); private set
    /** true when NewPipe's search failed and these results came from yt-dlp. */
    var fromBackup by mutableStateOf(false); private set
    private var page: SearchPage? = null
    private var job: Job? = null

    fun changeService(s: Service) {
        if (s == service) return
        service = s
        if (lastQuery.isNotEmpty()) search(lastQuery)
    }

    fun search(q: String = query.trim()) {
        if (q.isEmpty()) return
        job?.cancel()
        lastQuery = q
        loading = true; error = null; results = emptyList(); hasMore = false; page = null; fromBackup = false
        val svc = service
        job = scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { repo.search(q, svc) }
                page = p; results = p.items; hasMore = p.next != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                // NewPipeExtractor broken? Ask the yt-dlp backup instead.
                val backup = if (YtDlp.ready) runCatching { withContext(Dispatchers.IO) { YtDlp.search(q, svc) } }.getOrNull() else null
                if (!backup.isNullOrEmpty()) {
                    results = backup; fromBackup = true
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
        if (loading || p.next == null) return
        loading = true
        job = scope.launch {
            try {
                val more = withContext(Dispatchers.IO) { repo.searchMore(p) }
                page = more
                results = results + more.items.filter { n -> results.none { it.url == n.url } }
                hasMore = more.next != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace(); hasMore = false
            } finally {
                loading = false
            }
        }
    }

    /** Text for the service bar, e.g. 24 results for “outrun”. */
    fun summary(): String = when {
        lastQuery.isEmpty() -> "Type in the search box (top right). Links to YouTube or SoundCloud play directly."
        loading && results.isEmpty() -> "Searching ${service.label} for “$lastQuery”…"
        error != null -> "Search failed"
        fromBackup -> "${results.size} results for “$lastQuery” on ${service.label} (via yt-dlp backup — NewPipe search is having problems)"
        else -> "${results.size}${if (hasMore) "+" else ""} results for “$lastQuery” on ${service.label}"
    }
}

@Composable
fun SearchScreen(state: SearchState, actions: VideoActions) {
    when {
        state.error != null -> CenterMessage("Error: ${state.error}")
        state.results.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        state.results.isEmpty() && state.lastQuery.isNotEmpty() -> CenterMessage("No results.")
        state.results.isEmpty() -> CenterMessage("Search YouTube or SoundCloud with the box at the top right.\nRight-click anything for more options.")
        else -> {
            val grid = rememberLazyGridState()
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(220.dp),
                    state = grid,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.results, key = { it.url }) { item -> TrackCard(item, actions) }
                    if (state.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(state.results.size) { state.loadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                if (state.loading) CircularProgressIndicator(Modifier.size(28.dp)) else Text("Loading more…", color = Aero.textDim)
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(grid), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}
