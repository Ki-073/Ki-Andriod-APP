package com.KiYY.lost.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.KiYY.lost.BuildConfig
import com.KiYY.lost.R
import com.KiYY.lost.ksu.KsuHomeActions
import com.KiYY.lost.ksu.KsuHomePager
import com.KiYY.lost.ksu.KsuHomeState
import com.KiYY.lost.ksu.KsuSystemInfo
import com.KiYY.lost.ksu.BootImportMethod
import com.KiYY.lost.ksu.InstallBootActions
import com.KiYY.lost.ksu.InstallBootPage
import com.KiYY.lost.ksu.InstallBootState
import com.KiYY.lost.ksu.JailbreakPhase
import com.KiYY.lost.ksu.JailbreakProgressDialog

// ---- State / model / actions ----

enum class WorkState { IDLE, WORKING }

enum class Workspace { HOME, EXECUTE, ADVANCED, SETTINGS }

data class GhostlockUiState(
    val deviceName: String = "",
    val kernelRelease: String = "",
    val socName: String = "",
    val fingerprint: String = "",
    val kernelSupported: Boolean = false,
    val running: Boolean = false,
    val workState: WorkState = WorkState.IDLE,
    val workspace: Workspace = Workspace.HOME,
    val advancedVisible: Boolean = false,
    val exportVisible: Boolean = false,
    val cpuPairLabels: List<String> = emptyList(),
    val cpuPairIndex: Int = 0,
    /** 高级选项里 CPU 核心列表是否展开 */
    val cpuExpanded: Boolean = false,
    val safeModeEnabled: Boolean = false,
    val tcpRouteEnabled: Boolean = true,
    /** 开机自动越狱（需自启动权限） */
    val autoJailbreakOnBoot: Boolean = false,
    /** 越狱失败自动重试（最多 3 次） */
    val autoRetryJailbreak: Boolean = false,
    val compact: Boolean = false,
    /** SELinux 模式 */
    val seLinux: String = "-",
    /** Seccomp 状态 */
    val seccomp: String = "-",
    /** boot.img 是否已成功解析（写入 offsets） */
    val bootParsed: Boolean = false,
    // ---- boot 导入页（对应 KSU 安装页）----
    val bootPageVisible: Boolean = false,
    val bootMethod: BootImportMethod? = null,
    val bootFileName: String? = null,
    val bootFileStaged: Boolean = false,
    val bootAdvancedShown: Boolean = false,
    val bootForceBackup: Boolean = false,
    /** 缓存解析数据：开启后解析结果被缓存，后续越狱直接用缓存 */
    val bootCacheParseData: Boolean = false,
    /** 当前 UI 风格（Material / Miuix） */
    val uiStyle: com.KiYY.lost.ksu.UiStyle = com.KiYY.lost.ksu.UiStyle.Material,
    /** 越狱进度弹窗：控制台是否展开 */
    val consoleExpanded: Boolean = false,
    /** 越狱进度弹窗：是否已被用户关闭（运行中不可关闭） */
    val progressDialogDismissed: Boolean = false,
    val executionSheetVisible: Boolean = false,
    val executionSheetDismissible: Boolean = false,
    val dialogVisible: Boolean = false,
    val dialogType: DialogType = DialogType.NONE,
    val dialogTitleRes: Int = 0,
    val dialogMessage: String = "",
    val dialogMessageRes: Int = 0,
    val dialogItems: List<String> = emptyList(),
    val dialogItemResIds: List<Int> = emptyList(),
    val dialogCurrentItemIndex: Int = -1,
    val dialogInput: String = "",
    val overwriteDialogVisible: Boolean = false,
    val overwriteMessage: String = "",
    val logLines: List<GhostlockLogLine> = emptyList(),
)

enum class DialogType { NONE, LIST, INPUT, ADVANCED, CONFIRM_RUN }

data class GhostlockLogLine(val text: String, val color: Int)

