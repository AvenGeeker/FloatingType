# AGENTS.md

面向 AI 编码代理与后续维护者的仓库工作指南。产品介绍、用户文档与手测清单见 [README.md](README.md)。

## 项目概览

FloatingType 是一个 Android 无障碍悬浮球 AI 语音输入应用：键盘弹出且输入框可编辑时出球，点球录音 → DashScope 批量转写 → 可选 OpenAI 兼容接口润色 → 无障碍注入当前焦点输入框，会话结束写入本地 Room 历史。

- 单模块 Gradle 项目：`:app`，包名 / applicationId `com.avengeeker.floatingtype`。
- 技术栈：Kotlin 2.0.0 + Jetpack Compose（Material 3，BOM 2024.04.01）、Room 2.6.1（KSP）、kotlinx-coroutines 1.8.1、JUnit 4。
- 版本：AGP 8.7.3 / Gradle wrapper 8.9 / KSP 2.0.0-1.0.24；compileSdk = targetSdk = 34，minSdk = 24；JVM target 11。
- 刻意保持轻依赖：网络用 JDK `HttpURLConnection`，DI 用手动 `core/ServiceLocator`。**不要**引入 OkHttp/Retrofit/Hilt/统计 SDK，除非维护者明确要求。
- 当前 `versionCode = 1`、`versionName = "1.0"`（`app/build.gradle.kts`）；release 未配置签名，`isMinifyEnabled = false`。
- 许可证：Apache-2.0（根目录 `LICENSE`，Copyright 2026 Aven）；仓库级 LICENSE 即可，新增源文件无需逐个添加许可证头。

## 环境与常用命令

构建需要 **JDK 17+**（AGP 8.x 要求）。本机 `/usr/libexec/java_home` 默认指向 JDK 11，直接跑 Gradle 会失败；使用 Android Studio 自带 JBR：

