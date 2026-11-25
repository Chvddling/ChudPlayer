package newpipe.desktop.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import newpipe.desktop.data.LocalMusic
import newpipe.desktop.data.SettingsStore
import newpipe.desktop.data.VideoItem
import java.io.File
import javax.swing.JFileChooser

/** Opens Windows' folder picker. Returns null if cancelled. */
fun chooseFolder(start: File?, title: String = "Choose your music folder"): File? {
    val chooser = JFileChooser(start)
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = title
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun File.displayName(): String = name.ifEmpty { absolutePath }

/** Folder tree + the songs of the selected folder. Remembers the last folder. */
class LocalState(private val settings: SettingsStore, private val scope: CoroutineScope) {
    data class Node(val dir: File, val depth: Int)

    var root by mutableStateOf(resolveRoot()); private set
    var selected by mutableStateOf<File?>(null); private set
    var tracks by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set

    private val children = mutableStateMapOf<String, List<File>>()
    private val expanded = mutableStateMapOf<String, Boolean>()
    private var job: Job? = null
    private val listing = HashSet<String>()   // folders being listed right now

    init {
        root?.let { expanded[it.absolutePath] = true; loadChildren(it) }
        val last = settings.value.lastLocalFolder.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        (last ?: root)?.let { open(it, remember = false) }
    }

    private fun resolveRoot(): File? =
        settings.value.musicFolder.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
            ?: LocalMusic.defaultMusicFolder()

    /** Sets the main music folder (from Settings or the "Change…" button). Empty = Windows Music folder. */
    fun changeMusicFolder(dir: File?) {
        settings.update { it.copy(musicFolder = dir?.absolutePath.orEmpty()) }
        root = resolveRoot()
        children.clear()
        listing.clear()
        expanded.clear()
        root?.let { expanded[it.absolutePath] = true; loadChildren(it); open(it) }
    }

    fun childrenOf(dir: File): List<File>? = children[dir.absolutePath]
    fun isExpanded(dir: File) = expanded[dir.absolutePath] == true

    fun loadChildren(dir: File) {
        val key = dir.absolutePath
        if (children.containsKey(key) || !listing.add(key)) return
        scope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { LocalMusic.subfolders(dir) }.getOrDefault(emptyList()) }
            children[key] = list
            listing.remove(key)
        }
    }

    fun toggle(dir: File) {
        val now = !isExpanded(dir)
        expanded[dir.absolutePath] = now
        if (now) loadChildren(dir)
    }

    private fun expandParents(dir: File) {
        val r = root ?: return
        var d: File? = dir.parentFile
        while (d != null) {
            expanded[d.absolutePath] = true
            loadChildren(d)
            if (d.absolutePath == r.absolutePath) break
            d = d.parentFile
        }
    }

    /** Shows the songs in [dir] (names first, then tags and lengths as they are read). */
    fun open(dir: File, remember: Boolean = true) {
        selected = dir
        if (remember) settings.update { it.copy(lastLocalFolder = dir.absolutePath) }
        expandParents(dir)
        loadChildren(dir)
        job?.cancel()
        loading = true
        tracks = emptyList()
        job = scope.launch {
            try {
                val files = withContext(Dispatchers.IO) { LocalMusic.audioFiles(dir) }
                tracks = files.map { LocalMusic.item(it, null) }
                withContext(Dispatchers.IO) {
                    val out = tracks.toMutableList()
                    files.forEachIndexed { i, f ->
                        out[i] = LocalMusic.item(f, LocalMusic.tags(f))
                        if (i % 12 == 11 || i == files.lastIndex) tracks = out.toList()
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    /** The tree rows currently visible (expanded folders only). */
    fun visibleNodes(): List<Node> {
        val r = root ?: return emptyList()
        val out = mutableListOf<Node>()
        fun walk(d: File, depth: Int) {
            out += Node(d, depth)
            if (isExpanded(d)) childrenOf(d)?.forEach { walk(it, depth + 1) }
        }
        walk(r, 0)
        return out
    }
}

@Composable
fun LocalScreen(state: LocalState, actions: VideoActions, onChooseFolder: () -> Unit) {
    val root = state.root
    if (root == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Choose the folder where your music is.", color = Aero.text)
            Spacer(Modifier.height(10.dp))
            GlossButton("Choose music folder…", onChooseFolder, brush = Gloss.green, textColor = Color.White, bold = true)
        }
        return
    }
    Row(Modifier.fillMaxSize()) {
        FolderTree(state, onChooseFolder, Modifier.width(280.dp).fillMaxHeight())
        FolderTracks(state, actions, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun FolderTree(state: LocalState, onChooseFolder: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .background(Gloss.sidebar)
            .themed()
            .drawBehind { drawLine(Aero.edge, Offset(size.width - 1, 0f), Offset(size.width - 1, size.height)) },
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("FOLDERS", Modifier.weight(1f))
            GlossButton("Change…", onChooseFolder, height = 20.dp, round = false, modifier = Modifier.padding(top = 8.dp))
        }
        val list = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(state.visibleNodes(), key = { it.dir.absolutePath }) { node -> FolderRow(node, state) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun FolderRow(node: LocalState.Node, state: LocalState) {
    val dir = node.dir
    LaunchedEffect(dir.absolutePath) { state.loadChildren(dir) }   // to know whether it has subfolders
    val kids = state.childrenOf(dir)
    val expanded = state.isExpanded(dir)
    val selected = state.selected?.absolutePath == dir.absolutePath
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Gloss.selection).border(1.dp, Aero.selectionBorder, shape) else Modifier)
            .clickable { state.open(dir) }
            .padding(start = (4 + node.depth * 16).dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // expand arrow (only if the folder has subfolders)
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (kids == null || kids.isNotEmpty()) {
                Text(
                    if (expanded) "▾" else "▸",
                    color = Aero.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { state.toggle(dir) },
                )
            }
        }
        Icon(
            if (expanded || selected) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            null,
            tint = Color(0xFFE8C26A),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            dir.displayName(),
            color = if (selected) Aero.onSelection else Aero.text,
            fontSize = 12.sp,
            fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderTracks(state: LocalState, actions: VideoActions, modifier: Modifier) {
    val dir = state.selected
    val tracks = state.tracks
    Column(modifier) {
        val total = formatTotal(tracks.sumOf { it.durationSec.coerceAtLeast(0) })
        val subtitle = listOf(
            if (state.loading && tracks.isEmpty()) "Reading folder…" else "${tracks.size} songs",
            total,
            dir?.absolutePath.orEmpty(),
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        PageHeader(dir?.displayName() ?: "My Music", subtitle) {
            val has = tracks.isNotEmpty()
            GlossButton("Play folder", { actions.playAll(tracks) }, enabled = has, brush = Gloss.green, textColor = Color.White, bold = true)
            GlossButton("Shuffle", { actions.shuffle(tracks) }, enabled = has)
            GlossButton("Add to queue", { tracks.forEach { actions.enqueue(it) } }, enabled = has)
            GlossButton("Save as playlist", { actions.newPlaylist(tracks, dir?.displayName().orEmpty()) }, enabled = has)
        }
        when {
            tracks.isEmpty() && state.loading -> CenterMessage("Reading folder…")
            tracks.isEmpty() -> CenterMessage("No music files in this folder.\nPick a folder on the left.")
            else -> {
                TrackHeader()
                val list = rememberLazyListState()
                val playingUrl = actions.player.current?.url
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = list) {
                        itemsIndexed(tracks, key = { _, t -> t.url }) { i, t ->
                            TrackRow(
                                number = if (t.url == playingUrl) "▶" else "${i + 1}",
                                item = t,
                                zebra = i % 2 == 1,
                                selected = t.url == playingUrl,
                                onClick = { actions.play(t) },
                                actions = actions,
                                extraMenu = {
                                    listOf(androidx.compose.foundation.ContextMenuItem("Play this folder from here") { actions.playAll(tracks, start = i) })
                                },
                            )
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}
