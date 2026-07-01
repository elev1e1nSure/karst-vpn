package karst.vpn.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import karst.vpn.net.LatencyProbe
import karst.vpn.net.NetworkSubscriptionFetcher
import karst.vpn.net.SocketLatencyProbe
import karst.vpn.net.SubscriptionFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: KarstDatabase = Room.databaseBuilder(
        appContext,
        KarstDatabase::class.java,
        "karst.db",
    ).addMigrations(MIGRATION_1_2).build()

    val settingsRepository = SettingsRepository(appContext)
    val subscriptionFetcher: SubscriptionFetcher = NetworkSubscriptionFetcher()
    val latencyProbe: LatencyProbe = SocketLatencyProbe()
    val serverRepository = ServerRepository(database)
    val importCoordinator = ImportCoordinator(database, subscriptionFetcher)
    val subscriptionRefresher = SubscriptionRefresher(database, subscriptionFetcher)
    private val subscriptionAutoRefreshScheduler = SubscriptionAutoRefreshScheduler(appContext)
    val latencyTracker = LatencyTracker(database, latencyProbe)

    fun startBackgroundTasks(scope: CoroutineScope) {
        scope.launch {
            combine(
                settingsRepository.subscriptionAutoRefreshMode,
                settingsRepository.subscriptionAutoRefreshHours,
                database.subscriptions().observeAll(),
            ) { mode, hours, subscriptions ->
                SubscriptionAutoRefreshConfig(mode, hours, subscriptions)
            }.collect(subscriptionAutoRefreshScheduler::apply)
        }
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN announce TEXT")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN profileUpdateIntervalHours INTEGER")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN profileWebPageUrl TEXT")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN routingEnabled INTEGER")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN uploadBytes INTEGER")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN downloadBytes INTEGER")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN totalBytes INTEGER")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN expireAtEpochSeconds INTEGER")
    }
}
