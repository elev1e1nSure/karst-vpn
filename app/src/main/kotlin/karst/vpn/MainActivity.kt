package karst.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import karst.vpn.core.KarstVpnService
import karst.vpn.ui.VpnScreen
import karst.vpn.ui.VpnViewModel

class MainActivity : ComponentActivity() {
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>
    private var pendingServerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val serverId = pendingServerId
            pendingServerId = null
            if (result.resultCode == Activity.RESULT_OK && serverId != null) {
                KarstVpnService.connect(this, serverId)
            }
        }

        setContent {
            val application = application as KarstApplication
            val vpnViewModel: VpnViewModel = viewModel(factory = VpnViewModel.Factory(application))
            VpnScreen(
                viewModel = vpnViewModel,
                onConnectRequest = ::requestVpnStart,
                onDisconnectRequest = { KarstVpnService.disconnect(this) },
                onThemeChanged = { isDark ->
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                },
            )
        }
    }

    private fun requestVpnStart(serverId: String) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingServerId = serverId
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            KarstVpnService.connect(this, serverId)
        }
    }
}
