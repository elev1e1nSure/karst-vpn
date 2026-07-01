package karst.vpn.link

data class VlessLink(
    val raw: String,
    val uuid: String,
    val host: String,
    val port: Int,
    val encryption: String,
    val security: SecurityMode,
    val transport: TransportType,
    val remark: String,
    val serverName: String?,
    val fingerprint: String?,
    val alpn: List<String>,
    val publicKey: String?,
    val shortId: String?,
    val spiderX: String?,
    val flow: String?,
    val hostHeader: String?,
    val path: String?,
    val serviceName: String?,
    val allowInsecure: Boolean,
)

enum class SecurityMode(val wireValue: String) {
    None("none"),
    Tls("tls"),
    Reality("reality"),
}

enum class TransportType(val wireValue: String) {
    Tcp("tcp"),
    Ws("ws"),
    Grpc("grpc"),
    Http("http"),
    HttpUpgrade("httpupgrade"),
}
