package karst.vpn.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessUriParserTest {
    @Test
    fun parsesTcpNone() {
        val link = parseVlessLink("$BASE@example.com:443?encryption=none&type=tcp&security=none#Plain").getOrThrow()

        assertEquals("example.com", link.host)
        assertEquals(443, link.port)
        assertEquals(SecurityMode.None, link.security)
        assertEquals(TransportType.Tcp, link.transport)
        assertEquals("Plain", link.remark)
    }

    @Test
    fun parsesTcpTls() {
        val link = parseVlessLink("$BASE@example.com:443?security=tls&sni=front.example.com&fp=chrome&alpn=h2,http/1.1").getOrThrow()

        assertEquals(SecurityMode.Tls, link.security)
        assertEquals("front.example.com", link.serverName)
        assertEquals("chrome", link.fingerprint)
        assertEquals(listOf("h2", "http/1.1"), link.alpn)
    }

    @Test
    fun parsesTcpRealityVisionFlow() {
        val link = parseVlessLink("$BASE@[2001:db8::1]:443?security=reality&type=tcp&pbk=publicKey&sid=&flow=xtls-rprx-vision&sni=www.example.com").getOrThrow()

        assertEquals("2001:db8::1", link.host)
        assertEquals(SecurityMode.Reality, link.security)
        assertEquals("publicKey", link.publicKey)
        assertEquals("", link.shortId)
        assertEquals("xtls-rprx-vision", link.flow)
    }

    @Test
    fun parsesWsTls() {
        val link = parseVlessLink("$BASE@example.com:443?security=tls&type=ws&host=edge.example.com&path=%2Fws").getOrThrow()

        assertEquals(TransportType.Ws, link.transport)
        assertEquals("edge.example.com", link.hostHeader)
        assertEquals("/ws", link.path)
    }

    @Test
    fun parsesGrpcReality() {
        val link = parseVlessLink("$BASE@example.com:443?security=reality&type=grpc&serviceName=TunService&pbk=publicKey").getOrThrow()

        assertEquals(TransportType.Grpc, link.transport)
        assertEquals("TunService", link.serviceName)
        assertEquals("publicKey", link.publicKey)
    }

    @Test
    fun rejectsMalformedUuid() {
        val result = parseVlessLink("vless://not-a-uuid@example.com:443?security=none")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is InvalidVlessLinkError)
    }

    @Test
    fun rejectsXhttpTransport() {
        val result = parseVlessLink("$BASE@example.com:443?security=tls&type=xhttp")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is UnsupportedTransportError)
    }

    @Test
    fun rejectsVisionFlowForNonTcpTransport() {
        val result = parseVlessLink("$BASE@example.com:443?security=tls&type=ws&flow=xtls-rprx-vision")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is InvalidVlessLinkError)
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"
        const val BASE = "vless://$UUID"
    }
}
