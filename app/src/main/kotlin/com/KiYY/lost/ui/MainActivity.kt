package com.KiYY.lost.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.KiYY.lost.GhostlockApplication
import com.KiYY.lost.R
import com.KiYY.lost.ksu.BootImportMethod

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<GhostlockViewModel> {
        viewModelFactory {
            initializer { GhostlockViewModel((application as GhostlockApplication).createRepository()) }
        }
    }

    private var pendingDocumentRequest: DocumentRequest? = null
    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val request = pendingDocumentRequest
        pendingDocumentRequest = null
        if (uri != null && request != null) viewModel.onDocumentResult(request, uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initialize()
        setContent {
            GhostlockRoute(viewModel, ::handleEffect)
        }
        setupSystemBars()
    }

    private fun handleEffect(effect: GhostlockEffect) {
        when (effect) {
            is GhostlockEffect.PickDocument -> {
                pendingDocumentRequest = effect.request
                documentPicker.launch(arrayOf("*/*"))
            }

            is GhostlockEffect.Share -> shareOffsets(effect.uri.toUri())
            is GhostlockEffect.Toast -> Toast.makeText(this, effect.resourceId, Toast.LENGTH_SHORT).show()
            is GhostlockEffect.ToastText -> Toast.makeText(this, effect.text, Toast.LENGTH_LONG).show()
            is GhostlockEffect.Clipboard -> {
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("ghostlock-log", effect.text))
            }

            is GhostlockEffect.KeepScreenAwake -> if (effect.enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            // ---- 越狱成功后：未授予 root 权限的引导 ----
            is GhostlockEffect.PromptRootPermission -> showRootPermissionDialog()
        }
    }

    /**
     * 越狱成功后检测到 App 未被授予 root 权限时弹出。
     *
     * 为什么需要：越狱本身（libghostlock.so）不需要 root，但
     *  - 「检测越狱状态」需要读内核态信息
     *  - 后续调 ksud（关内核保护 / 写 offsets / 开机自启）需要 root
     * 因此建议用户去 KernelSU 管理器授予超级用户权限。
     */
    private fun showRootPermissionDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("授予超级用户权限")
            .setMessage("推荐您在 KernelSU 管理器内授予本 App 超级用户权限，以便检测越狱状态。")
            .setPositiveButton("打开 KernelSU") { _, _ ->
                val ok = openKsuManager()
                if (!ok) {
                    Toast.makeText(this, "未找到 KernelSU 管理器，请手动打开", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    /** 打开已安装的 KernelSU 管理器（兼容各分支包名） */
    private fun openKsuManager(): Boolean {
        val candidates = listOf(
            "com.sukisu.ultra",          // SukiSU Ultra
            "com.resukisu.resukisu",     // ReSukiSU
            "me.weishu.kernelsu",        // 官方 KernelSU
            "me.weishu.kernelsu.pr",     // 官方 Preview
            "com.kowx712.supermanager",  // SuperManager
        )
        for (pkg in candidates) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return runCatching {
                    startActivity(launchIntent)
                    true
                }.getOrDefault(false)
            }
        }
        // 兜底：打开应用详情页（用户可从这里进管理器）
        return com.KiYY.lost.util.AutoStartHelper.openAppDetail(this)
    }

    private fun shareOffsets(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export_share)))
    }

    private fun setupSystemBars() {
        val controller = window.decorView.windowInsetsController ?: return
        val lightStatus = if (resources.getBoolean(R.bool.window_light_status_bar)) {
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        } else {
            0
        }
        val lightNavigation = if (resources.getBoolean(R.bool.window_light_navigation_bar)) {
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        } else {
            0
        }
        controller.setSystemBarsAppearance(
            lightStatus or lightNavigation,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }
}

@Composable
private fun GhostlockRoute(
    viewModel: GhostlockViewModel,
    onEffect: (GhostlockEffect) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 供匿名 GhostlockActions 内做系统跳转（QQ / 浏览器）使用
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect(onEffect)
    }
    GhostlockApp(
        state = state,
        actions = object : GhostlockActions {
            override fun onRun() = viewModel.onRun()
            override fun onRequestRun() = viewModel.onRequestRun()
            override fun onCloseExecutionSheet() = viewModel.onCloseExecutionSheet()
            override fun onToggleAdvanced() = viewModel.toggleAdvanced()
            override fun onCopyLogs() = viewModel.copyLogs()
            override fun onImportOffsets() = viewModel.importOffsets()
            override fun onParseOta() = viewModel.promptParseUrl()
            override fun onParseImage() = viewModel.parseOffsets()
            override fun onExportOffsets() = viewModel.exportOffsets()
            override fun onCpuPairSelected(index: Int) = viewModel.selectCpuPair(index)
            override fun onToggleCpuExpanded() = viewModel.toggleCpuExpanded()
            override fun onSafeModeChanged(enabled: Boolean) = viewModel.toggleSafeMode(enabled)
            override fun onTcpRouteChanged(enabled: Boolean) = viewModel.toggleTcpRoute(enabled)
            override fun onAutoRetryChanged(enabled: Boolean) = viewModel.toggleAutoRetry(enabled)
            override fun onRequestRoot() = viewModel.requestRootPermission()
            override fun onFeedback() {
                com.KiYY.lost.util.AutoStartHelper.openQqChat(ctx, "3283138152")
            }
            override fun onDialogItemSelected(index: Int) = viewModel.onDialogItemSelected(index)
            override fun onDialogInputChange(value: String) = viewModel.onDialogInputChange(value)
            override fun onDialogConfirm(value: String) = viewModel.onDialogConfirm(value)
            override fun onDialogDismiss() = viewModel.onDialogDismiss()
            override fun onDialogDismissFinished() = viewModel.onDialogDismissFinished()
            override fun onOverwriteConfirm() = viewModel.onOverwriteConfirm()
            override fun onOverwriteDismiss() = viewModel.onOverwriteDismiss()
            override fun onToggleUiStyle() = viewModel.toggleUiStyle()
            override fun onWorkspaceSelected(workspace: Workspace) = viewModel.onWorkspaceSelected(workspace)
            override fun onOpenBootPage() = viewModel.openBootPage()
            override fun onCloseBootPage() = viewModel.closeBootPage()
            override fun onBootMethodSelected(method: BootImportMethod) = viewModel.selectBootMethod(method)
            override fun onBootPickFile() = viewModel.bootPickFile()
            override fun onBootClearFile() = viewModel.bootClearFile()
            override fun onBootToggleAdvanced() = viewModel.bootToggleAdvanced()
            override fun onBootToggleForceBackup(enabled: Boolean) = viewModel.bootToggleForceBackup(enabled)
            override fun onBootToggleCacheParseData(enabled: Boolean) = viewModel.bootToggleCacheParseData(enabled)
            override fun onBootNext() = viewModel.bootNext()
            override fun onToggleConsoleExpand() = viewModel.toggleConsoleExpand()
            override fun onDismissProgressDialog() = viewModel.dismissProgressDialog()
        },
    )
}