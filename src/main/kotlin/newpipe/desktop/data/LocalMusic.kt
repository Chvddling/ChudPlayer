package newpipe.desktop.data

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The local music source: folders on the user's drives, audio files played by mpv.
 * Local songs use file: URLs; the "channel" of a local song is its folder (folder:<path>).
 */
object LocalMusic {
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "flac", "ogg", "oga", "opus", "m4a", "m4b", "aac", "alac", "wma",
        "aiff", "aif", "aifc", "ape", "wv", "mka", "mpc", "dsf", "dff", "tta", "ac3", "mp2",
    )
    private val COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart", "albumartsmall")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")
    const val FOLDER_PREFIX = "folder:"
    const val COVER_PREFIX = "cover:"

    init {
        // jaudiotagger is very chatty
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    data class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: String?,
        val track: Int?,
        val durationSec: Long,
    )

    private val tagCache = ConcurrentHashMap<String, Tags>()

    fun isAudio(f: File) = f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS

    fun defaultMusicFolder(): File? =
        File(System.getProperty("user.home"), "Music").takeIf { it.isDirectory }

    /** Subfolders, sorted like Explorer (case-insensitive), hidden ones skipped. */
    fun subfolders(dir: File): List<File> =
        (dir.listFiles { f -> f.isDirectory && !f.isHidden && !f.name.startsWith(".") } ?: emptyArray())
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun audioFiles(dir: File): List<File> =
        (dir.listFiles { f -> isAudio(f) } ?: emptyArray())
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun isLocal(url: String) = url.startsWith("file:")

    fun fileOf(url: String): File? = if (!isLocal(url)) null else runCatching { File(URI(url)) }.getOrNull()

    fun folderUrl(dir: File) = FOLDER_PREFIX + dir.absolutePath
    fun folderOf(url: String?): File? = url?.takeIf { it.startsWith(FOLDER_PREFIX) }?.let { File(it.removePrefix(FOLDER_PREFIX)) }

    /** Reads title/artist/album/… (cached). Never throws: unreadable files just get no tags. */
    fun tags(f: File): Tags {
        val key = f.absolutePath + "|" + f.lastModified()
        tagCache[key]?.let { return it }
        val t = runCatching {
            val af = AudioFileIO.read(f)
            val tag = af.tag
            fun field(k: FieldKey) = runCatching { tag?.getFirst(k) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            Tags(
                title = field(FieldKey.TITLE),
                artist = field(FieldKey.ARTIST) ?: field(FieldKey.ALBUM_ARTIST),
                album = field(FieldKey.ALBUM),
                year = field(FieldKey.YEAR)?.take(4),
                track = field(FieldKey.TRACK)?.substringBefore('/')?.trim()?.toIntOrNull(),
                durationSec = af.audioHeader?.trackLength?.toLong() ?: -1,
            )
        }.getOrElse { Tags(null, null, null, null, null, -1) }
        tagCache[key] = t
        if (tagCache.size > 20_000) tagCache.clear()
        return t
    }

    /** A list item for a file; pass [tags] = null for a quick, name-only item. */
    fun item(f: File, tags: Tags?): VideoItem = VideoItem(
        url = f.toURI().toString(),
        title = tags?.title ?: f.nameWithoutExtension,
        uploader = tags?.artist ?: f.parentFile?.name.orEmpty(),
        uploaderUrl = f.parentFile?.let { folderUrl(it) },
        durationSec = tags?.durationSec ?: -1,
        thumbnail = COVER_PREFIX + f.absolutePath,
        uploadDate = listOfNotNull(tags?.album, tags?.year).joinToString(" · ").ifEmpty { null },
        serviceId = Service.LOCAL.id,
    )

    /** Album art: embedded in the file, else cover.jpg / folder.jpg / … next to it. */
    fun cover(f: File): ByteArray? {
        val embedded = runCatching { AudioFileIO.read(f).tag?.firstArtwork?.binaryData }.getOrNull()
        if (embedded != null && embedded.isNotEmpty()) return embedded
        return folderCover(f.parentFile)
    }

    fun folderCover(dir: File?): ByteArray? {
        val images = dir?.listFiles { x -> x.isFile && x.extension.lowercase() in IMAGE_EXTENSIONS } ?: return null
        val pick = images.firstOrNull { it.nameWithoutExtension.lowercase() in COVER_NAMES }
            ?: images.firstOrNull { it.nameWithoutExtension.lowercase().startsWith("albumart") }
            ?: images.singleOrNull()
        return pick?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    /** What the player needs to play a local file; "related" = the rest of its folder. */
    fun details(f: File): StreamDetails {
        if (!f.isFile) throw ContentMissingException("File not found: ${f.absolutePath}")
        val t = tags(f)
        val item = item(f, t)
        val folder = f.parentFile?.let { audioFiles(it) }.orEmpty()
        val i = folder.indexOfFirst { it.absolutePath == f.absolutePath }
        val related = (if (i >= 0) folder.drop(i + 1) + folder.take(i) else folder).map { item(it, null) }
        val text = listOfNotNull(
            t.artist?.let { "Artist: $it" },
            t.album?.let { "Album: $it" + (t.year?.let { y -> " ($y)" } ?: "") },
            "File: ${f.absolutePath}",
        ).joinToString("\n")
        return StreamDetails(
            info = null,
            item = item,
            description = text,
            likeCount = -1,
            related = related,
            heights = emptyList(),
            direct = PlaybackSource(f.absolutePath, null, "", "", "Local · ${f.extension.uppercase()}", hasVideo = false),
        )
    }
}

class ContentMissingException(msg: String) : Exception(msg)
