package party.qwer.iris.features.reply.infrastructure

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Read checksum evidence without accepting malformed or unsupported attachments. */
internal fun imageCommitChecksums(type: String?, raw: String): List<String> = runCatching {
    val attachment = Json.parseToJsonElement(raw).jsonObject
    val checksums = when (type) {
        "2" -> listOf(attachment.getValue("cs").jsonPrimitive.content)
        "27" -> attachment.getValue("csl").jsonArray.map { it.jsonPrimitive.content }
        else -> emptyList()
    }.map { it.lowercase() }
    if (checksums.size in 1..30 && checksums.all { it.matches(Regex("[0-9a-f]{40}")) }) {
        checksums
    } else emptyList()
}.getOrDefault(emptyList())

/** Match every requested image once; an unrelated or partial batch is not proof. */
internal fun matchingImageCommit(expected: List<String>, rows: List<Pair<Long, List<String>>>): Long? {
    if (expected.isEmpty()) return null
    val remaining = expected.toMutableList()
    for ((id, checksums) in rows.distinctBy { it.first }) {
        for (checksum in checksums) {
            if (remaining.remove(checksum) && remaining.isEmpty()) return id
        }
    }
    return null
}
