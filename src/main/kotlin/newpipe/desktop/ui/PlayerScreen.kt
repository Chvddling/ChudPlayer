package newpipe.desktop.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.desktop.player.PlayerController

// ======================= now playing =======================

@Composable
fun PlayerScreen(player: PlayerController, actions: VideoActions) {
    if (player.current == null) {
        CenterMessage("Nothing is playing.\nSearch for something, or right-click a result → Add to queue.")
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Leave at least ~120dp for the picture and the info strip; the queue keeps at least its tab bar
        val maxQueue = (maxHeight.value - 200f).coerceAtLeast(MIN_QUEUE)
        val queueH = player.queuePanelHeight.coerceIn(MIN_QUEUE, maxQueue)
        Column(Modifier.fillMaxSize()) {
            MediaArea(player, Modifier.fillMaxWidth().weight(1f))
            InfoStrip(player, actions)
            Splitter(
                onDrag = { dy ->
                    val from = player.queuePanelHeight.coerceIn(MIN_QUEUE, maxQueue)
                    player.changeQueuePanelHeight((from - dy).coerceIn(MIN_QUEUE, maxQueue))
                },
                onDragEnd = { player.saveQueuePanelHeight() },
                onReset = { player.changeQueuePanelHeight(DEFAULT_QUEUE); player.saveQueuePanelHeight() },
            )
            QueuePanel(player, actions, Modifier.fillMaxWidth().height(queueH.dp))
        }
    }
}

private const val MIN_QUEUE = 80f
private const val DEFAULT_QUEUE = 260f

@OptIn(ExperimentalComposeUiApi::class)
/** Drag up for a bigger queue, down for a bigger picture. Double-click resets it. */
@Composable
private fun Splitter(onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onReset: () -> Unit) {
    val density = LocalDensity.current.density
    // pointerInput(Unit) keeps its first lambdas, so always call the latest ones
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val reset by rememberUpdatedState(onReset)
    var hover by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(Gloss.splitter)
            .edgeLine(bottom = false)
            .edgeLine()
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.N_RESIZE_CURSOR)))
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { dragEnd() }, onDragCancel = { dragEnd() }) { change, dy ->
                    change.consume()
                    drag(dy / density)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // glowing handle in the theme's accent colour
        val glow = Aero.glow
        Box(
            Modifier
                .width(if (hover) 64.dp else 44.dp)
                .height(4.dp)
                .drawBehind {
                    if (glow != Color.Transparent) drawRoundRect(
                        glow.copy(alpha = if (hover) 0.9f else 0.6f),
                        topLeft = androidx.compose.ui.geometry.Offset(-4f, -3f),
                        size = androidx.compose.ui.geometry.Size(size.width + 8f, size.height + 6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                    )
                }
                .clip(themeShape(true))
                .background(Gloss.progress),
        )
    }
}

/** Cover art / thumbnail, scaled with the window (drag the splitter to trade it for queue space). */
@Composable
private fun MediaArea(player: PlayerController, modifier: Modifier) {
    val theme = chud.theme.ThemeState.current
    Box(
        modifier
            .background(Brush.radialGradient(listOf(Aero.accent.copy(alpha = 0.16f), Aero.bgDeep)))
            .themed(),
        contentAlignment = Alignment.Center,
    ) {
        val art = player.details?.item?.thumbnail ?: player.current?.thumbnail
        NetImage(
            art,
            Modifier.fillMaxSize().padding(14.dp).graphicsLayer { rotationZ = theme.artTilt },
            contentScale = ContentScale.Fit,
        )
        when {
            player.error != null -> {
                Box(Modifier.fillMaxSize().background(Color(0xAA000000)))
                Text(player.error ?: "", color = Color.White, modifier = Modifier.padding(24.dp))
            }
            player.loading -> {
                Box(Modifier.fillMaxSize().background(Color(0x77000000)))
                CircularProgressIndicator()
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
            val theme = chud.theme.ThemeState.current
            Text(
                if (theme.upperTitles) item.title.uppercase() else item.title,
                color = Aero.textBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = themeFont(theme.titleFont),
            )
            val likes = d?.likeCount?.let { formatCount(it) }?.takeIf { it.isNotEmpty() }?.let { "$it likes" }
            val stats = listOfNotNull(
                formatCount(item.viewCount).takeIf { it.isNotEmpty() }?.let { "$it ${playsWord(item.serviceId)}" },
                item.uploadDate?.takeIf { it.isNotBlank() },
                likes,
            ).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLink(item, actions, Aero.link, fontSize = 11)
                if (stats.isNotEmpty()) Text("  ·  $stats", color = Aero.textDim, fontSize = 11.sp, maxLines = 1)
            }
        }
        GlossButton("+ Playlist", { actions.addToPlaylist(listOf(item)) })
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
                    textColor = if (player.autoplay) Aero.onAccent else Aero.text,
                )
            }
        }
        when (tab) {
            0 -> {
                TrackHeader(trailingWidth = 24.dp, grip = true)
                val state = rememberLazyListState()
                val reorder = rememberReorderState(state) { from, to -> player.moveInQueue(from, to) }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = state) {
                        itemsIndexed(player.queue, key = { _, q -> q.url }) { i, q ->
                            val current = i == player.index
                            TrackRow(
                                modifier = Modifier.reorderable(reorder, q.url),
                                gripModifier = Modifier.dragHandle(reorder, q.url),
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
