package karst.vpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import karst.vpn.log.AppLogBuffer
import kotlinx.coroutines.delay

@Composable
fun LogsScreen(
    theme: VpnColors,
    onBack: () -> Unit,
) {
    val lines by AppLogBuffer.lines.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copyDone by remember { mutableStateOf(false) }
    var clearDone by remember { mutableStateOf(false) }

    LaunchedEffect(copyDone) {
        if (copyDone) { delay(1000); copyDone = false }
    }
    LaunchedEffect(clearDone) {
        if (clearDone) { delay(1000); clearDone = false }
    }

    val copyScale by animateFloatAsState(if (copyDone) 1.25f else 1f, label = "copyScale")
    val copyColor by animateColorAsState(if (copyDone) DefaultAccent else theme.mutedInk, label = "copyColor")

    val clearScale by animateFloatAsState(if (clearDone) 1.25f else 1f, label = "clearScale")
    val clearColor by animateColorAsState(if (clearDone) DefaultAccent else theme.mutedInk, label = "clearColor")

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
            Pressable(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = theme.ink)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Логи",
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = theme.ink,
                modifier = Modifier.weight(1f),
            )
            Pressable(onClick = {
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                copyDone = true
            }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Копировать",
                    tint = copyColor,
                    modifier = Modifier.graphicsLayer {
                        scaleX = copyScale
                        scaleY = copyScale
                    }
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Pressable(onClick = {
                AppLogBuffer.clear()
                clearDone = true
            }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Очистить",
                    tint = clearColor,
                    modifier = Modifier.graphicsLayer {
                        scaleX = clearScale
                        scaleY = clearScale
                    }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(lines) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = theme.ink,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
