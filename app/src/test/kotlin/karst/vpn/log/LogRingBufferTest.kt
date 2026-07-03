package karst.vpn.log

import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRingBufferTest {

    @Test
    fun testAppendKeepsInsertionOrder() {
        val buffer = LogRingBuffer(capacity = 10)
        buffer.append("a")
        buffer.append("b")
        buffer.append("c")
        buffer.refresh()

        assertEquals(listOf("a", "b", "c"), buffer.lines.value)
    }

    @Test
    fun testAppendEvictsOldestWhenOverCapacity() {
        val buffer = LogRingBuffer(capacity = 3)
        buffer.append("1")
        buffer.append("2")
        buffer.append("3")
        buffer.append("4")
        buffer.refresh()

        assertEquals(listOf("2", "3", "4"), buffer.lines.value)
    }

    @Test
    fun testClearEmptiesBuffer() {
        val buffer = LogRingBuffer(capacity = 5)
        buffer.append("x")
        buffer.append("y")
        buffer.clear()

        assertEquals(emptyList<String>(), buffer.lines.value)
        buffer.refresh()
        assertEquals(emptyList<String>(), buffer.lines.value)
    }

    @Test
    fun testRefreshPublishesLinesWithoutActiveSubscriber() {
        val buffer = LogRingBuffer(capacity = 5)
        buffer.append("only")
        assertEquals(emptyList<String>(), buffer.lines.value)

        buffer.refresh()
        assertEquals(listOf("only"), buffer.lines.value)
    }

    @Test
    fun testConcurrentAppendsDoNotExceedCapacityOrCorruptState() {
        val capacity = 100
        val buffer = LogRingBuffer(capacity = capacity)
        val threads = (0 until 8).map { threadIndex ->
            thread {
                repeat(50) { i -> buffer.append("t$threadIndex-$i") }
            }
        }
        threads.forEach { it.join() }
        buffer.refresh()

        assertTrue(buffer.lines.value.size <= capacity)
        assertEquals(capacity, buffer.lines.value.size)
    }
}
