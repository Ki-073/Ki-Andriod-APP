package com.KiYY.lost.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.KiYY.lost.R
import com.KiYY.lost.domain.model.KernelSnapshot
import com.KiYY.lost.domain.model.LogTone
import com.KiYY.lost.domain.model.OffsetCandidate
import com.KiYY.lost.domain.model.OffsetImportResult
import com.KiYY.lost.domain.model.ParseResult
import com.KiYY.lost.domain.repository.GhostlockRepository
import com.KiYY.lost.domain.usecase.ExportOffsetsUseCase
import com.KiYY.lost.domain.usecase.FormatLogUseCase
import com.KiYY.lost.domain.usecase.ImportOffsetsUseCase
import com.KiYY.lost.domain.usecase.LoadKernelSnapshotUseCase
import com.KiYY.lost.domain.usecase.ParseSourceUseCase
import com.KiYY.lost.domain.usecase.PublishOffsetsUseCase
import com.KiYY.lost.domain.usecase.ReadDocumentUseCase
import com.KiYY.lost.domain.usecase.RunExploitUseCase
import com.KiYY.lost.domain.usecase.SelectCpuPairUseCase
import com.KiYY.lost.ksu.BootImportMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GhostlockEffect {
    data class PickDocument(val request: DocumentRequest) : GhostlockEffect
    data class Share(val uri: String) : GhostlockEffect
    data class Toast(val resourceId: Int) : GhostlockEffect
    /** 直接显示文案的 Toast（用于动态内容） */
    data class ToastText(val text: String) : GhostlockEffect
    data class Clipboard(val text: String) : GhostlockEffect
    data class KeepScreenAwake(val enabled: Boolean) : GhostlockEffect
    /** 越狱成功后未授予 root 权限 → 提示前往 KernelSU 管理器授权 */
    data object PromptRootPermission : GhostlockEffect
}

enum class DocumentRequest { ImportOffsets, BootImage, XblImage }

