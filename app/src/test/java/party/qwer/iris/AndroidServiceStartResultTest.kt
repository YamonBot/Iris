package party.qwer.iris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidServiceStartResultTest {
    @Test fun missingServiceIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            requireAndroidServiceStarted(null, null)
        }
    }

    @Test fun permissionDenialIsNotAnAcceptedDispatch() {
        val error = assertThrows(SecurityException::class.java) {
            requireAndroidServiceStarted("!", "example.permission.SEND")
        }
        assertEquals("Android service requires permission: example.permission.SEND", error.message)
    }

    @Test fun backgroundAndOtherNativeRejectionsArePreserved() {
        for (marker in listOf("!!", "?")) {
            val error = assertThrows(IllegalStateException::class.java) {
                requireAndroidServiceStarted(marker, "native rejection")
            }
            assertEquals("Android service start rejected: native rejection", error.message)
        }
    }

    @Test fun acceptedComponentRemainsAccepted() {
        requireAndroidServiceStarted(
            "com.kakao.talk", "com.kakao.talk.notification.NotificationActionService"
        )
    }
}
