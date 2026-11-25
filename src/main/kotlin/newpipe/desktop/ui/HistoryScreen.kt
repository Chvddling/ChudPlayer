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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.desktop.data.HistoryStore

@Composable
fun HistoryScreen(history: HistoryStore, actions: VideoActions) {
    var filter by remember { mutableStateOf("") }
    val words = filter.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val shown = if (words.isEmpty()) history.entries else history.entries.filter { e ->
        val text = (e.item.title + " " + e.item.uploader).lowercase()
        words.all { it in text }
    }
    Column(Modifier.fillMaxSize()) {
        val subtitle = if (words.isEmpty()) "${history.entries.size} played" else "${shown.size} of ${history.entries.size} match “${filter.trim()}”"
        PageHeader("History", subtitle) {
            GlassSearchField(filter, { filter = it }, {}, Modifier.width(240.dp), placeholder = "Search history")
            val items = shown.map { it.item }
            GlossButton("Play all", { actions.playAll(items) }, enabled = items.isNotEmpty(), brush = Gloss.blue, textColor = Color.White, bold = true)
            GlossButton("Save as playlist", { actions.newPlaylist(items) }, enabled = items.isNotEmpty())
            GlossButton("Clear all", { history.clear() }, enabled = history.entries.isNotEmpty())
        }
        if (history.entries.isEmpty()) {
            CenterMessage("Videos and songs you play will show up here.")
        } else if (shown.isEmpty()) {
            CenterMessage("Nothing in your history matches “${filter.trim()}”.")
        } else {
            TrackHeader()
            val state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    itemsIndexed(shown, key = { _, e -> e.item.url }) { i, e ->
                        TrackRow(
                            number = "${i + 1}",
                            item = e.item,
                            zebra = i % 2 == 1,
                            onClick = { actions.play(e.item) },
                            actions = actions,
                            extraMenu = { listOf(ContextMenuItem("Remove from history") { history.remove(e.item.url) }) },
                        )
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

/** Title + subtitle on the left, glossy buttons on the right. */
@Composable
fun PageHeader(title: String, subtitle: String, buttons: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Gloss.serviceBar).edgeLine().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(title, color = Aero.textBright, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Aero.textDim, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        buttons()
    }
}
