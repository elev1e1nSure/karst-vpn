package karst.vpn.core

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnLifecycleTest {

    @Test
    fun testVpnServiceCommandHandlingWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, KarstVpnService::class.java).apply {
            action = "karst.vpn.action.CONNECT"
            putExtra("server_id", "non-existent-id-dummy")
        }

        context.startService(intent)

        val state = ConnectionStateHolder.phase.value
        assertEquals(ConnectionPhase.Off, state)

        val disconnectIntent = Intent(context, KarstVpnService::class.java).apply {
            action = "karst.vpn.action.DISCONNECT"
        }
        context.startService(disconnectIntent)

        assertEquals(ConnectionPhase.Off, ConnectionStateHolder.phase.value)
    }
}
