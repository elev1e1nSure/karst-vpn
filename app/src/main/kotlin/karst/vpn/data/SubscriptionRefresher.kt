package karst.vpn.data

import androidx.room.withTransaction
import java.util.UUID
import karst.vpn.link.SubscriptionParser
import karst.vpn.link.toImportSummary
import karst.vpn.link.toServerEntity
import karst.vpn.net.SubscriptionFetcher

class SubscriptionRefresher(
    private val database: KarstDatabase,
    private val fetcher: SubscriptionFetcher,
) {
    private val servers = database.servers()
    private val subscriptions = database.subscriptions()

    suspend fun refresh(id: String): Result<ImportSummary> = runCatching {
        try {
            val subscription = subscriptions.getById(id) ?: error("Подписка не найдена")
            val fetchResult = fetcher.fetch(subscription.url).getOrThrow()
            val parsed = SubscriptionParser.parse(fetchResult.body)
            require(parsed.links.isNotEmpty()) {
                "Обновление не применено: подписка не вернула VLESS-серверы"
            }
            val now = System.currentTimeMillis()
            val startOrder = servers.maxSortOrder() + 1
            val serverEntities = parsed.links.mapIndexed { index, link ->
                link.toServerEntity(
                    id = UUID.randomUUID().toString(),
                    subscriptionId = id,
                    sortOrder = startOrder + index,
                    now = now,
                )
            }

            database.withTransaction {
                servers.deleteBySubscriptionId(id)
                servers.upsertAll(serverEntities)
                subscriptions.upsert(
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

            parsed.toImportSummary(serverEntities.firstOrNull()?.id)
        } catch (e: Throwable) {
            subscriptions.updateRefreshResult(id, null, e.message)
            throw e
        }
    }
}
