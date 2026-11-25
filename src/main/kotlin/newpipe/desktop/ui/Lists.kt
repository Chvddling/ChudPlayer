package newpipe.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import newpipe.desktop.data.ChannelRef
import newpipe.desktop.data.PlaylistRef
import newpipe.desktop.data.PlaylistStore
import newpipe.desktop.data.SearchItem
import newpipe.desktop.data.VideoResult
import newpipe.desktop.data.VideoItem
import newpipe.desktop.player.PlayerController

/**
 * Everything you can do with a video/song, shared by every list, grid and right-click menu.
 */
class VideoActions(
    val player: PlayerController,
    val playlists: PlaylistStore,
    private val showDialog: (PlaylistDialog) -> Unit,
    private val openPlayer: () -> Unit,
    private val openChannelPage: (String) -> Unit,
    private val openPlaylistPage: (String) -> Unit,
) {
    /** A click on a song: add it to the queue and play it now (the rest of the queue stays). */
    fun play(item: VideoItem) { player.addAndPlay(item); openPlayer() }
    fun openChannel(url: String?) { if (!url.isNullOrBlank()) openChannelPage(url) }
    fun openPlaylist(url: String) = openPlaylistPage(url)
    fun playAll(items: List<VideoItem>, start: Int = 0) { player.playCollection(items, start); openPlayer() }
    fun shuffle(items: List<VideoItem>) { player.playCollection(items, shuffle = true); openPlayer() }
    fun background(item: VideoItem) = player.playInBackground(item)
    fun backgroundAll(items: List<VideoItem>) = player.playCollection(items, audioOnly = true)
    fun playNext(item: VideoItem) = player.playNext(item)
    fun enqueue(item: VideoItem) = player.enqueue(item)
    fun addToPlaylist(items: List<VideoItem>) { if (items.isNotEmpty()) showDialog(PlaylistDialog.AddTo(items)) }
    fun newPlaylist(items: List<VideoItem> = emptyList(), name: String = "") = showDialog(PlaylistDialog.Create(items, name))
    fun saveQueueAsPlaylist() { if (player.queue.isNotEmpty()) showDialog(PlaylistDialog.Create(player.queue)) }
    fun renamePlaylist(id: String, name: String) = showDialog(PlaylistDialog.Rename(id, name))
    fun deletePlaylist(id: String, name: String) = showDialog(PlaylistDialog.Delete(id, name))

    /** The right-click menu for one video/song. [extra] items go after the standard ones. */
    fun menuFor(item: VideoItem, extra: List<ContextMenuItem> = emptyList()): List<ContextMenuItem> {
        val menu = mutableListOf(
            ContextMenuItem("Play now") { play(item) },
            ContextMenuItem("Play next") { playNext(item) },
            ContextMenuItem("Add to queue") { enqueue(item) },
            ContextMenuItem("Play in background (audio only)") { background(item) },
            ContextMenuItem("Add to playlist…") { addToPlaylist(listOf(item)) },
        )
        if (!item.uploaderUrl.isNullOrBlank()) {
            menu += ContextMenuItem("Go to channel${if (item.uploader.isNotBlank()) ": " + item.uploader else ""}") { openChannel(item.uploaderUrl) }
        }
        // Quick "add to" entries for the first few playlists that don't have it yet
        playlists.playlists
            .filter { p -> p.items.none { it.url == item.url } }
            .take(6)
            .forEach { p -> menu += ContextMenuItem("    Add to “${p.name}”") { playlists.add(p.id, listOf(item)) } }
        menu += extra
        if (player.queue.isNotEmpty()) {
            menu += ContextMenuItem("Save current queue as playlist… (${player.queue.size})") { saveQueueAsPlaylist() }
        }
        return menu
    }
}

/** Wraps content so right-clicking it opens the video menu. */
@Composable
fun VideoMenuArea(
    actions: VideoActions,
    item: VideoItem,
    extra: () -> List<ContextMenuItem> = { emptyList() },
    content: @Composable () -> Unit,
) {
    ContextMenuArea(items = { actions.menuFor(item, extra()) }, content = content)
}

