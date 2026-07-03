package karst.vpn.data

import androidx.room.withTransaction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import karst.vpn.data.entities.ServerEntity
import karst.vpn.data.entities.SubscriptionEntity
import karst.vpn.link.SubscriptionParser
import karst.vpn.link.VlessLink
import karst.vpn.link.parseVlessLink
import karst.vpn.log.AppLog
import karst.vpn.net.SubscriptionFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ImportResult(
    val firstServerId: String?,
    val message: String,
)

class SubscriptionSync(
    private val database: KarstDatabase,
    private val fetcher: SubscriptionFetcher,
) {
    private val servers = database.servers()
    private val subscriptions = database.subscriptions()
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun importInput(rawInput: String): Result<ImportResult> = runCatching {
        val text = rawInput.trim()
        AppLog.info(AppLog.Category.LINK, "importInput called with input: $text")
        when {
            text.startsWith("vless://", ignoreCase = true) -> importManualServer(text)
            text.startsWith("https://", ignoreCase = true) -> importSubscription(text).toImportResult()
            text.startsWith("http://", ignoreCase = true) -> throw IllegalArgumentException("Подписка должна использовать HTTPS")
            else -> throw IllegalArgumentException("Не похоже на VLESS-ссылку или HTTPS URL подписки")
        }
    }

    suspend fun refresh(id: String): Result<ImportSummary> =
        refreshLocks.getOrPut(id) { Mutex() }.withLock { refreshLocked(id) }

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

    private suspend fun importManualServer(rawLink: String): ImportResult {
        AppLog.info(AppLog.Category.LINK, "importManualServer: parsing VLESS link")
        val link = parseVlessLink(rawLink).getOrThrow()
        val now = System.currentTimeMillis()
        val entity = link.toServerEntity(
            id = UUID.randomUUID().toString(),
            subscriptionId = null,
            sortOrder = servers.maxSortOrder() + 1,
            now = now,
        )
        servers.upsert(entity)
        AppLog.info(AppLog.Category.LINK, "importManualServer successful. Server id=${entity.id} host=${entity.host}:${entity.port}")
        return ImportResult(firstServerId = entity.id, message = "Сервер добавлен")
    }

    private suspend fun importSubscription(url: String): ImportSummary {
        AppLog.info(AppLog.Category.NET, "importSubscription: looking up existing subscription for URL: $url")
        val existing = subscriptions.getByUrl(url)
        if (existing != null) {
            AppLog.info(AppLog.Category.NET, "importSubscription: subscription already exists (id=${existing.id}), refreshing")
            val summary = refresh(existing.id).getOrThrow()
            AppLog.info(AppLog.Category.NET, "refreshExistingSubscription successful. id=${existing.id}.")
            return summary.copy(message = "Подписка уже была добавлена, обновлено")
        }
        AppLog.info(AppLog.Category.NET, "importSubscription: importing new subscription")
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

        AppLog.info(AppLog.Category.NET, "importNewSubscription successful. id=$subscriptionId. Imported ${serverEntities.size} servers.")
        return parsed.toImportSummary(serverEntities.firstOrNull()?.id)
    }

    private suspend fun refreshLocked(id: String): Result<ImportSummary> = runCatching {
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

private fun ImportSummary.toImportResult(): ImportResult =
    ImportResult(firstServerId = firstServerId, message = message)

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
