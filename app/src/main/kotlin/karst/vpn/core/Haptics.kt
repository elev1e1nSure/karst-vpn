package karst.vpn.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {

    fun click(context: Context) = vibrate(context, 10, 0.3f)

    fun light(context: Context) = vibrate(context, 10, 0.5f)

    fun medium(context: Context) = vibrate(context, 25, 0.8f)

    fun heavy(context: Context) = vibrate(context, 50, 1.0f)

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, durationMs: Long, amplitude: Float) {
        val intensity = (amplitude * 255).toInt().coerceIn(1, 255)
        val effect = VibrationEffect.createOneShot(durationMs, intensity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(effect)
        } else {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(effect)
        }
    }
}
