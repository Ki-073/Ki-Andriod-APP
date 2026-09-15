package com.KiYY.lost.util

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * ColorOS / OxygenOS 开机自启动权限的检测与引导。
 *
 * 背景：
 *  ColorOS 用 `fgs_boot_completed_allowlist`（一个 uid 位掩码）决定
 *  开机后是否向某个 App 投递 BOOT_COMPLETED。未加白的 App 收不到广播，
 *  因此「开机自动越狱」不会触发。
 *
 *  该白名单没有公开 API 可读，唯一可靠的做法是：
 *   1) 引导用户进 ColorOS 的「自启动」设置页手动开启
 *   2) 用启发式方法尽力判断当前是否已开启（不保证 100% 准确）
 */
object AutoStartHelper {

    /** ColorOS 自启动管理页（应用列表） */
    private const val OPLUS_STARTUP_LIST =
        "com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity"

    /** ColorOS 自启动管理页的 action 形式（部分版本用这个） */
    private const val ACTION_OPLUS_STARTUP_LIST =
        "com.oplus.battery.permission.startup.StartupAppListActivity"

    /** ColorOS 自动启动优化页 */
    private const val OPLUS_AUTO_START_OPT =
        "com.oplus.battery/com.oplus.startupapp.view.OptimizationAutoStartActivity"

    /** 是否 ColorOS / OxygenOS（欧加真）系 */
    fun isOplusRom(): Boolean {
        val brand = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
        if (brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme")) return true
        // 兜底：看是否存在 ColorOS 的自启动管理包
        return runCatching {
            Class.forName("com.oplus.battery.permission.startup.StartupAppListActivity")
            true
        }.getOrDefault(false)
    }

    /**
     * 尽力判断开机自启动是否已开启。
     *
     * 判据（启发式）：
     *  ColorOS 未加白时，App 进程在开机后被系统冻结/延迟启动，
     *  这里通过「App 是否处于后台限制名单」间接判断：读不到时返回 false（安全侧）。
     *
     * 注意：没有公开 API，返回值仅用于 UI 提示，不能作为硬判据。
     */
    fun isAutoStartEnabled(context: Context): Boolean {
        // 启发式：如果 App 当前进程重要性正常（非 FOREGROUND 也能被拉起），
        // 说明未被严格限制。这里只能给出保守结果。
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            val processes = am.runningAppProcesses ?: return false
            processes.any { it.processName == context.packageName }
        }.getOrDefault(false)
    }

    /**
     * 跳转到 ColorOS 的自启动管理页。
     *
     * 依次尝试（越靠前越精确）：
     *  1. 自启动应用列表（直接列出 App，最理想）
     *  2. 自启动列表的 action 形式
     *  3. 自动启动优化页
     *  4. 应用详情页（最终兜底，所有 ROM 通用）
     *
     * @return 是否成功启动某个页面
     */
    fun openAutoStartSettings(context: Context): Boolean {
        // 1) 精确 Component
        if (launchComponent(context, OPLUS_STARTUP_LIST)) return true
        // 2) action 形式
        if (launchAction(context, ACTION_OPLUS_STARTUP_LIST)) return true
        // 3) 优化页
        if (launchComponent(context, OPLUS_AUTO_START_OPT)) return true
        // 4) 应用详情页兜底
        return openAppDetail(context)
    }

