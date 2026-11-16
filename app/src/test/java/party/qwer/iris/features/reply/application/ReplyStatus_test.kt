package party.qwer.iris.features.reply.application

import java.io.File
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import party.qwer.iris.features.reply.domain.*
import party.qwer.iris.features.reply.infrastructure.FileReplyLedger

class ReplyStatusTest {
    @Test fun explicitExactReplayCanQualifyAnOldCommittedReceiptWithoutSending() = runBlocking {
        val file = File.createTempFile("iris-qualified-receipt-", ".jsonl")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val command = ReplyCommand(ReplyRequestId("old-committed"), 42, null,
                ReplyPayload.Text("original-body"))
            FileReplyLedger(file).save(ReplyRecord(command.requestId, command.fingerprint(),
                ReplyStatus.KAKAO_DB_COMMITTED, 10, 11))
            val dispatcher = ReplyDispatcher(KakaoReplySender { error("never send") },
                object : KakaoReplyCommitProbe {
                    override fun latestLogId(): Long = error("never probe")
                    override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long? =
                        error("never probe")
                }, FileReplyLedger(file), { 0 }, scope = scope)
            assertNull(dispatcher.status(command.requestId, 42))
            assertEquals(11L, dispatcher.dispatch(command).kakaoLogId)
            assertEquals(11L, dispatcher.status(command.requestId, 42)?.kakaoLogId)
            assertFalse(file.readText().contains("original-body"))
            assertEquals(command.commitEvidence(), FileReplyLedger(file).find(command.requestId)?.commitEvidence)
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun delayedCommitCanBeReadAfterRestartWithoutResendingOrLedgerWrites() = runBlocking {
        val file = File.createTempFile("iris-receipt-", ".jsonl")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            for (payload in listOf(ReplyPayload.Text("test-only-private-body"),
                ReplyPayload.Images(listOf(Base64.getEncoder().encodeToString("test-image".toByteArray()))))) {
                val command = ReplyCommand(ReplyRequestId("test-receipt"), 42, null, payload)
                val record = ReplyRecord(command.requestId, command.fingerprint(),
                    ReplyStatus.KAKAO_DB_UNCONFIRMED, baselineLogId = 10,
                    commitEvidence = command.commitEvidence())
                FileReplyLedger(file).save(record)
                val ledger = FileReplyLedger(file)
                val before = file.readBytes()
                assertFalse(file.readText().contains("test-only-private-body"))
                var observed: Long? = null
                var probes = 0
                val dispatcher = ReplyDispatcher(
                    sender = KakaoReplySender { error("status must never send") },
                    commitProbe = object : KakaoReplyCommitProbe {
                        override fun latestLogId(): Long = error("retain the original baseline")
                        override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long? =
                            error("status must not wait or replay a command")
                        override fun findOwnRow(evidence: ReplyCommitEvidence, afterLogId: Long): Long? {
                            assertEquals(command.commitEvidence(), evidence)
                            assertEquals(10L, afterLogId)
                            probes++
                            return observed
                        }
                    },
                    ledger = ledger, sendDelayMillis = { 0 }, scope = scope,
                )
                assertNull(dispatcher.status(command.requestId, 43))
                assertNull(dispatcher.status(ReplyRequestId("unknown-request"), 42))
                assertEquals(0, probes)
                assertEquals(ReplyStatus.KAKAO_DB_UNCONFIRMED, dispatcher.status(command.requestId, 42)?.status)
                observed = 11
                val receipt = requireNotNull(dispatcher.status(command.requestId, 42))
                assertEquals(ReplyStatus.KAKAO_DB_COMMITTED, receipt.status)
                assertEquals(11L, receipt.kakaoLogId)
                assertTrue(receipt.duplicate)
                assertTrue(before.contentEquals(file.readBytes()))
                assertEquals(record, FileReplyLedger(file).find(command.requestId))
            }
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun historicalRecordsWithoutEvidenceDoNotGuessACorrespondingMessage() = runBlocking {
        val file = File.createTempFile("iris-legacy-receipt-", ".jsonl")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val id = ReplyRequestId("legacy-request")
            FileReplyLedger(file).save(ReplyRecord(id, "fingerprint", ReplyStatus.KAKAO_DB_UNCONFIRMED, 10))
            val dispatcher = ReplyDispatcher(KakaoReplySender { error("never send") },
                object : KakaoReplyCommitProbe {
                    override fun latestLogId(): Long = error("never probe")
                    override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long? =
                        error("never probe")
                }, FileReplyLedger(file), { 0 }, scope = scope)
            assertNull(dispatcher.status(id, 42))
        } finally { scope.cancel(); file.delete() }
    }
}
