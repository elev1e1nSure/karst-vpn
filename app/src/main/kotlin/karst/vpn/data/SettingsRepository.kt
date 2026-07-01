package karst.vpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("karst_settings")

enum class RoutingMode(val value: String) {
    Full("full"),
    BypassLocal("bypass_local"),
    BypassRu("bypass_ru");

    companion object {
        fun from(value: String?): RoutingMode =
            entries.firstOrNull { it.value == value } ?: BypassRu
    }
}

enum class SubscriptionAutoRefreshMode(val value: String) {
    Auto("auto"),
    Off("off"),
    EveryHours("every_hours");

    companion object {
        fun from(value: String?): SubscriptionAutoRefreshMode =
            entries.firstOrNull { it.value == value } ?: Auto
    }
}

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.settingsDataStore)

    val selectedServerId: Flow<String?> = dataStore.data.map { it[SELECTED_SERVER_ID] }
    val darkMode: Flow<Boolean> = dataStore.data.map { it[DARK_MODE] ?: true }
    val dnsDohUrl: Flow<String> = dataStore.data.map { it[DNS_DOH_URL] ?: DEFAULT_DNS_DOH_URL }
    val routingMode: Flow<RoutingMode> = dataStore.data.map { RoutingMode.from(it[ROUTING_MODE]) }
    val subscriptionAutoRefreshMode: Flow<SubscriptionAutoRefreshMode> =
        dataStore.data.map { SubscriptionAutoRefreshMode.from(it[SUBSCRIPTION_AUTO_REFRESH_MODE]) }
    val subscriptionAutoRefreshHours: Flow<Int> =
        dataStore.data.map { (it[SUBSCRIPTION_AUTO_REFRESH_HOURS] ?: DEFAULT_AUTO_REFRESH_HOURS).coerceAtLeast(1) }

    suspend fun setSelectedServerId(id: String?) {
        dataStore.edit {
            if (id == null) {
                it.remove(SELECTED_SERVER_ID)
            } else {
                it[SELECTED_SERVER_ID] = id
            }
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        dataStore.edit { it[ROUTING_MODE] = mode.value }
    }

    suspend fun setSubscriptionAutoRefreshMode(mode: SubscriptionAutoRefreshMode) {
        dataStore.edit { it[SUBSCRIPTION_AUTO_REFRESH_MODE] = mode.value }
    }

    suspend fun setSubscriptionAutoRefreshHours(hours: Int) {
        dataStore.edit { it[SUBSCRIPTION_AUTO_REFRESH_HOURS] = hours.coerceAtLeast(1) }
    }

    companion object {
        const val DEFAULT_DNS_DOH_URL = "https://1.1.1.1/dns-query"
        const val DEFAULT_AUTO_REFRESH_HOURS = 24

        private val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val DNS_DOH_URL = stringPreferencesKey("dns_doh_url")
        private val ROUTING_MODE = stringPreferencesKey("routing_mode")
        private val SUBSCRIPTION_AUTO_REFRESH_MODE = stringPreferencesKey("subscription_auto_refresh_mode")
        private val SUBSCRIPTION_AUTO_REFRESH_HOURS = intPreferencesKey("subscription_auto_refresh_hours")
    }
}
