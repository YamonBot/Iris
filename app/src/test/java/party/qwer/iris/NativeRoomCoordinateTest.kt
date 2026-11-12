package party.qwer.iris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import party.qwer.iris.features.reply.domain.ReplyCommand
import party.qwer.iris.features.reply.domain.ReplyPayload
import party.qwer.iris.features.reply.domain.ReplyRequestId

class NativeRoomCoordinateTest {
    @Test fun preservesSignedNativeLongCoordinatesWithoutRounding() {
        for (value in listOf("1", "-1", "9007199254740995", "-9007199254740995",
            Long.MAX_VALUE.toString(), Long.MIN_VALUE.toString())) {
            assertEquals(value, value.toLong(), parseNativeRoomId(value))
        }
    }

    @Test fun rejectsNoncanonicalAndOutOfRangeCoordinates() {
        for (value in listOf(null, "", "0", "-0", "+1", "01", "-01", " 1", "-1\n",
            "1.0", "1e3", "١", "9223372036854775808", "-9223372036854775809")) {
            assertNull(value, parseNativeRoomId(value))
        }
    }

    @Test fun durableSendPreservesSignedRoomAndItsIdempotencyBoundary() {
        val request = ReplyRequestId("request-123")
        for (raw in listOf("-1", "-9007199254740995", Long.MIN_VALUE.toString())) {
            val command = ReplyCommand(request, requireNativeRoomId(raw), null, ReplyPayload.Text("hello"))
            assertEquals(raw, command.roomId.toString())
            val other = command.copy(roomId = 1L)
            assertNotEquals(other.fingerprint(), command.fingerprint())
            assertEquals(command.fingerprint(), command.copy().fingerprint())
        }
    }

    @Test fun invalidSendCoordinatesFailBeforeDispatchAndThreadsRemainPositive() {
        for (raw in listOf(null, "0", "-0", "01", "-01", "+1", "1e3", "9223372036854775808")) {
            assertThrows(IllegalArgumentException::class.java) { requireNativeRoomId(raw) }
        }
        val request = ReplyRequestId("request-123")
        assertThrows(IllegalArgumentException::class.java) {
            ReplyCommand(request, 0L, null, ReplyPayload.Text("hello"))
        }
        for (thread in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) {
                ReplyCommand(request, -1L, thread, ReplyPayload.Text("hello"))
            }
        }
    }
}
