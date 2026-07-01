package karst.vpn.core

import karst.vpn.data.entities.ServerEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConfigBuilderTest {

    @Test
    fun testBuildConfigStructure() {
        val server = ServerEntity(
            id = "test-id",
            subscriptionId = null,
            displayName = "Test Server",
            rawLink = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none",
            outboundConfigJson = """{"type":"vless","tag":"proxy","server":"example.com"}""",
            host = "example.com",
            port = 443,
            sortOrder = 0,
            latencyMs = null,
            latencyStatus = "UNTESTED",
            latencyMeasuredAtEpochMs = null,
            addedAtEpochMs = 123456789L
        )

        val configStr = VpnConfigBuilder.build(server)
        val config = Json.parseToJsonElement(configStr).jsonObject

        assertTrue(config.containsKey("dns"))
        assertTrue(config.containsKey("inbounds"))
        assertTrue(config.containsKey("outbounds"))
        assertTrue(config.containsKey("route"))
        assertTrue(config.containsKey("experimental"))

        val outbounds = config["outbounds"]!!.jsonArray
        assertEquals(2, outbounds.size)
        assertEquals("vless", outbounds[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("proxy", outbounds[0].jsonObject["tag"]?.jsonPrimitive?.content)
        assertEquals("direct", outbounds[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("direct", outbounds[1].jsonObject["tag"]?.jsonPrimitive?.content)

        val dns = config["dns"]!!.jsonObject
        assertEquals("remote-doh", dns["final"]?.jsonPrimitive?.content)
        assertTrue(dns["independent_cache"]!!.jsonPrimitive.boolean)

        val dnsServers = dns["servers"]!!.jsonArray
        assertEquals(2, dnsServers.size)
        assertEquals("remote-doh", dnsServers[0].jsonObject["tag"]?.jsonPrimitive?.content)
        assertEquals("https://1.1.1.1/dns-query", dnsServers[0].jsonObject["address"]?.jsonPrimitive?.content)
        assertEquals("direct", dnsServers[0].jsonObject["detour"]?.jsonPrimitive?.content)
        assertEquals("local-dns", dnsServers[1].jsonObject["tag"]?.jsonPrimitive?.content)
        assertEquals("local", dnsServers[1].jsonObject["address"]?.jsonPrimitive?.content)

        val dnsRules = dns["rules"]!!.jsonArray
        assertTrue(dnsRules.size >= 2)
        assertEquals("local-dns", dnsRules[0].jsonObject["server"]?.jsonPrimitive?.content)
        assertEquals("local-dns", dnsRules[1].jsonObject["server"]?.jsonPrimitive?.content)

        val route = config["route"]!!.jsonObject
        assertEquals("proxy", route["final"]?.jsonPrimitive?.content)
        assertTrue(route["auto_detect_interface"]!!.jsonPrimitive.boolean)

        val routeRules = route["rules"]!!.jsonArray
        assertTrue(routeRules.size >= 5)

        val dnsHijackRule = routeRules.firstOrNull { it.jsonObject["protocol"]?.jsonPrimitive?.content == "dns" }
        assertNotNull(dnsHijackRule)
        assertEquals("hijack-dns", dnsHijackRule!!.jsonObject["action"]?.jsonPrimitive?.content)

        val privateIpRule = routeRules.firstOrNull { it.jsonObject["ip_is_private"]?.jsonPrimitive?.boolean == true }
        assertNotNull(privateIpRule)
        assertEquals("direct", privateIpRule!!.jsonObject["outbound"]?.jsonPrimitive?.content)

        val ruDomainRule = routeRules.firstOrNull {
            val suffixes = it.jsonObject["domain_suffix"]?.jsonArray
            suffixes != null && suffixes.any { s -> s.jsonPrimitive.content == ".ru" }
        }
        assertNotNull(ruDomainRule)
        assertEquals("direct", ruDomainRule!!.jsonObject["outbound"]?.jsonPrimitive?.content)
    }
}
