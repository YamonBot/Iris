package party.qwer.iris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
