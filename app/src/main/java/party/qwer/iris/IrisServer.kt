package party.qwer.iris

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.send
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import party.qwer.iris.model.AotResponse
import party.qwer.iris.model.ApiResponse
import party.qwer.iris.model.CommonErrorResponse
import party.qwer.iris.model.ConfigRequest
import party.qwer.iris.model.ConfigResponse
import party.qwer.iris.model.DashboardStatusResponse
import party.qwer.iris.model.DecryptRequest
import party.qwer.iris.model.DecryptResponse
import party.qwer.iris.model.QueryRequest
import party.qwer.iris.model.QueryResponse
import party.qwer.iris.model.ReplyRequest
import party.qwer.iris.model.ReplyResponse
import party.qwer.iris.model.ReplyType
import party.qwer.iris.features.media.infrastructure.KakaoImageSource
import party.qwer.iris.features.reply.application.ReplyDispatcher
import party.qwer.iris.features.reply.application.ReplyQueueFullException
import party.qwer.iris.features.reply.application.ReplyRequestConflictException
import party.qwer.iris.features.reply.domain.ReplyCommand
import party.qwer.iris.features.reply.domain.ReplyPayload
import party.qwer.iris.features.reply.domain.ReplyRequestId
import party.qwer.iris.features.security.infrastructure.BearerTokenFile


