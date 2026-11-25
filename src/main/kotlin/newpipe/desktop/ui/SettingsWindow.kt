package newpipe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import newpipe.desktop.data.LocalMusic
import newpipe.desktop.data.SettingsStore
import newpipe.desktop.update.AppInfo
import newpipe.desktop.update.Updater
import newpipe.desktop.update.YtDlp
import newpipe.desktop.update.openInBrowser
import java.io.File

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Gloss.serviceBar).edgeLine().edgeLine(bottom = false).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Aero.textBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    GlossButton(
        if (on) "$label: on" else "$label: off",
        { onChange(!on) },
        brush = if (on) Gloss.blue else Gloss.button,
        textColor = if (on) Color.White else Aero.text,
    )
}

/** ChudPlayer settings: music folder, updates, yt-dlp backup, Squidify server. */
@Composable
fun SettingsWindow(
    settings: SettingsStore,
    local: LocalState,
    updater: Updater,
    autoUpdate: Boolean,
    onToggleAutoUpdate: () -> Unit,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val state = rememberDialogState(position = WindowPosition(Alignment.Center), size = DpSize(620.dp, 640.dp))
    DialogWindow(
        onCloseRequest = onClose,
        state = state,
        title = "ChudPlayer – Settings",
        onKeyEvent = { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onClose(); true } else false },
    ) {
        AppTheme {
            Column(
                Modifier.fillMaxSize().background(Aero.bgContent).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---------- My Music ----------
                Section("My Music") {
                    val current = local.root?.absolutePath ?: "(not set)"
                    Text("Main music folder: $current", color = Aero.text, fontSize = 12.sp)
                    Text(
                        "This folder is the top of the folder tree under “My Music”. The folder you opened last is remembered.",
                        color = Aero.textDim, fontSize = 11.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlossButton("Choose folder…", {
                            chooseFolder(local.root)?.let { local.changeMusicFolder(it) }
                        }, brush = Gloss.green, textColor = Color.White, bold = true)
                        GlossButton("Use Windows “Music” folder", { local.changeMusicFolder(null) }, enabled = LocalMusic.defaultMusicFolder() != null)
                    }
                }

                // ---------- Updates ----------
                Section("Updates") {
                    Text("ChudPlayer v${AppInfo.version} · NewPipeExtractor ${AppInfo.extractor}", color = Aero.text, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Toggle("Check for updates at startup", autoUpdate) { onToggleAutoUpdate() }
                        GlossButton("Check now", { updater.check(manual = true) }, enabled = !updater.busy)
                    }
                    if (updater.status.isNotEmpty()) Text(updater.status, color = Aero.textDim, fontSize = 11.sp)
                }

                // ---------- yt-dlp ----------
                Section("yt-dlp backup") {
                    Text(
                        "Used automatically when NewPipe can't load something from YouTube/SoundCloud. Kept up to date daily.",
                        color = Aero.textDim, fontSize = 11.sp,
                    )
                    var backup by remember { mutableStateOf(settings.value.ytDlpBackup) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Toggle("Keep yt-dlp up to date", backup) {
                            backup = it
                            settings.update { s -> s.copy(ytDlpBackup = it) }
                        }
                        GlossButton("Update yt-dlp now", { scope.launch { YtDlp.ensure(force = true) } })
                    }
                    Text(YtDlp.status.ifEmpty { if (YtDlp.ready) "Ready" else "Not downloaded yet" }, color = Aero.textDim, fontSize = 11.sp)
                }

                // ---------- Squidify ----------
                Section("Squidify server") {
                    var url by remember { mutableStateOf(settings.value.squidifyUrl) }
                    var user by remember { mutableStateOf(settings.value.squidifyUser) }
                    var pass by remember { mutableStateOf(settings.value.squidifyPassword) }
                    var saved by remember { mutableStateOf(false) }
                    OutlinedTextField(url, { url = it; saved = false }, label = { Text("Server address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(user, { user = it; saved = false }, label = { Text("Username") }, singleLine = true, modifier = Modifier.width(220.dp))
                        OutlinedTextField(
                            pass, { pass = it; saved = false }, label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(220.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlossButton("Save", {
                            settings.update { it.copy(squidifyUrl = url.trim(), squidifyUser = user.trim(), squidifyPassword = pass) }
                            saved = true
                        }, brush = Gloss.purple, textColor = Color.White, bold = true)
                        GlossButton("Reset to Squidify guest", {
                            url = "https://www.squidify.net"; user = "Guest"; pass = "Guest"; saved = false
                        })
                        if (saved) Text("Saved — takes effect after restarting ChudPlayer.", color = Aero.textDim, fontSize = 11.sp)
                    }
                    Text("Works with any Subsonic / Navidrome server.", color = Aero.textDim, fontSize = 11.sp)
                }

                // ---------- About ----------
                Section("About") {
                    Text("Your playlists, history and settings are in ${newpipe.desktop.data.AppDirs.dir.absolutePath}", color = Aero.textDim, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlossButton("ChudPlayer on GitHub", { openInBrowser("https://github.com/${AppInfo.REPO}") })
                        GlossButton("Open data folder", {
                            runCatching { java.awt.Desktop.getDesktop().open(newpipe.desktop.data.AppDirs.dir) }
                        })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlossButton("Close", onClose, brush = Gloss.blue, textColor = Color.White, bold = true)
                }
            }
        }
    }
}

@Suppress("unused")
private fun File.short() = name.ifEmpty { absolutePath }
