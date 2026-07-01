package karst.vpn.data

import android.content.Context
import androidx.room.Room
import karst.vpn.net.LatencyProbe
import karst.vpn.net.SubscriptionFetcher
import karst.vpn.net.NetworkSubscriptionFetcher
import karst.vpn.net.SocketLatencyProbe

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: KarstDatabase = Room.databaseBuilder(
        appContext,
        KarstDatabase::class.java,
        "karst.db",
    ).build()

    val settingsRepository = SettingsRepository(appContext)
    val subscriptionFetcher: SubscriptionFetcher = NetworkSubscriptionFetcher()
    val latencyProbe: LatencyProbe = SocketLatencyProbe()
    val serverRepository = ServerRepository(database, subscriptionFetcher, latencyProbe)
}
