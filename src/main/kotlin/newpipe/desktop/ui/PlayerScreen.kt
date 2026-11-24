package newpipe.desktop.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.desktop.data.PlayMode
import newpipe.desktop.player.PlayerController
import newpipe.desktop.player.VideoHost
import kotlin.math.roundToInt

// ======================= video embedding =======================

/**
 * Always composed at the window root, so the AWT canvas (and mpv's window inside it) is never destroyed.
 * It is moved over whatever [VideoSlot] is on screen, or pushed off-screen when there is none.
 */
@Composable
fun VideoLayer(host: VideoHost) {
    val density = LocalDensity.current
    val r = host.bounds
    val modifier = if (r != null && r.width > 2 && r.height > 2) {
        with(density) {
            Modifier
                .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                .size(r.width.toDp(), r.height.toDp())
        }
    } else {
        Modifier.offset { IntOffset(-200, -200) }.size(2.dp)
    }
    SwingPanel(background = Color.Black, factory = { host.canvas }, modifier = modifier)
}

/** A placeholder: wherever this sits, the video appears. */
@Composable
fun VideoSlot(host: VideoHost, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black).onGloballyPositioned { host.bounds = it.boundsInRoot() })
    DisposableEffect(Unit) { onDispose { host.bounds = null } }
}

// ======================= now playing =======================

@Composable
fun PlayerScreen(player: PlayerController, actions: VideoActions) {
    if (player.fullscreen) {
        MediaArea(player, Modifier.fillMaxSize())
        return
    }
    if (player.current == null) {
        CenterMessage("Nothing is playing.\nSearch for something, or right-click a result → Add to queue.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        MediaArea(player, Modifier.fillMaxWidth().weight(1f))
        InfoStrip(player, actions)
        QueuePanel(player, actions, Modifier.fillMaxWidth().height(260.dp))
    }
}

@Composable
private fun MediaArea(player: PlayerController, modifier: Modifier) {
    val showVideo = player.mode == PlayMode.VIDEO && player.hasVideo && !player.loading && player.error == null
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (showVideo) {
            VideoSlot(player.video, Modifier.fillMaxSize())
        } else {
            // Artwork for audio / SoundCloud, or while loading. (Nothing can be drawn on top of the video itself.)
            val art = player.details?.item?.thumbnail ?: player.current?.thumbnail
            NetImage(art, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), Color(0xCC000000)))))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    player.error != null -> Text(player.error ?: "", color = Color.White, modifier = Modifier.padding(24.dp))
                    player.loading -> CircularProgressIndicator()
                    else -> {
                        Icon(Icons.Filled.Headphones, null, tint = Aero.lcdText, modifier = Modifier.size(40.dp))
                        Text(if (player.hasVideo) "Audio only" else "Audio", color = Aero.lcdText)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStrip(player: PlayerController, actions: VideoActions) {
    val d = player.details
    val item = d?.item ?: player.current ?: return
    player.backupNotice?.let { notice ->
        Text(
            notice,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(Gloss.orange).padding(horizontal = 14.dp, vertical = 3.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth().background(Gloss.serviceBar).edgeLine(bottom = false).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Aero.textBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val likes = d?.likeCount?.let { formatCount(it) }?.takeIf { it.isNotEmpty() }?.let { "$it likes" }
            Text(listOfNotNull(item.subtitle().ifEmpty { null }, likes).joinToString(" · "), color = Aero.textDim, fontSize = 11.sp, maxLines = 1)
        }
        if (player.hasVideo || player.mode == PlayMode.AUDIO) {
            PillGroup(listOf("Video", "Audio"), if (player.mode == PlayMode.VIDEO) 0 else 1, {
                player.changeMode(if (it == 0) PlayMode.VIDEO else PlayMode.AUDIO)
            })
        }
        QualityButton(player)
        GlossButton("+ Playlist", { actions.addToPlaylist(listOf(item)) })
        GlossButton(
            if (player.fullscreen) "Exit fullscreen" else "Fullscreen",
            { player.changeFullscreen(!player.fullscreen) },
            enabled = player.mode == PlayMode.VIDEO && player.hasVideo,
        )
    }
}

@Composable
private fun QualityButton(player: PlayerController) {
    var open by remember { mutableStateOf(false) }
    val heights = player.details?.heights.orEmpty()
    Box {
        GlossButton(
            if (heights.isEmpty()) "Quality" else "${player.maxHeight}p ▾",
            { open = true },
            enabled = heights.isNotEmpty() && player.mode == PlayMode.VIDEO,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            heights.forEach { h ->
                DropdownMenuItem(
                    text = { Text("${h}p" + if (h == player.maxHeight) "  ✓" else "") },
                    onClick = { open = false; player.changeMaxHeight(h) },
                )
            }
        }
    }
}

@Composable
private fun QueuePanel(player: PlayerController, actions: VideoActions, modifier: Modifier) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier.background(Aero.rowA)) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).background(Gloss.serviceBar).edgeLine(bottom = false).edgeLine().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillGroup(listOf("Queue (${player.queue.size})", "Related", "Description"), tab, { tab = it })
            Spacer(Modifier.weight(1f))
            when (tab) {
                0 -> {
                    GlossButton("Save queue as playlist", { actions.saveQueueAsPlaylist() }, enabled = player.queue.isNotEmpty())
                    GlossButton("Clear", { player.stop() }, enabled = player.queue.isNotEmpty())
                }
                1 -> GlossButton(
                    if (player.autoplay) "Autoplay ✓" else "Autoplay",
                    { player.changeAutoplay(!player.autoplay) },
                    brush = if (player.autoplay) Gloss.blue else Gloss.button,
                    textColor = if (player.autoplay) Color.White else Aero.text,
                )
            }
        }
        when (tab) {
            0 -> {
                TrackHeader(trailingWidth = 24.dp)
                val state = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = state) {
                        itemsIndexed(player.queue) { i, q ->
                            val current = i == player.index
                            TrackRow(
                                number = if (current) "▶" else "${i + 1}",
                                item = q,
                                zebra = i % 2 == 1,
                                selected = current,
                                onClick = { player.jumpTo(i) },
                                actions = actions,
                                extraMenu = { listOf(ContextMenuItem("Remove from queue") { player.removeAt(i) }) },
                                trailing = { RowIcon("✕", "Remove from queue") { player.removeAt(i) } },
                            )
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
            1 -> {
                val related = player.details?.related.orEmpty()
                if (related.isEmpty()) {
                    CenterMessage(if (player.loading) "Loading…" else "No related items.")
                } else {
                    TrackHeader()
                    val state = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), state = state) {
                            itemsIndexed(related) { i, r ->
                                TrackRow("${i + 1}", r, zebra = i % 2 == 1, onClick = { actions.play(r) }, actions = actions)
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
            else -> {
                val text = player.details?.description.orEmpty().ifEmpty { "No description." }
                SelectionContainer {
                    Text(
                        text,
                        color = Aero.text,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    )
                }
            }
        }
    }
}
