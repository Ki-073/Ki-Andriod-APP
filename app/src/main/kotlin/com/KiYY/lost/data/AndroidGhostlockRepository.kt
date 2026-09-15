package com.KiYY.lost.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.system.Os
import androidx.core.content.edit
import androidx.core.net.toUri
import com.KiYY.lost.domain.model.CpuPair
import com.KiYY.lost.domain.model.KernelOffsets
import com.KiYY.lost.domain.model.KernelSnapshot
import com.KiYY.lost.domain.model.OffsetCandidate
import com.KiYY.lost.domain.model.OffsetImportResult
import com.KiYY.lost.domain.model.ParseResult
import com.KiYY.lost.domain.model.SupportedKernels
import com.KiYY.lost.domain.repository.GhostlockRepository
import com.KiYY.lost.domain.usecase.OffsetMatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Android implementation of the domain repository. All platform I/O lives here. */
class AndroidGhostlockRepository(context: Context) : GhostlockRepository {
    private companion object {
        const val OffsetsFileName = "offsets.json"
        const val KsuLogName = ".ghostlock_ksu.log"
        const val ExtractBinaryName = "libextract.so"
    }

    private val appContext = context.applicationContext
    private val filesDir: File = appContext.filesDir
    private val offsetsFile get() = File(filesDir, OffsetsFileName)
    private val cpuPairs = mutableListOf<CpuPair>()
    private val cpuPairLabels = mutableListOf<String>()
    private var selectedCpuPair = 0
    private var safeModeEnabled = false
    private var tcpRouteEnabled = true
    private var pendingParsedEntries: JSONArray? = null

    init {
        buildCpuPairs()
        restoreCpuPair()
    }

    override suspend fun snapshot(): KernelSnapshot = KernelSnapshot(
        deviceName = resolveDeviceName(),
        kernelRelease = System.getProperty("os.version", "unknown").orEmpty(),
        socName = resolveSocName(),
        kernelSupported = isKernelSupported(),
        cpuPairs = cpuPairs.toList(),
        cpuPairLabels = cpuPairLabels.toList(),
        selectedCpuPair = selectedCpuPair,
        safeModeEnabled = safeModeEnabled,
        tcpRouteEnabled = tcpRouteEnabled,
        compact = isCompactKernel(),
        jailbroken = isJailbroken(),
        seLinux = seLinuxStatus(),
        seccomp = seccompStatus(),
    )

