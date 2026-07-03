package karst.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionDisplayNameTest {

    @Test
    fun testUsesHostWhenPresent() {
        assertEquals("example.com", subscriptionDisplayName("https://example.com/sub?token=abc"))
    }

    @Test
    fun testFallsBackToRawUrlWhenUnparsable() {
        val raw = "not a valid uri"
        assertEquals(raw, subscriptionDisplayName(raw))
    }

    @Test
    fun testFallsBackToRawUrlWhenHostIsBlank() {
        val raw = "file:///local/sub"
        assertEquals(raw, subscriptionDisplayName(raw))
    }
}
