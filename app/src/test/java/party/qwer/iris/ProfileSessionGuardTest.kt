package party.qwer.iris

import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileSessionGuardTest {
    @Test fun lockedProfileDeniesUntilTheSameSessionUnlocks() {
        var unlocked = true
        val guard = ProfileSessionGuard({ unlocked }, { "same-owner-and-files" })
        unlocked = false
        assertThrows(IllegalStateException::class.java) { guard.requireAvailable() }
        unlocked = true
        guard.requireAvailable()
    }

    @Test fun changedOwnerFilesOrLoginSessionCannotBeReacceptedWithoutRestart() {
        for (changed in listOf("other-owner", "other-inode", "other-app-uid", "other-login")) {
            var session = "original"
            val guard = ProfileSessionGuard({ true }, { session })
            session = changed
            assertThrows(IllegalStateException::class.java) { guard.requireAvailable() }
            session = "original"
            assertThrows(IllegalStateException::class.java) { guard.requireAvailable() }
        }
    }

    @Test fun unavailableSnapshotRequiresRestartRatherThanTrustingAnOldHandle() {
        var available = true
        val guard = ProfileSessionGuard({ true }, { check(available); "original" })
        available = false
        assertThrows(IllegalStateException::class.java) { guard.requireAvailable() }
        available = true
        assertThrows(IllegalStateException::class.java) { guard.requireAvailable() }
    }

    @Test fun lockedProfileCannotEstablishAnInitialSession() {
        assertThrows(IllegalStateException::class.java) { ProfileSessionGuard({ false }, { "original" }) }
    }
}
