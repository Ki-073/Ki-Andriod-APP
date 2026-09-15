# Ki-PrisonBreak

> Android 本地提权 / 越狱工具（基于 GhostLock · CVE-2026-43499）
> 包名 `com.KiYY.lost` · 应用名 **Ki-PrisonBreak**

---

## 一、这个 App 是干什么的

**Ki-PrisonBreak 是一个把 CVE-2026-43499 内核漏洞利用能力封装成一键操作的 Android App。**

它在**不需要解锁 Bootloader、不需要刷入内核模块**的前提下，利用 Linux 内核的 **pselect/tree-waiter 竞态漏洞（CVE-2026-43499）** 完成本地提权，越狱后：

- 关闭内核保护（SELinux / 内核写保护相关开关）
- 加载 KernelSU 内核模块（`ksud`），实现 **内核级 root（KernelSU）**
- 在 KernelSU 管理器里授予 App 超级用户权限后，可持续检测越狱状态

### 界面

App 提供**两套 UI**，可在设置里切换：

| 风格 | 说明 |
| --- | --- |
| **液态玻璃（Glass）** | 复刻 iOS "Liquid Glass" 视觉效果，毛玻璃卡片 + 模糊背景 |
| **Material 3** | 原生 Material Design 3 风格 |

### 主要功能

| 功能 | 说明 |
| --- | --- |
| **执行越狱** | 一键运行漏洞利用，提权到 uid 0 |
| **解析 boot.img** | 从 `boot.img`（+ `xbl_config.img`）在线提取内核偏移（offsets） |
| **解析 OTA 链接** | 直接从 OTA 包 URL 提取偏移 |
| **导入 offsets.json** | 导入已提取的偏移表，不用重新编译 App |
| **高级选项** | 导入 offsets / boot / xbl 镜像、越狱失败自动重试、问题反馈等 |
| **使用教程** | 内置图文指引（切换 UI / 解析 boot / 执行越狱 / 授权） |
| **状态检测** | 实时显示内核版本、SELinux、Seccomp、越狱状态 |
| **加入频道** | 跳转 Telegram 频道 |
| **支持开发** | 跳转本开源仓库 |

---

## 二、原理简述（CVE-2026-43499）

漏洞出在 Linux 内核 **`select()` / `pselect6()` 的 tree-waiter 等待队列** 实现中：

- 主线程高频调用 `select()` / `getsockopt(TCP_ZEROCOPY_RECEIVE)`，不断创建／销毁等待队列节点；
- 消费者线程扰动 waiter 的优先级（priority inversion）；
- 在竞态窗口内触发 **use-after-free**，篡改 `struct task_struct` 的 `cred` 指针，把当前进程权限提升到 uid 0。

越狱成功后，通过 KernelSU 的 `ksud` 加载内核模块，实现持久的内核级 root。

**支持的内核**：见 `src/kernels/<uname -r>/offsets.h`。内核按 **精确的 `uname -r`** 匹配，不匹配的构建会被拒绝，App 会在顶部显示状态。新增内核可用提取器的 `--register` 添加。

---

## 三、编译方法

### 环境要求

| 项目 | 版本 |
| --- | --- |
| JDK | **17** |
| Gradle | **9.7.1**（仓库自带 wrapper） |
| Android SDK | `compileSdk 37` / `buildTools 34.0.0` |
| NDK | **r30**（交叉编译 native 部分） |
| minSdk / targetSdk | 34 / 34 |

### 1. 准备 native 库

App 依赖两个 native 库（**本仓库已包含预编译的 aarch64 版本**）：

```
app/src/main/jniLibs/arm64-v8a/libghostlock.so   ← 漏洞利用逻辑
app/src/main/jniLibs/arm64-v8a/libextract.so     ← 偏移提取器
```

若要**自行从源码编译**（需要 NDK r30 + clang）：

```bash
# 设置 NDK 路径
export ANDROID_NDK_HOME=/path/to/android-ndk-r30

# 编译 exploit（src/core 下的 C 源码）
# 编译 extractor（tools/extract_rs，Rust）
rustup target add aarch64-linux-android
cargo build --release --target aarch64-linux-android \
      --manifest-path tools/extract_rs/Cargo.toml
```

### 2. 构建 APK

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

./gradlew :app:assembleDebug
```

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

.\gradlew.bat :app:assembleDebug
```

产物路径：

```
app/build/outputs/apk/debug/app-debug.apk
```

发布版（需配置签名）：

```bash
./gradlew :app:assembleRelease
```

### 3. 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或把 APK 传到手机直接点击安装。

### 4. 常见问题

| 问题 | 解决 |
| --- | --- |
| `DexWorkAction` / `graph.bin` 缺失 | 增量构建污染，删除 `app/build/intermediates/dex` 与 `desugar_graph` 后重跑 |
| Kotlin 编译报缓存错误 | 删除 `app/build/tmp/kotlin-classes` 与 `app/build/kotlin` 后重跑 |
| native 库缺失 | 确认 `app/src/main/jniLibs/arm64-v8a/` 下有 `.so` |

