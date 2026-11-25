package chud.remote.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import chud.remote.DetailPage
import chud.remote.Entry
import chud.remote.Item
import chud.remote.RemoteViewModel
import chud.remote.Tab
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ======================= root =======================

@Composable
fun RemoteApp(vm: RemoteViewModel) {
    Box(Modifier.fillMaxSize().background(Gloss.page)) {
        if (vm.connection == null) {
            PairScreen(vm)
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopBar(vm)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (vm.tab) {
                        Tab.PLAYING -> PlayingScreen(vm)
                        Tab.QUEUE -> QueueScreen(vm)
                        Tab.SEARCH -> SearchScreen(vm)
                        Tab.LIBRARY -> LibraryScreen(vm)
                    }
                    vm.detail?.let { DetailScreen(vm, it) }
                }
                if (vm.tab != Tab.PLAYING) MiniPlayer(vm)
                BottomTabs(vm)
            }
            vm.sheetItem?.let { ActionSheet(vm, it) }
            vm.pickPlaylistFor?.let { PlaylistPicker(vm, it) }
            if (showSettings.value) SettingsSheet(vm) { showSettings.value = false }
        }
        // short messages ("Added to queue")
        vm.message?.let { msg ->
            LaunchedEffect(msg) { delay(2200); if (vm.message == msg) vm.message = null }
            Box(Modifier.fillMaxSize().padding(bottom = 150.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    msg, color = Aero.bright, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xEE1F2731))
                        .border(1.dp, Color.Black, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
    BackHandler(enabled = vm.sheetItem != null || vm.pickPlaylistFor != null || vm.detail != null || showSettings.value) {
        when {
            showSettings.value -> showSettings.value = false
            vm.pickPlaylistFor != null -> vm.pickPlaylistFor = null
            vm.sheetItem != null -> vm.sheetItem = null
            else -> vm.closeDetail()
        }
    }
}

private val showSettings = mutableStateOf(false)

@Composable
private fun TopBar(vm: RemoteViewModel) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Gloss.bar).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when (vm.tab) { Tab.PLAYING -> "ChudPlayer Remote"; Tab.QUEUE -> "Queue"; Tab.SEARCH -> "Search"; Tab.LIBRARY -> "Library" },
                color = Aero.bright, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (vm.connected) Aero.green else Color(0xFFE0261C)))
                Spacer(Modifier.width(5.dp))
                val name = vm.state.pc.ifEmpty { vm.connection?.name.orEmpty() }.ifEmpty { vm.connection?.host.orEmpty() }
                Text(
                    if (vm.connected) "Connected to $name" else "Connecting to $name…",
                    color = Aero.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (vm.tab == Tab.QUEUE) {
            GlossButton("Save as playlist", { vm.pickPlaylistFor = emptyList() }, height = 30.dp)
            Spacer(Modifier.width(8.dp))
        }
        Orb(Icons.Filled.Settings, "Settings", 38.dp) { showSettings.value = true }
    }
}

