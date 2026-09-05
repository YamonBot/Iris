package party.qwer.iris.features.reply.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageCommitTest {
    @Test fun exactImageMatches() {
        assertEquals(12L, matchingImageCommit(listOf("a"), listOf(11L to "b", 12L to "a")))
    }
    @Test fun partialOrUnrelatedBatchFails() {
        assertNull(matchingImageCommit(listOf("a", "b"), listOf(11L to "a")))
        assertNull(matchingImageCommit(listOf("a"), listOf(11L to "b")))
    }
    @Test fun repeatedImageNeedsDistinctRows() {
        assertNull(matchingImageCommit(listOf("a", "a"), listOf(11L to "a", 11L to "a")))
        assertEquals(12L, matchingImageCommit(listOf("a", "a"), listOf(11L to "a", 12L to "a")))
    }
}
