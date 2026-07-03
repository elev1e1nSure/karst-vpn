package karst.vpn.data

import karst.vpn.data.entities.ServerEntity
import karst.vpn.net.LatencyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LatencyTrackerTest {

    private val dbState = FakeDatabaseState()
    private val serverDao = FakeServerDao(dbState)
    private val subscriptionDao = FakeSubscriptionDao(dbState)
    private val db = FakeKarstDatabase(serverDao, subscriptionDao)
    private val latencyProbe = FakeLatencyProbe()
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
    fun testTestLatencyOnMissingServerDoesNothing() = runTest {
        latencyTracker.testLatency("missing-id")
        assertEquals(0, dbState.servers.size)
        assertEquals(0, latencyProbe.measureCallCount)
    }

    @Test
    fun testTestLatencySetsTestingStatusBeforeMeasuring() = runTest {
        val server = ServerEntity(
            id = "s1",
            subscriptionId = null,
            displayName = "S1",
            rawLink = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#S1",
            outboundConfigJson = "{}",
            host = "example.com",
            port = 443,
            sortOrder = 0,
            latencyMs = null,
            latencyStatus = LatencyStatus.Untested,
            latencyMeasuredAtEpochMs = null,
            addedAtEpochMs = 0L,
        )
        dbState.servers[server.id] = server

        latencyProbe.result = LatencyResult.Ok(10L)
        latencyTracker.testLatency("s1")

        assertEquals(1, latencyProbe.measureCallCount)
        assertEquals(LatencyStatus.Ok, dbState.servers["s1"]?.latencyStatus)
        assertEquals(10L, dbState.servers["s1"]?.latencyMs)
    }
}
