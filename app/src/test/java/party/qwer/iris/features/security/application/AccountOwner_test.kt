package party.qwer.iris.features.security.application

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AccountOwner_test {
    @Test fun exactOwnerAndLegacyUnconfiguredPrimaryKeepTheirMeaning() {
        requireConfiguredOwner(null, 0)
        requireConfiguredOwner("413080895", 413080895)
        requireConfiguredOwner("43418238", 43418238)
        assertFailsWith<AccountOwnerMismatch> { requireConfiguredOwner("413080895", 43418238) }
        listOf("", "0", "-1", "+7", "07", " 7", "9223372036854775808").forEach {
            assertFailsWith<AccountOwnerMismatch> { requireConfiguredOwner(it, 7) }
        }
    }

    @Test fun unknownAccountIsUnavailableRatherThanAForeignAccount() {
        val error = assertFailsWith<IllegalStateException> { requireConfiguredOwner("7", 0) }
        assertFalse(error is AccountOwnerMismatch)
    }
}
