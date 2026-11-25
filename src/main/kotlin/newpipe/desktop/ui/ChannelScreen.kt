package newpipe.desktop.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.desktop.data.ChannelDetails
import newpipe.desktop.data.VideoResult

@Composable
private fun Spinner() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

// ======================= channel / artist page =======================

@Composable
fun ChannelScreen(state: ChannelState, actions: VideoActions, onBack: (() -> Unit)?) {
    val d = state.details
    when {
        d == null && state.error != null -> CenterMessage("Couldn't open this channel:\n${state.error}")
        d == null -> Spinner()
        else -> ChannelContent(d, state, actions, onBack)
    }
}

@Composable
private fun ChannelContent(d: ChannelDetails, state: ChannelState, actions: VideoActions, onBack: (() -> Unit)?) {
    val sc = d.ref.serviceId == 1
    val squid = d.ref.serviceId == 100
    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier.fillMaxWidth().background(Gloss.serviceBar).edgeLine().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack != null) GlossButton("◀ Back", onBack)
            Box(Modifier.size(64.dp).clip(CircleShape).border(1.dp, Color.Black, CircleShape)) {
                NetImage(d.ref.avatar, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(32.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent))))
            }
            Column(Modifier.weight(1f)) {
                Text(d.ref.name, color = Aero.textBright, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subs = formatCount(d.ref.subscribers).takeIf { it.isNotEmpty() }?.let { if (sc) "$it followers" else "$it subscribers" }
                val kindText = when { squid -> "Squidify artist · ${d.ref.streamCount} albums"; sc -> "SoundCloud artist"; else -> "YouTube channel" }
                Text(listOfNotNull(subs, kindText).joinToString(" · "), color = Aero.textDim, fontSize = 11.sp)
                if (d.ref.description.isNotBlank()) {
                    Text(d.ref.description.replace('\n', ' '), color = Aero.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val uploads = state.videos?.videos().orEmpty()
            GlossButton("Play uploads", { actions.playAll(uploads) }, enabled = uploads.isNotEmpty(), brush = Gloss.blue, textColor = Aero.onAccent, bold = true)
            GlossButton("Save uploads as playlist", { actions.newPlaylist(uploads, d.ref.name) }, enabled = uploads.isNotEmpty())
        }

        // tabs + search inside the channel
        Row(
            Modifier.fillMaxWidth().height(36.dp).background(Gloss.serviceBar).edgeLine().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val labels = buildList {
                add(
                    when {
                        squid -> "Songs (newest album first)"
                        sc -> "Tracks (newest first)"
                        else -> "Videos (newest first)"
                    },
                )
                if (d.playlists != null) add(if (d.ref.serviceId == 0) "Playlists" else if (sc) "Playlists & albums" else "Albums")
            }
            PillGroup(labels, state.tab.coerceAtMost(labels.lastIndex), { state.selectTab(it) })
            Spacer(Modifier.weight(1f))
            GlassSearchField(
                value = state.query,
                onValueChange = { state.query = it; if (it.isBlank()) state.clearSearch() },
                onSubmit = { state.deepSearch() },
                modifier = Modifier.width(280.dp),
                placeholder = "Search ${d.ref.name}",
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.query.isNotBlank()) {
                val results = state.searchResults ?: state.localMatches()
                Column(Modifier.fillMaxSize()) {
                    Text(
                        state.searchNote.ifEmpty { "${results.size} matches in the loaded uploads — press Enter to search the whole channel" },
                        color = Aero.textDim, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (results.isEmpty() && !state.searching) CenterMessage("No matches.")
                    else ResultGrid(results.map { VideoResult(it) }, actions, hasMore = false, loading = state.searching, onLoadMore = {})
                }
            } else {
                val pager = if (state.tab == 1) state.playlists else state.videos
                if (pager == null) CenterMessage("Nothing here.") else PagerGrid(pager, actions)
            }
        }
    }
}

@Composable
private fun PagerGrid(pager: Pager, actions: VideoActions) {
    when {
        pager.items.isEmpty() && pager.error != null -> CenterMessage("Couldn't load this list:\n${pager.error}")
        pager.items.isEmpty() && (pager.loading || !pager.started) -> Spinner()
        pager.items.isEmpty() -> CenterMessage("Nothing here.")
        else -> ResultGrid(pager.items, actions, pager.hasMore, pager.loading, onLoadMore = { pager.loadMore() })
    }
}

// ======================= online playlist page =======================

@Composable
fun RemotePlaylistScreen(state: RemotePlaylistState, actions: VideoActions, onBack: (() -> Unit)?) {
    val ref = state.ref
    val pager = state.pager
    if (ref == null || pager == null) {
        if (state.error != null) CenterMessage("Couldn't open this playlist:\n${state.error}") else Spinner()
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Gloss.serviceBar).edgeLine().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) GlossButton("◀ Back", onBack)
            val shape = RoundedCornerShape(3.dp)
            Box(Modifier.width(112.dp).height(63.dp).clip(shape).border(1.dp, Color.Black, shape)) {
                NetImage(ref.thumbnail, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(31.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent))))
            }
            Column(Modifier.weight(1f)) {
                Text(ref.name, color = Aero.textBright, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ref.uploader.isNotBlank()) {
                        Text(
                            ref.uploader, color = Aero.link, fontSize = 11.sp, maxLines = 1,
                            modifier = if (!ref.uploaderUrl.isNullOrBlank()) Modifier.clickable { actions.openChannel(ref.uploaderUrl) } else Modifier,
                        )
                    }
                    if (ref.count >= 0) Text("· ${ref.count} items", color = Aero.textDim, fontSize = 11.sp)
                    ServiceBadge(ref.serviceId)
                }
            }
            val ready = pager.items.isNotEmpty() && !state.busy
            GlossButton(if (state.busy) "Loading all…" else "Play", { state.withAllVideos { actions.playAll(it) } }, enabled = ready, brush = Gloss.blue, textColor = Aero.onAccent, bold = true)
            GlossButton("Shuffle", { state.withAllVideos { actions.shuffle(it) } }, enabled = ready)
            GlossButton("Save as my playlist", { state.withAllVideos { actions.newPlaylist(it, ref.name) } }, enabled = ready)
        }

        val videos = pager.videos()
        when {
            videos.isEmpty() && pager.error != null -> CenterMessage("Couldn't load this playlist:\n${pager.error}")
            videos.isEmpty() && pager.loading -> Spinner()
            videos.isEmpty() -> CenterMessage("This playlist is empty.")
            else -> {
                TrackHeader()
                val list = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = list) {
                        itemsIndexed(videos, key = { _, it -> it.url }) { i, item ->
                            TrackRow(
                                number = "${i + 1}",
                                item = item,
                                zebra = i % 2 == 1,
                                onClick = { actions.play(item) },
                                actions = actions,
                                extraMenu = {
                                    listOf(ContextMenuItem("Play this playlist from here") { state.withAllVideos { actions.playAll(it, start = i) } })
                                },
                            )
                        }
                        if (pager.hasMore) {
                            item {
                                LaunchedEffect(videos.size) { pager.loadMore() }
                                Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}
