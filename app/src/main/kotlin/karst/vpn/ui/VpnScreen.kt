package karst.vpn.ui

import karst.vpn.R
import karst.vpn.core.ConnectionPhase
import karst.vpn.core.Haptics
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import karst.vpn.data.RoutingMode
import karst.vpn.data.SubscriptionAutoRefreshMode
import kotlinx.coroutines.delay

private enum class Route {
    Main,
    Logs,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
            error = theme.danger,
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val forward = targetState == Route.Logs
                    (slideInHorizontally(tween(220)) { width -> if (forward) width / 3 else -width / 3 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { width -> if (forward) -width / 3 else width / 3 } + fadeOut(tween(160)))
                },
                label = "routeTransition",
            ) { currentRoute ->
                if (currentRoute == Route.Logs) {
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
    var subscriptionMenuId by remember { mutableStateOf<String?>(null) }
    val subscriptionMenu = state.subscriptionGroups.firstOrNull { it.id == subscriptionMenuId }

    LaunchedEffect(state.importMessage) {
        if (state.importMessage != null) {
            addServerOpen = false
            addServerValue = ""
        }
    }
    LaunchedEffect(subscriptionMenuId, subscriptionMenu) {
        if (subscriptionMenuId != null && subscriptionMenu == null) {
            subscriptionMenuId = null
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
                subscriptionMenuId = null
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
                    refreshAllLoading = state.refreshAllLoading,
                    subscriptionMenu = subscriptionMenu,
                    onSelect = { id ->
                        viewModel.selectServer(id)
                        menuVisible = false
                    },
                    onRemove = viewModel::deleteServer,
                    onDeleteSubscription = { id ->
                        viewModel.deleteSubscription(id)
                        subscriptionMenuId = null
                        menuVisible = false
                    },
                    onOpenSubscription = { id -> subscriptionMenuId = id },
                    onCloseSubscription = { subscriptionMenuId = null },
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
        val settingsNestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
                override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
            }
        }
        ModalBottomSheet(
            onDismissRequest = { settingsVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = sheetShape,
            containerColor = theme.appBg,
            dragHandle = dragHandle,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .nestedScroll(settingsNestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                SettingsSheetContent(
                    theme = theme,
                    accent = accent,
                    darkModeOn = state.darkModeOn,
                    routingMode = state.routingMode,
                    subscriptionAutoRefreshMode = state.subscriptionAutoRefreshMode,
                    subscriptionAutoRefreshHours = state.subscriptionAutoRefreshHours,
                    onToggleDarkMode = { viewModel.setDarkMode(!state.darkModeOn) },
                    onSetRoutingMode = viewModel::setRoutingMode,
                    onSetSubscriptionAutoRefreshMode = viewModel::setSubscriptionAutoRefreshMode,
                    onSetSubscriptionAutoRefreshHours = viewModel::setSubscriptionAutoRefreshHours,
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

    val infiniteTransition = rememberInfiniteTransition(label = "connectingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val ringAlpha = when {
        isConnected -> 0.16f
        isConnecting -> pulseAlpha
        else -> 0f
    }

    Box(modifier = Modifier.size(208.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = ringAlpha)),
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
    refreshAllLoading: Boolean,
    subscriptionMenu: UiSubscription?,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onOpenSubscription: (String) -> Unit,
    onCloseSubscription: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenAddServer: () -> Unit,
    onCancelAddServer: () -> Unit,
    onChangeAddServerValue: (String) -> Unit,
    onSubmitAddServer: () -> Unit,
) {
    // subscriptionMenu goes null the instant the menu is closed (or its subscription is
    // deleted), in the same recomposition that starts the exit animation. AnimatedContent
    // still renders the outgoing "menu" slot for the duration of that animation, so reading
    // subscriptionMenu live inside it would flip the outgoing content to the server list
    // mid-exit. Latch the last non-null value so the exit animation shows consistent content.
    var latchedSubscriptionMenu by remember { mutableStateOf(subscriptionMenu) }
    if (subscriptionMenu != null) {
        latchedSubscriptionMenu = subscriptionMenu
    }
    val listNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    AnimatedContent(
        targetState = subscriptionMenu != null,
        transitionSpec = {
            val forward = targetState
            // The list pane (fixed-height LazyColumn) and the menu pane (content-height
            // scroll column) differ a lot in height, and this sits inside a ModalBottomSheet
            // that recalculates its drag anchors on every content size change. The default
            // sizeTransform is a spring, which keeps resizing (and re-triggering anchor
            // recalculation) well past the 200ms slide/fade, tanking frame rate. A tween
            // matched to the other transitions settles in lockstep instead.
            (slideInHorizontally(tween(200)) { width -> if (forward) width / 4 else -width / 4 } + fadeIn(tween(200)))
                .togetherWith(slideOutHorizontally(tween(200)) { width -> if (forward) -width / 4 else width / 4 } + fadeOut(tween(150)))
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(200) }))
        },
        label = "subscriptionDrillIn",
    ) { showMenu ->
        val menu = latchedSubscriptionMenu
        if (showMenu && menu != null) {
            SubscriptionMenuContent(
                subscription = menu,
                theme = theme,
                accent = accent,
                onBack = onCloseSubscription,
                onDelete = { menu.id?.let(onDeleteSubscription) },
                modifier = Modifier.nestedScroll(listNestedScrollConnection),
            )
        } else {
            Column {
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
                    // Material3 ModalBottomSheet forwards any scroll/fling delta the LazyColumn
                    // doesn't consume (e.g. at the list edges) to its own drag handling, which
                    // makes the sheet jerk mid-drag or on fling release. Swallowing both here
                    // keeps the sheet's own drag-to-dismiss limited to its handle. This also
                    // suppresses the list's own overscroll bounce at the edges — a nicer bounce
                    // would need a purpose-built local effect instead of relying on nested-scroll
                    // dispatch order, which isn't reliable to tune without on-device testing.
                    val listState = rememberLazyListState()

                    RefreshAllButton(
                        loading = refreshAllLoading,
                        enabled = !refreshAllLoading,
                        accent = accent,
                        onClick = onRefreshAll,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
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
                                        .animateContentSize(animationSpec = tween(durationMillis = 220))
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
                                        onOpenMenu = group.id?.let { id -> { onOpenSubscription(id) } },
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

                AnimatedContent(
                    targetState = addServerOpen,
                    transitionSpec = {
                        (fadeIn(tween(180)) + expandVertically(tween(200)))
                            .togetherWith(fadeOut(tween(120)) + shrinkVertically(tween(200)))
                    },
                    label = "addServerReveal",
                ) { isOpen ->
                    if (isOpen) {
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
    onOpenMenu: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val content: @Composable () -> Unit = {
        SubscriptionGroupHeaderRow(
            name = name,
            announce = announce,
            theme = theme,
            accent = accent,
            onRefresh = onRefresh,
            showMenuArrow = onOpenMenu != null,
        )
    }
    if (onOpenMenu != null) {
        Pressable(
            onClick = {
                Haptics.click(context)
                onOpenMenu()
            },
            pressedScale = 0.99f,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    } else {
        content()
    }
}

@Composable
private fun SubscriptionGroupHeaderRow(
    name: String,
    announce: String?,
    theme: VpnColors,
    accent: Color,
    onRefresh: (() -> Unit)?,
    showMenuArrow: Boolean,
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
        if (showMenuArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Меню подписки",
                tint = theme.mutedInk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SubscriptionMenuContent(
    subscription: UiSubscription,
    theme: VpnColors,
    accent: Color,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember(subscription.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Pressable(onClick = onBack, pressedScale = 0.88f) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = theme.mutedInk,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.name,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = theme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${subscription.servers.size} серверов",
                    fontSize = 12.sp,
                    color = theme.mutedInk,
                )
            }
        }

        subscription.announce?.takeIf { it.isNotBlank() }?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.cardBg)
                    .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = theme.mutedInk, modifier = Modifier.size(16.dp))
                Text(text = it, fontSize = 12.sp, lineHeight = 16.sp, color = theme.mutedInk)
            }
        }

        SubscriptionTrafficCard(subscription = subscription, theme = theme, accent = accent)

        SubscriptionDetailsCard(subscription = subscription, theme = theme)

        subscription.lastRefreshError?.takeIf { it.isNotBlank() }?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.danger.copy(alpha = 0.1f))
                    .border(1.dp, theme.danger.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = theme.danger, modifier = Modifier.size(16.dp))
                Text(text = error, fontSize = 12.sp, lineHeight = 16.sp, color = theme.danger)
            }
        }

        if (confirmDelete) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.cardBg)
                    .border(1.dp, theme.danger.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Удалить подписку и все её серверы?", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = theme.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Pressable(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) {
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
                    Pressable(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.danger)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Удалить", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        } else {
            Pressable(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, theme.danger.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = theme.danger, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Удалить подписку", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = theme.danger)
                        Text("Группа и её серверы будут удалены", fontSize = 12.sp, color = theme.mutedInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionTrafficCard(subscription: UiSubscription, theme: VpnColors, accent: Color) {
    val used = listOfNotNull(subscription.uploadBytes, subscription.downloadBytes).takeIf { it.isNotEmpty() }?.sum()
    val total = subscription.totalBytes
    val fraction = if (used != null && total != null && total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    val daysLeft = daysUntilEpochSeconds(subscription.expireAtEpochSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.cardBg)
            .border(1.dp, theme.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.DataUsage, contentDescription = null, tint = theme.mutedInk, modifier = Modifier.size(16.dp))
            Text("Трафик", fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = theme.ink, modifier = Modifier.weight(1f))
            Text(
                formatTraffic(subscription.uploadBytes, subscription.downloadBytes, subscription.totalBytes),
                fontSize = 12.sp,
                color = theme.mutedInk,
            )
        }
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (fraction >= 0.9f) theme.danger else accent,
                trackColor = theme.border,
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.border))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Event, contentDescription = null, tint = theme.mutedInk, modifier = Modifier.size(16.dp))
            Text("Истекает", fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = theme.ink, modifier = Modifier.weight(1f))
            Text(formatEpochSeconds(subscription.expireAtEpochSeconds), fontSize = 12.sp, color = theme.mutedInk)
            if (daysLeft != null) {
                val pillColor = if (daysLeft <= 3) theme.danger else accent
                Text(
                    text = daysLeftLabel(daysLeft),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = pillColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillColor.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun SubscriptionDetailsCard(subscription: UiSubscription, theme: VpnColors) {
    val profilePage = subscription.profileWebPageUrl?.takeIf { it.isNotBlank() && it != subscription.url }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.cardBg)
            .border(1.dp, theme.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        IconDetailRow(Icons.Filled.Link, "Ссылка", subscription.url ?: "Не указана", theme, monospace = true)
        profilePage?.let {
            IconDetailRow(Icons.Filled.Language, "Страница профиля", it, theme, monospace = true)
        }
        IconDetailRow(Icons.Filled.Schedule, "Обновление профиля", formatUpdateInterval(subscription.profileUpdateIntervalHours), theme)
    }
}

@Composable
private fun IconDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    theme: VpnColors,
    monospace: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = theme.mutedInk, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 11.sp, color = theme.mutedInk)
            Text(
                text = value,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = theme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RefreshAllButton(
    loading: Boolean,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Pressable(
        onClick = {
            Haptics.medium(context)
            onClick()
        },
        pressedScale = 0.85f,
        enabled = enabled,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(accent.copy(alpha = if (enabled) 0.1f else 0.06f), RoundedCornerShape(10.dp))
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
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(tween(180), initialScale = 0.6f) + fadeIn(tween(180)),
                exit = scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(100)),
            ) {
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
            Text(error, fontSize = 12.sp, color = theme.danger)
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
    routingMode: RoutingMode,
    subscriptionAutoRefreshMode: SubscriptionAutoRefreshMode,
    subscriptionAutoRefreshHours: Int,
    onToggleDarkMode: () -> Unit,
    onSetRoutingMode: (RoutingMode) -> Unit,
    onSetSubscriptionAutoRefreshMode: (SubscriptionAutoRefreshMode) -> Unit,
    onSetSubscriptionAutoRefreshHours: (Int) -> Unit,
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
    RoutingModeSection(theme, accent, routingMode, onSetRoutingMode)
    AutoRefreshSection(
        theme = theme,
        accent = accent,
        mode = subscriptionAutoRefreshMode,
        hours = subscriptionAutoRefreshHours,
        onSetMode = onSetSubscriptionAutoRefreshMode,
        onSetHours = onSetSubscriptionAutoRefreshHours,
    )
}

private fun routingModeLabel(mode: RoutingMode): String = when (mode) {
    RoutingMode.Full -> "Полный VPN"
    RoutingMode.BypassLocal -> "Обход локалки"
    RoutingMode.BypassRu -> "Обход RU и локалки"
}

private fun autoRefreshModeLabel(mode: SubscriptionAutoRefreshMode, hours: Int): String = when (mode) {
    SubscriptionAutoRefreshMode.Auto -> "Авто"
    SubscriptionAutoRefreshMode.Off -> "Выкл"
    SubscriptionAutoRefreshMode.EveryHours -> "Каждые $hours ч"
}

@Composable
private fun RoutingModeSection(
    theme: VpnColors,
    accent: Color,
    selectedMode: RoutingMode,
    onSelect: (RoutingMode) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    SettingsActionRow(theme, "Маршрутизация", routingModeLabel(selectedMode), onClick = { dialogOpen = true })

    if (dialogOpen) {
        SettingsPickerDialog(theme = theme, title = "Маршрутизация", onDismiss = { dialogOpen = false }) {
            SettingsChoiceRow(theme, accent, "Полный VPN", "Весь трафик через выбранный сервер", selectedMode == RoutingMode.Full) {
                onSelect(RoutingMode.Full)
                dialogOpen = false
            }
            SettingsChoiceRow(theme, accent, "Обход локалки", "Локальные сети и private IP идут напрямую", selectedMode == RoutingMode.BypassLocal) {
                onSelect(RoutingMode.BypassLocal)
                dialogOpen = false
            }
            SettingsChoiceRow(theme, accent, "Обход RU и локалки", "RU-домены и локальные сети идут напрямую", selectedMode == RoutingMode.BypassRu) {
                onSelect(RoutingMode.BypassRu)
                dialogOpen = false
            }
        }
    }
}

@Composable
private fun AutoRefreshSection(
    theme: VpnColors,
    accent: Color,
    mode: SubscriptionAutoRefreshMode,
    hours: Int,
    onSetMode: (SubscriptionAutoRefreshMode) -> Unit,
    onSetHours: (Int) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var hoursText by remember(hours) { mutableStateOf(hours.toString()) }

    SettingsActionRow(theme, "Обновление подписок", autoRefreshModeLabel(mode, hours), onClick = { dialogOpen = true })

    if (dialogOpen) {
        SettingsPickerDialog(theme = theme, title = "Обновление подписок", onDismiss = { dialogOpen = false }) {
            SettingsChoiceRow(theme, accent, "Авто", "По Profile-Update-Interval, иначе раз в 24 часа", mode == SubscriptionAutoRefreshMode.Auto) {
                onSetMode(SubscriptionAutoRefreshMode.Auto)
                dialogOpen = false
            }
            SettingsChoiceRow(theme, accent, "Выкл", "Обновлять только вручную", mode == SubscriptionAutoRefreshMode.Off) {
                onSetMode(SubscriptionAutoRefreshMode.Off)
                dialogOpen = false
            }
            SettingsChoiceRow(theme, accent, "Каждые N часов", "Фиксированный интервал для всех подписок", mode == SubscriptionAutoRefreshMode.EveryHours) {
                onSetMode(SubscriptionAutoRefreshMode.EveryHours)
            }

            AnimatedVisibility(
                visible = mode == SubscriptionAutoRefreshMode.EveryHours,
                enter = expandVertically(tween(160)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(120)) + fadeOut(tween(120)),
            ) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { raw ->
                        val digits = raw.filter(Char::isDigit).take(3)
                        hoursText = digits
                        digits.toIntOrNull()?.takeIf { it > 0 }?.let(onSetHours)
                    },
                    label = { Text("Часы") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, color = theme.ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.pageBg,
                        unfocusedContainerColor = theme.pageBg,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = theme.border,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("auto_refresh_hours_input"),
                )
            }
        }
    }
}

@Composable
private fun SettingsPickerDialog(
    theme: VpnColors,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(theme.appBg)
                .border(1.dp, theme.border, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = theme.ink,
                modifier = Modifier.padding(top = 18.dp, bottom = 13.dp),
            )
            content()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(theme: VpnColors, title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = theme.mutedInk,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsChoiceRow(
    theme: VpnColors,
    accent: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, color = theme.ink)
                Text(subtitle, fontSize = 12.sp, color = theme.mutedInk)
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (selected) accent else theme.border, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
        }
    }
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

private fun daysUntilEpochSeconds(expireAtEpochSeconds: Long?): Long? =
    expireAtEpochSeconds?.let { (it * 1000 - System.currentTimeMillis()) / 86_400_000L }

private fun daysLeftLabel(days: Long): String {
    if (days < 0) return "Истекло"
    if (days == 0L) return "Сегодня"
    val lastTwoDigits = days % 100
    val word = when {
        lastTwoDigits in 11..14 -> "дней"
        days % 10 == 1L -> "день"
        days % 10 in 2..4 -> "дня"
        else -> "дней"
    }
    return "$days $word"
}

private fun formatUpdateInterval(hours: Int?): String =
    hours?.takeIf { it > 0 }?.let { "$it ч" } ?: "Не указан"

private fun formatEpochSeconds(value: Long?): String =
    value?.let { dateFormatter().format(Date(it * 1000)) } ?: "Не указано"

private fun formatTraffic(uploadBytes: Long?, downloadBytes: Long?, totalBytes: Long?): String {
    val used = listOfNotNull(uploadBytes, downloadBytes).takeIf { it.isNotEmpty() }?.sum()
    return when {
        used != null && totalBytes != null -> "${formatBytes(used)} / ${formatBytes(totalBytes)}"
        used != null -> "Использовано ${formatBytes(used)}"
        totalBytes != null -> "Лимит ${formatBytes(totalBytes)}"
        else -> "Не указано"
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value Б"
    val units = listOf("КБ", "МБ", "ГБ", "ТБ")
    var amount = value / 1024.0
    var unitIndex = 0
    while (amount >= 1024 && unitIndex < units.lastIndex) {
        amount /= 1024
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", amount, units[unitIndex])
}

private fun dateFormatter(): SimpleDateFormat =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
