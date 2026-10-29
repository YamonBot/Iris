package party.qwer.iris

/** Explicit daemon scope. An unavailable profile must never fall back to user 0. */
internal data class AndroidProfile(val userId: Int, val appUid: Int, val expectedBotId: Long) {
    val dataPath = "/data/user/$userId/com.kakao.talk/"
    val workPath = "/data/local/tmp/iris-users/$userId"
    val configPath = "$workPath/config.json"
    val tokenPath = "$workPath/tokens"
    val mediaPath = "/storage/emulated/$userId/Android/data/com.kakao.talk/files/iris"
    val port = 3000 + userId

    fun requireOwner(actualBotId: Long) {
        check(actualBotId == expectedBotId) { "KakaoTalk account does not match the configured profile owner" }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String>): AndroidProfile? {
            val raw = env["IRIS_ANDROID_USER_ID"] ?: return null
            require(raw.matches(Regex("[1-9][0-9]*"))) { "IRIS_ANDROID_USER_ID must identify a secondary profile" }
            val user = raw.toIntOrNull()
            require(user != null && user in 1..21473) { "IRIS_ANDROID_USER_ID is out of range" }
            val uid = env["KAKAOTALK_APP_UID"]?.toIntOrNull()
            require(uid != null && uid / 100000 == user && uid % 100000 >= 10000) {
                "KAKAOTALK_APP_UID must belong to the selected Android profile"
            }
            val bot = env["IRIS_EXPECTED_BOT_ID"]?.toLongOrNull()
            require(bot != null && bot > 0) { "IRIS_EXPECTED_BOT_ID is required for profile isolation" }
            return AndroidProfile(user, uid, bot).also { profile ->
                require(env["IRIS_CONFIG_PATH"] == null || env["IRIS_CONFIG_PATH"] == profile.configPath) {
                    "Profile configuration cannot use another scope's path"
                }
                require(env["IRIS_WORK_DIR"] == null || env["IRIS_WORK_DIR"] == profile.workPath) {
                    "Profile working files cannot use another scope's path"
                }
                require(env["IRIS_AUTH_TOKEN_FILE"] == null || env["IRIS_AUTH_TOKEN_FILE"] == profile.tokenPath) {
                    "Profile authentication cannot use another scope's token file"
                }
            }
        }
    }
}

internal object RuntimeProfile {
    val scope: AndroidProfile? by lazy { AndroidProfile.fromEnvironment(System.getenv()) }
}
