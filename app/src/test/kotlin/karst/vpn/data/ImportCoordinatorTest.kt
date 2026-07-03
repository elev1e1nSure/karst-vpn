package karst.vpn.data

import karst.vpn.net.SubscriptionFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

class ImportCoordinatorTest {

    private val dbState = FakeDatabaseState()
    private val serverDao = FakeServerDao(dbState)
    private val subscriptionDao = FakeSubscriptionDao(dbState)
    private val db = FakeKarstDatabase(serverDao, subscriptionDao)
    private val fetcher = FakeSubscriptionFetcher()
    private val subscriptionRefresher = SubscriptionRefresher(db, fetcher)
    private val importCoordinator = ImportCoordinator(db, fetcher, subscriptionRefresher)

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
        val result = importCoordinator.importInput("http://example.com/sub")
        assertTrue(result.isFailure)
        assertEquals(
            "Подписка должна использовать HTTPS",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun testImportInputRejectsUnknownFormat() = runTest {
        val result = importCoordinator.importInput("not-a-link-or-url")
        assertTrue(result.isFailure)
        assertEquals(
            "Не похоже на VLESS-ссылку или HTTPS URL подписки",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun testImportInputRejectsInvalidVlessLink() = runTest {
        val result = importCoordinator.importInput("vless://not-a-valid-link")
        assertTrue(result.isFailure)
    }

    @Test
    fun testImportInputTrimsWhitespace() = runTest {
        val link = "  vless://11111111-1111-4111-8111-111111111111@example.com:443?security=none#Trimmed  \n"
        val result = importCoordinator.importInput(link)
        assertTrue(result.isSuccess)
        assertEquals("Сервер добавлен", result.getOrThrow().message)
    }

    @Test
    fun testImportInputReimportsExistingSubscriptionAsRefresh() = runTest {
        val url = "https://example.com/sub"
        val uuid = "11111111-1111-4111-8111-111111111111"
        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s1.com:443?security=none#S1"))

        importCoordinator.importInput(url).getOrThrow()
        assertEquals(1, subscriptionDao.observeAll().first().size)

        fetcher.result = Result.success(SubscriptionFetchResult("vless://$uuid@s2.com:443?security=none#S2"))
        val second = importCoordinator.importInput(url)

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

        val result = importCoordinator.importInput(url)
        assertTrue(result.isFailure)
        assertEquals("network down", result.exceptionOrNull()?.message)
    }
}
