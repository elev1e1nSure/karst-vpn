package karst.vpn.data

import androidx.room.withTransaction
import java.util.UUID
import karst.vpn.data.entities.SubscriptionEntity
import karst.vpn.link.SubscriptionParser
import karst.vpn.link.parseVlessLink
import karst.vpn.link.toImportSummary
import karst.vpn.link.toServerEntity
import karst.vpn.net.SubscriptionFetcher

data class ImportResult(
    val firstServerId: String?,
    val message: String,
)

class ImportCoordinator(
    private val database: KarstDatabase,
    private val fetcher: SubscriptionFetcher,
) {
    private val servers = database.servers()
    private val subscriptions = database.subscriptions()

    suspend fun importInput(rawInput: String): Result<ImportResult> = runCatching {
        val text = rawInput.trim()
        when {
            text.startsWith("vless://", ignoreCase = true) -> importManualServer(text)
            text.startsWith("https://", ignoreCase = true) -> importSubscription(text).toImportResult()
            text.startsWith("http://", ignoreCase = true) -> throw IllegalArgumentException("Подписка должна использовать HTTPS")
            else -> throw IllegalArgumentException("Не похоже на VLESS-ссылку или HTTPS URL подписки")
        }
    }

    private suspend fun importManualServer(rawLink: String): ImportResult {
        val link = parseVlessLink(rawLink).getOrThrow()
        val now = System.currentTimeMillis()
        val entity = link.toServerEntity(
            id = UUID.randomUUID().toString(),
            subscriptionId = null,
            sortOrder = servers.maxSortOrder() + 1,
            now = now,
        )
        servers.upsert(entity)
        return ImportResult(firstServerId = entity.id, message = "Сервер добавлен")
    }

    private suspend fun importSubscription(url: String): ImportSummary {
        val existing = subscriptions.getByUrl(url)
        if (existing != null) {
            return refreshExistingSubscription(existing)
        }
        return importNewSubscription(url)
    }

    private suspend fun importNewSubscription(url: String): ImportSummary {
        val fetchResult = fetcher.fetch(url).getOrThrow()
        val parsed = SubscriptionParser.parse(fetchResult.body)
        val now = System.currentTimeMillis()
        val subscriptionId = UUID.randomUUID().toString()
        val startOrder = servers.maxSortOrder() + 1
        val serverEntities = parsed.links.mapIndexed { index, link ->
            link.toServerEntity(
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
                    displayName = fetchResult.metadata.profileTitle ?: subscriptionDisplayName(url),
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
                    createdAtEpochMs = now,
                ),
            )
            servers.upsertAll(serverEntities)
        }

        return parsed.toImportSummary(serverEntities.firstOrNull()?.id)
    }

    private suspend fun refreshExistingSubscription(subscription: SubscriptionEntity): ImportSummary {
        val fetchResult = fetcher.fetch(subscription.url).getOrThrow()
        val parsed = SubscriptionParser.parse(fetchResult.body)
        val now = System.currentTimeMillis()
        val startOrder = servers.maxSortOrder() + 1
        val serverEntities = parsed.links.mapIndexed { index, link ->
            link.toServerEntity(
                id = UUID.randomUUID().toString(),
                subscriptionId = subscription.id,
                sortOrder = startOrder + index,
                now = now,
            )
        }

        database.withTransaction {
            servers.deleteBySubscriptionId(subscription.id)
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

        val summary = parsed.toImportSummary(serverEntities.firstOrNull()?.id)
        return summary.copy(message = "Подписка уже была добавлена, обновлено")
    }
}

private fun ImportSummary.toImportResult(): ImportResult =
    ImportResult(firstServerId = firstServerId, message = message)
