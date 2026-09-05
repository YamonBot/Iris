package party.qwer.iris.features.reply.infrastructure

/** Match every requested image once; an unrelated or partial batch is not proof. */
internal fun matchingImageCommit(expected: List<String>, rows: List<Pair<Long, String>>): Long? {
    if (expected.isEmpty()) return null
    val remaining = expected.toMutableList()
    for ((id, checksum) in rows.distinctBy { it.first }) {
        if (remaining.remove(checksum) && remaining.isEmpty()) return id
    }
    return null
}