class GhostlockViewModel(
    private val repository: GhostlockRepository,
) : ViewModel() {
    private val effectChannel = Channel<GhostlockEffect>(Channel.BUFFERED)
    private val mutableState = MutableStateFlow(GhostlockUiState())
    private var initialized = false
    private var running = false
    private val loadKernelSnapshot = LoadKernelSnapshotUseCase(repository)
    private val selectCpuPairUseCase = SelectCpuPairUseCase(repository)
    private val importOffsetsUseCase = ImportOffsetsUseCase(repository)
    private val parseSourceUseCase = ParseSourceUseCase(repository)
    private val exportOffsetsUseCase = ExportOffsetsUseCase(repository)
    private val publishOffsetsUseCase = PublishOffsetsUseCase(repository)
    private val readDocumentUseCase = ReadDocumentUseCase(repository)
    private val runExploitUseCase = RunExploitUseCase(repository)
    private val formatLog = FormatLogUseCase()

    val state = mutableState.asStateFlow()
    val effects = effectChannel.receiveAsFlow()

    private var kernelSnapshot: KernelSnapshot? = null
    private var pendingParseWithXbl = false
    private var pendingBootPath: String? = null
    private var exportCandidates: List<OffsetCandidate> = emptyList()
    private var pendingConfirmation: PendingConfirmation? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        // 恢复上次保存的 UI 风格（Material / Glass）与自动越狱开关
        runCatching {
            val glass = repository.getUiStyle()
            val autoBoot = repository.getAutoJailbreakOnBoot()
            val autoRetry = repository.getAutoRetryJailbreak()
            mutableState.update {
                it.copy(
                    uiStyle = if (glass) com.KiYY.lost.ksu.UiStyle.Glass
                    else com.KiYY.lost.ksu.UiStyle.Material,
                    autoJailbreakOnBoot = autoBoot,
                    autoRetryJailbreak = autoRetry,
                )
            }
        }
        viewModelScope.launch { refreshSnapshot() }
    }

    fun toggleAdvanced() {
        mutableState.update {
            it.copy(
                dialogVisible = true,
                dialogType = DialogType.ADVANCED,
            )
        }
    }

    /** Opens the confirmation dialog before actually running the exploit. */
    fun onRequestRun() {
        if (running) return
        mutableState.update {
            it.copy(
                dialogVisible = true,
                dialogType = DialogType.CONFIRM_RUN,
            )
        }
    }

    fun selectCpuPair(index: Int) {
        val snapshot = kernelSnapshot ?: return
        if (index !in snapshot.cpuPairs.indices) return
        selectCpuPairUseCase(index)
        kernelSnapshot = snapshot.copy(selectedCpuPair = index)
        mutableState.update { it.copy(cpuPairIndex = index) }
    }

    /** 高级选项：展开/折叠 CPU 核心列表 */
    fun toggleCpuExpanded() {
        mutableState.update { it.copy(cpuExpanded = !it.cpuExpanded) }
    }

    /** 右上角切换 UI 风格（Material ↔ Glass），并持久化 */
    fun toggleUiStyle() {
        mutableState.update {
            val next = if (it.uiStyle == com.KiYY.lost.ksu.UiStyle.Material) {
                com.KiYY.lost.ksu.UiStyle.Glass
            } else {
                com.KiYY.lost.ksu.UiStyle.Material
            }
            // 落盘，重启后恢复
            runCatching { repository.setUiStyle(next == com.KiYY.lost.ksu.UiStyle.Glass) }
            it.copy(uiStyle = next)
        }
    }

    fun toggleSafeMode(enabled: Boolean) {
        repository.setSafeModeEnabled(enabled)
        mutableState.update { it.copy(safeModeEnabled = enabled) }
    }

    fun toggleTcpRoute(enabled: Boolean) {
        repository.setTcpRouteEnabled(enabled)
        mutableState.update { it.copy(tcpRouteEnabled = enabled) }
    }

    /**
     * 主动申请 Root 授权。
     *
     * 原理：跑一次 `su -c id`。
     *  - 若 App 已在 KSU allowlist → 直接返回 uid=0
     *  - 若不在 → KSU 会**弹出授权请求框**，用户点「允许」后即写入 allowlist，
     *    此后永久生效（开机自启的越狱流程也能正常调用 ksud）。
     *
     * 这是「越狱成功但后续 root 操作卡住」的根治手段：
     * 越狱用的 libghostlock.so 本身不需要 root，但成功后要调 ksud
     * 关内核保护 / 写 offsets，这些必须 root。先把这个授权拿到手。
     */
    fun requestRootPermission() {
        if (running) return
        viewModelScope.launch(Dispatchers.IO) {
            appendLog("==== 申请 Root 授权 ====")
            val ok = runCatching { repository.requestRoot() }.getOrDefault(false)
            if (ok) {
                appendLog("root: 已获得授权（uid=0），后续越狱可直接调用 ksud")
                send(GhostlockEffect.ToastText("已获得 Root 权限"))
            } else {
                appendLog("root: 未获得授权 —— 请在弹框中点「允许」，或到 Root 管理器中手动授予")
                send(GhostlockEffect.ToastText("未获得 Root 权限，请在弹出的授权框中点「允许」"))
            }
        }
    }

    /** 失败自动重试开关 */
    fun toggleAutoRetry(enabled: Boolean) {
        runCatching { repository.setAutoRetryJailbreak(enabled) }
        mutableState.update { it.copy(autoRetryJailbreak = enabled) }
    }
    /**
     * 供以后可能的延迟操作使用：判断内核 snapshot 是否已就绪。
     */
    fun isSnapshotReady(): Boolean = kernelSnapshot != null

    fun onRun(force: Boolean = false) {
        val snapshot = kernelSnapshot ?: return
        if (!snapshot.kernelSupported) {
            if (beginOperation()) {
                appendLog("result: exploit chain unsupported by this kernel")
                endOperation()
            }
            return
        }
        // 幂等保护：**仅在本次操作真正进行中**时拦截（防止重复点击 / 广播重复投递）。
        // ⚠ 注意：这里绝不能用 snapshot.jailbroken 判断 —— 它表示的是「su 是否可用」
        //    （即 KSU 自身的 root），与 GhostLock 的临时越狱状态无关。
        //    若用它做判据，App 一旦拿到 root 授权，点执行按钮就会被永久跳过。
        //    GhostLock 提权是临时的，重启后必须重新越狱，因此只按 workState 拦截。
        if (!force && state.value.workState == WorkState.WORKING) {
            appendLog("skip: 正在工作中，无需重复触发")
            return
        }
        val pair = snapshot.cpuPairs.getOrNull(snapshot.selectedCpuPair) ?: return
        if (!beginOperation()) return
        send(GhostlockEffect.KeepScreenAwake(true))
        appendLog("==== start ====")
        if (force) appendLog("mode: 开机自动越狱（强制重新越狱）")
        // 越狱过程依赖 App 前台存活（boot 解析 / 内存布局探测 / exploit 执行）
        appendLog("请在越狱过程中保持前台，以保证越狱成功！")
        appendLog("cpu pair: ${snapshot.cpuPairLabels.getOrElse(snapshot.selectedCpuPair) { pair.toString() }}")
        val retry = state.value.autoRetryJailbreak
        viewModelScope.launch(Dispatchers.IO) {
            var attempt = 1
            try {
                while (true) {
                    if (attempt > 1) {
                        appendLog("---- 自动重试 第 $attempt 次 / 3 ----")
                    }
                    val code = runExploitUseCase(pair, ::appendLog)
                    appendLog(if (code == 0) "result: exploit completed" else "result: exploit failed (exit code=$code)")
                    appendLog("exit code=$code")

                    if (code == 0) {
                        // 成功：写入持久化标记 → 重启 App 后依然保持“工作中”
                        repository.markJailbroken()
                        mutableState.update { it.copy(workState = WorkState.WORKING) }
                        refreshSnapshot()
                        // ---- 越狱成功后：检测 App 是否已被授予 root 权限 ----
                        // 越狱用的 libghostlock.so 不需要 root，但「检测越狱状态」
                        // 以及后续调 ksud 都需要 root。这里做一次**非阻塞快速检测**
                        // （只等 3 秒，不弹授权框），没授权就提示用户去 KSU 管理器授予。
                        val hasRoot = runCatching { repository.hasRootPermission() }.getOrDefault(false)
                        if (hasRoot) {
                            appendLog("root: 已获得超级用户权限")
                        } else {
                            appendLog("root: 未授予超级用户权限 —— 已提示用户前往 KernelSU 管理器授权")
                            send(GhostlockEffect.PromptRootPermission)
                        }
                        break
                    }

                    // 失败：判断是否自动重试（最多 3 次）
                    if (retry && attempt < MAX_JAILBREAK_ATTEMPTS) {
                        attempt++
                        appendLog("retry: 将在 2 秒后重试…")
                        kotlinx.coroutines.delay(2000)
                        continue
                    }
                    if (retry) appendLog("retry: 已达最大重试次数（$MAX_JAILBREAK_ATTEMPTS），停止")
                    mutableState.update { it.copy(workState = WorkState.IDLE) }
                    break
                }
            } finally {
                endOperation()
                send(GhostlockEffect.KeepScreenAwake(false))
            }
        }
    }

    companion object {
        /** 越狱最大尝试次数（含首次，失败后自动重试至多 3 次） */
        const val MAX_JAILBREAK_ATTEMPTS = 3
    }

    fun onWorkspaceSelected(workspace: Workspace) {
        mutableState.update { it.copy(workspace = workspace) }
    }

    fun onCloseExecutionSheet() {
        if (running && !state.value.executionSheetDismissible) return
        mutableState.update { it.copy(executionSheetVisible = false) }
    }

    fun copyLogs() {
        val text = state.value.logLines.joinToString(separator = "") { it.text }
        send(GhostlockEffect.Clipboard(text))
        send(GhostlockEffect.Toast(R.string.copied))
    }

    fun importOffsets() = send(GhostlockEffect.PickDocument(DocumentRequest.ImportOffsets))

    fun parseOffsets() {
        exportCandidates = emptyList()
        // 直接从本地文件选择器导入 boot.img（不再走 URL / 选项弹窗）
        pickBoot(withXbl = false)
    }

    // ======================================================================
    // boot 导入页（对应 KSU 安装页）
    // ======================================================================

    /** 点击状态卡主体 → 进入 boot 导入页 */
    fun openBootPage() {
        mutableState.update {
            it.copy(
                bootPageVisible = true,
                bootMethod = it.bootMethod ?: BootImportMethod.SelectLocalFile,
            )
        }
    }

    fun closeBootPage() {
        mutableState.update { it.copy(bootPageVisible = false) }
    }

    // ======================================================================
    // 越狱进度弹窗
    // ======================================================================

    /** 展开 / 收起控制台日志 */
    fun toggleConsoleExpand() {
        mutableState.update { it.copy(consoleExpanded = !it.consoleExpanded) }
    }

    /** 关闭进度弹窗（运行中不允许关闭） */
    fun dismissProgressDialog() {
        if (running) return
        mutableState.update {
            it.copy(
                progressDialogDismissed = true,
                executionSheetVisible = false,
            )
        }
    }

    fun selectBootMethod(method: BootImportMethod) {
        mutableState.update { it.copy(bootMethod = method) }
    }

    fun bootPickFile() {
        // 文件选择器回调走 onDocumentResult(DocumentRequest.BootImage)
        pendingParseWithXbl = false
        exportCandidates = emptyList()
        send(GhostlockEffect.PickDocument(DocumentRequest.BootImage))
    }

    fun bootClearFile() {
        pendingBootPath = null
        mutableState.update {
            it.copy(
                bootFileName = null,
                bootFileStaged = false,
            )
        }
    }

    fun bootToggleAdvanced() {
        mutableState.update { it.copy(bootAdvancedShown = !it.bootAdvancedShown) }
    }

    fun bootToggleForceBackup(enabled: Boolean) {
        mutableState.update { it.copy(bootForceBackup = enabled) }
    }

    /** 高级选项：缓存解析数据开关 */
    fun bootToggleCacheParseData(enabled: Boolean) {
        mutableState.update { it.copy(bootCacheParseData = enabled) }
        // 同步到仓库，供解析/越狱流程读取
        runCatching { repository.setCacheParseData(enabled) }
    }

    /** 下一步：开始解析（回到主界面并展示进度） */
    fun bootNext() {
        val bootPath = pendingBootPath
        if (bootPath == null) {
            send(GhostlockEffect.Toast(R.string.parse_failed))
            return
        }
        mutableState.update { it.copy(bootPageVisible = false) }
        viewModelScope.launch { runParse(bootPath) }
    }

    fun promptParseUrl() {
        mutableState.update {
            it.copy(
                dialogVisible = true,
                dialogType = DialogType.INPUT,
                dialogTitleRes = R.string.parse_url_title,
                dialogMessageRes = R.string.parse_url_hint,
                dialogInput = "",
            )
        }
    }

    fun exportOffsets() {
        viewModelScope.launch {
            exportCandidates = withContext(Dispatchers.IO) { exportOffsetsUseCase() }
            if (exportCandidates.isEmpty()) {
                send(GhostlockEffect.Toast(R.string.export_none))
            } else {
                mutableState.update {
                    it.copy(
                        dialogVisible = true,
                        dialogType = DialogType.LIST,
                        dialogTitleRes = R.string.export_title,
                        dialogItems = exportCandidates.map(OffsetCandidate::release),
                        dialogItemResIds = emptyList(),
                        dialogCurrentItemIndex = exportCandidates.indexOfFirst { offsetCandidate ->
                            offsetCandidate.release == kernelSnapshot?.kernelRelease
                        },
                    )
                }
            }
        }
    }

    fun onDocumentResult(request: DocumentRequest, uri: String) {
        when (request) {
            DocumentRequest.ImportOffsets -> importDocument(uri)
            DocumentRequest.BootImage -> stageBoot(uri)
            DocumentRequest.XblImage -> stageXbl(uri)
        }
    }

    fun onDialogItemSelected(index: Int) {
        val candidates = exportCandidates
        dismissDialog()
        if (candidates.isNotEmpty()) {
            candidates.getOrNull(index)?.let(::publish)
            exportCandidates = emptyList()
            return
        }
        when (index) {
            0 -> pickBoot(withXbl = false)
            1 -> pickBoot(withXbl = true)
        }
    }

    fun onDialogInputChange(value: String) = mutableState.update { it.copy(dialogInput = value) }

    fun onDialogConfirm(value: String) {
        val dialogType = state.value.dialogType
        dismissDialog(clearConfirmation = false)
        when (dialogType) {
            DialogType.INPUT -> parseUrl(value)
            DialogType.CONFIRM_RUN -> onRun()
            DialogType.NONE, DialogType.LIST, DialogType.ADVANCED -> Unit
        }
    }

    fun onDialogDismiss() = dismissDialog()

    fun onDialogDismissFinished() {
        if (!state.value.dialogVisible) {
            clearDialog()
        }
    }

    override fun onCleared() {
        repository.close()
        effectChannel.close()
        super.onCleared()
    }

    private suspend fun refreshSnapshot() {
        val snapshot = withContext(Dispatchers.IO) { loadKernelSnapshot() }
        val canExport = withContext(Dispatchers.IO) { exportOffsetsUseCase().isNotEmpty() }
        val diag = withContext(Dispatchers.IO) { runCatching { repository.diagnose() }.getOrDefault(emptyList()) }
        diag.forEach { appendLog(it) }
        kernelSnapshot = snapshot
        mutableState.update {
            it.copy(
                deviceName = snapshot.deviceName,
                kernelRelease = snapshot.kernelRelease,
                socName = snapshot.socName,
                fingerprint = android.os.Build.FINGERPRINT.orEmpty(),
                kernelSupported = snapshot.kernelSupported,
                cpuPairLabels = snapshot.cpuPairLabels,
                cpuPairIndex = snapshot.selectedCpuPair,
                safeModeEnabled = snapshot.safeModeEnabled,
                tcpRouteEnabled = snapshot.tcpRouteEnabled,
                compact = snapshot.compact,
                seLinux = snapshot.seLinux,
                seccomp = snapshot.seccomp,
                exportVisible = canExport,
                // offsets 已存在（内置或已解析）→ boot 视为已解析
                bootParsed = canExport,
                // ⚠ 关键（v1.65）：把 snapshot.jailbroken 同步给 UI。
                // snapshot.jailbroken 现在是**真探测**（su -c id 拿到 uid=0），
                // 代表「KSU root 已授予」这一持久状态。
                // UI 的「越狱状态」= workState==WORKING || jailbroken，二者取或。
                // 这样即使用户重启了 App（workState 回 IDLE），只要 root 还在，
                // 状态卡依然显示「工作中」，不再出现「授予了 root 却显示未工作」。
                jailbroken = snapshot.jailbroken,
            )
        }
    }

    private fun importDocument(uri: String) {
        if (!beginOperation()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = readDocumentUseCase(uri)
                handleImportResult(importOffsetsUseCase(json), json)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appendLog("import offsets failed: ${error.message}")
                appendLog("result: import failed")
                send(GhostlockEffect.Toast(R.string.import_failed))
            } finally {
                endOperation()
            }
        }
    }

    private suspend fun handleImportResult(result: OffsetImportResult, json: String) {
        when (result) {
            is OffsetImportResult.RequiresOverwrite -> {
                pendingConfirmation = PendingConfirmation.Import(json)
                showOverwriteDialog(result.releases)
            }

            is OffsetImportResult.Imported -> {
                refreshSnapshot()
                appendLog("offsets.json imported: ${result.releases.joinToString()}")
                appendLog("result: offsets imported successfully")
                send(GhostlockEffect.Toast(R.string.import_success))
            }

            OffsetImportResult.AlreadyPresent -> {
                appendLog("result: offsets already present")
                send(GhostlockEffect.Toast(R.string.offsets_already_exist))
            }
            is OffsetImportResult.Failed -> {
                appendLog("import offsets failed: ${result.reason}")
                appendLog("result: import failed")
                send(GhostlockEffect.Toast(R.string.import_failed))
            }
        }
    }

    private fun pickBoot(withXbl: Boolean) {
        pendingParseWithXbl = withXbl
        if (withXbl) send(GhostlockEffect.Toast(R.string.parse_pick_boot_hint))
        send(GhostlockEffect.PickDocument(DocumentRequest.BootImage))
    }

    private fun stageBoot(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bootPath = readDocumentUseCase.cache(uri, "boot.img")
                pendingBootPath = bootPath
                val displayName = uri.substringAfterLast('/').substringAfterLast('%').ifEmpty { "boot.img" }
                mutableState.update {
                    it.copy(
                        bootFileName = displayName,
                        bootFileStaged = true,
                    )
                }
                appendLog("boot.img ready: $bootPath")
                if (pendingParseWithXbl) {
                    send(GhostlockEffect.Toast(R.string.parse_pick_xbl_hint))
                    send(GhostlockEffect.PickDocument(DocumentRequest.XblImage))
                }
                // 注意：不再自动解析。由用户在 boot 导入页点“下一步”触发。
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appendLog("parse error: ${error.message}")
                appendLog("result: parse failed")
                send(GhostlockEffect.Toast(R.string.parse_failed))
            }
        }
    }

    private fun stageXbl(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bootPath = requireNotNull(pendingBootPath) { "boot.img is not staged" }
                val xblPath = readDocumentUseCase.cache(uri, "xbl_config.img")
                appendLog("xbl_config.img ready: $xblPath")
                runParse(bootPath, xblPath)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appendLog("parse error: ${error.message}")
                appendLog("result: parse failed")
                send(GhostlockEffect.Toast(R.string.parse_failed))
            }
        }
    }

    private fun parseUrl(value: String) {
        val url = value.trim()
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            appendLog("error: invalid OTA URL: $url")
            appendLog("result: parse failed")
            send(GhostlockEffect.Toast(R.string.parse_failed_url))
            return
        }
        appendLog("parse OTA: $url")
        viewModelScope.launch(Dispatchers.IO) { runParse(url) }
    }

    private suspend fun runParse(input: String, xblPath: String? = null, overwrite: Boolean = false) {
        if (!beginOperation()) return
        try {
            when (val result = parseSourceUseCase(input, xblPath, overwrite, ::appendLog)) {
                is ParseResult.RequiresOverwrite -> {
                    pendingConfirmation = PendingConfirmation.Parse(input, xblPath)
                    showOverwriteDialog(result.releases)
                }

                is ParseResult.Parsed -> {
                    refreshSnapshot()
                    mutableState.update { it.copy(bootParsed = true) }
                    appendLog("offsets.json written: ${result.releases.joinToString()}")
                    appendLog("result: offsets parsed successfully")
                    send(GhostlockEffect.Toast(R.string.parse_success))
                }

                ParseResult.AlreadyPresent -> {
                    mutableState.update { it.copy(bootParsed = true) }
                    appendLog("result: offsets already present")
                    send(GhostlockEffect.Toast(R.string.offsets_already_exist))
                }
                is ParseResult.Failed -> {
                    result.reason?.let { appendLog("parse failed: $it") }
                    appendLog("result: ${parseFailureResult(result.code)}")
                    send(GhostlockEffect.Toast(parseFailureToast(result.code)))
                }
            }
        } finally {
            endOperation()
            // 解析结束后关闭进度弹窗：解析不是越狱，不应残留“正在越狱”状态
            mutableState.update {
                it.copy(
                    executionSheetVisible = false,
                    progressDialogDismissed = true,
                    consoleExpanded = false,
                )
            }
        }
    }

    fun onOverwriteConfirm() {
        mutableState.update { it.copy(overwriteDialogVisible = false, overwriteMessage = "") }
        confirmPendingOperation()
    }

    fun onOverwriteDismiss() {
        pendingConfirmation = null
        running = false
        appendLog("result: overwrite cancelled")
        mutableState.update {
            it.copy(
                overwriteDialogVisible = false,
                overwriteMessage = "",
                running = false,
                executionSheetDismissible = true,
            )
        }
    }

    private fun confirmPendingOperation() {
        val confirmation = pendingConfirmation ?: return
        pendingConfirmation = null
        when (confirmation) {
            is PendingConfirmation.Import -> {
                if (!beginOperation()) return
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        handleImportResult(importOffsetsUseCase.overwrite(confirmation.json), confirmation.json)
                    } finally {
                        endOperation()
                    }
                }
            }

            is PendingConfirmation.Parse -> viewModelScope.launch(Dispatchers.IO) {
                runParse(confirmation.input, confirmation.xblPath, overwrite = true)
            }
        }
    }

    private fun publish(candidate: OffsetCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = publishOffsetsUseCase(candidate)
                appendLog("exported offsets: offsets-${candidate.release}.json")
                send(GhostlockEffect.Share(uri))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appendLog("export offsets failed: ${error.message}")
                send(GhostlockEffect.Toast(R.string.export_failed))
            }
        }
    }

    private fun showOverwriteDialog(releases: List<String>) {
        mutableState.update {
            it.copy(
                overwriteDialogVisible = true,
                overwriteMessage = releases.joinToString("\n"),
            )
        }
    }

    private fun dismissDialog(clearConfirmation: Boolean = true) {
        if (clearConfirmation) pendingConfirmation = null
        mutableState.update {
            it.copy(
                dialogVisible = false,
            )
        }
    }

    private fun clearDialog() {
        mutableState.update {
            it.copy(
                dialogVisible = false,
                dialogType = DialogType.NONE,
                dialogTitleRes = 0,
                dialogMessage = "",
                dialogMessageRes = 0,
                dialogItems = emptyList(),
                dialogItemResIds = emptyList(),
                dialogCurrentItemIndex = -1,
                dialogInput = "",
            )
        }
    }

    private fun appendLog(line: String) {
        val entry = formatLog(line)
        val uiLine = GhostlockLogLine(entry.text, toneColor(entry.tone))
        mutableState.update { it.copy(logLines = it.logLines + uiLine) }
    }

    private fun beginOperation(): Boolean {
        if (running) return false
        running = true
        mutableState.update {
            it.copy(
                running = true,
                executionSheetVisible = true,
                executionSheetDismissible = false,
                progressDialogDismissed = false,
                consoleExpanded = false,
            )
        }
        return true
    }

    private fun endOperation() {
        running = false
        mutableState.update { it.copy(running = false, executionSheetDismissible = true) }
    }

    private fun send(effect: GhostlockEffect) {
        effectChannel.trySend(effect)
    }

    private fun toneColor(tone: LogTone): Int = when (tone) {
        LogTone.Error -> 0xFFFF6B6B.toInt()
        LogTone.Success -> 0xFF5FD68A.toInt()
        LogTone.Warning -> 0xFFFFC94D.toInt()
        LogTone.Progress -> 0xFF60A5FA.toInt()
        LogTone.Default -> -1
    }

    private fun parseFailureToast(code: Int): Int = when (code) {
        3, 4 -> R.string.parse_failed_route
        5 -> R.string.parse_failed_kallsyms
        6 -> R.string.parse_failed_fixed
        -1 -> R.string.parse_timeout
        else -> R.string.parse_failed
    }

    private fun parseFailureResult(code: Int): String = when (code) {
        3, 4 -> "exploit chain unsupported by this kernel"
        5 -> "kernel symbol table could not be recovered"
        6 -> "kernel has fixed the vulnerability"
        -1 -> "parse timed out"
        else -> "parse failed"
    }

    private sealed interface PendingConfirmation {
        data class Import(val json: String) : PendingConfirmation
        data class Parse(val input: String, val xblPath: String?) : PendingConfirmation
    }
}