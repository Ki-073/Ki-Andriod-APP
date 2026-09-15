package com.KiYY.lost.ksu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

// ==========================================================================
// Ported from tiann/KernelSU  manager/.../ui/screen/home/HomeMaterial.kt
// GhostLock 版本：状态源改为 GhostlockUiState，动作改为执行越狱 / 高级选项
// ==========================================================================

import com.KiYY.lost.ksu.component.SegmentedColumn
import com.KiYY.lost.ksu.component.SegmentedListItem
import com.KiYY.lost.ksu.component.StatusTag
import com.KiYY.lost.ksu.component.TonalCard
import com.KiYY.lost.ksu.component.ExpressiveScaffold
import com.KiYY.lost.ksu.component.expressiveTopAppBarColors
import com.KiYY.lost.ksu.component.GlassSurface
import com.KiYY.lost.ksu.component.GlassRow
import com.KiYY.lost.ksu.component.GlassDivider
import com.KiYY.lost.ksu.component.GlassSectionTitle
import com.KiYY.lost.ksu.component.GlassBackdrop

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun KsuHomePager(
    state: KsuHomeState,
    actions: KsuHomeActions,
    bottomInnerPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // 按当前 UI 风格分派到两套布局（KernelSU 也是 Material / Miuix 双实现）
    when (state.uiStyle) {
        UiStyle.Glass -> GlassBackdrop {
            GlassHome(state = state, actions = actions, bottomInnerPadding = bottomInnerPadding)
        }
        UiStyle.Material -> MaterialHome(state = state, actions = actions, bottomInnerPadding = bottomInnerPadding)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MaterialHome(
    state: KsuHomeState,
    actions: KsuHomeActions,
    bottomInnerPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Box(Modifier.fillMaxSize()) {
        ExpressiveScaffold(
            topBar = { TopBar(actions = actions, scrollBehavior = scrollBehavior) },
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                if (state.showUnsupportedWarning) {
                    WarningCard(state.unsupportedMessage, WarningLevel.Notice)
                }
                StatusCard(state = state, actions = actions)
                InfoCard(systemInfo = state.systemInfo)
                Spacer(Modifier.height(bottomInnerPadding + 24.dp))
            }
        }
        // 滚动后出现的「居中标题」覆盖层（Material 版）
        ScrolledCenterTitle(scrollBehavior)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    actions: KsuHomeActions,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        // 滚动后再居中：collapsedFraction 0（顶部）→ 左对齐；
        // 1（完全折叠/下滑）→ 居中。用 lerp 平滑过渡。
        title = { TopBarTitle(scrollBehavior) },
        actions = {
            IconButton(onClick = actions.onToggleUiStyle) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = "切换 UI 风格"
                )
            }
            IconButton(onClick = actions.onAdvancedClick) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "高级选项"
                )
            }
        },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

