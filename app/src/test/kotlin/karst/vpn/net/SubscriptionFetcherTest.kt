package karst.vpn.net

import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubscriptionFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: NetworkSubscriptionFetcher

    @Before
    fun setUp() {
        val localhostCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(localhostCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(localhostCertificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        fetcher = NetworkSubscriptionFetcher(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testFetchSuccess() {
        server.enqueue(MockResponse().setBody("subscription_content"))

        val url = server.url("/sub").toString()
        val result = fetcher.fetch(url)

        assertTrue(result.isSuccess)
        assertEquals("subscription_content", result.getOrThrow().body)

        val request = server.takeRequest()
        assertEquals("/sub", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun testFetchSubscriptionMetadataHeaders() {
        val profileTitle = Base64.getEncoder().encodeToString("Proseka VPN".toByteArray())
        val announce = Base64.getEncoder().encodeToString("Обнови подписку".toByteArray())
        server.enqueue(
            MockResponse()
                .setBody("subscription_content")
                .setHeader("Profile-Title", "base64:$profileTitle")
                .setHeader("Announce", "base64:$announce")
                .setHeader("Profile-Update-Interval", "12")
                .setHeader("Profile-Web-Page-Url", "https://example.com/sub")
                .setHeader("Routing-Enable", "false")
                .setHeader("Subscription-Userinfo", "upload=10; download=20; total=0; expire=1784109514"),
        )

        val url = server.url("/sub").toString()
        val result = fetcher.fetch(url)

        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow().metadata
        assertEquals("Proseka VPN", metadata.profileTitle)
        assertEquals("Обнови подписку", metadata.announce)
        assertEquals(12, metadata.profileUpdateIntervalHours)
        assertEquals("https://example.com/sub", metadata.profileWebPageUrl)
        assertEquals(false, metadata.routingEnabled)
        assertEquals(10L, metadata.uploadBytes)
        assertEquals(20L, metadata.downloadBytes)
        assertEquals(0L, metadata.totalBytes)
        assertEquals(1784109514L, metadata.expireAtEpochSeconds)
    }

    @Test
    fun testFetchErrorStatus() {
        server.enqueue(MockResponse().setResponseCode(500))

        val url = server.url("/sub").toString()
        val result = fetcher.fetch(url)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("HTTP 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun testFetchEmptyBody() {
        server.enqueue(MockResponse().setBody(""))

        val url = server.url("/sub").toString()
        val result = fetcher.fetch(url)

        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow().body)
    }

    @Test
    fun testFetchRedirect() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server.url("/redirected-sub").toString())
        )
        server.enqueue(MockResponse().setBody("redirected_content"))

        val url = server.url("/sub").toString()
        val result = fetcher.fetch(url)

        assertTrue(result.isSuccess)
        assertEquals("redirected_content", result.getOrThrow().body)

        assertEquals(2, server.requestCount)
    }
}
