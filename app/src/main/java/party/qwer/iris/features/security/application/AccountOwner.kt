package party.qwer.iris.features.security.application

internal class AccountOwnerMismatch(message: String) : IllegalStateException(message)

/** Optional for the legacy primary daemon; mandatory when its native caller pins an owner. */
internal fun requireConfiguredOwner(expected: String?, actual: Long) {
    if (expected == null) return
    val owner = expected.toLongOrNull()?.takeIf { it > 0 && it.toString() == expected }
        ?: throw AccountOwnerMismatch("An exact positive account owner is required")
    check(actual > 0) { "KakaoTalk account identity is not available" }
    if (actual != owner) throw AccountOwnerMismatch("KakaoTalk account does not match the configured owner")
}
