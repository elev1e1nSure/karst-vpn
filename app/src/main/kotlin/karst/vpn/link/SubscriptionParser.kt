package karst.vpn.link

import java.nio.charset.StandardCharsets
import java.util.Base64

data class SubscriptionParseResult(
    val links: List<VlessLink>,
    val failures: List<SubscriptionParseFailure>,
)

data class SubscriptionParseFailure(
    val line: String,
    val reason: String,
)

object SubscriptionParser {
    fun parse(body: String): SubscriptionParseResult {
        val decoded = decodeSubscription(body)
        val links = mutableListOf<VlessLink>()
        val failures = mutableListOf<SubscriptionParseFailure>()

        decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                parseVlessLink(line)
                    .onSuccess { links += it }
                    .onFailure { failures += SubscriptionParseFailure(line, it.message ?: it::class.simpleName.orEmpty()) }
            }

        return SubscriptionParseResult(links = links, failures = failures)
    }

    private fun decodeSubscription(body: String): String {
        val stripped = body.filterNot(Char::isWhitespace)
        val padded = stripped + "=".repeat((4 - stripped.length % 4) % 4)
        val decoded = decodeBase64(padded)
        if (decoded != null && decoded.contains("://")) {
            return decoded
        }
        return body
    }

    private fun decodeBase64(value: String): String? {
        val bytes = runCatching { Base64.getDecoder().decode(value) }
            .recoverCatching { Base64.getUrlDecoder().decode(value) }
            .getOrNull() ?: return null
        return String(bytes, StandardCharsets.UTF_8)
    }
}
