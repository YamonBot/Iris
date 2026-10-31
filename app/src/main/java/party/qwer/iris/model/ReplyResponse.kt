package party.qwer.iris.model

import kotlinx.serialization.Serializable

/** Result of one idempotent Kakao reply request. */
@Serializable
data class ReplyResponse(
    val success: Boolean,
    val request_id: String,
    val status: String,
    val kakao_log_id: Long? = null,
    val duplicate: Boolean = false,
    val message: String? = null,
)
