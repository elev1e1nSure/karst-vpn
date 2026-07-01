package karst.vpn.net

import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

interface SubscriptionFetcher {
    fun fetch(url: String): Result<String>
}

class NetworkSubscriptionFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
) : SubscriptionFetcher {
    override fun fetch(url: String): Result<String> = runCatching {
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
            buffer.readString(charset)
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
