package karst.vpn.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun testSettingsRepositoryFlows() = runTest {
        val dataStore = FakeDataStore()
        val repository = SettingsRepository(dataStore)

        // Check defaults
        assertNull(repository.selectedServerId.first())
        assertTrue(repository.darkMode.first())
        assertTrue(repository.notificationsEnabled.first())
        assertEquals(SettingsRepository.DEFAULT_DNS_DOH_URL, repository.dnsDohUrl.first())

        // Change selected server
        repository.setSelectedServerId("server-uuid-1")
        assertEquals("server-uuid-1", repository.selectedServerId.first())

        // Clear selected server
        repository.setSelectedServerId(null)
        assertNull(repository.selectedServerId.first())

        // Toggle dark mode
        repository.setDarkMode(false)
        assertEquals(false, repository.darkMode.first())

        // Toggle notifications
        repository.setNotificationsEnabled(false)
        assertEquals(false, repository.notificationsEnabled.first())
    }
}
