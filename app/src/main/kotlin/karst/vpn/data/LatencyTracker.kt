package karst.vpn.data

import karst.vpn.net.LatencyProbe
import karst.vpn.net.LatencyResult

class LatencyTracker(
    database: KarstDatabase,
    private val latencyProbe: LatencyProbe,
) {
    private val servers = database.servers()

    suspend fun testLatency(serverId: String) {
        val server = servers.getById(serverId) ?: return
        servers.updateLatency(serverId, LatencyStatus.Testing, null, null)
        when (val result = latencyProbe.measure(server.host, server.port)) {
            is LatencyResult.Ok -> servers.updateLatency(serverId, LatencyStatus.Ok, result.ms, System.currentTimeMillis())
            LatencyResult.Timeout -> servers.updateLatency(serverId, LatencyStatus.Timeout, null, System.currentTimeMillis())
            is LatencyResult.Error -> servers.updateLatency(serverId, LatencyStatus.Error, null, System.currentTimeMillis())
        }
    }
}
