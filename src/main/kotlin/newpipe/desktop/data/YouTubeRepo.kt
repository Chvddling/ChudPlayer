package newpipe.desktop.data

import newpipe.desktop.net.DownloaderImpl
import newpipe.desktop.net.Http
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale
import org.schabi.newpipe.extractor.Image as NpImage

object NewPipeSetup {
    fun init() {
        val locale = Locale.getDefault()
        val localization = runCatching { Localization.fromLocale(locale) }.getOrDefault(Localization.DEFAULT)
        val country = locale.country.takeIf { it.length == 2 }?.let { ContentCountry(it) } ?: ContentCountry.DEFAULT
        NewPipe.init(DownloaderImpl.instance, localization, country)
    }
}

class SearchPage(
    val handler: SearchQueryHandler,
    val items: List<VideoItem>,
    val next: Page?,
    val service: Service = Service.YOUTUBE,
)

class NoStreamsException(msg: String) : Exception(msg)

/** All talking to NewPipeExtractor lives here. Every function blocks: call it from Dispatchers.IO. */
class YouTubeRepo {
    private fun np(service: Service): StreamingService = when (service) {
        Service.YOUTUBE -> ServiceList.YouTube
        Service.SOUNDCLOUD -> ServiceList.SoundCloud
    }

    /** Which supported service a link belongs to (YouTube or SoundCloud), or null. */
    fun serviceOfUrl(text: String): Service? = runCatching {
        val url = text.trim()
        val s = NewPipe.getServiceByUrl(url)
        val supported = Service.entries.firstOrNull { it.id == s.serviceId }
        if (supported != null && s.streamLHFactory.acceptUrl(url)) supported else null
    }.getOrNull()

    fun isVideoUrl(text: String): Boolean = serviceOfUrl(text) != null

    fun search(query: String, service: Service = Service.YOUTUBE): SearchPage {
        val s = np(service)
        // YoutubeSearchQueryHandlerFactory.VIDEOS / SoundcloudSearchQueryHandlerFactory.TRACKS
        val filter = if (service == Service.SOUNDCLOUD) "tracks" else "videos"
        val handler = s.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(s, handler)
        return SearchPage(handler, info.relatedItems.toVideoItems(), info.nextPage.validOrNull(), service)
    }

    fun searchMore(previous: SearchPage): SearchPage {
        val page = previous.next ?: return SearchPage(previous.handler, emptyList(), null, previous.service)
        val res = SearchInfo.getMoreItems(np(previous.service), previous.handler, page)
        return SearchPage(previous.handler, res.items.toVideoItems(), res.nextPage.validOrNull(), previous.service)
    }

    fun streamDetails(url: String): StreamDetails {
        val service = NewPipe.getServiceByUrl(url)
        val info = StreamInfo.getInfo(service, url)
        val item = VideoItem(
            url = info.url,
            title = info.name,
            uploader = info.uploaderName.orEmpty(),
            uploaderUrl = info.uploaderUrl,
            durationSec = info.duration,
            thumbnail = bestThumbnail(info.thumbnails, target = 720),
            viewCount = info.viewCount,
            uploadDate = info.textualUploadDate,
            isLive = info.streamType.isLive(),
            serviceId = info.serviceId,
        )
        val related = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
        val heights = (info.videoOnlyStreams + info.videoStreams)
            .filter { it.isPlayable() && it.height > 0 }
            .map { it.height }
            .distinct()
            .sortedDescending()
        return StreamDetails(
            info = info,
            item = item,
            description = cleanDescription(info.description?.content),
            likeCount = info.likeCount,
            related = related,
            heights = heights,
        )
    }

    /**
     * Picks the streams mpv should play.
     * VIDEO: best video-only stream <= maxHeight + best audio (mpv merges them), else a muxed stream.
     * AUDIO: best audio stream only (this is the "background player").
     */
    fun selectSources(info: StreamInfo, mode: PlayMode, maxHeight: Int): PlaybackSource {
        if (info.streamType.isLive()) {
            val hls = info.hlsUrl
            if (!hls.isNullOrEmpty()) return source(hls, null, "Live")
        }

        // Audio may be a direct file or HLS (SoundCloud mostly serves HLS). mpv plays both.
        val audio = info.audioStreams
            .filter { it.isUrl && !it.content.isNullOrEmpty() && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS) }
            .sortedWith(
                compareBy<AudioStream> { if (it.audioTrackType == null || it.audioTrackType?.name == "ORIGINAL") 0 else 1 }
                    .thenBy { if (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP) 0 else 1 }
                    .thenByDescending { maxOf(it.averageBitrate, it.bitrate) }
            )
            .firstOrNull()

        val hasAnyVideo = (info.videoStreams + info.videoOnlyStreams).any { it.isPlayable() }

