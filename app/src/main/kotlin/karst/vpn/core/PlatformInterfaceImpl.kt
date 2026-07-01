package karst.vpn.core

import android.net.IpPrefix
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.system.OsConstants
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.InterfaceAddress
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

class PlatformInterfaceImpl(
    private val service: KarstVpnService,
) : PlatformInterface {
    private val connectivityManager = service.getSystemService(ConnectivityManager::class.java)
    private val defaultNetworkCallbacks = ConcurrentHashMap<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        service.protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (VpnService.prepare(service) != null) {
            error("android: missing vpn permission")
        }

        val builder = service.Builder()
            .setSession("Karst VPN")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        selectUnderlyingNetwork(connectivityManager.activeNetwork)?.let { network ->
            builder.setUnderlyingNetworks(arrayOf(network))
        }

        val inet4Addresses = options.inet4Address.toList()
        val inet6Addresses = options.inet6Address.toList()
        inet4Addresses.forEach { builder.addAddress(it.address(), it.prefix()) }
        inet6Addresses.forEach { builder.addAddress(it.address(), it.prefix()) }

        if (options.autoRoute) {
            runCatching { options.dnsServerAddress.value }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.addDnsServer(it) }

            val inet4Routes = options.inet4RouteAddress.toList()
            val inet6Routes = options.inet6RouteAddress.toList()
            val inet4Ranges = options.inet4RouteRange.toList()
            val inet6Ranges = options.inet6RouteRange.toList()

            when {
                inet4Routes.isNotEmpty() -> inet4Routes.forEach { builder.addRouteCompat(it) }
                inet4Ranges.isNotEmpty() -> inet4Ranges.forEach { builder.addRoute(it.address(), it.prefix()) }
                inet4Addresses.isNotEmpty() -> builder.addRoute("0.0.0.0", 0)
            }
            when {
                inet6Routes.isNotEmpty() -> inet6Routes.forEach { builder.addRouteCompat(it) }
                inet6Ranges.isNotEmpty() -> inet6Ranges.forEach { builder.addRoute(it.address(), it.prefix()) }
                inet6Addresses.isNotEmpty() -> builder.addRoute("::", 0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                options.inet4RouteExcludeAddress.toList().forEach { builder.excludeRoute(it.toIpPrefix()) }
                options.inet6RouteExcludeAddress.toList().forEach { builder.excludeRoute(it.toIpPrefix()) }
            }

            options.includePackage.drain().forEach { runCatching { builder.addAllowedApplication(it) } }
            options.excludePackage.drain().forEach { runCatching { builder.addDisallowedApplication(it) } }
        }

        val descriptor = builder.establish() ?: error("android: failed to establish vpn")
        val fd = descriptor.fd
        service.activeTunDescriptor = descriptor
        android.util.Log.d("KarstVPN", "openTun: established, fd=$fd")
        return fd
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        throw UnsupportedOperationException("connection owner lookup is not implemented")
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateDefaultInterface(listener, network)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateDefaultInterface(listener, network)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                updateDefaultInterface(listener, network)
            }

            override fun onLost(network: Network) {
                updateDefaultInterface(listener, connectivityManager.activeNetwork)
            }

            override fun onUnavailable() {
                listener.updateDefaultInterface("", -1, false, false)
            }
        }
        defaultNetworkCallbacks.put(listener, callback)?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        updateDefaultInterface(listener, connectivityManager.activeNetwork)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultNetworkCallbacks.remove(listener)?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val metadataByName = androidInterfaceMetadataByName()
        val interfaces = NetworkInterface.getNetworkInterfaces().toList().mapNotNull { networkInterface ->
            val addresses = networkInterface.interfaceAddresses.mapNotNull { it.toPrefixString() }
            if (addresses.isEmpty()) return@mapNotNull null
            val metadata = metadataByName[networkInterface.name]

            LibboxNetworkInterface().apply {
                name = networkInterface.name
                index = networkInterface.index
                mtu = runCatching { networkInterface.mtu }.getOrDefault(1500)
                this.addresses = StringListIterator(addresses)
                flags = networkInterface.flags()
                type = metadata?.type ?: Libbox.InterfaceTypeOther
                dnsServer = StringListIterator(metadata?.dnsServers.orEmpty())
                metered = metadata?.metered ?: false
            }
        }
        return NetworkInterfaceListIterator(interfaces)
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() = Unit

    override fun readWIFIState(): WIFIState? = null

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun sendNotification(notification: Notification) {
        // Android foreground notification is owned by KarstVpnService.
    }

    override fun systemCertificates(): StringIterator = StringListIterator(emptyList())

    private fun updateDefaultInterface(listener: InterfaceUpdateListener, preferredNetwork: Network?) {
        val network = selectUnderlyingNetwork(preferredNetwork)
        val interfaceName = network?.let { connectivityManager.getLinkProperties(it)?.interfaceName }
        val interfaceIndex = interfaceName?.let {
            runCatching { NetworkInterface.getByName(it)?.index }.getOrNull()
        }
        if (interfaceName.isNullOrBlank() || interfaceIndex == null || interfaceIndex < 0) {
            listener.updateDefaultInterface("", -1, false, false)
            return
        }

        val capabilities = connectivityManager.getNetworkCapabilities(network)
        listener.updateDefaultInterface(interfaceName, interfaceIndex, capabilities?.isMetered() ?: false, false)
    }

    @Suppress("DEPRECATION")
    private fun selectUnderlyingNetwork(preferredNetwork: Network?): Network? {
        // After the tunnel is established, Android may expose the VPN itself as default.
        return preferredNetwork?.takeIf { it.isUsableUnderlyingNetwork() }
            ?: connectivityManager.allNetworks.firstOrNull { it.isUsableUnderlyingNetwork() }
    }

    private fun Network.isUsableUnderlyingNetwork(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(this) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    @Suppress("DEPRECATION")
    private fun androidInterfaceMetadataByName(): Map<String, AndroidInterfaceMetadata> =
        connectivityManager.allNetworks.mapNotNull { network ->
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            val interfaceName = linkProperties.interfaceName ?: return@mapNotNull null
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            interfaceName to AndroidInterfaceMetadata(
                type = capabilities?.toLibboxInterfaceType() ?: Libbox.InterfaceTypeOther,
                dnsServers = linkProperties.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') },
                metered = capabilities?.isMetered() ?: false,
            )
        }.toMap()

    private fun NetworkCapabilities.isMetered(): Boolean =
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

    private fun NetworkCapabilities.toLibboxInterfaceType(): Int = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
        else -> Libbox.InterfaceTypeOther
    }
}

private data class AndroidInterfaceMetadata(
    val type: Int,
    val dnsServers: List<String>,
    val metered: Boolean,
)

private fun RoutePrefixIterator.toList(): List<RoutePrefix> {
    val values = mutableListOf<RoutePrefix>()
    while (hasNext()) values += next()
    return values
}

private fun StringIterator.drain(): List<String> {
    val values = mutableListOf<String>()
    while (hasNext()) values += next()
    return values
}

private fun VpnService.Builder.addRouteCompat(route: RoutePrefix) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        addRoute(route.toIpPrefix())
    } else {
        addRoute(route.address(), route.prefix())
    }
}

private fun RoutePrefix.toIpPrefix(): IpPrefix =
    IpPrefix(InetAddress.getByName(address()), prefix())

private fun java.util.Enumeration<NetworkInterface>.toList(): List<NetworkInterface> =
    buildList {
        while (hasMoreElements()) add(nextElement())
    }

private fun InterfaceAddress.toPrefixString(): String? {
    val host = address?.hostAddress?.substringBefore('%') ?: return null
    return "$host/$networkPrefixLength"
}

private fun NetworkInterface.flags(): Int {
    var flags = 0
    if (isUp) flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
    if (isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
    if (isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
    if (supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
    return flags
}
