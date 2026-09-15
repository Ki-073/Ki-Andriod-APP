package com.KiYY.lost.ksu

import androidx.compose.runtime.Immutable

/** UI 风格：Material（标准） / Glass（iOS 液态玻璃），可在右上角切换 */
enum class UiStyle {
    Material,
    Glass,
}

/** 对应 KSU 的 SystemInfo */
@Immutable
data class KsuSystemInfo(
    val kernelVersion: String,
    val managerVersion: String,
    val deviceModel: String,
    val fingerprint: String,
    /** SELinux 模式：Enforcing / Permissive / Unknown */
    val seLinux: String = "-",
    /** Seccomp 状态：Filter / Disabled / Strict / Unknown */
    val seccomp: String = "-",
)

/** 对应 KSU 的 HomeUiState（精简为 GhostLock 所需字段） */
@Immutable
data class KsuHomeState(
    val isWorking: Boolean = false,
    val workingVersion: String = "",
    val workingMode: String = "PWNED",
    val idleSummary: String = "",
    val isJailbreakMode: Boolean = false,
    /** boot 是否已解析（决定状态卡下方显示“boot 已解析 / 未解析”） */
    val bootParsed: Boolean = false,
    /** 当前 UI 风格（Material / Miuix），右上角可切换 */
    val uiStyle: UiStyle = UiStyle.Material,
    val showUnsupportedWarning: Boolean = false,
    val unsupportedMessage: String = "",
    val systemInfo: KsuSystemInfo = KsuSystemInfo(
        kernelVersion = "-",
        managerVersion = "-",
        deviceModel = "-",
        fingerprint = "-",
        seLinux = "-",
        seccomp = "-",
    ),
)

/** 对应 KSU 的 HomeActions */
@Immutable
data class KsuHomeActions(
    val onJailbreakClick: () -> Unit = {},
    val onAdvancedClick: () -> Unit = {},
    /** 右上角切换 UI 风格（Material ↔ Miuix） */
    val onToggleUiStyle: () -> Unit = {},
    /** 点击状态卡主体（非按钮区）→ 进入 boot 导入页 */
    val onStatusCardClick: () -> Unit = {},
)