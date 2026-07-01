package karst.vpn.core

import karst.vpn.data.SettingsRepository
import karst.vpn.data.entities.ServerEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object VpnConfigBuilder {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun build(server: ServerEntity, dnsDohUrl: String = SettingsRepository.DEFAULT_DNS_DOH_URL): String {
        val outbound = json.parseToJsonElement(server.outboundConfigJson).jsonObject
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("dns", dns(dnsDohUrl))
                put("inbounds", buildJsonArray { add(tunInbound()) })
                put(
                    "outbounds",
                    buildJsonArray {
                        add(outbound)
                        add(buildJsonObject { put("type", "direct"); put("tag", "direct") })
                    },
                )
                put("route", route())
                put("experimental", experimental())
            },
        )
    }

    private fun dns(dnsDohUrl: String): JsonObject = buildJsonObject {
        put(
            "servers",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("tag", "remote-doh")
                        put("address", dnsDohUrl)
                        put("detour", "direct")
                    },
                )
                add(buildJsonObject { put("tag", "local-dns"); put("address", "local") })
            },
        )
        put(
            "rules",
            buildJsonArray {
                add(privateDomainDnsRule())
                add(ruDomainDnsRule())
            },
        )
        put("final", "remote-doh")
        put("strategy", "prefer_ipv4")
        put("independent_cache", true)
    }

    private fun tunInbound(): JsonObject = buildJsonObject {
        put("type", "tun")
        put("tag", "tun-in")
        put("interface_name", "tun0")
        put("address", buildJsonArray { add(JsonPrimitive("172.19.0.1/28")) })
        put("mtu", 1500)
        put("auto_route", true)
        put("strict_route", true)
        put("stack", "mixed")
    }

    private fun route(): JsonObject = buildJsonObject {
        put(
            "rules",
            buildJsonArray {
                add(buildJsonObject { put("action", "sniff") })
                add(buildJsonObject { put("protocol", "dns"); put("action", "hijack-dns") })
                add(buildJsonObject { put("ip_is_private", true); put("outbound", "direct") })
                add(privateDomainRouteRule())
                add(ruDomainRouteRule())
            },
        )
        put("final", "proxy")
        put("auto_detect_interface", true)
    }

    private fun experimental(): JsonObject = buildJsonObject {
        put(
            "cache_file",
            buildJsonObject {
                put("enabled", true)
            },
        )
    }

    private fun privateDomainDnsRule(): JsonObject = buildJsonObject {
        put("domain", buildJsonArray { add(JsonPrimitive("localhost")) })
        put("domain_suffix", privateDomainSuffixes())
        put("action", "route")
        put("server", "local-dns")
    }

    private fun ruDomainDnsRule(): JsonObject = buildJsonObject {
        put("domain_suffix", ruDomainSuffixes())
        put("action", "route")
        put("server", "local-dns")
    }

    private fun privateDomainRouteRule(): JsonObject = buildJsonObject {
        put("domain", buildJsonArray { add(JsonPrimitive("localhost")) })
        put("domain_suffix", privateDomainSuffixes())
        put("outbound", "direct")
    }

    private fun ruDomainRouteRule(): JsonObject = buildJsonObject {
        put("domain_suffix", ruDomainSuffixes())
        put("outbound", "direct")
    }

    private fun privateDomainSuffixes() = buildJsonArray {
        add(JsonPrimitive(".local"))
        add(JsonPrimitive(".lan"))
        add(JsonPrimitive(".localdomain"))
        add(JsonPrimitive(".home.arpa"))
        add(JsonPrimitive(".arpa"))
    }

    private fun ruDomainSuffixes() = buildJsonArray {
        add(JsonPrimitive(".ru"))
        add(JsonPrimitive(".su"))
        add(JsonPrimitive(".xn--p1ai"))
    }
}
