package karst.vpn.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import karst.vpn.log.AppLogBuffer
import karst.vpn.core.Haptics

@Composable
fun LogsScreen(
    theme: VpnColors,
    onBack: () -> Unit,
) {
    val lines by AppLogBuffer.lines.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) { AppLogBuffer.refresh() }

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
                Haptics.click(context)
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
            }) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Копировать",
                    tint = theme.mutedInk,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Pressable(onClick = {
                Haptics.medium(context)
                AppLogBuffer.clear()
            }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Очистить",
                    tint = theme.mutedInk,
                )
            }
        }

        if (lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
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
}
