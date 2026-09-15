package com.KiYY.lost.ksu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================================================
// 越狱进度弹窗
//   视觉参考 KernelSU：
//     - LoadingIndicator（material3 新版圆形加载指示器，来自 DialogMaterial.kt）
//     - ExpandMore 旋转展开（来自 InstallMaterial.kt 的 advancedOptions）
//   功能：运行中显示“正在越狱”+ 旋转指示器；可展开查看实时控制台日志
// ==========================================================================

enum class JailbreakPhase { Running, Success, Failed }

@Composable
fun JailbreakProgressDialog(
    phase: JailbreakPhase,
    logLines: List<Pair<String, Int>>,
    dismissible: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyLogs: () -> Unit,
    onDismiss: () -> Unit,
    glass: Boolean = false,
) {
    if (glass) {
        GlassJailbreakProgressDialog(
            phase = phase,
            logLines = logLines,
            dismissible = dismissible,
            expanded = expanded,
            onToggleExpand = onToggleExpand,
            onCopyLogs = onCopyLogs,
            onDismiss = onDismiss,
        )
        return
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "RotationAnimation"
    )

    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        title = { Text(dialogTitle(phase)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---- 顶部：指示器 + 状态文案 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (phase) {
                        JailbreakPhase.Running -> {
                            LoadingIndicator(modifier = Modifier.size(28.dp))
                        }

                        JailbreakPhase.Success -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        JailbreakPhase.Failed -> {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = statusText(phase),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // ---- 运行中：前台保活提示（越狱依赖进程存活，切后台可能被冻结/回收）----
                if (phase == JailbreakPhase.Running) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠ 请保持前台，以保证越狱成功",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // ---- 控制台：可展开（整行可点击）----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "收起控制台" else "展开控制台",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    // 复制日志按钮（有日志时才显示）
                    if (logLines.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制日志",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onCopyLogs() }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (logLines.isEmpty()) {
                            Text(
                                text = "（暂无日志）",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            logLines.forEach { (line, color) ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (color == -1) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        Color(color)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (phase != JailbreakPhase.Running) {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
    )
}

private fun dialogTitle(phase: JailbreakPhase): String = when (phase) {
    JailbreakPhase.Running -> "正在越狱"
    JailbreakPhase.Success -> "越狱完成"
    JailbreakPhase.Failed -> "越狱失败"
}

private fun statusText(phase: JailbreakPhase): String = when (phase) {
    JailbreakPhase.Running -> "正在执行漏洞利用，请勿退出…"
    JailbreakPhase.Success -> "提权成功，已获得 root 权限"
    JailbreakPhase.Failed -> "漏洞利用未成功，请查看控制台日志"
}

// ==========================================================================
// 液态玻璃版越狱进度弹窗（与首页 Glass 视觉统一）
// ==========================================================================

@Composable
private fun GlassJailbreakProgressDialog(
    phase: JailbreakPhase,
    logLines: List<Pair<String, Int>>,
    dismissible: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyLogs: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "RotationAnimation"
    )

    com.KiYY.lost.ksu.component.GlassDialog(
        onDismiss = { if (dismissible) onDismiss() },
        dismissOnClickOutside = dismissible,
        dismissOnBackPress = dismissible,
    ) {
        com.KiYY.lost.ksu.component.GlassDialogTitle(dialogTitle(phase))

        // ---- 顶部：指示器 + 状态文案 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (phase) {
                JailbreakPhase.Running -> {
                    LoadingIndicator(modifier = Modifier.size(28.dp))
                }

                JailbreakPhase.Success -> {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(28.dp)
                    )
                }

                JailbreakPhase.Failed -> {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = statusText(phase),
                style = MaterialTheme.typography.bodyLarge,
                color = com.KiYY.lost.ksu.component.GlassTextPrimary,
            )
        }

        // ---- 运行中：前台保活提示 ----
        if (phase == JailbreakPhase.Running) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF3E0))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "⚠ 请保持前台，以保证越狱成功",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB26A00),
                )
            }
        }

        // ---- 控制台：可展开 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "收起控制台" else "展开控制台",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF0A84FF),
                modifier = Modifier.weight(1f)
            )
            if (logLines.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "复制日志",
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onCopyLogs() }
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = Color(0xFF0A84FF),
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1C1E).copy(alpha = 0.92f))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (logLines.isEmpty()) {
                    Text(
                        text = "（暂无日志）",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF9AA1B0)
                    )
                } else {
                    logLines.forEach { (line, color) ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (color == -1) {
                                Color(0xFFD8DCE3)
                            } else {
                                Color(color)
                            }
                        )
                    }
                }
            }
        }

        if (phase != JailbreakPhase.Running) {
            com.KiYY.lost.ksu.component.GlassDialogButtons(
                confirmText = "完成",
                onConfirm = onDismiss,
            )
        }
    }
}