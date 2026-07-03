package karst.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibboxIteratorsTest {

    @Test
    fun testEmptyList() {
        val iterator = StringListIterator(emptyList())
        assertEquals(0, iterator.len())
        assertFalse(iterator.hasNext())
    }

    @Test
    fun testSingleElement() {
        val iterator = StringListIterator(listOf("only"))
        assertEquals(1, iterator.len())
        assertTrue(iterator.hasNext())
        assertEquals("only", iterator.next())
        assertFalse(iterator.hasNext())
    }

    @Test
    fun testIteratesInOrderAndStopsAtEnd() {
        val values = listOf("a", "b", "c")
        val iterator = StringListIterator(values)

        val collected = mutableListOf<String>()
        while (iterator.hasNext()) {
            collected += iterator.next()
        }

        assertEquals(values, collected)
        assertFalse(iterator.hasNext())
    }

    @Test
    fun testLenStaysConstantAsIterationProgresses() {
        val iterator = StringListIterator(listOf("x", "y"))
        assertEquals(2, iterator.len())
        iterator.next()
        assertEquals(2, iterator.len())
    }
}