/**
 * 滚动后出现的「居中标题」覆盖层。
 *
 * 独立于 `LargeFlexibleTopAppBar` 的 title 槽 —— 因为 title 槽的坐标系原点
 * 在开始 inset 之后，无法稳定地按「屏幕居中」定位（实测会偏右）。
 * 这里把它做成 Scaffold 之上的覆盖层，坐标以**整屏左上角**为原点，
 * 因此水平居中就是纯数学：(屏宽 - 文字宽) / 2。
 *
 * 可见性：标题随 `collapsedFraction` 淡入（0 → 1 时 alpha 0 → 1），
 * 完全展开（页面顶部）时不可见，下滑后才出现并居中。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ScrolledCenterTitle(scrollBehavior: TopAppBarScrollBehavior?) {
    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    if (collapsedFraction <= 0.01f) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    // 状态栏高度：从系统资源读取（零依赖，避免 WindowInsets 扩展 API 版本差异）
    val context = androidx.compose.ui.platform.LocalContext.current
    val statusBarPx = androidx.compose.runtime.remember(context) {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
    val topOffsetPx = statusBarPx + with(density) { 26.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = with(density) { topOffsetPx.toDp() }),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = "Ki-PrisonBreak",
            style = MaterialTheme.typography.titleLarge,
            color = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer { alpha = collapsedFraction }
        )
    }
}

/**
 * TopBar 内的标题：展开态使用（左对齐）。
 * 折叠后由 [ScrolledCenterTitle] 覆盖层接管居中显示。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TopBarTitle(scrollBehavior: TopAppBarScrollBehavior?) {
    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    // 折叠过程中原槽位标题同步淡出，交棒给居中覆盖层
    Text(
        text = "Ki-PrisonBreak",
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer { alpha = (1f - collapsedFraction * 2f).coerceIn(0f, 1f) }
    )
}

enum class WarningLevel { Error, Notice }

@Composable
private fun StatusCard(
    state: KsuHomeState,
    actions: KsuHomeActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        val active = state.isWorking
        val notActive = !active

        val containerColor = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)

        val statusIcon = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Warning
        val statusTitle = if (active) "工作中" else "未工作"
        val workingMode = if (active) state.workingMode else ""

        val statusTrailing: (@Composable () -> Unit)? = if (active && workingMode.isNotEmpty()) {
            {
                StatusTag(
                    label = workingMode,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary
                )
            }
        } else if (notActive) {
            {
                Button(
                    onClick = actions.onJailbreakClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("执行越狱")
                }
            }
        } else null

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
            onClick = actions.onStatusCardClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---- 主行：大图标 + 状态标题 + 越狱模式标签 + 右侧按钮 ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusTitle,
                        modifier = Modifier.size(if (active) 48.dp else 40.dp),
                        tint = contentColor
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusTitle,
                            style = if (active) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            color = contentColor
                        )
                    }
                    statusTrailing?.invoke()
                }

                // ---- 支持信息：仅“未工作”时显示（工作中不显示内核已适配 / boot 解析）----
                if (notActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = state.idleSummary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor.copy(alpha = 0.9f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.bootParsed) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.Info
                                },
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.bootParsed) "boot 已解析" else "boot 未解析",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    message: String,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null,
) {
    val containerColor = when (level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.contentColorFor(containerColor)
            )
        }
    }
    if (onClick != null) {
        TonalCard(containerColor = containerColor, onClick = onClick, content = content)
    } else {
        TonalCard(containerColor = containerColor, content = content)
    }
}

@Composable
private fun InfoCard(
    systemInfo: KsuSystemInfo,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun InfoCardItem(
        icon: ImageVector,
        label: String,
        content: String,
        modifier: Modifier = Modifier,
    ) {
        SegmentedListItem(
            modifier = modifier,
            headlineContent = { Text(text = label, style = MaterialTheme.typography.bodyLarge) },
            leadingContent = { Icon(imageVector = icon, contentDescription = label) },
            supportingContent = {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        var showGuide by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        val context = androidx.compose.ui.platform.LocalContext.current

        SegmentedColumn(modifier = Modifier.fillMaxWidth(), content = listOf(
            {
                InfoCardItem(
                    icon = Icons.Filled.Tag,
                    label = "管理器版本",
                    content = systemInfo.managerVersion,
                )
            },
            {
                InfoCardItem(
                    icon = Icons.Filled.DeveloperBoard,
                    label = "内核版本",
                    content = systemInfo.kernelVersion,
                )
            },
            {
                InfoCardItem(
                    icon = Icons.Filled.Smartphone,
                    label = "设备型号",
                    content = systemInfo.deviceModel,
                )
            },
            {
                InfoCardItem(
                    icon = Icons.Filled.Fingerprint,
                    label = "系统指纹",
                    content = systemInfo.fingerprint,
                )
            },
        ))
        SegmentedColumn(modifier = Modifier.fillMaxWidth(), content = listOf(
            {
                InfoCardItem(
                    icon = Icons.Filled.Security,
                    label = "SELinux 模式",
                    content = systemInfo.seLinux,
                )
            },
            {
                InfoCardItem(
                    icon = Icons.Filled.Lock,
                    label = "Seccomp 模式",
                    content = systemInfo.seccomp,
                )
            },
        ))

        // ---- 使用教程（可点击行 → Material 弹窗）----
        SegmentedColumn(modifier = Modifier.fillMaxWidth(), content = listOf(
            {
                SegmentedListItem(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showGuide = true },
                    headlineContent = {
                        Text(text = "使用教程", style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Info, contentDescription = "使用教程")
                    },
                    supportingContent = {
                        Text(
                            text = "切换 UI / 解析 boot / 越狱 / 高级选项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "查看",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            },
        ))

        // ---- 支持开发（可点击行 → 跳转 GitHub 开源仓库）----
        SegmentedColumn(modifier = Modifier.fillMaxWidth(), content = listOf(
            {
                SegmentedListItem(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openProjectGithub(context) },
                    headlineContent = {
                        Text(text = "支持开发", style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Tag, contentDescription = "支持开发")
                    },
                    supportingContent = {
                        Text(
                            text = "开源地址 · GitHub",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "前往",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            },
        ))

        // ---- 加入频道（Telegram，参考 Ki-VPN 的跳转实现）----
        SegmentedColumn(modifier = Modifier.fillMaxWidth(), content = listOf(
            {
                SegmentedListItem(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        com.KiYY.lost.util.AutoStartHelper.openTelegramChannel(
                            context, TELEGRAM_CHANNEL_DOMAIN
                        )
                    },
                    headlineContent = {
                        Text(text = "加入频道", style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Info, contentDescription = "加入频道")
                    },
                    supportingContent = {
                        Text(
                            text = "Telegram · @$TELEGRAM_CHANNEL_DOMAIN",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "加入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            },
        ))

        if (showGuide) {
            UsageGuideDialog(onDismiss = { showGuide = false })
        }
    }
}

/**
 * 使用教程弹窗（Material 版）。
 * 与玻璃 UI 的 [GlassUsageGuideDialog] 内容一致，仅外壳不同。
 */
