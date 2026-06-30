# 项目协作说明

本仓库是 Android 端 MaaNikke 项目，默认工作目录为 `F:\Codex\Nikke\Nikke_MAA`。后续 Android MaaNikke 的源码修改、构建验证和 Git 同步都在本仓库完成。

## 项目范围

本仓库只维护以下内容：

- `tools/android_maanikke_debug_apk/`：Android 调试 APK、UI、任务入口、资源同步和打包脚本。
- `tools/android_root_imagereader_probe/`：root/app_process 后端、虚拟显示、ImageReader 抓帧、输入注入和 MaaCore bridge。
- `tools/android_virtual_display_probe/`：早期虚拟显示探针，仅作保留对照。
- `tools/android_maanikke_debug_apk/assets/MaaSync/`：随 APK 内置的 MaaNikke 资源、OCR 证据和资源清单。
- `supports/`：项目依赖和支持组件。`supports/shizuku/` 随仓库提交；`supports/maacore_sdk/`、`supports/prebuilt/` 是本机缓存，不提交。
- `docs/`：Android MaaNikke 方案、任务映射、OCR/MAA 审计和采集模板。
- `NIKKE_ANDROID_MAA_DESIGN.md`：Android 端方案设计记录。
- `启动安卓投屏.bat`、`稳定安卓投屏.bat`：本机调试投屏入口。

不要把 APK、录屏、截图、实机日志、设备私有配置、密钥或大型本机依赖提交到 GitHub。构建产物和验证证据统一放在 `outputs/android_probe/`，该目录不提交。

## 技术路线

- NIKKE 默认运行在后端创建的 `1280x720@160dpi` 虚拟显示中；`1920x1080@240dpi` 作为同一套任务逻辑的兼容切换值。
- 坐标、OCR ROI 和触控探针以 `1280x720` 为语义基准，后端按分辨率配置做比例映射。
- 截图链路使用 `ImageReader` / native buffer；输入必须带目标 `displayId`，不要把坐标打到物理屏。
- 实时预览主路径为 `SurfaceView + native EGL`。Shizuku 设备优先通过 Shizuku `UserService` 渲染 `/data/local/tmp/maanikke_preview_frame.jpg`；root/同进程场景保留 native fallback 和 `PreviewFrameServer` LocalSocket/JPEG 兜底。
- 后端临时文件使用 `/data/local/tmp/maanikke_*`。
- 用户可见长期文件统一放入 `/storage/emulated/0/Documents/MaaNikke/`。
- APK 内置资源首次启动或升级后同步到 `/storage/emulated/0/Documents/MaaNikke/resource/`。
- 当前主线是“Android 控制壳 + MaaCore/MAA 资源执行层”：APK 负责虚拟显示、截图、输入、调试模式和证据导出；识别、OCR、模板匹配和 pipeline 分支优先向 MaaCore / MaaFramework 收敛。

## 运行边界

- 登录页只提示 `login_required`，不点击第三方登录按钮，不做登录绕过。
- 网络或服务器连接失败只返回 `network_retry_required`，不自动确认重试。
- 协同作战是限时开放玩法，当前暂缓。
- 任务节点结束后优先回大厅，不默认重启游戏。
- 调试模式进入奖励、购买、咨询等入口并展示提示，但跳过最终领取、购买、咨询确认和进入战斗等真实点击。
- 关闭调试模式才执行真实领取、购买或战斗相关点击。
- 涉及钻石、购买、招募、升级等高风险动作时，先做单任务验证，不直接跑完整真实流程。

## 常用命令

```powershell
# 构建 APK，并重建 root/ImageReader 后端 jar
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\build_debug_apk.ps1

# 跳过 native 预览库重编译，复用 supports/prebuilt
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\build_debug_apk.ps1 -SkipPreviewNativeBuild

# 本地 OCR 证据 ROI 回归，不连接设备、不点击游戏
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\scripts\validate_ocr_evidence.ps1

# 安装 APK
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 install -r .\outputs\android_probe\apk\MaaNikkeAndroidDebug.apk

# 推送后端 jar
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 push .\outputs\android_probe\root_ir_probe\maanikke-root-ir-probe.jar /data/local/tmp/maanikke-root-ir-probe.jar

# 打开 App
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -n com.codex.maanikke.debug/.MainActivity

# 运行完整调试工作流
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -a com.codex.maanikke.debug.RUN_WORKFLOW_DAILY_SAFE -n com.codex.maanikke.debug/.MainActivity

# 完整适配回归
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\run_full_adapter_regression.ps1
```

## 验证顺序

1. 离线验证：解析配置、检查入口任务、枚举缺失模板、确认 ROI 不越界。
2. 固定截图验证：模板匹配、OCR、裁剪区域和回归用例。
3. 只读实机验证：枚举 display、截图、识别页面，不点击。
4. dry run：打印将要执行的节点、坐标、等待和回退路径。
5. 单任务真实点击：先跑单个任务，不直接跑完整每日链路。
6. 完整流程：调试模式先跑，稳定后再关闭调试模式验证真实领取。

## 当前状态

- 2026-06-29：OCR 证据回归与 MaaCoreProbe 增强已同步；`validate_ocr_evidence.ps1` 可校验 JSON、图片尺寸、ROI 边界并裁剪 ROI。
- 2026-06-29：只读实机探针返回 `finalState=maacore_probe_native_ocr_ready`、`coreReady=true`、`controlUnitReady=true`、`bridgeOcrSucceeded=true`。
- App 设置页已有只读“运行环境预检”入口，可检查 Shizuku/root shell、`/data/local/tmp`、`Documents/MaaNikke`、目标游戏包、资源目录、证据文件和 MaaCore 相关库。
- 2026-06-30：爬塔企业塔按 MaaNikke 逻辑修正。未勾选无限塔挑战时，进入初始选择页后只点击下方四个企业塔区块；1280x720 基准坐标为 `500,515`、`594,515`、`688,515`、`782,515`。
- 2026-06-30：正常模式单任务 `claim_climb_tower` 已验证可进入第一场企业塔战斗；残留问题是战斗后继续第二个企业塔的收口仍待补强，已观测到 `finalState=climb_tower_company_2_fight_gate_not_confirmed`。
- 2026-06-30：项目依赖和支持组件已收口到 `supports/`；`outputs/` 只保留构建产物和验证证据。

## Git 约定

- 本仓库只提交 Android MaaNikke 工程源码、必要资源、文档和小型编译依赖。
- 每轮实机调试稳定后更新本文件的当前状态。
- 不提交 `outputs/`、APK、截图、录屏、日志、设备证据、本机密钥、`supports/maacore_sdk/` 或 `supports/prebuilt/`。
- 不执行封包、注入、内存读取、登录绕过或反检测逻辑。
