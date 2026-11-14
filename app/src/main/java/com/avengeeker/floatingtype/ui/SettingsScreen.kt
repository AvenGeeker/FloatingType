package com.avengeeker.floatingtype.ui

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.avengeeker.floatingtype.core.ServiceLocator
import com.avengeeker.floatingtype.history.InputMode
import com.avengeeker.floatingtype.history.StatusTone
import com.avengeeker.floatingtype.overlay.BallView
import com.avengeeker.floatingtype.permission.PermissionStatus
import com.avengeeker.floatingtype.settings.SettingsRepository
import com.avengeeker.floatingtype.ui.components.SectionCard
import com.avengeeker.floatingtype.ui.components.StatusChip
import com.avengeeker.floatingtype.ui.theme.UiTokens
import kotlinx.coroutines.launch

/**
 * Settings tab: permissions/onboarding, default mode, ASR, polish, ball,
 * data & test. All former startup onboarding items live here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    status: PermissionStatus,
    onRefresh: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val settings = ServiceLocator.settings
    val history = ServiceLocator.history
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mode by settings.defaultModeFlow.collectAsState()
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(settings.model) }
    var endpoint by remember { mutableStateOf(settings.endpoint) }
    var polishBaseUrl by remember { mutableStateOf(settings.polishBaseUrl) }
    var polishApiKey by remember { mutableStateOf(settings.polishApiKey) }
    var polishKeyVisible by remember { mutableStateOf(false) }
    var polishModel by remember { mutableStateOf(settings.polishModel) }
    var polishPrompt by remember { mutableStateOf(settings.polishPrompt) }
    var ballEnabled by remember { mutableStateOf(settings.ballEnabled) }
    var ballSize by remember { mutableStateOf(settings.ballSizeDp.toFloat()) }
    var opacity by remember { mutableStateOf(settings.ballOpacity) }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(UiTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.CardSpacing),
    ) {
        SectionCard(
            title = "权限与引导",
            icon = Icons.Outlined.Shield,
            subtitle = "开启必要权限后，悬浮球才能跟随输入框出现",
        ) {
            PermissionChecklist(status = status, onRefresh = onRefresh)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("API Key 配置", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StatusChip(
                    label = if (apiKey.isBlank()) "未配置" else "已配置",
                    tone = if (apiKey.isBlank()) StatusTone.ERROR else StatusTone.SUCCESS,
                )
            }
        }

        SectionCard(
            title = "默认输入模式",
            icon = Icons.Outlined.Tune,
            subtitle = "长按悬浮球也可切换并保存",
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                InputMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { settings.defaultMode = m },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (m == InputMode.TRANSCRIBE) "直接转写" else "AI 润色")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (mode == InputMode.TRANSCRIBE) {
                    "直接转写：语音识别后原文写入输入框。"
                } else {
                    "AI 润色：语音识别后先润色，再写入输入框；润色失败自动回退原文。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "DashScope ASR",
            icon = Icons.Outlined.Mic,
            subtitle = "语音转写服务",
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; settings.apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = settings.apiKeyFromLocal,
                label = { Text("DashScope API Key") },
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { keyVisible = !keyVisible }) {
                        Text(if (keyVisible) "隐藏" else "显示")
                    }
                },
            )
            if (settings.apiKeyFromLocal) {
                Spacer(Modifier.height(4.dp))
                LocalPropertyHint("asr.apiKey")
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (apiKey.isBlank()) "状态：未配置" else "状态：已配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SettingsRepository.API_KEY_CONSOLE_URL)))
                    } catch (_: Exception) {
                        onMessage("无法打开浏览器，请手动访问百炼控制台")
                    }
                }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("获取 API Key")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; settings.model = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = settings.modelFromLocal,
                label = { Text("转写模型") },
            )
            if (settings.modelFromLocal) {
                Spacer(Modifier.height(4.dp))
                LocalPropertyHint("asr.model")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; settings.endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = settings.endpointFromLocal,
                label = { Text("服务端点（高级）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            if (settings.endpointFromLocal) {
                Spacer(Modifier.height(4.dp))
                LocalPropertyHint("asr.baseUrl")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "一般无需修改；如使用业务空间专属域名，可改为 https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1。注意：转写走原生 API（/api/v1），误填润色用的 compatible-mode 地址会自动转换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "AI 润色",
            icon = Icons.Outlined.AutoAwesome,
            subtitle = "OpenAI 兼容 Chat Completions",
        ) {
            OutlinedTextField(
                value = polishBaseUrl,
                onValueChange = { polishBaseUrl = it; settings.polishBaseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = settings.polishBaseUrlFromLocal,
                label = { Text("Base URL") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            if (settings.polishBaseUrlFromLocal) {
                Spacer(Modifier.height(4.dp))
                LocalPropertyHint("polish.baseUrl")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = polishApiKey,
                onValueChange = { polishApiKey = it; settings.polishApiKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = settings.polishApiKeyFromLocal,
                label = { Text("API Key（默认地址可留空复用 ASR Key）") },
                visualTransformation = if (polishKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { polishKeyVisible = !polishKeyVisible }) {
                        Text(if (polishKeyVisible) "隐藏" else "显示")
                    }
                },
            )
            if (settings.polishApiKeyFromLocal) {
                Spacer(Modifier.height(4.dp))
                LocalPropertyHint("polish.apiKey")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = polishModel,
                onValueChange = { polishModel = it; settings.polishModel = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = settings.polishModelFromLocal,
                label = { Text("润色模型") },
            )
            if (settings.polishModelFromLocal) {
                Spacer(Modifier.height(4.dp))
                LocalPropertyHint("polish.model")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = polishPrompt,
                onValueChange = { polishPrompt = it; settings.polishPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("润色提示词") },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    polishPrompt = SettingsRepository.DEFAULT_POLISH_PROMPT
                    settings.polishPrompt = SettingsRepository.DEFAULT_POLISH_PROMPT
                }) { Text("恢复默认提示词") }
            }
            Text(
                "自定义 Base URL 时不会复用 DashScope Key，请填写独立 API Key；缺少 Key 时润色失败并回退原文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "悬浮球",
            icon = Icons.Outlined.Adjust,
            subtitle = "外观与行为",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启用悬浮球", style = MaterialTheme.typography.titleSmall)
                    Text("关闭后悬浮球不再出现", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ballEnabled, onCheckedChange = { ballEnabled = it; settings.ballEnabled = it })
            }
            Spacer(Modifier.height(8.dp))
            Text("大小：${ballSize.toInt()} dp", style = MaterialTheme.typography.titleSmall)
            Slider(value = ballSize, onValueChange = { ballSize = it; settings.ballSizeDp = it.toInt() }, valueRange = 40f..72f)
            Text("不透明度：${(opacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(value = opacity, onValueChange = { opacity = it; settings.ballOpacity = it }, valueRange = 0.3f..1f)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { BallView(it) },
                    update = { it.mode = mode; it.ballOpacity = opacity },
                    modifier = Modifier.size(ballSize.dp),
                )
            }
        }

        SectionCard(
            title = "数据与测试",
            icon = Icons.Outlined.Storage,
            subtitle = "历史记录与注入测试",
        ) {
            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清空全部历史")
            }
            Spacer(Modifier.height(12.dp))
            Text("测试输入框", style = MaterialTheme.typography.titleSmall)
            Text(
                "点击输入框后使用悬浮球，验证文字写入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AndroidView(
                factory = { ctx ->
                    EditText(ctx).apply {
                        hint = "点击此处，再点击悬浮球开始说话"
                        minLines = 3
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部历史") },
            text = { Text("确定删除所有历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        history.clear()
                        onMessage("已清空全部历史")
                    }
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

/** Shown under a field whose value is preconfigured via local.properties. */
@Composable
private fun LocalPropertyHint(propertyKey: String) {
    Text(
        text = "已在 local.properties 配置 $propertyKey，如需修改请编辑该文件后重新构建。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
