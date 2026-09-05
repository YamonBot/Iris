package party.qwer.iris.features.media.infrastructure

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import party.qwer.iris.KakaoDB
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_IMAGE_BYTES = 10L * 1024 * 1024
private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")

/** Detect the small raster allowlist from magic bytes, never from MIME alone. */
internal fun detectedImageType(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xff.toByte() &&
        bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
    bytes.size >= 8 && bytes.sliceArray(0..7).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    ) -> "image/png"
    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
        bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
    else -> null
}

/** Validated image bytes resolved from one Kakao image chat-log row. */
data class KakaoImage(val contentType: String, val bytes: ByteArray)

/** Normalize one indexed attachment without accepting a URL from the caller. */
internal fun selectImageAttachment(type: String?, attachment: JSONObject, index: Int): JSONObject {
    require(index in 0..29) { "invalid image index" }
    if (type == "2") {
        require(index == 0) { "single image index must be zero" }
        return attachment
    }
    require(type == "27") { "chat log is not a supported image" }
    val urls = attachment.optJSONArray("imageUrls")
        ?: throw IllegalArgumentException("missing grouped images")
    val types = attachment.optJSONArray("mtl")
        ?: throw IllegalArgumentException("missing grouped image types")
    val sizes = attachment.optJSONArray("sl")
        ?: throw IllegalArgumentException("missing grouped image sizes")
    require(urls.length() in 1..30 && index < urls.length() &&
        types.length() == urls.length() && sizes.length() == urls.length()) {
        "invalid grouped image arrays or index"
    }
    return JSONObject().put("url", urls.optString(index))
        .put("mt", types.optString(index)).put("s", sizes.optLong(index, -1))
}

/** Resolves Kakao-hosted single/grouped image attachments by database id. */
class KakaoImageSource(
    private val kakaoDB: KakaoDB,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val maxBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
) {
    /** Fetch a validated Kakao image without accepting caller-provided URLs. */
    fun fetch(logId: Long, index: Int = 0): KakaoImage {
        val row = kakaoDB.findChatLog(logId) ?: throw NoSuchElementException("chat log not found")
        val attachment = selectImageAttachment(row["type"], JSONObject(row["attachment"] ?: "{}"), index)
        val contentType = attachment.optString("mt").lowercase()
        require(contentType in ALLOWED_IMAGE_TYPES) { "attachment is not a supported image" }
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
            val responseType = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
            require(responseType == null || responseType == contentType) {
                "Kakao image content type does not match its attachment"
            }
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
            require(detectedImageType(bytes) == contentType) {
                "Kakao image bytes do not match the declared type"
            }
            return KakaoImage(contentType, bytes)
        }
    }
}
