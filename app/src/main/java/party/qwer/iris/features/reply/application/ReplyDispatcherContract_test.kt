package party.qwer.iris.features.reply.application

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import party.qwer.iris.features.reply.domain.KakaoReplyCommitProbe
import party.qwer.iris.features.reply.domain.KakaoReplySender
import party.qwer.iris.features.reply.domain.ReplyCommand
import party.qwer.iris.features.reply.domain.ReplyLedger
import party.qwer.iris.features.reply.domain.ReplyPayload
import party.qwer.iris.features.reply.domain.ReplyRecord
import party.qwer.iris.features.reply.domain.ReplyRequestId
import party.qwer.iris.features.reply.domain.ReplyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReplyDispatcherTest {
    private class MemoryLedger : ReplyLedger {
        private val records = mutableMapOf<String, ReplyRecord>()
        override fun find(requestId: ReplyRequestId): ReplyRecord? = records[requestId.value]
        override fun save(record: ReplyRecord) {
            records[record.requestId.value] = record
        }
    }

    @Test
    fun `returns committed evidence and replays duplicate without sending twice`() = runBlocking {
        var sends = 0
        val dispatcher = ReplyDispatcher(
            sender = KakaoReplySender { sends += 1 },
            commitProbe = object : KakaoReplyCommitProbe {
                override fun latestLogId(): Long = 41
                override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long = 42
            },
            ledger = MemoryLedger(),
            sendDelayMillis = { 0 },
        )
        val command = ReplyCommand(
            ReplyRequestId("reply-test-0001"),
            7,
            null,
            ReplyPayload.Text("hello"),
        )

        val first = dispatcher.dispatch(command)
        val duplicate = dispatcher.dispatch(command)

        assertEquals(ReplyStatus.KAKAO_DB_COMMITTED, first.status)
        assertEquals(42, first.kakaoLogId)
        assertTrue(duplicate.duplicate)
        assertEquals(1, sends)
    }

    @Test
    fun `rejects request id reuse with another payload`() = runBlocking {
        val ledger = MemoryLedger()
        val dispatcher = ReplyDispatcher(
            sender = KakaoReplySender {},
            commitProbe = object : KakaoReplyCommitProbe {
                override fun latestLogId(): Long = 1
                override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long = 2
            },
            ledger = ledger,
            sendDelayMillis = { 0 },
        )
        val id = ReplyRequestId("reply-test-0002")
        dispatcher.dispatch(ReplyCommand(id, 7, null, ReplyPayload.Text("first")))

        assertFailsWith<ReplyRequestConflictException> {
            dispatcher.dispatch(ReplyCommand(id, 7, null, ReplyPayload.Text("different")))
        }
        Unit
    }

    @Test
    fun `same in flight request waits for one send`() = runBlocking {
        var sends = 0
        val sending = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dispatcher = ReplyDispatcher(
            sender = KakaoReplySender {
                sends += 1
                sending.complete(Unit)
                release.await()
            },
            commitProbe = object : KakaoReplyCommitProbe {
                override fun latestLogId(): Long = 10
                override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long = 11
            },
            ledger = MemoryLedger(),
            sendDelayMillis = { 0 },
        )
        val command = ReplyCommand(
            ReplyRequestId("reply-test-inflight"), 7, null, ReplyPayload.Text("hello")
        )

        val first = async { dispatcher.dispatch(command) }
        sending.await()
        val duplicate = async { dispatcher.dispatch(command) }
        release.complete(Unit)

        assertEquals(ReplyStatus.KAKAO_DB_COMMITTED, first.await().status)
        assertTrue(duplicate.await().duplicate)
        assertEquals(1, sends)
    }

    @Test
    fun `reconciles a processing record without resending`() = runBlocking {
        val command = ReplyCommand(
            ReplyRequestId("reply-test-recovery"), 7, null, ReplyPayload.Text("hello")
        )
        val ledger = MemoryLedger().also {
            it.save(
                ReplyRecord(
                    command.requestId,
                    command.fingerprint(),
                    ReplyStatus.PROCESSING,
                    baselineLogId = 41,
                )
            )
        }
        var sends = 0
        val dispatcher = ReplyDispatcher(
            sender = KakaoReplySender { sends += 1 },
            commitProbe = object : KakaoReplyCommitProbe {
                override fun latestLogId(): Long = 99
                override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long = 42
            },
            ledger = ledger,
            sendDelayMillis = { 0 },
        )

        val result = dispatcher.dispatch(command)

        assertEquals(ReplyStatus.KAKAO_DB_COMMITTED, result.status)
        assertEquals(42, result.kakaoLogId)
        assertTrue(result.duplicate)
        assertEquals(0, sends)
    }

    @Test
    fun `ledger failure cancels queued work before the side effect`() = runBlocking {
        var sends = 0
        val dispatcher = ReplyDispatcher(
            sender = KakaoReplySender { sends += 1 },
            commitProbe = object : KakaoReplyCommitProbe {
                override fun latestLogId(): Long = 1
                override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long = 2
            },
            ledger = object : ReplyLedger {
                override fun find(requestId: ReplyRequestId): ReplyRecord? = null
                override fun save(record: ReplyRecord) = error("ledger unavailable")
            },
            sendDelayMillis = { 0 },
        )

        val result = dispatcher.dispatch(
            ReplyCommand(
                ReplyRequestId("reply-test-ledger-failure"),
                7,
                null,
                ReplyPayload.Text("hello"),
            )
        )

        assertEquals(ReplyStatus.SEND_FAILED, result.status)
        assertEquals(0, sends)
    }
}
