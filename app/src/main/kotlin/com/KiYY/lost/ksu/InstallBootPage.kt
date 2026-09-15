package com.KiYY.lost.ksu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

// ==========================================================================
// Ported from tiann/KernelSU
//   manager/.../ui/screen/install/InstallMaterial.kt   (页面骨架)
//   manager/.../ui/screen/install/InstallUiState.kt    (状态/动作)
// 用途：GhostLock 的 boot.img 导入 / 解析页（对应 KSU 的“安装/修补”页）
// ==========================================================================

import com.KiYY.lost.ksu.component.ExpressiveScaffold
import com.KiYY.lost.ksu.component.SegmentedColumn
import com.KiYY.lost.ksu.component.SegmentedListItem
import com.KiYY.lost.ksu.component.SegmentedRadioItem
import com.KiYY.lost.ksu.component.SegmentedCheckboxItem
import com.KiYY.lost.ksu.component.SegmentedItemContainer
import com.KiYY.lost.ksu.component.TopBarBackButton
import com.KiYY.lost.ksu.component.expressiveTopAppBarColors

/** 导入方式（对应 KSU 的 InstallMethod） */
enum class BootImportMethod {
    SelectLocalFile,   // 选择本地 boot.img
}

data class InstallBootState(
    val method: BootImportMethod? = null,
    val selectedFileName: String? = null,
    val fileStaged: Boolean = false,
    val slotSuffix: String = "",
    val advancedShown: Boolean = false,
    val forceBackup: Boolean = false,
    /** 缓存解析数据：开启后本次解析结果缓存，后续越狱直接用缓存 */
    val cacheParseData: Boolean = false,
    val busy: Boolean = false,
)

