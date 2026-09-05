package party.qwer.iris.features.reply.infrastructure

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
