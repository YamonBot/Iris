package party.qwer.iris

/** Ignore empty optional labels instead of overwriting a known room with blank. */
internal fun notificationRoomName(vararg labels: String?): String? =
    labels.firstOrNull { !it.isNullOrBlank() }

/** Keep the latest valid name from each sender's own message, never the title. */
internal fun latestSenderNames(senders: List<Pair<String, String>>): Map<String, String> =
    senders.filter { (id, name) -> id.isNotBlank() && name.isNotBlank() }.toMap()
