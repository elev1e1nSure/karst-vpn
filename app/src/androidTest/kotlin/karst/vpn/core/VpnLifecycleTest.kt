package karst.vpn.core

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        // Simulating starting the service. It will handle the CONNECT action,
        // search for the server ID, and since it is not found, it should stop itself
        // and update ConnectionStateHolder to off with an error.
        context.startService(intent)

        // Verify that it reached some state in ConnectionStateHolder (off with error)
        val state = ConnectionStateHolder.phase.value
        assertEquals(ConnectionPhase.Off, state)
        
        // Disconnect simulation
        val disconnectIntent = Intent(context, KarstVpnService::class.java).apply {
            action = "karst.vpn.action.DISCONNECT"
        }
        context.startService(disconnectIntent)
        
        assertEquals(ConnectionPhase.Off, ConnectionStateHolder.phase.value)
    }
}
