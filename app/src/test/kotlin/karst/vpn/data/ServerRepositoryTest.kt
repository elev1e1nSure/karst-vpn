package karst.vpn.data

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.util.concurrent.Executor
import karst.vpn.data.dao.ServerDao
import karst.vpn.data.dao.ServerWithSubscription
import karst.vpn.data.dao.SubscriptionDao
import karst.vpn.data.entities.ServerEntity
import karst.vpn.data.entities.SubscriptionEntity
import karst.vpn.net.LatencyProbe
import karst.vpn.net.LatencyResult
import karst.vpn.net.SubscriptionFetcher
import karst.vpn.net.SubscriptionFetchResult
import karst.vpn.net.SubscriptionMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ServerRepositoryTest {

    private val dbState = FakeDatabaseState()
    private val serverDao = FakeServerDao(dbState)
    private val subscriptionDao = FakeSubscriptionDao(dbState)
    private val db = FakeKarstDatabase(serverDao, subscriptionDao)
    private val fetcher = FakeSubscriptionFetcher()
    private val latencyProbe = FakeLatencyProbe()

    private val repository = ServerRepository(db)
    private val importCoordinator = ImportCoordinator(db, fetcher)
    private val subscriptionRefresher = SubscriptionRefresher(db, fetcher)
    private val latencyTracker = LatencyTracker(db, latencyProbe)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAddManualServer() = runTest {
        val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#MyManual"
        val result = importCoordinator.importInput(link)
        assertTrue(result.isSuccess)

        val server = serverDao.getById(result.getOrThrow().firstServerId!!)
        assertNotNull(server)
        assertEquals("MyManual", server!!.displayName)
        assertEquals("example.com", server.host)
        assertEquals(443, server.port)
        assertNull(server.subscriptionId)

        val saved = serverDao.getById(server.id)
        assertNotNull(saved)
        assertEquals("MyManual", saved?.displayName)
    }

    @Test
    fun testAddSubscriptionSuccess() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"
        val vlessList = "vless://$uuid@s1.com:443?security=none#S1\nvless://$uuid@s2.com:443?security=none#S2"

        fetcher.result = Result.success(SubscriptionFetchResult(vlessList))

        val result = importCoordinator.importInput(url)
        assertTrue(result.isSuccess)

        val importResult = result.getOrThrow()
        assertEquals("Импортировано: 2", importResult.message)

        val subs = subscriptionDao.observeAll().first()
        assertEquals(1, subs.size)
        assertEquals("example.com", subs[0].displayName)

        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(2, servers.size)
        assertEquals("S1", servers[0].server.displayName)
        assertEquals("S2", servers[1].server.displayName)
        assertEquals("example.com", servers[0].subscriptionName)
    }

    @Test
    fun testAddSubscriptionUsesProfileTitle() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"
        val vlessList = "vless://$uuid@s1.com:443?security=none#S1"

        fetcher.result = Result.success(
            SubscriptionFetchResult(
                body = vlessList,
                metadata = SubscriptionMetadata(
                    profileTitle = "Proseka VPN",
                    announce = "Обнови подписку",
                    profileUpdateIntervalHours = 12,
                    profileWebPageUrl = url,
                    routingEnabled = false,
                    uploadBytes = 10,
                    downloadBytes = 20,
                    totalBytes = 0,
                    expireAtEpochSeconds = 1784109514,
                ),
            ),
        )

        val result = importCoordinator.importInput(url)
        assertTrue(result.isSuccess)

        val subs = subscriptionDao.observeAll().first()
        assertEquals("Proseka VPN", subs[0].displayName)
        assertEquals("Обнови подписку", subs[0].announce)
        assertEquals(12, subs[0].profileUpdateIntervalHours)
        assertEquals(url, subs[0].profileWebPageUrl)
        assertEquals(false, subs[0].routingEnabled)
        assertEquals(10L, subs[0].uploadBytes)
        assertEquals(20L, subs[0].downloadBytes)
        assertEquals(0L, subs[0].totalBytes)
        assertEquals(1784109514L, subs[0].expireAtEpochSeconds)
    }

    @Test
    fun testAddSubscriptionPartialFailure() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"
        val mixedContent = "vless://$uuid@s1.com:443?security=none#S1\ninvalid-line\nvless://$uuid@s2.com:443?security=none#S2"

        fetcher.result = Result.success(SubscriptionFetchResult(mixedContent))

        val result = importCoordinator.importInput(url)
        assertTrue(result.isSuccess)

        val importResult = result.getOrThrow()
        assertTrue(importResult.message.startsWith("Импортировано: 2, пропущено: 1"))

        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(2, servers.size)
    }

    @Test
    fun testRefreshSubscription() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"

        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s1.com:443?security=none#S1\nvless://$uuid@s2.com:443?security=none#S2"))
        importCoordinator.importInput(url).getOrThrow()
        val subId = subscriptionDao.observeAll().first()[0].id

        fetcher.result = Result.success(
            SubscriptionFetchResult(
                body = "vless://$uuid@s3.com:443?security=none#S3",
                metadata = SubscriptionMetadata(profileTitle = "Updated Sub"),
            ),
        )
        val refreshResult = subscriptionRefresher.refresh(subId)
        assertTrue(refreshResult.isSuccess)

        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(1, servers.size)
        assertEquals("S3", servers[0].server.displayName)
        assertEquals(subId, servers[0].server.subscriptionId)
        assertEquals("Updated Sub", subscriptionDao.observeAll().first()[0].displayName)
    }

    @Test
    fun testRefreshSubscriptionKeepsServersWhenResponseHasNoLinks() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"

        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s1.com:443?security=none#S1"))
        importCoordinator.importInput(url).getOrThrow()
        val subId = subscriptionDao.observeAll().first()[0].id

        fetcher.result = Result.success(SubscriptionFetchResult(""))
        val refreshResult = subscriptionRefresher.refresh(subId)

        assertTrue(refreshResult.isFailure)
        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(1, servers.size)
        assertEquals("S1", servers[0].server.displayName)
        assertEquals(subId, servers[0].server.subscriptionId)
        assertEquals(
            "Обновление не применено: подписка не вернула VLESS-серверы",
            subscriptionDao.observeAll().first()[0].lastRefreshError,
        )
    }

    @Test
    fun testDeleteServer() = runTest {
        val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#Del"
        val serverId = importCoordinator.importInput(link).getOrThrow().firstServerId!!

        assertNotNull(serverDao.getById(serverId))

        repository.deleteServer(serverId)
        assertNull(serverDao.getById(serverId))
    }

    @Test
    fun testTestLatency() = runTest {
        val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#Lat"
        val serverId = importCoordinator.importInput(link).getOrThrow().firstServerId!!

        assertEquals(LatencyStatus.Untested, serverDao.getById(serverId)?.latencyStatus)

        latencyProbe.result = LatencyResult.Ok(45L)
        latencyTracker.testLatency(serverId)
        var updated = serverDao.getById(serverId)
        assertEquals(LatencyStatus.Ok, updated?.latencyStatus)
        assertEquals(45L, updated?.latencyMs)

        latencyProbe.result = LatencyResult.Timeout
        latencyTracker.testLatency(serverId)
        updated = serverDao.getById(serverId)
        assertEquals(LatencyStatus.Timeout, updated?.latencyStatus)
        assertNull(updated?.latencyMs)

        latencyProbe.result = LatencyResult.Error("Failed")
        latencyTracker.testLatency(serverId)
        updated = serverDao.getById(serverId)
        assertEquals(LatencyStatus.Error, updated?.latencyStatus)
        assertNull(updated?.latencyMs)
    }
}

private class FakeDatabaseState {
    val servers = mutableMapOf<String, ServerEntity>()
    val subscriptions = mutableMapOf<String, SubscriptionEntity>()
    val serversFlow = MutableStateFlow<List<ServerEntity>>(emptyList())
    val subscriptionsFlow = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
}

private class FakeServerDao(private val db: FakeDatabaseState) : ServerDao {
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

private class FakeSubscriptionDao(private val db: FakeDatabaseState) : SubscriptionDao {
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
private class FakeKarstDatabase(
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

private class FakeSubscriptionFetcher : SubscriptionFetcher {
    var result: Result<SubscriptionFetchResult> = Result.success(SubscriptionFetchResult(""))

    override fun fetch(url: String): Result<SubscriptionFetchResult> = result
}

private class FakeLatencyProbe : LatencyProbe {
    var result: LatencyResult = LatencyResult.Ok(50L)
    override suspend fun measure(host: String, port: Int, timeoutMs: Int): LatencyResult {
        return result
    }
}
