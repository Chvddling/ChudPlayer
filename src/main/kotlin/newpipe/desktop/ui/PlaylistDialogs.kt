package newpipe.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import newpipe.desktop.data.PlaylistStore
import newpipe.desktop.data.VideoItem

/** Which playlist dialog is open (at most one at a time). */
sealed interface PlaylistDialog {
    /** Pick a playlist to add these videos to. */
    data class AddTo(val items: List<VideoItem>) : PlaylistDialog
    /** Name a new playlist, optionally already filled with videos. */
    data class Create(val items: List<VideoItem> = emptyList(), val name: String = "") : PlaylistDialog
    data class Rename(val id: String, val current: String) : PlaylistDialog
    data class Delete(val id: String, val name: String) : PlaylistDialog
}

/**
 * Dialogs are real (small) windows, not in-window popups,
 * because in-window popups would be hidden behind the native video surface.
 */
@Composable
fun PlaylistDialogHost(
    dialog: PlaylistDialog?,
    store: PlaylistStore,
    onDismiss: () -> Unit,
    onSwitch: (PlaylistDialog) -> Unit,
    onDeleted: (String) -> Unit,
) {
    when (dialog) {
        null -> Unit
        is PlaylistDialog.AddTo -> DialogFrame("Add to playlist", DpSize(420.dp, 480.dp), onDismiss) {
            AddToContent(dialog, store, onDismiss, onSwitch)
        }
        is PlaylistDialog.Create -> DialogFrame("New playlist", DpSize(420.dp, 220.dp), onDismiss) {
            NameContent(
                initial = dialog.name,
                hint = if (dialog.items.isEmpty()) "Playlist name" else "Playlist name (${dialog.items.size} videos)",
                confirmLabel = "Create",
                onCancel = onDismiss,
                onConfirm = { name -> store.create(name, dialog.items); onDismiss() },
            )
        }
        is PlaylistDialog.Rename -> DialogFrame("Rename playlist", DpSize(420.dp, 220.dp), onDismiss) {
            NameContent(
                initial = dialog.current,
                hint = "Playlist name",
                confirmLabel = "Rename",
                onCancel = onDismiss,
                onConfirm = { name -> store.rename(dialog.id, name); onDismiss() },
            )
        }
        is PlaylistDialog.Delete -> DialogFrame("Delete playlist", DpSize(420.dp, 200.dp), onDismiss) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Delete “${dialog.name}”? This can't be undone.")
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { store.delete(dialog.id); onDeleted(dialog.id); onDismiss() }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun DialogFrame(title: String, size: DpSize, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberDialogState(position = WindowPosition(Alignment.Center), size = size)
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = title,
        resizable = false,
        onKeyEvent = { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false },
    ) {
        AppTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
        }
    }
}

@Composable
private fun AddToContent(
    dialog: PlaylistDialog.AddTo,
    store: PlaylistStore,
    onDismiss: () -> Unit,
    onSwitch: (PlaylistDialog) -> Unit,
) {
    val single = dialog.items.singleOrNull()
    Column(Modifier.fillMaxSize()) {
        Text(
            single?.title ?: "${dialog.items.size} videos",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp),
        )
        Row(
            Modifier.fillMaxWidth().clickable { onSwitch(PlaylistDialog.Create(dialog.items)) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("New playlist…", color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider()
        if (store.playlists.isEmpty()) {
            CenterMessage("No playlists yet.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(store.playlists, key = { it.id }) { p ->
                    val already = single != null && p.items.any { it.url == single.url }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) { store.add(p.id, dialog.items); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NetImage(p.items.firstOrNull()?.thumbnail, Modifier.size(64.dp, 36.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.items.size} videos", style = MaterialTheme.typography.bodySmall)
                        }
                        if (already) Icon(Icons.Filled.Check, "Already in this playlist", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

@Composable
private fun NameContent(
    initial: String,
    hint: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(hint) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter) && name.isNotBlank()) {
                        onConfirm(name); true
                    } else false
                },
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        }
    }
}
