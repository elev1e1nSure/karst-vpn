package karst.vpn.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStateHolderTest {

    @After
    fun tearDown() {
        ConnectionStateHolder.off()
    }

    @Test
    fun testInitialStateIsOff() {
        ConnectionStateHolder.off()
        assertEquals(ConnectionPhase.Off, ConnectionStateHolder.phase.value)
        assertNull(ConnectionStateHolder.connectedSinceMillis.value)
    }

    @Test
    fun testConnectingClearsPreviousError() {
        ConnectionStateHolder.off("previous failure")
        assertEquals("previous failure", ConnectionStateHolder.lastError.value)

        ConnectionStateHolder.connecting()
        assertEquals(ConnectionPhase.Connecting, ConnectionStateHolder.phase.value)
        assertNull(ConnectionStateHolder.lastError.value)
    }

    @Test
    fun testConnectedSetsPhaseAndTimestampAndClearsError() {
        ConnectionStateHolder.off("boom")
        ConnectionStateHolder.connected(sinceMillis = 12345L)

        assertEquals(ConnectionPhase.On, ConnectionStateHolder.phase.value)
        assertEquals(12345L, ConnectionStateHolder.connectedSinceMillis.value)
        assertNull(ConnectionStateHolder.lastError.value)
    }

    @Test
    fun testOffAfterConnectedResetsTimestampAndSetsError() {
        ConnectionStateHolder.connected(sinceMillis = 999L)
        ConnectionStateHolder.off("connection lost")

        assertEquals(ConnectionPhase.Off, ConnectionStateHolder.phase.value)
        assertNull(ConnectionStateHolder.connectedSinceMillis.value)
        assertEquals("connection lost", ConnectionStateHolder.lastError.value)
    }

    @Test
    fun testOffWithoutErrorClearsLastError() {
        ConnectionStateHolder.off("some error")
        ConnectionStateHolder.off()

        assertNull(ConnectionStateHolder.lastError.value)
        assertTrue(ConnectionStateHolder.phase.value == ConnectionPhase.Off)
    }
}
