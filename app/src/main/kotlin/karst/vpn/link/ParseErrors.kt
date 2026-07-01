package karst.vpn.link

sealed class ParseError(message: String) : IllegalArgumentException(message)

class InvalidSchemeError : ParseError("Ожидается ссылка vless://")

class InvalidVlessLinkError(message: String) : ParseError(message)

class UnsupportedTransportError(
    val transport: String,
) : ParseError("Транспорт $transport не поддерживается sing-box")
