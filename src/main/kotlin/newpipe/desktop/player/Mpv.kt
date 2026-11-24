package newpipe.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File

/**
 * Minimal JNA binding to libmpv's client API (client.h).
 * Get libmpv-2.dll from https://github.com/shinchiro/mpv-winbuild-cmake/releases (mpv-dev-x86_64-*.7z).
 */
@Suppress("FunctionName")
interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_command(ctx: Pointer, args: Array<String>): Int   // JNA appends the NULL terminator
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_error_string(error: Int): String

    companion object {
        private val names = listOf("mpv-2", "libmpv-2", "mpv", "libmpv")

        /** Folders searched for libmpv-2.dll. Call before anything touches JNA. */
        fun searchDirs(): List<String> = listOfNotNull(
            System.getProperty("compose.application.resources.dir"), // packaged app / gradlew run
            File("native-resources/windows").absolutePath,           // running from the project folder
            File("native").absolutePath,
            System.getProperty("user.dir"),
        ).filter { File(it).isDirectory }

        fun prepareSearchPath() {
            val dirs = searchDirs()
            for (name in names) for (d in dirs) NativeLibrary.addSearchPath(name, d)
        }

        val instance: MpvLib by lazy {
            var last: Throwable? = null
            for (name in names) {
                try {
                    return@lazy Native.load(name, MpvLib::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
                } catch (e: UnsatisfiedLinkError) {
                    last = e
                }
            }
            throw UnsatisfiedLinkError(
                "libmpv-2.dll not found. Put it in native-resources\\windows\\ (searched: ${searchDirs()}). Cause: ${last?.message}"
            )
        }
    }
}

sealed interface MpvEvent {
    data class Property(val name: String, val value: Any?) : MpvEvent
    data object FileLoaded : MpvEvent
    data class EndFile(val reason: Int, val error: Int) : MpvEvent
    data object Shutdown : MpvEvent
}

/**
 * One mpv instance. If [windowId] is given (an HWND), mpv draws into that window,
 * which is how the video is embedded in the Compose UI.
 */
class Mpv(windowId: Long?, ytdlPath: String?, private val listener: (MpvEvent) -> Unit) {
    private val lib = MpvLib.instance
    private val ctx: Pointer = lib.mpv_create() ?: throw IllegalStateException("mpv_create() failed")
    private val thread: Thread
    @Volatile private var alive = true

    init {
        windowId?.let { option("wid", it.toString()) }
        option("hwdec", "auto-safe")
        option("osc", "yes")                       // mpv's own on-screen controller over the video
        option("input-default-bindings", "yes")    // mpv keyboard/mouse shortcuts when the video has focus
        option("input-vo-keyboard", "yes")
        option("idle", "yes")
        option("force-window", if (windowId != null) "yes" else "no")
        option("keep-open", "no")
        option("audio-display", "no")
        option("ytdl", "yes")                      // yt-dlp fallback
        if (ytdlPath != null) option("script-opts", "ytdl_hook-ytdl_path=$ytdlPath")
        option("cache", "yes")
        option("demuxer-max-bytes", "150MiB")
        option("demuxer-readahead-secs", "20")
        option("network-timeout", "30")
        option("cursor-autohide", "1000")
        option("volume-max", "150")

        val rc = lib.mpv_initialize(ctx)
        if (rc < 0) {
            lib.mpv_terminate_destroy(ctx)
            throw IllegalStateException("mpv_initialize failed: ${lib.mpv_error_string(rc)}")
        }
        lib.mpv_request_log_messages(ctx, "warn")

        observe("pause", FORMAT_FLAG)
        observe("time-pos", FORMAT_DOUBLE)
        observe("duration", FORMAT_DOUBLE)
        observe("paused-for-cache", FORMAT_FLAG)
        observe("volume", FORMAT_DOUBLE)
        observe("fullscreen", FORMAT_FLAG)
        observe("height", FORMAT_INT64)

        thread = Thread(::eventLoop, "mpv-events").apply { isDaemon = true; start() }
    }

    fun command(vararg args: String): Int {
        if (!alive) return -1
        val rc = lib.mpv_command(ctx, arrayOf(*args))
        if (rc < 0) System.err.println("[mpv] command ${args.firstOrNull()} failed: ${lib.mpv_error_string(rc)}")
        return rc
    }

    fun setProperty(name: String, value: String): Int {
        if (!alive) return -1
        val rc = lib.mpv_set_property_string(ctx, name, value)
        if (rc < 0) System.err.println("[mpv] set $name=$value failed: ${lib.mpv_error_string(rc)}")
        return rc
    }

    /** Stops mpv. Must run before the window that holds the video is destroyed. */
    fun destroy() {
        if (!alive) return
        lib.mpv_command(ctx, arrayOf("quit"))
        alive = false
        thread.join(3000)
    }

    private fun option(name: String, value: String) {
        val rc = lib.mpv_set_option_string(ctx, name, value)
        if (rc < 0) System.err.println("[mpv] option $name=$value: ${lib.mpv_error_string(rc)}")
    }

    private fun observe(name: String, format: Int) {
        lib.mpv_observe_property(ctx, 0L, name, format)
    }

    // Struct layouts (64-bit):
    //   mpv_event          { int event_id @0; int error @4; uint64 reply_userdata @8; void* data @16 }
    //   mpv_event_property { char* name @0; int format @8; void* data @16 }
    //   mpv_event_end_file { int reason @0; int error @4; ... }
    //   mpv_event_log_message { char* prefix @0; char* level @8; char* text @16; ... }
    private fun eventLoop() {
        while (true) {
            val ev = lib.mpv_wait_event(ctx, -1.0)
            val id = ev.getInt(0)
            if (id == EVENT_SHUTDOWN) {
                runCatching { listener(MpvEvent.Shutdown) }
                break
            }
            try {
                when (id) {
                    EVENT_PROPERTY_CHANGE -> ev.getPointer(16)?.let { p ->
                        val name = p.getPointer(0)?.getString(0, "UTF-8") ?: return@let
                        val format = p.getInt(8)
                        val data = p.getPointer(16)
                        val value: Any? = if (data == null) null else when (format) {
                            FORMAT_FLAG -> data.getInt(0) != 0
                            FORMAT_INT64 -> data.getLong(0)
                            FORMAT_DOUBLE -> data.getDouble(0)
                            FORMAT_STRING -> data.getPointer(0)?.getString(0, "UTF-8")
                            else -> null
                        }
                        listener(MpvEvent.Property(name, value))
                    }
                    EVENT_FILE_LOADED -> listener(MpvEvent.FileLoaded)
                    EVENT_END_FILE -> {
                        val d = ev.getPointer(16)
                        listener(MpvEvent.EndFile(d?.getInt(0) ?: -1, d?.getInt(4) ?: 0))
                    }
                    EVENT_LOG_MESSAGE -> ev.getPointer(16)?.let { d ->
                        val prefix = d.getPointer(0)?.getString(0, "UTF-8")
                        val text = d.getPointer(16)?.getString(0, "UTF-8")?.trimEnd()
                        println("[mpv/$prefix] $text")
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        lib.mpv_terminate_destroy(ctx)
    }

    companion object {
        const val FORMAT_STRING = 1
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5

        const val EVENT_SHUTDOWN = 1
        const val EVENT_LOG_MESSAGE = 2
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_PROPERTY_CHANGE = 22

        const val END_EOF = 0
        const val END_STOP = 2
        const val END_ERROR = 4
    }
}
