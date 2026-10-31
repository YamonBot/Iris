package party.qwer.iris

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatLogPagesTest {
    @Test fun reachesEvidenceBeyondFirstPageAndStopsAtSnapshot() {
        val calls = mutableListOf<Long>()
        val rows = chatLogPages(0, 130) { cursor, through ->
            calls.add(cursor)
            (1L..140L).filter { it > cursor && it <= through }.take(64)
                .map { mapOf("_id" to it.toString()) }
        }.toList()
        assertEquals(130, rows.size)
        assertEquals("130", rows.last()["_id"])
        assertEquals(listOf(0L, 64L, 128L), calls)
    }
    @Test fun staleOrOutOfWindowPageFailsInsteadOfLooping() {
        for (id in listOf("5", "11")) {
            assertFailsWith<IllegalArgumentException> {
                chatLogPages(5, 10) { _, _ -> listOf(mapOf("_id" to id)) }.toList()
            }
        }
    }
    @Test fun emptyPageEndsSparseWindow() {
        assertEquals(emptyList(), chatLogPages(5, 10) { _, _ -> emptyList() }.toList())
    }
}