class IrisServer(
    private val kakaoDB: KakaoDB,
    private val dbObserver: DBObserver,
    private val observerHelper: ObserverHelper,
    private val wsBroadcastFlow: MutableSharedFlow<String>,
    private val bearerTokenFile: BearerTokenFile,
    private val replyDispatcher: ReplyDispatcher,
    private val imageSource: KakaoImageSource,
) {
    val sharedFlow = wsBroadcastFlow.asSharedFlow()

    fun startServer() {
        embeddedServer(Netty, port = Configurable.botSocketPort) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }

            install(ContentNegotiation) {
                json()
            }

            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError, CommonErrorResponse(
                            message = cause.message ?: "unknown error"
                        )
                    )
                }
            }

            routing {
                intercept(ApplicationCallPipeline.Plugins) {
                    if (!bearerTokenFile.accepts(context.request.header(HttpHeaders.Authorization))) {
                        context.respond(
                            HttpStatusCode.Unauthorized,
                            CommonErrorResponse(message = "missing or invalid bearer token"),
                        )
                        finish()
                    }
                }

                get("/healthz") {
                    call.respond(ApiResponse(success = true, message = "healthy"))
                }

                route("/dashboard") {
                    get {
                        val html = PageRenderer.renderDashboard()
                        call.respondText(html, ContentType.Text.Html)
                    }

                    get("status") {
                        call.respond(
                            DashboardStatusResponse(
                                isObserving = dbObserver.isPollingThreadAlive,
                                statusMessage = if (dbObserver.isPollingThreadAlive) {
                                    "Observing database"
                                } else {
                                    "Not observing database"
                                },
                                lastLogs = observerHelper.lastChatLogs
                            )
                        )
                    }
                }

                route("/config") {
                    get {
                        call.respond(
                            ConfigResponse(
                                bot_name = Configurable.botName,
                                bot_http_port = Configurable.botSocketPort,
                                web_server_endpoint = Configurable.webServerEndpoint,
                                db_polling_rate = Configurable.dbPollingRate,
                                message_send_rate = Configurable.messageSendRate,
                                bot_id = Configurable.botId,
                            )
                        )
                    }

                    post("{name}") {
                        val name = call.parameters["name"]
                        val req = call.receive<ConfigRequest>()

                        when (name) {
                            "endpoint" -> {
                                var value = req.endpoint
                                if (value == null) {
                                    value = ""
                                }
                                Configurable.webServerEndpoint = value
                            }

                            "botname" -> {
                                val value = req.botname
                                if (value.isNullOrBlank()) {
                                    throw Exception("missing or empty value")
                                }
                                Configurable.botName = value
                            }

                            "dbrate" -> {
                                val value = req.rate ?: throw Exception("missing or invalid value")

                                Configurable.dbPollingRate = value
                            }

                            "sendrate" -> {
                                val value = req.rate ?: throw Exception("missing or invalid value")

                                Configurable.messageSendRate = value
                            }

                            "botport" -> {
                                val value = req.port ?: throw Exception("missing or invalid value")

                                if (value < 1 || value > 65535) {
                                    throw Exception("Invalid port number. Port must be between 1 and 65535.")
                                }

                                Configurable.botSocketPort = value
                            }

                            else -> {
                                throw Exception("Unknown config $name")
                            }
                        }

                        call.respond(ApiResponse(success = true, message = "success"))
                    }
                }

                get("/aot") {
                    val aotToken = AuthProvider.getToken()

                    call.respond(
                        AotResponse(
                            success = true,
                            aot = Json.parseToJsonElement(aotToken.toString()).jsonObject
                        )
                    )
                }

                post("/reply") {
                    val replyRequest = call.receive<ReplyRequest>()
                    val payload = when (replyRequest.type) {
                        ReplyType.TEXT -> ReplyPayload.Text(replyRequest.data.jsonPrimitive.content)
                        ReplyType.IMAGE -> ReplyPayload.Images(
                            listOf(replyRequest.data.jsonPrimitive.content)
                        )
                        ReplyType.IMAGE_MULTIPLE -> ReplyPayload.Images(
                            replyRequest.data.jsonArray.map { it.jsonPrimitive.content }
                        )
                    }
                    val command = ReplyCommand(
                        ReplyRequestId(replyRequest.request_id),
                        replyRequest.room.toLong(),
                        replyRequest.threadId?.toLong(),
                        payload,
                    )

                    try {
                        val receipt = replyDispatcher.dispatch(command)
                        call.respond(
                            if (receipt.success) HttpStatusCode.OK else HttpStatusCode.BadGateway,
                            ReplyResponse(
                                receipt.success,
                                receipt.requestId.value,
                                receipt.status.wireValue,
                                receipt.kakaoLogId,
                                receipt.duplicate,
                                receipt.message,
                            ),
                        )
                    } catch (error: ReplyRequestConflictException) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            CommonErrorResponse(message = error.message ?: "request_id conflict"),
                        )
                    } catch (error: ReplyQueueFullException) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            CommonErrorResponse(message = error.message ?: "reply queue is full"),
                        )
                    }
                }

                get("/media/{logId}") {
                    val logId = call.parameters["logId"]?.toLongOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            CommonErrorResponse(message = "invalid log id"),
                        )
                    try {
                        val image = imageSource.fetch(logId)
                        call.respondBytes(image.bytes, ContentType.parse(image.contentType))
                    } catch (error: NoSuchElementException) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            CommonErrorResponse(message = error.message ?: "image not found"),
                        )
                    } catch (error: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            CommonErrorResponse(message = error.message ?: "unsupported image"),
                        )
                    }
                }

                post("/query") {
                    val queryRequest = call.receive<QueryRequest>()

                    try {
                        val rows = kakaoDB.executeQuery(
                            queryRequest.query,
                            (queryRequest.bind?.map { it.content } ?: listOf()).toTypedArray())

                        call.respond(QueryResponse(data = rows.map {
                            KakaoDB.decryptRow(it)
                        }))
                    } catch (e: Exception) {
                        throw Exception("Query 오류: query=${queryRequest.query}, err=${e.message}")
                    }
                }

                post("/decrypt") {
                    val decryptRequest = call.receive<DecryptRequest>()
                    val plaintext = KakaoDecrypt.decrypt(
                        decryptRequest.enc,
                        decryptRequest.b64_ciphertext,
                        decryptRequest.user_id ?: Configurable.botId
                    )

                    call.respond(DecryptResponse(plain_text = plaintext))
                }

                webSocket("/ws") {
                    sharedFlow.collect { msg ->
                        send(msg)
                    }
                }
            }
        }.start(wait = true)
    }
}
