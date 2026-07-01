package karst.vpn.log

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    enum class Category {
        VPN, NET, LINK, DB, CORE, SERVICE
    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val isRunningOnAndroid = runCatching {
        android.os.Build.VERSION.SDK_INT > 0
    }.getOrDefault(false)

    fun debug(category: Category, message: String) = log(Level.DEBUG, category, message)
    fun info(category: Category, message: String) = log(Level.INFO, category, message)
    fun warn(category: Category, message: String) = log(Level.WARN, category, message)
    fun error(category: Category, message: String, throwable: Throwable? = null) = log(Level.ERROR, category, message, throwable)

    private fun log(level: Level, category: Category, message: String, throwable: Throwable? = null) {
        val tag = "KarstLog:${category.name}"
        val sanitizedMsg = sanitize(message)
        val timestamp = timeFormat.format(Date())

        if (isRunningOnAndroid) {
            when (level) {
                Level.DEBUG -> if (throwable != null) Log.d(tag, sanitizedMsg, throwable) else Log.d(tag, sanitizedMsg)
                Level.INFO -> if (throwable != null) Log.i(tag, sanitizedMsg, throwable) else Log.i(tag, sanitizedMsg)
                Level.WARN -> if (throwable != null) Log.w(tag, sanitizedMsg, throwable) else Log.w(tag, sanitizedMsg)
                Level.ERROR -> if (throwable != null) Log.e(tag, sanitizedMsg, throwable) else Log.e(tag, sanitizedMsg)
            }
        } else {
            val consoleLine = "[$timestamp] [${level.name}] [$category] $sanitizedMsg"
            if (level == Level.ERROR) {
                System.err.println(consoleLine)
                throwable?.printStackTrace()
            } else {
                println(consoleLine)
            }
        }

        // Always write formatted message to the in-memory RingBuffer
        val suffix = if (throwable != null) " | Exception: ${throwable.message} ${throwable.stackTraceToString().take(300)}..." else ""
        AppLogBuffer.append("[$timestamp] [${level.name}] [$category] $sanitizedMsg$suffix")
    }

    fun sanitize(message: String): String {
        var result = message

        // 1. Mask VLESS UUID/user info: vless://UUID@host
        val vlessRegex = """(?i)vless://[^@\s]+@""".toRegex()
        result = result.replace(vlessRegex, "vless://***@")

        // 2. Mask user:password in http/https URLs
        val urlUserPassRegex = """(?i)(https?://)[^:/]+:[^@]+@""".toRegex()
        result = result.replace(urlUserPassRegex, "$1***:***@")

        // 3. Mask sensitive query parameters in URLs or strings (token, pass, key, secret, etc.)
        val queryParamRegex = """(?i)(token|pass|password|key|secret|uuid|id)=[^&\s#]+""".toRegex()
        result = result.replace(queryParamRegex, "$1=***")

        return result
    }
}
