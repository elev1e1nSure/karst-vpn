package karst.vpn.link

import karst.vpn.data.ImportSummary
import karst.vpn.data.LatencyStatus
import karst.vpn.data.entities.ServerEntity

fun VlessLink.toServerEntity(
    id: String,
    subscriptionId: String?,
    sortOrder: Int,
    now: Long,
): ServerEntity =
    ServerEntity(
        id = id,
        subscriptionId = subscriptionId,
        displayName = remark,
        rawLink = raw,
        outboundConfigJson = SingBoxOutboundBuilder.build(this).toString(),
        host = host,
        port = port,
        sortOrder = sortOrder,
        latencyMs = null,
        latencyStatus = LatencyStatus.Untested,
        latencyMeasuredAtEpochMs = null,
        addedAtEpochMs = now,
    )

fun SubscriptionParseResult.toImportSummary(firstServerId: String?): ImportSummary {
    val skipped = failures.size
    val imported = links.size
    val reason = failures.firstOrNull()?.reason
    val message = if (skipped == 0) {
        "Импортировано: $imported"
    } else {
        "Импортировано: $imported, пропущено: $skipped${reason?.let { " ($it)" }.orEmpty()}"
    }
    return ImportSummary(imported = imported, skipped = skipped, firstServerId = firstServerId, message = message)
}
