package party.qwer.iris

/** Scan a fixed sequence window without repeatedly returning the first page. */
internal fun chatLogPages(
    afterId: Long,
    throughId: Long,
    fetch: (Long, Long) -> List<Map<String, String?>>,
): Sequence<Map<String, String?>> = sequence {
    var cursor = afterId
    while (cursor < throughId) {
        val rows = fetch(cursor, throughId)
        if (rows.isEmpty()) break
        for (row in rows) {
            val id = requireNotNull(row["_id"]?.toLongOrNull()) { "missing chat log sequence" }
            require(id > cursor && id <= throughId) { "chat log page did not advance within its boundary" }
            cursor = id
            yield(row)
        }
    }
}
