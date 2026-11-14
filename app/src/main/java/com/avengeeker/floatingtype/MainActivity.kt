package com.avengeeker.floatingtype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.avengeeker.floatingtype.core.ServiceLocator
import com.avengeeker.floatingtype.history.InputMode
import com.avengeeker.floatingtype.polish.PolishConfig
import com.avengeeker.floatingtype.settings.SettingsRepository
import com.avengeeker.floatingtype.ui.HistoryDetailScreen
import com.avengeeker.floatingtype.ui.HistoryScreen
import com.avengeeker.floatingtype.ui.SettingsScreen
import com.avengeeker.floatingtype.ui.theme.FloatingTypeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MainTab { HISTORY, SETTINGS }

/**
 * Two-tab home: History (default) and Settings. No blocking onboarding;
 * missing setup is surfaced as a reminder card on the History tab.
 */
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloatingTypeTheme {
                val settings = ServiceLocator.settings
                val history = ServiceLocator.history
                val permissions = ServiceLocator.permissions
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val clipboard = LocalClipboardManager.current

                val records by history.records.collectAsState(initial = emptyList())
                var selectedTab by rememberSaveable { mutableStateOf(MainTab.HISTORY) }
                var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
                var status by remember { mutableStateOf(permissions.status()) }
                var settingsRevision by remember { mutableStateOf(0) }
                val mode by settings.defaultModeFlow.collectAsState()

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            status = permissions.status()
                            settingsRevision += 1
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                DisposableEffect(settings) {
                    val listener = { settingsRevision += 1 }
                    settings.addChangeListener(listener)
                    onDispose { settings.removeChangeListener(listener) }
                }
                LaunchedEffect(status.allGranted) {
                    while (!status.allGranted) {
                        delay(1000)
                        status = permissions.status()
                    }
                }

                val keyConfigured = remember(settingsRevision) { settings.apiKey.isNotBlank() }
                val polishKeyAvailable = remember(settingsRevision, mode) {
                    PolishConfig.resolveKey(
                        asrKey = settings.apiKey,
                        polishKey = settings.polishApiKey,
                        baseUrl = settings.polishBaseUrl,
                        defaultBaseUrl = SettingsRepository.DEFAULT_POLISH_BASE_URL,
                    ) != null
                }
                val setupIncomplete = !status.allGranted || !keyConfigured ||
                    (mode == InputMode.POLISH && !polishKeyAvailable)
                val missingSummary = remember(status, keyConfigured, mode, polishKeyAvailable) {
                    val missing = mutableListOf<String>()
                    if (!status.overlay) missing += "悬浮窗"
                    if (!(status.accessibilityEnabled && status.accessibilityConnected)) missing += "无障碍"
                    if (!status.microphone) missing += "麦克风"
                    if (!status.notification) missing += "通知"
                    if (!status.battery) missing += "后台运行"
                    if (!keyConfigured) missing += "ASR Key"
                    if (mode == InputMode.POLISH && !polishKeyAvailable) missing += "润色 Key"
                    if (missing.isEmpty()) "" else "还差：" + missing.joinToString("、")
                }

                val detailRecord = detailId?.let { id -> records.find { it.id == id } }
                LaunchedEffect(detailId, records) {
                    if (detailId != null && records.none { it.id == detailId }) detailId = null
                }

                BackHandler(enabled = detailId != null) { detailId = null }
                BackHandler(enabled = detailId == null && selectedTab == MainTab.SETTINGS) {
                    selectedTab = MainTab.HISTORY
                }

                val showMessage: (String) -> Unit = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        when {
                            detailRecord != null -> TopAppBar(
                                title = { Text("记录详情") },
                                navigationIcon = {
                                    IconButton(onClick = { detailId = null }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                    }
                                },
                            )
                            selectedTab == MainTab.HISTORY -> TopAppBar(title = { Text("历史记录") })
                            else -> TopAppBar(title = { Text("功能设置") })
                        }
                    },
                    bottomBar = {
                        if (detailRecord == null) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == MainTab.HISTORY,
                                    onClick = { selectedTab = MainTab.HISTORY; detailId = null },
                                    icon = { Icon(Icons.Outlined.History, contentDescription = "历史记录") },
                                    label = { Text("历史记录") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == MainTab.SETTINGS,
                                    onClick = { selectedTab = MainTab.SETTINGS; detailId = null },
                                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "功能设置") },
                                    label = { Text("功能设置") },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        when {
                            detailRecord != null -> HistoryDetailScreen(
                                record = detailRecord,
                                onBack = { detailId = null },
                                onCopy = { text ->
                                    clipboard.setText(AnnotatedString(text))
                                    showMessage("已复制")
                                },
                                onDelete = {
                                    val id = detailRecord.id
                                    scope.launch {
                                        history.delete(id)
                                        detailId = null
                                        snackbarHostState.showSnackbar("已删除记录")
                                    }
                                },
                                onMessage = showMessage,
                            )
                            selectedTab == MainTab.HISTORY -> HistoryScreen(
                                records = records,
                                setupIncomplete = setupIncomplete,
                                missingSummary = missingSummary,
                                onOpenSettings = { selectedTab = MainTab.SETTINGS },
                                onOpenDetail = { detailId = it },
                            )
                            else -> SettingsScreen(
                                status = status,
                                onRefresh = {
                                    status = permissions.status()
                                    settingsRevision += 1
                                },
                                onMessage = showMessage,
                            )
                        }
                    }
                }
            }
        }
    }
}
