package karst.vpn.link

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SingBoxOutboundBuilder {
    fun build(link: VlessLink, tag: String = "proxy"): JsonObject = buildJsonObject {
        put("type", "vless")
        put("tag", tag)
        put("server", link.host)
        put("server_port", link.port)
        put("uuid", link.uuid)
        link.flow?.let { put("flow", it) }
        put("packet_encoding", "xudp")

        buildTls(link)?.let { put("tls", it) }
        buildTransport(link)?.let { put("transport", it) }
    }

    private fun buildTls(link: VlessLink): JsonObject? {
        if (link.security == SecurityMode.None) return null
        return buildJsonObject {
            put("enabled", true)
            (link.serverName ?: link.hostHeader ?: link.host).takeIf { it.isNotBlank() }?.let {
                put("server_name", it)
            }
            put("insecure", link.allowInsecure)
            if (link.alpn.isNotEmpty()) {
                put("alpn", link.alpn.toJsonArray())
            }
            link.fingerprint?.let {
                put(
                    "utls",
                    buildJsonObject {
                        put("enabled", true)
                        put("fingerprint", it)
                    },
                )
            }
            if (link.security == SecurityMode.Reality) {
                put(
                    "reality",
                    buildJsonObject {
                        put("enabled", true)
                        put("public_key", link.publicKey.orEmpty())
                        link.shortId?.let { put("short_id", it) }
                    },
                )
            }
        }
    }

    private fun buildTransport(link: VlessLink): JsonObject? =
        when (link.transport) {
            TransportType.Tcp -> null
            TransportType.Ws -> buildJsonObject {
                put("type", "ws")
                link.path?.let { put("path", it) }
                link.hostHeader?.let {
                    put("headers", buildJsonObject { put("Host", it) })
                }
            }
            TransportType.Grpc -> buildJsonObject {
                put("type", "grpc")
                link.serviceName?.let { put("service_name", it) }
            }
            TransportType.Http -> buildJsonObject {
                put("type", "http")
                link.hostHeader?.let { put("host", it.split(',').map(String::trim).filter(String::isNotEmpty).toJsonArray()) }
                link.path?.let { put("path", it) }
            }
            TransportType.HttpUpgrade -> buildJsonObject {
                put("type", "httpupgrade")
                link.hostHeader?.let { put("host", it) }
                link.path?.let { put("path", it) }
            }
        }
}

private fun List<String>.toJsonArray(): JsonArray =
    buildJsonArray {
        forEach { add(JsonPrimitive(it)) }
    }
