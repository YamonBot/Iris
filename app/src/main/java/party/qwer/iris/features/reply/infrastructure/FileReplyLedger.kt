package party.qwer.iris.features.reply.infrastructure

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.qwer.iris.features.reply.domain.ReplyLedger
import party.qwer.iris.features.reply.domain.ReplyRecord
import party.qwer.iris.features.reply.domain.ReplyRequestId
import party.qwer.iris.features.reply.domain.ReplyStatus
import java.io.File

/** Append-only local idempotency ledger that survives Iris restarts. */
class FileReplyLedger(private val file: File) : ReplyLedger {
    @Serializable
    private data class StoredRecord(
        val requestId: String,
        val fingerprint: String,
        val status: String,
        val baselineLogId: Long? = null,
        val kakaoLogId: Long? = null,
        val message: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val records = linkedMapOf<String, ReplyRecord>()

    init {
        if (file.exists()) {
            file.forEachLine { line ->
                runCatching { json.decodeFromString<StoredRecord>(line).toDomain() }
                    .onSuccess { records[it.requestId.value] = it }
                    .onFailure { System.err.println("Ignoring invalid reply ledger record") }
            }
        }
    }

    @Synchronized
    override fun find(requestId: ReplyRequestId): ReplyRecord? = records[requestId.value]

    @Synchronized
    override fun save(record: ReplyRecord) {
        file.parentFile?.mkdirs()
        val stored = StoredRecord(
            record.requestId.value,
            record.fingerprint,
            record.status.name,
            record.baselineLogId,
            record.kakaoLogId,
            record.message,
        )
        file.appendText(json.encodeToString(stored) + "\n")
        records[record.requestId.value] = record
    }

    private fun StoredRecord.toDomain(): ReplyRecord = ReplyRecord(
        ReplyRequestId(requestId),
        fingerprint,
        ReplyStatus.valueOf(status),
        baselineLogId,
        kakaoLogId,
        message,
    )
}
