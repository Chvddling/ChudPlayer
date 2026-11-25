package newpipe.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.desktop.data.Playlist
import newpipe.desktop.data.PlaylistStore

private fun Playlist.summary(): String =
    listOf("${items.size} items", formatTotal(items.sumOf { it.durationSec.coerceAtLeast(0) })).filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
fun PlaylistsScreen(
    store: PlaylistStore,
    actions: VideoActions,
    openId: String?,
    onOpen: (String?) -> Unit,
) {
    val open = openId?.let { store.get(it) }
    if (open == null) PlaylistOverview(store, actions, onOpen) else PlaylistDetail(open, store, actions, onBack = { onOpen(null) })
}

private fun playlistMenu(p: Playlist, actions: VideoActions): List<ContextMenuItem> = listOf(
    ContextMenuItem("Play") { actions.playAll(p.items) },
    ContextMenuItem("Shuffle") { actions.shuffle(p.items) },
    ContextMenuItem("Play in background (audio only)") { actions.backgroundAll(p.items) },
    ContextMenuItem("Add all to queue") { p.items.forEach { actions.enqueue(it) } },
    ContextMenuItem("Rename…") { actions.renamePlaylist(p.id, p.name) },
    ContextMenuItem("Delete…") { actions.deletePlaylist(p.id, p.name) },
)

@Composable
private fun PlaylistOverview(store: PlaylistStore, actions: VideoActions, onOpen: (String?) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Playlists", "${store.playlists.size} playlists") {
            GlossButton("New playlist", { actions.newPlaylist() }, brush = Gloss.blue, textColor = Color.White, bold = true)
            GlossButton("Save queue as playlist", { actions.saveQueueAsPlaylist() }, enabled = actions.player.queue.isNotEmpty())
        }
        if (store.playlists.isEmpty()) {
            CenterMessage("No playlists yet.\nRight-click any video or song → Add to playlist, or use “New playlist”.")
        } else OverviewList(store, actions, onOpen)
    }
}

@Composable
private fun OverviewList(store: PlaylistStore, actions: VideoActions, onOpen: (String?) -> Unit) {
    run {
        val state = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                itemsIndexed(store.playlists, key = { _, p -> p.id }) { i, p ->
                    ContextMenuArea(items = { playlistMenu(p, actions) }) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (i % 2 == 1) Aero.rowB else Aero.rowA)
                                .clickable { onOpen(p.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Cover(p)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Aero.textBright, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.summary(), color = Aero.textDim, fontSize = 11.sp)
                            }
                            val has = p.items.isNotEmpty()
                            GlossButton("Play", { actions.playAll(p.items) }, enabled = has, brush = Gloss.blue, textColor = Color.White, bold = true)
                            GlossButton("Shuffle", { actions.shuffle(p.items) }, enabled = has)
                            GlossButton("Background", { actions.backgroundAll(p.items) }, enabled = has)
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun Cover(p: Playlist) {
    val shape = RoundedCornerShape(3.dp)
    Box(Modifier.width(112.dp).height(63.dp).clip(shape).border(1.dp, Color.Black, shape)) {
        NetImage(p.items.firstOrNull()?.thumbnail, Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().height(31.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent))))
    }
}

@Composable
private fun PlaylistDetail(p: Playlist, store: PlaylistStore, actions: VideoActions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(p.name, p.summary()) {
            val has = p.items.isNotEmpty()
            GlossButton("◀ All playlists", onBack)
            GlossButton("Play", { actions.playAll(p.items) }, enabled = has, brush = Gloss.blue, textColor = Color.White, bold = true)
            GlossButton("Shuffle", { actions.shuffle(p.items) }, enabled = has)
            GlossButton("Background", { actions.backgroundAll(p.items) }, enabled = has)
            GlossButton("Rename", { actions.renamePlaylist(p.id, p.name) })
            GlossButton("Delete", { actions.deletePlaylist(p.id, p.name) })
        }
        if (p.items.isEmpty()) {
            CenterMessage("This playlist is empty.\nRight-click any video or song → Add to “${p.name}”.")
        } else DetailList(p, store, actions)
    }
}

@Composable
private fun DetailList(p: Playlist, store: PlaylistStore, actions: VideoActions) {
    Column(Modifier.fillMaxSize()) {
        TrackHeader(trailingWidth = 72.dp)
        val state = rememberLazyListState()
        val reorder = rememberReorderState(state) { from, to -> store.move(p.id, from, to) }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                itemsIndexed(p.items, key = { _, it -> it.url }) { i, item ->
                    TrackRow(
                        modifier = Modifier.reorderable(reorder, item.url),
                        number = "${i + 1}",
                        item = item,
                        zebra = i % 2 == 1,
                        onClick = { actions.playAll(p.items, start = i) },
                        actions = actions,
                        extraMenu = {
                            listOfNotNull(
                                ContextMenuItem("Play from here") { actions.playAll(p.items, start = i) },
                                if (i > 0) ContextMenuItem("Move up") { store.move(p.id, i, i - 1) } else null,
                                if (i < p.items.lastIndex) ContextMenuItem("Move down") { store.move(p.id, i, i + 1) } else null,
                                ContextMenuItem("Remove from “${p.name}”") { store.removeAt(p.id, i) },
                            )
                        },
                        trailing = {
                            RowIcon("▲", "Move up", enabled = i > 0) { store.move(p.id, i, i - 1) }
                            RowIcon("▼", "Move down", enabled = i < p.items.lastIndex) { store.move(p.id, i, i + 1) }
                            RowIcon("✕", "Remove from playlist") { store.removeAt(p.id, i) }
                        },
                    )
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
