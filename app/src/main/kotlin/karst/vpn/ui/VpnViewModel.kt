package karst.vpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import karst.vpn.KarstApplication
import karst.vpn.core.ConnectionPhase
import karst.vpn.core.ConnectionStateHolder
import karst.vpn.data.ImportCoordinator
import karst.vpn.data.LatencyStatus
import karst.vpn.data.LatencyTracker
import karst.vpn.data.ServerRepository
import karst.vpn.data.SettingsRepository
import karst.vpn.data.SubscriptionRefresher
import karst.vpn.data.dao.ServerWithSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VpnUiState(
    val darkModeOn: Boolean = true,
    val phase: ConnectionPhase = ConnectionPhase.Off,
    val connectedSinceMillis: Long? = null,
    val lastError: String? = null,
    val servers: List<UiServer> = emptyList(),
    val subscriptionGroups: List<UiSubscription> = emptyList(),
    val selectedServerId: String? = null,
    val addServerLoading: Boolean = false,
    val addServerError: String? = null,
    val importMessage: String? = null,
    val refreshAllLoading: Boolean = false,
)

private data class SettingsState(
    val selectedServerId: String?,
    val darkModeOn: Boolean,
)

private data class ConnectionState(
    val phase: ConnectionPhase,
    val lastError: String?,
    val connectedSinceMillis: Long?,
)

private data class AddServerState(
    val loading: Boolean = false,
    val error: String? = null,
)

private data class RefreshAllState(
    val loading: Boolean = false,
)

class VpnViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val importCoordinator: ImportCoordinator,
    private val subscriptionRefresher: SubscriptionRefresher,
    private val latencyTracker: LatencyTracker,
) : ViewModel() {
    private val addServerState = MutableStateFlow(AddServerState())
    private val importMessage = MutableStateFlow<String?>(null)
    private val refreshAllState = MutableStateFlow(RefreshAllState())

    init {
        viewModelScope.launch {
            serverRepository.observeServers().collect { servers ->
                servers
                    .filter { it.server.latencyStatus == "UNTESTED" }
                    .forEach {
                        withContext(Dispatchers.IO) {
                            latencyTracker.testLatency(it.server.id)
                        }
                    }
            }
        }
    }

    private val settingsState = combine(
        settingsRepository.selectedServerId,
        settingsRepository.darkMode,
    ) { selectedServerId, darkModeOn ->
        SettingsState(selectedServerId, darkModeOn)
    }

    private val connectionState = combine(
        ConnectionStateHolder.phase,
        ConnectionStateHolder.lastError,
        ConnectionStateHolder.connectedSinceMillis,
    ) { phase, lastError, connectedSinceMillis ->
        ConnectionState(phase, lastError, connectedSinceMillis)
    }

    private val addFormState = combine(
        addServerState,
        importMessage,
        refreshAllState,
    ) { addState, message, refreshAll ->
        Triple(addState, message, refreshAll)
    }

    val uiState: StateFlow<VpnUiState> = combine(
        serverRepository.observeServers(),
        settingsState,
        connectionState,
        addFormState,
    ) { serverRows, settings, connection, (addState, message, refreshAll) ->
        val servers = serverRows.map { it.toUiServer() }
        val groups = serverRows.groupBy { it.server.subscriptionId }.map { (subId, subRows) ->
            val first = subRows.firstOrNull()
            val name = if (subId == null) "Вручную" else subRows.firstOrNull()?.subscriptionName ?: "Подписка"
            UiSubscription(
                id = subId,
                name = name,
                announce = first?.subscriptionAnnounce,
                url = first?.subscriptionUrl,
                profileUpdateIntervalHours = first?.subscriptionProfileUpdateIntervalHours,
                profileWebPageUrl = first?.subscriptionProfileWebPageUrl,
                routingEnabled = first?.subscriptionRoutingEnabled,
                uploadBytes = first?.subscriptionUploadBytes,
                downloadBytes = first?.subscriptionDownloadBytes,
                totalBytes = first?.subscriptionTotalBytes,
                expireAtEpochSeconds = first?.subscriptionExpireAtEpochSeconds,
                lastRefreshedAtEpochMs = first?.subscriptionLastRefreshedAtEpochMs,
                lastRefreshError = first?.subscriptionLastRefreshError,
                servers = subRows.map { it.toUiServer() },
            )
        }
        VpnUiState(
            darkModeOn = settings.darkModeOn,
            phase = connection.phase,
            connectedSinceMillis = connection.connectedSinceMillis,
            lastError = connection.lastError,
            servers = servers,
            subscriptionGroups = groups,
            selectedServerId = settings.selectedServerId ?: servers.firstOrNull()?.id,
            addServerLoading = addState.loading,
            addServerError = addState.error,
            importMessage = message,
            refreshAllLoading = refreshAll.loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VpnUiState())

    fun selectServer(id: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedServerId(id)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDarkMode(enabled)
        }
    }

    fun clearAddError() {
        addServerState.value = addServerState.value.copy(error = null)
    }

    fun clearImportMessage() {
        importMessage.value = null
    }

    fun addServerInput(rawInput: String) {
        val text = rawInput.trim()
        if (text.isBlank()) {
            addServerState.value = AddServerState(error = "Вставь VLESS-ссылку или URL подписки")
            return
        }

        viewModelScope.launch {
            addServerState.value = AddServerState(loading = true)
            importMessage.value = null

            val result = withContext(Dispatchers.IO) {
                importCoordinator.importInput(text)
            }

            result
                .onSuccess { imported ->
                    imported.firstServerId?.let { settingsRepository.setSelectedServerId(it) }
                    addServerState.value = AddServerState()
                    importMessage.value = imported.message.hideImportCount()
                }
                .onFailure {
                    addServerState.value = AddServerState(error = it.message ?: "Не удалось добавить")
                }
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                serverRepository.deleteServer(id)
            }
            if (uiState.value.selectedServerId == id) {
                settingsRepository.setSelectedServerId(null)
            }
        }
    }

    fun deleteSubscription(id: String) {
        val selectedWasInSubscription = uiState.value.subscriptionGroups
            .firstOrNull { it.id == id }
            ?.servers
            ?.any { it.id == uiState.value.selectedServerId } == true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                serverRepository.deleteSubscription(id)
            }
            if (selectedWasInSubscription) {
                settingsRepository.setSelectedServerId(null)
            }
        }
    }

    fun refreshSubscription(id: String) {
        viewModelScope.launch {
            importMessage.value = null
            val result = withContext(Dispatchers.IO) {
                subscriptionRefresher.refresh(id)
            }
            result
                .onSuccess {
                    importMessage.value = it.message.hideImportCount()
                }
                .onFailure {
                    importMessage.value = it.message ?: "Не удалось обновить подписку"
                }
        }
    }

    fun refreshAllSubscriptions() {
        if (refreshAllState.value.loading) return
        viewModelScope.launch {
            refreshAllState.value = RefreshAllState(loading = true)
            importMessage.value = null
            val result = withContext(Dispatchers.IO) {
                subscriptionRefresher.refreshAll()
            }
            result
                .onSuccess {
                    importMessage.value = it.message
                    refreshAllState.value = refreshAllState.value.copy(loading = false)
                }
                .onFailure {
                    importMessage.value = it.message ?: "Не удалось обновить подписки"
                    refreshAllState.value = refreshAllState.value.copy(loading = false)
                }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val application: KarstApplication,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VpnViewModel(
                application.container.serverRepository,
                application.container.settingsRepository,
                application.container.importCoordinator,
                application.container.subscriptionRefresher,
                application.container.latencyTracker,
            ) as T
    }
}

private fun String.hideImportCount(): String =
    if (startsWith("Импортировано:")) "" else this

private fun ServerWithSubscription.toUiServer(): UiServer {
    val latencyLabel = when (server.latencyStatus) {
        LatencyStatus.Testing -> "Проверяем..."
        LatencyStatus.Ok -> "${server.latencyMs ?: 0} мс"
        LatencyStatus.Timeout -> "Таймаут"
        LatencyStatus.Error -> "Ошибка"
        else -> "Не проверено"
    }
    val source = subscriptionName ?: "ручной"
    return UiServer(
        id = server.id,
        name = server.displayName,
        tag = "VLESS · ${server.host}:${server.port} · $source",
        latencyLabel = latencyLabel,
        isCustom = server.subscriptionId == null,
        subscriptionId = server.subscriptionId,
    )
}
