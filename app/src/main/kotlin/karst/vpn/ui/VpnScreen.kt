package karst.vpn.ui

import karst.vpn.R
import karst.vpn.core.ConnectionPhase
import karst.vpn.core.Haptics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private enum class Route {
    Main,
    Logs,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnScreen(
    viewModel: VpnViewModel,
    modifier: Modifier = Modifier,
    accent: Color = DefaultAccent,
    mood: Mood = Mood.Focused,
    onConnectRequest: (String) -> Unit,
    onDisconnectRequest: () -> Unit,
    onThemeChanged: (isDark: Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val theme = if (state.darkModeOn) VpnDarkColors else VpnLightColors
    var route by remember { mutableStateOf(Route.Main) }

    LaunchedEffect(state.darkModeOn) { onThemeChanged(state.darkModeOn) }

    val colorScheme = remember(theme, accent) {
        val base = if (state.darkModeOn) darkColorScheme() else lightColorScheme()
        base.copy(
            background = theme.appBg,
            onBackground = theme.ink,
            surface = theme.cardBg,
            onSurface = theme.ink,
            surfaceVariant = theme.cardBg,
            onSurfaceVariant = theme.mutedInk,
            outline = theme.border,
            primary = accent,
            onPrimary = Color.White,
            error = Color(0xFFA56060),
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        if (route == Route.Logs) {
            LogsScreen(theme = theme, onBack = { route = Route.Main })
        } else {
            MainVpnScreen(
                state = state,
                viewModel = viewModel,
                modifier = modifier,
                theme = theme,
                accent = accent,
                mood = mood,
                onOpenLogs = { route = Route.Logs },
                onConnectRequest = onConnectRequest,
                onDisconnectRequest = onDisconnectRequest,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainVpnScreen(
    state: VpnUiState,
    viewModel: VpnViewModel,
    modifier: Modifier,
    theme: VpnColors,
    accent: Color,
    mood: Mood,
    onOpenLogs: () -> Unit,
    onConnectRequest: (String) -> Unit,
    onDisconnectRequest: () -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var addServerOpen by remember { mutableStateOf(false) }
    var addServerValue by remember { mutableStateOf("") }

    LaunchedEffect(state.importMessage) {
        if (state.importMessage != null) {
            addServerOpen = false
            addServerValue = ""
        }
    }

    val selectedServer = state.servers.find { it.id == state.selectedServerId }
    val sheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val dragHandle: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(theme.border),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.appBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp)
                .padding(top = 8.dp, bottom = 36.dp),
        ) {
            Header(theme = theme, onSettingsClick = { settingsVisible = true })

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CenterStage(
                    phase = state.phase,
                    connectedSinceMillis = state.connectedSinceMillis,
                    hasServer = selectedServer != null,
                    theme = theme,
                    accent = accent,
                    mood = mood,
                    onTapButton = {
                        when (state.phase) {
                            ConnectionPhase.Off -> selectedServer?.id?.let(onConnectRequest)
                            ConnectionPhase.On -> onDisconnectRequest()
                            ConnectionPhase.Connecting -> Unit
                        }
                    },
                )
            }

            LocationChip(
                server = selectedServer,
                theme = theme,
                chipRadiusDp = mood.chipRadius,
                onClick = { menuVisible = true },
            )
        }
    }

    if (menuVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                menuVisible = false
                addServerOpen = false
                viewModel.clearAddError()
                viewModel.clearImportMessage()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = sheetShape,
            containerColor = theme.appBg,
            dragHandle = dragHandle,
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                ServerSheetContent(
                    theme = theme,
                    accent = accent,
                    groups = state.subscriptionGroups,
                    selectedServerId = state.selectedServerId,
                    addServerOpen = addServerOpen,
                    addServerValue = addServerValue,
                    addServerError = state.addServerError,
                    addServerLoading = state.addServerLoading,
                    importMessage = state.importMessage,
                    refreshAllVersion = state.refreshAllVersion,
                    refreshAllLoading = state.refreshAllLoading,
                    onSelect = { id ->
                        viewModel.selectServer(id)
                        menuVisible = false
                    },
                    onRemove = viewModel::deleteServer,
                    onRefreshAll = viewModel::refreshAllSubscriptions,
                    onOpenAddServer = {
                        addServerOpen = true
                        viewModel.clearAddError()
                        viewModel.clearImportMessage()
                    },
                    onCancelAddServer = {
                        addServerOpen = false
                        addServerValue = ""
                        viewModel.clearAddError()
                    },
                    onChangeAddServerValue = {
                        addServerValue = it
                        viewModel.clearAddError()
                    },
                    onSubmitAddServer = { viewModel.addServerInput(addServerValue) },
                )
            }
        }
    }

    if (settingsVisible) {
        ModalBottomSheet(
            onDismissRequest = { settingsVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = sheetShape,
            containerColor = theme.appBg,
            dragHandle = dragHandle,
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                SettingsSheetContent(
                    theme = theme,
                    accent = accent,
                    darkModeOn = state.darkModeOn,
                    onToggleDarkMode = { viewModel.setDarkMode(!state.darkModeOn) },
                    notifications = state.notificationsEnabled,
                    onToggleNotifications = { viewModel.setNotifications(!state.notificationsEnabled) },
                    onOpenLogs = {
                        settingsVisible = false
                        onOpenLogs()
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(theme: VpnColors, onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pressable(onClick = {
            Haptics.click(context)
            onSettingsClick()
        }, pressedScale = 0.92f, modifier = Modifier.testTag("settings_btn")) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(theme.cardBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Настройки",
                    tint = theme.mutedInk,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun CenterStage(
    phase: ConnectionPhase,
    connectedSinceMillis: Long?,
    hasServer: Boolean,
    theme: VpnColors,
    accent: Color,
    mood: Mood,
    onTapButton: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase, connectedSinceMillis) {
        while (phase == ConnectionPhase.On && connectedSinceMillis != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = connectedSinceMillis?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) } ?: 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        ConnectButton(
            phase = phase,
            enabled = hasServer || phase == ConnectionPhase.On,
            theme = theme,
            accent = accent,
            onClick = onTapButton,
        )

        Column(
            modifier = Modifier.heightIn(min = 78.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val statusLabel = when {
                !hasServer && phase == ConnectionPhase.Off -> "Сервер не выбран"
                phase == ConnectionPhase.Off -> "Не подключено"
                phase == ConnectionPhase.Connecting -> "Подключаемся..."
                else -> "Подключено"
            }
            Text(
                text = statusLabel,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                color = theme.ink,
            )
            if (phase == ConnectionPhase.On) {
                Text(
                    text = formatElapsed(elapsed),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = theme.mutedInk,
                )
            } else {
                Text(
                    text = if (!hasServer) "Добавь VLESS-ссылку или подписку" else if (phase == ConnectionPhase.Connecting) mood.subtitleConnecting else mood.subtitleOff,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    color = theme.mutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(250.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectButton(
    phase: ConnectionPhase,
    enabled: Boolean,
    theme: VpnColors,
    accent: Color,
    onClick: () -> Unit,
) {
    val isConnecting = phase == ConnectionPhase.Connecting
    val isConnected = phase == ConnectionPhase.On
    val buttonBg = if (isConnected) accent else theme.buttonOffBg
    val borderColor = if (isConnected) Color.Transparent else if (isConnecting) accent else theme.buttonOffBorder
    val iconColor = if (isConnected) Color.White else if (isConnecting) accent else theme.buttonOffIcon
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "buttonScale")

    Box(modifier = Modifier.size(208.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (isConnected) 0.16f else 0f)),
        )

        Box(
            modifier = Modifier
                .size(152.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.55f
                }
                .clip(CircleShape)
                .background(buttonBg)
                .border(1.5.dp, borderColor, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled && !isConnecting,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = Color.White,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = "Подключить VPN",
                    tint = iconColor,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun LocationChip(server: UiServer?, theme: VpnColors, chipRadiusDp: Int, onClick: () -> Unit) {
    Pressable(onClick = onClick, pressedScale = 0.985f, modifier = Modifier.fillMaxWidth().testTag("location_chip")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(chipRadiusDp.dp))
                .background(theme.cardBg)
                .border(1.dp, theme.border, RoundedCornerShape(chipRadiusDp.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.pageBg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .border(2.dp, theme.mutedInk, CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(server?.name ?: "Добавить сервер", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = theme.ink)
                Text(server?.tag ?: "VLESS-ссылка или URL подписки", fontSize = 12.sp, color = theme.mutedInk)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.mutedInk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ServerSheetContent(
    theme: VpnColors,
    accent: Color,
    groups: List<UiSubscription>,
    selectedServerId: String?,
    addServerOpen: Boolean,
    addServerValue: String,
    addServerError: String?,
    addServerLoading: Boolean,
    importMessage: String?,
    refreshAllVersion: Int,
    refreshAllLoading: Boolean,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onOpenAddServer: () -> Unit,
    onCancelAddServer: () -> Unit,
    onChangeAddServerValue: (String) -> Unit,
    onSubmitAddServer: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Выбор сервера",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = theme.ink,
        )
    }

    if (groups.isEmpty()) {
        Text(
            "Список пуст. Добавь VLESS-ссылку или URL подписки ниже.",
            fontSize = 13.sp,
            color = theme.mutedInk,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    } else {
        // Material3 ModalBottomSheet forwards any scroll delta the LazyColumn doesn't
        // consume (e.g. at the list edges) to its own drag handling, which makes the
        // sheet jump toward dismiss mid-scroll. Swallowing unconsumed post-scroll here
        // keeps drag-to-dismiss limited to the handle instead of fighting the list.
        val listNestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    return available
                }
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(refreshAllVersion) {
            if (refreshAllVersion > 0 && listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(0)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RefreshAllButton(
                loading = refreshAllLoading,
                accent = accent,
                onClick = onRefreshAll,
            )
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp).nestedScroll(listNestedScrollConnection),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groups.forEach { group ->
                item(key = "group-${group.id}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.cardBg)
                            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        SubscriptionGroupHeader(
                            name = group.name,
                            announce = group.announce,
                            theme = theme,
                            accent = accent,
                            onRefresh = null,
                        )
                        group.servers.forEach { server ->
                            ServerRow(
                                server = server,
                                isSelected = server.id == selectedServerId,
                                theme = theme,
                                accent = accent,
                                onSelect = { onSelect(server.id) },
                                onRemove = { onRemove(server.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    importMessage?.takeIf { it.isNotBlank() }?.let {
        Text(it, fontSize = 12.sp, color = accent, modifier = Modifier.padding(vertical = 8.dp))
    }

    Spacer(modifier = Modifier.height(6.dp))

    if (addServerOpen) {
        AddServerForm(
            theme = theme,
            accent = accent,
            value = addServerValue,
            error = addServerError,
            loading = addServerLoading,
            onChangeValue = onChangeAddServerValue,
            onCancel = onCancelAddServer,
            onSubmit = onSubmitAddServer,
        )
    } else {
        Pressable(onClick = onOpenAddServer, modifier = Modifier.fillMaxWidth().testTag("open_add_server_btn")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, theme.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(theme.cardBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = theme.mutedInk, modifier = Modifier.size(14.dp))
                }
                Text(
                    "Добавить VLESS или подписку",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = theme.mutedInk,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionGroupHeader(
    name: String,
    announce: String?,
    theme: VpnColors,
    accent: Color,
    onRefresh: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = theme.ink,
            )
            announce?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = theme.mutedInk,
                    lineHeight = 16.sp,
                )
            }
        }
        if (onRefresh != null) {
            var isRefreshing by remember { mutableStateOf(false) }
            LaunchedEffect(isRefreshing) {
                if (isRefreshing) {
                    delay(1200)
                    isRefreshing = false
                }
            }

            Pressable(
                onClick = {
                    isRefreshing = true
                    Haptics.medium(context)
                    onRefresh()
                },
                pressedScale = 0.85f,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = accent,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Обновить подписку",
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshAllButton(
    loading: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Pressable(
        onClick = {
            Haptics.medium(context)
            onClick()
        },
        pressedScale = 0.85f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(accent.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = accent,
                    strokeWidth = 2.5.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Обновить все подписки",
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Обновить",
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = accent,
            )
        }
    }
}

@Composable
private fun ServerRow(
    server: UiServer,
    isSelected: Boolean,
    theme: VpnColors,
    accent: Color,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Pressable(onClick = {
        Haptics.click(context)
        onSelect()
    }, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accent else theme.border),
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(server.name, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = theme.ink)
                Spacer(modifier = Modifier.width(8.dp))
                Text(server.latencyLabel, fontSize = 14.sp, color = theme.mutedInk)
            }
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            if (server.isCustom) {
                Pressable(onClick = onRemove) {
                    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = theme.mutedInk, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun AddServerForm(
    theme: VpnColors,
    accent: Color,
    value: String,
    error: String?,
    loading: Boolean,
    onChangeValue: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.cardBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("VLESS-ссылка или URL подписки", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = theme.mutedInk)
        OutlinedTextField(
            value = value,
            onValueChange = onChangeValue,
            placeholder = { Text("vless://... или https://.../sub/...", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
            singleLine = true,
            enabled = !loading,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = theme.ink),
            trailingIcon = {
                IconButton(
                    onClick = {
                        clipboardManager.getText()
                            ?.text
                            ?.takeIf { it.isNotBlank() }
                            ?.let(onChangeValue)
                    },
                    enabled = !loading,
                    modifier = Modifier.testTag("paste_clipboard_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = "Вставить из буфера",
                        tint = theme.mutedInk,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = theme.pageBg,
                unfocusedContainerColor = theme.pageBg,
                focusedBorderColor = theme.border,
                unfocusedBorderColor = theme.border,
            ),
            modifier = Modifier.fillMaxWidth().testTag("add_server_input"),
        )
        if (error != null) {
            Text(error, fontSize = 12.sp, color = Color(0xFFA56060))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pressable(onClick = onCancel, enabled = !loading, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Отмена", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = theme.mutedInk)
                }
            }
            Pressable(onClick = {
                Haptics.medium(context)
                onSubmit()
            }, enabled = !loading, modifier = Modifier.weight(1f).testTag("add_server_submit")) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = if (loading) 0.6f else 1f))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (loading) "Добавляем..." else "Добавить", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SettingsSheetContent(
    theme: VpnColors,
    accent: Color,
    darkModeOn: Boolean,
    onToggleDarkMode: () -> Unit,
    notifications: Boolean,
    onToggleNotifications: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    Text(
        text = "Настройки",
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        color = theme.ink,
        modifier = Modifier.padding(bottom = 16.dp),
    )

    SettingsActionRow(theme, "Логи", "Открыть журнал sing-box", onOpenLogs, Modifier.testTag("logs_action_row"))
    ToggleRow(theme, accent, "Тёмная тема", "Спокойнее для глаз вечером", darkModeOn, onToggleDarkMode, Modifier.testTag("dark_mode_toggle"))
    ToggleRow(theme, accent, "Уведомления", "О статусе подключения", notifications, onToggleNotifications, Modifier.testTag("notifications_toggle"))
}

@Composable
private fun SettingsActionRow(
    theme: VpnColors,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        Haptics.click(context)
                        onClick()
                    },
                )
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, color = theme.ink)
                Text(subtitle, fontSize = 12.sp, color = theme.mutedInk)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.mutedInk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    theme: VpnColors,
    accent: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toggleWithHaptic: () -> Unit = {
        Haptics.click(context)
        onToggle()
    }
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = toggleWithHaptic,
                )
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, color = theme.ink)
                Text(subtitle, fontSize = 12.sp, color = theme.mutedInk)
            }
            MiniSwitch(checked = checked, accent = accent, theme = theme, onToggle = toggleWithHaptic)
        }
    }
}
