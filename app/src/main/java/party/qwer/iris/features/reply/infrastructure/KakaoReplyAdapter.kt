package party.qwer.iris.features.reply.infrastructure

import kotlinx.coroutines.delay
import party.qwer.iris.KakaoDB
import party.qwer.iris.RuntimeProfile
import party.qwer.iris.Replier
import party.qwer.iris.features.reply.domain.KakaoReplyCommitProbe
import party.qwer.iris.features.reply.domain.KakaoReplySender
import party.qwer.iris.features.reply.domain.ReplyCommand
import party.qwer.iris.features.reply.domain.ReplyPayload
import party.qwer.iris.features.reply.domain.ReplyCommitEvidence

/** Executes outbound intents through the existing Kakao integration. */
class KakaoReplySenderAdapter(private val notificationReferer: String) : KakaoReplySender {
    override suspend fun send(command: ReplyCommand) {
        RuntimeProfile.requireAvailable()
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

    override fun findOwnRow(evidence: ReplyCommitEvidence, afterLogId: Long): Long? {
        RuntimeProfile.requireAvailable()
        return when (evidence.kind) {
            "text" -> kakaoDB.findOwnTextHashAfter(evidence.roomId, afterLogId, evidence.hashes.single())
            "images" -> matchingImageCommit(evidence.hashes,
                kakaoDB.findOwnImageChatLogsAfter(evidence.roomId, afterLogId))
            else -> null
        }
    }

    override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long? {
        val evidence = command.commitEvidence()
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            RuntimeProfile.requireAvailable()
            val committed = findOwnRow(evidence, afterLogId)
            if (committed != null) return committed
            delay(pollingMillis)
        }
        return null
    }
}
