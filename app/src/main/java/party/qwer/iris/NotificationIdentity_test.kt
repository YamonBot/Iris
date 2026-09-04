package party.qwer.iris

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationIdentityTest {
    @Test
    fun groupedSendersRemainDistinct() {
        assertEquals(mapOf("a" to "Alice", "b" to "Bob"),
            latestSenderNames(listOf("a" to "Alice", "b" to "Bob")))
    }

    @Test
    fun renameUsesNewestMessageWithoutChangingIdentity() {
        assertEquals(mapOf("a" to "New"),
            latestSenderNames(listOf("a" to "Old", "a" to "New")))
    }

    @Test
    fun missingIdentityNeverOverwritesKnownName() {
        assertEquals(mapOf("a" to "Alice"),
            latestSenderNames(listOf("a" to "Alice", "a" to "", "" to "Bob")))
    }
}
