package karst.vpn.link

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Some subscription panels (e.g. Liberty VPN / 3X-UI in "Xray JSON" mode) don't serve the
 * classic base64-encoded `vless://` link list -- they always return a full Xray/v2ray client
 * config: a JSON array of profiles, each with an `outbounds` list. This synthesizes `vless://`
 * URIs from that shape so [SubscriptionParser] can handle it unchanged.
 */
object XrayJsonSubscription {
    /**
     * Returns `null` if [body] doesn't look like JSON at all, so callers fall back to the
     * classic line-based parser. Returns a (possibly empty) list once JSON framing is detected --
     * an empty result means the JSON contained no usable vless outbounds.
     */
    fun extractVlessUris(body: String): List<String>? {
        val trimmed = body.trim()
        if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) return null
        val element = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull() ?: return null
        val profiles: List<JsonObject> = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> return null
        }

        val uris = mutableListOf<String>()
        for (profile in profiles) {
            val outbounds = profile["outbounds"].arrayValue() ?: continue
            val remarks = profile["remarks"].stringValue()
            for (outbound in outbounds.mapNotNull { it as? JsonObject }) {
                if (outbound["protocol"].stringValue() != "vless") continue
                vlessUriFromOutbound(outbound, remarks)?.let(uris::add)
            }
        }
        return uris
    }

    private fun vlessUriFromOutbound(outbound: JsonObject, remarks: String?): String? {
        val vnext = outbound["settings"].objectValue()
            ?.get("vnext").arrayValue()
            ?.firstOrNull() as? JsonObject ?: return null
        val address = vnext["address"].stringValue() ?: return null
        val port = vnext["port"].intValue() ?: return null
        val user = vnext["users"].arrayValue()?.firstOrNull() as? JsonObject ?: return null
        val id = user["id"].stringValue() ?: return null

        val stream = outbound["streamSettings"].objectValue()
        val rawNetwork = stream?.get("network").stringValue() ?: "tcp"
        // Xray's config schema calls HTTP/2 transport "h2"; our own URI param convention uses "http".
        val network = if (rawNetwork == "h2") "http" else rawNetwork
        val security = stream?.get("security").stringValue() ?: "none"

        val query = LinkedHashMap<String, String>()
        query["encryption"] = "none"
        query["type"] = network
        if (security != "none") query["security"] = security
        user["flow"].stringValue()?.takeIf { it.isNotEmpty() }?.let { query["flow"] = it }

        stream?.get("tlsSettings").objectValue()?.let { tls ->
            tls["serverName"].stringValue()?.let { query["sni"] = it }
            tls["fingerprint"].stringValue()?.let { query["fp"] = it }
            tls["alpn"].arrayValue()
                ?.mapNotNull { it.stringValue() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { query["alpn"] = it.joinToString(",") }
            if (tls["allowInsecure"].boolValue() == true) query["allowInsecure"] = "1"
        }
        stream?.get("realitySettings").objectValue()?.let { reality ->
            reality["serverName"].stringValue()?.let { query["sni"] = it }
            reality["fingerprint"].stringValue()?.let { query["fp"] = it }
            reality["publicKey"].stringValue()?.let { query["pbk"] = it }
            reality["shortId"].stringValue()?.let { query["sid"] = it }
        }

        when (network) {
            "ws" -> stream?.get("wsSettings").objectValue()?.let { ws ->
                ws["path"].stringValue()?.let { query["path"] = it }
                ws["headers"].objectValue()?.get("Host").stringValue()?.let { query["host"] = it }
            }
            "grpc" -> stream?.get("grpcSettings").objectValue()?.let { grpc ->
                grpc["serviceName"].stringValue()?.let { query["serviceName"] = it }
            }
            "http", "httpupgrade" -> {
                val key = if (network == "httpupgrade") "httpupgradeSettings" else "httpSettings"
                stream?.get(key).objectValue()?.let { http ->
                    http["path"].stringValue()?.let { query["path"] = it }
                    http["host"].arrayValue()?.firstOrNull().stringValue()?.let { query["host"] = it }
                }
            }
        }

        val queryString = query.entries.joinToString("&") { (key, value) -> "${key.encode()}=${value.encode()}" }
        val name = remarks?.trim()?.takeIf { it.isNotEmpty() } ?: outbound["tag"].stringValue()
        val fragment = name?.let { "#${it.encode()}" }.orEmpty()

        return "vless://${id.encode()}@$address:$port?$queryString$fragment"
    }

    private fun String.encode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.objectValue(): JsonObject? = this as? JsonObject
private fun JsonElement?.arrayValue(): JsonArray? = this as? JsonArray
private fun JsonElement?.intValue(): Int? = (this as? JsonPrimitive)?.intOrNull
private fun JsonElement?.boolValue(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
