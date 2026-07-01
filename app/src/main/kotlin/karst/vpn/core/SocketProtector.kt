package karst.vpn.core

import java.net.Socket

fun interface SocketProtector {
    fun protect(socket: Socket): Boolean
}

object SocketProtectorRegistry {
    @Volatile
    var current: SocketProtector? = null
}