@Composable
private fun UsageGuideDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用教程") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuideStep(
                    index = "1",
                    title = "切换 UI 风格",
                    body = "点击右上角的 ⇄ 图标，可在「液态玻璃」与「Material」两套界面之间切换。",
                )
                GuideStep(
                    index = "2",
                    title = "打开高级选项",
                    body = "点击右上角的滑块图标（≡）。内含 CPU 核心选择、安全模式、TCP 路由、" +
                        "失败自动重试、导入/导出 offsets 等设置。",
                )
                GuideStep(
                    index = "3",
                    title = "解析 boot",
                    body = "点击状态卡进入「boot 解析」页，选择与机型匹配的 boot 镜像与偏移。" +
                        "解析成功后状态卡会显示「boot 已解析」。",
                )
                GuideStep(
                    index = "4",
                    title = "执行越狱",
                    body = "确认状态卡显示「未工作」时，点击「执行越狱」。越狱过程中请保持 App 在前台，" +
                        "以保证越狱成功。成功后状态卡变为「工作中」。",
                )
                GuideStep(
                    index = "5",
                    title = "授予超级用户权限",
                    body = "越狱成功后，请在 KernelSU 管理器内授予本 App 超级用户权限，" +
                        "以便检测越狱状态。",
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

// ==========================================================================
// iOS 液态玻璃（Liquid Glass）风格首页
// 视觉语言：透明玻璃卡片 + 镜面高光 + 流光背景 + 悬浮投影
// ==========================================================================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GlassHome(
    state: KsuHomeState,
    actions: KsuHomeActions,
    bottomInnerPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // 关键：Scaffold 用透明容器色，让外层的流光背景透上来，
    // 玻璃卡片才能真正“透视”背景 —— 否则叠在浅灰上会发白发灰。
    Box(Modifier.fillMaxSize()) {
        ExpressiveScaffold(
            topBar = { GlassTopBar(actions = actions, scrollBehavior = scrollBehavior) },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (state.showUnsupportedWarning) {
                    WarningCard(state.unsupportedMessage, WarningLevel.Notice)
                }
                GlassStatusCard(state = state, actions = actions)
                GlassInfoSection(systemInfo = state.systemInfo)
                Spacer(Modifier.height(bottomInnerPadding + 24.dp))
            }
        }
        // 滚动后出现的「居中标题」覆盖层（Glass 版）
        ScrolledCenterTitle(scrollBehavior)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GlassTopBar(
    actions: KsuHomeActions,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        // 滚动后再居中（同 Material 版逻辑）
        title = { TopBarTitle(scrollBehavior) },
        actions = {
            IconButton(onClick = actions.onToggleUiStyle) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "切换 UI 风格")
            }
            IconButton(onClick = actions.onAdvancedClick) {
                Icon(Icons.Filled.Tune, contentDescription = "高级选项")
            }
        },
        // 浅色玻璃：TopBar 透明背景 + 深色文字
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            titleContentColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
            actionIconContentColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
            navigationIconContentColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun GlassStatusCard(
    state: KsuHomeState,
    actions: KsuHomeActions,
) {
    val active = state.isWorking
    val notActive = !active
    val statusIcon = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Warning
    val statusTitle = if (active) "工作中" else "未工作"

    GlassSurface(
        corner = 32,
        onClick = actions.onStatusCardClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(if (active) 52.dp else 42.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = statusTitle,
                        style = if (active) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                    )
                    if (active && state.workingVersion.isNotEmpty()) {
                        Text(
                            text = "版本：${state.workingVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF6C6C70),
                        )
                    }
                }
                if (active && state.workingMode.isNotEmpty()) {
                    StatusTag(
                        label = state.workingMode,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // 工作中时隐藏内核已适配 / boot 解析信息（与 Material 版一致）
            if (notActive) {
                GlassDivider()
                Text(
                    text = if (state.bootParsed) "boot 已解析" else "boot 未解析",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color(0xFF6C6C70),
                )
                Button(
                    onClick = actions.onJailbreakClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                ) { Text("执行越狱") }
            }
        }
    }
}

