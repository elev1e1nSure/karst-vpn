package karst.vpn.ui

import androidx.compose.ui.graphics.Color

/**
 * Color tokens ported from the design's `theme` object. The original used OKLCH;
 * values below are sRGB approximations of the same swatches.
 */
data class VpnColors(
    val pageBg: Color,
    val appBg: Color,
    val cardBg: Color,
    val ink: Color,
    val mutedInk: Color,
    val border: Color,
    val buttonOffBg: Color,
    val buttonOffBorder: Color,
    val buttonOffIcon: Color,
)

val VpnDarkColors = VpnColors(
    pageBg = Color(0xFF1A1A19),
    appBg = Color(0xFF1A1A19),
    cardBg = Color(0xFF262421),
    ink = Color(0xFFEAE8E4),
    mutedInk = Color(0xFF98948E),
    border = Color(0xFF32302C),
    buttonOffBg = Color(0xFF262421),
    buttonOffBorder = Color(0xFF373430),
    buttonOffIcon = Color(0xFFA7A39D),
)

val VpnLightColors = VpnColors(
    pageBg = Color(0xFFDFDCD7),
    appBg = Color(0xFFEDEAE5),
    cardBg = Color(0xFFF9F8F5),
    ink = Color(0xFF352E27),
    mutedInk = Color(0xFF847D74),
    border = Color(0xFFDAD6CF),
    buttonOffBg = Color(0xFFF9F8F5),
    buttonOffBorder = Color(0xFFD1CDC5),
    buttonOffIcon = Color(0xFF675F56),
)

val DefaultAccent = Color(0xFFD97757)

enum class Mood(
    val chipRadius: Int,
    val subtitleOff: String,
    val subtitleConnecting: String,
) {
    Calm(16, "Нажми на кнопку, чтобы защититься", "Устанавливаем безопасное соединение"),
    Focused(14, "Готов к подключению", "Настраиваем туннель"),
    Urgent(10, "Защита выключена — нажми сейчас", "Срочно шифруем соединение"),
}