interface GhostlockActions {
    fun onRun()
    fun onRequestRun()          // opens the confirm dialog
    fun onCloseExecutionSheet()
    fun onToggleAdvanced()
    fun onCopyLogs()
    fun onImportOffsets()
    fun onParseOta()
    fun onParseImage()
    fun onExportOffsets()
    fun onCpuPairSelected(index: Int)
    fun onToggleCpuExpanded()
    fun onSafeModeChanged(enabled: Boolean)
    fun onTcpRouteChanged(enabled: Boolean)
    /** 失败自动重试开关 */
    fun onAutoRetryChanged(enabled: Boolean)
    /** 申请 Root 授权（触发 KSU 授权弹框） */
    fun onRequestRoot()
    /** 问题反馈（跳转 QQ 聊天） */
    fun onFeedback()
    fun onDialogItemSelected(index: Int)
    fun onDialogInputChange(value: String)
    fun onDialogConfirm(value: String)
    fun onDialogDismiss()
    fun onDialogDismissFinished()
    fun onOverwriteConfirm()
    fun onOverwriteDismiss()
    fun onToggleUiStyle()
    fun onWorkspaceSelected(workspace: Workspace)
    // ---- boot 导入页（对应 KSU 安装页）----
    fun onOpenBootPage()
    fun onCloseBootPage()
    fun onBootMethodSelected(method: BootImportMethod)
    fun onBootPickFile()
    fun onBootClearFile()
    fun onBootToggleAdvanced()
    fun onBootToggleForceBackup(enabled: Boolean)
    fun onBootToggleCacheParseData(enabled: Boolean)
    fun onBootNext()
    // ---- 越狱进度弹窗 ----
    fun onToggleConsoleExpand()
    fun onDismissProgressDialog()
}

@Composable
fun GhostlockTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}

/**
 * 照搬自 KernelSU 的 PagerNavigationSpringSpec：
 *   spring(stiffness = 322.2f, dampingRatio = 32.31f / (2f * sqrt(322.2f)), visibilityThreshold = 0.5f)
 * 临界阻尼弹簧（dampingRatio ≈ 0.90），用于页面/标签切换，替代生硬的 tween 线性动画。
 */
private val KsuSpringSpec: androidx.compose.animation.core.SpringSpec<Float> =
    androidx.compose.animation.core.spring(
        stiffness = 322.2f,
        dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
        visibilityThreshold = 0.5f,
    )

/** 同参数，但用于 slide 的 IntOffset 版本 */
private val KsuSlideSpringSpec: androidx.compose.animation.core.SpringSpec<androidx.compose.ui.unit.IntOffset> =
    androidx.compose.animation.core.spring(
        stiffness = 322.2f,
        dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
        visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1),
    )

