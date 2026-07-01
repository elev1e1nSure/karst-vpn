package karst.vpn.net

import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

data class SubscriptionFetchResult(
    val body: String,
    val metadata: SubscriptionMetadata = SubscriptionMetadata(),
)

data class SubscriptionMetadata(
    val profileTitle: String? = null,
    val announce: String? = null,
    val profileUpdateIntervalHours: Int? = null,
    val profileWebPageUrl: String? = null,
    val routingEnabled: Boolean? = null,
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireAtEpochSeconds: Long? = null,
)

interface SubscriptionFetcher {
    fun fetch(url: String): Result<SubscriptionFetchResult>
}

class NetworkSubscriptionFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
) : SubscriptionFetcher {
    override fun fetch(url: String): Result<SubscriptionFetchResult> = runCatching {
        requireHttpsUrl(url)
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body
            if (body.contentLength() > MAX_SUBSCRIPTION_BYTES) {
                throw IOException("Subscription response is too large")
            }
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val buffer = Buffer()
            var total = 0L
            val source = body.source()
            while (true) {
                val read = source.read(buffer, READ_CHUNK_BYTES)
                if (read == -1L) break
                total += read
                if (total > MAX_SUBSCRIPTION_BYTES) {
                    throw IOException("Subscription response is too large")
                }
            }
            SubscriptionFetchResult(
                body = buffer.readString(charset),
                metadata = SubscriptionMetadata(
                    profileTitle = response.header("Profile-Title")?.decodeHeaderValue(),
                    announce = response.header("Announce")?.decodeHeaderValue(),
                    profileUpdateIntervalHours = response.header("Profile-Update-Interval")?.toIntOrNull(),
                    profileWebPageUrl = response.header("Profile-Web-Page-Url")?.takeIf { it.isNotBlank() },
                    routingEnabled = response.header("Routing-Enable")?.toBooleanStrictOrNull(),
                    uploadBytes = response.header("Subscription-Userinfo").parseUserInfoField("upload"),
                    downloadBytes = response.header("Subscription-Userinfo").parseUserInfoField("download"),
                    totalBytes = response.header("Subscription-Userinfo").parseUserInfoField("total"),
                    expireAtEpochSeconds = response.header("Subscription-Userinfo").parseUserInfoField("expire"),
                ),
            )
        }
    }

    private fun requireHttpsUrl(url: String) {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IOException("Subscription URL must use HTTPS")
        }
    }

    private companion object {
        const val MAX_SUBSCRIPTION_BYTES = 1L * 1024L * 1024L
        const val READ_CHUNK_BYTES = 8L * 1024L
    }
}

private fun String.decodeHeaderValue(): String? {
    val raw = trim()
    val decoded = if (raw.startsWith("base64:", ignoreCase = true)) {
        val payload = raw.substringAfter(':')
        runCatching { Base64.getDecoder().decode(payload).toString(Charsets.UTF_8) }.getOrNull()
    } else {
        raw
    }
    return decoded?.trim()?.takeIf { it.isNotBlank() }
}

private fun String?.parseUserInfoField(name: String): Long? =
    this
        ?.split(';')
        ?.asSequence()
        ?.map { it.trim() }
        ?.mapNotNull { part ->
            val key = part.substringBefore('=', missingDelimiterValue = "").trim()
            val value = part.substringAfter('=', missingDelimiterValue = "").trim()
            if (key == name) value.toLongOrNull() else null
        }
        ?.firstOrNull()
