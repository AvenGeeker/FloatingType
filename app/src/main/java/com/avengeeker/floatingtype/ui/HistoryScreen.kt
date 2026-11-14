package com.avengeeker.floatingtype.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avengeeker.floatingtype.history.HistoryPresentation
import com.avengeeker.floatingtype.history.InputHistoryRecord
import com.avengeeker.floatingtype.history.InputMode
import com.avengeeker.floatingtype.ui.components.EmptyStateCard
import com.avengeeker.floatingtype.ui.components.SetupReminderCard
import com.avengeeker.floatingtype.ui.components.StatusChip
import com.avengeeker.floatingtype.ui.theme.UiTokens

@Composable
fun HistoryScreen(
    records: List<InputHistoryRecord>,
    setupIncomplete: Boolean,
    missingSummary: String,
    onOpenSettings: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val grouped = remember(records) {
        records.groupBy { HistoryPresentation.dayLabel(it.createdAt, now) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.CardSpacing),
    ) {
        if (setupIncomplete) {
            item(key = "setup_reminder") {
                SetupReminderCard(missingSummary = missingSummary, onOpenSettings = onOpenSettings)
            }
        }
        if (records.isEmpty()) {
            item(key = "empty") {
                EmptyStateCard(
                    icon = Icons.Outlined.History,
                    title = "还没有输入记录",
                    subtitle = "聚焦任意输入框后点击悬浮球开始语音输入，成功与失败的记录都会出现在这里。",
                    actionLabel = "去设置",
                    onAction = onOpenSettings,
                )
            }
        }
        grouped.forEach { (label, list) ->
            item(key = "header_$label") {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            items(list, key = { it.id }) { record ->
                HistoryRecordCard(record = record, onClick = { onOpenDetail(record.id) })
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(record: InputHistoryRecord, onClick: () -> Unit) {
    val modeIcon = if (record.mode == InputMode.POLISH) Icons.Outlined.AutoAwesome else Icons.Outlined.Mic
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(UiTokens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(UiTokens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(UiTokens.IconBadge),
            ) {
                Icon(
                    imageVector = modeIcon,
                    contentDescription = record.mode.label,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = HistoryPresentation.preview(record),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${HistoryPresentation.formatTime(record.createdAt)} · " +
                        "${record.sourceLabel} · " +
                        HistoryPresentation.formatDuration(record.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(
                label = HistoryPresentation.statusLabel(record),
                tone = HistoryPresentation.statusTone(record),
            )
        }
    }
}