```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'

./gradlew assembleDebug          # 构建 debug APK
./gradlew testDebugUnitTest      # 单元测试（当前 90 个，必须全绿）
./gradlew assembleRelease        # release（未签名、未混淆）
./gradlew connectedDebugAndroidTest  # 仪器测试（目前仅模板，需真机/模拟器）

~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- 仓库无 CI；提交前在本地跑 `testDebugUnitTest` + `assembleDebug` 验证。
- `local.properties` 提供 `sdk.dir` 与可选服务预置（见下文），已被 gitignore，**不要提交**。
- 测试依赖 `org.json` 是 `testImplementation`（Android 运行时自带 org.json，JVM 单测需要真实现）；不要挪到 `implementation`。

## 目录结构

源码根：`app/src/main/java/com/avengeeker/floatingtype/`

| 包 | 职责 | 关键文件 |
| --- | --- | --- |
| `accessibility/` | 常驻 backbone 与显隐/焦点/注入 | `FloatingAccessibilityService`、`FocusEventPolicy`、`BallVisibilityPolicy`、`InputFocusFinder`、`TextInjector` |
| `overlay/` | WindowManager 悬浮球 | `OverlayBallManager`（手势/定位）、`BallView`（绘制/动画）、`BallMetrics`、`BallState` |
| `audio/` | 录音 | `AudioRecordingForegroundService`（microphone FGS）、`RecorderController`、`WavEncoder` |
| `asr/` | 语音转写 | `AsrEngine`（接口）、`DashScopeAsrEngine`、`AsrResponseParser` |
| `polish/` | AI 润色 | `PolishEngine`（接口）、`OpenAiCompatiblePolishEngine`、`PolishConfig`、`PolishResponseParser` |
| `pipeline/` | 会话状态机编排 | `VoiceInputPipeline` |
| `history/` | Room 历史 | `AppDatabase`（v1）、`InputHistoryEntity`、`HistoryDao`、`HistoryRepository`、`HistoryPresentation`、`InputMode`、`StageStatus` |
| `permission/` | 权限状态 | `PermissionRepository`、`PermissionStatus` |
| `settings/` | 设置与配置优先级 | `SettingsRepository`（SharedPreferences）、`ServiceConfigDefaults`（纯函数） |
| `ui/` | Compose 界面 | `MainActivity` 双 Tab、`HistoryScreen`、`HistoryDetailScreen`、`SettingsScreen`、`PermissionChecklist`、`components/`、`theme/UiTokens` |
| `core/` | 手动依赖装配 | `ServiceLocator`（application 作用域） |
| `util/` | 工具 | `LogUtils`（日志裁剪）、`Haptics` |

其他关键路径：

- `app/src/main/res/xml/floating_accessibility_service.xml` — 无障碍订阅配置（注释说明了状态驱动显隐的设计原因）。
- `app/src/main/AndroidManifest.xml` — 权限与两个 service 声明。
- `app/schemas/` — Room 导出 schema（v1），随代码入库。
- `app/src/test/` — 单元测试，按包镜像主源码。
- `gradle/libs.versions.toml` — 版本目录，依赖升级只改这里。

## 核心不变量（改代码必须保持）

1. **显隐是状态驱动，不是事件驱动。** 无障碍事件只触发重判：焦点/点击→立即 `refresh()`；打字/窗口/内容变化→节流重判（`RECHECK_MIN_INTERVAL_MS = 80ms` 合并）。真正的决策在纯函数 `BallVisibilityPolicy`：IME 窗口在屏 + 可编辑焦点→显示；会话进行中（录音/转写/注入）→无条件常驻；键盘收起→`HIDE_GRACE_MS = 300ms` 宽限后重判再隐藏。**不要**恢复「收到某事件就隐藏」的写法（键盘弹出事件会在用户刚点击输入框时立刻抹掉球）。
2. **键盘检测靠 IME 窗口。** 遍历 `getWindows()` 找焦点应用窗口与 `TYPE_INPUT_METHOD` 且高度 > 0 的窗口；IME 顶边用于球定位。`IME_SETTLE_MS = 320ms` 处理键盘弹出动画。实体键盘（无 IME 窗口）不出球是预期行为。
3. **过滤自身事件。** 本应用 UI 与悬浮球自身会发 content change，`onAccessibilityEvent` 里按 `packageName` 过滤，否则会形成布局反馈循环。新增事件处理时保持该过滤。
4. **Compose 焦点兜底不能删。** 虚拟视图树 `findFocus(FOCUS_INPUT)` 返回非焦点 stub；`InputFocusFinder` 用缓存节点 refresh + 有界树搜索（`MAX_VISITED_NODES = 400`）兜底，树搜索节流 `TREE_SEARCH_MIN_INTERVAL_MS = 400ms`（焦点/点击事件可强制触发）。
5. **线程模型：** `VoiceInputPipeline` 所有状态迁移在主线程（`Dispatchers.Main.immediate` + main Handler）；网络在引擎内部 `Dispatchers.IO`。状态暴露为 `StateFlow<BallState>`。不要把网络或磁盘 IO 搬到主线程，也不要在引擎里碰 UI。
6. **历史写入语义：** 真正开始录音才建立 Session；会话结束时一次性写库，成功与失败（录音/转写/润色/注入各阶段 `StageStatus` + 错误）都记录；未聚焦、未配 Key 等前置失败**不**记录。重新润色只更新历史，不重新注入。
7. **配置优先级：** `local.properties` → BuildConfig（非空时）> App 内 SharedPreferences > 内置默认，统一走纯函数 `ServiceConfigDefaults.resolve / resolveBaseUrl`（后者去尾部斜杠）。被 local 接管的字段 UI 只读并标注来源（`*FromLocal` 标志）。新增设置项必须同时支持这三级与只读标注。
8. **BuildConfig 字段刻意非常量。** `app/build.gradle.kts` 用 `new String("...")` 生成字段，避免编译期内联导致增量构建读不到 `local.properties` 新值。**不要**「优化」成字符串字面量。
9. **润色 Key 复用规则：** `polish.apiKey` 为空时，仅当润色 Base URL 仍为默认 DashScope compatible-mode 地址才复用 ASR Key；自定义地址必须独立 Key，缺失则回退原文。
10. **录音合规与参数：** targetSdk 34 必须走 `foregroundServiceType="microphone"` 前台服务 + `FOREGROUND_SERVICE_MICROPHONE` 权限。AudioRecord 16kHz 单声道 PCM16；`MAX_DURATION_MS = 60_000` 自动停止，`MIN_DURATION_MS = 300` 过短报错。WAV 头由 `WavEncoder` 生成（有单测）。
11. **注入链路：** 选区拼接（`TextInjector.splice`，单测 `TextSpliceTest`）+ `ACTION_SET_TEXT`，失败回退剪贴板粘贴，再失败提示重新聚焦并记失败历史。**密码输入框不出球、不注入**，任何改动不得破坏该隐私保护。
12. **Room 迁移纪律：** `AppDatabase` version = 1，`exportSchema = true`，schema 在 `app/schemas/`。改 Entity 必须：版本号 +1、提供 `Migration`（禁止 `fallbackToDestructiveMigration` 丢用户历史）、提交新 schema JSON。
13. **无障碍订阅配置联动：** 改 `floating_accessibility_service.xml` 的 eventTypes/flags 时，同步检查 `FocusEventPolicy` 分类与其单测；`notificationTimeout=10` 等参数为实测调优值，改动需真机验证。
14. **日志隐私：** 不记录 API Key、Base64 音频、完整用户文本；只记错误码、HTTP 状态、request id、尺寸与时序；远端错误体用 `LogUtils.clip`（默认 2000 字符）裁剪后记录。

## 代码风格与约定

- Kotlin official code style（`kotlin.code.style=official`），4 空格缩进；JVM target 11，可用 Kotlin 2.0 语言特性。
- **核心逻辑写成无 Android 依赖的纯函数/对象**（参考 `FocusEventPolicy`、`BallVisibilityPolicy`、`ServiceConfigDefaults`、`HistoryPresentation`），框架胶水保持薄；新逻辑必须配 JUnit 4 单测（`app/src/test/` 按包镜像）。
- 代码注释/KDoc 用英文，解释「为什么」（现有风格）；用户可见文案用中文，集中在 `res/values/strings.xml`，不要硬编码到 Composable。
- UI 遵循 `ui/theme/UiTokens`：Material 3 卡片、16dp 圆角与页面边距、12dp 组间距、8dp 间距网格、触控目标 ≥48dp；Android 12+ 动态取色，低版本回退品牌靛紫；浅色/深色与 edge-to-edge 都要兼顾。
- 设置变更通过 `SettingsRepository.notifyChanged()` 广播，监听方（如无障碍服务）据此刷新；新增设置项沿用该机制，不要另起监听体系。
- 提交信息：中文 Conventional Commits（`feat: ...` / `fix: ...`），参考 git log。分支前缀 `codex/`（代理创建分支时）。
- 依赖升级只改 `gradle/libs.versions.toml`；升级 AGP/Kotlin/KSP 注意三者配套（KSP 版本绑定 Kotlin 版本）。

## 测试要求

- 提交前必跑：`./gradlew testDebugUnitTest`（90 个）与 `./gradlew assembleDebug`。
- 新增/修改纯函数逻辑必须同步增改单测；解析类测试用真实响应结构（参考 `AsrResponseParserTest`、`PolishResponseParserTest`）。
- 显隐、注入、录音、权限等框架行为无法 JVM 单测覆盖：按 README「手测清单」真机回归，至少覆盖 Compose 应用输入框、键盘弹起/收起、录音中收起键盘、长按切换模式。
- `androidTest` 目前只有模板（`ExampleInstrumentedTest`），不要声称仪器测试已覆盖业务。

## 安全红线

- **绝不提交真实 API Key。** `local.properties` 已 gitignore；提交前检查 diff 与新增文件，确认无 Key、无含 Key 的构建产物。
- 预置 Key 的 BuildConfig 构建仅限本机调试；对外分发的构建必须留空 `local.properties` 相关键。
- 不新增第三方中转、统计、崩溃上报 SDK；请求只发往官方或用户自配端点。
- 不削弱密码框保护、不记录用户完整文本/音频（见不变量 11、14）。

## 发布检查清单

1. `app/build.gradle.kts` 递增 `versionCode` / `versionName`。
2. 清空 `local.properties` 中六个服务键后重新构建（避免 Key 进 APK）。
3. release 目前未签名、未混淆：对外发布前配置签名；若开启 minify，需验证 Room/KSP 生成类与反射路径。
4. 跑完单元测试 + README 手测清单（真机，含 Compose 应用与深色模式）。
5. 确认 `app/schemas/` 与数据库版本一致；README/AGENTS 中的数字（测试数、默认模型、常量）随改动更新。
