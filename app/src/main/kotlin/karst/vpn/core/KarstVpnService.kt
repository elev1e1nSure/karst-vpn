package karst.vpn.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import karst.vpn.KarstApplication
import karst.vpn.MainActivity
import karst.vpn.R
import karst.vpn.log.AppLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class KarstVpnService : VpnService(), CommandServerHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var commandServer: CommandServer? = null
    var activeTunDescriptor: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupLibbox(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                scope.launch { stopTunnel() }
            }
            ACTION_CONNECT -> {
                startForegroundCompat(buildNotification("Подключаемся", null))
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
                if (serverId == null) {
                    ConnectionStateHolder.off("Сервер не выбран")
                    stopSelf()
                } else {
                    scope.launch { startTunnel(serverId) }
                }
            }
            else -> stopSelf()
        }
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? =
        super.onBind(intent)

    override fun onDestroy() {
        AppLogBuffer.append("onDestroy() called")
        try {
            runBlocking { stopTunnel() }
        } finally {
            scope.cancel()
            super.onDestroy()
        }
    }

    override fun onRevoke() {
        scope.launch { stopTunnel("VPN permission revoked") }
    }

    private suspend fun startTunnel(serverId: String) {
        ConnectionStateHolder.connecting()
        runCatching {
            withContext(Dispatchers.IO) {
                val app = KarstApplication.instance
                val server = app.container.serverRepository.getServer(serverId) ?: error("Сервер не найден")
                val dnsUrl = app.container.settingsRepository.dnsDohUrl.first()
                val config = VpnConfigBuilder.build(server, dnsUrl)

                commandServer?.closeService()
                commandServer?.close()
                val nextServer = CommandServer(this@KarstVpnService, PlatformInterfaceImpl(this@KarstVpnService))
                nextServer.start()
                nextServer.startOrReloadService(config, OverrideOptions())
                commandServer = nextServer
            }
        }.onSuccess {
            val connectedAt = System.currentTimeMillis()
            SocketProtectorRegistry.current = SocketProtector { socket -> protect(socket) }
            ConnectionStateHolder.connected(connectedAt)
            Haptics.heavy(this)
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification("Подключено", connectedAt))
            AppLogBuffer.append("VPN connected")
        }.onFailure {
            AppLogBuffer.append("VPN start failed: ${it.message}")
            stopTunnel(it.message ?: "Не удалось подключиться")
        }
    }

    private suspend fun stopTunnel(error: String? = null) = withContext(NonCancellable) {
        withContext(Dispatchers.IO) {
            runCatching { commandServer?.closeService() }
                .onFailure { AppLogBuffer.append("Failed to close libbox service: ${it.message}") }
            runCatching { commandServer?.close() }
                .onFailure { AppLogBuffer.append("Failed to close command server: ${it.message}") }
            commandServer = null

            runCatching { activeTunDescriptor?.close() }
                .onFailure { AppLogBuffer.append("Failed to close TUN descriptor: ${it.message}") }
            activeTunDescriptor = null
        }
        SocketProtectorRegistry.current = null
        ConnectionStateHolder.off(error)
        Haptics.heavy(this@KarstVpnService)
        stopForegroundCompat()
        stopSelf()
    }

    override fun serviceStop() {
        scope.launch { stopTunnel() }
    }

    override fun serviceReload() = Unit

    override fun writeDebugMessage(message: String) {
        AppLogBuffer.append(message)
    }

    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply {
            available = false
            enabled = false
        }

    override fun setSystemProxyEnabled(enabled: Boolean) = Unit

    private fun buildNotification(status: String, connectedSinceMillis: Long?): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, KarstVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle("Karst VPN")
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_power, "Отключить", disconnectIntent)

        if (connectedSinceMillis != null) {
            builder
                .setWhen(connectedSinceMillis)
                .setUsesChronometer(true)
        }

        return builder.build()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN status",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_CONNECT = "karst.vpn.action.CONNECT"
        private const val ACTION_DISCONNECT = "karst.vpn.action.DISCONNECT"
        private const val EXTRA_SERVER_ID = "server_id"
        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1001

        fun connect(context: Context, serverId: String) {
            val intent = Intent(context, KarstVpnService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_SERVER_ID, serverId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun disconnect(context: Context) {
            val intent = Intent(context, KarstVpnService::class.java).setAction(ACTION_DISCONNECT)
            context.startService(intent)
        }

        private var libboxReady = false

        @Synchronized
        private fun setupLibbox(context: Context) {
            if (libboxReady) return
            val options = SetupOptions().apply {
                basePath = context.filesDir.path
                workingPath = (context.getExternalFilesDir(null) ?: context.filesDir).path
                tempPath = context.cacheDir.path
                fixAndroidStack = true
                logMaxLines = 2000
                debug = context.isDebuggable()
            }
            Libbox.setup(options)
            libboxReady = true
        }

        private fun Context.isDebuggable(): Boolean =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
