package karst.vpn.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import karst.vpn.data.KarstDatabase
import karst.vpn.data.ServerRepository
import karst.vpn.data.SettingsRepository
import karst.vpn.net.LatencyProbe
import karst.vpn.net.LatencyResult
import karst.vpn.net.SubscriptionFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: KarstDatabase
    private lateinit var fetcher: FakeSubscriptionFetcher
    private lateinit var latencyProbe: FakeLatencyProbe
    private lateinit var serverRepository: ServerRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: VpnViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KarstDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fetcher = FakeSubscriptionFetcher()
        latencyProbe = FakeLatencyProbe()

        serverRepository = ServerRepository(db, fetcher, latencyProbe)

        val dataStore = FakeDataStore()
        settingsRepository = SettingsRepository(dataStore)

        viewModel = VpnViewModel(serverRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testEmptyStateAndAddServerFlow() {
        composeTestRule.setContent {
            VpnScreen(
                viewModel = viewModel,
                onConnectRequest = {},
                onDisconnectRequest = {}
            )
        }

        composeTestRule.onNodeWithTag("location_chip").assertIsDisplayed()

        composeTestRule.onNodeWithTag("location_chip").performClick()

        composeTestRule.onNodeWithTag("open_add_server_btn").performClick()

        val uuid = "11111111-1111-4111-8111-111111111111"
        val vlessLink = "vless://$uuid@1.2.3.4:443?security=none#MyCustomServer"
        composeTestRule.onNodeWithTag("add_server_input").performTextInput(vlessLink)
        composeTestRule.onNodeWithTag("add_server_submit").performClick()

        composeTestRule.onNodeWithTag("open_add_server_btn").assertIsDisplayed()
    }

    @Test
    fun testSettingsBottomSheetToggles() {
        composeTestRule.setContent {
            VpnScreen(
                viewModel = viewModel,
                onConnectRequest = {},
                onDisconnectRequest = {}
            )
        }

        composeTestRule.onNodeWithTag("settings_btn").performClick()

        composeTestRule.onNodeWithTag("logs_action_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dark_mode_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notifications_toggle").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dark_mode_toggle").performClick()
    }
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
