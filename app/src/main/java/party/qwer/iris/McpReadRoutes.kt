package party.qwer.iris

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import party.qwer.iris.features.media.infrastructure.KakaoImageSource
import party.qwer.iris.model.ApiResponse
import party.qwer.iris.model.ChatIdentityResponse
import party.qwer.iris.model.CommonErrorResponse

/** Preserve the existing authenticated Room API read protocol on current upstream. */
internal fun Route.mcpReadRoutes(database: KakaoDB) {
    val images = KakaoImageSource(database)
    val slots = Semaphore(2)
    get("/healthz") { call.respond(ApiResponse(true, "healthy")) }
    get("/identity/{chatId}/{userId}") {
        val room = call.parameters["chatId"]?.toLongOrNull()?.takeIf { it > 0 }
        val actor = call.parameters["userId"]?.toLongOrNull()?.takeIf { it > 0 }
        if (room == null || actor == null) {
            return@get call.respond(HttpStatusCode.BadRequest, CommonErrorResponse(message = "invalid identity"))
        }
        val names = database.getChatInfo(room, actor)
        val count = database.executeQuery(
            "SELECT active_members_count FROM chat_rooms WHERE id=? LIMIT 1", arrayOf(room.toString())
        ).firstOrNull()?.get("active_members_count")?.toIntOrNull() ?: 0
        call.respond(ChatIdentityResponse(
            names[0]?.takeIf { it.isNotBlank() } ?: "room-$room",
            names[1]?.takeIf { it.isNotBlank() } ?: "user-$actor",
            count > 2, (room and (2L shl 53)) != 0L,
        ))
    }
    get("/media/{logId}") {
        val log = call.parameters["logId"]?.toLongOrNull()?.takeIf { it > 0 }
        val index = (call.request.queryParameters["index"] ?: "0").toIntOrNull()?.takeIf { it in 0..29 }
        if (log == null || index == null) {
            return@get call.respond(HttpStatusCode.BadRequest, CommonErrorResponse(message = "invalid image reference"))
        }
        try {
            val image = slots.withPermit { withContext(Dispatchers.IO) { images.fetch(log, index) } }
            call.respondBytes(image.bytes, ContentType.parse(image.contentType))
        } catch (_: NoSuchElementException) {
            call.respond(HttpStatusCode.NotFound, CommonErrorResponse(message = "image not found"))
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.UnprocessableEntity, CommonErrorResponse(message = "unsupported image"))
        }
    }
}
