package karst.vpn

import android.app.Application
import karst.vpn.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KarstApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        container.startBackgroundTasks(appScope)
    }

    companion object {
        lateinit var instance: KarstApplication
            private set
    }
}
