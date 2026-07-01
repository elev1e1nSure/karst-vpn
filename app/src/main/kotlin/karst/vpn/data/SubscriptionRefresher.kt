package karst.vpn.data

import androidx.room.withTransaction
import java.util.UUID
import karst.vpn.data.entities.ServerEntity
import karst.vpn.link.VlessLink
import karst.vpn.link.SubscriptionParser
import karst.vpn.link.toImportSummary
import karst.vpn.link.toServerEntity
import karst.vpn.net.SubscriptionFetcher
import karst.vpn.log.AppLog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SubscriptionRefresher(
    private val database: KarstDatabase,
    private val fetcher: SubscriptionFetcher,
) {
    private val servers = database.servers()
    private val subscriptions = database.subscriptions()

    suspend fun refreshAll(): Result<ImportSummary> = runCatching {
        AppLog.info(AppLog.Category.NET, "Starting refresh of all subscriptions")
        val ids = subscriptions.getAllIds()
        if (ids.isEmpty()) {
            AppLog.info(AppLog.Category.NET, "No subscriptions to refresh")
            return@runCatching ImportSummary(imported = 0, skipped = 0, firstServerId = null, message = "")
        }
        val summaries = coroutineScope {
            ids.map { id -> async { refresh(id) } }.map { it.await() }
        }
        var totalImported = 0
        var lastFirstServerId: String? = null
        summaries.forEach { result ->
            result.onSuccess { summary ->
                totalImported += summary.imported
                if (summary.firstServerId != null) lastFirstServerId = summary.firstServerId
            }
        }
        AppLog.info(AppLog.Category.NET, "Finished refresh of all subscriptions. Total imported: $totalImported")
        ImportSummary(imported = totalImported, skipped = 0, firstServerId = lastFirstServerId, message = "")
    }

    suspend fun refresh(id: String): Result<ImportSummary> = runCatching {
        try {
            val subscription = subscriptions.getById(id) ?: error("Подписка не найдена")
            AppLog.info(AppLog.Category.NET, "Refreshing subscription: id=$id name=${subscription.displayName}")
            val fetchResult = fetcher.fetch(subscription.url).getOrThrow()
            val parsed = SubscriptionParser.parse(fetchResult.body)
            require(parsed.links.isNotEmpty()) {
                "Обновление не применено: подписка не вернула VLESS-серверы"
            }
            val now = System.currentTimeMillis()
            val existingServers = servers.getBySubscriptionId(id)
            val existingByRawLink = existingServers.associateBy { it.rawLink }
            val startOrder = existingServers.minOfOrNull { it.sortOrder } ?: (servers.maxSortOrder() + 1)
            val serverEntities = parsed.links.mapIndexed { index, link ->
                link.toRefreshServerEntity(
                    existing = existingByRawLink[link.raw],
                    subscriptionId = id,
                    sortOrder = startOrder + index,
                    now = now,
                )
            }

            database.withTransaction {
                servers.deleteBySubscriptionId(id)
                servers.upsertAll(serverEntities)
                subscriptions.update(
                    subscription.copy(
                        displayName = fetchResult.metadata.profileTitle ?: subscription.displayName,
                        announce = fetchResult.metadata.announce,
                        profileUpdateIntervalHours = fetchResult.metadata.profileUpdateIntervalHours,
                        profileWebPageUrl = fetchResult.metadata.profileWebPageUrl,
                        routingEnabled = fetchResult.metadata.routingEnabled,
                        uploadBytes = fetchResult.metadata.uploadBytes,
                        downloadBytes = fetchResult.metadata.downloadBytes,
                        totalBytes = fetchResult.metadata.totalBytes,
                        expireAtEpochSeconds = fetchResult.metadata.expireAtEpochSeconds,
                        lastRefreshedAtEpochMs = now,
                        lastRefreshError = null,
                    ),
                )
            }

            AppLog.info(AppLog.Category.NET, "Subscription refreshed successfully: id=$id name=${subscription.displayName}. Parsed ${parsed.links.size} servers.")
            parsed.toImportSummary(serverEntities.firstOrNull()?.id)
        } catch (e: Throwable) {
            AppLog.error(AppLog.Category.NET, "Failed to refresh subscription: id=$id", e)
            subscriptions.updateRefreshResult(id, null, e.message)
            throw e
        }
    }
}

private fun VlessLink.toRefreshServerEntity(
    existing: ServerEntity?,
    subscriptionId: String,
    sortOrder: Int,
    now: Long,
): ServerEntity {
    val next = toServerEntity(
        id = existing?.id ?: UUID.randomUUID().toString(),
        subscriptionId = subscriptionId,
        sortOrder = sortOrder,
        now = existing?.addedAtEpochMs ?: now,
    )
    return if (existing == null) {
        next
    } else {
        next.copy(
            latencyMs = existing.latencyMs,
            latencyStatus = existing.latencyStatus,
            latencyMeasuredAtEpochMs = existing.latencyMeasuredAtEpochMs,
        )
    }
}
