package newpipe.desktop.ui

import chud.theme.title
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.desktop.data.PlaylistStore
import newpipe.desktop.player.PlayerController
import newpipe.desktop.update.AppInfo
import newpipe.desktop.update.Updater
import newpipe.desktop.update.YtDlp
import newpipe.desktop.update.openInBrowser

/** Draws a 1px line at the bottom (or top) of a bar. */
fun Modifier.edgeLine(bottom: Boolean = true, color: Color = Aero.edge) = drawBehind {
    val y = if (bottom) size.height - 1 else 0f
    drawLine(color, Offset(0f, y), Offset(size.width, y))
}

// ---------------- top bar: transport · display · search ----------------

@Composable
fun TopBar(player: PlayerController, query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Gloss.bar)
            .themed()
            .edgeLine(bottom = false, color = Color.White.copy(alpha = 0.12f))
            .edgeLine()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OrbButton({ player.previous() }, "Previous", Icons.Filled.SkipPrevious, 32.dp)
            OrbButton(
                { player.togglePause() },
                if (player.paused || player.current == null) "Play" else "Pause",
                if (player.paused || player.current == null) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                46.dp,
                primary = true,
            )
            OrbButton({ player.next() }, "Next", Icons.Filled.SkipNext, 32.dp)
            Spacer(Modifier.width(10.dp))
            // playback modes
            ModeToggle(Icons.Filled.RepeatOne, "Repeat this song", player.repeatOne) { player.changeRepeatOne(!player.repeatOne) }
            ModeToggle(Icons.Filled.Shuffle, "Shuffle (play the queue in random order)", player.shuffle) { player.changeShuffle(!player.shuffle) }
            ModeToggle(Icons.Filled.Repeat, "Loop queue (start over after the last song)", player.loopQueue) { player.changeLoopQueue(!player.loopQueue) }
            ModeToggle(Icons.Filled.AllInclusive, "Autoplay (play related songs when the queue ends)", player.autoplay) { player.changeAutoplay(!player.autoplay) }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = Aero.textDim, modifier = Modifier.size(16.dp))
            var vol by remember { mutableStateOf<Float?>(null) }
            GlossSlider(
                value = (vol ?: player.volume) / 100f,
                onValueChange = { vol = it * 100f; player.changeVolume(it * 100f, save = false) },
                onValueChangeFinished = { vol?.let { player.changeVolume(it, save = true) }; vol = null },
                modifier = Modifier.width(90.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        LcdDisplay(player, Modifier.widthIn(min = 260.dp, max = 470.dp).fillMaxWidth(0.5f))
        Spacer(Modifier.weight(1f))
        GlassSearchField(query, onQueryChange, onSubmit, Modifier.width(230.dp))
    }
}

/** The iTunes "LCD": what's playing + a seek bar, in black glass with glowing blue text. */
@Composable
fun LcdDisplay(player: PlayerController, modifier: Modifier = Modifier) {
    val theme = chud.theme.ThemeState.current
    val shape = if (theme.id == "smoked") RoundedCornerShape(6.dp) else themeShape(true)
    val glossy = theme.id == "smoked" || theme.id.startsWith("obsidian")
    Box(modifier.height(58.dp).clip(shape).background(Gloss.lcd).themed(lcd = true).border(Aero.edgeWidth, Aero.edge, shape)) {
        if (glossy) Box(Modifier.fillMaxWidth().height(28.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent))))
        val lcdFont = themeFont(theme.lcdFont)
        val glow = TextStyle(fontFamily = lcdFont, shadow = if (Aero.glow != Color.Transparent) Shadow(Aero.glow, blurRadius = 8f) else null)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val item = player.current
            if (item == null) {
                Text("ChudPlayer", color = Aero.lcdText, fontWeight = FontWeight.Bold, fontSize = 12.sp, style = glow)
                Text("Nothing playing", color = Aero.lcdDim, fontSize = 11.sp)
            } else {
                val title = player.details?.item?.title ?: item.title
                Text(theme.title(title), color = Aero.lcdText, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = glow)
                val sub = listOf(item.uploader, serviceName(item.serviceId)).filter { it.isNotBlank() }.joinToString(" — ")
                Text(sub, color = Aero.lcdDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                var drag by remember { mutableStateOf<Float?>(null) }
                val dur = player.duration
                val pos = drag?.let { it * dur } ?: player.position
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(pos), color = Aero.lcdDim, fontSize = 10.sp)
                    GlossSlider(
                        value = if (dur > 0) (pos / dur).toFloat() else 0f,
                        onValueChange = { drag = it },
                        onValueChangeFinished = { drag?.let { player.seek(it * dur) }; drag = null },
                        enabled = dur > 0 && !item.isLive,
                        trackHeight = 4.dp,
                        knob = true,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    Text(if (item.isLive) "LIVE" else "-" + formatTime((dur - pos).coerceAtLeast(0.0)), color = Aero.lcdDim, fontSize = 10.sp)
                }
            }
        }
    }
}

// ---------------- service bar ----------------

