package karst.vpn

import android.app.Application
import karst.vpn.data.AppContainer

class KarstApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: KarstApplication
            private set
    }
}
