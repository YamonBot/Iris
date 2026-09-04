package party.qwer.iris.features.reply.application

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
                override suspend fun awaitOwnRow(roomId: Long, afterLogId: Long): Long = 42
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
                override suspend fun awaitOwnRow(roomId: Long, afterLogId: Long): Long = 2
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
}