@Composable
fun ServiceBar(
    serviceId: Int,
    onService: (Int) -> Unit,
    info: String,
    kindLabels: List<String>? = null,
    kind: Int = 0,
    onKind: (Int) -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(Gloss.serviceBar).edgeLine().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val services = newpipe.desktop.data.Service.entries
        PillGroup(
            services.map { it.label },
            services.indexOfFirst { it.id == serviceId }.coerceAtLeast(0),
            { onService(services[it].id) },
            selectedBrush = { serviceBrush(services[it].id) },
            selectedText = { Color.White },
        )
        if (kindLabels != null) PillGroup(kindLabels, kind, onKind)
        Text(info, color = Aero.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------- source list (sidebar) ----------------

enum class Screen { Search, Player, Playlists, History, Channel, RemotePlaylist }

@Composable
fun SourceList(
    screen: Screen,
    openPlaylistId: String?,
    playlists: PlaylistStore,
    player: PlayerController,
    actions: VideoActions,
    go: (Screen, String?) -> Unit,
) {
    Column(
        Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(Gloss.sidebar)
            .themed()
            .drawBehind { drawLine(Aero.edge, Offset(size.width - 1, 0f), Offset(size.width - 1, size.height)) }
            .padding(vertical = 6.dp),
    ) {
        SectionLabel("LIBRARY")
        SidebarItem("Search", screen == Screen.Search, { go(Screen.Search, null) })
        SidebarItem("Now Playing", screen == Screen.Player, { go(Screen.Player, null) })
        SidebarItem("History", screen == Screen.History, { go(Screen.History, null) })

        SectionLabel("PLAYLISTS")
        SidebarItem("All playlists", screen == Screen.Playlists && openPlaylistId == null, { go(Screen.Playlists, null) })
        val state = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                items(playlists.playlists, key = { it.id }) { p ->
                    ContextMenuArea(items = {
                        listOf(
                            ContextMenuItem("Play") { actions.playAll(p.items) },
                            ContextMenuItem("Shuffle") { actions.shuffle(p.items) },
                            ContextMenuItem("Add all to queue") { p.items.forEach { actions.enqueue(it) } },
                            ContextMenuItem("Rename…") { actions.renamePlaylist(p.id, p.name) },
                            ContextMenuItem("Delete…") { actions.deletePlaylist(p.id, p.name) },
                        )
                    }) {
                        SidebarItem(
                            "${p.name}  (${p.items.size})",
                            screen == Screen.Playlists && openPlaylistId == p.id,
                            { go(Screen.Playlists, p.id) },
                        )
                    }
                }
                item { SidebarItem("+ New playlist", false, { actions.newPlaylist() }, color = Aero.link) }
                if (player.queue.isNotEmpty()) {
                    item { SidebarItem("+ Save queue as playlist", false, { actions.saveQueueAsPlaylist() }, color = Aero.link) }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }

        // artwork of what's playing, with a reflection
        val art = player.details?.item?.thumbnail ?: player.current?.thumbnail
        if (art != null) {
            Box(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth().heightIn(max = 120.dp).height(112.dp)
                    .border(1.dp, Color.Black)
                    .clickable { go(Screen.Player, null) },
            ) {
                NetImage(art, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(56.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))))
            }
            Box(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(18.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color.Transparent))),
            )
        }
    }
}

// ---------------- status bar ----------------

@Composable
fun StatusBar(
    player: PlayerController,
    onMiniPlayer: () -> Unit,
    updater: Updater,
    onInstallUpdate: () -> Unit,
    autoUpdate: Boolean,
    onToggleAutoUpdate: () -> Unit,
    onUpdateYtDlp: () -> Unit,
    onSettings: () -> Unit,
    onTray: () -> Unit,
    remoteOn: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().height(28.dp).background(Gloss.statusBar).themed().edgeLine(bottom = false).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlossButton("Settings", onSettings, round = false, height = 20.dp)
        GlossButton("To tray", onTray, round = false, height = 20.dp)
        GlossButton("Mini player", onMiniPlayer, round = false, height = 20.dp)
        val modes = listOfNotNull(
            "Repeat song".takeIf { player.repeatOne },
            "Shuffle".takeIf { player.shuffle },
            "Loop queue".takeIf { player.loopQueue },
            "Autoplay".takeIf { player.autoplay },
        ).joinToString(", ")
        val info = listOf(
            if (player.queue.isEmpty()) "Queue empty" else "${player.queue.size} in queue",
            formatTotal(player.queueSeconds),
            modes,
            if (player.queue.size > 1) "hold & drag songs to reorder" else "",
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        Text(info, color = Aero.textDim, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

        val release = updater.available
        if (release != null) {
            GlossButton(
                if (updater.busy) updater.status else "Update to v${release.version}",
                onInstallUpdate,
                brush = Gloss.orange, textColor = Color.White, bold = true, round = false, height = 20.dp,
                enabled = !updater.busy,
            )
        } else if (updater.status.isNotEmpty()) {
            Text(updater.status, color = Aero.textDim, fontSize = 11.sp, maxLines = 1)
        }

        if (remoteOn) Text("● Phone remote on", color = Color(0xFF8FE07F), fontSize = 11.sp)

        // Version label: right-click for update options
        ContextMenuArea(items = {
            listOf(
                ContextMenuItem("Settings…") { onSettings() },
                ContextMenuItem("Check for updates now") { updater.check(manual = true) },
                ContextMenuItem(if (autoUpdate) "Turn off automatic update checks" else "Turn on automatic update checks") { onToggleAutoUpdate() },
                ContextMenuItem("Update yt-dlp backup now") { onUpdateYtDlp() },
                ContextMenuItem("Open ChudPlayer on GitHub") { openInBrowser("https://github.com/${AppInfo.REPO}") },
            )
        }) {
            Text(
                "ChudPlayer v${AppInfo.version} · NewPipeExtractor ${AppInfo.extractor}" +
                    (if (YtDlp.status.isNotEmpty()) " · ${YtDlp.status}" else ""),
                color = Aero.label,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.clickable { updater.check(manual = true) },
            )
        }
    }
}
