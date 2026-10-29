package party.qwer.iris.model

import kotlinx.serialization.Serializable

/** Mutable display labels and room classification for stable Kakao ids. */
@Serializable
data class ChatIdentityResponse(
    val room_name: String,
    val actor_name: String,
    val is_group: Boolean,
    val is_open_chat: Boolean,
)
