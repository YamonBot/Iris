package party.qwer.iris.features.reply.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageCommitTest {
    @Test fun attachmentChecksumsPreserveGroupedDuplicates() {
        val checksum = "a".repeat(40)
        assertEquals(listOf(checksum), imageCommitChecksums("2", """{"cs":"${checksum.uppercase()}"}"""))
        assertEquals(listOf(checksum, checksum), imageCommitChecksums("27", """{"csl":["$checksum","$checksum"]}"""))
    }
    @Test fun malformedAttachmentsProvideNoEvidence() {
        val checksum = "a".repeat(40)
        for ((type, raw) in listOf(
            "27" to """{"csl":["$checksum","bad"]}""",
            "27" to """{"csl":[]}""",
            "27" to """{"csl":"$checksum"}""",
            "2" to "{}", "2" to "invalid", "1" to """{"cs":"$checksum"}""",
        )) assertEquals(emptyList(), imageCommitChecksums(type, raw))
    }
    @Test fun exactImageMatches() {
        assertEquals(12L, matchingImageCommit(listOf("a"), listOf(11L to listOf("b"), 12L to listOf("a"))))
    }
    @Test fun partialOrUnrelatedBatchFails() {
        assertNull(matchingImageCommit(listOf("a", "b"), listOf(11L to listOf("a"))))
        assertNull(matchingImageCommit(listOf("a"), listOf(11L to listOf("b"))))
    }
    @Test fun repeatedImageNeedsDistinctRows() {
        assertNull(matchingImageCommit(listOf("a", "a"), listOf(11L to listOf("a"), 11L to listOf("a"))))
        assertEquals(12L, matchingImageCommit(listOf("a", "a"), listOf(11L to listOf("a"), 12L to listOf("a"))))
    }
    @Test fun groupedImagesPreserveMultiplicity() {
        assertEquals(11L, matchingImageCommit(listOf("a", "a"), listOf(11L to listOf("a", "a"))))
        assertEquals(11L, matchingImageCommit(listOf("a", "b"), listOf(11L to listOf("a", "b"))))
        assertNull(matchingImageCommit(listOf("a", "b"), listOf(11L to listOf("a", "c"))))
    }
}
