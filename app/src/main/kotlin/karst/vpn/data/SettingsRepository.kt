package karst.vpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("karst_settings")

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.settingsDataStore)

    val selectedServerId: Flow<String?> = dataStore.data.map { it[SELECTED_SERVER_ID] }
    val darkMode: Flow<Boolean> = dataStore.data.map { it[DARK_MODE] ?: true }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val dnsDohUrl: Flow<String> = dataStore.data.map { it[DNS_DOH_URL] ?: DEFAULT_DNS_DOH_URL }

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

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    companion object {
        const val DEFAULT_DNS_DOH_URL = "https://1.1.1.1/dns-query"

        private val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val DNS_DOH_URL = stringPreferencesKey("dns_doh_url")
    }
}
