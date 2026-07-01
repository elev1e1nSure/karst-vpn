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

    @Test
    fun parsesIpv4Ipv6AndDomainHosts() {
        val ipv4Link = parseVlessLink("$BASE@127.0.0.1:443?security=none").getOrThrow()
        assertEquals("127.0.0.1", ipv4Link.host)
        assertEquals(443, ipv4Link.port)

        val ipv6Link = parseVlessLink("$BASE@[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8443?security=none").getOrThrow()
        assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", ipv6Link.host)
        assertEquals(8443, ipv6Link.port)

        val domainLink = parseVlessLink("$BASE@sub.domain-name.co.uk:80?security=none").getOrThrow()
        assertEquals("sub.domain-name.co.uk", domainLink.host)
        assertEquals(80, domainLink.port)
    }

    @Test
    fun parsesPercentDecodedRemarksAndPath() {
        val link = parseVlessLink("$BASE@example.com:443?security=none&path=%2Fmy%20path#My%20Remark%20%D1%82%D0%B5%D1%81%D1%82").getOrThrow()
        assertEquals("/my path", link.path)
        assertEquals("My Remark тест", link.remark)
    }

    @Test
    fun parsesEmptyFragmentFallbackToHost() {
        val link = parseVlessLink("$BASE@example.com:443?security=none#").getOrThrow()
        assertEquals("example.com", link.remark)

        val linkNoFragment = parseVlessLink("$BASE@example.com:443?security=none").getOrThrow()
        assertEquals("example.com", linkNoFragment.remark)
    }

    @Test
    fun parsesNonStandardQueryParamsSilently() {
        val link = parseVlessLink("$BASE@example.com:443?security=none&some-custom-field=123&another=xyz").getOrThrow()
        assertEquals("example.com", link.host)
    }

    @Test
    fun rejectsMissingOrInvalidPort() {
        val noPort = parseVlessLink("$BASE@example.com?security=none")
        assertTrue(noPort.isFailure)
        assertTrue(noPort.exceptionOrNull() is InvalidVlessLinkError)

        val nonNumericPort = parseVlessLink("$BASE@example.com:abc?security=none")
        assertTrue(nonNumericPort.isFailure)

        val zeroPort = parseVlessLink("$BASE@example.com:0?security=none")
        assertTrue(zeroPort.isFailure)

        val outOfRangePort = parseVlessLink("$BASE@example.com:65536?security=none")
        assertTrue(outOfRangePort.isFailure)
    }

    @Test
    fun rejectsRealityWithoutPublicKey() {
        val noPbk = parseVlessLink("$BASE@example.com:443?security=reality")
        assertTrue(noPbk.isFailure)
        assertEquals("security=reality требует параметр pbk", noPbk.exceptionOrNull()?.message)
    }

    @Test
    fun parsesAllSupportedTransportsAndUnsupported() {
        val tcp = parseVlessLink("$BASE@example.com:443?security=none&type=tcp").getOrThrow()
        assertEquals(TransportType.Tcp, tcp.transport)

        val ws = parseVlessLink("$BASE@example.com:443?security=none&type=ws").getOrThrow()
        assertEquals(TransportType.Ws, ws.transport)

        val grpc = parseVlessLink("$BASE@example.com:443?security=none&type=grpc").getOrThrow()
        assertEquals(TransportType.Grpc, grpc.transport)

        val http = parseVlessLink("$BASE@example.com:443?security=none&type=http").getOrThrow()
        assertEquals(TransportType.Http, http.transport)

        val httpupgrade = parseVlessLink("$BASE@example.com:443?security=none&type=httpupgrade").getOrThrow()
        assertEquals(TransportType.HttpUpgrade, httpupgrade.transport)

        val xhttp = parseVlessLink("$BASE@example.com:443?security=none&type=xhttp")
        assertTrue(xhttp.isFailure)
        assertTrue(xhttp.exceptionOrNull() is UnsupportedTransportError)

        val unknown = parseVlessLink("$BASE@example.com:443?security=none&type=unknown_transport")
        assertTrue(unknown.isFailure)
        assertTrue(unknown.exceptionOrNull() is InvalidVlessLinkError)
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"
        const val BASE = "vless://$UUID"
    }
}
