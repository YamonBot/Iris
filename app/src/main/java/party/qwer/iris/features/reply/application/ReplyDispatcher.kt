package party.qwer.iris.features.reply.application

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import party.qwer.iris.features.reply.domain.KakaoReplyCommitProbe
import party.qwer.iris.features.reply.domain.KakaoReplySender
import party.qwer.iris.features.reply.domain.ReplyCommand
import party.qwer.iris.features.reply.domain.ReplyLedger
import party.qwer.iris.features.reply.domain.ReplyReceipt
import party.qwer.iris.features.reply.domain.ReplyRecord
import party.qwer.iris.features.reply.domain.ReplyStatus

/** Raised when a caller reuses a request id for another payload. */
class ReplyRequestConflictException(message: String) : IllegalArgumentException(message)

/** Raised when the bounded gateway queue cannot accept more work. */
class ReplyQueueFullException(message: String) : IllegalStateException(message)

/** Runs Kakao replies in a bounded FIFO and returns database commit evidence. */
class ReplyDispatcher(
    private val sender: KakaoReplySender,
    private val commitProbe: KakaoReplyCommitProbe,
    private val ledger: ReplyLedger,
    private val sendDelayMillis: () -> Long,
    queueCapacity: Int = 64,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private data class Work(
        val command: ReplyCommand,
        val ready: CompletableDeferred<Unit>,
        val receipt: CompletableDeferred<ReplyReceipt>,
    )

    private val queue = Channel<Work>(queueCapacity)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Pair<String, CompletableDeferred<ReplyReceipt>>>()

    init {
        scope.launch {
            for (work in queue) {
                work.ready.await()
                val receipt = try {
                    process(work.command)
                } catch (error: Throwable) {
                    ReplyReceipt(
                        work.command.requestId,
                        ReplyStatus.SEND_FAILED,
                        message = error.message ?: error::class.java.simpleName,
                    )
                }
                work.receipt.complete(receipt)
                mutex.withLock { inFlight.remove(work.command.requestId.value) }
                delay(sendDelayMillis().coerceAtLeast(0))
            }
        }
    }

    /** Submit one command or replay its existing idempotent result. */
    suspend fun dispatch(command: ReplyCommand): ReplyReceipt {
        val fingerprint = command.fingerprint()
        val existing = ledger.find(command.requestId)
        if (existing != null) return existing.toReceipt(command, fingerprint)

        var duplicateWait: CompletableDeferred<ReplyReceipt>? = null
        var work: Work? = null
        mutex.withLock {
            val current = inFlight[command.requestId.value]
            if (current != null) {
                if (current.first != fingerprint) {
                    throw ReplyRequestConflictException("request_id is already used by another payload")
                }
                duplicateWait = current.second
            } else {
                val receipt = CompletableDeferred<ReplyReceipt>()
                val ready = CompletableDeferred<Unit>()
                val candidate = Work(command, ready, receipt)
                if (queue.trySend(candidate).isFailure) {
                    throw ReplyQueueFullException("reply queue is full")
                }
                inFlight[command.requestId.value] = fingerprint to receipt
                ledger.save(
                    ReplyRecord(command.requestId, fingerprint, ReplyStatus.QUEUED)
                )
                ready.complete(Unit)
                work = candidate
            }
        }

        val result = (duplicateWait ?: work!!.receipt).await()
        return if (duplicateWait != null) result.copy(duplicate = true) else result
    }

    private suspend fun process(command: ReplyCommand): ReplyReceipt {
        val fingerprint = command.fingerprint()
        val baseline = commitProbe.latestLogId()
        ledger.save(
            ReplyRecord(
                command.requestId,
                fingerprint,
                ReplyStatus.PROCESSING,
                baselineLogId = baseline,
            )
        )
        return try {
            sender.send(command)
            val committedLogId = commitProbe.awaitOwnRow(command.roomId, baseline)
            val status = if (committedLogId != null) {
                ReplyStatus.KAKAO_DB_COMMITTED
            } else {
                ReplyStatus.KAKAO_DB_UNCONFIRMED
            }
            val message = if (committedLogId == null) {
                "Kakao side effect was attempted but no matching local isMine row was observed"
            } else null
            val record = ReplyRecord(
                command.requestId,
                fingerprint,
                status,
                baselineLogId = baseline,
                kakaoLogId = committedLogId,
                message = message,
            )
            ledger.save(record)
            record.toReceipt()
        } catch (error: Throwable) {
            val record = ReplyRecord(
                command.requestId,
                fingerprint,
                ReplyStatus.SEND_FAILED,
                baselineLogId = baseline,
                message = error.message ?: error::class.java.simpleName,
            )
            ledger.save(record)
            record.toReceipt()
        }
    }

    private fun ReplyRecord.toReceipt(
        command: ReplyCommand? = null,
        requestedFingerprint: String? = null,
    ): ReplyReceipt {
        if (command != null && requestedFingerprint != fingerprint) {
            throw ReplyRequestConflictException("request_id is already used by another payload")
        }
        return ReplyReceipt(
            requestId,
            status,
            kakaoLogId,
            duplicate = command != null,
            message = message ?: if (status == ReplyStatus.QUEUED || status == ReplyStatus.PROCESSING) {
                "request state survived a restart; not resent to avoid a duplicate"
            } else null,
        )
    }
}