@Composable
private fun GlassInfoSection(systemInfo: KsuSystemInfo) {
    var showGuide by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // 注：不再显示「系统信息」小节标题（用户反馈占空间），直接呈现信息卡
        GlassSurface(corner = 26) {
            GlassRow(title = "内核版本", subtitle = systemInfo.kernelVersion)
            GlassDivider()
            GlassRow(title = "管理器版本", subtitle = systemInfo.managerVersion)
            GlassDivider()
            GlassRow(title = "设备型号", subtitle = systemInfo.deviceModel)
            GlassDivider()
            GlassRow(title = "系统指纹", subtitle = systemInfo.fingerprint)
            GlassDivider()
            GlassRow(title = "SELinux 模式", subtitle = systemInfo.seLinux)
            GlassDivider()
            GlassRow(title = "Seccomp 模式", subtitle = systemInfo.seccomp)
        }

        // ---- 使用教程（独立玻璃行，点击弹出玻璃弹窗）----
        GlassSurface(corner = 26, onClick = { showGuide = true }) {
            GlassRow(
                title = "使用教程",
                subtitle = "切换 UI / 解析 boot / 越狱 / 高级选项",
                trailing = {
                    Text(
                        text = "查看",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color(0xFF0A84FF),
                    )
                },
            )
        }

        // ---- 支持开发（跳转 GitHub 开源仓库）----
        GlassSurface(corner = 26, onClick = { openProjectGithub(context) }) {
            GlassRow(
                title = "支持开发",
                subtitle = "开源地址 · GitHub",
                trailing = {
                    Text(
                        text = "前往",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color(0xFF0A84FF),
                    )
                },
            )
        }

        // ---- 加入频道（Telegram，参考 Ki-VPN 的跳转实现）----
        GlassSurface(
            corner = 26,
            onClick = {
                com.KiYY.lost.util.AutoStartHelper.openTelegramChannel(
                    context, TELEGRAM_CHANNEL_DOMAIN
                )
            }
        ) {
            GlassRow(
                title = "加入频道",
                subtitle = "Telegram · @$TELEGRAM_CHANNEL_DOMAIN",
                trailing = {
                    Text(
                        text = "加入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color(0xFF0A84FF),
                    )
                },
            )
        }
    }

    if (showGuide) {
        GlassUsageGuideDialog(onDismiss = { showGuide = false })
    }
}

