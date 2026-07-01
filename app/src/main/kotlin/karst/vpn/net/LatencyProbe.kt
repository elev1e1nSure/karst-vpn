package karst.vpn.net

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import karst.vpn.core.SocketProtectorRegistry
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
            val socket = Socket()
            SocketProtectorRegistry.current?.protect(socket)
            val start = System.nanoTime()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                LatencyResult.Ok((System.nanoTime() - start) / 1_000_000)
            } catch (_: SocketTimeoutException) {
                LatencyResult.Timeout
            } catch (e: IOException) {
                LatencyResult.Error(e.message)
            } finally {
                runCatching { socket.close() }
            }
        }
}
