package karst.vpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionPhase {
    Off,
    Connecting,
    On,
}

object ConnectionStateHolder {
    private val mutablePhase = MutableStateFlow(ConnectionPhase.Off)
    private val mutableLastError = MutableStateFlow<String?>(null)
    private val mutableConnectedSinceMillis = MutableStateFlow<Long?>(null)

    val phase: StateFlow<ConnectionPhase> = mutablePhase.asStateFlow()
    val lastError: StateFlow<String?> = mutableLastError.asStateFlow()
    val connectedSinceMillis: StateFlow<Long?> = mutableConnectedSinceMillis.asStateFlow()

    fun connecting() {
        mutablePhase.value = ConnectionPhase.Connecting
        mutableLastError.value = null
    }

    fun connected(sinceMillis: Long) {
        mutableConnectedSinceMillis.value = sinceMillis
        mutablePhase.value = ConnectionPhase.On
        mutableLastError.value = null
    }

    fun off(error: String? = null) {
        mutablePhase.value = ConnectionPhase.Off
        mutableConnectedSinceMillis.value = null
        mutableLastError.value = error
    }
}
