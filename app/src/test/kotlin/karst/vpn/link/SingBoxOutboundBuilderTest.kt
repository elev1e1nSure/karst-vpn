package karst.vpn.link

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxOutboundBuilderTest {

    @Test
    fun testBuildSecurityNoneTcp() {
        val link = parseVlessLink("$BASE@example.com:443?security=none&type=tcp").getOrThrow()
        val outbound = SingBoxOutboundBuilder.build(link)

        assertEquals("vless", outbound["type"]?.jsonPrimitive?.content)
        assertEquals("proxy", outbound["tag"]?.jsonPrimitive?.content)
        assertEquals("example.com", outbound["server"]?.jsonPrimitive?.content)
        assertEquals("443", outbound["server_port"]?.jsonPrimitive?.content)
        assertEquals(UUID, outbound["uuid"]?.jsonPrimitive?.content)
        assertEquals("xudp", outbound["packet_encoding"]?.jsonPrimitive?.content)
        
        assertNull(outbound["flow"])
        assertNull(outbound["tls"])
        assertNull(outbound["transport"])
    }

    @Test
    fun testBuildTlsWs() {
        val link = parseVlessLink("$BASE@example.com:443?security=tls&type=ws&host=edge.com&path=%2Fws&sni=my-sni&alpn=h2,http/1.1&fp=chrome").getOrThrow()
        val outbound = SingBoxOutboundBuilder.build(link)

        val tls = outbound["tls"]?.jsonObject
        assertNotNull(tls)
        assertTrue(tls!!["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("my-sni", tls["server_name"]?.jsonPrimitive?.content)
        assertFalse(tls["insecure"]!!.jsonPrimitive.boolean)
        
        val alpn = tls["alpn"]?.jsonArray
        assertNotNull(alpn)
        assertEquals(2, alpn!!.size)
        assertEquals("h2", alpn[0].jsonPrimitive.content)

        val utls = tls["utls"]?.jsonObject
        assertNotNull(utls)
        assertTrue(utls!!["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("chrome", utls["fingerprint"]?.jsonPrimitive?.content)
        assertNull(tls["reality"])

        val transport = outbound["transport"]?.jsonObject
        assertNotNull(transport)
        assertEquals("ws", transport!!["type"]?.jsonPrimitive?.content)
        assertEquals("/ws", transport["path"]?.jsonPrimitive?.content)
        assertEquals("edge.com", transport["headers"]?.jsonObject?.get("Host")?.jsonPrimitive?.content)
    }

    @Test
    fun testBuildRealityGrpc() {
        val link = parseVlessLink("$BASE@example.com:8443?security=reality&type=grpc&pbk=pubkey123&sid=shortid12&serviceName=test-service").getOrThrow()
        val outbound = SingBoxOutboundBuilder.build(link)

        assertNull(outbound["flow"])

        val tls = outbound["tls"]?.jsonObject
        assertNotNull(tls)
        assertTrue(tls!!["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("example.com", tls["server_name"]?.jsonPrimitive?.content) // Fallback to host
        
        val reality = tls["reality"]?.jsonObject
        assertNotNull(reality)
        assertTrue(reality!!["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("pubkey123", reality["public_key"]?.jsonPrimitive?.content)
        assertEquals("shortid12", reality["short_id"]?.jsonPrimitive?.content)

        val transport = outbound["transport"]?.jsonObject
        assertNotNull(transport)
        assertEquals("grpc", transport!!["type"]?.jsonPrimitive?.content)
        assertEquals("test-service", transport["service_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun testBuildHttpAndHttpUpgrade() {
        // Http
        val linkHttp = parseVlessLink("$BASE@example.com:80?security=none&type=http&host=h1,h2&path=%2Fhttp").getOrThrow()
        val outboundHttp = SingBoxOutboundBuilder.build(linkHttp)
        val transHttp = outboundHttp["transport"]?.jsonObject
        assertNotNull(transHttp)
        assertEquals("http", transHttp!!["type"]?.jsonPrimitive?.content)
        assertEquals("/http", transHttp["path"]?.jsonPrimitive?.content)
        val hosts = transHttp["host"]?.jsonArray
        assertNotNull(hosts)
        assertEquals(2, hosts!!.size)
        assertEquals("h1", hosts[0].jsonPrimitive.content)
        assertEquals("h2", hosts[1].jsonPrimitive.content)

        // HttpUpgrade
        val linkHttpUpgrade = parseVlessLink("$BASE@example.com:80?security=none&type=httpupgrade&host=h1&path=%2Fup").getOrThrow()
        val outboundHttpUpgrade = SingBoxOutboundBuilder.build(linkHttpUpgrade)
        val transUp = outboundHttpUpgrade["transport"]?.jsonObject
        assertNotNull(transUp)
        assertEquals("httpupgrade", transUp!!["type"]?.jsonPrimitive?.content)
        assertEquals("/up", transUp["path"]?.jsonPrimitive?.content)
        assertEquals("h1", transUp["host"]?.jsonPrimitive?.content)
    }

    private companion object {
        const val UUID = "11111111-1111-4111-8111-111111111111"
        const val BASE = "vless://$UUID"
    }
}
