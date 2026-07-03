package karst.vpn.data

import karst.vpn.net.SubscriptionFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubscriptionSyncTest {

    private val dbState = FakeDatabaseState()
    private val serverDao = FakeServerDao(dbState)
    private val subscriptionDao = FakeSubscriptionDao(dbState)
    private val db = FakeKarstDatabase(serverDao, subscriptionDao)
    private val fetcher = FakeSubscriptionFetcher()
    private val subscriptionSync = SubscriptionSync(db, fetcher)

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
    fun testImportInputRejectsPlainHttp() = runTest {
        val result = subscriptionSync.importInput("http://example.com/sub")
        assertTrue(result.isFailure)
        assertEquals(
            "Подписка должна использовать HTTPS",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun testImportInputRejectsUnknownFormat() = runTest {
        val result = subscriptionSync.importInput("not-a-link-or-url")
        assertTrue(result.isFailure)
        assertEquals(
            "Не похоже на VLESS-ссылку или HTTPS URL подписки",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun testImportInputRejectsInvalidVlessLink() = runTest {
        val result = subscriptionSync.importInput("vless://not-a-valid-link")
        assertTrue(result.isFailure)
    }

    @Test
    fun testImportInputTrimsWhitespace() = runTest {
        val link = "  vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#Trimmed  \n"
        val result = subscriptionSync.importInput(link)
        assertTrue(result.isSuccess)
        assertEquals("Сервер добавлен", result.getOrThrow().message)
    }

    @Test
    fun testImportInputReimportsExistingSubscriptionAsRefresh() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"
        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s1.com:443?security=none#S1"))

        subscriptionSync.importInput(url).getOrThrow()
        assertEquals(1, subscriptionDao.observeAll().first().size)

        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s2.com:443?security=none#S2"))
        val second = subscriptionSync.importInput(url)

        assertTrue(second.isSuccess)
        assertEquals("Подписка уже была добавлена, обновлено", second.getOrThrow().message)
        assertEquals(1, subscriptionDao.observeAll().first().size)
        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(1, servers.size)
        assertEquals("S2", servers[0].server.displayName)
    }

    @Test
    fun testImportInputPropagatesFetchFailure() = runTest {
        val url = "https://example.com/sub"
        fetcher.result = Result.failure(RuntimeException("network down"))

        val result = subscriptionSync.importInput(url)
        assertTrue(result.isFailure)
        assertEquals("network down", result.exceptionOrNull()?.message)
    }

    @Test
    fun testRefreshUnknownIdFails() = runTest {
        val result = subscriptionSync.refresh("missing-id")
        assertTrue(result.isFailure)
        assertEquals("Подписка не найдена", result.exceptionOrNull()?.message)
    }

    @Test
    fun testRefreshAllWithNoSubscriptions() = runTest {
        val result = subscriptionSync.refreshAll()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().imported)
    }

    @Test
    fun testRefreshAllAggregatesAcrossSubscriptions() = runTest {
        val uuid = "11111111-1111-4111-8111-111111111111"
        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@a1.com:443?security=none#A1"))
        subscriptionSync.importInput("https://one.example.com/sub").getOrThrow()
        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@b1.com:443?security=none#B1\nvless://$uuid@b2.com:443?security=none#B2"))
        subscriptionSync.importInput("https://two.example.com/sub").getOrThrow()

        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@c1.com:443?security=none#C1"))
        val result = subscriptionSync.refreshAll()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().imported)
    }

    @Test
    fun testRefreshAllContinuesWhenOneSubscriptionFails() = runTest {
        val uuid = "11111111-1111-4111-8111-111111111111"
        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@a1.com:443?security=none#A1"))
        subscriptionSync.importInput("https://one.example.com/sub").getOrThrow()
        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@b1.com:443?security=none#B1"))
        subscriptionSync.importInput("https://two.example.com/sub").getOrThrow()

        fetcher.result = Result.failure(RuntimeException("boom"))
        val result = subscriptionSync.refreshAll()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().imported)
        subscriptionDao.observeAll().first().forEach {
            assertEquals("boom", it.lastRefreshError)
        }
    }

    @Test
    fun testConcurrentRefreshOfSameSubscriptionIsSerialized() = runTest {
        val uuid = "11111111-1111-4111-8111-111111111111"
        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s1.com:443?security=none#S1"))
        subscriptionSync.importInput("https://example.com/sub").getOrThrow()
        val subId = subscriptionDao.observeAll().first()[0].id

        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s2.com:443?security=none#S2"))
        val results = listOf(
            async { subscriptionSync.refresh(subId) },
            async { subscriptionSync.refresh(subId) },
        ).awaitAll()

        results.forEach { assertTrue(it.isSuccess) }
        val servers = serverDao.observeAllWithSubscriptions().first()
        assertEquals(1, servers.size)
        assertEquals("S2", servers[0].server.displayName)
    }
}