// ---------------- iTunes-style list view ----------------

private val colNum = 36.dp
private val colTime = 64.dp
private val colSource = 92.dp

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, color = Aero.textDim, fontSize = 11.sp, maxLines = 1)
}

@Composable
fun TrackHeader(trailingWidth: Dp = 0.dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Gloss.header)
            .drawBehind { drawLine(Color.Black, Offset(0f, size.height - 1), Offset(size.width, size.height - 1)) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("#", Modifier.width(colNum))
        HeaderCell("Title", Modifier.weight(3f))
        HeaderCell("Channel / Artist", Modifier.weight(2f))
        HeaderCell("Time", Modifier.width(colTime))
        HeaderCell("Source", Modifier.width(colSource))
        if (trailingWidth > 0.dp) Spacer(Modifier.width(trailingWidth))
    }
}

@Composable
fun TrackRow(
    number: String,
    item: VideoItem,
    zebra: Boolean,
    onClick: () -> Unit,
    actions: VideoActions,
    selected: Boolean = false,
    extraMenu: () -> List<ContextMenuItem> = { emptyList() },
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    // [modifier] goes on the outermost box so drag & drop can lift the whole row
    Box(modifier.fillMaxWidth()) { VideoMenuArea(actions, item, extraMenu) {
        val color = if (selected) Color.White else Aero.text
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(if (selected) Gloss.rowSelected else SolidColor(if (zebra) Aero.rowB else Aero.rowA))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(number, Modifier.width(colNum), color = color, fontSize = 12.sp)
            Text(
                item.title, Modifier.weight(3f).padding(end = 10.dp),
                color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Box(Modifier.weight(2f).padding(end = 10.dp)) {
                ChannelLink(item, actions, if (selected) Color.White else Aero.textDim)
            }
            Text(item.lengthText(), Modifier.width(colTime), color = if (selected) Color.White else Aero.textDim, fontSize = 12.sp)
            Box(Modifier.width(colSource)) { ServiceBadge(item.serviceId) }
            trailing?.invoke(this)
        }
    }
}
}

// ---------------- grid card with reflection ----------------

@Composable
fun TrackCard(item: VideoItem, actions: VideoActions) {
    VideoMenuArea(actions, item) {
        Column(Modifier.fillMaxWidth().clickable { actions.play(item) }) {
            val shape = RoundedCornerShape(4.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(shape)
                    .border(1.dp, Color.Black, shape),
            ) {
                NetImage(item.thumbnail, Modifier.fillMaxSize())
                // glass gloss on the top half
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.5f)
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.03f)))),
                )
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { ServiceBadge(item.serviceId) }
                val len = item.lengthText()
                if (len.isNotEmpty()) {
                    Text(
                        len, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (item.isLive) Aero.ytRed else Color(0x99000000))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            // iTunes-style reflection
            Box(Modifier.fillMaxWidth().height(14.dp).clipToBounds()) {
                NetImage(
                    item.thumbnail,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer { scaleY = -1f; alpha = 0.18f },
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Aero.bgContent))))
            }
            Text(
                item.title, color = Aero.textBright, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            ChannelLink(item, actions, Aero.textDim, fontSize = 11)
            val stats = listOfNotNull(
                formatCount(item.viewCount).takeIf { it.isNotEmpty() }?.let { "$it ${playsWord(item.serviceId)}" },
                item.uploadDate?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (stats.isNotEmpty()) Text(stats, color = Aero.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                GlossButton("Play", { actions.play(item) }, brush = serviceBrush(item.serviceId), textColor = Color.White, bold = true, height = 22.dp)
                GlossButton("+ Queue", { actions.enqueue(item) }, height = 22.dp)
                GlossButton("+ Playlist", { actions.addToPlaylist(listOf(item)) }, height = 22.dp)
            }
        }
    }
}

