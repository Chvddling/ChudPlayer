package newpipe.desktop.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import newpipe.desktop.net.Http
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Properties
import java.util.zip.ZipInputStream

/** Version info baked in at build time (see genAppInfo in build.gradle.kts). */
object AppInfo {
    const val NAME = "ChudPlayer"
    const val REPO = "Chvddling/ChudPlayer"
    const val ZIP_NAME = "ChudPlayer-windows-x64.zip"

    private val props = Properties().apply {
        AppInfo::class.java.getResourceAsStream("/chudplayer/appinfo.properties")?.use { load(it) }
    }
    val version: String = props.getProperty("version", "dev")
    val extractor: String = props.getProperty("extractor", "?")
}

/** "1.0.12" > "1.0.9" */
fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

fun openInBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
        .onFailure { runCatching { ProcessBuilder("cmd", "/c", "start", "\"\"", url).start() } }
}

/**
 * Checks GitHub for a newer ChudPlayer release and installs it:
 * download the zip, unpack it, then a small batch script waits for the app to close,
 * copies the new files over the old ones and starts ChudPlayer again.
 */
class Updater(private val scope: CoroutineScope) {
    data class Release(val version: String, val zipUrl: String, val pageUrl: String, val notes: String)

    var available by mutableStateOf<Release?>(null); private set
    var status by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    private val json = Json { ignoreUnknownKeys = true }

    /** The folder holding ChudPlayer.exe, or null when running from Gradle (no self-update then). */
    val appDir: File? = runCatching {
        ProcessHandle.current().info().command().orElse(null)
            ?.let { File(it) }
            ?.takeIf { it.name.equals("ChudPlayer.exe", ignoreCase = true) }
            ?.parentFile
    }.getOrNull()

    fun check(manual: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            if (manual) status = "Checking for updates…"
            try {
                val latest = withContext(Dispatchers.IO) { fetchLatest() }
                when {
                    latest == null -> if (manual) status = "No releases found yet."
                    isNewer(latest.version, AppInfo.version) -> {
                        available = latest
                        status = "Update available: v${latest.version}"
                    }
                    else -> if (manual) status = "You have the latest version (v${AppInfo.version})."
                }
            } catch (e: Exception) {
                if (manual) status = "Update check failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun fetchLatest(): Release? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${AppInfo.REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ChudPlayer/${AppInfo.version}")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IllegalStateException("GitHub answered ${resp.code}")
            val o = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return null
            val page = o["html_url"]?.jsonPrimitive?.content ?: "https://github.com/${AppInfo.REPO}/releases"
            val notes = o["body"]?.jsonPrimitive?.content.orEmpty()
            val zip = o["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content == AppInfo.ZIP_NAME }
                ?.get("browser_download_url")?.jsonPrimitive?.content
                ?: return null
            return Release(tag.removePrefix("v"), zip, page, notes)
        }
    }

    /** Downloads and installs [available]. Calls [exitApp] when the app should close so the files can be replaced. */
    fun install(exitApp: () -> Unit) {
        val release = available ?: return
        val dir = appDir
        if (dir == null || !canWrite(dir)) {
            status = if (dir == null) "Self-update only works in the built app — opening the download page."
            else "No permission to update this folder — opening the download page."
            openInBrowser(release.pageUrl)
            return
        }
        if (busy) return
        scope.launch {
            busy = true
            try {
                val newAppFolder = withContext(Dispatchers.IO) { downloadAndUnpack(release) }
                val script = withContext(Dispatchers.IO) { writeScript(newAppFolder, dir) }
                status = "Restarting to finish the update…"
                ProcessBuilder(
                    "cmd", "/c", "start", "\"\"", "/min",
                    script.absolutePath, ProcessHandle.current().pid().toString(),
                ).start()
                exitApp()
            } catch (e: Exception) {
                e.printStackTrace()
                status = "Update failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun canWrite(dir: File): Boolean = runCatching {
        val probe = File(dir, ".chudplayer-write-test")
        probe.writeText("x"); probe.delete()
        true
    }.getOrDefault(false)

    private fun downloadAndUnpack(release: Release): File {
        val root = File(System.getProperty("java.io.tmpdir"), "chudplayer-update")
        root.deleteRecursively()
        root.mkdirs()
        val zip = File(root, AppInfo.ZIP_NAME)

        val req = Request.Builder().url(release.zipUrl).header("User-Agent", "ChudPlayer/${AppInfo.version}").build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Download failed (${resp.code})")
            val body = resp.body ?: throw IllegalStateException("Empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; status = "Downloading v${release.version}… $pct%" }
                        }
                    }
                }
            }
        }

        status = "Unpacking…"
        val unpacked = File(root, "unpacked").apply { mkdirs() }
        val rootPath = unpacked.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(unpacked, entry.name)
                if (!out.canonicalPath.startsWith(rootPath)) throw IllegalStateException("Bad zip entry ${entry.name}")
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
            }
        }
        // The zip contains a ChudPlayer folder; find the folder that holds ChudPlayer.exe
        return unpacked.walkTopDown().maxDepth(3)
            .firstOrNull { it.isFile && it.name.equals("ChudPlayer.exe", ignoreCase = true) }
            ?.parentFile
            ?: throw IllegalStateException("ChudPlayer.exe not found in the update")
    }

    private fun writeScript(newApp: File, appDir: File): File {
        val script = File(System.getProperty("java.io.tmpdir"), "chudplayer-update.bat")
        val text = """
            @echo off
            title ChudPlayer update
            set "PID=%~1"
            :wait
            tasklist /FI "PID eq %PID%" 2>nul | find "%PID%" >nul
            if not errorlevel 1 (
                timeout /t 1 /nobreak >nul
                goto wait
            )
            timeout /t 1 /nobreak >nul
            robocopy "${newApp.absolutePath}" "${appDir.absolutePath}" /MIR /R:5 /W:1 /NFL /NDL /NJH /NJS /NP >nul
            start "" "${File(appDir, "ChudPlayer.exe").absolutePath}"
            rmdir /s /q "${newApp.parentFile.parentFile.absolutePath}" >nul 2>&1
            (goto) 2>nul & del "%~f0"
        """.trimIndent().replace("\n", "\r\n")
        script.writeText(text)
        return script
    }
}
