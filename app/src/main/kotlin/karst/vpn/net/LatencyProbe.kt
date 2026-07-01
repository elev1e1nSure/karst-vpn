package karst.vpn.net

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import karst.vpn.core.SocketProtectorRegistry
import karst.vpn.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class LatencyResult {
    data class Ok(val ms: Long) : LatencyResult()
    data object Timeout : LatencyResult()
    data class Error(val message: String?) : LatencyResult()
}

interface LatencyProbe {
    suspend fun measure(host: String, port: Int, timeoutMs: Int = 1500): LatencyResult
}

class SocketLatencyProbe : LatencyProbe {
    override suspend fun measure(host: String, port: Int, timeoutMs: Int): LatencyResult =
        withContext(Dispatchers.IO) {
            AppLog.debug(AppLog.Category.NET, "Socket connection check started for $host:$port (timeout=${timeoutMs}ms)")
            val socket = Socket()
            SocketProtectorRegistry.current?.protect(socket)
            val start = System.nanoTime()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val durationMs = (System.nanoTime() - start) / 1_000_000
                AppLog.debug(AppLog.Category.NET, "Socket connection successful for $host:$port. Duration: $durationMs ms")
                LatencyResult.Ok(durationMs)
            } catch (e: SocketTimeoutException) {
                AppLog.warn(AppLog.Category.NET, "Socket connection timeout for $host:$port")
                LatencyResult.Timeout
            } catch (e: IOException) {
                AppLog.error(AppLog.Category.NET, "Socket connection error for $host:$port: ${e.message}")
                LatencyResult.Error(e.message)
            } finally {
                runCatching { socket.close() }
            }
        }
}
