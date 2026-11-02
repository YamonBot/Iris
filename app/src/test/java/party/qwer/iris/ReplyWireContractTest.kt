package party.qwer.iris

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import party.qwer.iris.model.ReplyRequest
import party.qwer.iris.model.ReplyResponse
import party.qwer.iris.model.toWire

class ReplyWireContractTest {
    @Test fun firstCommitIncludesDefaultAndNullFieldsRequiredBySharedTransport() {
        val wire = ReplyResponse(true, "request-123", "kakao_db_committed", 42L).toWire().jsonObject
        assertEquals(setOf("success", "request_id", "status", "kakao_log_id", "duplicate", "message"), wire.keys)
        assertEquals("false", wire.getValue("duplicate").jsonPrimitive.content)
        assertEquals(JsonNull, wire.getValue("message"))
    }

    @Test fun legacyAndDurableRequestsRemainDistinct() {
        val legacy = Json.decodeFromString<ReplyRequest>("""{"room":"42","data":"hello"}""")
        assertNull(legacy.request_id)
        val durable = Json.decodeFromString<ReplyRequest>("""{"request_id":"request-123","room":"42","data":"hello"}""")
        assertEquals("request-123", durable.request_id)
    }
}
