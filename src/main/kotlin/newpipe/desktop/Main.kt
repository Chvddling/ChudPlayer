package newpipe.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import newpipe.desktop.remote.RemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import newpipe.desktop.data.HistoryStore
import newpipe.desktop.data.NewPipeSetup
import newpipe.desktop.data.PlayMode
import newpipe.desktop.data.PlaylistStore
import newpipe.desktop.data.Service
import newpipe.desktop.data.SettingsStore
import newpipe.desktop.data.SubsonicClient
import newpipe.desktop.data.YouTubeRepo
import newpipe.desktop.player.MpvLib
import newpipe.desktop.player.PlayerController
import newpipe.desktop.player.VideoHost
import newpipe.desktop.ui.Aero
import newpipe.desktop.ui.AppTheme
import newpipe.desktop.ui.Gloss
import newpipe.desktop.ui.HistoryScreen
import newpipe.desktop.ui.MiniPlayerWindow
import newpipe.desktop.ui.PlayerScreen
import newpipe.desktop.ui.PlaylistDialog
import newpipe.desktop.ui.PlaylistDialogHost
import newpipe.desktop.ui.PlaylistsScreen
import newpipe.desktop.ui.Screen
import newpipe.desktop.ui.SearchScreen
import newpipe.desktop.ui.SearchState
import newpipe.desktop.ui.ServiceBar
import newpipe.desktop.ui.SourceList
import newpipe.desktop.ui.StatusBar
import newpipe.desktop.ui.TopBar
import newpipe.desktop.ui.VideoActions
import newpipe.desktop.ui.VideoLayer
import newpipe.desktop.ui.serviceName
import newpipe.desktop.data.SearchKind
import newpipe.desktop.ui.ChannelScreen
import newpipe.desktop.ui.ChannelState
import newpipe.desktop.ui.InputFocus
import newpipe.desktop.ui.GlassWindowFrame
import newpipe.desktop.ui.WindowMaximizer
import newpipe.desktop.ui.LocalScreen
import newpipe.desktop.ui.LocalState
import newpipe.desktop.ui.SettingsWindow
import newpipe.desktop.ui.chooseFolder
import newpipe.desktop.data.LocalMusic
import newpipe.desktop.ui.RemotePlaylistScreen
import newpipe.desktop.ui.RemotePlaylistState
import newpipe.desktop.update.Updater
import newpipe.desktop.update.YtDlp
import kotlinx.coroutines.launch

/** One place in the app (a screen plus what it shows). */
class Nav(
    val screen: Screen,
    val playlistId: String? = null,
    val channel: ChannelState? = null,
    val remote: RemotePlaylistState? = null,
)

/** The main window's state, for the tray (set once the window exists). */
private var mainWindow0: androidx.compose.ui.window.WindowState? = null

/** Tray icon: a small glossy blue orb with a play sign. */
private object TrayIcon : androidx.compose.ui.graphics.painter.Painter() {
    override val intrinsicSize = androidx.compose.ui.geometry.Size(32f, 32f)
    override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
        val r = size.minDimension / 2f
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(androidx.compose.ui.graphics.Color(0xFFD8F0FF), androidx.compose.ui.graphics.Color(0xFF3E9BEA), androidx.compose.ui.graphics.Color(0xFF0E4C90)),
                center = androidx.compose.ui.geometry.Offset(center.x, size.height * 0.3f),
                radius = r * 1.6f,
            ),
            radius = r,
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.40f, size.height * 0.28f)
            lineTo(size.width * 0.74f, size.height * 0.50f)
            lineTo(size.width * 0.40f, size.height * 0.72f)
            close()
        }
        drawPath(path, androidx.compose.ui.graphics.Color.White)
    }
}

class AppState {
    var nav by mutableStateOf(Nav(Screen.Search)); private set
    var canGoBack by mutableStateOf(false); private set
    private val back = mutableListOf<Nav>()
    val screen: Screen get() = nav.screen
    val openPlaylistId: String? get() = nav.playlistId
    var miniPlayerOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var playlistDialog by mutableStateOf<PlaylistDialog?>(null)

