package party.qwer.iris

import org.junit.Assert.*
import org.junit.Test

class AndroidProfileTest {
    @Test fun authenticationProjectionCannotUseCompanyTokens() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidProfile.fromEnvironment(personal + ("IRIS_AUTH_TOKEN_FILE" to "/data/local/tmp/iris.tokens"))
        }
        assertEquals("/data/local/tmp/iris-users/10/tokens",
            AndroidProfile.fromEnvironment(personal)!!.tokenPath)
    }
    private val personal = mapOf(
        "IRIS_ANDROID_USER_ID" to "10",
        "KAKAOTALK_APP_UID" to "1010089",
        "IRIS_EXPECTED_BOT_ID" to "123456789"
    )

    @Test fun legacyDaemonKeepsItsExistingScope() {
        assertNull(AndroidProfile.fromEnvironment(emptyMap()))
        assertNull(AndroidProfile.fromEnvironment(mapOf("KAKAOTALK_APP_UID" to "10089")))
    }

    @Test fun secondaryProfileScopesEveryOwnedPathAndPort() {
        val profile = AndroidProfile.fromEnvironment(personal)!!
        assertEquals(10, profile.userId)
        assertEquals(1010089, profile.appUid)
        assertEquals("/data/user/10/com.kakao.talk/", profile.dataPath)
        assertEquals("/data/local/tmp/iris-users/10/config.json", profile.configPath)
        assertEquals("/data/local/tmp/iris-users/10", profile.workPath)
        assertEquals("/storage/emulated/10/Android/data/com.kakao.talk/files/iris", profile.mediaPath)
        assertEquals(3010, profile.port)
    }

    @Test fun invalidProfileCannotFallBackToCompany() {
        for (value in listOf("", "0", "-1", "current", "10/../0", "21474", "9999999999999")) {
            assertThrows(IllegalArgumentException::class.java) {
                AndroidProfile.fromEnvironment(personal + ("IRIS_ANDROID_USER_ID" to value))
            }
        }
    }

    @Test fun companyOrForeignUidCannotBeUsedByPersonalProfile() {
        for (uid in listOf("10089", "1110089", "1000000", "-1", "broken")) {
            assertThrows(IllegalArgumentException::class.java) {
                AndroidProfile.fromEnvironment(personal + ("KAKAOTALK_APP_UID" to uid))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidProfile.fromEnvironment(personal - "KAKAOTALK_APP_UID")
        }
    }

    @Test fun profileOwnerMustBeKnownAndMatch() {
        val profile = AndroidProfile.fromEnvironment(personal)!!
        profile.requireOwner(123456789)
        for (other in listOf(0L, 333333333L, 987654321L)) {
            assertThrows(IllegalStateException::class.java) { profile.requireOwner(other) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidProfile.fromEnvironment(personal - "IRIS_EXPECTED_BOT_ID")
        }
    }

    @Test fun legacyConfigAndWorkDirectoriesAreRejected() {
        for ((key, path) in listOf(
            "IRIS_CONFIG_PATH" to "/data/local/tmp/config.json",
            "IRIS_WORK_DIR" to "/data/local/tmp/Iris.work"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                AndroidProfile.fromEnvironment(personal + (key to path))
            }
        }
    }
}