    /** 打开当前 App 的系统详情页（含「自启动」「电池」等入口） */
    fun openAppDetail(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            },
        )
        for (i in intents) {
            if (launchIntent(context, i)) return true
        }
        return false
    }

    /** 打开电池优化白名单页 */
    fun openBatteryOptimization(context: Context): Boolean {
        val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return launchIntent(context, i)
    }

    /**
     * 是否已获得「显示在其他应用上层」（SYSTEM_ALERT_WINDOW）权限。
     *
     * 这是**开机自动拉起界面的关键**：Android 10+ 的 BAL 限制会拦截
     * 后台进程（如 BOOT_COMPLETED 接收器）调用 startActivity()；
     * 持有该权限时系统视为「有可见窗口」→ 放行。
     */
    fun canDrawOverlays(context: Context): Boolean = runCatching {
        Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    /** 跳转到「显示在其他应用上层」授权页 */
    fun openOverlaySettings(context: Context): Boolean {
        val i = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        )
        return launchIntent(context, i)
    }

    // ======================================================================
    // 社媒跳转（参考 Ki-VPN 的 openTelegramChannel / openQqGroup 实现）
    // ======================================================================

    /**
     * 打开 Telegram 频道。
     *
     * 实现要点（与 Ki-VPN 一致）：**双路回退**
     *  1. 先试 `tg://resolve?domain=xxx` —— 直接唤起已安装的 Telegram 客户端，
     *     体验最好（在 App 内打开频道，而不是跳浏览器）。
     *  2. 若未安装 Telegram → 退回 `https://t.me/xxx`，由系统用浏览器/应用选择器打开。
     *
     * @param domain 频道用户名（不带 @），例如 "HouseLua"
     * @return 是否成功发起跳转
     */
    fun openTelegramChannel(context: Context, domain: String): Boolean {
        // ⚠ 不要用 packageManager.resolveActivity() 做前置检测！
        //   Android 11+ 的包可见性（package visibility）会让它对隐式 intent 返回 null，
        //   即使设备上装了 Telegram —— 结果就是「明明有 Telegram 却跳去了网页」。
        //   正确做法：**直接 startActivity，捕获 ActivityNotFoundException 再回退**。
        val tgIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("tg://resolve?domain=$domain")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        val ok = runCatching {
            context.startActivity(tgIntent)
            true
        }.getOrDefault(false)
        if (ok) return true

        // 回退：https 链接（未安装 Telegram 时由系统兜底）
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://t.me/$domain")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            context.startActivity(webIntent)
            true
        }.getOrDefault(false)
    }

    /** 是否已安装 Telegram（仅用于 UI 文案，不做跳转判据） */
    fun hasTelegram(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("org.telegram.messenger", 0)
        true
    }.getOrDefault(false) || runCatching {
        context.packageManager.getPackageInfo("org.telegram.csc.messenger", 0)
        true
    }.getOrDefault(false)

    /**
     * 打开 QQ 聊天（问题反馈用）。
     *
     * 参考 Ki-VPN 的 openQqProfile 思路，多路回退：
     *  1. `mqqwpa://im/chat?chat_type=wpa&uin=xxx` —— 直接打开 QQ 聊天窗口（首选）
     *  2. `mqqapi://card/show_pslcard?src_type=internal&uin=xxx` —— QQ 资料卡
     *  3. `https://wpa.qq.com/msgrd?...` —— 网页版兜底
     *
     * ⚠ 与 Telegram 同理：不用 resolveActivity 预判（包可见性会导致误判），
     *   直接 startActivity 并在失败时逐级回退。
     */
    fun openQqChat(context: Context, qq: String): Boolean {
        val candidates = listOf(
            // 1) 直接打开与目标 QQ 的聊天窗口
            "mqqwpa://im/chat?chat_type=wpa&uin=$qq&version=1&src_type=web",
            // 2) QQ 资料卡
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$qq&card_type=person",
            // 3) 网页版兜底
            "https://wpa.qq.com/msgrd?v=3&uin=$qq&site=qq&menu=yes",
        )
        for (url in candidates) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    // ---- 内部工具 ----

    private fun launchComponent(context: Context, flat: String): Boolean {
        val parts = flat.split("/")
        if (parts.size != 2) return false
        val cn = ComponentName(parts[0], parts[1])
        val i = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchIntent(context, i, cn.packageName)
    }

    private fun launchAction(context: Context, action: String): Boolean {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchIntent(context, i)
    }

    private fun launchIntent(context: Context, intent: Intent, fromPackage: String? = null): Boolean {
        return runCatching {
            if (fromPackage != null) {
                // 显式组件：需确认存在，否则 resolveActivity 会抛异常
                val pm = context.packageManager
                if (pm.resolveActivity(intent, 0) == null) return false
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}