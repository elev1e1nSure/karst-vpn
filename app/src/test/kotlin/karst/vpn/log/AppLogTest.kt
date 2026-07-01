package karst.vpn.log

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLogTest {
    @Test
    fun testSanitizeVlessLink() {
        val raw = "Connecting to vless://7d483488-8255-46f9-aa2b-eb9b5b2dbcc4@1.1.1.1:443?security=reality#my-server"
        val expected = "Connecting to vless://***@1.1.1.1:443?security=reality#my-server"
        assertEquals(expected, AppLog.sanitize(raw))
    }

    @Test
    fun testSanitizeUrlCredentials() {
        val raw = "Fetching subscription from https://john:pass123@example.com/sub/profile"
        val expected = "Fetching subscription from https://***:***@example.com/sub/profile"
        assertEquals(expected, AppLog.sanitize(raw))
    }

    @Test
    fun testSanitizeUrlQueryParameters() {
        val raw = "Querying URL https://example.com/sub?token=my_secret_token_123&type=vless&pass=foo"
        val expected = "Querying URL https://example.com/sub?token=***&type=vless&pass=***"
        assertEquals(expected, AppLog.sanitize(raw))
    }

    @Test
    fun testLoggingDoesNotCrashOnJvm() {
        // AppLog should fallback to console and not throw "android.util.Log not mocked"
        AppLog.info(AppLog.Category.CORE, "Test logging in JUnit")
        AppLog.error(AppLog.Category.VPN, "Test error in JUnit", RuntimeException("Dummy error"))
    }
}
