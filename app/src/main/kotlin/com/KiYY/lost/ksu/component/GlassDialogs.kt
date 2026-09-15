package com.KiYY.lost.ksu.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ==========================================================================
// iOS 液态玻璃风格的对话框容器与子控件
//   - 与首页 GlassSurface 同一套视觉语言：白底半透明 + 镜面高光 + 强外阴影
//   - 切换玻璃 UI 后，高级选项 / boot 导入页 / 越狱弹窗 / 各类确认框统一使用
// ==========================================================================

/** 玻璃对话框的文本主色（浅色玻璃底 → 深色字） */
val GlassTextPrimary = Color(0xFF1C1C1E)
val GlassTextSecondary = Color(0xFF6C6C70)

/**
 * 液态玻璃对话框容器。
 *
 * 用原生 [Dialog] 自绘，而不是 Material 的 AlertDialog ——
 * 后者会把 containerColor 强制填成 surface，无法透出玻璃质感。
 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = dismissOnBackPress,
            usePlatformDefaultWidth = false,
        ),
    ) {
        GlassDialogCard(modifier = modifier, content = content)
    }
}

/**
 * 玻璃对话框卡片本体：6 层立体液态玻璃
 *  A) 强外阴影（悬浮感）
 *  B) 白底半透明（磨砂玻璃）
 *  C) 斜向镜面反光条
 *  D) 竖直明暗（顶亮底暗 = 厚度）
 *  E) 边缘高光圈（上白下灰）
 *  F) 内侧顶部细高光
 */
@Composable
fun GlassDialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    // ⚠ Dialog(usePlatformDefaultWidth = false) 的窗口不限制宽度，
    //   直接用 fillMaxWidth() 可能拿到异常小的测量基准（实测正文被挤成半屏）。
    //   这里显式按「屏幕宽 - 2*边距」计算卡片宽度，保证内容有充足空间。
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val cardWidth = (screenWidthDp - 48).coerceAtLeast(240).dp
    Box(
        modifier = modifier
            .width(cardWidth)
            // A) 悬浮投影
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = Color(0xFF8A8FA3).copy(alpha = 0.45f),
                spotColor = Color(0xFF6B7080).copy(alpha = 0.50f),
            )
            .clip(shape)
            // B) 玻璃底
            .background(Color.White.copy(alpha = 0.82f))
            // C) 斜向反光条
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.02f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.45f),
                    )
                )
            )
            // D) 竖直明暗（立体厚度）
            .background(
                Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = 0.55f),
                    0.10f to Color.White.copy(alpha = 0.12f),
                    0.55f to Color.Transparent,
                    0.90f to Color(0xFFB9BFCC).copy(alpha = 0.22f),
                    1.00f to Color(0xFF9AA1B0).copy(alpha = 0.32f),
                )
            )
            // E) 边缘高光圈（上亮下暗）
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.95f),
                    0.5f to Color.White.copy(alpha = 0.45f),
                    1.0f to Color(0xFFA9AFBC).copy(alpha = 0.55f),
                ),
                shape = shape,
            )
            // F) 内侧顶部细高光
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.9f),
                    0.15f to Color.Transparent,
                ),
                shape = shape,
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** 玻璃对话框标题 */
@Composable
fun GlassDialogTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = GlassTextPrimary,
    )
}

/** 玻璃对话框正文文字 */
@Composable
fun GlassDialogBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = GlassTextSecondary,
    )
}

/** 玻璃分隔线（与首页一致：极细、半透明灰） */
@Composable
fun GlassDialogDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFD8DCE3).copy(alpha = 0.25f),
                        Color(0xFFD8DCE3).copy(alpha = 0.55f),
                        Color(0xFFD8DCE3).copy(alpha = 0.25f),
                    )
                )
            )
    )
}

/** 玻璃开关（iOS 风格：开启为主色，关闭为浅灰浮起） */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFF0A84FF),
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFD8DCE3).copy(alpha = 0.9f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** 玻璃对话框底部按钮（主/次） */
@Composable
fun GlassDialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dismissText != null) {
            GlassDialogButton(
                text = dismissText,
                onClick = { onDismiss?.invoke() },
                primary = false,
            )
            Spacer(Modifier.width(10.dp))
        }
        GlassDialogButton(
            text = confirmText,
            onClick = onConfirm,
            primary = true,
            enabled = confirmEnabled,
        )
    }
}

/** 单个玻璃胶囊按钮 */
@Composable
fun GlassDialogButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val bg = when {
        !enabled -> Color(0xFFD8DCE3)
        primary -> Color(0xFF0A84FF)
        else -> Color.White.copy(alpha = 0.75f)
    }
    val fg = when {
        !enabled -> Color(0xFF8A8F9A)
        primary -> Color.White
        else -> GlassTextPrimary
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(
                if (!primary && enabled) {
                    Modifier.border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.9f),
                            1f to Color(0xFFA9AFBC).copy(alpha = 0.45f),
                        ),
                        shape = shape,
                    )
                } else Modifier
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}

/** 玻璃对话框内的可点击行（用于导入/导出 offsets 等动作） */
@Composable
fun GlassDialogActionRow(
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.55f))
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.9f),
                    1f to Color(0xFFD8DCE3).copy(alpha = 0.6f),
                ),
                shape = shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF0A84FF),
        )
    }
}