# FloatingType

> 跟随键盘出现/隐藏的 Android 无障碍悬浮球 AI 语音输入：点球说话、再点停止，文字自动写入当前输入框。配合任意现有键盘使用，不自研输入法。

![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?logo=android&logoColor=white)
![Kotlin 2.0](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![targetSdk 34](https://img.shields.io/badge/targetSdk-34-blue)
![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue)

FloatingType 在**键盘弹出且输入框可编辑时出现，键盘收起时消失**，输入过程中一直常驻。点击悬浮球开始录音，再点停止，经阿里云百炼 DashScope 批量转写后，把文字自动写入当前焦点输入框。可选「AI 润色」模式：转写后再经 OpenAI 兼容接口润色，失败自动回退原文。每次会话（含失败）都会写入本地历史记录。

应用主页为底部两个 Tab：**历史记录**（默认）与**功能设置**。启动不强制权限引导，所有引导项集中在设置页。

## 功能特性

- **键盘感知的悬浮球**：状态驱动显隐——打字、移动光标、注入文字都不会让球消失；收起键盘后约 300ms 内自动隐藏；录音/转写/注入期间始终常驻。
- **两种输入模式**：直接转写 / AI 润色（转写后经 LLM 整理口语、错别字与标点，失败自动回退原文）。长按悬浮球 500ms 快速切换，球体徽标显示当前模式。
- **点按式录音**：点球开始（红色脉冲），再点停止（琥珀色旋转），注入成功绿色一闪；60 秒自动停止，最短 300ms。
- **本地历史记录**：Room 持久化，覆盖成功与失败会话，按日期分组；详情页支持复制、重新润色、删除。不保存音频。
- **可插拔引擎**：ASR 默认 DashScope `qwen-audio-3.1-asr-flash`；润色默认 DashScope OpenAI 兼容地址 + `qwen-plus`，可改为任意 OpenAI 兼容接口。端点、模型、Key、提示词均可在设置页修改。
- **现代化 UI**：Material 3 卡片式布局，Android 12+ 动态取色，浅色/深色主题，edge-to-edge。
- **隐私优先**：密码输入框不出球、不注入；API Key 默认仅存本机；请求直连官方或用户自配端点，无第三方中转。

## 工作原理

```text
点击悬浮球
  → microphone 前台服务录音（16kHz 单声道 PCM16，≤60s）
  → 再点停止 → WAV 编码 → DashScope 批量转写（Base64 Data URI）
  → [AI 润色模式] OpenAI 兼容 Chat Completions 润色（失败回退原文）
  → 无障碍 ACTION_SET_TEXT 注入当前焦点输入框（失败回退剪贴板粘贴）
  → 会话结束写入本地历史（含各阶段状态与错误）
```

显隐策略为**状态驱动**：无障碍事件只触发重新判定，真正决定显隐的是「键盘是否在屏 + 焦点是否可编辑」。键盘在屏通过 `getWindows()` 中的 IME 窗口（`TYPE_INPUT_METHOD`、高度 > 0）检测，其顶边同时用于把球定位到键盘上方。权限清单与无障碍事件/标志组合针对「键盘感知」场景调优，刻意不采用「打字/窗口事件即隐藏」的常见做法——那会在点击输入框后被键盘弹出事件立刻抹掉悬浮球。详见 `app/src/main/res/xml/floating_accessibility_service.xml` 注释与下文「关键设计决策」。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| 运行设备 | Android 7.0+（minSdk 24，targetSdk 34） |
| 构建 JDK | 17+（AGP 8.7.3 要求；推荐直接用 Android Studio 自带 JBR） |
| Android SDK | compileSdk 34（含 platform-tools / adb） |
| Gradle | 8.9（wrapper 自带，无需单独安装） |
| 服务 | 阿里云百炼 DashScope API Key（[官方控制台](https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key)创建）；润色可用任意 OpenAI 兼容接口 |

## 快速开始

### 构建安装

```bash
git clone <your-repo-url> FloatingType && cd FloatingType

export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开项目运行。

### 首次配置

1. 打开应用，默认进入**历史记录** Tab。若权限或 Key 未完成，顶部显示「完成设置」提醒卡，点击进入**功能设置**。
2. 在设置页逐项开启权限：**悬浮窗、无障碍（开启且确认已连接）、麦克风、通知、电池优化白名单**。
3. 填写 DashScope API Key（设置页提供官方控制台入口）。Key 默认仅保存在本机；开发者也可在 `local.properties` 预置，见下文「本地构建配置」。
4. 选择默认输入模式：**直接转写**或 **AI 润色**。

### 开始使用

1. 在任意应用点击输入框 → 键盘弹出、悬浮球出现。
2. 点击球说话 → 再点球停止 → 文字写入输入框，并生成一条历史记录。
3. **长按悬浮球 500ms** 在「转写 / 润色」间切换（写回默认设置）；录音/转写/注入中禁止切换。
4. 拖动球松手自动贴边；键盘弹出时球位于键盘上方。
5. 收起键盘（返回键 / IME「完成」）后球自动消失；再次点击输入框重新出现。

## 设置项

- **权限与引导**：原启动引导项全部在此，含 API Key 配置状态。
- **默认输入模式**：直接转写 / AI 润色；长按悬浮球同步修改。
- **DashScope ASR**：API Key、转写模型、服务端点（高级，支持业务空间专属域名）。
- **AI 润色**：Base URL、API Key、模型、提示词（可恢复默认）。默认地址可留空 Key 复用 ASR Key；自定义 Base URL 必须填写独立 Key，不会复用 DashScope Key。
- **悬浮球**：启用开关、大小（40–72dp，默认 52dp）、不透明度（0.3–1.0，默认 0.95），含实时预览。
- **数据与测试**：清空全部历史、测试输入框（可直接验证注入）。

## 本地构建配置（local.properties）

不想每次重装/清数据后都在 App 里重填端点、Key 和模型，可在根目录 `local.properties`（已被 gitignore）预置六个可选项：

```properties
asr.baseUrl=https://dashscope.aliyuncs.com/api/v1
asr.apiKey=sk-xxxxxxxx
asr.model=qwen-audio-3.1-asr-flash
polish.baseUrl=https://dashscope.aliyuncs.com/compatible-mode/v1
polish.apiKey=sk-xxxxxxxx
polish.model=qwen-plus
```

- **非空值在构建时写入 BuildConfig，并优先于 App 内设置**，重装/清数据后依然生效；改值后重新构建安装即可生效（无需 clean）。
- 留空或保持注释的键仍按原逻辑在 App 设置页填写、仅保存在本机 SharedPreferences。
- 被 `local.properties` 接管的字段在设置页只读，并标注来源键名；未接管的字段照常可编辑。
- `polish.apiKey` 留空时沿用原复用规则：仅当润色 Base URL 仍为默认 DashScope 兼容地址时复用 ASR Key；自定义地址必须配独立 Key。
- ⚠️ 此处配置会随 BuildConfig **打包进 APK**。`local.properties` 不入库，请勿分发含真实 Key 的构建；对外发布建议留空、改回 App 内填写。

## 架构

单模块（`:app`），包名 `com.avengeeker.floatingtype`。手动 `ServiceLocator` 依赖装配，无障碍服务为常驻 backbone。

| 模块 | 说明 |
| --- | --- |
| `accessibility/FloatingAccessibilityService` | 常驻 backbone：焦点/点击事件→立即判定；打字/窗口/键盘事件→节流重判（80ms 合并）；键盘收起后 300ms 宽限再隐藏；录音/转写/注入中常驻 |
| `accessibility/FocusEventPolicy` | 纯函数事件策略：EVALUATE / RECHECK / IGNORE（可单测） |
| `accessibility/BallVisibilityPolicy` | 纯函数显隐决策：键盘+可编辑焦点→显示，会话进行中→保持，宽限期内→重判（可单测） |
| `accessibility/InputFocusFinder` | 焦点输入框查找：`findFocus` 快路径 + 缓存 refresh + 有界树搜索（修复 Compose 虚拟节点返回非焦点 stub 的问题） |
| `accessibility/TextInjector` | 选区拼接 + `ACTION_SET_TEXT` 注入，剪贴板粘贴回退 |
| `overlay/OverlayBallManager` + `BallView` | WindowManager 悬浮球：拖动贴边、键盘上方定位、状态动画、模式徽标（转/润）、长按 500ms 切换模式 |
| `audio/AudioRecordingForegroundService` | microphone 类型前台服务（targetSdk 34 合规），AudioRecord 16kHz 单声道 PCM16，60s 自动停止 |
| `asr/DashScopeAsrEngine` | 官方非流式 HTTP API（multimodal-generation，Base64 WAV Data URI），默认模型 `qwen-audio-3.1-asr-flash`，可插拔 `AsrEngine` 接口 |
| `polish/OpenAiCompatiblePolishEngine` | OpenAI 兼容 Chat Completions 润色，默认 DashScope compatible-mode + `qwen-plus`，可插拔 `PolishEngine` 接口 |
| `pipeline/VoiceInputPipeline` | 状态机编排：idle→recording→transcribing→(polish)→injecting→idle；会话上下文 + 历史写入 |
| `history/*` | Room 本地历史：Entity/DAO/Database/Repository、`InputMode`、阶段状态、纯函数展示逻辑 |
| `permission/*` + `ui/PermissionChecklist` | 权限清单：悬浮窗/无障碍(开启+连接)/麦克风/通知/电池白名单，嵌入设置页 |
| `settings/SettingsRepository` + `ServiceConfigDefaults` | SharedPreferences 设置 + 纯函数配置优先级解析（local.properties > App 内设置 > 内置默认） |
| `ui/HistoryScreen` + `ui/HistoryDetailScreen` | 卡片式历史列表（日期分组、状态徽章）与详情页（复制/重新润色/删除） |
| `ui/SettingsScreen` | 设置 Tab：权限与引导、默认输入模式、ASR、AI 润色、悬浮球、数据与测试 |
| `ui/components/*` + `ui/theme/UiTokens` | 现代化卡片组件与设计 token（圆角、间距、语义状态色） |
| `core/ServiceLocator` | 轻量手动依赖装配，全部 application 作用域 |

网络请求使用 JDK 自带 `HttpURLConnection`，不引入 OkHttp/Retrofit 等第三方网络库；无第三方 DI、统计或中转 SDK。

## 关键设计决策

- **状态驱动显隐，而非事件驱动**：`TYPE_WINDOWS_CHANGED` / `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED` 等事件在键盘弹出、打字、注入文字时都会触发，直接按事件隐藏会让球在用户刚开始输入时就消失。现在事件只触发（节流）重判，显隐由 `BallVisibilityPolicy` 根据「IME 窗口在屏 + 可编辑焦点 + 会话状态 + 宽限期」纯函数决定。
- **纯函数策略 + 薄框架胶水**：事件分类、显隐决策、文本拼接、配置优先级、历史展示等核心逻辑都是无 Android 依赖的纯函数，配套单元测试；框架交互留在 Service/Manager 薄层。
- **Compose 焦点兜底**：Compose 等虚拟视图树的 `findFocus(FOCUS_INPUT)` 会返回非焦点 stub，用「缓存节点 refresh + 有界树搜索（≤400 节点）」兜底；树搜索仅在焦点/点击事件或 ≥400ms 间隔触发，避免性能问题。
- **targetSdk 34 录音合规**：录音必须走 `foregroundServiceType="microphone"` 前台服务，且需 `FOREGROUND_SERVICE_MICROPHONE` 权限。
- **BuildConfig 字段刻意非常量**：`local.properties` 预置值以 `new String("...")` 形式生成，避免 Kotlin/Java 编译期内联常量导致增量构建读不到新值（详见 `app/build.gradle.kts` 注释）。

## 历史记录

- 使用 Room 本地存储，不保存音频，按时间倒序展示，不自动限量。
- 仅在真正开始录音后建立会话；录音、转写、润色、注入任一阶段失败都会在会话结束时写入历史。未聚焦、未配置 Key 等前置提示不记录。
- 列表卡片显示最终文本摘要、时间、来源应用、时长与状态徽章；按日期分组。
- 详情页展示原始转写、润色结果、会话元数据与各阶段错误；支持复制原文/最终文本、重新润色（仅润色记录）、删除。
- 重新润色只更新历史结果，不会重新写入原输入框；详情页不提供重新注入，统一使用复制。

## 隐私与安全

- 密码输入框不出球、不注入（隐私保护）。
- API Key 默认由用户在应用设置页自行填写，仅存于本机 SharedPreferences，不打包进 APK；仅当在 `local.properties` 显式预置时才会随 BuildConfig 进入 APK（本地开发便利，见上文）。
- 请求直连官方或用户自配端点，无第三方中转；不保存音频；日志不记录 Key、Base64 音频或完整用户文本。
- 历史记录仅存本机 Room 数据库，可在设置页一键清空。

## 测试

```bash
./gradlew testDebugUnitTest   # 90 个单元测试
./gradlew assembleDebug
```

单元测试覆盖：事件策略、显隐决策（键盘/焦点/会话/宽限/节流）、权限清单、文本拼接、WAV 头、ASR 响应解析、请求体结构、润色 URL/Key/请求/响应解析、模式切换、历史状态摘要与日期分组、本地配置优先级、悬浮球几何、日志裁剪。

### 手测清单（需真机/模拟器）

- [ ] 底部两个 Tab 切换；详情页隐藏底部栏，返回回到列表；设置 Tab 返回回到历史
- [ ] 缺权限/Key 时历史页提醒卡，点击跳转设置；权限清单状态恢复刷新
- [ ] 多个 App（含 Compose 应用）点击输入框→键盘弹出→球出现并常驻；打字/移动光标/注入文字时球不消失
- [ ] 收起键盘（返回键、IME「完成」、切到无输入框页面）→球在约 300ms 内消失；再次点击输入框→球重新出现
- [ ] 键盘弹出动画过程中球跟随上移，最终停在键盘上方；录音中收起键盘球仍常驻
- [ ] 拖动球松手贴边；键盘弹出时球位于键盘上方
- [ ] 点击球录音（红色脉冲）→再点停止（琥珀色旋转）→文字注入（绿色一闪）
- [ ] 长按球切换模式，徽标与设置页同步；录音/转写/注入中禁止切换
- [ ] 直接转写生成历史；润色成功、失败回退原文、重新润色
- [ ] 录音过短/断网/注入失败生成失败记录；未聚焦/未配 Key 不记录
- [ ] 详情页复制、单条删除、清空全部历史
- [ ] 设置页测试输入框可直接验证注入
- [ ] 深色/浅色、字体放大、小屏宽度下卡片无重叠裁切

## 已知限制

- DashScope 旧文档模型 `sensevoice-v1` 已被官方下线，当前默认 `qwen-audio-3.1-asr-flash`（官方对 ≤5 分钟短音频的推荐）；模型与端点可在设置页修改（支持业务空间专属域名）。
- 录音上限 60 秒（自动停止并转写），最短 300ms。
- 显隐以「软键盘在屏」为准：收起键盘后即使输入框仍持有焦点，球也会消失；使用实体键盘（无软键盘窗口）时不出球。
- 键盘收起后有 300ms 宽限期并重新判定一次，避免切换输入法/窗口时闪烁。
- 注入失败时回退剪贴板粘贴；仍失败则提示重新聚焦输入框，并记录失败历史。
- 润色失败自动回退原文并标记历史；自定义润色地址缺少独立 Key 时同样回退原文。
- 历史在一次会话结束时写库；会话过程中被系统杀进程的极端情况不保证留下记录。
- 部分国产 ROM 对无障碍服务与后台保活有额外限制，建议按设置页引导加入电池优化白名单。

## 开发与维护

- 面向 AI 编码代理与后续维护者的仓库指南见 [AGENTS.md](AGENTS.md)：构建命令、架构不变量、常见陷阱、安全红线。
- 提交信息沿用中文 Conventional Commits（`feat:` / `fix:`，见 git log）。
- 改动核心逻辑请同步补充纯函数单元测试；涉及显隐/注入/录音的改动需按上文手测清单真机回归。

## 致谢

- 阿里云百炼 DashScope：语音转写与 OpenAI 兼容润色接口。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。

- 允许商业使用、修改、分发与私有使用，并包含贡献者的明确专利授权。
- 分发时需保留许可证与版权声明；修改过的文件需注明改动。
- 软件按「原样」提供，作者不承担任何担保与责任。

Copyright 2026 Aven.