@Composable
private fun BottomTabs(vm: RemoteViewModel) {
    Row(Modifier.fillMaxWidth().height(62.dp).background(Gloss.tabs)) {
        listOf(
            Triple(Tab.PLAYING, "Playing", Icons.Filled.PlayArrow),
            Triple(Tab.QUEUE, "Queue", Icons.AutoMirrored.Filled.QueueMusic),
            Triple(Tab.SEARCH, "Search", Icons.Filled.Search),
            Triple(Tab.LIBRARY, "Library", Icons.Filled.LibraryMusic),
        ).forEach { (t, label, icon) ->
            val sel = vm.tab == t
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (sel) Modifier.background(Gloss.tabSelected).border(1.dp, Color(0xFF3D7FBF)) else Modifier)
                    .clickable { vm.tab = t; vm.closeAllDetails() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = if (sel) Color.White else Aero.dim, modifier = Modifier.size(22.dp))
                Text(label, color = if (sel) Color.White else Aero.dim, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// ======================= pairing =======================

@Composable
private fun PairScreen(vm: RemoteViewModel) {
    val scan = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { vm.pairFromQr(it) } }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Orb(Icons.Filled.PlayArrow, "ChudPlayer", 96.dp, primary = true) {}
        Text("ChudPlayer Remote", color = Aero.bright, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "On your PC open ChudPlayer → Settings → Phone remote, turn it on, then scan the QR code.",
            color = Aero.dim, fontSize = 14.sp,
        )
        GlossButton(
            if (vm.pairing) "Connecting…" else "Scan QR code",
            {
                scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the code in ChudPlayer's Settings").setBeepEnabled(false).setOrientationLocked(false))
            },
            brush = Gloss.blue, textColor = Color.White, bold = true, height = 48.dp, enabled = !vm.pairing,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("— or type it in —", color = Aero.label, fontSize = 12.sp)
        OutlinedTextField(host, { host = it }, label = { Text("PC address (e.g. 192.168.1.20)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(code, { code = it }, label = { Text("Pairing code") }, singleLine = true, modifier = Modifier.weight(2f))
        }
        GlossButton("Connect", { vm.pairManual(host, port, code) }, enabled = host.isNotBlank() && code.isNotBlank() && !vm.pairing, modifier = Modifier.fillMaxWidth(), height = 44.dp)
        vm.connectError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }
    }
}

@Composable
private fun SettingsSheet(vm: RemoteViewModel, onClose: () -> Unit) {
    Sheet(onClose) {
        Text("Settings", color = Aero.bright, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        val c = vm.connection
        Text("PC: ${vm.state.pc.ifEmpty { c?.name.orEmpty() }}  (${c?.host}:${c?.port})", color = Aero.dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        if (vm.state.version.isNotEmpty()) Text("ChudPlayer v${vm.state.version}", color = Aero.dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        SheetAction(if (vm.volumeKeys) "Phone volume buttons control the PC: on" else "Phone volume buttons control the PC: off") { vm.changeVolumeKeys(!vm.volumeKeys) }
        SheetAction("Connect to a different PC (scan again)", Color(0xFFFF8A80)) { onClose(); vm.forget() }
    }
}

// ======================= now playing =======================

@Composable
private fun PlayingScreen(vm: RemoteViewModel) {
    val s = vm.state
    val cur = s.current
    if (cur == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Nothing is playing.", color = Aero.dim, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            GlossButton("Search something", { vm.tab = Tab.SEARCH }, brush = Gloss.blue, textColor = Color.White, bold = true)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f)) {
            Thumb(vm.api?.img(cur.thumbnail), Modifier.fillMaxSize().shadow(12.dp, RoundedCornerShape(6.dp)), corner = 6.dp)
            Box(Modifier.padding(8.dp)) { ServiceTag(cur.serviceId) }
        }
        Spacer(Modifier.height(16.dp))
        // LCD
        var drag by remember { mutableStateOf<Float?>(null) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Gloss.lcd).border(1.dp, Color.Black, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(cur.title, color = Aero.lcdText, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                cur.uploader, color = Aero.lcdDim, fontSize = 13.sp, maxLines = 1,
                modifier = if (!cur.uploaderUrl.isNullOrBlank()) Modifier.clickable { vm.openChannel(cur.uploaderUrl!!, cur.uploader) } else Modifier,
            )
            val dur = s.duration
            val pos = drag?.let { it * dur } ?: s.position
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(formatTime(pos), color = Aero.lcdDim, fontSize = 11.sp)
                GlossSlider(
                    value = if (dur > 0) (pos / dur).toFloat() else 0f,
                    onChange = { drag = it },
                    onFinished = { drag?.let { vm.seek(it * dur) }; drag = null },
                    enabled = dur > 0 && !cur.isLive,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(if (cur.isLive) "LIVE" else "-" + formatTime((dur - pos).coerceAtLeast(0.0)), color = Aero.lcdDim, fontSize = 11.sp)
            }
            if (s.loading) Text("Loading…", color = Aero.lcdDim, fontSize = 11.sp)
            s.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Orb(Icons.Filled.SkipPrevious, "Previous", 56.dp) { vm.prev() }
            Orb(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (s.paused) "Play" else "Pause", 80.dp, primary = true) { vm.toggle() }
            Orb(Icons.Filled.SkipNext, "Next", 56.dp) { vm.next() }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeToggle(Icons.Filled.RepeatOne, "Repeat song", s.repeatOne) { vm.setMode("repeatOne", !s.repeatOne) }
            ModeToggle(Icons.Filled.Shuffle, "Shuffle", s.shuffle) { vm.setMode("shuffle", !s.shuffle) }
            ModeToggle(Icons.Filled.Repeat, "Loop queue", s.loopQueue) { vm.setMode("loopQueue", !s.loopQueue) }
            ModeToggle(Icons.Filled.AllInclusive, "Autoplay", s.autoplay) { vm.setMode("autoplay", !s.autoplay) }
        }
        Spacer(Modifier.height(16.dp))
        var vol by remember { mutableStateOf<Float?>(null) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume", tint = Aero.dim, modifier = Modifier.size(20.dp))
            GlossSlider(
                value = (vol ?: s.volume) / 100f,
                onChange = { vol = it * 100f },
                onFinished = { vol?.let { vm.volume(it) }; vol = null },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Text("${(vol ?: s.volume).roundToInt()}%", color = Aero.dim, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        }
        if (vm.volumeKeys) Text("Phone volume buttons control the PC", color = Aero.label, fontSize = 10.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.hasVideo || s.mode == "AUDIO") {
                PillGroup(listOf("Video", "Audio"), if (s.mode == "AUDIO") 1 else 0, { vm.audioOnly(it == 1) })
            }
            GlossButton("+ Playlist", { vm.pickPlaylistFor = listOf(cur) })
            GlossButton("⋮ More", { vm.sheetItem = cur })
        }
    }
}

@Composable
private fun MiniPlayer(vm: RemoteViewModel) {
    val s = vm.state
    val cur = s.current ?: return
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(Gloss.lcd).clickable { vm.tab = Tab.PLAYING; vm.closeAllDetails() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumb(vm.api?.img(cur.thumbnail), Modifier.size(42.dp))
        Column(Modifier.weight(1f)) {
            Text(cur.title, color = Aero.lcdText, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val frac = if (s.duration > 0) (s.position / s.duration).toFloat().coerceIn(0f, 1f) else 0f
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1C2A38))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Gloss.progress))
            }
        }
        Orb(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, "Play/Pause", 44.dp, primary = true) { vm.toggle() }
        Orb(Icons.Filled.SkipNext, "Next", 38.dp) { vm.next() }
    }
}

// ======================= queue =======================

@Composable
private fun QueueScreen(vm: RemoteViewModel) {
    val q = vm.queue
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Aero.rowA).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val modes = listOfNotNull("Shuffle".takeIf { vm.state.shuffle }, "Loop".takeIf { vm.state.loopQueue }, "Autoplay".takeIf { vm.state.autoplay }).joinToString(", ")
            Text(
                listOf("${q.size} songs", formatTotal(vm.state.queueSeconds), modes).filter { it.isNotEmpty() }.joinToString(" · "),
                color = Aero.dim, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            if (q.isNotEmpty()) GlossButton("Clear", { vm.clearQueue() }, height = 28.dp)
        }
        Text("Tap to play · hold ≡ and drag to reorder · swipe left to remove", color = Aero.label, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        if (q.isEmpty()) Centered("The queue is empty.\nSearch for something and tap it.") else QueueList(vm, q)
    }
}

@Composable
private fun QueueList(vm: RemoteViewModel, q: List<Item>) {
    run {
        val density = LocalDensity.current
        val rowPx = with(density) { 64.dp.toPx() }
        var draggingUrl by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(q, key = { _, it -> it.url }) { i, item ->
                val current = i == vm.state.index
                val dragging = draggingUrl == item.url
                var swipe by remember(item.url) { mutableFloatStateOf(0f) }
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f; translationX = swipe }
                        .then(if (dragging) Modifier.shadow(10.dp) else Modifier)
                        .pointerInput(item.url) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (swipe < -size.width * 0.35f) {
                                        val idx = vm.queue.indexOfFirst { it.url == item.url }
                                        if (idx >= 0) vm.remove(idx)
                                    }
                                    swipe = 0f
                                },
                                onDragCancel = { swipe = 0f },
                            ) { ch, dx -> ch.consume(); swipe = (swipe + dx).coerceAtMost(0f) }
                        },
                ) {
                    ItemRow(
                        vm, item,
                        zebra = i % 2 == 1,
                        selected = current,
                        prefix = if (current) "▶" else null,
                        onClick = { vm.jump(i) },
                        trailing = {
                            Box(
                                Modifier.size(44.dp).pointerInput(item.url) {
                                    detectDragGestures(
                                        onDragStart = { draggingUrl = item.url; dragOffset = 0f },
                                        onDragEnd = { draggingUrl = null; dragOffset = 0f },
                                        onDragCancel = { draggingUrl = null; dragOffset = 0f },
                                    ) { ch, amount ->
                                        ch.consume()
                                        dragOffset += amount.y
                                        val idx = vm.queue.indexOfFirst { it.url == item.url }
                                        if (dragOffset > rowPx / 2 && idx < vm.queue.lastIndex) { vm.move(idx, idx + 1); dragOffset -= rowPx }
                                        else if (dragOffset < -rowPx / 2 && idx > 0) { vm.move(idx, idx - 1); dragOffset += rowPx }
                                    }
                                },
                                contentAlignment = Alignment.Center,
                            ) { Text("≡", color = Aero.dim, fontSize = 22.sp) }
                        },
                    )
                }
            }
        }
    }
}

