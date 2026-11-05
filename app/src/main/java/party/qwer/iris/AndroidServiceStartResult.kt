package party.qwer.iris

/** Match ActivityManagerShellCommand: a Binder return can reject a service without throwing. */
internal fun requireAndroidServiceStarted(packageName: String?, detail: String?) {
    when (packageName) {
        null -> error("Android service not found; no service started")
        "!" -> throw SecurityException("Android service requires permission: $detail")
        "!!", "?" -> error("Android service start rejected: $detail")
    }
}
