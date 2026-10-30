package party.qwer.iris

/** A locked profile is temporarily unavailable; a changed identity requires a new daemon. */
internal class ProfileSessionGuard(
    private val unlocked: () -> Boolean,
    private val snapshot: () -> String
) {
    private val initial: String
    private var changed = false

    init {
        check(unlocked()) { "Selected Android profile is locked" }
        initial = snapshot()
    }

    @Synchronized fun requireAvailable() {
        check(!changed && unlocked()) { "Selected Android profile is unavailable" }
        val current = try {
            snapshot()
        } catch (error: Exception) {
            changed = true
            throw error
        }
        if (current != initial) changed = true
        check(!changed) { "Selected Android profile session changed; restart required" }
    }
}
