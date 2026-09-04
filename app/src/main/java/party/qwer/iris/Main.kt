// SendMsg : ye-seola/go-kdb
// Kakaodecrypt : jiru/kakaodecrypt
package party.qwer.iris

import kotlinx.coroutines.flow.MutableSharedFlow
import party.qwer.iris.features.media.infrastructure.KakaoImageSource
import party.qwer.iris.features.reply.application.ReplyDispatcher
import party.qwer.iris.features.reply.infrastructure.FileReplyLedger
import party.qwer.iris.features.reply.infrastructure.KakaoReplyCommitProbeAdapter
import party.qwer.iris.features.reply.infrastructure.KakaoReplySenderAdapter
import party.qwer.iris.features.security.infrastructure.BearerTokenFile
import java.io.File
import java.util.concurrent.TimeUnit

const val IMAGE_DIR_PATH: String = "/sdcard/Android/data/com.kakao.talk/files"

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val wsEventFlow = MutableSharedFlow<String>()

                val notificationReferer = readNotificationReferer()

                val kakaoDb = KakaoDB()
                val observerHelper = ObserverHelper(kakaoDb, wsEventFlow)

                val dbObserver = DBObserver(kakaoDb, observerHelper)
                dbObserver.startPolling()
                println("DBObserver started")

                val notificationPoller = NotificationPoller()
                notificationPoller.startPolling()
                println("Notification Poller started")

                val imageDeleter = ImageDeleter(IMAGE_DIR_PATH, TimeUnit.HOURS.toMillis(1))
                imageDeleter.startDeletion()
                println("ImageDeleter started, and will delete images older than 1 hour.")

                val authTokenPath = System.getenv("IRIS_AUTH_TOKEN_FILE")
                    ?: error("IRIS_AUTH_TOKEN_FILE must point to a protected token file")
                val replyLedgerPath = System.getenv("IRIS_REPLY_LEDGER_FILE")
                    ?: "/data/local/tmp/iris-reply-ledger.jsonl"
                val replyDispatcher = ReplyDispatcher(
                    sender = KakaoReplySenderAdapter(notificationReferer),
                    commitProbe = KakaoReplyCommitProbeAdapter(kakaoDb),
                    ledger = FileReplyLedger(File(replyLedgerPath)),
                    sendDelayMillis = { Configurable.messageSendRate.toLong() },
                )
                val irisServer = IrisServer(
                    kakaoDb,
                    dbObserver,
                    observerHelper,
                    wsEventFlow,
                    BearerTokenFile(File(authTokenPath)),
                    replyDispatcher,
                    KakaoImageSource(kakaoDb),
                )
                irisServer.startServer()
                println("Iris Server started")

                kakaoDb.closeConnection()
            } catch (e: Exception) {
                System.err.println("Iris Error")
                e.printStackTrace()
                System.exit(1)
            }
        }

        private fun readNotificationReferer(): String {
            val appPath = PathUtils.getAppPath()
            val prefsFile = File("${appPath}shared_prefs/KakaoTalk.hw.perferences.xml")
            val data = prefsFile.bufferedReader().use {
                it.readText()
            }
            val regex = Regex("""<string name="NotificationReferer">(.*?)</string>""")
            val match = regex.find(data) ?: throw Exception("failed to extract referer from data")

            val referer =
                match.groups[1]?.value ?: throw Exception("failed to extract referer from data")

            return referer
        }
    }
}
