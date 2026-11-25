package newpipe.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.sun.jna.Native
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import newpipe.desktop.data.HistoryStore
import newpipe.desktop.data.NoStreamsException
import newpipe.desktop.data.PlayMode
import newpipe.desktop.data.SettingsStore
import newpipe.desktop.data.StreamDetails
import newpipe.desktop.data.VideoItem
import newpipe.desktop.data.YouTubeRepo
import newpipe.desktop.update.YtDlp
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.awt.Canvas
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * The heavyweight AWT canvas mpv renders into. It lives for the whole app
 * (destroying it under a running mpv would crash), and the UI only moves it around.
 */
class VideoHost {
    val canvas = Canvas().apply { background = java.awt.Color.BLACK }

    /** Where the video should be drawn, in window coordinates. null = hidden. */
    var bounds by mutableStateOf<Rect?>(null)

    fun windowId(): Long? = if (canvas.isDisplayable) Native.getComponentID(canvas) else null
}

/** Player state + queue. All public state is Compose state; all mpv calls run on one background thread. */
class PlayerController(
    private val repo: YouTubeRepo,
    private val history: HistoryStore,
    private val settings: SettingsStore,
    val video: VideoHost,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mpvExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "mpv-commands").apply { isDaemon = true } }
    private val mpvThread = mpvExecutor.asCoroutineDispatcher()

    var queue by mutableStateOf(listOf<VideoItem>()); private set
    var index by mutableStateOf(-1); private set
    val current: VideoItem? get() = queue.getOrNull(index)

    var details by mutableStateOf<StreamDetails?>(null); private set
    var sourceLabel by mutableStateOf(""); private set
    /** false while playing something without a picture (SoundCloud, audio mode). */
    var hasVideo by mutableStateOf(true); private set
    /** Set when NewPipeExtractor failed and yt-dlp is playing instead. */
    var backupNotice by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    var paused by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var position by mutableStateOf(0.0); private set
    var duration by mutableStateOf(0.0); private set
    var volume by mutableStateOf(settings.value.volume.toFloat()); private set
    var fullscreen by mutableStateOf(false); private set

    var mode by mutableStateOf(runCatching { PlayMode.valueOf(settings.value.mode) }.getOrDefault(PlayMode.VIDEO)); private set
    var maxHeight by mutableStateOf(settings.value.maxHeight); private set
    var autoplay by mutableStateOf(settings.value.autoplay); private set

    private var mpv: Mpv? = null
    private var loadJob: Job? = null
    private var pendingAudio: String? = null
    private var pendingSeek: Double? = null
    private var triedYtDlp = false
    @Volatile private var lastPosSent = -1.0

    // ---------------- queue ----------------

    fun play(items: List<VideoItem>, start: Int = 0) {
        if (items.isEmpty()) return
        queue = items
        index = start.coerceIn(0, items.lastIndex)
        loadCurrent()
    }

    fun play(item: VideoItem) = play(listOf(item))

    fun playUrl(url: String) = play(VideoItem(url = url, title = "Loading…", serviceId = repo.serviceOfUrl(url)?.id ?: 0))

    /** Total length of the queue in seconds (unknown lengths count as 0). */
    val queueSeconds: Long get() = queue.sumOf { it.durationSec.coerceAtLeast(0) }

    /**
     * What a click on a song does: add it to the end of the queue and play it now.
     * The rest of the queue stays; if it's already queued, jump to it.
     */
    fun addAndPlay(item: VideoItem) {
        if (queue.isEmpty()) { play(item); return }
        val existing = queue.indexOfFirst { it.url == item.url }
        if (existing >= 0) { jumpTo(existing); return }
        queue = queue + item
        index = queue.lastIndex
        loadCurrent()
    }

    /** "Play in background": audio only. */
    fun playInBackground(item: VideoItem) {
        if (mode != PlayMode.AUDIO) { mode = PlayMode.AUDIO; settings.update { it.copy(mode = mode.name) } }
        addAndPlay(item)
    }

    /** Play a whole playlist: in order, shuffled, and/or audio only. */
    fun playCollection(items: List<VideoItem>, start: Int = 0, shuffle: Boolean = false, audioOnly: Boolean = false) {
        if (items.isEmpty()) return
        if (audioOnly && mode != PlayMode.AUDIO) {
            mode = PlayMode.AUDIO
            settings.update { it.copy(mode = mode.name) }
        }
        if (shuffle) play(items.shuffled()) else play(items, start)
    }

    fun enqueue(item: VideoItem) {
        queue = queue + item
        if (index < 0) { index = 0; loadCurrent() }
    }

    fun playNext(item: VideoItem) {
        if (index < 0) { enqueue(item); return }
        queue = queue.toMutableList().apply { add(index + 1, item) }
    }

    fun jumpTo(i: Int) {
        if (i !in queue.indices) return
        index = i
        loadCurrent()
    }

    fun removeAt(i: Int) {
        if (i !in queue.indices) return
        val wasCurrent = i == index
        queue = queue.toMutableList().apply { removeAt(i) }
        when {
            queue.isEmpty() -> stop()
            i < index -> index--
            wasCurrent -> { index = index.coerceAtMost(queue.lastIndex); loadCurrent() }
        }
    }

    fun next() {
        when {
            index < queue.lastIndex -> { index++; loadCurrent() }
            autoplay -> autoplayRelated()
        }
    }

    fun previous() {
        if (position > 5 || index <= 0) seek(0.0) else { index--; loadCurrent() }
    }

    fun stop() {
        loadJob?.cancel()
        queue = emptyList(); index = -1; details = null; loading = false; error = null
        position = 0.0; duration = 0.0
        mpvCmd { it.command("stop") }
    }

    // ---------------- playback controls ----------------

    fun togglePause() { if (current != null) mpvCmd { it.command("cycle", "pause") } }

    fun seek(seconds: Double) {
        position = seconds
        mpvCmd { it.command("seek", seconds.toString(), "absolute") }
    }

    fun seekRelative(seconds: Int) = mpvCmd { it.command("seek", seconds.toString(), "relative") }

    fun changeVolume(v: Float, save: Boolean) {
        volume = v
        mpvCmd { it.setProperty("volume", v.toInt().toString()) }
        if (save) settings.update { it.copy(volume = v.toInt()) }
    }

    fun changeFullscreen(fs: Boolean) {
        fullscreen = fs
        mpvCmd { it.setProperty("fullscreen", if (fs) "yes" else "no") }
    }

    fun changeMode(m: PlayMode) {
        if (m == mode) return
        mode = m
        settings.update { it.copy(mode = m.name) }
        if (m == PlayMode.AUDIO && fullscreen) changeFullscreen(false)
        restartAtCurrentPosition()
    }

    fun changeMaxHeight(h: Int) {
        if (h == maxHeight) return
        maxHeight = h
        settings.update { it.copy(maxHeight = h) }
        if (mode == PlayMode.VIDEO) restartAtCurrentPosition()
    }

    fun changeAutoplay(on: Boolean) {
        autoplay = on
        settings.update { it.copy(autoplay = on) }
    }

    fun shutdown() {
        loadJob?.cancel()
        runCatching { mpv?.destroy() }
        mpv = null
        mpvExecutor.shutdownNow()
        scope.cancel()
    }

    // ---------------- internals ----------------

    private fun loadCurrent(seekTo: Double? = null) {
        val item = current ?: return
        loadJob?.cancel()
        details = null; error = null; loading = true; sourceLabel = ""; backupNotice = null
        position = 0.0; duration = item.durationSec.coerceAtLeast(0).toDouble()
        loadJob = scope.launch {
            try {
                val d = withContext(Dispatchers.IO) { repo.streamDetails(item.url) }
                details = d
                // Replace the placeholder item with the full one (title, thumbnail…)
                if (index in queue.indices && queue[index].url == item.url) {
                    queue = queue.toMutableList().also { it[index] = d.item }
                }
                history.add(d.item)
                startPlayback(d, seekTo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                // NewPipeExtractor couldn't handle it (e.g. YouTube changed something): try the yt-dlp backup.
                if (e !is ReCaptchaException && YtDlp.ready && playWithYtDlp(item, seekTo)) {
                    backupNotice = "NewPipe couldn't load this (${e.message ?: e::class.simpleName}) — playing through the yt-dlp backup."
                    history.add(item)
                } else {
                    loading = false
                    error = friendlyError(e)
                }
            }
        }
    }

    /** Let mpv + yt-dlp handle the page URL directly. */
    private fun playWithYtDlp(item: VideoItem, seekTo: Double? = null): Boolean {
        val m = mpvOrNull() ?: return false
        triedYtDlp = true
        pendingAudio = null
        pendingSeek = seekTo
        hasVideo = mode == PlayMode.VIDEO && item.serviceId != 1
        sourceLabel = "yt-dlp backup"
        loading = true
        error = null
        val format = if (!hasVideo) "bestaudio/best" else "bestvideo[height<=?$maxHeight]+bestaudio/best"
        val title = item.title
        val audioOnly = !hasVideo
        scope.launch(mpvThread) {
            m.setProperty("user-agent", "")
            m.setProperty("http-header-fields", "")
            m.setProperty("ytdl-format", format)
            m.setProperty("vid", if (audioOnly) "no" else "auto")
            m.setProperty("force-media-title", title)
            m.command("loadfile", item.url, "replace")
            m.setProperty("pause", "no")
        }
        return true
    }

    private fun restartAtCurrentPosition() {
        val d = details ?: return
        startPlayback(d, position.takeIf { it > 1 && !d.item.isLive })
    }

    private fun startPlayback(d: StreamDetails, seekTo: Double?) {
        val src = try {
            repo.selectSources(d.info, mode, maxHeight)
        } catch (e: Exception) {
            loading = false; error = friendlyError(e); return
        }
        val m = mpvOrNull() ?: run { loading = false; return }
        sourceLabel = src.label
        hasVideo = src.hasVideo
        triedYtDlp = false
        loading = true
        pendingAudio = src.audioUrl
        pendingSeek = seekTo
        val audioOnly = mode == PlayMode.AUDIO || !src.hasVideo
        val title = d.item.title
        scope.launch(mpvThread) {
            m.setProperty("user-agent", src.userAgent)
            m.setProperty("http-header-fields", src.headers)
            m.setProperty("vid", if (audioOnly) "no" else "auto")
            m.setProperty("force-media-title", title)
            m.command("loadfile", src.mainUrl, "replace")
            m.setProperty("pause", "no")
        }
    }

    /** If the direct stream fails, let mpv ask yt-dlp (only works if yt-dlp.exe is on PATH). */
    private fun tryYtDlpFallback(): Boolean {
        val item = current ?: return false
        if (triedYtDlp || mpv == null) return false
        val ok = playWithYtDlp(item, position.takeIf { it > 1 })
        if (ok) backupNotice = "The direct stream failed — playing through the yt-dlp backup."
        return ok
    }

    private fun autoplayRelated() {
        val d = details ?: return
        val recent = queue.map { it.url }.toSet() + history.entries.take(50).map { it.item.url }
        val nextItem = d.related.firstOrNull { it.url !in recent && !it.isLive } ?: d.related.firstOrNull() ?: return
        queue = queue + nextItem
        index = queue.lastIndex
        loadCurrent()
    }

    private var mpvKnowsYtDlp = false

    private fun mpvOrNull(): Mpv? {
        val ytdl = YtDlp.exe.takeIf { it.isFile }?.absolutePath
        mpv?.let { existing ->
            // yt-dlp was downloaded after mpv started: restart mpv once so it can use it.
            if (ytdl == null || mpvKnowsYtDlp) return existing
            runCatching { existing.destroy() }
            mpv = null
        }
        return try {
            Mpv(video.windowId(), ytdl, ::onMpvEvent).also {
                it.setProperty("volume", volume.toInt().toString())
                mpv = it
                mpvKnowsYtDlp = ytdl != null
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            error = "Could not start mpv: ${t.message}"
            null
        }
    }

    private fun mpvCmd(block: (Mpv) -> Unit) {
        val m = mpv ?: return
        scope.launch(mpvThread) { block(m) }
    }

    /** Called on mpv's event thread: hop to the UI thread. */
    private fun onMpvEvent(e: MpvEvent) {
        if (e is MpvEvent.Property && e.name == "time-pos") {
            val v = e.value as? Double ?: return
            if (abs(v - lastPosSent) < 0.25) return
            lastPosSent = v
        }
        scope.launch { handle(e) }
    }

    private fun handle(e: MpvEvent) {
        when (e) {
            is MpvEvent.Property -> when (e.name) {
                "pause" -> paused = e.value as? Boolean ?: false
                "time-pos" -> (e.value as? Double)?.let { position = it }
                "duration" -> (e.value as? Double)?.let { duration = it }
                "paused-for-cache" -> buffering = e.value as? Boolean ?: false
                "volume" -> (e.value as? Double)?.let { volume = it.toFloat() }
                "fullscreen" -> {
                    // mpv's own fullscreen (double click / F key on the video) → mirror on our window.
                    val fs = e.value as? Boolean ?: false
                    if (fs != fullscreen && mode == PlayMode.VIDEO && hasVideo) fullscreen = fs
                }
            }
            MpvEvent.FileLoaded -> {
                loading = false
                val m = mpv ?: return
                val audio = pendingAudio
                val seekTo = pendingSeek
                pendingAudio = null; pendingSeek = null
                scope.launch(mpvThread) {
                    if (audio != null) m.command("audio-add", audio, "select")
                    if (seekTo != null) m.command("seek", seekTo.toString(), "absolute")
                }
            }
            is MpvEvent.EndFile -> when (e.reason) {
                Mpv.END_EOF -> next()
                Mpv.END_ERROR -> if (!tryYtDlpFallback()) {
                    loading = false
                    error = "Playback failed (mpv error ${e.error}). Try a lower quality or audio mode."
                }
            }
            MpvEvent.Shutdown -> Unit
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is ReCaptchaException -> "YouTube is asking for a CAPTCHA (too many requests from your IP). Wait a bit or switch network/VPN."
        is AgeRestrictedContentException -> "This video is age-restricted and can't be played without an account."
        is ContentNotAvailableException -> e.message ?: "This content is not available."
        is NoStreamsException -> e.message ?: "No playable streams."
        else -> e.message ?: e::class.simpleName ?: "Unknown error"
    }
}