// ======================= search =======================

@Composable
private fun SearchScreen(vm: RemoteViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(Aero.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(50)).background(Color(0xFF0B1118)).border(1.dp, Color.Black, RoundedCornerShape(50)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Aero.label, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (vm.query.isEmpty()) Text("Search", color = Aero.label, fontSize = 15.sp)
                    BasicTextField(
                        vm.query, { vm.query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Aero.bright, fontSize = 15.sp),
                        cursorBrush = SolidColor(Aero.bright),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val services = listOf(0 to "YouTube", 1 to "SoundCloud", 100 to "Squidify")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                PillGroup(services.map { it.second }, services.indexOfFirst { it.first == vm.service }, { vm.service = services[it].first; vm.search() }, selectedBrush = { serviceBrush(services[it].first) })
            }
            val kinds = listOf("ITEMS", "CHANNELS", "PLAYLISTS")
            val kindLabels = when (vm.service) {
                1 -> listOf("Tracks", "Artists", "Playlists")
                100 -> listOf("Songs", "Artists", "Albums")
                else -> listOf("Videos", "Channels", "Playlists")
            }
            PillGroup(kindLabels, kinds.indexOf(vm.kind), { vm.kind = kinds[it]; vm.search() }, height = 28.dp)
        }
        when {
            vm.results.isEmpty() && vm.searching -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            vm.results.isEmpty() -> Centered("Search YouTube, SoundCloud or Squidify.\nTap a song to play it, or ⋮ for more.")
            else -> EntryList(vm, vm.results, more = if (vm.resultsNext != null) ({ vm.searchMore() }) else null, loading = vm.searching)
        }
    }
}

