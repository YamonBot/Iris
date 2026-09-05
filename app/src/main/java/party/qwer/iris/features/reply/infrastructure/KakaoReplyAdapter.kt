package party.qwer.iris.features.reply.infrastructure

import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.Base64
import party.qwer.iris.KakaoDB
import party.qwer.iris.Replier
import party.qwer.iris.features.reply.domain.KakaoReplyCommitProbe
import party.qwer.iris.features.reply.domain.KakaoReplySender
import party.qwer.iris.features.reply.domain.ReplyCommand
import party.qwer.iris.features.reply.domain.ReplyPayload

/** Executes outbound intents through the existing Kakao integration. */
class KakaoReplySenderAdapter(private val notificationReferer: String) : KakaoReplySender {
    override suspend fun send(command: ReplyCommand) {
        when (val payload = command.payload) {
            is ReplyPayload.Text -> Replier.sendMessageNow(
                notificationReferer,
                command.roomId,
                payload.value,
                command.threadId,
            )

            is ReplyPayload.Images -> {
                require(command.threadId == null) {
                    "threaded image replies are not supported"
                }
                Replier.sendImagesNow(
                    command.roomId,
                    payload.base64Values,
                )
            }
        }
    }
}

/** Confirms only the local Kakao database commit; it is not a delivery receipt. */
class KakaoReplyCommitProbeAdapter(
    private val kakaoDB: KakaoDB,
    private val timeoutMillis: Long = 10_000,
    private val pollingMillis: Long = 100,
) : KakaoReplyCommitProbe {
    override fun latestLogId(): Long = kakaoDB.latestChatLogId()

    override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long? {
        val expectedText = (command.payload as? ReplyPayload.Text)?.value
        val imageChecksums = (command.payload as? ReplyPayload.Images)?.base64Values?.map {
            MessageDigest.getInstance("SHA-1").digest(Base64.getDecoder().decode(it))
                .joinToString("") { byte -> "%02x".format(byte) }
        }
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val committed = if (expectedText != null) {
                kakaoDB.findOwnTextChatLogAfter(command.roomId, afterLogId, expectedText)
            } else {
                matchingImageCommit(imageChecksums.orEmpty(),
                    kakaoDB.findOwnImageChatLogsAfter(command.roomId, afterLogId))
            }
            if (committed != null) return committed
            delay(pollingMillis)
        }
        return null
    }
}