---

## 四、命令行走廊调试（免 App）

adb / shell 环境**没有 seccomp 过滤器**，可直接运行 exploit 快速验证：

```bash
make ghostlock
adb push ghostlock /data/local/tmp/ghostlock
adb shell chmod 755 /data/local/tmp/ghostlock
adb shell /data/local/tmp/ghostlock
```

环境变量：

| 变量 | 说明 |
| --- | --- |
| `GHOSTLOCK_CORE` | 指定主线程绑定的 CPU 核（默认大核，回退 0/1） |
| `GHOSTLOCK_CONSUMER_CORE` | 指定消费者线程绑定的 CPU 核 |
| `GHOSTLOCK_TCP_ROUTE=0` | 强制走 pselect 路线（默认 6.1 内核走 TCP 路线） |

---

## 五、偏移提取

`tools/extract_rs` 可从 `boot.img`（+ 可选 `xbl_config.img`）、完整 OTA ZIP，或指向它们的 `http(s)` URL 中推导出内核偏移。

```bash
cargo build --release --manifest-path tools/extract_rs/Cargo.toml

# 从 boot.img 提取并注册
tools/extract_rs/target/release/ghostlock-extract boot.img \
      --xbl-config xbl_config.img --register

# 从 OTA ZIP 导出 JSON
tools/extract_rs/target/release/ghostlock-extract OTA.zip \
      --format json --out offsets.json
```

- `--register`：把偏移表存到 `src/kernels/<uname-release>/offsets.h`
- `--format c --out offsets.h`：导出独立的头文件

**预检（Preflight）**：提取器会先反汇编 `remove_waiter()`，已修复的内核以退出码 `6` 被拒绝，只有存在漏洞的内核才会继续。

**免重编译导入偏移**：点击 App 里的 **导入 offsets.json** 选择提取器生成的 JSON，或推到 `<GHOSTLOCK_HOME>/offsets.json`（默认 `/data/local/tmp`）。启动时 native 会先把当前 `uname -r` 与导入项比对，再进行匹配。

---

## 六、目录结构

```
Ki-Andriod-APP/
├── app/
│   ├── build.gradle                     # App 模块构建脚本（versionCode / versionName）
│   └── src/main/
│       ├── AndroidManifest.xml          # 权限、包可见性 <queries>、组件声明
│       ├── jniLibs/arm64-v8a/
│       │   ├── libghostlock.so          # CVE-2026-43499 漏洞利用
│       │   └── libextract.so            # 偏移提取器
│       ├── kotlin/com/KiYY/lost/
│       │   ├── GhostlockApplication.kt
│       │   ├── data/                    # Repository（exec exploit、调 ksud）
│       │   ├── domain/                  # 模型 / 用例 / 偏移匹配
│       │   ├── ksu/                     # UI：KsuHome、玻璃组件、教程弹窗
│       │   ├── ui/                      # ViewModel、MainActivity、两套 UI
│       │   └── util/                    # AutoStartHelper（社交跳转等）
│       └── res/                         # 字符串 / 颜色 / 图标 / 主题
├── src/
│   ├── core/                            # exploit C 源码
│   └── kernels/<uname -r>/offsets.h     # 各内核的偏移表
├── tools/extract_rs/                    # Rust 偏移提取器
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat
```

---

## 七、越狱流程

1. 打开 **Ki-PrisonBreak**，确认顶部显示的**内核版本与当前设备一致**
2. 点击 **执行越狱**
3. 等待 exploit 运行完成（日志区会输出进度）
4. 成功后，App 会提示 **去 KernelSU 管理器授予超级用户权限**
5. 在 KernelSU 里给 `com.KiYY.lost` 授予 root，App 即可检测越狱状态
6. 若显示内核不支持 → 用 **解析 boot.img / OTA 链接 / 导入 offsets.json** 添加

> 越狱本身（`libghostlock.so` 的执行）**不需要 root**；越狱成功后要调 `ksud` 关内核保护 / 写偏移才需要 root。

---

## 八、Credits & License

本项目基于以下项目，遵循 **Apache License 2.0**（见 [LICENSE](LICENSE)）：

- [NebuSec/CyberMeowfia](https://github.com/NebuSec/CyberMeowfia)
- [JoinChang/ghostlock-oneplus](https://github.com/JoinChang/ghostlock-oneplus)
- [x-spy/CVE-2026-43499-popsicle](https://github.com/x-spy/CVE-2026-43499-popsicle)
- 上游 GhostLock App（`YuKongA/ghostlock-app`）

**CVE**：CVE-2026-43499 — Linux kernel pselect/tree-waiter 竞态导致本地提权

---

## 九、免责声明

本 App 仅供**安全研究**与**在您自己拥有或已获授权的设备**上使用。请遵守所在国家/地区的法律法规，因使用本工具造成的一切后果由使用者自行承担。