// ======================= library =======================

@Composable
private fun LibraryScreen(vm: RemoteViewModel) {
    LaunchedEffect(vm.librarySection, vm.connected) {
        when (vm.librarySection) {
            0 -> vm.loadPlaylists()
            1 -> vm.loadHistory()
            else -> if (vm.localDir == null) vm.openLocal(null)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Aero.bg).padding(10.dp), horizontalArrangement = Arrangement.Center) {
            PillGroup(listOf("Playlists", "History", "My Music"), vm.librarySection, { vm.librarySection = it })
        }
        when (vm.librarySection) {
            0 -> PlaylistsList(vm)
            1 -> HistoryList(vm)
            else -> LocalBrowser(vm)
        }
    }
}

@Composable
private fun PlaylistsList(vm: RemoteViewModel) {
    if (vm.playlists.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No playlists yet.", color = Aero.dim)
            Spacer(Modifier.height(10.dp))
            GlossButton("+ New playlist", { vm.pickPlaylistFor = emptyList() }, brush = Gloss.blue, textColor = Color.White, bold = true)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End) {
                GlossButton("+ New playlist", { vm.pickPlaylistFor = emptyList() }, brush = Gloss.blue, textColor = Color.White, bold = true, height = 30.dp)
            }
        }
        itemsIndexed(vm.playlists, key = { _, p -> p.id }) { i, p ->
            Row(
                Modifier.fillMaxWidth().background(if (i % 2 == 1) Aero.rowB else Aero.rowA).clickable { vm.openPlaylist(p) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Thumb(vm.api?.img(p.thumb), Modifier.size(72.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Aero.bright, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf("${p.count} items", formatTotal(p.seconds)).filter { it.isNotEmpty() }.joinToString(" · "), color = Aero.dim, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        GlossButton("Play", { vm.playPlaylist(p.id) }, brush = Gloss.blue, textColor = Color.White, bold = true, height = 28.dp, enabled = p.count > 0)
                        GlossButton("Shuffle", { vm.playPlaylist(p.id, shuffle = true) }, height = 28.dp, enabled = p.count > 0)
                        GlossButton("Background", { vm.playPlaylist(p.id, audio = true) }, height = 28.dp, enabled = p.count > 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(vm: RemoteViewModel) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            vm.historyFilter, { vm.historyFilter = it; vm.loadHistory() },
            label = { Text("Search history") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (vm.history.isEmpty()) Centered("Nothing here yet.") else EntryList(vm, vm.history, more = null, loading = false)
    }
}

@Composable
private fun LocalBrowser(vm: RemoteViewModel) {
    val d = vm.localDir
    when {
        d == null || (vm.libraryLoading && d.folders.isEmpty() && d.tracks.isEmpty()) -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        d.error != null -> Centered(d.error)
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().background(Aero.rowA).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (d.parent != null) {
                        Orb(Icons.AutoMirrored.Filled.ArrowBack, "Up", 34.dp) { vm.openLocal(d.parent) }
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = Aero.bright, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${d.tracks.size} songs", color = Aero.dim, fontSize = 11.sp)
                    }
                    if (d.tracks.isNotEmpty()) {
                        GlossButton("Play", { vm.playAll(d.tracks) }, brush = Gloss.green, textColor = Color.White, bold = true, height = 30.dp)
                        Spacer(Modifier.width(6.dp))
                        GlossButton("Shuffle", { vm.playAll(d.tracks, shuffle = true) }, height = 30.dp)
                    }
                }
            }
            items(d.folders, key = { it.path }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openLocal(f.path) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Folder, null, tint = Color(0xFFE8C26A), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(f.name, color = Aero.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            itemsIndexed(d.tracks, key = { _, t -> t.url }) { i, t ->
                ItemRow(vm, t, zebra = i % 2 == 1, onClick = { vm.play(t) })
            }
        }
    }
}

// ======================= detail page (channel, playlist…) =======================

@Composable
private fun DetailScreen(vm: RemoteViewModel, d: DetailPage) {
    Column(Modifier.fillMaxSize().background(Gloss.page)) {
        Row(Modifier.fillMaxWidth().background(Gloss.bar).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Orb(Icons.AutoMirrored.Filled.ArrowBack, "Back", 36.dp) { vm.closeDetail() }
            Spacer(Modifier.width(10.dp))
            if (d.image != null) { Thumb(vm.api?.img(d.image), Modifier.size(44.dp), corner = 22.dp); Spacer(Modifier.width(10.dp)) }
            Text(d.title, color = Aero.bright, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        val items = d.entries.mapNotNull { it.item }
        Row(Modifier.fillMaxWidth().background(Aero.bg).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.channelUrl != null && d.hasPlaylists) {
                PillGroup(listOf("Uploads", "Playlists"), if (d.channelTab == "playlists") 1 else 0, { vm.switchChannelTab(if (it == 1) "playlists" else "videos") }, height = 28.dp)
            }
            if (items.isNotEmpty()) {
                GlossButton("Play all", { vm.playAll(items) }, brush = Gloss.blue, textColor = Color.White, bold = true, height = 28.dp)
                GlossButton("Shuffle", { vm.playAll(items, shuffle = true) }, height = 28.dp)
                if (d.playlistId == null) GlossButton("Save", { vm.pickPlaylistFor = items }, height = 28.dp)
            }
        }
        when {
            d.loading && d.entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            d.error != null -> Centered("Couldn't load: ${d.error}")
            d.entries.isEmpty() -> Centered("Nothing here.")
            else -> EntryList(vm, d.entries, more = if (d.next != null) ({ vm.detailMore() }) else null, loading = d.loading)
        }
    }
}

// ======================= rows =======================

@Composable
private fun EntryList(vm: RemoteViewModel, entries: List<Entry>, more: (() -> Unit)?, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(entries, key = { i, e -> e.url.ifEmpty { "$i" } }) { i, e ->
            val item = e.item
            if (item != null) {
                ItemRow(vm, item, zebra = i % 2 == 1, onClick = { vm.play(item) })
            } else {
                Row(
                    Modifier.fillMaxWidth().background(if (i % 2 == 1) Aero.rowB else Aero.rowA)
                        .clickable { if (e.type == "channel") vm.openChannel(e.url, e.name) else vm.openOnlinePlaylist(e.url, e.name) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Thumb(vm.api?.img(e.image), Modifier.size(52.dp), corner = if (e.type == "channel") 26.dp else 4.dp)
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = Aero.bright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(if (e.type == "channel") "Channel" else "Playlist", e.sub).filter { it.isNotEmpty() }.joinToString(" · "),
                            color = Aero.dim, fontSize = 11.sp, maxLines = 1,
                        )
                    }
                    ServiceTag(e.serviceId)
                }
            }
        }
        if (more != null || loading) {
            item {
                if (more != null) LaunchedEffect(entries.size) { more() }
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
        }
    }
}

@Composable
private fun ItemRow(
    vm: RemoteViewModel,
    item: Item,
    zebra: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
    prefix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (selected) Gloss.rowSelected else SolidColor(if (zebra) Aero.rowB else Aero.rowA))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumb(vm.api?.img(item.thumbnail), Modifier.size(52.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (prefix?.let { "$it  " } ?: "") + item.title,
                color = if (selected) Color.White else Aero.bright, fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                ServiceTag(item.serviceId)
                Spacer(Modifier.width(6.dp))
                val len = if (item.isLive) "LIVE" else if (item.durationSec > 0) formatTime(item.durationSec.toDouble()) else ""
                Text(
                    listOf(item.uploader, len).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = if (selected) Color(0xFFDBEAFF) else Aero.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
        else Box(Modifier.size(44.dp).clickable { vm.sheetItem = item }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreVert, "More options", tint = Aero.dim)
        }
    }
}

// ======================= sheets =======================

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x8C000000)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(Gloss.tabs)
                .border(1.dp, Color.Black, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(50)).background(Color(0xFF4A5563)))
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SheetAction(label: String, color: Color = Aero.bright, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 13.dp),
    )
}

