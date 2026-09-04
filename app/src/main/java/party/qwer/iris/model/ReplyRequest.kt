package party.qwer.iris.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ReplyRequest(
    val request_id: String,
    val type: ReplyType = ReplyType.TEXT,
    val room: String,
    val data: JsonElement,
    val threadId: String? = null,
)
