package com.avengeeker.floatingtype.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avengeeker.floatingtype.core.ServiceLocator
import com.avengeeker.floatingtype.permission.PermissionStatus

/**
 * Permission checklist formerly shown as a blocking onboarding screen.
 * Now embedded in the Settings tab; the app never gates the main UI.
 */
@Composable
fun PermissionChecklist(status: PermissionStatus, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val repo = ServiceLocator.permissions

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    fun safeStart(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(repo.appDetailsSettingsIntent())
            } catch (_: Exception) {
                // Nothing else we can do; the user can open settings manually.
            }
        }
    }

    Column {
        PermissionRow(
            title = "悬浮窗权限",
            subtitle = "允许在其他应用上层显示悬浮球",
            granted = status.overlay,
            actionLabel = "去开启",
        ) { safeStart(repo.overlaySettingsIntent()) }
        HorizontalDivider()
        PermissionRow(
            title = "无障碍服务",
            subtitle = when {
                status.accessibilityEnabled && status.accessibilityConnected -> "已开启并连接"
                status.accessibilityEnabled -> "已开启，但服务未连接"
                else -> "检测输入框焦点并写入文字"
            },
            granted = status.accessibilityEnabled && status.accessibilityConnected,
            actionLabel = "去开启",
        ) { safeStart(repo.accessibilitySettingsIntent()) }
        HorizontalDivider()
        PermissionRow(
            title = "麦克风权限",
            subtitle = "录制语音用于 AI 转写",
            granted = status.microphone,
            actionLabel = "授权",
        ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        HorizontalDivider()
        PermissionRow(
            title = "通知权限",
            subtitle = "录音时显示前台服务通知",
            granted = status.notification,
            actionLabel = "授权",
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                safeStart(repo.notificationSettingsIntent())
            }
        }
        HorizontalDivider()
        PermissionRow(
            title = "后台运行",
            subtitle = "电池优化白名单，避免服务被清理",
            granted = status.battery,
            actionLabel = "去设置",
        ) { safeStart(repo.batteryOptimizationIntent()) }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (granted) "已开启" else "未开启",
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