/**
 * 主界面：直接使用 KernelSU 原版 Home 页布局（KsuHomePager）。
 * 状态映射：
 *   WorkState.WORKING -> 工作中（次要色卡片 + 版本 + 模式徽章）
 *   WorkState.IDLE    -> 未工作（错误色卡片 + “执行越狱”按钮）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostlockApp(state: GhostlockUiState, actions: GhostlockActions) {
    GhostlockTheme {
        val ksuState = KsuHomeState(
            isWorking = state.workState == WorkState.WORKING,
            workingVersion = "v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
            workingMode = "PWNED",
            uiStyle = state.uiStyle,
            idleSummary = if (state.kernelSupported) {
                "内核已适配"
            } else {
                "内核未适配"
            },
            isJailbreakMode = state.workState == WorkState.WORKING,
            bootParsed = state.bootParsed,
            showUnsupportedWarning = !state.kernelSupported,
            unsupportedMessage = "当前内核不在适配表内，请先解析 boot.img 或导入 offsets.json",
            systemInfo = KsuSystemInfo(
                kernelVersion = state.kernelRelease.ifEmpty { "-" },
                managerVersion = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                deviceModel = state.deviceName.ifEmpty { "-" },
                fingerprint = state.fingerprint.ifEmpty { "-" },
                seLinux = state.seLinux,
                seccomp = state.seccomp,
            ),
        )

        val ksuActions = KsuHomeActions(
            onJailbreakClick = { actions.onRequestRun() },
            onAdvancedClick = { actions.onToggleAdvanced() },
            onToggleUiStyle = { actions.onToggleUiStyle() },
            // 已越狱（工作中）时禁止进入 boot 导入页：越狱后 offsets 已固化，重复解析无意义
            onStatusCardClick = { if (state.workState != WorkState.WORKING) actions.onOpenBootPage() },
        )

// ---- 页面切换：KSU 风格弹簧转场（PagerNavigationSpringSpec）----
        AnimatedContent(
            targetState = state.bootPageVisible,
            transitionSpec = {
                if (targetState) {
                    // 进入 boot 页：从右侧滑入 + 淡入（弹簧曲线）
                    (slideInHorizontally(
                        animationSpec = KsuSlideSpringSpec,
                        initialOffsetX = { it }
                    ) + fadeIn(animationSpec = tween(220))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = KsuSlideSpringSpec,
                            targetOffsetX = { -it / 3 }
                        ) + fadeOut(animationSpec = tween(200)))
                } else {
                    // 返回主界面：从左侧滑入 + 淡入（弹簧曲线）
                    (slideInHorizontally(
                        animationSpec = KsuSlideSpringSpec,
                        initialOffsetX = { -it / 3 }
                    ) + fadeIn(animationSpec = tween(220))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = KsuSlideSpringSpec,
                            targetOffsetX = { it }
                        ) + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "PageTransition"
        ) { showBootPage ->
            if (showBootPage) {
                InstallBootPage(
                    state = InstallBootState(
                        method = state.bootMethod,
                        selectedFileName = state.bootFileName,
                        fileStaged = state.bootFileStaged,
                        advancedShown = state.bootAdvancedShown,
                        forceBackup = state.bootForceBackup,
                        cacheParseData = state.bootCacheParseData,
                        busy = state.running,
                    ),
                    glassStyle = state.uiStyle == com.KiYY.lost.ksu.UiStyle.Glass,
                    actions = InstallBootActions(
                        onBack = { actions.onCloseBootPage() },
                        onSelectMethod = { actions.onBootMethodSelected(it) },
                        onPickFile = { actions.onBootPickFile() },
                        onClearFile = { actions.onBootClearFile() },
                        onToggleAdvanced = { actions.onBootToggleAdvanced() },
                        onToggleForceBackup = { actions.onBootToggleForceBackup(it) },
                        onToggleCacheParseData = { actions.onBootToggleCacheParseData(it) },
                        onNext = { actions.onBootNext() },
                    ),
                )
            } else {
                KsuHomePager(state = ksuState, actions = ksuActions)
            }
        }
        // ---- 越狱进度弹窗（“正在越狱” + 可展开控制台）----
        val showProgress = !state.progressDialogDismissed &&
            (state.running || state.executionSheetVisible)
        if (showProgress) {
            val phase = when {
                state.running -> JailbreakPhase.Running
                state.workState == WorkState.WORKING -> JailbreakPhase.Success
                else -> JailbreakPhase.Failed
            }
            JailbreakProgressDialog(
                phase = phase,
                logLines = state.logLines.map { it.text to it.color },
                dismissible = !state.running,
                expanded = state.consoleExpanded,
                onToggleExpand = { actions.onToggleConsoleExpand() },
                onCopyLogs = { actions.onCopyLogs() },
                onDismiss = { actions.onDismissProgressDialog() },
                glass = state.uiStyle == com.KiYY.lost.ksu.UiStyle.Glass,
            )
        }

        // ---- Dialogs (保留原有弹窗逻辑) ----
        if (state.dialogVisible) {
            when (state.dialogType) {
                DialogType.ADVANCED -> AdvancedDialog(state, actions)
                DialogType.CONFIRM_RUN -> RunConfirmDialog(state, actions)
                DialogType.LIST -> ListDialog(state, actions)
                DialogType.INPUT -> InputDialog(state, actions)
                DialogType.NONE -> Unit
            }
        }

        if (state.overwriteDialogVisible) {
            if (state.uiStyle == com.KiYY.lost.ksu.UiStyle.Glass) {
                com.KiYY.lost.ksu.component.GlassDialog(onDismiss = { actions.onOverwriteDismiss() }) {
                    com.KiYY.lost.ksu.component.GlassDialogTitle(stringResource(R.string.overwrite_title))
                    com.KiYY.lost.ksu.component.GlassDialogBody(state.overwriteMessage)
                    com.KiYY.lost.ksu.component.GlassDialogButtons(
                        confirmText = stringResource(R.string.confirm),
                        onConfirm = { actions.onOverwriteConfirm() },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { actions.onOverwriteDismiss() },
                    )
                }
            } else {
                AlertDialog(
                    onDismissRequest = { actions.onOverwriteDismiss() },
                    title = { Text(stringResource(R.string.overwrite_title)) },
                    text = { Text(state.overwriteMessage) },
                    confirmButton = {
                        TextButton(onClick = { actions.onOverwriteConfirm() }) {
                            Text(stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { actions.onOverwriteDismiss() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AdvancedDialog(state: GhostlockUiState, actions: GhostlockActions) {
    // 按 UI 风格分派：Glass 走液态玻璃自绘对话框
    if (state.uiStyle == com.KiYY.lost.ksu.UiStyle.Glass) {
        GlassAdvancedDialog(state, actions)
    } else {
        MaterialAdvancedDialog(state, actions)
    }
}

/** 液态玻璃版「高级选项」（iOS 风格：玻璃卡片 + 胶囊按钮 + 圆角开关） */
@Composable
private fun GlassAdvancedDialog(state: GhostlockUiState, actions: GhostlockActions) {
    com.KiYY.lost.ksu.component.GlassDialog(onDismiss = { actions.onDialogDismiss() }) {
        com.KiYY.lost.ksu.component.GlassDialogTitle(stringResource(R.string.advanced_title))
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- CPU 核心：可展开/折叠 ----
            if (state.cpuPairLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { actions.onToggleCpuExpanded() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.advanced_cpu_pair),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = com.KiYY.lost.ksu.component.GlassTextPrimary,
                    )
                    Text(
                        text = state.cpuPairLabels.getOrNull(state.cpuPairIndex) ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.KiYY.lost.ksu.component.GlassTextSecondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (state.cpuExpanded) "收起" else "展开",
                        tint = com.KiYY.lost.ksu.component.GlassTextSecondary,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (state.cpuExpanded) 180f else 0f
                        },
                    )
                }
                AnimatedVisibility(visible = state.cpuExpanded) {
                    Column {
                        state.cpuPairLabels.forEachIndexed { index, label ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = com.KiYY.lost.ksu.component.GlassTextPrimary,
                                )
                                com.KiYY.lost.ksu.component.GlassSwitch(
                                    checked = state.cpuPairIndex == index,
                                    onCheckedChange = { if (it) actions.onCpuPairSelected(index) },
                                )
                            }
                        }
                    }
                }
            }
            GlassSwitchRow(
                title = stringResource(R.string.advanced_safe_mode),
                subtitle = null,
                checked = state.safeModeEnabled,
                onCheckedChange = { actions.onSafeModeChanged(it) },
            )
            GlassSwitchRow(
                title = stringResource(R.string.advanced_tcp_route),
                subtitle = null,
                checked = state.tcpRouteEnabled,
                onCheckedChange = { actions.onTcpRouteChanged(it) },
            )
            GlassSwitchRow(
                title = "越狱失败自动重试",
                subtitle = "失败后自动重新越狱，最多 3 次",
                checked = state.autoRetryJailbreak,
                onCheckedChange = { actions.onAutoRetryChanged(it) },
            )
            com.KiYY.lost.ksu.component.GlassDialogDivider()
            com.KiYY.lost.ksu.component.GlassDialogActionRow(
                label = stringResource(R.string.advanced_import_offsets),
                onClick = { actions.onImportOffsets() },
            )
            com.KiYY.lost.ksu.component.GlassDialogActionRow(
                label = stringResource(R.string.advanced_export_offsets),
                onClick = { actions.onExportOffsets() },
            )
            com.KiYY.lost.ksu.component.GlassDialogDivider()
            // ---- 问题反馈（跳转 QQ）----
            com.KiYY.lost.ksu.component.GlassDialogActionRow(
                label = "问题反馈（QQ：3283138152）",
                onClick = { actions.onFeedback() },
            )
        }
        com.KiYY.lost.ksu.component.GlassDialogButtons(
            confirmText = stringResource(R.string.done),
            onConfirm = { actions.onDialogDismiss() },
        )
    }
}

