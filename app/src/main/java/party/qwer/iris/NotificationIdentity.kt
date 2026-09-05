package party.qwer.iris

/** Keep the latest valid name from each sender's own message, never the title. */
internal fun latestSenderNames(senders: List<Pair<String, String>>): Map<String, String> =
    senders.filter { (id, name) -> id.isNotBlank() && name.isNotBlank() }.toMap()
