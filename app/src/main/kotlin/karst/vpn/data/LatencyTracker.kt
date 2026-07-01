package karst.vpn.data

import karst.vpn.net.LatencyProbe
import karst.vpn.net.LatencyResult
import karst.vpn.log.AppLog

class LatencyTracker(
    database: KarstDatabase,
    private val latencyProbe: LatencyProbe,
) {
    private val servers = database.servers()

    suspend fun testLatency(serverId: String) {
        val server = servers.getById(serverId) ?: run {
            AppLog.warn(AppLog.Category.NET, "testLatency: Server not found (id=$serverId)")
            return
        }
        AppLog.info(AppLog.Category.NET, "Starting latency check for ${server.displayName} (${server.host}:${server.port})")
        servers.updateLatency(serverId, LatencyStatus.Testing, null, null)
        val result = latencyProbe.measure(server.host, server.port)
        when (result) {
            is LatencyResult.Ok -> {
                AppLog.info(AppLog.Category.NET, "Latency for ${server.displayName} is ${result.ms} ms")
                servers.updateLatency(serverId, LatencyStatus.Ok, result.ms, System.currentTimeMillis())
            }
            LatencyResult.Timeout -> {
                AppLog.warn(AppLog.Category.NET, "Latency check timeout for ${server.displayName}")
                servers.updateLatency(serverId, LatencyStatus.Timeout, null, System.currentTimeMillis())
            }
            is LatencyResult.Error -> {
                AppLog.error(AppLog.Category.NET, "Latency check error for ${server.displayName}: ${result.message}")
                servers.updateLatency(serverId, LatencyStatus.Error, null, System.currentTimeMillis())
            }
        }
    }
}
