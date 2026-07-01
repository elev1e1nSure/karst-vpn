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
            val body = fetcher.fetch(subscription.url).getOrThrow()
            val parsed = SubscriptionParser.parse(body)
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
                subscriptions.updateRefreshResult(id, now, null)
            }

            parsed.toImportSummary(serverEntities.firstOrNull()?.id)
        } catch (e: Throwable) {
            subscriptions.updateRefreshResult(id, null, e.message)
            throw e
        }
    }
}
