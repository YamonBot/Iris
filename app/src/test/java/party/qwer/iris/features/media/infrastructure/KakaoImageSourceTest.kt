package party.qwer.iris.features.media.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KakaoImageSourceTest {
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
