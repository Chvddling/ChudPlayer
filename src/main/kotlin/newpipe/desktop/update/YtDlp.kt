package newpipe.desktop.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import newpipe.desktop.data.AppDirs
import newpipe.desktop.data.Service
import newpipe.desktop.data.VideoItem
import newpipe.desktop.net.Http
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A private, self-updating copy of yt-dlp (%APPDATA%\ChudPlayer\bin\yt-dlp.exe).
 * yt-dlp usually gets YouTube fixes within hours, so it is the backup whenever
 * NewPipeExtractor is broken: for search, and (through mpv) for playback.
 */
object YtDlp {
    private const val DOWNLOAD_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    val exe: File = File(AppDirs.dir, "bin/yt-dlp.exe")
    private val stamp = File(AppDirs.dir, "bin/yt-dlp.updated")
    private val json = Json { ignoreUnknownKeys = true }

    var ready by mutableStateOf(exe.isFile); private set
    var status by mutableStateOf(if (exe.isFile) "yt-dlp backup ready" else ""); private set

    /** Download yt-dlp if missing, otherwise let it update itself (at most once a day unless [force]). */
    suspend fun ensure(force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            if (!exe.isFile) {
                status = "Downloading yt-dlp backup…"
                download()
                stamp.writeText(System.currentTimeMillis().toString())
            } else if (force || System.currentTimeMillis() - lastUpdate() > DAY_MS) {
                status = "Updating yt-dlp…"
                run(listOf("-U"), timeoutSec = 180)
                stamp.writeText(System.currentTimeMillis().toString())
            }
            ready = exe.isFile
            status = if (ready) "yt-dlp backup ready" else "yt-dlp not available"
        } catch (e: Exception) {
            ready = exe.isFile
            status = "yt-dlp: ${e.message}"
        }
    }

    private fun lastUpdate(): Long = runCatching { stamp.readText().trim().toLong() }.getOrDefault(0L)

    private fun download() {
        exe.parentFile.mkdirs()
        val tmp = File(exe.parentFile, "yt-dlp.exe.part")
        val req = Request.Builder().url(DOWNLOAD_URL).header("User-Agent", "ChudPlayer/${AppInfo.version}").build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("download failed (${resp.code})")
            resp.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("empty download")
        }
        if (exe.exists()) exe.delete()
        if (!tmp.renameTo(exe)) throw IllegalStateException("could not save yt-dlp.exe")
    }

    /** Runs yt-dlp and returns its standard output. */
    private fun run(args: List<String>, timeoutSec: Long): String {
        val errFile = File.createTempFile("ytdlp", ".err")
        try {
            val p = ProcessBuilder(listOf(exe.absolutePath) + args)
                .redirectError(errFile)
                .start()
            p.outputStream.close()
            val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                throw IllegalStateException("yt-dlp timed out")
            }
            if (p.exitValue() != 0 && out.isBlank()) {
                throw IllegalStateException(errFile.readText().lines().lastOrNull { it.isNotBlank() } ?: "yt-dlp failed")
            }
            return out
        } finally {
            errFile.delete()
        }
    }

    /** Backup search through yt-dlp ("ytsearch30:…" / "scsearch30:…"). Blocking: call on Dispatchers.IO. */
    fun search(query: String, service: Service, limit: Int = 30): List<VideoItem> {
        val prefix = if (service == Service.SOUNDCLOUD) "scsearch$limit:" else "ytsearch$limit:"
        val out = run(listOf("--flat-playlist", "-J", "--no-warnings", "--encoding", "utf-8", prefix + query), timeoutSec = 90)
        val root = json.parseToJsonElement(out).jsonObject
        val entries = root["entries"]?.jsonArray ?: return emptyList()
        return entries.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")
            val url = o.str("webpage_url") ?: o.str("url")?.takeIf { it.startsWith("http") }
                ?: if (service == Service.YOUTUBE && id != null) "https://www.youtube.com/watch?v=$id" else return@mapNotNull null
            val thumb = o["thumbnails"]?.let { runCatching { it.jsonArray.lastOrNull()?.jsonObject?.str("url") }.getOrNull() }
                ?: o.str("thumbnail")
                ?: if (service == Service.YOUTUBE && id != null) "https://i.ytimg.com/vi/$id/mqdefault.jpg" else null
            VideoItem(
                url = url,
                title = o.str("title") ?: url,
                uploader = o.str("channel") ?: o.str("uploader") ?: "",
                uploaderUrl = o.str("channel_url") ?: o.str("uploader_url"),
                durationSec = o["duration"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: -1,
                thumbnail = thumb,
                viewCount = o["view_count"]?.jsonPrimitive?.longOrNull ?: -1,
                isLive = o.str("live_status") == "is_live",
                serviceId = service.id,
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
}
