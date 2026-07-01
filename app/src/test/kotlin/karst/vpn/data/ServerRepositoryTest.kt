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

    private val repository = ServerRepository(db, fetcher, latencyProbe)

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
        val result = repository.addManualServer(link)
        assertTrue(result.isSuccess)

        val server = result.getOrThrow()
        assertEquals("MyManual", server.displayName)
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

        fetcher.result = Result.success(vlessList)

        val result = repository.addSubscription(url)
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(2, summary.imported)
        assertEquals(0, summary.skipped)

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
    fun testAddSubscriptionPartialFailure() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"
        val mixedContent = "vless://$uuid@s1.com:443?security=none#S1\ninvalid-line\nvless://$uuid@s2.com:443?security=none#S2"

        fetcher.result = Result.success(mixedContent)

        val result = repository.addSubscription(url)
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(2, summary.imported)
        assertEquals(1, summary.skipped)

        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(2, servers.size)
    }

    @Test
    fun testRefreshSubscription() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"

        fetcher.result = Result.success("vless://$uuid@s1.com:443?security=none#S1\nvless://$uuid@s2.com:443?security=none#S2")
        repository.addSubscription(url).getOrThrow()
        val subId = subscriptionDao.observeAll().first()[0].id

        fetcher.result = Result.success("vless://$uuid@s3.com:443?security=none#S3")
        val refreshResult = repository.refreshSubscription(subId)
        assertTrue(refreshResult.isSuccess)

        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(1, servers.size)
        assertEquals("S3", servers[0].server.displayName)
        assertEquals(subId, servers[0].server.subscriptionId)
    }

    @Test
    fun testDeleteServer() = runTest {
        val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#Del"
        val server = repository.addManualServer(link).getOrThrow()

        assertNotNull(serverDao.getById(server.id))

        repository.deleteServer(server.id)
        assertNull(serverDao.getById(server.id))
    }

    @Test
    fun testTestLatency() = runTest {
        val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#Lat"
        val server = repository.addManualServer(link).getOrThrow()

        assertEquals(LatencyStatus.Untested, serverDao.getById(server.id)?.latencyStatus)

        latencyProbe.result = LatencyResult.Ok(45L)
        repository.testLatency(server.id)
        var updated = serverDao.getById(server.id)
        assertEquals(LatencyStatus.Ok, updated?.latencyStatus)
        assertEquals(45L, updated?.latencyMs)

        latencyProbe.result = LatencyResult.Timeout
        repository.testLatency(server.id)
        updated = serverDao.getById(server.id)
        assertEquals(LatencyStatus.Timeout, updated?.latencyStatus)
        assertNull(updated?.latencyMs)

        latencyProbe.result = LatencyResult.Error("Failed")
        repository.testLatency(server.id)
        updated = serverDao.getById(server.id)
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
                ServerWithSubscription(server, sub?.displayName)
            }.sortedWith(compareBy({ it.server.sortOrder }, { it.server.addedAtEpochMs }))
        }
    }
    override suspend fun getById(id: String): ServerEntity? = db.servers[id]
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
    override suspend fun delete(subscription: SubscriptionEntity) {
        db.subscriptions.remove(subscription.id)
        db.servers.values.removeIf { it.subscriptionId == subscription.id }
        db.serversFlow.value = db.servers.values.toList()
        db.subscriptionsFlow.value = db.subscriptions.values.toList()
    }
    override fun observeAll(): Flow<List<SubscriptionEntity>> {
        return db.subscriptionsFlow.map { list ->
            list.sortedByDescending { it.createdAtEpochMs }
        }
    }
    override suspend fun getById(id: String): SubscriptionEntity? = db.subscriptions[id]
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
    var result: Result<String> = Result.success("")

    override fun fetch(url: String): Result<String> = result
}

private class FakeLatencyProbe : LatencyProbe {
    var result: LatencyResult = LatencyResult.Ok(50L)
    override suspend fun measure(host: String, port: Int, timeoutMs: Int): LatencyResult {
        return result
    }
}
