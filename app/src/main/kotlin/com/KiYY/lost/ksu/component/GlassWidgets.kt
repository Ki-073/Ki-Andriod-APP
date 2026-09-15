package com.KiYY.lost.ksu.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ==========================================================================
// iOS 26「液态玻璃（Liquid Glass）」风格组件
//
// 视觉语言要点（纯 Compose 绘制，无需第三方库）：
//   1. 半透明材质：卡片底色 alpha≈0.55，透出背景壁纸
//   2. 镜面高光：45° 线性渐变（白→透明→白）模拟玻璃边缘反光
//   3. 透镜折射：顶部内侧一道高光 + 底部内侧一道阴影（内阴影浮雕）
//   4. 大圆角：28dp 连续圆角（胶囊化）
//   5. 柔和外阴影：低 alpha、大 blur，营造悬浮玻璃片感
// ==========================================================================

/** 玻璃材质底色：白色半透明（让下方渐变高光透出，形成立体感） */
@Composable
fun glassFill(): Color {
    return Color.White.copy(alpha = 0.58f)
}

/** 玻璃高光描边色（顶部亮 / 底部暗） */
@Composable
fun glassHighlight(): Color = Color.White.copy(alpha = 0.55f)

@Composable
fun glassShadowEdge(): Color = Color.Black.copy(alpha = 0.10f)

/**
 * 液态玻璃容器：大圆角 + 半透明底 + 镜面高光边框 + 内浮雕 + 外投影。
 * 这是所有玻璃组件的基座。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    corner: Int = 28,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // A) 强外阴影 → 玻璃片悬浮
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color(0xFF8A8FA3).copy(alpha = 0.45f),
                spotColor = Color(0xFF6B7080).copy(alpha = 0.50f),
            )
            .clip(shape)
            // B) 玻璃主体：白底半透明
            .background(glassFill())
            // C) 液态斜向反光条（左上→右下的高光带，模拟玻璃弧面掠射反光）
            .background(
                Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.85f),
                    0.18f to Color.White.copy(alpha = 0.25f),
                    0.42f to Color.White.copy(alpha = 0.02f),
                    0.62f to Color.White.copy(alpha = 0.10f),
                    1.0f to Color.White.copy(alpha = 0.45f),
                )
            )
            // D) 竖直明暗：顶部提亮、底部压深 → 形成「隆起」体积感
            .background(
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.55f),
                    0.10f to Color.White.copy(alpha = 0.12f),
                    0.55f to Color.Transparent,
                    0.90f to Color(0xFFB9BFCC).copy(alpha = 0.22f),
                    1.0f to Color(0xFF9AA1B0).copy(alpha = 0.32f),
                )
            )
            // E) 边缘高光圈：上白 → 下灰，模拟玻璃厚度剖面
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.95f),
                    0.5f to Color.White.copy(alpha = 0.45f),
                    1.0f to Color(0xFFA9AFBC).copy(alpha = 0.55f),
                ),
                shape = shape,
            )
            // F) 内侧顶部一道细高光（玻璃内表面反射）
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.55f),
                    0.04f to Color.Transparent,
                    1.0f to Color.Transparent,
                ),
                shape = RoundedCornerShape((corner - 2).coerceAtLeast(4).dp),
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 5.dp),
    ) {
        Column(content = content)
    }
}

/** 玻璃风格的段落行：左标题 + 右内容 */
@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    /** 与 leadingIcon 等价，供 boot 页调用 */
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val resolvedIcon = leadingIcon ?: icon
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (resolvedIcon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFFE8EAEF))
                    .border(
                        1.dp,
                        Color(0xFFD8DCE3),
                        RoundedCornerShape(11.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(resolvedIcon, null, modifier = Modifier.size(19.dp), tint = Color(0xFF3A3A3C))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1C1C1E),
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6C6C70),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

// ==========================================================================
// 玻璃版通用控件（供 boot 导入页 / 其他玻璃页面使用）
// ==========================================================================

/** 玻璃版返回按钮（透明底 + 深色箭头） */
@Composable
fun GlassBackButton(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = Color(0xFF1C1C1E),
        )
    }
}

/** 玻璃版单选行（用于安装方式选择） */
@Composable
fun GlassRadioRow(
    title: String,
    summary: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iOS 风格圆形单选指示器
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .border(
                    width = 1.6.dp,
                    color = if (selected) Color(0xFF0A84FF) else Color(0xFFC7CBD1),
                    shape = RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0A84FF)),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1C1C1E),
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6C6C70),
                )
            }
        }
    }
}

/** 玻璃版复选行（用于「缓存解析数据 / 强制备份」） */
@Composable
fun GlassCheckRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) Color(0xFF0A84FF) else Color(0xFFE8EAEF))
                .border(
                    width = 1.dp,
                    color = if (checked) Color(0xFF0A84FF) else Color(0xFFC7CBD1),
                    shape = RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1C1C1E),
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6C6C70),
                )
            }
        }
    }
}

/** 玻璃版主操作按钮（iOS 蓝色胶囊） */
@Composable
fun GlassPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) Color(0xFF0A84FF) else Color(0xFFD8DCE3))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else Color(0xFF8A8F9A),
        )
    }
}

/** 玻璃内分隔线：极细浅灰，模拟玻璃内部刻痕 */
@Composable
fun GlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .height(0.7.dp)
            .background(Color(0xFFD8DCE3).copy(alpha = 0.55f))
    )
}

/** 玻璃风格的段标题 */
@Composable
fun GlassSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1C1C1E),
    )
}

/** 玻璃风格状态点 */
@Composable
fun GlassDot(color: Color, size: Int = 8) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * iOS 液态玻璃背景（浅色版）：白 → 浅灰的柔和渐变，
 * 给玻璃卡片提供「可透视」的浅色底层 —— 这才是 iOS 浅色模式的观感。
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7F8FA),
                        Color(0xFFEDEFF3),
                        Color(0xFFF2F3F7),
                    )
                )
            )
    ) {
        content()
    }
}