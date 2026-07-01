package karst.vpn.link

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
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
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"
        const val LINK_ONE = "vless://$UUID@example.com:443?security=none&type=tcp#one"
        const val LINK_TWO = "vless://$UUID@example.org:8443?security=tls&type=ws&path=%2Fws#two"
        const val LIST = "$LINK_ONE\n$LINK_TWO"
    }
}