@Composable
private fun ActionSheet(vm: RemoteViewModel, item: Item) {
    val close = { vm.sheetItem = null }
    Sheet(close) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(vm.api?.img(item.thumbnail), Modifier.size(46.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(item.title, color = Aero.bright, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.uploader, color = Aero.dim, fontSize = 12.sp, maxLines = 1)
            }
        }
        SheetAction("▶  Play now") { close(); vm.play(item) }
        SheetAction("⤷  Play next") { close(); vm.playNext(item) }
        SheetAction("＋  Add to queue") { close(); vm.enqueue(item) }
        SheetAction("◐  Play in background (audio only)") { close(); vm.background(item) }
        SheetAction("☰  Add to playlist…") { close(); vm.pickPlaylistFor = listOf(item) }
        val up = item.uploaderUrl
        if (!up.isNullOrBlank()) {
            SheetAction(if (up.startsWith("folder:")) "→  Open folder" else "→  Go to ${item.uploader.ifEmpty { "channel" }}", Aero.link) {
                close(); vm.openChannel(up, item.uploader)
            }
        }
    }
}

/** Pick a playlist to add to, or create a new one. Empty list = "save the queue". */
@Composable
private fun PlaylistPicker(vm: RemoteViewModel, items: List<Item>) {
    LaunchedEffect(Unit) { vm.loadPlaylists() }
    val fromQueue = items.isEmpty()
    var name by remember { mutableStateOf("") }
    Sheet({ vm.pickPlaylistFor = null }) {
        Text(if (fromQueue) "Save the queue as a playlist" else "Add to playlist", color = Aero.bright, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("New playlist name") }, singleLine = true, modifier = Modifier.weight(1f))
            GlossButton("Create", { vm.createPlaylist(name.ifBlank { "New playlist" }, items, fromQueue) }, brush = Gloss.blue, textColor = Color.White, bold = true)
        }
        if (!fromQueue) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                vm.playlists.forEach { p ->
                    SheetAction("☰  ${p.name}  (${p.count})") { vm.addToPlaylist(p.id, items) }
                }
            }
        }
    }
}

