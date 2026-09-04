package party.qwer.iris.features.reply.domain

import java.security.MessageDigest

/** Stable caller-owned identity for an outbound side effect. */
@JvmInline
value class ReplyRequestId(val value: String) {
    init {
        require(value.matches(Regex("[A-Za-z0-9._:-]{8,128}"))) {
            "request_id must be 8-128 safe ASCII characters"
        }
    }
}

/** Framework-free outbound payload. */
sealed interface ReplyPayload {
    data class Text(val value: String) : ReplyPayload
    data class Images(val base64Values: List<String>) : ReplyPayload
}

/** One ordered outbound Kakao command. */
data class ReplyCommand(
    val requestId: ReplyRequestId,
    val roomId: Long,
    val threadId: Long?,
    val payload: ReplyPayload,
) {
    /** Stable payload fingerprint used to reject request-id reuse. */
    fun fingerprint(): String {
        val payloadText = when (payload) {
            is ReplyPayload.Text -> "text:${payload.value}"
            is ReplyPayload.Images -> "images:${payload.base64Values.joinToString(":") { sha256(it) }}"
        }
        return sha256("$roomId|$threadId|$payloadText")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/** Evidence-backed state of an outbound reply. */
enum class ReplyStatus(val wireValue: String) {
    QUEUED("queued"),
    PROCESSING("processing"),
    KAKAO_DB_COMMITTED("kakao_db_committed"),
    KAKAO_DB_UNCONFIRMED("kakao_db_unconfirmed"),
    SEND_FAILED("send_failed"),
}

/** Durable reply result. */
data class ReplyReceipt(
    val requestId: ReplyRequestId,
    val status: ReplyStatus,
    val kakaoLogId: Long? = null,
    val duplicate: Boolean = false,
    val message: String? = null,
) {
    val success: Boolean get() = status == ReplyStatus.KAKAO_DB_COMMITTED
}

/** Persisted idempotency record. */
data class ReplyRecord(
    val requestId: ReplyRequestId,
    val fingerprint: String,
    val status: ReplyStatus,
    val baselineLogId: Long? = null,
    val kakaoLogId: Long? = null,
    val message: String? = null,
)

/** Durable request-id repository. */
interface ReplyLedger {
    fun find(requestId: ReplyRequestId): ReplyRecord?
    fun save(record: ReplyRecord)
}

/** Kakao side-effect port. */
fun interface KakaoReplySender {
    suspend fun send(command: ReplyCommand)
}

/** Kakao database commit-evidence port. */
interface KakaoReplyCommitProbe {
    /** Return the newest local row that matches this exact command after the boundary. */
    suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long?

    /** Return the current local Kakao log boundary before a side effect. */
    fun latestLogId(): Long
}
