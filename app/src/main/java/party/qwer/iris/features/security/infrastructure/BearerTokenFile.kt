package party.qwer.iris.features.security.infrastructure

import java.io.File
import java.security.MessageDigest

/** Reads one or more overlapping bearer tokens from a root-projected file. */
class BearerTokenFile(private val file: File) {

    init {
        require(readTokens().isNotEmpty()) { "Iris bearer token file is missing or empty" }
    }

    /** Constant-time token comparison across the active rotation set. */
    fun accepts(authorizationHeader: String?): Boolean {
        val candidate = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.toByteArray()
            ?: return false
        return readTokens().fold(false) { accepted, token ->
            MessageDigest.isEqual(token.toByteArray(), candidate) || accepted
        }
    }

    private fun readTokens(): List<String> = runCatching {
        file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())
}
