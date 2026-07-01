package karst.vpn.data

import androidx.room.withTransaction
import java.net.URI
import java.util.UUID
import karst.vpn.data.dao.ServerWithSubscription
import karst.vpn.data.entities.ServerEntity
import karst.vpn.data.entities.SubscriptionEntity
import karst.vpn.link.SingBoxOutboundBuilder
import karst.vpn.link.SubscriptionParser
import karst.vpn.link.parseVlessLink
import karst.vpn.net.LatencyProbe
import karst.vpn.net.LatencyResult
import karst.vpn.net.SubscriptionFetcher
import kotlinx.coroutines.flow.Flow

data class ImportSummary(
    val imported: Int,
    val skipped: Int,
    val firstServerId: String?,
    val message: String,
)

object LatencyStatus {
    const val Untested = "UNTESTED"
    const val Testing = "TESTING"
    const val Ok = "OK"
    const val Timeout = "TIMEOUT"
    const val Error = "ERROR"
}

class ServerRepository(
    private val database: KarstDatabase,
    private val fetcher: SubscriptionFetcher,
    private val latencyProbe: LatencyProbe,
) {
    private val servers = database.servers()
    private val subscriptions = database.subscriptions()

    fun observeServers(): Flow<List<ServerWithSubscription>> =
        servers.observeAllWithSubscriptions()

    suspend fun getServer(id: String): ServerEntity? =
        servers.getById(id)

    suspend fun addManualServer(rawLink: String): Result<ServerEntity> = runCatching {
        val link = parseVlessLink(rawLink).getOrThrow()
        val now = System.currentTimeMillis()
        val entity = link.toEntity(
            id = UUID.randomUUID().toString(),
            subscriptionId = null,
            sortOrder = servers.maxSortOrder() + 1,
            now = now,
        )
        servers.upsert(entity)
        entity
    }

    suspend fun addSubscription(url: String): Result<ImportSummary> = runCatching {
        val body = fetcher.fetch(url).getOrThrow()
        val parsed = SubscriptionParser.parse(body)
        val now = System.currentTimeMillis()
        val subscriptionId = UUID.randomUUID().toString()
        val displayName = subscriptionDisplayName(url)
        val startOrder = servers.maxSortOrder() + 1
        val serverEntities = parsed.links.mapIndexed { index, link ->
            link.toEntity(
                id = UUID.randomUUID().toString(),
                subscriptionId = subscriptionId,
                sortOrder = startOrder + index,
                now = now,
            )
        }

        database.withTransaction {
            subscriptions.upsert(
                SubscriptionEntity(
                    id = subscriptionId,
                    url = url,
                    displayName = displayName,
                    lastRefreshedAtEpochMs = now,
                    lastRefreshError = null,
                    createdAtEpochMs = now,
                ),
            )
            servers.upsertAll(serverEntities)
        }

        parsed.toImportSummary(serverEntities.firstOrNull()?.id)
    }

    suspend fun refreshSubscription(id: String): Result<ImportSummary> = runCatching {
        try {
            val subscription = subscriptions.getById(id) ?: error("Подписка не найдена")
            val body = fetcher.fetch(subscription.url).getOrThrow()
            val parsed = SubscriptionParser.parse(body)
            val now = System.currentTimeMillis()
            val startOrder = servers.maxSortOrder() + 1
            val serverEntities = parsed.links.mapIndexed { index, link ->
                link.toEntity(
                    id = UUID.randomUUID().toString(),
                    subscriptionId = id,
                    sortOrder = startOrder + index,
                    now = now,
                )
            }

            database.withTransaction {
                servers.deleteBySubscriptionId(id)
                servers.upsertAll(serverEntities)
                subscriptions.updateRefreshResult(id, now, null)
            }

            parsed.toImportSummary(serverEntities.firstOrNull()?.id)
        } catch (e: Throwable) {
            subscriptions.updateRefreshResult(id, null, e.message)
            throw e
        }
    }

    suspend fun deleteServer(id: String) {
        servers.deleteById(id)
    }

    suspend fun deleteSubscription(subscription: SubscriptionEntity) {
        subscriptions.delete(subscription)
    }

    suspend fun testLatency(serverId: String) {
        val server = servers.getById(serverId) ?: return
        servers.updateLatency(serverId, LatencyStatus.Testing, null, null)
        when (val result = latencyProbe.measure(server.host, server.port)) {
            is LatencyResult.Ok -> servers.updateLatency(serverId, LatencyStatus.Ok, result.ms, System.currentTimeMillis())
            LatencyResult.Timeout -> servers.updateLatency(serverId, LatencyStatus.Timeout, null, System.currentTimeMillis())
            is LatencyResult.Error -> servers.updateLatency(serverId, LatencyStatus.Error, null, System.currentTimeMillis())
        }
    }

    private fun subscriptionDisplayName(url: String): String =
        runCatching { URI(url).host?.ifBlank { null } }.getOrNull() ?: url
}

private fun karst.vpn.link.VlessLink.toEntity(
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

private fun karst.vpn.link.SubscriptionParseResult.toImportSummary(firstServerId: String?): ImportSummary {
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