/** Small icon-sized clickable text used for row actions (▲ ▼ ✕). */
@Composable
fun RowIcon(symbol: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        symbol,
        color = Aero.textDim,
        fontSize = 12.sp,
        modifier = Modifier
            .width(24.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(vertical = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}


/** The uploader's name; clicking it opens their channel / artist page. */
@Composable
fun ChannelLink(item: VideoItem, actions: VideoActions, color: Color, fontSize: Int = 12) {
    val canOpen = !item.uploaderUrl.isNullOrBlank()
    Text(
        item.uploader,
        color = color,
        fontSize = fontSize.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (canOpen) Modifier.clickable(onClickLabel = "Open channel") { actions.openChannel(item.uploaderUrl) } else Modifier,
    )
}

// ---------------- channel & online playlist cards ----------------

@Composable
fun ChannelCard(ch: ChannelRef, actions: VideoActions) {
    ContextMenuArea(items = { listOf(ContextMenuItem("Open ${ch.name}") { actions.openChannel(ch.url) }) }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { actions.openChannel(ch.url) }.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(120.dp).clip(CircleShape).border(1.dp, Color.Black, CircleShape)) {
                NetImage(ch.avatar, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(60.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent))))
            }
            Spacer(Modifier.height(8.dp))
            Text(ch.name, color = Aero.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val stats = listOfNotNull(
                formatCount(ch.subscribers).takeIf { it.isNotEmpty() }?.let { if (ch.serviceId == 0) "$it subscribers" else "$it followers" },
                formatCount(ch.streamCount).takeIf { it.isNotEmpty() }?.let { when (ch.serviceId) { 0 -> "$it videos"; 100 -> "$it albums"; else -> "$it tracks" } },
            ).joinToString(" · ")
            if (stats.isNotEmpty()) Text(stats, color = Aero.textDim, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ServiceBadge(ch.serviceId)
                GlossButton("Open", { actions.openChannel(ch.url) }, brush = Gloss.blue, textColor = Color.White, bold = true, height = 22.dp)
            }
        }
    }
}

@Composable
fun PlaylistCard(pl: PlaylistRef, actions: VideoActions) {
    ContextMenuArea(items = {
        listOfNotNull(
            ContextMenuItem("Open playlist") { actions.openPlaylist(pl.url) },
            pl.uploaderUrl?.takeIf { it.isNotBlank() }?.let { ContextMenuItem("Go to channel: ${pl.uploader}") { actions.openChannel(it) } },
        )
    }) {
        Column(Modifier.fillMaxWidth().clickable { actions.openPlaylist(pl.url) }) {
            val shape = RoundedCornerShape(4.dp)
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).border(1.dp, Color.Black, shape)) {
                NetImage(pl.thumbnail, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.03f)))))
                // stacked "playlist" strip on the right
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(70.dp).background(Color(0xCC0D1117)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(if (pl.count >= 0) "${pl.count}" else "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("≡ items", color = Aero.textDim, fontSize = 10.sp)
                }
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { ServiceBadge(pl.serviceId) }
            }
            Spacer(Modifier.height(16.dp))
            Text(pl.name, color = Aero.textBright, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (pl.uploader.isNotBlank()) {
                Text(
                    pl.uploader, color = Aero.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = if (!pl.uploaderUrl.isNullOrBlank()) Modifier.clickable { actions.openChannel(pl.uploaderUrl) } else Modifier,
                )
            }
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlossButton("Open", { actions.openPlaylist(pl.url) }, brush = Gloss.blue, textColor = Color.White, bold = true, height = 22.dp)
            }
        }
    }
}

/** Grid of mixed results (videos, channels, playlists) with infinite scroll. */
@Composable
fun ResultGrid(
    results: List<SearchItem>,
    actions: VideoActions,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = rememberLazyGridState()
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            state = grid,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { it.url }) { r ->
                when (r) {
                    is VideoResult -> TrackCard(r.item, actions)
                    is ChannelRef -> ChannelCard(r, actions)
                    is PlaylistRef -> PlaylistCard(r, actions)
                }
            }
            if (hasMore || loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (hasMore) LaunchedEffect(results.size) { onLoadMore() }
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        if (loading) CircularProgressIndicator(Modifier.size(28.dp)) else Text("Loading more…", color = Aero.textDim)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(grid), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
