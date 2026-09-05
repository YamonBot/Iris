package party.qwer.iris.features.reply.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import party.qwer.iris.features.reply.domain.*

/** Recovery must reconcile an attempted side effect, never repeat it. */
class ReplyDispatcherRecoveryTest {
    @Test fun processingRecoveryNeverResends() = runBlocking {
        for (initialStatus in listOf(ReplyStatus.PROCESSING, ReplyStatus.KAKAO_DB_UNCONFIRMED)) {
            verifyRecovery(initialStatus)
        }
    }

    private suspend fun verifyRecovery(initialStatus: ReplyStatus) {
        for (baseline in listOf<Long?>(10L, null)) {
            for (observed in listOf<Long?>(11L, null)) {
                val command = ReplyCommand(ReplyRequestId("recovery-test"), 42L, null,
                    ReplyPayload.Text("test-only"))
                var currentRecord = ReplyRecord(command.requestId, command.fingerprint(),
                    initialStatus, baselineLogId = baseline)
                var sends = 0
                var probes = 0
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                try {
                    val dispatcher = ReplyDispatcher(
                        sender = KakaoReplySender { sends++ },
                        commitProbe = object : KakaoReplyCommitProbe {
                            override fun latestLogId(): Long = error("must retain original boundary")
                            override suspend fun awaitOwnRow(command: ReplyCommand, afterLogId: Long): Long? {
                                assertEquals(baseline, afterLogId)
                                probes++
                                return observed
                            }
                        },
                        ledger = object : ReplyLedger {
                            override fun find(requestId: ReplyRequestId): ReplyRecord = currentRecord
                            override fun save(record: ReplyRecord) { currentRecord = record }
                        },
                        sendDelayMillis = { 0L }, scope = scope,
                    )
                    val result = withTimeout(2_000) { dispatcher.dispatch(command) }
                    val expected = if (baseline != null && observed != null)
                        ReplyStatus.KAKAO_DB_COMMITTED else ReplyStatus.KAKAO_DB_UNCONFIRMED
                    assertEquals(expected, result.status)
                    assertEquals(expected, currentRecord.status)
                    assertTrue(result.duplicate)
                    assertEquals(0, sends)
                    assertEquals(if (baseline == null) 0 else 1, probes)
                    assertEquals(result, withTimeout(2_000) { dispatcher.dispatch(command) })
                    assertEquals(0, sends)
                } finally {
                    scope.cancel()
                }
            }
        }
    }
}
