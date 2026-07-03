package karst.vpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import karst.vpn.log.AppLogBuffer
import karst.vpn.core.Haptics
import kotlinx.coroutines.delay

private val logPrefixRegex = Regex("""^(\[.*?]\s*)+""")

@Composable
fun LogsScreen(
    theme: VpnColors,
    accent: Color,
    darkModeOn: Boolean,
    onBack: () -> Unit,
) {
    val lines by AppLogBuffer.lines.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val actionsEnabled = lines.isNotEmpty()

    LaunchedEffect(Unit) { AppLogBuffer.refresh() }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1800)
            toastMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.appBg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pressable(onClick = {
                    Haptics.click(context)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = theme.ink)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Логи",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = theme.ink,
                    modifier = Modifier.weight(1f),
                )
                Pressable(onClick = {
                    if (!actionsEnabled) return@Pressable
                    Haptics.click(context)
                    clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                    toastMessage = "Логи скопированы"
                }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Копировать",
                        tint = theme.mutedInk.copy(alpha = if (actionsEnabled) 1f else 0.4f),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Pressable(onClick = {
                    if (!actionsEnabled) return@Pressable
                    Haptics.medium(context)
                    AppLogBuffer.clear()
                    toastMessage = "Логи очищены"
                }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Очистить",
                        tint = theme.mutedInk.copy(alpha = if (actionsEnabled) 1f else 0.4f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(theme.border),
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = theme.mutedInk.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Логов пока нет",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = theme.mutedInk,
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(lines) { line ->
                        val prefix = logPrefixRegex.find(line)?.value.orEmpty()
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = accent)) { append(prefix) }
                                append(line.removePrefix(prefix))
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = theme.ink,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        val toastBg = if (darkModeOn) Color(0xFFEBE8E3) else Color.Black.copy(alpha = 0.82f)
        val toastInk = if (darkModeOn) Color(0xFF1A1A19) else Color.White
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 28.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.96f),
                exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f),
            ) {
                Box(
                    modifier = Modifier
                        .background(toastBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = toastMessage.orEmpty(),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        color = toastInk,
                    )
                }
            }
        }
    }
}
