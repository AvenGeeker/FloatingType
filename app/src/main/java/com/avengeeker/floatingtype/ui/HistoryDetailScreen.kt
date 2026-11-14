package com.avengeeker.floatingtype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avengeeker.floatingtype.core.ServiceLocator
import com.avengeeker.floatingtype.history.HistoryPresentation
import com.avengeeker.floatingtype.history.InputHistoryRecord
import com.avengeeker.floatingtype.history.InputMode
import com.avengeeker.floatingtype.history.StageStatus
import com.avengeeker.floatingtype.history.StatusTone
import com.avengeeker.floatingtype.polish.OpenAiCompatiblePolishEngine
import com.avengeeker.floatingtype.polish.PolishException
import com.avengeeker.floatingtype.ui.components.MetaRow
import com.avengeeker.floatingtype.ui.components.SectionCard
import com.avengeeker.floatingtype.ui.components.StatusChip
import com.avengeeker.floatingtype.ui.theme.UiTokens
import kotlinx.coroutines.launch

@Composable
fun HistoryDetailScreen(
    record: InputHistoryRecord,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings = ServiceLocator.settings
    val history = ServiceLocator.history
    val polishEngine = remember { OpenAiCompatiblePolishEngine(settings) }
    var repolishing by remember { mutableStateOf(false) }
    var retryError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(UiTokens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.CardSpacing),
        ) {
            item {
                SectionCard(title = "输入结果", icon = Icons.Outlined.Info) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(record.mode.label, StatusTone.INFO)
                        StatusChip(
                            label = HistoryPresentation.statusLabel(record),
                            tone = HistoryPresentation.statusTone(record),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = record.finalText.ifBlank { record.rawText.ifBlank { "无文本内容" } },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                SectionCard(title = "会话信息", icon = Icons.Outlined.Schedule) {
                    MetaRow(Icons.Outlined.Schedule, "时间", HistoryPresentation.formatDateTime(record.createdAt))
                    MetaRow(Icons.Outlined.Smartphone, "来源应用", record.sourceLabel)
                    MetaRow(Icons.Outlined.Mic, "录音时长", HistoryPresentation.formatDuration(record.durationMs))
                    MetaRow(Icons.Outlined.Info, "ASR 模型", record.asrModel)
                    MetaRow(Icons.Outlined.AutoAwesome, "润色模型", record.polishModel ?: "—")
                }
            }
            if (record.rawText.isNotBlank()) {
                item {
                    SectionCard(title = "原始转写", icon = Icons.Outlined.Mic) {
                        Text(text = record.rawText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (record.mode == InputMode.POLISH) {
                item {
                    SectionCard(
                        title = "润色结果",
                        icon = Icons.Outlined.AutoAwesome,
                        subtitle = if (record.polishStatus == StageStatus.FAILED) "润色失败，已回退原文" else null,
                    ) {
                        Text(
                            text = record.finalText.ifBlank { "无润色结果" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            errorCard("录音失败", record.recordingError)
            errorCard("转写失败", record.asrError)
            errorCard("润色失败", record.polishError)
            errorCard("注入失败", record.injectionError)
            if (retryError != null) {
                item { ErrorCard(title = "重新润色失败", message = retryError!!) }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCopy(record.finalText) },
                        enabled = record.finalText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制最终文本")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除记录",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (record.rawText.isNotBlank() &&
                    (record.mode == InputMode.POLISH || record.rawText != record.finalText)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onCopy(record.rawText) },
                            modifier = Modifier.weight(1f),
                        ) { Text("复制原文") }
                        if (record.mode == InputMode.POLISH) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        repolishing = true
                                        retryError = null
                                        val result = polishEngine.polish(record.rawText)
                                        result.fold(
                                            onSuccess = { text ->
                                                history.update(
                                                    record.copy(
                                                        finalText = text,
                                                        polishStatus = StageStatus.SUCCESS,
                                                        polishError = null,
                                                        polishModel = settings.polishModel,
                                                    )
                                                )
                                                onMessage("已重新润色")
                                            },
                                            onFailure = { e ->
                                                retryError = if (e is PolishException) {
                                                    "${e.code}: ${e.message}"
                                                } else {
                                                    e.message ?: "润色失败"
                                                }
                                                onMessage("重新润色失败")
                                            },
                                        )
                                        repolishing = false
                                    }
                                },
                                enabled = !repolishing,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (repolishing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("重新润色")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除记录") },
            text = { Text("确定删除这条历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.errorCard(title: String, message: String?) {
    if (!message.isNullOrBlank()) {
        item { ErrorCard(title = title, message = message) }
    }
}

@Composable
private fun ErrorCard(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.CardCorner),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(Modifier.padding(UiTokens.CardPadding)) {
            Icon(Icons.Outlined.Error, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
