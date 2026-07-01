package karst.vpn.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import karst.vpn.data.KarstDatabase
import karst.vpn.data.entities.ServerEntity
import karst.vpn.data.entities.SubscriptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerDaoTest {

    private lateinit var database: KarstDatabase
    private lateinit var serverDao: ServerDao
    private lateinit var subscriptionDao: SubscriptionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KarstDatabase::class.java
        ).allowMainThreadQueries().build()

        serverDao = database.servers()
        subscriptionDao = database.subscriptions()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndGetServer() = runTest {
        val server = ServerEntity(
            id = "s1",
            subscriptionId = null,
            displayName = "Manual Server",
            rawLink = "vless://...",
            outboundConfigJson = "{}",
            host = "1.2.3.4",
            port = 443,
            sortOrder = 0,
            latencyMs = null,
            latencyStatus = "UNTESTED",
            latencyMeasuredAtEpochMs = null,
            addedAtEpochMs = System.currentTimeMillis()
        )

        serverDao.upsert(server)

        val retrieved = serverDao.getById("s1")
        assertNotNull(retrieved)
        assertEquals("Manual Server", retrieved?.displayName)
        assertEquals("1.2.3.4", retrieved?.host)
    }

    @Test
    fun testObserveServersWithSubscriptionJoin() = runTest {
        val sub = SubscriptionEntity(
            id = "sub1",
            url = "http://url",
            displayName = "My Subscription",
            lastRefreshedAtEpochMs = null,
            lastRefreshError = null,
            createdAtEpochMs = System.currentTimeMillis()
        )
        subscriptionDao.upsert(sub)

        val server = ServerEntity(
            id = "s2",
            subscriptionId = "sub1",
            displayName = "Sub Server",
            rawLink = "vless://...",
            outboundConfigJson = "{}",
            host = "2.3.4.5",
            port = 80,
            sortOrder = 0,
            latencyMs = null,
            latencyStatus = "UNTESTED",
            latencyMeasuredAtEpochMs = null,
            addedAtEpochMs = System.currentTimeMillis()
        )
        serverDao.upsert(server)

        val list = serverDao.observeAllWithSubscriptions().first()
        assertEquals(1, list.size)
        assertEquals("Sub Server", list[0].server.displayName)
        assertEquals("My Subscription", list[0].subscriptionName)
    }

    @Test
    fun testDeleteBehavior() = runTest {
        val server = ServerEntity(
            id = "s3",
            subscriptionId = null,
            displayName = "Temp",
            rawLink = "vless://...",
            outboundConfigJson = "{}",
            host = "host",
            port = 443,
            sortOrder = 1,
            latencyMs = null,
            latencyStatus = "UNTESTED",
            latencyMeasuredAtEpochMs = null,
            addedAtEpochMs = System.currentTimeMillis()
        )
        serverDao.upsert(server)
        assertNotNull(serverDao.getById("s3"))

        serverDao.deleteById("s3")
        assertNull(serverDao.getById("s3"))
    }

    @Test
    fun testCascadeDeleteSubscriptionDeletesServers() = runTest {
        val sub = SubscriptionEntity(
            id = "sub_to_delete",
            url = "http://url",
            displayName = "To Delete",
            lastRefreshedAtEpochMs = null,
            lastRefreshError = null,
            createdAtEpochMs = System.currentTimeMillis()
        )
        subscriptionDao.upsert(sub)

        val server1 = ServerEntity(
            id = "s_sub1",
            subscriptionId = "sub_to_delete",
            displayName = "Server 1",
            rawLink = "vless://...",
            outboundConfigJson = "{}",
            host = "host1",
            port = 443,
            sortOrder = 1,
            latencyMs = null,
            latencyStatus = "UNTESTED",
            latencyMeasuredAtEpochMs = null,
            addedAtEpochMs = System.currentTimeMillis()
        )
        val server2 = ServerEntity(
            id = "s_sub2",
            subscriptionId = "sub_to_delete",
            displayName = "Server 2",
            rawLink = "vless://...",
            outboundConfigJson = "{}",
            host = "host2",
            port = 443,
            sortOrder = 2,
            latencyMs = null,
            latencyStatus = "UNTESTED",
            latencyMeasuredAtEpochMs = null,
            addedAtEpochMs = System.currentTimeMillis()
        )
        serverDao.upsertAll(listOf(server1, server2))

        // Verify they are inserted
        assertNotNull(serverDao.getById("s_sub1"))
        assertNotNull(serverDao.getById("s_sub2"))

        // Delete subscription
        subscriptionDao.delete(sub)

        // Verify servers are cascade deleted
        assertNull(serverDao.getById("s_sub1"))
        assertNull(serverDao.getById("s_sub2"))
    }
}