data class InstallBootActions(
    val onBack: () -> Unit = {},
    val onSelectMethod: (BootImportMethod) -> Unit = {},
    val onPickFile: () -> Unit = {},
    val onClearFile: () -> Unit = {},
    val onToggleAdvanced: () -> Unit = {},
    val onToggleForceBackup: (Boolean) -> Unit = {},
    val onToggleCacheParseData: (Boolean) -> Unit = {},
    val onNext: () -> Unit = {},
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InstallBootPage(
    state: InstallBootState,
    actions: InstallBootActions,
    glassStyle: Boolean = false,
) {
    if (glassStyle) {
        GlassInstallBootPage(state = state, actions = actions)
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = { InstallTopBar(onBack = actions.onBack, scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            // ---- 选择安装方式（对应 KSU SelectInstallMethod）----
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf(
                    {
                        SegmentedRadioItem(
                            title = "选择本地 boot.img",
                            summary = "从设备存储导入 boot.img 并解析偏移",
                            selected = state.method == BootImportMethod.SelectLocalFile,
                            onClick = { actions.onSelectMethod(BootImportMethod.SelectLocalFile) },
                        )
                    },
                )
            )

            // ---- 文件选择行（对应 KSU 的 upload lkm 行）----
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            leadingContent = {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, null)
                            },
                            headlineContent = { Text("选择 boot.img 文件") },
                            supportingContent = {
                                Text(state.selectedFileName ?: "尚未选择文件")
                            },
                            trailingContent = {
                                if (state.fileStaged) {
                                    IconButton(onClick = actions.onClearFile) {
                                        Icon(Icons.Filled.Close, contentDescription = "取消")
                                    }
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = actions.onPickFile,
                        )
                    }
                )
            )

            // ---- 高级选项（对应 KSU 的 advancedOptions 折叠区）----
            // 折叠时不显示任何选项内容
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                content = buildList<@Composable () -> Unit> {
                    add {
                        SegmentedListItem(
                            headlineContent = { Text("高级选项") },
                            supportingContent = { Text(if (state.advancedShown) "收起" else "展开") },
                            onClick = actions.onToggleAdvanced,
                        )
                    }
                    if (state.advancedShown) {
                        add {
                            SegmentedCheckboxItem(
                                title = "缓存解析数据",
                                summary = "解析后缓存偏移数据，后续越狱直接使用缓存；再次解析则更新缓存",
                                checked = state.cacheParseData,
                                onCheckedChange = actions.onToggleCacheParseData,
                            )
                        }
                        add {
                            SegmentedCheckboxItem(
                                title = "强制备份原始偏移",
                                summary = "解析前备份当前内核的 offsets 表",
                                checked = state.forceBackup,
                                onCheckedChange = actions.onToggleForceBackup,
                            )
                        }
                    }
                }
            )

            // ---- 下一步（对应 KSU 的 install_next 按钮）----
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                enabled = state.method != null && !state.busy,
                onClick = actions.onNext,
            ) { Text(if (state.busy) "解析中…" else "下一步") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InstallTopBar(
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        title = { Text("导入并解析 boot") },
        navigationIcon = { TopBarBackButton(onClick = onBack) },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

// ==========================================================================
// 液态玻璃版 boot 导入 / 解析页（与首页 Glass 视觉统一）
// ==========================================================================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GlassInstallBootPage(
    state: InstallBootState,
    actions: InstallBootActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    com.KiYY.lost.ksu.component.GlassBackdrop {
        androidx.compose.material3.Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text("导入并解析 boot") },
                    navigationIcon = {
                        com.KiYY.lost.ksu.component.GlassBackButton(onClick = actions.onBack)
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        titleContentColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                        navigationIconContentColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                    ),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- 选择安装方式 ----
                com.KiYY.lost.ksu.component.GlassSurface(corner = 26) {
                    com.KiYY.lost.ksu.component.GlassRadioRow(
                        title = "选择本地 boot.img",
                        summary = "从设备存储导入 boot.img 并解析偏移",
                        selected = state.method == BootImportMethod.SelectLocalFile,
                        onClick = { actions.onSelectMethod(BootImportMethod.SelectLocalFile) },
                    )
                }

                // ---- 文件选择行 ----
                com.KiYY.lost.ksu.component.GlassSurface(corner = 26) {
                    com.KiYY.lost.ksu.component.GlassRow(
                        title = "选择 boot.img 文件",
                        subtitle = state.selectedFileName ?: "尚未选择文件",
                        icon = Icons.AutoMirrored.Filled.DriveFileMove,
                        onClick = { if (!state.fileStaged) actions.onPickFile() },
                    )
                    if (state.fileStaged) {
                        com.KiYY.lost.ksu.component.GlassDivider()
                        com.KiYY.lost.ksu.component.GlassRow(
                            title = "取消选择",
                            subtitle = "清除当前已暂存的 boot.img",
                            icon = Icons.Filled.Close,
                            onClick = actions.onClearFile,
                        )
                    }
                }

                // ---- 高级选项 ----
                com.KiYY.lost.ksu.component.GlassSurface(corner = 26) {
                    com.KiYY.lost.ksu.component.GlassRow(
                        title = "高级选项",
                        subtitle = if (state.advancedShown) "收起" else "展开",
                        icon = Icons.Filled.Tune,
                        onClick = actions.onToggleAdvanced,
                    )
                    if (state.advancedShown) {
                        com.KiYY.lost.ksu.component.GlassDivider()
                        com.KiYY.lost.ksu.component.GlassCheckRow(
                            title = "缓存解析数据",
                            summary = "解析后缓存偏移数据，后续越狱直接使用缓存；再次解析则更新缓存",
                            checked = state.cacheParseData,
                            onCheckedChange = actions.onToggleCacheParseData,
                        )
                        com.KiYY.lost.ksu.component.GlassDivider()
                        com.KiYY.lost.ksu.component.GlassCheckRow(
                            title = "强制备份原始偏移",
                            summary = "解析前备份当前内核的 offsets 表",
                            checked = state.forceBackup,
                            onCheckedChange = actions.onToggleForceBackup,
                        )
                    }
                }

                // ---- 下一步 ----
                com.KiYY.lost.ksu.component.GlassPrimaryButton(
                    text = if (state.busy) "解析中…" else "下一步",
                    enabled = state.method != null && !state.busy,
                    onClick = actions.onNext,
                )
                androidx.compose.foundation.layout.Spacer(
                    Modifier.height(24.dp)
                )
            }
        }
    }
}