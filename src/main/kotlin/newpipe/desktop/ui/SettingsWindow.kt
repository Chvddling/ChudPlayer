package newpipe.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import newpipe.desktop.remote.QrCode
import newpipe.desktop.remote.RemoteServer
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
        textColor = if (on) Aero.onAccent else Aero.text,
    )
}

/** A small preview of a theme: its bar, accent and text colours. */
@Composable
private fun ThemeSwatch(t: chud.theme.ChudTheme, selected: Boolean, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    Column(
        Modifier
            .width(186.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Aero.accent else Aero.edge, shape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp).background(t.bar), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Brush.radialGradient(t.orb)))
                Box(Modifier.width(60.dp).height(6.dp).clip(shape).background(t.progress))
            }
        }
        Row(Modifier.fillMaxWidth().background(t.bgContent).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.name, color = t.text, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
            if (selected) Text("✓", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
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
    remote: RemoteServer,
    onClose: () -> Unit,
) {
    val state = rememberDialogState(position = WindowPosition(Alignment.Center), size = DpSize(640.dp, 720.dp))
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
                // ---------- Theme ----------
                Section("Theme") {
                    Text("How ChudPlayer looks. The phone remote follows this unless you pick its own theme there.", color = Aero.textDim, fontSize = 11.sp)
                    val current = chud.theme.ThemeState.current
                    chud.theme.Themes.all.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { t ->
                                ThemeSwatch(t, t.id == current.id) {
                                    chud.theme.ThemeState.current = t
                                    settings.update { s -> s.copy(theme = t.id) }
                                }
                            }
                        }
                    }
                }

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

                // ---------- Phone remote ----------
                Section("Phone remote (ChudPlayer Remote app)") {
                    Text(
                        "Control ChudPlayer from your Android phone on the same Wi-Fi. Off = nothing runs and no memory is used.",
                        color = Aero.textDim, fontSize = 11.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Toggle("Phone remote", remote.running) { on ->
                            if (on) {
                                val token = settings.value.remoteToken.ifEmpty { RemoteServer.newToken() }
                                settings.update { it.copy(remoteEnabled = true, remoteToken = token) }
                                remote.start(settings.value.remotePort, token)
                            } else {
                                settings.update { it.copy(remoteEnabled = false) }
                                remote.stop()
                            }
                        }
                        if (remote.running) {
                            GlossButton("New pairing code", {
                                val token = RemoteServer.newToken()
                                settings.update { it.copy(remoteToken = token) }
                                remote.start(settings.value.remotePort, token)
                            })
                        }
                        GlossButton("Get the app (APK)", { openInBrowser("https://github.com/${AppInfo.REPO}/releases/latest") })
                    }
                    remote.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
                    if (remote.running) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            QrCode(remote.pairingLink(), 200.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("1. Install ChudPlayer Remote on your phone", color = Aero.text, fontSize = 12.sp)
                                Text("2. Open it and tap “Scan QR code”", color = Aero.text, fontSize = 12.sp)
                                Text("Or type it in by hand:", color = Aero.textDim, fontSize = 11.sp)
                                val addrs = RemoteServer.lanAddresses()
                                Text("Address: " + (addrs.firstOrNull() ?: "?") + "   Port: ${remote.port}", color = Aero.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Pairing code: ${remote.token}", color = Aero.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (addrs.size > 1) Text("Other addresses: " + addrs.drop(1).joinToString(), color = Aero.textDim, fontSize = 11.sp)
                                Text("If Windows asks about the firewall, allow ChudPlayer on private networks.", color = Aero.textDim, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // ---------- Tray ----------
                Section("System tray") {
                    var tray by remember { mutableStateOf(settings.value.closeToTray) }
                    Toggle("Close button sends ChudPlayer to the tray", tray) {
                        tray = it
                        settings.update { s -> s.copy(closeToTray = it) }
                    }
                    Text("Use “To tray” in the bottom bar any time. Click the tray icon to bring ChudPlayer back; right-click it for play/pause, next and exit.", color = Aero.textDim, fontSize = 11.sp)
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
                    GlossButton("Close", onClose, brush = Gloss.blue, textColor = Aero.onAccent, bold = true)
                }
            }
        }
    }
}

@Suppress("unused")
private fun File.short() = name.ifEmpty { absolutePath }
