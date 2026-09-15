package com.KiYY.lost.domain.repository

import com.KiYY.lost.domain.model.CpuPair
import com.KiYY.lost.domain.model.KernelSnapshot
import com.KiYY.lost.domain.model.OffsetCandidate
import com.KiYY.lost.domain.model.OffsetImportResult
import com.KiYY.lost.domain.model.ParseResult

interface GhostlockRepository {
    suspend fun snapshot(): KernelSnapshot

    fun selectCpuPair(index: Int)

    fun setSafeModeEnabled(enabled: Boolean)

    fun setTcpRouteEnabled(enabled: Boolean)

    suspend fun exportCandidates(): List<OffsetCandidate>

    suspend fun importOffsets(json: String): OffsetImportResult

    suspend fun confirmImport(json: String): OffsetImportResult

    suspend fun parseSource(
        input: String,
        xblPath: String? = null,
        overwrite: Boolean = false,
        onLog: (String) -> Unit = {},
    ): ParseResult

    suspend fun readDocument(uri: String): String

    suspend fun cacheDocument(uri: String, fileName: String): String

    suspend fun publishOffsets(candidate: OffsetCandidate): String

    suspend fun runExploit(pair: CpuPair, onLog: (String) -> Unit): Int

    /** 越狱成功后写入持久化标记，供重启后恢复状态。 */
    fun markJailbroken()

    /**
     * 主动申请 Root 授权（跑一次 `su -c id`）。
     * 不在 KSU allowlist 时会触发系统授权弹框，用户点「允许」后永久生效。
     * @return 是否已拿到 root（uid=0）
     */
    suspend fun requestRoot(): Boolean

    /**
     * 快速检测是否已获得 root 权限（非阻塞，最多等 3 秒）。
     * 用于越狱成功后判断「是否需要引导用户去 KernelSU 管理器授权」。
     */
    suspend fun hasRootPermission(): Boolean

    /** 设置「缓存解析数据」开关（持久化到 SharedPreferences）。 */
    fun setCacheParseData(enabled: Boolean)

    /** 持久化当前 UI 风格（Material / Glass）。 */
    fun setUiStyle(glass: Boolean)

    /** 读取已保存的 UI 风格（true=Glass，false=Material）。 */
    fun getUiStyle(): Boolean

    /** 持久化「开机自动越狱」开关。 */
    fun setAutoJailbreakOnBoot(enabled: Boolean)

    /** 读取「开机自动越狱」开关。 */
    fun getAutoJailbreakOnBoot(): Boolean

    /** 持久化「越狱失败自动重试」开关。 */
    fun setAutoRetryJailbreak(enabled: Boolean)

    /** 读取「越狱失败自动重试」开关。 */
    fun getAutoRetryJailbreak(): Boolean

    /** 诊断用：返回探测原始文本。 */
    fun diagnose(): List<String>

    fun close()
}