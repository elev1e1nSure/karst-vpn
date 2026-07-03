package karst.vpn.data

import karst.vpn.link.SecurityMode
import karst.vpn.link.SubscriptionParseFailure
import karst.vpn.link.SubscriptionParseResult
import karst.vpn.link.TransportType
import karst.vpn.link.VlessLink
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerEntityMapperTest {

    private fun link(remark: String, raw: String) = VlessLink(
        raw = raw,
        uuid = "11111111-1111-4111-8111-111111111111",
        host = "example.com",
        port = 443,
        encryption = "none",
        security = SecurityMode.None,
        transport = TransportType.Tcp,
        remark = remark,
        serverName = null,
        fingerprint = null,
        alpn = emptyList(),
        publicKey = null,
        shortId = null,
        spiderX = null,
        flow = null,
        hostHeader = null,
        path = null,
        serviceName = null,
        allowInsecure = false,
    )

    @Test
    fun testToServerEntityMapsFieldsAndDefaultsLatency() {
        val entity = link("MyLink", "vless://raw").toServerEntity(
            id = "server-1",
            subscriptionId = "sub-1",
            sortOrder = 3,
            now = 1000L,
        )

        assertEquals("server-1", entity.id)
        assertEquals("sub-1", entity.subscriptionId)
        assertEquals("MyLink", entity.displayName)
        assertEquals("vless://raw", entity.rawLink)
        assertEquals("example.com", entity.host)
        assertEquals(443, entity.port)
        assertEquals(3, entity.sortOrder)
        assertEquals(LatencyStatus.Untested, entity.latencyStatus)
        assertEquals(null, entity.latencyMs)
        assertEquals(null, entity.latencyMeasuredAtEpochMs)
        assertEquals(1000L, entity.addedAtEpochMs)
    }

    @Test
    fun testToImportSummaryWithoutFailures() {
        val result = SubscriptionParseResult(
            links = listOf(link("A", "vless://a"), link("B", "vless://b")),
            failures = emptyList(),
        )
        val summary = result.toImportSummary(firstServerId = "id-1")

        assertEquals(2, summary.imported)
        assertEquals(0, summary.skipped)
        assertEquals("id-1", summary.firstServerId)
        assertEquals("Импортировано: 2", summary.message)
    }

    @Test
    fun testToImportSummaryWithFailuresIncludesReason() {
        val result = SubscriptionParseResult(
            links = listOf(link("A", "vless://a")),
            failures = listOf(SubscriptionParseFailure(line = "bad-line", reason = "Некорректная ссылка")),
        )
        val summary = result.toImportSummary(firstServerId = "id-1")

        assertEquals(1, summary.imported)
        assertEquals(1, summary.skipped)
        assertEquals("Импортировано: 1, пропущено: 1 (Некорректная ссылка)", summary.message)
    }

    @Test
    fun testToImportSummaryWithMultipleFailuresUsesFirstReason() {
        val result = SubscriptionParseResult(
            links = emptyList(),
            failures = listOf(
                SubscriptionParseFailure(line = "bad-1", reason = "Причина 1"),
                SubscriptionParseFailure(line = "bad-2", reason = "Причина 2"),
            ),
        )
        val summary = result.toImportSummary(firstServerId = null)

        assertEquals(0, summary.imported)
        assertEquals(2, summary.skipped)
        assertEquals(null, summary.firstServerId)
        assertEquals("Импортировано: 0, пропущено: 2 (Причина 1)", summary.message)
    }
}
