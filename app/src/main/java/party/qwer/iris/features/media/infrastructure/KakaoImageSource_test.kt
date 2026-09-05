package party.qwer.iris.features.media.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class KakaoImageSourceTest {
    @Test
    fun `grouped photos resolve exact index and reject mismatched arrays`() {
        val grouped = """{"imageUrls":["https://talk.kakaocdn.net/a","https://talk.kakaocdn.net/b"],"mtl":["image/png","image/jpeg"],"sl":[10,20]}"""
        val second = selectImageAttachment("27", grouped, 1)
        assertEquals("https://talk.kakaocdn.net/b", second.url)
        assertEquals("image/jpeg", second.contentType)
        assertEquals(20L, second.size)
        for (index in listOf(-1, 2, 30)) {
            assertFailsWith<IllegalArgumentException> { selectImageAttachment("27", grouped, index) }
        }
        assertFailsWith<IllegalArgumentException> { selectImageAttachment("2", grouped, 1) }
        assertFailsWith<IllegalArgumentException> { selectImageAttachment("12", grouped, 0) }
        assertFailsWith<IllegalArgumentException> { selectImageAttachment("27", grouped.replace("[10,20]", "[10]"), 0) }
    }

    @Test
    fun `accepts only JPEG PNG and WebP magic bytes`() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
        val webp = "RIFF0000WEBP".encodeToByteArray()

        assertEquals("image/jpeg", detectedImageType(jpeg))
        assertEquals("image/png", detectedImageType(png))
        assertEquals("image/webp", detectedImageType(webp))
        assertNull(detectedImageType("<svg/>".encodeToByteArray()))
    }
}
