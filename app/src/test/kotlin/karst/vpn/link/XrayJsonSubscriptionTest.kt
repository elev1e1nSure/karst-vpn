package karst.vpn.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayJsonSubscriptionTest {

    @Test
    fun testReturnsNullForNonJsonBody() {
        assertNull(XrayJsonSubscription.extractVlessUris("vless://uuid@host:443?security=none#tag"))
        assertNull(XrayJsonSubscription.extractVlessUris(""))
    }

    @Test
    fun testReturnsEmptyListWhenNoVlessOutbounds() {
        val body = """{"outbounds":[{"protocol":"freedom"}]}"""
        assertEquals(emptyList<String>(), XrayJsonSubscription.extractVlessUris(body))
    }

    @Test
    fun testExtractsBasicTcpOutbound() {
        val body = """
            {
              "remarks": "MyServer",
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "example.com",
                        "port": 443,
                        "users": [{"id": "11111111-1111-4111-8111-111111111111", "flow": "xtls-rprx-vision"}]
                      }
                    ]
                  },
                  "streamSettings": { "network": "tcp", "security": "none" }
                }
              ]
            }
        """.trimIndent()

        val uris = XrayJsonSubscription.extractVlessUris(body)
        assertEquals(1, uris?.size)
        val uri = uris!![0]
        assertTrue(uri.startsWith("vless://11111111-1111-4111-8111-111111111111@example.com:443?"))
        assertTrue(uri.contains("type=tcp"))
        assertTrue(uri.contains("flow=xtls-rprx-vision"))
        assertTrue(uri.endsWith("#MyServer"))
    }

    @Test
    fun testExtractsTlsRealitySettings() {
        val body = """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "tag": "reality-node",
                  "settings": {
                    "vnext": [
                      { "address": "1.2.3.4", "port": 8443, "users": [{"id": "22222222-2222-4222-8222-222222222222"}] }
                    ]
                  },
                  "streamSettings": {
                    "network": "tcp",
                    "security": "reality",
                    "realitySettings": {
                      "serverName": "reality.example.com",
                      "fingerprint": "chrome",
                      "publicKey": "pbk-value",
                      "shortId": "sid-value"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val uris = XrayJsonSubscription.extractVlessUris(body)
        val uri = uris!![0]
        assertTrue(uri.contains("security=reality"))
        assertTrue(uri.contains("sni=reality.example.com"))
        assertTrue(uri.contains("fp=chrome"))
        assertTrue(uri.contains("pbk=pbk-value"))
        assertTrue(uri.contains("sid=sid-value"))
        assertTrue(uri.endsWith("#reality-node"))
    }

    @Test
    fun testExtractsWsTransportSettings() {
        val body = """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      { "address": "ws.example.com", "port": 80, "users": [{"id": "33333333-3333-4333-8333-333333333333"}] }
                    ]
                  },
                  "streamSettings": {
                    "network": "ws",
                    "wsSettings": { "path": "/ray", "headers": { "Host": "ws.example.com" } }
                  }
                }
              ]
            }
        """.trimIndent()

        val uri = XrayJsonSubscription.extractVlessUris(body)!![0]
        assertTrue(uri.contains("type=ws"))
        assertTrue(uri.contains("path=%2Fray"))
        assertTrue(uri.contains("host=ws.example.com"))
    }

    @Test
    fun testExtractsGrpcTransportSettings() {
        val body = """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      { "address": "grpc.example.com", "port": 443, "users": [{"id": "44444444-4444-4444-8444-444444444444"}] }
                    ]
                  },
                  "streamSettings": {
                    "network": "grpc",
                    "grpcSettings": { "serviceName": "MyService" }
                  }
                }
              ]
            }
        """.trimIndent()

        val uri = XrayJsonSubscription.extractVlessUris(body)!![0]
        assertTrue(uri.contains("type=grpc"))
        assertTrue(uri.contains("serviceName=MyService"))
    }

    @Test
    fun testMapsH2NetworkToHttpQueryType() {
        val body = """
            {
              "outbounds": [
                {
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      { "address": "h2.example.com", "port": 443, "users": [{"id": "55555555-5555-4555-8555-555555555555"}] }
                    ]
                  },
                  "streamSettings": {
                    "network": "h2",
                    "httpSettings": { "path": "/h2path", "host": ["h2.example.com"] }
                  }
                }
              ]
            }
        """.trimIndent()

        val uri = XrayJsonSubscription.extractVlessUris(body)!![0]
        assertTrue(uri.contains("type=http"))
        assertTrue(uri.contains("path=%2Fh2path"))
        assertTrue(uri.contains("host=h2.example.com"))
    }

    @Test
    fun testSkipsOutboundsMissingRequiredFields() {
        val body = """
            {
              "outbounds": [
                { "protocol": "vless", "settings": { "vnext": [] } }
              ]
            }
        """.trimIndent()

        assertEquals(emptyList<String>(), XrayJsonSubscription.extractVlessUris(body))
    }

    @Test
    fun testHandlesArrayOfProfiles() {
        val body = """
            [
              {
                "remarks": "First",
                "outbounds": [
                  {
                    "protocol": "vless",
                    "settings": { "vnext": [{ "address": "a.com", "port": 443, "users": [{"id": "11111111-1111-4111-8111-111111111111"}] }] },
                    "streamSettings": { "network": "tcp", "security": "none" }
                  }
                ]
              },
              {
                "remarks": "Second",
                "outbounds": [
                  {
                    "protocol": "vless",
                    "settings": { "vnext": [{ "address": "b.com", "port": 443, "users": [{"id": "22222222-2222-4222-8222-222222222222"}] }] },
                    "streamSettings": { "network": "tcp", "security": "none" }
                  }
                ]
              }
            ]
        """.trimIndent()

        val uris = XrayJsonSubscription.extractVlessUris(body)
        assertEquals(2, uris?.size)
        assertTrue(uris!![0].endsWith("#First"))
        assertTrue(uris[1].endsWith("#Second"))
    }
}