    /**
     * 探测内核当前是否已处于越狱态。
     *
     * ⚠ 关键修复（v1.50）：**绝不能在 snapshot 加载阶段阻塞式调用 su**。
     * 冷启动 / 开机自启时 App 尚无 root 授权，`su -c id` 会让 KSU 弹授权框或
     * 长时间阻塞（5s 超时），导致 snapshot 永远加载不完 → kernelSnapshot == null
     * → onRun() 被静默跳过，表现为「必须先手动越狱、拿到 root 才会自启动」。
     *
     * 现在改为：读 App 自己持久化的标记（零阻塞）。真实的 root 能力在
     * 越狱执行阶段由 runExploit 内部通过 ksud 判定，不依赖这里。
     */
    private fun isJailbroken(): Boolean {
        return runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .getBoolean("jailbroken", false)
        }.getOrDefault(false)
    }

    /**
     * 执行一条需要 root 的命令并返回输出。
     *
     * 关键经验（实测）：
     *  - App 域**不能**直接 exec /system/bin/su（exec failed），必须 `sh -c "su -c ..."`
     *  - KernelSU 的 su 子进程 PATH 很窄，`getenforce` 等命令找不到
     *    → 因此**命令一律用绝对路径**（/system/bin/...）
     * 返回 null 表示无法获得 root。
     */
    private fun runSu(cmd: String): String? {
        return try {
            val process = ProcessBuilder("sh", "-c", "su -c $cmd")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            output.ifBlank { null }
        } catch (_: Throwable) {
            null
        }
    }

    /** 诊断用：返回越狱/SELinux/Seccomp 的原始探测文本，供 UI 控制台显示。 */
    override fun diagnose(): List<String> {
        val lines = mutableListOf<String>()
        lines += "[diag] su -c id -> ${runSuRaw("id") ?: "<null>"}"
        lines += "[diag] su -c cat enforce -> ${runSuRaw("/system/bin/cat /sys/fs/selinux/enforce") ?: "<null>"}"
        lines += "[diag] su -c whoami -> ${runSuRaw("id -un") ?: "<null>"}"
        lines += "[diag] /system/bin/su exists -> ${File("/system/bin/su").exists()}"
        lines += "[diag] isJailbroken -> ${isJailbroken()}"
        lines += "[diag] seLinux -> ${seLinuxStatus()}"
        lines += "[diag] seccomp -> ${seccompStatus()}"
        // 写诊断文件到 App 私有目录，便于从外部读取排错
        runCatching {
            File(appContext.filesDir, ".ghostlock_diag.log").writeText(lines.joinToString("\n"))
        }
        return lines
    }

    /** 诊断用：执行 su 命令，返回 "exit=N output=..." 形式。 */
    private fun runSuRaw(cmd: String): String? {
        return try {
            val process = ProcessBuilder("sh", "-c", "su -c $cmd")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                "<timeout>"
            } else {
                "exit=${process.exitValue()} out=${output.trim().replace('\n', ' ')}"
            }
        } catch (e: Throwable) {
            "<err:${e.javaClass.simpleName} ${e.message}>"
        }
    }

    /** 越狱成功后调用：写入持久化标记（仅作辅助参考，权威判据仍是 isJailbroken()）。 */
    override fun markJailbroken() {
        runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("jailbroken", true) }
        }
    }

    /**
     * 主动申请 Root 授权。
     *
     * 执行 `su -c id`：
     *  - 已在 allowlist → 秒回 uid=0
     *  - 不在 → KSU 弹出授权请求框，这里**等最长 60 秒**让用户点「允许」
     *
     * 为什么不复用 runSu()：后者只等 5 秒，来不及让用户点授权框。
     */
    override suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("sh", "-c", "su -c id")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            // 等用户点授权框：最多 60s
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext false
            }
            output.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 快速检测 App 是否已被授予 root 权限（**非阻塞**，最多等 3 秒）。
     *
     * 与 [requestRoot] 的区别：
     *  - requestRoot：等 60 秒，用长等待让 KSU 授权框有足够时间被用户点击
     *  - hasRootPermission：只等 3 秒，用于「状态检测」场景 ——
     *    已授权时秒回 uid=0；未授权时 3 秒后放弃（不弹框、不卡界面）
     */
    override suspend fun hasRootPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("sh", "-c", "su -c id")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                output.contains("uid=0")
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** 持久化「缓存解析数据」开关 */
    override fun setCacheParseData(enabled: Boolean) {
        runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("cache_parse_data", enabled) }
        }
    }

    /** 持久化 UI 风格（true=Glass，false=Material） */
    override fun setUiStyle(glass: Boolean) {
        runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("ui_style_glass", glass) }
        }
    }

    /** 读取 UI 风格，默认 Material */
    override fun getUiStyle(): Boolean {
        return runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .getBoolean("ui_style_glass", false)
        }.getOrDefault(false)
    }

    /** 持久化「开机自动越狱」 */
    override fun setAutoJailbreakOnBoot(enabled: Boolean) {
        runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("auto_jailbreak_boot", enabled) }
        }
    }

    override fun getAutoJailbreakOnBoot(): Boolean {
        return runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .getBoolean("auto_jailbreak_boot", false)
        }.getOrDefault(false)
    }

    /** 持久化「越狱失败自动重试」 */
    override fun setAutoRetryJailbreak(enabled: Boolean) {
        runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("auto_retry_jailbreak", enabled) }
        }
    }

    override fun getAutoRetryJailbreak(): Boolean {
        return runCatching {
            appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
                .getBoolean("auto_retry_jailbreak", false)
        }.getOrDefault(false)
    }

    /** 宽松读取文本：逐行读，任意 IOException/SELinux 拒绝都吞掉并返回已读内容。 */
    private fun readTextLenient(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val sb = StringBuilder()
            file.bufferedReader().use { reader ->
                var line = reader.readLine()
                var count = 0
                while (line != null && count < 4096) {
                    sb.append(line).append('\n')
                    count++
                    if (sb.contains("kernelsu", ignoreCase = true)) break
                    line = reader.readLine()
                }
            }
            sb.toString()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 探测 SELinux 当前模式。
     *
     * ⚠ 同样不能在 snapshot 阶段调用 su（冷启动会阻塞/弹授权框）。
     * 只走 framework API，App 无 root 也能拿到正确值。
     */
    private fun seLinuxStatus(): String {
        runCatching {
            val cls = Class.forName("android.os.SELinux")
            val enforced = cls.getMethod("isSELinuxEnforced").invoke(null) as? Boolean
            if (enforced != null) return if (enforced) "强制模式" else "宽容模式"
        }
        // 兜底：直接读 sysfs（App 域对 /sys/fs/selinux/enforce 有读权限）
        runCatching {
            val v = File("/sys/fs/selinux/enforce").readText().trim()
            when {
                v.startsWith("1") -> return "强制模式"
                v.startsWith("0") -> return "宽容模式"
            }
        }
        return "未知"
    }

    /**
     * 探测当前进程 Seccomp 模式。
     *
     * 关键（KSU 同款做法）：必须在**本进程**用 prctl(PR_GET_SECCOMP=21) 查询，
     * 而不是去读 `/proc/self/status` —— 后者通过 `su -c` 读的是 su 子进程的 status，
     * 其 Seccomp 恒为 0，于是永远误判为“未启用”。
     *
     * prctl 返回值：0=未启用 / 1=严格模式(STRICT) / 2=过滤模式(FILTER)
     */
    private fun seccompStatus(): String {
        // 主路径：本进程 prctl
        runCatching {
            val cls = Class.forName("android.system.Os")
            val prctl = cls.getMethod(
                "prctl",
                Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType, Long::class.javaPrimitiveType
            )
            when (prctl.invoke(null, 21, 0L, 0L, 0L, 0L) as? Int) {
                1 -> return "严格模式"
                2 -> return "过滤模式"
                0 -> return "未启用"
            }
        }
        // 兜底：直接读本进程 /proc/self/status（不经 su，读的就是自己）
        runCatching {
            val out = File("/proc/self/status").readText()
            val line = out.lineSequence().firstOrNull { it.startsWith("Seccomp:") } ?: ""
            val value = line.substringAfter(":").trim()
            return when (value) {
                "2" -> "过滤模式"
                "1" -> "严格模式"
                "0" -> "未启用"
                else -> "Unknown"
            }
        }
        return "Unknown"
    }

    override fun selectCpuPair(index: Int) {
        if (index !in cpuPairs.indices) return
        selectedCpuPair = index
        appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
            .edit {
                putString("cpu_pair", cpuPairs[index].toString())
            }
    }

    override fun setSafeModeEnabled(enabled: Boolean) {
        safeModeEnabled = enabled
    }

    override fun setTcpRouteEnabled(enabled: Boolean) {
        tcpRouteEnabled = enabled
    }

    override suspend fun exportCandidates(): List<OffsetCandidate> {
        val entries = readOffsetsFile(offsetsFile) ?: return emptyList()
        val current = System.getProperty("os.version", "")
        return (0 until entries.length()).asSequence()
            .mapNotNull { entries.optJSONObject(it) }
            .map { entry: JSONObject -> entry.optString("release", "") to entry }
            .filter { (release, entry) ->
                release.isNotEmpty() &&
                        !(SupportedKernels.BUILTIN.containsKey(release) && matchesBuiltin(entry))
            }
            .distinctBy { it.first }
            .sortedWith(compareBy<Pair<String, JSONObject>> { if (it.first == current) 0 else 1 }.thenBy { it.first })
            .map { (release, entry) -> OffsetCandidate(release, entry.toString(2)) }
            .toList()
    }

    override suspend fun importOffsets(json: String): OffsetImportResult = mergeImported(json, overwrite = false)

    override suspend fun confirmImport(json: String): OffsetImportResult = mergeImported(json, overwrite = true)

    private fun mergeImported(json: String, overwrite: Boolean): OffsetImportResult {
        return try {
            val imported = parseEntries(json) ?: return OffsetImportResult.Failed("not a valid offsets.json")
            val existing = readOffsetsFile(offsetsFile) ?: JSONArray()
            val fresh = JSONArray()
            val skipped = mutableListOf<String>()
            val differingBuiltins = mutableListOf<String>()
            for (index in 0 until imported.length()) {
                val entry = imported.optJSONObject(index) ?: continue
                val release = entry.optString("release", "")
                if (release.isEmpty()) continue
                if (release in SupportedKernels.BUILTIN) {
                    if (matchesBuiltin(entry)) {
                        skipped += release
                    } else {
                        differingBuiltins += release
                        fresh.put(entry)
                    }
                } else {
                    fresh.put(entry)
                }
            }
            if (fresh.length() == 0) return OffsetImportResult.AlreadyPresent

            val overlaps = overlappingReleases(existing, fresh)
            val replaced = (overlaps + differingBuiltins).distinct()
            if (!overwrite && replaced.isNotEmpty()) {
                return OffsetImportResult.RequiresOverwrite(replaced)
            }
            mergeAndSave(existing, fresh, overwrite)
            OffsetImportResult.Imported(freshReleases(fresh))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            OffsetImportResult.Failed(error.message ?: "import failed")
        }
    }

    override suspend fun parseSource(input: String, xblPath: String?, overwrite: Boolean, onLog: (String) -> Unit): ParseResult {
        val parsedFile = File(filesDir, "offsets_parse.tmp")
        return try {
            if (overwrite) {
                val pending = pendingParsedEntries
                if (pending != null) {
                    pendingParsedEntries = null
                    val existing = readOffsetsFile(offsetsFile) ?: JSONArray()
                    mergeAndSave(existing, pending, overwrite = true)
                    return ParseResult.Parsed(freshReleases(pending))
                }
            }
            val binary = File(appContext.applicationInfo.nativeLibraryDir, ExtractBinaryName)
            if (!binary.isFile) return ParseResult.Failed(1, "missing native binary: ${binary.absolutePath}")
            parsedFile.delete()
            val args = buildList {
                add(input)
                if (xblPath != null) {
                    add("--xbl-config")
                    add(xblPath)
                }
                addAll(listOf("--format", "json", "--out", parsedFile.absolutePath, "--work-dir", filesDir.absolutePath))
            }
            onLog("extract: $input")
            val code = runProcess(
                ProcessBuilder(listOf(binary.absolutePath) + args)
                    .directory(filesDir)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["GHOSTLOCK_HOME"] = filesDir.absolutePath
                        environment()["TMPDIR"] = filesDir.absolutePath
                        environment()["HOME"] = filesDir.absolutePath
                    },
                onLog = onLog,
                timeoutSeconds = 1800,
            )
            onLog("extract exit code=$code")
            if (code != 0 || !parsedFile.isFile) return ParseResult.Failed(code)
            val fresh = parseEntries(parsedFile.readText()) ?: return ParseResult.Failed(code, "invalid extractor output")
            val existing = readOffsetsFile(offsetsFile) ?: JSONArray()
            val filtered = JSONArray()
            val skipped = mutableListOf<String>()
            val differingBuiltins = mutableListOf<String>()
            for (index in 0 until fresh.length()) {
                val entry = fresh.optJSONObject(index) ?: continue
                val release = entry.optString("release", "")
                if (release in SupportedKernels.BUILTIN) {
                    if (matchesBuiltin(entry)) skipped += release
                    else {
                        differingBuiltins += release
                        filtered.put(entry)
                    }
                } else {
                    filtered.put(entry)
                }
            }
            if (filtered.length() == 0) return ParseResult.AlreadyPresent
            val replaced = if (overwrite) emptyList() else differingBuiltins.distinct()
            if (replaced.isNotEmpty()) {
                pendingParsedEntries = filtered
                return ParseResult.RequiresOverwrite(replaced)
            }
            mergeAndSave(existing, filtered, overwrite = true)
            ParseResult.Parsed(freshReleases(filtered))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ParseResult.Failed(1, error.message)
        } finally {
            parsedFile.delete()
        }
    }

    override suspend fun runExploit(pair: CpuPair, onLog: (String) -> Unit): Int {
        val workDir = filesDir
        return try {
            val binary = File(appContext.applicationInfo.nativeLibraryDir, "libghostlock.so")
            require(binary.isFile) { "missing native binary: ${binary.absolutePath}" }
            if (prepareKsud(workDir, onLog) != null) onLog("ksud ready") else onLog("warning: ksud not found")
            // the root script creates its log as root, so one name per run
            // keeps the last run's lines out of this run's log
            val ksuLog = File(workDir, "$KsuLogName.${System.currentTimeMillis()}")
            val nativeLog = File(workDir, ".ghostlock_native.log")
            nativeLog.writeText("")
            val ksuOffset = AtomicLong()
            val nativeOffset = AtomicLong()
            // tag root-script lines so they cannot be read as the native stages'
            val ksuSink: (String) -> Unit = { onLog("[ksu] $it") }
            val tailer = Thread {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        tailKsuLog(nativeLog, nativeOffset, onLog)
                        tailKsuLog(ksuLog, ksuOffset, ksuSink)
                        Thread.sleep(200)
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }.apply {
                name = "ksu-log-tailer"
                isDaemon = true
                start()
            }
            val command = ProcessBuilder(binary.absolutePath)
                .directory(workDir)
                .redirectErrorStream(false)   // keep stdout/stderr separate; we capture both below
                .apply {
                    environment()["GHOSTLOCK_HOME"] = workDir.absolutePath
                    environment()["TMPDIR"] = workDir.absolutePath
                    environment()["HOME"] = workDir.absolutePath
                    environment()["GHOSTLOCK_KSU_LOG"] = ksuLog.absolutePath
                    if (pair.primary != 0 || pair.consumer != 1) {
                        environment()["GHOSTLOCK_CORE"] = pair.primary.toString()
                        environment()["GHOSTLOCK_CONSUMER_CORE"] = pair.consumer.toString()
                    }
                    if (safeModeEnabled) environment()["GHOSTLOCK_DISABLE_MODULES"] = "1"
                    if (!tcpRouteEnabled) environment()["GHOSTLOCK_TCP_ROUTE"] = "0"
                }
            try {
                // Capture stdout directly (do not rely on the 200ms file tailer only:
                // a fast SIGSEGV would lose the last lines). stderr is merged into
                // the same file as before for the tailer path.
                val exit = runProcessWithBothOutputs(
                    builder = command,
                    onStdout = onLog,
                    onStderr = onLog,
                )
                exit
            } finally {
                withContext(Dispatchers.IO) {
                    tailer.interrupt()
                    tailer.join(1000)
                    tailKsuLog(nativeLog, nativeOffset, onLog)
                    tailKsuLog(ksuLog, ksuOffset, ksuSink)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onLog("error: ${error::class.simpleName}: ${error.message}")
            1
        }
    }

    override suspend fun readDocument(uri: String): String = appContext.contentResolver
        .openInputStream(uri.toUri())
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: throw IOException("cannot open $uri")

    override suspend fun cacheDocument(uri: String, fileName: String): String {
        val target = File(filesDir, fileName)
        appContext.contentResolver.openInputStream(uri.toUri())?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: throw IOException("cannot open $uri")
        return target.absolutePath
    }

    override suspend fun publishOffsets(candidate: OffsetCandidate): String {
        val safeRelease = candidate.release.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "offsets-$safeRelease.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
        }
        val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("cannot create download entry")
        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            output.write("[${candidate.json}]".toByteArray(StandardCharsets.UTF_8))
        } ?: throw IOException("cannot open download entry")
        return uri.toString()
    }

    override fun close() {
        synchronized(processes) {
            processes.forEach(Process::destroyForcibly)
            processes.clear()
        }
    }

    private fun parseEntries(text: String): JSONArray? {
        if (text.isBlank()) return null
        return try {
            when (val value = JSONTokener(text).nextValue()) {
                is JSONArray -> value
                is JSONObject -> JSONArray().put(value)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readOffsetsFile(file: File): JSONArray? = if (file.isFile) parseEntries(file.readText()) else null

    private fun overlappingReleases(existing: JSONArray, imported: JSONArray): List<String> {
        val known = (0 until existing.length()).mapNotNull { existing.optJSONObject(it)?.optString("release") }.toSet()
        return (0 until imported.length()).mapNotNull { imported.optJSONObject(it)?.optString("release") }
            .filter { it in known }
            .distinct()
    }

    private fun mergeAndSave(existing: JSONArray, imported: JSONArray, overwrite: Boolean) {
        val importedByRelease = (0 until imported.length()).mapNotNull { imported.optJSONObject(it) }
            .associateBy { it.optString("release", "") }
        val known = (0 until existing.length()).mapNotNull { existing.optJSONObject(it)?.optString("release") }.toSet()
        val merged = JSONArray()
        for (index in 0 until existing.length()) {
            val entry = existing.optJSONObject(index) ?: continue
            val release = entry.optString("release", "")
            if (overwrite && release in importedByRelease) continue
            merged.put(entry)
        }
        for (index in 0 until imported.length()) {
            val entry = imported.optJSONObject(index) ?: continue
            if (!overwrite && entry.optString("release", "") in known) continue
            merged.put(entry)
        }
        offsetsFile.writeText(merged.toString(2), StandardCharsets.UTF_8)
    }

    private fun freshReleases(entries: JSONArray): List<String> =
        (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.optString("release") }.distinct()

    private fun matchesBuiltin(entry: JSONObject): Boolean = OffsetMatching.matchesBuiltin(toKernelOffsets(entry), SupportedKernels.BUILTIN)

    private fun toKernelOffsets(entry: JSONObject): KernelOffsets = KernelOffsets(
        release = entry.optString("release", ""),
        scalars = scalarFields.associateWith { if (entry.has(it) && !entry.isNull(it)) entry.optLong(it) else null },
        symbols = objectFields(entry.optJSONObject("symbols")),
        structFields = objectFields(entry.optJSONObject("struct_fields")),
    )

    private fun objectFields(value: JSONObject?): Map<String, Long?> = value?.keys()?.asSequence()
        ?.associateWith { key -> if (value.isNull(key)) null else value.optLong(key) }
        ?: emptyMap()

    private val scalarFields = listOf("pselect_waiter_shift", "compact_waiter", "mm_struct_sz", "kernel_phys_load")

    private fun isKernelSupported(): Boolean {
        val version = System.getProperty("os.version", "").orEmpty()
        return version in SupportedKernels.UNAMES || importedOffsetsMatch(version)
    }

    private fun isCompactKernel(): Boolean {
        val version = System.getProperty("os.version", "").orEmpty()
        // an imported entry overrides the built-in one, a member it omits keeps the built-in
        // value, as in select_offsets. first match wins, same as load_offsets_json
        val entries = readOffsetsFile(offsetsFile)
        val imported = (0 until (entries?.length() ?: 0))
            .mapNotNull { entries?.optJSONObject(it) }
            .firstOrNull { it.optString("release", "") == version }
            ?.let { toKernelOffsets(it).scalars["compact_waiter"] }
        val value = imported ?: SupportedKernels.BUILTIN[version]?.get("compact_waiter")
        return value != null && value != 0L
    }

    private fun importedOffsetsMatch(version: String): Boolean {
        val entries = readOffsetsFile(offsetsFile) ?: return false
        return (0 until entries.length()).any { entries.optJSONObject(it)?.optString("release") == version }
    }

    private fun buildCpuPairs() {
        cpuPairs.clear()
        cpuPairLabels.clear()
        val online = parseCpuList(readSysFile("/sys/devices/system/cpu/online"))
        online.groupBy { readMaxFreq(it) }
            .filterKeys { it > 0 }
            .toSortedMap(compareByDescending { it })
            .forEach { (freq, cluster) ->
                cluster.sorted().chunked(2).filter { it.size == 2 }.forEach { pair ->
                    cpuPairs += CpuPair(pair[0], pair[1])
                    cpuPairLabels += "${pair[0]},${pair[1]} · ${formatFreq(freq)}"
                }
            }
        if (CpuPair(0, 1) !in cpuPairs) {
            cpuPairs += CpuPair(0, 1)
            val freq = readMaxFreq(0)
            cpuPairLabels += "0,1" + if (freq > 0) " · ${formatFreq(freq)}" else ""
        }
    }

    private fun restoreCpuPair() {
        val saved = appContext.getSharedPreferences("ghostlock_prefs", Context.MODE_PRIVATE)
            .getString("cpu_pair", null) ?: return
        val pair = saved.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (pair.size == 2) cpuPairs.indexOf(CpuPair(pair[0], pair[1])).takeIf { it >= 0 }?.let { selectedCpuPair = it }
    }

    private fun parseCpuList(value: String): List<Int> = value.split(',').flatMap { part ->
        val range = part.trim().split('-').mapNotNull { it.toIntOrNull() }
        when (range.size) {
            1 -> range
            2 -> (range[0]..range[1]).toList()
            else -> emptyList()
        }
    }

    private fun readMaxFreq(cpu: Int): Long = readSysFile("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").toLongOrNull() ?: -1L

    private fun formatFreq(khz: Long): String =
        if (khz >= 1_000_000L) "%.2f GHz".format(Locale.ROOT, khz / 1_000_000.0) else "%.0f MHz".format(Locale.ROOT, khz / 1000.0)

    private fun readSysFile(path: String): String = File(path).takeIf { it.isFile }?.useLines { it.firstOrNull()?.trim().orEmpty() } ?: ""

    @SuppressLint("PrivateApi")
    private fun systemProperty(key: String): String = try {
        val properties = Class.forName("android.os.SystemProperties")
        properties.getMethod("get", String::class.java).invoke(null, key) as? String ?: ""
    } catch (_: Throwable) {
        ""
    }

    private fun validDeviceName(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.contains("unknown", true) && !it.contains("null", true) }

    private fun resolveDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val marketName = when (manufacturer.lowercase(Locale.ROOT)) {
            "xiaomi" -> firstValidProperty("ro.product.marketname")
            "oppo", "oneplus", "realme", "oplus" -> {
                val cn = Locale.getDefault().country.equals("CN", true)
                firstValidProperty(
                    *(if (cn) arrayOf(
                        "ro.vendor.oplus.market.name",
                        "ro.vendor.oplus.market.enname"
                    ) else arrayOf("ro.vendor.oplus.market.enname", "ro.vendor.oplus.market.name"))
                )
            }

            "vivo" -> firstValidProperty("ro.vivo.market.name")
            "honor", "huawei" -> firstValidProperty("ro.config.marketing_name")
            "zte", "nubia" -> firstValidProperty("ro.vendor.product.ztename")
            else -> null
        }
        return marketName ?: listOfNotNull(
            manufacturer,
            Build.BRAND.orEmpty().takeIf { !it.equals(manufacturer, true) },
            Build.MODEL.orEmpty()
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun resolveSocName(): String = listOf(
        systemProperty("ro.soc.manufacturer"),
        systemProperty("ro.soc.model"),
    )
        .mapNotNull(::validDeviceName)
        .joinToString(" ")
        .ifBlank { "unknown" }

    private fun firstValidProperty(vararg keys: String): String? =
        keys.asSequence().firstNotNullOfOrNull { validDeviceName(systemProperty(it)) }

    private fun prepareKsud(workDir: File, onLog: (String) -> Unit): File? {
        // 补充 com.sukisu.ultra（SukiSU Ultra 新包名，原版遗漏）
        val packages = listOf(
            "com.sukisu.ultra",
            "me.weishu.kernelsu.pr",
            "me.weishu.kernelsu",
            "com.resukisu.resukisu",
            "com.kowx712.supermanager",
        )
        var installed = false
        for (packageName in packages) {
            val appInfo = runCatching { appContext.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: continue
            installed = true
            val source = File(appInfo.nativeLibraryDir, "libksud.so")
            if (!source.isFile) continue
            val output = File(workDir, "ksud")
            runCatching {
                source.inputStream().use { input -> output.outputStream().use { input.copyTo(it) } }
                runCatching { Os.chmod(output.absolutePath, 448) }
                return output
            }.onFailure { onLog("copy ksud failed: ${it.message}") }
        }
        // 兜底：直接从 /data/adb 复制（已 root 的设备上通常已存在）
        val adbKsud = File("/data/adb/ksud")
        if (adbKsud.isFile) {
            val output = File(workDir, "ksud")
            runCatching {
                adbKsud.inputStream().use { input -> output.outputStream().use { input.copyTo(it) } }
                runCatching { Os.chmod(output.absolutePath, 448) }
                return output
            }.onFailure { onLog("copy /data/adb/ksud failed: ${it.message}") }
        }
        if (!installed) onLog("KernelSU/ReSukiSU/KowSU/SukiSU app not installed")
        return null
    }

    private suspend fun runProcess(
        builder: ProcessBuilder,
        onLog: (String) -> Unit = {},
        timeoutSeconds: Long = 300,
        captureOutput: Boolean = true,
    ): Int = runInterruptible {
        val process = builder.start()
        synchronized(processes) { processes += process }
        val reader = if (captureOutput) Thread {
            try {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines -> lines.forEach(onLog) }
            } catch (_: IOException) { }
        }.apply {
            name = "process-output-reader"
            isDaemon = true
        } else null
        try {
            reader?.start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            reader?.let(::joinReader)
            if (finished) process.exitValue() else -1
        } finally {
            if (process.isAlive) process.destroyForcibly()
            reader?.interrupt()
            runCatching { process.inputStream.close() }
            reader?.let(::joinReader)
            synchronized(processes) { processes -= process }
        }
    }

    private fun joinReader(reader: Thread) {
        try {
            reader.join(3000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun tailKsuLog(logFile: File, offset: AtomicLong, onLog: (String) -> Unit) {
        if (!logFile.isFile) return
        synchronized(offset) {
            runCatching {
                RandomAccessFile(logFile, "r").use { file ->
                    val position = offset.get().takeIf { it <= file.length() } ?: 0L
                    file.seek(position)
                    var lastComplete = position
                    val pending = StringBuilder()
                    while (true) {
                        val byte = file.read()
                        if (byte == -1) break
                        if (byte == '\n'.code) {
                            if (pending.isNotEmpty()) onLog(pending.toString())
                            pending.clear()
                            lastComplete = file.filePointer
                        } else {
                            pending.append(byte.toChar())
                        }
                    }
                    offset.set(lastComplete)
                }
            }
        }
    }

    private val processes = mutableSetOf<Process>()

    /**
     * Like [runProcess] but reads stdout and stderr concurrently and forwards
     * every line to [onStdout] / [onStderr] as it arrives. This guarantees the
     * last lines survive a fast crash (the file-tailer path is polled every
     * 200 ms and can lose them).
     */
    private suspend fun runProcessWithBothOutputs(
        builder: ProcessBuilder,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        timeoutSeconds: Long = 300,
    ): Int = runInterruptible {
        val process = builder.start()
        synchronized(processes) { processes += process }

        val outReader = Thread {
            try {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8)
                    .useLines { it.forEach(onStdout) }
            } catch (_: IOException) { }
        }.apply { name = "native-stdout-reader"; isDaemon = true }

        val errReader = Thread {
            try {
                process.errorStream.bufferedReader(StandardCharsets.UTF_8)
                    .useLines { it.forEach(onStderr) }
            } catch (_: IOException) { }
        }.apply { name = "native-stderr-reader"; isDaemon = true }

        try {
            outReader.start()
            errReader.start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            joinReader(outReader)
            joinReader(errReader)
            if (finished) process.exitValue() else -1
        } finally {
            if (process.isAlive) process.destroyForcibly()
            outReader.interrupt()
            errReader.interrupt()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            joinReader(outReader)
            joinReader(errReader)
            synchronized(processes) { processes -= process }
        }
    }

}