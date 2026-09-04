package party.qwer.iris.features.media.infrastructure

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import party.qwer.iris.KakaoDB
import java.io.ByteArrayOutputStream
import java.net.URI

/** Validated image bytes resolved from one Kakao image chat-log row. */
data class KakaoImage(val contentType: String, val bytes: ByteArray)

/** Resolves only Kakao-hosted type-2 image attachments by database id. */
class KakaoImageSource(
    private val kakaoDB: KakaoDB,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .build(),
    private val maxBytes: Long = 10L * 1024 * 1024,
) {
    /** Fetch a validated Kakao image without accepting caller-provided URLs. */
    fun fetch(logId: Long): KakaoImage {
        val row = kakaoDB.findChatLog(logId) ?: throw NoSuchElementException("chat log not found")
        require(row["type"] == "2") { "chat log is not a supported image" }
        val attachment = JSONObject(row["attachment"] ?: "{}")
        val contentType = attachment.optString("mt")
        require(contentType.startsWith("image/")) { "attachment is not an image" }
        val declaredSize = attachment.optLong("s", -1)
        require(declaredSize in 0..maxBytes) { "image exceeds size limit" }

        val url = attachment.optString("url")
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "talk.kakaocdn.net") {
            "image source is not an allowed Kakao CDN host"
        }

        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Kakao image fetch failed: ${response.code}" }
            val body = response.body ?: error("Kakao image response is empty")
            val responseSize = body.contentLength()
            require(responseSize < 0 || responseSize <= maxBytes) { "image exceeds size limit" }
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "image exceeds size limit" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            return KakaoImage(contentType, bytes)
        }
    }
}
