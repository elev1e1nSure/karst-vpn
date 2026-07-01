package karst.vpn.link

import java.net.URLDecoder
import java.util.UUID

fun parseVlessLink(raw: String): Result<VlessLink> = runCatching {
    VlessUriParser.parse(raw)
}

object VlessUriParser {
    private const val SCHEME = "vless://"

    fun parse(raw: String): VlessLink {
        val input = raw.trim()
        if (!input.startsWith(SCHEME, ignoreCase = true)) {
            throw InvalidSchemeError()
        }

        val body = input.substring(SCHEME.length)
        val fragmentSplit = body.splitOnce('#')
        val withoutFragment = fragmentSplit.first
        val remark = fragmentSplit.second?.urlDecode().orEmpty()
        val querySplit = withoutFragment.splitOnce('?')
        val authority = querySplit.first
        val query = querySplit.second?.parseQuery().orEmpty()

        val atIndex = authority.indexOf('@')
        if (atIndex <= 0 || atIndex == authority.lastIndex) {
            throw InvalidVlessLinkError("Некорректный userinfo или адрес сервера")
        }

        val uuid = authority.substring(0, atIndex).urlDecode()
        validateUuid(uuid)

        val hostPort = authority.substring(atIndex + 1)
        val (host, port) = parseHostPort(hostPort)
        val encryption = query["encryption"]?.lowercase() ?: "none"
        if (encryption != "none") {
            throw InvalidVlessLinkError("VLESS encryption=$encryption не поддерживается")
        }

        val security = when (val value = query["security"]?.lowercase() ?: "none") {
            "none" -> SecurityMode.None
            "tls" -> SecurityMode.Tls
            "reality" -> SecurityMode.Reality
            else -> throw InvalidVlessLinkError("Неизвестный security=$value")
        }

        val typeValue = query["type"]?.lowercase() ?: "tcp"
        if (typeValue == "xhttp") {
            throw UnsupportedTransportError(typeValue)
        }
        val transport = when (typeValue) {
            "tcp" -> TransportType.Tcp
            "ws" -> TransportType.Ws
            "grpc" -> TransportType.Grpc
            "http" -> TransportType.Http
            "httpupgrade" -> TransportType.HttpUpgrade
            else -> throw InvalidVlessLinkError("Неизвестный transport=$typeValue")
        }

        val flow = query["flow"]?.takeIf { it.isNotBlank() }
        if (flow == "xtls-rprx-vision" && (transport != TransportType.Tcp || security == SecurityMode.None)) {
            throw InvalidVlessLinkError("flow=xtls-rprx-vision допустим только для tcp+tls/reality")
        }
        if (flow != null && flow != "xtls-rprx-vision") {
            throw InvalidVlessLinkError("Неизвестный flow=$flow")
        }

        val publicKey = query["pbk"]?.takeIf { it.isNotBlank() }
        if (security == SecurityMode.Reality && publicKey == null) {
            throw InvalidVlessLinkError("security=reality требует параметр pbk")
        }

        return VlessLink(
            raw = input,
            uuid = uuid,
            host = host,
            port = port,
            encryption = encryption,
            security = security,
            transport = transport,
            remark = remark.ifBlank { host },
            serverName = query["sni"]?.ifBlank { null } ?: query["peer"]?.ifBlank { null },
            fingerprint = query["fp"]?.ifBlank { null },
            alpn = query["alpn"].splitCsv(),
            publicKey = publicKey,
            shortId = if (query.containsKey("sid")) query["sid"].orEmpty() else null,
            spiderX = query["spx"]?.ifBlank { null },
            flow = flow,
            hostHeader = query["host"]?.ifBlank { null },
            path = query["path"]?.ifBlank { null },
            serviceName = query["serviceName"]?.ifBlank { null },
            allowInsecure = query["allowInsecure"].isTruthy(),
        )
    }

    private fun parseHostPort(value: String): Pair<String, Int> {
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            if (end <= 1 || end + 2 > value.length || value[end + 1] != ':') {
                throw InvalidVlessLinkError("Некорректный IPv6 host:port")
            }
            return value.substring(1, end) to parsePort(value.substring(end + 2))
        }

        val colon = value.lastIndexOf(':')
        if (colon <= 0 || colon == value.lastIndex) {
            throw InvalidVlessLinkError("Некорректный host:port")
        }
        if (value.indexOf(':') != colon) {
            throw InvalidVlessLinkError("IPv6 адрес должен быть в квадратных скобках")
        }
        return value.substring(0, colon).urlDecode() to parsePort(value.substring(colon + 1))
    }

    private fun parsePort(value: String): Int {
        val port = value.toIntOrNull() ?: throw InvalidVlessLinkError("Порт должен быть числом")
        if (port !in 1..65535) throw InvalidVlessLinkError("Порт вне диапазона 1..65535")
        return port
    }

    private fun validateUuid(value: String) {
        runCatching { UUID.fromString(value) }
            .getOrElse { throw InvalidVlessLinkError("Некорректный UUID") }
    }
}

private fun String.splitOnce(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    if (index == -1) return this to null
    return substring(0, index) to substring(index + 1)
}

private fun String.parseQuery(): Map<String, String> {
    if (isBlank()) return emptyMap()
    return split('&')
        .filter { it.isNotEmpty() }
        .associate { part ->
            val split = part.splitOnce('=')
            split.first.urlDecode() to split.second.orEmpty().urlDecode()
        }
}

private fun String.urlDecode(): String =
    URLDecoder.decode(this, Charsets.UTF_8.name())

private fun String?.splitCsv(): List<String> =
    this?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

private fun String?.isTruthy(): Boolean =
    equals("true", ignoreCase = true) || this == "1"
