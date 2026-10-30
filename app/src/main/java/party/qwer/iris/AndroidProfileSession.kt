package party.qwer.iris

import android.system.Os
import java.io.File
import java.security.MessageDigest

/** Pin the attached databases and login session, without retaining raw login material. */
internal fun AndroidProfile.sessionGuard(database: KakaoDB): ProfileSessionGuard = ProfileSessionGuard(
    unlocked = { AndroidHiddenApi.isUserUnlocked(userId) },
    snapshot = {
        requireOwner(database.currentBotUserId())
        val root = File(dataPath)
        check(root.canonicalPath == dataPath.trimEnd('/')) { "Profile data path changed" }
        val files = listOf("databases/KakaoTalk.db", "databases/KakaoTalk2.db",
            "databases/multi_profile_database.db", "shared_prefs/KakaoTalk.hw.perferences.xml")
        val identity = files.map { relative ->
            val file = File(root, relative)
            check(file.canonicalPath == dataPath + relative) { "Profile file path changed" }
            val stat = Os.stat(file.path)
            check(stat.st_uid == appUid) { "Profile file owner changed" }
            // SharedPreferences atomically replaces XML for unrelated settings.
            // Pin its owner and selected login value, rather than its transient inode.
            if (relative.startsWith("databases/")) "${stat.st_dev}:${stat.st_ino}:${stat.st_uid}"
            else stat.st_uid.toString()
        }
        val preferences = File(root, files.last()).readText()
        val referer = Regex("""<string name="NotificationReferer">(.*?)</string>""")
            .find(preferences)?.groupValues?.get(1)
        check(!referer.isNullOrEmpty()) { "Profile login session is unavailable" }
        identity.joinToString(":") + ":" + MessageDigest.getInstance("SHA-256")
            .digest(referer.toByteArray()).joinToString("") { "%02x".format(it) }
    }
)
