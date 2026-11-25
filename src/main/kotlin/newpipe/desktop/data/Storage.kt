package newpipe.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

object AppDirs {
    /** %APPDATA%\ChudPlayer. Data from the old "NewPipeDesktop" folder is copied over once. */
    val dir: File = run {
        val base = File(System.getenv("APPDATA") ?: System.getProperty("user.home"))
        val dir = File(base, "ChudPlayer")
        val old = File(base, "NewPipeDesktop")
        if (!dir.exists() && old.isDirectory) runCatching { old.copyRecursively(dir, overwrite = false) }
        dir.apply { mkdirs() }
    }
}

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

/** One background thread for all disk writes so the UI never waits on the disk. */
private val ioWriter = Executors.newSingleThreadExecutor { r -> Thread(r, "storage-writer").apply { isDaemon = true } }

private fun writeAsync(file: File, text: String) = ioWriter.execute {
    runCatching {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }.onFailure { it.printStackTrace() }
}

@Serializable
data class Settings(
    val maxHeight: Int = 1080,
    val autoplay: Boolean = true,
    val volume: Int = 100,
    val mode: String = "VIDEO",
    /** Check GitHub for a new ChudPlayer version at startup. */
    val autoUpdate: Boolean = true,
    /** Keep a copy of yt-dlp up to date and use it when NewPipeExtractor fails. */
    val ytDlpBackup: Boolean = true,
    /** Play the current song over and over. */
    val repeatOne: Boolean = false,
    /** Play the queue in random order. */
    val shuffle: Boolean = false,
    /** Start the queue again after the last song. */
    val loopQueue: Boolean = false,
    /** Squidify (Subsonic/Navidrome) server and its public guest login. */
    val squidifyUrl: String = "https://www.squidify.net",
    val squidifyUser: String = "Guest",
    val squidifyPassword: String = "Guest",
    /** Main music folder for "My Music" (empty = the Windows Music folder). */
    val musicFolder: String = "",
    /** The folder that was open last time in "My Music". */
    val lastLocalFolder: String = "",
    /** The X button hides ChudPlayer in the tray instead of closing it. */
    val closeToTray: Boolean = false,
    /** Phone remote (ChudPlayer Remote app). Off by default. */
    val remoteEnabled: Boolean = false,
    val remotePort: Int = 8765,
    val remoteToken: String = "",
    /** Height of the queue panel under the video on "Now Playing" (dp). */
    val queuePanelHeight: Int = 260,
    /** History sort: "recent" or "plays". */
    val historySort: String = "recent",
    /** Look of the app (chud.theme.Themes ids); the phone remote can follow it. */
    val theme: String = "smoked",
)

class SettingsStore {
    private val file = File(AppDirs.dir, "settings.json")

    var value: Settings = runCatching { json.decodeFromString(Settings.serializer(), file.readText()) }.getOrDefault(Settings())
        private set

    fun update(change: (Settings) -> Settings) {
        value = change(value)
        writeAsync(file, json.encodeToString(Settings.serializer(), value))
    }
}

@Serializable
data class HistoryEntry(val item: VideoItem, val watchedAt: Long, val plays: Int = 1)

private val historySerializer = ListSerializer(HistoryEntry.serializer())

class HistoryStore {
    private val file = File(AppDirs.dir, "history.json")

    var entries by mutableStateOf(
        runCatching { json.decodeFromString(historySerializer, file.readText()) }.getOrDefault(emptyList())
    )
        private set

    /** Every start of a song counts as one play (also each repeat-song loop). */
    fun add(item: VideoItem) {
        val old = entries.firstOrNull { it.item.url == item.url }
        val entry = HistoryEntry(item, System.currentTimeMillis(), (old?.plays ?: 0) + 1)
        entries = (listOf(entry) + entries.filter { it.item.url != item.url }).take(1000)
        save()
    }

    fun playsOf(url: String): Int = entries.firstOrNull { it.item.url == url }?.plays ?: 0

    fun remove(url: String) {
        entries = entries.filter { it.item.url != url }
        save()
    }

    fun clear() {
        entries = emptyList()
        save()
    }

    private fun save() = writeAsync(file, json.encodeToString(historySerializer, entries))
}

// ---------------- local playlists ----------------

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val items: List<VideoItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

private val playlistsSerializer = ListSerializer(Playlist.serializer())

/** Local playlists, saved to %APPDATA%\ChudPlayer\playlists.json. Call from the UI thread. */
class PlaylistStore {
    private val file = File(AppDirs.dir, "playlists.json")

    var playlists by mutableStateOf(
        runCatching { json.decodeFromString(playlistsSerializer, file.readText()) }.getOrDefault(emptyList())
    )
        private set

    fun get(id: String): Playlist? = playlists.firstOrNull { it.id == id }

    fun create(name: String, items: List<VideoItem> = emptyList()): Playlist {
        val p = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "New playlist" },
            items = items.distinctBy { it.url },
        )
        playlists = playlists + p
        save()
        return p
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) {
        playlists = playlists.filter { it.id != id }
        save()
    }

    /** Adds the videos that aren't already in the playlist. Returns how many were added. */
    fun add(id: String, items: List<VideoItem>): Int {
        var added = 0
        edit(id) { p ->
            val existing = p.items.map { it.url }.toSet()
            val fresh = items.filter { it.url !in existing }.distinctBy { it.url }
            added = fresh.size
            p.copy(items = p.items + fresh)
        }
        return added
    }

    fun removeAt(id: String, index: Int) = edit(id) { p ->
        if (index in p.items.indices) p.copy(items = p.items.toMutableList().apply { removeAt(index) }) else p
    }

    fun move(id: String, from: Int, to: Int) = edit(id) { p ->
        if (from !in p.items.indices || to !in p.items.indices) p
        else p.copy(items = p.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun contains(id: String, url: String): Boolean = get(id)?.items?.any { it.url == url } == true

    private fun edit(id: String, change: (Playlist) -> Playlist) {
        playlists = playlists.map { if (it.id == id) change(it) else it }
        save()
    }

    private fun save() = writeAsync(file, json.encodeToString(playlistsSerializer, playlists))
}
