package karst.vpn.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.util.concurrent.Executor
import karst.vpn.core.ConnectionPhase
import karst.vpn.core.ConnectionStateHolder
import karst.vpn.data.KarstDatabase
import karst.vpn.data.LatencyStatus
import karst.vpn.data.ServerRepository
import karst.vpn.data.SettingsRepository
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VpnViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val testScope = TestScope(testDispatcher)

    private lateinit var dbState: FakeDatabaseState
    private lateinit var serverDao: FakeServerDao
    private lateinit var subscriptionDao: FakeSubscriptionDao
    private lateinit var db: FakeKarstDatabase
    private lateinit var fetcher: FakeSubscriptionFetcher
    private lateinit var latencyProbe: FakeLatencyProbe
    private lateinit var serverRepository: ServerRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: VpnViewModel

    @Before
    fun setUp() {
        dbState = FakeDatabaseState()
        serverDao = FakeServerDao(dbState)
        subscriptionDao = FakeSubscriptionDao(dbState)
        db = FakeKarstDatabase(serverDao, subscriptionDao)
        fetcher = FakeSubscriptionFetcher()
        latencyProbe = FakeLatencyProbe()

        serverRepository = ServerRepository(db, fetcher, latencyProbe)

        val dataStore = FakeDataStore()
        settingsRepository = SettingsRepository(dataStore)

        ConnectionStateHolder.off()

        viewModel = VpnViewModel(serverRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun testInitialState() = testScope.runTest {
        val state = viewModel.uiState.first()
        assertTrue(state.darkModeOn)
        assertTrue(state.notificationsEnabled)
        assertEquals(ConnectionPhase.Off, state.phase)
        assertNull(state.connectedSinceMillis)
        assertNull(state.lastError)
        assertTrue(state.servers.isEmpty())
        assertTrue(state.subscriptionGroups.isEmpty())
        assertNull(state.selectedServerId)
    }

    @Test
    fun testAddManualServerSuccess() = testScope.runTest {
        val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#Manual"
        viewModel.addServerInput(link)

        val state = viewModel.uiState.first { it.importMessage != null }
        assertEquals(1, state.servers.size)
        assertEquals("Manual", state.servers[0].name)
        assertEquals("Сервер добавлен", state.importMessage)
        assertEquals(state.servers[0].id, state.selectedServerId)
    }

    @Test
    fun testAddManualServerError() = testScope.runTest {
        viewModel.addServerInput("invalid-vless-link")

        val state = viewModel.uiState.first { it.addServerError != null }
        assertEquals("Не похоже на VLESS-ссылку или HTTPS URL подписки", state.addServerError)
    }

    @Test
    fun testAddSubscriptionSuccess() = testScope.runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"
        fetcher.result = Result.success("vless://$uuid@s1.com:443?security=none#Sub1")

        viewModel.addServerInput(url)

        val state = viewModel.uiState.first { it.importMessage != null }
        assertEquals(1, state.servers.size)
        assertEquals("Sub1", state.servers[0].name)
        assertEquals("Импортировано: 1", state.importMessage)
    }

    @Test
    fun testSelectAndDeleteServer() = testScope.runTest {
        val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#MyServer"
        viewModel.addServerInput(link)
        var state = viewModel.uiState.first { it.importMessage != null }
        val id = state.servers[0].id

        viewModel.deleteServer(id)
        state = viewModel.uiState.first { it.selectedServerId == null }
        assertTrue(state.servers.isEmpty())
    }

    @Test
    fun testToggleDarkModeAndNotifications() = testScope.runTest {
        viewModel.setDarkMode(false)
        var state = viewModel.uiState.first { !it.darkModeOn }
        assertEquals(false, state.darkModeOn)

        viewModel.setNotifications(false)
        state = viewModel.uiState.first { !it.notificationsEnabled }
        assertEquals(false, state.notificationsEnabled)
    }

    @Test
    fun testVpnConnectionStateChanges() = testScope.runTest {
        ConnectionStateHolder.connecting()
        var state = viewModel.uiState.first { it.phase == ConnectionPhase.Connecting }
        assertEquals(ConnectionPhase.Connecting, state.phase)

        ConnectionStateHolder.connected(1000L)
        state = viewModel.uiState.first { it.phase == ConnectionPhase.On }
        assertEquals(ConnectionPhase.On, state.phase)
        assertEquals(1000L, state.connectedSinceMillis)

        ConnectionStateHolder.off("Connection Timeout")
        state = viewModel.uiState.first { it.phase == ConnectionPhase.Off }
        assertEquals(ConnectionPhase.Off, state.phase)
        assertEquals("Connection Timeout", state.lastError)
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

private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