/** 项目开源仓库地址（与 KernelSU 一样开源） */
private const val PROJECT_GITHUB_URL = "https://github.com/Ki-073/Ki-Andriod-APP"

/** Telegram 频道用户名（不带 @）—— 页面最底部「加入频道」跳转目标 */
private const val TELEGRAM_CHANNEL_DOMAIN = "HouseLua"

/** 用外部浏览器打开项目 GitHub 仓库 */
internal fun openProjectGithub(context: android.content.Context) {
    runCatching {
        val i = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(PROJECT_GITHUB_URL)
        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(i)
    }
}

/**
 * 使用教程弹窗（玻璃 UI）。
 *
 * 点击首页「使用教程」行后弹出，说明：
 *  - 如何切换 UI 风格
 *  - 高级选项在哪里
 *  - 如何解析 boot
 *  - 如何执行越狱
 *  - 如何授予超级用户权限
 */
@Composable
private fun GlassUsageGuideDialog(onDismiss: () -> Unit) {
    com.KiYY.lost.ksu.component.GlassDialog(onDismiss = onDismiss) {
        com.KiYY.lost.ksu.component.GlassDialogCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "使用教程",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                )
                // 教程条目区：固定最大高度，超出可滚动。
                // ⚠ 用 heightIn(min, max) 明确高度区间，不要用 weight ——
                //   卡片内部的 Column 不是 weight 的适用父容器，会导致宽度/高度计算异常。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GuideStep(
                        index = "1",
                        title = "切换 UI 风格",
                        body = "点击右上角的 ⇄ 图标，可在「液态玻璃」与「Material」两套界面之间切换。",
                    )
                    GuideStep(
                        index = "2",
                        title = "打开高级选项",
                        body = "点击右上角的滑块图标（≡）。内含 CPU 核心选择、安全模式、TCP 路由、" +
                            "失败自动重试、导入/导出 offsets 等设置。",
                    )
                    GuideStep(
                        index = "3",
                        title = "解析 boot",
                        body = "点击状态卡进入「boot 解析」页，选择与机型匹配的 boot 镜像与偏移。" +
                            "解析成功后状态卡会显示「boot 已解析」。",
                    )
                    GuideStep(
                        index = "4",
                        title = "执行越狱",
                        body = "确认状态卡显示「未工作」时，点击「执行越狱」。越狱过程中请保持 App 在前台，" +
                            "以保证越狱成功。成功后状态卡变为「工作中」。",
                    )
                    GuideStep(
                        index = "5",
                        title = "授予超级用户权限",
                        body = "越狱成功后，请在 KernelSU 管理器内授予本 App 超级用户权限，" +
                            "以便检测越狱状态。",
                    )
                }
                com.KiYY.lost.ksu.component.GlassDialogButtons(
                    confirmText = "知道了",
                    onConfirm = onDismiss,
                )
            }
        }
    }
}

/** 教程单条：序号圆点 + 标题 + 正文 */
@Composable
private fun GuideStep(index: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ⚠ 序号圆点必须是「固定 24dp 正方形」的正圆：
        //   - Box + size(24.dp) + CircleShape → 正圆背景
        //   - contentAlignment = Center → 数字正中对齐
        //   ⚠ 不要再叠加 wrapContentWidth / weight 之类，否则尺寸会被覆盖，变成椭圆。
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color(0xFF0A84FF),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index,
                style = MaterialTheme.typography.labelMedium,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        // 正文列必须 weight(1f) 占据剩余全部宽度，否则会被压窄成一列。
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFF6C6C70),
            )
        }
    }
}