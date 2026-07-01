package karst.vpn.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import karst.vpn.KarstApplication
import karst.vpn.data.entities.SubscriptionEntity
import karst.vpn.log.AppLog
import kotlinx.coroutines.flow.first

private const val AUTO_REFRESH_WORK_NAME = "subscription_auto_refresh"

data class SubscriptionAutoRefreshConfig(
    val mode: SubscriptionAutoRefreshMode,
    val customHours: Int,
    val subscriptions: List<SubscriptionEntity>,
)

class SubscriptionAutoRefreshScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private var lastAppliedHours: Int? = null

    fun apply(config: SubscriptionAutoRefreshConfig) {
        val hours = config.repeatHoursOrNull()
        AppLog.info(AppLog.Category.SERVICE, "SubscriptionAutoRefreshScheduler.apply: mode=${config.mode} resolvedHours=$hours")
        if (hours == null) {
            lastAppliedHours = null
            AppLog.info(AppLog.Category.SERVICE, "Auto-refresh disabled. Cancelling periodic work.")
            workManager.cancelUniqueWork(AUTO_REFRESH_WORK_NAME)
            return
        }
        if (lastAppliedHours == hours) {
            AppLog.info(AppLog.Category.SERVICE, "Auto-refresh schedule already matches desired interval: $hours hours.")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        AppLog.info(AppLog.Category.SERVICE, "Scheduling periodic auto-refresh every $hours hours.")
        workManager.enqueueUniquePeriodicWork(
            AUTO_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        lastAppliedHours = hours
    }
}

class SubscriptionRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KarstApplication
        AppLog.info(AppLog.Category.NET, "SubscriptionRefreshWorker: work execution started")
        if (app.container.settingsRepository.subscriptionAutoRefreshMode.first() == SubscriptionAutoRefreshMode.Off) {
            AppLog.info(AppLog.Category.NET, "SubscriptionRefreshWorker: auto-refresh is disabled. Skipping.")
            return Result.success()
        }

        return app.container.subscriptionRefresher.refreshAll()
            .fold(
                onSuccess = {
                    AppLog.info(AppLog.Category.NET, "SubscriptionRefreshWorker: refreshAll succeeded")
                    Result.success()
                },
                onFailure = {
                    AppLog.error(AppLog.Category.NET, "SubscriptionRefreshWorker: refreshAll failed, will retry", it)
                    Result.retry()
                },
            )
    }
}

private fun SubscriptionAutoRefreshConfig.repeatHoursOrNull(): Int? =
    when (mode) {
        SubscriptionAutoRefreshMode.Off -> null
        SubscriptionAutoRefreshMode.EveryHours -> customHours.coerceAtLeast(1)
        SubscriptionAutoRefreshMode.Auto -> subscriptions
            .mapNotNull { it.profileUpdateIntervalHours }
            .filter { it > 0 }
            .minOrNull()
            ?: SettingsRepository.DEFAULT_AUTO_REFRESH_HOURS
    }.takeIf { subscriptions.isNotEmpty() }