        val muxed = info.videoStreams
            .filter { it.isPlayable() && it.height in 1..maxHeight }
            .maxByOrNull { it.height }

        if (mode == PlayMode.AUDIO || !hasAnyVideo) {
            if (audio != null) return source(audio.content, null, audioLabel(audio), hasVideo = false)
            if (muxed != null) return source(muxed.content, null, "Audio (from ${muxed.height}p)", hasVideo = false)
        }

        val videoOnly = info.videoOnlyStreams
            .filter { it.isPlayable() && it.height in 1..maxHeight }
            .sortedWith(
                compareByDescending<VideoStream> { it.height }
                    .thenByDescending { it.fps }
                    .thenBy { codecRank(it.codec) }
            )
            .firstOrNull()

        if (videoOnly != null && audio != null && (muxed == null || videoOnly.height > muxed.height)) {
            return source(videoOnly.content, audio.content, "${videoOnly.height}p${if (videoOnly.fps > 30) videoOnly.fps.toString() else ""}")
        }
        if (muxed != null) return source(muxed.content, null, "${muxed.height}p")

        // Last resorts: manifests (mpv understands both).
        info.dashMpdUrl?.takeIf { it.isNotEmpty() }?.let { return source(it, null, "DASH") }
        info.hlsUrl?.takeIf { it.isNotEmpty() }?.let { return source(it, null, "HLS") }
        throw NoStreamsException("No playable streams found for this video.")
    }

    // ---------- helpers ----------

    private fun source(url: String, audio: String?, label: String, hasVideo: Boolean = true): PlaybackSource {
        val (ua, headers) = httpFor(url)
        return PlaybackSource(url, audio, ua, headers, label, hasVideo)
    }

    private fun audioLabel(a: AudioStream): String {
        val kbps = maxOf(a.averageBitrate, a.bitrate)
        val fmt = a.format?.suffix?.uppercase() ?: ""
        return listOf("Audio", fmt, if (kbps > 0) "$kbps kbps" else "").filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * YouTube stream URLs are tied to the client that requested them (c=ANDROID, c=IOS, c=WEB …).
     * The player must send a matching User-Agent or YouTube answers 403.
     */
    private fun httpFor(url: String): Pair<String, String> {
        val client = Regex("[?&]c=([A-Za-z0-9_]+)").find(url)?.groupValues?.get(1)?.uppercase()
        return when {
            client == null -> Http.USER_AGENT to ""
            client.startsWith("ANDROID") -> UserAgents.android to ""
            client.startsWith("IOS") -> UserAgents.ios to ""
            else -> Http.USER_AGENT to "Origin: https://www.youtube.com,Referer: https://www.youtube.com/"
        }
    }

    private fun codecRank(codec: String?): Int = when {
        codec == null -> 3
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 0
        codec.startsWith("avc") -> 1
        codec.startsWith("av01") -> 2 // AV1 is heavy on older GPUs
        else -> 3
    }

    private fun org.schabi.newpipe.extractor.stream.Stream.isPlayable(): Boolean =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !content.isNullOrEmpty()

    private fun Page?.validOrNull(): Page? = this?.takeIf { Page.isValid(it) }

    private fun List<InfoItem>.toVideoItems(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }

    private fun StreamType.isLive() = this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name,
        uploader = uploaderName.orEmpty(),
        uploaderUrl = uploaderUrl,
        durationSec = duration,
        thumbnail = bestThumbnail(thumbnails, target = 360),
        viewCount = viewCount,
        uploadDate = textualUploadDate,
        isLive = streamType.isLive(),
        serviceId = serviceId,
    )

    private fun bestThumbnail(images: List<NpImage>?, target: Int): String? {
        if (images.isNullOrEmpty()) return null
        val known = images.filter { it.height > 0 }
        if (known.isEmpty()) return images.last().url
        return (known.filter { it.height <= target }.maxByOrNull { it.height } ?: known.minByOrNull { it.height })?.url
    }

    private fun cleanDescription(html: String?): String = html.orEmpty()
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .trim()
}

/** User agents for YouTube's mobile clients; taken from the extractor when possible so they stay in sync. */
object UserAgents {
    val android: String by lazy {
        fromExtractor("getAndroidUserAgent") ?: "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
    }
    val ios: String by lazy {
        fromExtractor("getIosUserAgent") ?: "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    }

    private fun fromExtractor(method: String): String? {
        val classes = listOf(
            "org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper",
            "org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper",
        )
        for (cls in classes) {
            val result = runCatching {
                val m = Class.forName(cls).methods.firstOrNull { it.name == method && it.parameterCount == 1 }
                m?.invoke(null, *arrayOf<Any?>(null)) as? String
            }.getOrNull()
            if (!result.isNullOrEmpty()) return result
        }
        return null
    }
}
