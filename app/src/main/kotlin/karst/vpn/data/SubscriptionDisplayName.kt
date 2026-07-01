package karst.vpn.data

import java.net.URI

internal fun subscriptionDisplayName(url: String): String =
    runCatching { URI(url).host?.ifBlank { null } }.getOrNull() ?: url
