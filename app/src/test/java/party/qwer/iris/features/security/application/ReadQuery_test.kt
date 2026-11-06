package party.qwer.iris.features.security.application

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReadQuery_test {
    @Test fun readsUseTheExistingBoundSelectContract() {
        listOf("SELECT _id FROM chat_logs WHERE chat_id = ? LIMIT ?",
            "\n select count(*) from db2.friends", "SELECT sqlite_version()",
            "SELECT/* query */ _id FROM chat_logs").forEach(::requireReadQuery)
    }

    @Test fun foreignAttachmentsAndMutationsNeverReachTheDatabase() {
        listOf("ATTACH DATABASE ? AS other", "DETACH DATABASE db1",
            "PRAGMA query_only = OFF", "UPDATE db1.chat_logs SET message = ?",
            "DELETE FROM db1.chat_logs", "VACUUM INTO ?",
            "WITH x AS (SELECT 1) DELETE FROM db1.chat_logs",
            "-- comment\nATTACH DATABASE ? AS other",
            "/* comment */ ATTACH DATABASE ? AS other",
            "SELECT 1; ATTACH DATABASE ? AS other", "", " ",
            "SELECT " + "x".repeat(65_536)).forEach { sql ->
            assertFailsWith<IllegalArgumentException> { requireReadQuery(sql) }
        }
    }
}
