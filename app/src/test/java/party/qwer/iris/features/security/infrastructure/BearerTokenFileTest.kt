package party.qwer.iris.features.security.infrastructure

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class BearerTokenFileTest {
    @Test fun tokenRotationAndMissingProjectionFailClosed() {
        val directory = Files.createTempDirectory("iris-profile-auth-").toFile()
        try {
            val file = directory.resolve("tokens")
            assertThrows(IllegalArgumentException::class.java) { BearerTokenFile(file) }
            file.writeText("synthetic-owner-token\nsynthetic-previous-token\n")
            val guard = BearerTokenFile(file)
            assertFalse(guard.accepts(null))
            assertFalse(guard.accepts("Bearer company-token"))
            assertTrue(guard.accepts("Bearer synthetic-owner-token"))
            assertTrue(guard.accepts("Bearer synthetic-previous-token"))
            file.writeText("synthetic-new-token\n")
            assertFalse(guard.accepts("Bearer synthetic-owner-token"))
            assertTrue(guard.accepts("Bearer synthetic-new-token"))
            file.delete()
            assertFalse(guard.accepts("Bearer synthetic-new-token"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
