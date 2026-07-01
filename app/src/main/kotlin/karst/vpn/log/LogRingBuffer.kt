package karst.vpn.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class LogRingBuffer(
    private val capacity: Int = 2000,
) {
    private val lock = Any()
    private val buffer = ArrayDeque<String>(capacity)
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())

    val lines: StateFlow<List<String>> = mutableLines.asStateFlow()

    fun append(line: String) {
        synchronized(lock) {
            if (buffer.size == capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(line)
            // sing-box can call this very frequently (e.g. while connecting); skip the
            // O(capacity) snapshot+publish when nobody is collecting `lines` to avoid
            // needless allocation churn. refresh() catches the screen back up on open.
            if (mutableLines.subscriptionCount.value > 0) {
                mutableLines.value = buffer.toList()
            }
        }
    }

    fun refresh() {
        synchronized(lock) {
            mutableLines.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            mutableLines.value = emptyList()
        }
    }
}

object AppLogBuffer : LogRingBuffer()
