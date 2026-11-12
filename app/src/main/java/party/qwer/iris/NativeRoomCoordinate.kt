package party.qwer.iris

private val nativeRoomCoordinate = Regex("-?[1-9][0-9]*")

/** Read and send paths share exact signed native room coordinates. */
internal fun parseNativeRoomId(raw: String?): Long? =
    raw?.takeIf { nativeRoomCoordinate.matches(it) }?.toLongOrNull()

internal fun requireNativeRoomId(raw: String?): Long =
    requireNotNull(parseNativeRoomId(raw)) { "room must be a canonical nonzero signed 64-bit integer" }
