package party.qwer.iris.features.security.infrastructure

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BearerTokenFileTest {
    @Test
    fun `accepts rotation overlap and rejects malformed headers`() {
        val file = File.createTempFile("iris-token", ".txt")
        file.writeText("old-token\nnew-token\n")
        try {
            val tokens = BearerTokenFile(file)
            assertTrue(tokens.accepts("Bearer old-token"))
            assertTrue(tokens.accepts("Bearer new-token"))
            assertFalse(tokens.accepts("Basic new-token"))
            assertFalse(tokens.accepts("Bearer wrong-token"))
        } finally {
            file.delete()
        }
    }
}
