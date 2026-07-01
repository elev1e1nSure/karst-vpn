package karst.vpn.link

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {
    @Test
    fun parsesStandardBase64List() {
        val body = Base64.getEncoder().encodeToString(LIST.toByteArray(StandardCharsets.UTF_8))
        val result = SubscriptionParser.parse(body)

        assertEquals(2, result.links.size)
        assertEquals(0, result.failures.size)
    }

    @Test
    fun parsesUrlSafeBase64List() {
        val body = Base64.getUrlEncoder().encodeToString(LIST.toByteArray(StandardCharsets.UTF_8))
        val result = SubscriptionParser.parse(body)

        assertEquals(2, result.links.size)
        assertEquals(0, result.failures.size)
    }

    @Test
    fun parsesUnpaddedBase64List() {
        val body = Base64.getEncoder().withoutPadding().encodeToString(LIST.toByteArray(StandardCharsets.UTF_8))
        val result = SubscriptionParser.parse(body)

        assertEquals(2, result.links.size)
        assertEquals(0, result.failures.size)
    }

    @Test
    fun fallsBackToPlainTextListWithPartialSuccess() {
        val result = SubscriptionParser.parse("$LINK_ONE\nnot-a-link\n$LINK_TWO")

        assertEquals(2, result.links.size)
        assertEquals(1, result.failures.size)
        assertEquals("not-a-link", result.failures[0].line)
    }

    @Test
    fun parsesCrlfLineEndings() {
        val body = "$LINK_ONE\r\n$LINK_TWO\r\n"
        val result = SubscriptionParser.parse(body)

        assertEquals(2, result.links.size)
        assertEquals(0, result.failures.size)
    }

    @Test
    fun parsesMixedListWithGarbage() {
        val body = """
            
            $LINK_ONE
            random garbage string
            ss://another-protocol-not-supported
            
            $LINK_TWO
            
        """.trimIndent()
        val result = SubscriptionParser.parse(body)

        assertEquals(2, result.links.size)
        assertEquals(2, result.failures.size) // garbage and ss:// link
        assertEquals("random garbage string", result.failures[0].line)
        assertEquals("ss://another-protocol-not-supported", result.failures[1].line)
    }

    @Test
    fun parsesEmptyOrWhitespaceSubscription() {
        val emptyResult = SubscriptionParser.parse("")
        assertTrue(emptyResult.links.isEmpty())
        assertTrue(emptyResult.failures.isEmpty())

        val spacesResult = SubscriptionParser.parse("   \n  \t  \r\n ")
        assertTrue(spacesResult.links.isEmpty())
        assertTrue(spacesResult.failures.isEmpty())
    }

    @Test
    fun parsesBase64ContainingGarbage() {
        val rawList = "$LINK_ONE\nthis is invalid\n$LINK_TWO"
        val base64Body = Base64.getEncoder().encodeToString(rawList.toByteArray(StandardCharsets.UTF_8))
        val result = SubscriptionParser.parse(base64Body)

        assertEquals(2, result.links.size)
        assertEquals(1, result.failures.size)
        assertEquals("this is invalid", result.failures[0].line)
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"
        const val LINK_ONE = "vless://$UUID@example.com:443?security=none&type=tcp#one"
        const val LINK_TWO = "vless://$UUID@example.org:8443?security=tls&type=ws&path=%2Fws#two"
        const val LIST = "$LINK_ONE\n$LINK_TWO"
    }
}
