package karst.vpn.data

import karst.vpn.data.dao.ServerWithSubscription
import karst.vpn.data.entities.ServerEntity
import kotlinx.coroutines.flow.Flow

data class ImportSummary(
    val imported: Int,
    val skipped: Int,
    val firstServerId: String?,
    val message: String,
)

object LatencyStatus {
    const val Untested = "UNTESTED"
    const val Testing = "TESTING"
    const val Ok = "OK"
    const val Timeout = "TIMEOUT"
    const val Error = "ERROR"
}

class ServerRepository(
    database: KarstDatabase,
) {
    private val servers = database.servers()
    private val subscriptions = database.subscriptions()

    fun observeServers(): Flow<List<ServerWithSubscription>> =
        servers.observeAllWithSubscriptions()

    suspend fun getServer(id: String): ServerEntity? =
        servers.getById(id)

    suspend fun deleteServer(id: String) {
        servers.deleteById(id)
    }

    suspend fun deleteSubscription(id: String) {
        subscriptions.deleteById(id)
    }
}
