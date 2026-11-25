package newpipe.desktop.data

import kotlinx.serialization.Serializable
import org.schabi.newpipe.extractor.stream.StreamInfo

/** A video as shown in lists (search, queue, history, related). Serializable so history can be saved. */
@Serializable
data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String = "",
    val uploaderUrl: String? = null,
    val durationSec: Long = -1,
    val thumbnail: String? = null,
    val viewCount: Long = -1,
    val uploadDate: String? = null,
    val isLive: Boolean = false,
    /** NewPipe service id: 0 = YouTube, 1 = SoundCloud. */
    val serviceId: Int = 0,
)

/** The sites we support. Ids are NewPipeExtractor's ServiceList ids. */
enum class Service(val id: Int, val label: String) {
    YOUTUBE(0, "YouTube"),
    SOUNDCLOUD(1, "SoundCloud"),
    /** Subsonic/Navidrome server (not a NewPipe service). */
    SQUIDIFY(100, "Squidify"),
    /** Music files on this PC. */
    LOCAL(200, "My Music");

    companion object {
        fun of(id: Int): Service = entries.firstOrNull { it.id == id } ?: YOUTUBE
    }
}

/** Full info for the video that is currently loaded in the player. */
class StreamDetails(
    /** NewPipe's info (YouTube/SoundCloud); null for Squidify. */
    val info: StreamInfo?,
    val item: VideoItem,
    val description: String,
    val likeCount: Long,
    val related: List<VideoItem>,
    /** Available video heights (e.g. 1080, 720 …), highest first. */
    val heights: List<Int>,
    /** A ready-made stream (Squidify). When set, it's played as-is. */
    val direct: PlaybackSource? = null,
)

enum class PlayMode { VIDEO, AUDIO }

/** What mpv should actually open. */
data class PlaybackSource(
    val mainUrl: String,
    /** Separate audio track (YouTube serves >360p video without audio). */
    val audioUrl: String?,
    val userAgent: String,
    /** mpv http-header-fields value, comma separated. */
    val headers: String,
    val label: String,
    /** false for audio-only content (SoundCloud) or audio mode. */
    val hasVideo: Boolean = true,
)

/** What a search (or a channel/playlist page) can return. */
sealed interface SearchItem {
    val url: String
}

data class VideoResult(val item: VideoItem) : SearchItem {
    override val url: String get() = item.url
}

/** A YouTube channel or a SoundCloud artist. */
data class ChannelRef(
    override val url: String,
    val name: String,
    val avatar: String?,
    val subscribers: Long = -1,
    val streamCount: Long = -1,
    val description: String = "",
    val serviceId: Int = 0,
) : SearchItem

/** A playlist on YouTube/SoundCloud (not one of the user's local playlists). */
data class PlaylistRef(
    override val url: String,
    val name: String,
    val uploader: String = "",
    val uploaderUrl: String? = null,
    val thumbnail: String? = null,
    val count: Long = -1,
    val serviceId: Int = 0,
) : SearchItem

enum class SearchKind { ITEMS, CHANNELS, PLAYLISTS }
