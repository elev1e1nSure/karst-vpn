package karst.vpn.ui

data class UiServer(
    val id: String,
    val name: String,
    val tag: String,
    val latencyLabel: String,
    val isCustom: Boolean = false,
    val subscriptionId: String? = null,
)

data class UiSubscription(
    val id: String?,
    val name: String,
    val announce: String? = null,
    val servers: List<UiServer>,
)

fun formatElapsed(totalSeconds: Int): String {
    val m = (totalSeconds / 60).toString().padStart(2, '0')
    val s = (totalSeconds % 60).toString().padStart(2, '0')
    return "$m:$s"
}
