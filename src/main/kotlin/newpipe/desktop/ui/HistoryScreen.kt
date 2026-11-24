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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.desktop.data.HistoryStore

@Composable
fun HistoryScreen(history: HistoryStore, actions: VideoActions) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("History", "${history.entries.size} watched") {
            val items = history.entries.map { it.item }
            GlossButton("Play all", { actions.playAll(items) }, enabled = items.isNotEmpty(), brush = Gloss.blue, textColor = Color.White, bold = true)
            GlossButton("Save as playlist", { actions.newPlaylist(items) }, enabled = items.isNotEmpty())
            GlossButton("Clear all", { history.clear() }, enabled = items.isNotEmpty())
        }
        if (history.entries.isEmpty()) {
            CenterMessage("Videos and songs you play will show up here.")
        } else {
            TrackHeader()
            val state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    itemsIndexed(history.entries, key = { _, e -> e.item.url }) { i, e ->
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
