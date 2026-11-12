package party.qwer.iris.features.media.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class KakaoImageSourceTest {
    @Test
    fun `OpenChat image bit uses the same attachment parser without accepting other types`() {
        val single = """{"url":"https://talk.kakaocdn.net/a","mt":"image/png","s":10}"""
        assertEquals(selectImageAttachment("2", single, 0), selectImageAttachment("16386", single, 0))
        val grouped = """{"imageUrls":["https://talk.kakaocdn.net/a"],"mtl":["image/png"],"sl":[10]}"""
        assertEquals(selectImageAttachment("27", grouped, 0), selectImageAttachment("16411", grouped, 0))
        for (type in listOf(null, "invalid", "16385", "32770", "-1")) {
            assertFailsWith<IllegalArgumentException> { selectImageAttachment(type, single, 0) }
        }
        assertFailsWith<IllegalArgumentException> { selectImageAttachment("16386", single, 1) }
    }

    @Test
    fun `Kakao JPG alias matches JPEG without allowing other formats`() {
        assertEquals("image/jpeg", canonicalImageType("image/jpg"))
        assertEquals("image/jpeg", canonicalImageType(" IMAGE/JPG; charset=binary "))
        assertEquals("image/jpeg", canonicalImageType("image/jpeg"))
        assertEquals("image/svg+xml", canonicalImageType("image/svg+xml"))
        val photo = selectImageAttachment("2", """{"url":"https://talk.kakaocdn.net/photo","mt":"image/jpg","s":4202877}""", 0)
        assertEquals("image/jpeg", canonicalImageType(photo.contentType))
        assertEquals(4202877L, photo.size)
    }

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
