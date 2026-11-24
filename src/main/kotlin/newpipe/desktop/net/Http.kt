package newpipe.desktop.net

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

object Http {
    /** Same kind of desktop Firefox UA the Android app sends. */
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * The bridge between NewPipeExtractor and the network.
 * Desktop equivalent of NewPipe's DownloaderImpl (Android).
 */
class DownloaderImpl private constructor() : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        var body = request.dataToSend()?.toRequestBody()
        if (body == null && (method == "POST" || method == "PUT")) body = ByteArray(0).toRequestBody()

        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(method, body)
            .header("User-Agent", Http.USER_AGENT)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (value in values) builder.addHeader(name, value)
        }

        Http.client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }
            val text = resp.body?.string()
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                text,
                resp.request.url.toString(),
            )
        }
    }

    companion object {
        val instance: DownloaderImpl by lazy { DownloaderImpl() }
    }
}