/** 玻璃版「标题 + 副标题 + 开关」行 */
@Composable
private fun GlassSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = com.KiYY.lost.ksu.component.GlassTextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = com.KiYY.lost.ksu.component.GlassTextSecondary,
                )
            }
        }
        com.KiYY.lost.ksu.component.GlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun MaterialAdvancedDialog(state: GhostlockUiState, actions: GhostlockActions) {
    AlertDialog(
        onDismissRequest = { actions.onDialogDismiss() },
        title = { Text(stringResource(R.string.advanced_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---- CPU 核心：可展开/折叠 ----
                if (state.cpuPairLabels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actions.onToggleCpuExpanded() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.advanced_cpu_pair),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = state.cpuPairLabels.getOrNull(state.cpuPairIndex) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = if (state.cpuExpanded) "收起" else "展开",
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (state.cpuExpanded) 180f else 0f
                            },
                        )
                    }
                    AnimatedVisibility(visible = state.cpuExpanded) {
                        Column {
                            state.cpuPairLabels.forEachIndexed { index, label ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Switch(
                                        checked = state.cpuPairIndex == index,
                                        onCheckedChange = { if (it) actions.onCpuPairSelected(index) },
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.advanced_safe_mode),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = state.safeModeEnabled,
                        onCheckedChange = { actions.onSafeModeChanged(it) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.advanced_tcp_route),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = state.tcpRouteEnabled,
                        onCheckedChange = { actions.onTcpRouteChanged(it) },
                    )
                }
                // ---- 越狱失败自动重试 ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "越狱失败自动重试",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "失败后自动重新越狱，最多 3 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.autoRetryJailbreak,
                        onCheckedChange = { actions.onAutoRetryChanged(it) },
                    )
                }
                Spacer(Modifier.height(4.dp))

                // 注意：此处不再提供 boot.img 导入（已移到状态卡进入的导入页）
                DialogActionRow(
                    label = stringResource(R.string.advanced_import_offsets),
                    onClick = { actions.onImportOffsets() },
                )
                DialogActionRow(
                    label = stringResource(R.string.advanced_export_offsets),
                    onClick = { actions.onExportOffsets() },
                )

                // ---- 问题反馈（跳转 QQ）----
                DialogActionRow(
                    label = "问题反馈（QQ：3283138152）",
                    onClick = { actions.onFeedback() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onDialogDismiss() }) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun DialogActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun RunConfirmDialog(state: GhostlockUiState, actions: GhostlockActions) {
    if (state.uiStyle == com.KiYY.lost.ksu.UiStyle.Glass) {
        GlassRunConfirmDialog(state, actions)
        return
    }
    AlertDialog(
        onDismissRequest = { actions.onDialogDismiss() },
        title = { Text(stringResource(R.string.confirm_run_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.info_device)}: ${state.deviceName.ifEmpty { "-" }}")
                Text("${stringResource(R.string.info_kernel)}: ${state.kernelRelease.ifEmpty { "-" }}")
                Text("${stringResource(R.string.info_soc)}: ${state.socName.ifEmpty { "-" }}")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.confirm_run_message),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                // 越狱依赖 App 前台存活，明确告知用户
                Text(
                    text = "⚠ 越狱过程中请保持 App 在前台，切换后台可能导致越狱失败。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 只走一次：onDialogConfirm 内部已根据 DialogType.CONFIRM_RUN 触发 onRun()
                actions.onDialogConfirm("run")
            }) {
                Text(stringResource(R.string.run_now))
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.onDialogDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ListDialog(state: GhostlockUiState, actions: GhostlockActions) {
    if (state.uiStyle == com.KiYY.lost.ksu.UiStyle.Glass) {
        GlassListDialog(state, actions)
        return
    }
    AlertDialog(
        onDismissRequest = { actions.onDialogDismiss() },
        title = {
            Text(
                if (state.dialogTitleRes != 0) stringResource(state.dialogTitleRes)
                else state.dialogMessage
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.dialogItems.forEachIndexed { index, item ->
                    val label = if (index < state.dialogItemResIds.size && state.dialogItemResIds[index] != 0) {
                        stringResource(state.dialogItemResIds[index])
                    } else item
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = state.dialogCurrentItemIndex == index,
                            onCheckedChange = { if (it) actions.onDialogItemSelected(index) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onDialogDismiss() }) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun InputDialog(state: GhostlockUiState, actions: GhostlockActions) {
    if (state.uiStyle == com.KiYY.lost.ksu.UiStyle.Glass) {
        GlassInputDialog(state, actions)
        return
    }
    AlertDialog(
        onDismissRequest = { actions.onDialogDismiss() },
        title = {
            Text(
                if (state.dialogTitleRes != 0) stringResource(state.dialogTitleRes)
                else state.dialogMessage
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.dialogMessageRes != 0) {
                    Text(
                        text = stringResource(state.dialogMessageRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = state.dialogInput,
                    onValueChange = { actions.onDialogInputChange(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onDialogConfirm(state.dialogInput) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.onDialogDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ==========================================================================
// 液态玻璃版对话框（与首页 GlassWidgets 同一视觉语言）
// ==========================================================================

/** 玻璃版「执行越狱确认」对话框 */
@Composable
private fun GlassRunConfirmDialog(state: GhostlockUiState, actions: GhostlockActions) {
    com.KiYY.lost.ksu.component.GlassDialog(onDismiss = { actions.onDialogDismiss() }) {
        com.KiYY.lost.ksu.component.GlassDialogTitle(stringResource(R.string.confirm_run_title))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            com.KiYY.lost.ksu.component.GlassDialogBody(
                "${stringResource(R.string.info_device)}: ${state.deviceName.ifEmpty { "-" }}"
            )
            com.KiYY.lost.ksu.component.GlassDialogBody(
                "${stringResource(R.string.info_kernel)}: ${state.kernelRelease.ifEmpty { "-" }}"
            )
            com.KiYY.lost.ksu.component.GlassDialogBody(
                "${stringResource(R.string.info_soc)}: ${state.socName.ifEmpty { "-" }}"
            )
            Spacer(Modifier.height(2.dp))
            com.KiYY.lost.ksu.component.GlassDialogBody(stringResource(R.string.confirm_run_message))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFF3E0))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "⚠ 越狱过程中请保持 App 在前台，切换后台可能导致越狱失败。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB26A00),
            )
        }
        com.KiYY.lost.ksu.component.GlassDialogButtons(
            confirmText = stringResource(R.string.run_now),
            onConfirm = { actions.onDialogConfirm("run") },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { actions.onDialogDismiss() },
        )
    }
}

/** 玻璃版「列表选择」对话框（CPU 核心 / 工作区等） */
@Composable
private fun GlassListDialog(state: GhostlockUiState, actions: GhostlockActions) {
    com.KiYY.lost.ksu.component.GlassDialog(onDismiss = { actions.onDialogDismiss() }) {
        com.KiYY.lost.ksu.component.GlassDialogTitle(
            if (state.dialogTitleRes != 0) stringResource(state.dialogTitleRes) else state.dialogMessage
        )
        Column(
            modifier = Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.dialogItems.forEachIndexed { index, item ->
                val label = if (index < state.dialogItemResIds.size && state.dialogItemResIds[index] != 0) {
                    stringResource(state.dialogItemResIds[index])
                } else item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.KiYY.lost.ksu.component.GlassTextPrimary,
                    )
                    com.KiYY.lost.ksu.component.GlassSwitch(
                        checked = state.dialogCurrentItemIndex == index,
                        onCheckedChange = { if (it) actions.onDialogItemSelected(index) },
                    )
                }
            }
        }
        com.KiYY.lost.ksu.component.GlassDialogButtons(
            confirmText = stringResource(R.string.done),
            onConfirm = { actions.onDialogDismiss() },
        )
    }
}

/** 玻璃版「文本输入」对话框 */
@Composable
private fun GlassInputDialog(state: GhostlockUiState, actions: GhostlockActions) {
    com.KiYY.lost.ksu.component.GlassDialog(onDismiss = { actions.onDialogDismiss() }) {
        com.KiYY.lost.ksu.component.GlassDialogTitle(
            if (state.dialogTitleRes != 0) stringResource(state.dialogTitleRes) else state.dialogMessage
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.dialogMessageRes != 0) {
                com.KiYY.lost.ksu.component.GlassDialogBody(stringResource(state.dialogMessageRes))
            }
            OutlinedTextField(
                value = state.dialogInput,
                onValueChange = { actions.onDialogInputChange(it) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0A84FF),
                    unfocusedBorderColor = Color(0xFFD8DCE3),
                    focusedTextColor = com.KiYY.lost.ksu.component.GlassTextPrimary,
                    unfocusedTextColor = com.KiYY.lost.ksu.component.GlassTextPrimary,
                    cursorColor = Color(0xFF0A84FF),
                ),
            )
        }
        com.KiYY.lost.ksu.component.GlassDialogButtons(
            confirmText = stringResource(R.string.confirm),
            onConfirm = { actions.onDialogConfirm(state.dialogInput) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { actions.onDialogDismiss() },
        )
    }
}