    fun go(n: Nav) {
        val same = n.screen == nav.screen && n.playlistId == nav.playlistId && n.channel == null && n.remote == null
        if (same) return
        back.add(nav)
        if (back.size > 30) back.removeAt(0)
        nav = n
        canGoBack = true
    }

    fun goBack() {
        val prev = back.removeLastOrNull() ?: return
        nav = prev
        canGoBack = back.isNotEmpty()
    }
}

fun main() {
    System.setProperty("jna.encoding", "UTF-8")
    // Native Windows look for the folder picker
    runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
    // Show menus and popups as their own small windows, so they appear on top of the (native) video.
    System.setProperty("compose.layers.type", "WINDOW")
    MpvLib.prepareSearchPath()
    NewPipeSetup.init()

    val settings = SettingsStore()
    val history = HistoryStore()
    val playlists = PlaylistStore()
    val repo = YouTubeRepo(
        SubsonicClient(settings.value.squidifyUrl, settings.value.squidifyUser, settings.value.squidifyPassword),
    )

    application {
        val app = remember { AppState() }
        val player = remember { PlayerController(repo, history, settings, VideoHost()) }
        val search = remember { SearchState(repo, CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
        val bgScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val local = remember { LocalState(settings, bgScope) }
        val actions = remember {
            VideoActions(
                player = player,
                playlists = playlists,
                showDialog = { app.playlistDialog = it },
                openPlayer = { app.go(Nav(Screen.Player)) },
                openChannelPage = { url ->
                    val folder = LocalMusic.folderOf(url)
                    if (folder != null) {
                        // a local song's "channel" is its folder
                        search.changeService(Service.LOCAL)
                        local.open(folder)
                        app.go(Nav(Screen.Search))
                    } else {
                        app.go(Nav(Screen.Channel, channel = ChannelState(url, repo, bgScope)))
                    }
                },
                openPlaylistPage = { url -> app.go(Nav(Screen.RemotePlaylist, remote = RemotePlaylistState(url, repo, bgScope))) },
            )
        }
        val updater = remember { Updater(bgScope) }
        val remote = remember { RemoteServer(player, playlists, history, repo) { local.root } }
        var windowVisible by remember { mutableStateOf(true) }

        fun quit() {
            remote.stop()
            player.shutdown()
            exitApplication()
        }

        fun toTray() {
            if (player.fullscreen) player.changeFullscreen(false)
            windowVisible = false
        }

        fun fromTray() {
            windowVisible = true
            mainWindow0?.isMinimized = false
        }
        var autoUpdate by remember { mutableStateOf(settings.value.autoUpdate) }
        LaunchedEffect(Unit) {
            // Keep the yt-dlp backup fresh, then look for a new ChudPlayer release.
            if (settings.value.ytDlpBackup) bgScope.launch { YtDlp.ensure() }
            if (autoUpdate) updater.check(manual = false)
            // Phone remote: only started if the user turned it on
            if (settings.value.remoteEnabled) {
                val token = settings.value.remoteToken.ifEmpty { RemoteServer.newToken() }
                settings.update { it.copy(remoteToken = token) }
                remote.start(settings.value.remotePort, token)
            }
        }
        val mainWindow = rememberWindowState(size = DpSize(1280.dp, 820.dp))
        mainWindow0 = mainWindow

        // ---------- system tray ----------
        val trayState = rememberTrayState()
        Tray(
            icon = TrayIcon,
            state = trayState,
            tooltip = player.current?.let { "ChudPlayer – ${it.title}" } ?: "ChudPlayer",
            onAction = { fromTray() },
            menu = {
                Item("Show ChudPlayer", onClick = { fromTray() })
                Separator()
                Item(if (player.paused || player.current == null) "Play" else "Pause", onClick = { player.togglePause() })
                Item("Next", onClick = { player.next() })
                Item("Previous", onClick = { player.previous() })
                Separator()
                Item("Exit ChudPlayer", onClick = { quit() })
            },
        )
        var placementBeforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }

        LaunchedEffect(player.fullscreen) {
            if (player.fullscreen) {
                if (mainWindow.placement != WindowPlacement.Fullscreen) placementBeforeFullscreen = mainWindow.placement
                mainWindow.placement = WindowPlacement.Fullscreen
                app.go(Nav(Screen.Player))
            } else if (mainWindow.placement == WindowPlacement.Fullscreen) {
                mainWindow.placement = placementBeforeFullscreen
            }
        }

        fun submitSearch() {
            val q = search.query.trim()
            if (q.isEmpty()) return
            // "My Music" is browse-only: searching goes to YouTube
            if (search.service == Service.LOCAL) search.changeService(Service.YOUTUBE)
            if (repo.isVideoUrl(q)) {
                player.playUrl(q)
                app.go(Nav(Screen.Player))
            } else {
                app.go(Nav(Screen.Search))
                search.search(q)
            }
        }

        fun openMiniPlayer() {
            if (player.fullscreen) player.changeFullscreen(false)
            app.miniPlayerOpen = true
            mainWindow.isMinimized = true
        }

        val maximizer = remember { WindowMaximizer() }
        Window(
            onCloseRequest = {
                if (settings.value.closeToTray) toTray() else quit()
            },
            state = mainWindow,
            visible = windowVisible,
            undecorated = true,   // we draw our own Windows 7 style glass frame and buttons
            title = player.current?.let { "${it.title} – ChudPlayer" } ?: "ChudPlayer",
            onKeyEvent = { handleShortcut(it, player) },
        ) {
            AppTheme {
                Box(Modifier.fillMaxSize().background(Aero.bgDeep)) {
                    if (player.fullscreen) {
                        PlayerScreen(player, actions)
                    } else GlassWindowFrame(
                        title = player.current?.let { "${it.title} – ChudPlayer" } ?: "ChudPlayer",
                        maximizer = maximizer,
                        onMinimize = { mainWindow.isMinimized = true },
                        onClose = { if (settings.value.closeToTray) toTray() else quit() },
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            TopBar(player, search.query, { search.query = it }, ::submitSearch)
                            val info = when (app.screen) {
                                Screen.Search -> if (search.service == Service.LOCAL) {
                                    "My Music · ${local.selected?.absolutePath ?: "choose a music folder"} · right-click songs for more options"
                                } else search.summary()
                                Screen.Player -> player.current?.let { "Playing from ${serviceName(it.serviceId)}" + (if (player.sourceLabel.isNotEmpty()) " · ${player.sourceLabel}" else "") } ?: ""
                                Screen.Playlists -> "Right-click a playlist or a song for more options"
                                Screen.History -> "Right-click a song for more options"
                                Screen.Channel -> app.nav.channel?.details?.ref?.name?.let { "Channel: $it · right-click anything for more options" } ?: "Loading channel…"
                                Screen.RemotePlaylist -> app.nav.remote?.ref?.name?.let { "Playlist: $it" } ?: "Loading playlist…"
                            }
                            val onSearch = app.screen == Screen.Search
                            ServiceBar(
                                serviceId = search.service.id,
                                onService = { search.changeService(Service.of(it)); app.go(Nav(Screen.Search)) },
                                info = info,
                                kindLabels = if (onSearch && search.service != Service.LOCAL) search.kindLabels() else null,
                                kind = search.kind.ordinal,
                                onKind = { search.changeKind(SearchKind.entries[it]) },
                            )
                            Row(Modifier.weight(1f)) {
                                SourceList(app.screen, app.openPlaylistId, playlists, player, actions) { screen, playlistId ->
                                    app.go(Nav(screen, playlistId = playlistId))
                                }
                                Box(Modifier.weight(1f).fillMaxHeight().background(Gloss.content)) {
                                    when (app.screen) {
                                        Screen.Search -> if (search.service == Service.LOCAL) {
                                            LocalScreen(local, actions, onChooseFolder = { chooseFolder(local.root)?.let { local.changeMusicFolder(it) } })
                                        } else SearchScreen(search, actions)
                                        Screen.Player -> PlayerScreen(player, actions)
                                        Screen.Playlists -> PlaylistsScreen(playlists, actions, app.openPlaylistId) { app.go(Nav(Screen.Playlists, playlistId = it)) }
                                        Screen.History -> HistoryScreen(history, actions)
                                        Screen.Channel -> app.nav.channel?.let { ch ->
                                            ChannelScreen(ch, actions, onBack = if (app.canGoBack) ({ app.goBack() }) else null)
                                        }
                                        Screen.RemotePlaylist -> app.nav.remote?.let { pl ->
                                            RemotePlaylistScreen(pl, actions, onBack = if (app.canGoBack) ({ app.goBack() }) else null)
                                        }
                                    }
                                }
                            }
                            StatusBar(
                                player = player,
                                onMiniPlayer = ::openMiniPlayer,
                                updater = updater,
                                onInstallUpdate = {
                                    updater.install(exitApp = { quit() })
                                },
                                autoUpdate = autoUpdate,
                                onToggleAutoUpdate = {
                                    autoUpdate = !autoUpdate
                                    settings.update { it.copy(autoUpdate = autoUpdate) }
                                },
                                onUpdateYtDlp = { bgScope.launch { YtDlp.ensure(force = true) } },
                                onSettings = { app.settingsOpen = true },
                                onTray = { toTray() },
                                remoteOn = remote.running,
                            )
                        }
                    }
                    // Must stay last and always composed: hosts mpv's native video window.
                    VideoLayer(player.video)
                }

                if (app.settingsOpen) {
                    SettingsWindow(
                        settings = settings,
                        local = local,
                        updater = updater,
                        autoUpdate = autoUpdate,
                        onToggleAutoUpdate = {
                            autoUpdate = !autoUpdate
                            settings.update { it.copy(autoUpdate = autoUpdate) }
                        },
                        scope = bgScope,
                        remote = remote,
                        onClose = { app.settingsOpen = false },
                    )
                }

                PlaylistDialogHost(
                    dialog = app.playlistDialog,
                    store = playlists,
                    onDismiss = { app.playlistDialog = null },
                    onSwitch = { app.playlistDialog = it },
                    onDeleted = { id -> if (app.openPlaylistId == id) app.go(Nav(Screen.Playlists)) },
                )
            }
        }

        if (app.miniPlayerOpen) {
            MiniPlayerWindow(
                player = player,
                onClose = { app.miniPlayerOpen = false },
                onRestore = {
                    app.miniPlayerOpen = false
                    mainWindow.isMinimized = false
                    app.go(Nav(Screen.Player))
                },
            )
        }
    }
}

/** Keys that reach the window (i.e. not typed into the search box and not eaten by the video). */
private fun handleShortcut(e: KeyEvent, player: PlayerController): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    // While typing in a text box, Space and arrows belong to the text box.
    if (InputFocus.typing && (e.key == Key.Spacebar || e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) return false
    return when (e.key) {
        Key.F11 -> { if (player.mode == PlayMode.VIDEO && player.hasVideo) player.changeFullscreen(!player.fullscreen); true }
        Key.Escape -> if (player.fullscreen) { player.changeFullscreen(false); true } else false
        Key.Spacebar, Key.MediaPlayPause -> { player.togglePause(); true }
        Key.MediaNext -> { player.next(); true }
        Key.MediaPrevious -> { player.previous(); true }
        Key.DirectionRight -> { player.seekRelative(5); true }
        Key.DirectionLeft -> { player.seekRelative(-5); true }
        else -> false
    }
}
