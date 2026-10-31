package party.qwer.iris.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

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

private val replyWireJson = Json { encodeDefaults = true }

/** Keep all six fields required by the shared Iris transport, including nulls. */
internal fun ReplyResponse.toWire(): JsonElement = replyWireJson.encodeToJsonElement(this)
