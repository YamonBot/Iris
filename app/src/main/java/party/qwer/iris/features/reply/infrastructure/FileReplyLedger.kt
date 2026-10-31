package party.qwer.iris.features.reply.infrastructure

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import party.qwer.iris.features.reply.domain.ReplyLedger
import party.qwer.iris.features.reply.domain.ReplyRecord
import party.qwer.iris.features.reply.domain.ReplyRequestId
import party.qwer.iris.features.reply.domain.ReplyStatus
import java.io.File
import java.io.FileOutputStream

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
                val record = json.decodeFromString<StoredRecord>(line).toDomain()
                records[record.requestId.value] = record
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
        FileOutputStream(file, true).use { output ->
            output.write((json.encodeToString(stored) + "\n").encodeToByteArray())
            output.fd.sync()
        }
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
