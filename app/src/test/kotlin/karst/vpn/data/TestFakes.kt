package karst.vpn.data

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.util.concurrent.Executor
import karst.vpn.data.dao.ServerDao
import karst.vpn.data.dao.ServerWithSubscription
import karst.vpn.data.dao.SubscriptionDao
import karst.vpn.data.entities.ServerEntity
import karst.vpn.data.entities.SubscriptionEntity
import karst.vpn.net.LatencyProbe
import karst.vpn.net.LatencyResult
import karst.vpn.net.SubscriptionFetchResult
import karst.vpn.net.SubscriptionFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.mockito.Mockito.mock

internal class FakeDatabaseState {
    val servers = mutableMapOf<String, ServerEntity>()
    val subscriptions = mutableMapOf<String, SubscriptionEntity>()
    val serversFlow = MutableStateFlow<List<ServerEntity>>(emptyList())
    val subscriptionsFlow = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
}

internal class FakeServerDao(private val db: FakeDatabaseState) : ServerDao {
    override suspend fun upsert(server: ServerEntity) {
        db.servers[server.id] = server
        db.serversFlow.value = db.servers.values.toList()
    }
    override suspend fun upsertAll(servers: List<ServerEntity>) {
        servers.forEach { db.servers[it.id] = it }
        db.serversFlow.value = db.servers.values.toList()
    }
    override fun observeAllWithSubscriptions(): Flow<List<ServerWithSubscription>> {
        return db.serversFlow.map { list ->
            list.map { server ->
                val sub = server.subscriptionId?.let { db.subscriptions[it] }
                ServerWithSubscription(
                    server = server,
                    subscriptionName = sub?.displayName,
                    subscriptionAnnounce = sub?.announce,
                    subscriptionUrl = sub?.url,
                    subscriptionProfileUpdateIntervalHours = sub?.profileUpdateIntervalHours,
                    subscriptionProfileWebPageUrl = sub?.profileWebPageUrl,
                    subscriptionRoutingEnabled = sub?.routingEnabled,
                    subscriptionUploadBytes = sub?.uploadBytes,
                    subscriptionDownloadBytes = sub?.downloadBytes,
                    subscriptionTotalBytes = sub?.totalBytes,
                    subscriptionExpireAtEpochSeconds = sub?.expireAtEpochSeconds,
                    subscriptionLastRefreshedAtEpochMs = sub?.lastRefreshedAtEpochMs,
                    subscriptionLastRefreshError = sub?.lastRefreshError,
                )
            }.sortedWith(compareBy({ it.server.sortOrder }, { it.server.addedAtEpochMs }))
        }
    }
    override suspend fun getById(id: String): ServerEntity? = db.servers[id]
    override suspend fun getBySubscriptionId(subscriptionId: String): List<ServerEntity> =
        db.servers.values
            .filter { it.subscriptionId == subscriptionId }
            .sortedWith(compareBy({ it.sortOrder }, { it.addedAtEpochMs }))

    override suspend fun deleteById(id: String) {
        db.servers.remove(id)
        db.serversFlow.value = db.servers.values.toList()
    }
    override suspend fun deleteBySubscriptionId(subscriptionId: String) {
        db.servers.values.removeIf { it.subscriptionId == subscriptionId }
        db.serversFlow.value = db.servers.values.toList()
    }
    override suspend fun maxSortOrder(): Int {
        return db.servers.values.maxOfOrNull { it.sortOrder } ?: -1
    }
    override suspend fun updateLatency(id: String, status: String, latencyMs: Long?, measuredAt: Long?) {
        db.servers[id] = db.servers[id]?.copy(
            latencyStatus = status,
            latencyMs = latencyMs,
            latencyMeasuredAtEpochMs = measuredAt
        ) ?: return
        db.serversFlow.value = db.servers.values.toList()
    }
}

internal class FakeSubscriptionDao(private val db: FakeDatabaseState) : SubscriptionDao {
    override suspend fun upsert(subscription: SubscriptionEntity) {
        db.subscriptions[subscription.id] = subscription
        db.subscriptionsFlow.value = db.subscriptions.values.toList()
    }
    override suspend fun update(subscription: SubscriptionEntity) {
        db.subscriptions[subscription.id] = subscription
        db.subscriptionsFlow.value = db.subscriptions.values.toList()
    }
    override suspend fun deleteById(id: String) {
        db.subscriptions.remove(id)
        db.servers.values.removeIf { it.subscriptionId == id }
        db.serversFlow.value = db.servers.values.toList()
        db.subscriptionsFlow.value = db.subscriptions.values.toList()
    }
    override fun observeAll(): Flow<List<SubscriptionEntity>> {
        return db.subscriptionsFlow.map { list ->
            list.sortedByDescending { it.createdAtEpochMs }
        }
    }
    override suspend fun getById(id: String): SubscriptionEntity? = db.subscriptions[id]
    override suspend fun getByUrl(url: String): SubscriptionEntity? =
        db.subscriptions.values.firstOrNull { it.url == url }
    override suspend fun getAllIds(): List<String> = db.subscriptions.values.map { it.id }
    override suspend fun updateRefreshResult(id: String, refreshedAt: Long?, error: String?) {
        db.subscriptions[id] = db.subscriptions[id]?.copy(
            lastRefreshedAtEpochMs = refreshedAt,
            lastRefreshError = error
        ) ?: return
        db.subscriptionsFlow.value = db.subscriptions.values.toList()
    }
}

@Suppress("OVERRIDE_DEPRECATION")
internal class FakeKarstDatabase(
    private val serverDao: ServerDao,
    private val subscriptionDao: SubscriptionDao
) : KarstDatabase() {
    override fun servers() = serverDao
    override fun subscriptions() = subscriptionDao

    override fun createInvalidationTracker(): InvalidationTracker = mock(InvalidationTracker::class.java)
    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = mock(SupportSQLiteOpenHelper::class.java)
    override fun clearAllTables() {}

    private val directExecutor = Executor { it.run() }
    override val transactionExecutor: Executor = directExecutor
    override val queryExecutor: Executor = directExecutor

    override fun beginTransaction() {}
    override fun endTransaction() {}
    override fun setTransactionSuccessful() {}
}

internal class FakeSubscriptionFetcher : SubscriptionFetcher {
    var result: Result<SubscriptionFetchResult> = Result.success(SubscriptionFetchResult(""))

    override fun fetch(url: String): Result<SubscriptionFetchResult> = result
}

internal class FakeLatencyProbe : LatencyProbe {
    var result: LatencyResult = LatencyResult.Ok(50L)
    var measureCallCount = 0
    override suspend fun measure(host: String, port: Int, timeoutMs: Int): LatencyResult {
        measureCallCount++
        return result
    }
}
