package karst.vpn.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("karst_settings")

class SettingsRepository(
    private val context: Context,
) {
    val selectedServerId: Flow<String?> = context.settingsDataStore.data.map { it[SELECTED_SERVER_ID] }
    val darkMode: Flow<Boolean> = context.settingsDataStore.data.map { it[DARK_MODE] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val dnsDohUrl: Flow<String> = context.settingsDataStore.data.map { it[DNS_DOH_URL] ?: DEFAULT_DNS_DOH_URL }

    suspend fun setSelectedServerId(id: String?) {
        context.settingsDataStore.edit {
            if (id == null) {
                it.remove(SELECTED_SERVER_ID)
            } else {
                it[SELECTED_SERVER_ID] = id
            }
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    companion object {
        const val DEFAULT_DNS_DOH_URL = "https://1.1.1.1/dns-query"

        private val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val DNS_DOH_URL = stringPreferencesKey("dns_doh_url")
    }
}
