# 项目协作说明

当前目录是独立的安卓端 MaaNikke 项目，不再混放本地资料库、PC 自动化和联盟突袭工具。

## 边界

只在本仓库处理以下内容：

- `tools/android_maanikke_debug_apk/`：Android 调试 APK、UI、任务配置、MAA 资源。
- `tools/android_root_imagereader_probe/`：root/app_process 后端、虚拟显示、ImageReader 抓帧、输入注入。
- `tools/android_virtual_display_probe/`：早期虚拟显示探针，仅作对照。
- `docs/`：从历史工作区迁入的 Android MaaNikke 方案、任务映射、OCR/MAA 审计和采集模板。
- `NIKKE_ANDROID_MAA_DESIGN.md`：安卓端设计记录。
- `启动安卓投屏.bat`、`稳定安卓投屏.bat`：本机调试投屏启动脚本。

不要把以下内容加入本仓库：

- GameKee/NIKKE 资料抓取缓存、角色图标、xlsx/html 导出。
- PC 可见窗口自动化脚本。
- 联盟突袭/公会战工具。
- `_ref_*` 外部参考快照。
- APK、录屏、截图、实机日志、`outputs/` 构建与验证证据。

本机调试缓存可以放在 `outputs/android_probe/`，例如 MaaCore SDK、APK、root 后端 jar 和实机证据；这些内容用于本机复现和构建，不提交到 GitHub。

## 当前技术路线

- NIKKE 运行在后端创建的 `1280x720@160dpi` 虚拟显示中。
- 截图链路使用 `ImageReader` / native buffer。
- 输入必须带目标 `displayId`，不要把坐标直接打到物理屏。
- 实时预览主路径为 `SurfaceView + native EGL`：Shizuku 设备优先通过 Shizuku `UserService` 渲染 `/data/local/tmp/maanikke_preview_frame.jpg`，root/同进程场景保留 native fallback 和 `PreviewFrameServer` LocalSocket/JPEG 兜底；不要退回高频 PNG 文件轮询。
- 后端临时文件使用 `/data/local/tmp/maanikke_*`。
- 用户可见长期文件统一放入 `/storage/emulated/0/Documents/MaaNikke/`。
- APK 内置资源首次启动/升级后同步到 `/storage/emulated/0/Documents/MaaNikke/resource/`；`base/` 放 PC MaaNikke `resource/base`，`evidence/` 放 OCR 证据截图和 `ocr_regression_cases.json`，`resource-version.txt` 作为版本标记。
- 当前主线是“Android 控制壳 + MaaCore/MAA 资源执行层”：APK 继续负责虚拟显示、截图、输入、调试模式和证据导出；页面识别、OCR、模板匹配、pipeline 分支和 option override 后续优先迁到 MaaCore / MaaFramework。

## 任务约束

- 登录页只提示用户需要登录，不点击 QQ/微信，不做登录绕过。
- 网络或服务器连接失败只返回 `network_retry_required`，不自动确认重试。
- 协同作战是限时开放玩法，当前暂缓，不继续调坐标或判定。
- 任务节点结束后优先回大厅，不默认重启游戏。
- 调试模式进入奖励、购买、咨询等入口并展示提示，但跳过最终领取、购买、咨询确认和进入战斗等真实点击。
- 关闭调试模式才执行真实领取或购买。

## 常用命令

```powershell
# 构建 APK，并重建 root/ImageReader 后端 jar
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\build_debug_apk.ps1

# 本地 OCR 证据 ROI 回归，不连接设备、不点击游戏
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\scripts\validate_ocr_evidence.ps1

# 安装 APK
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 install -r .\outputs\android_probe\apk\MaaNikkeAndroidDebug.apk

# 推送后端 jar
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 push .\outputs\android_probe\root_ir_probe\maanikke-root-ir-probe.jar /data/local/tmp/maanikke-root-ir-probe.jar

# 打开 App
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -n com.codex.maanikke.debug/.MainActivity

# 完整适配回归
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\run_full_adapter_regression.ps1
```

## 验证顺序

1. 离线验证：解析配置、检查入口任务、枚举缺失模板、确认 ROI 不越界。
2. 固定截图验证：模板匹配、OCR、裁剪区域、JSON/CSV 输出。
3. 只读实机验证：枚举 display、截图、识别页面，不点击。
4. dry run：打印将要执行的节点、坐标、等待和回退路径。
5. 单任务真实点击：先跑单个任务，不直接跑完整每日链路。
6. 完整流程：调试模式先跑，稳定后再关闭调试模式验证真实领取。

## 当前状态

- 2026-06-29：OCR 证据回归与 MaaCoreProbe 增强已同步。`validate_ocr_evidence.ps1` 可校验 JSON、图片尺寸、ROI 边界并裁剪 ROI；混合工作区证据 `outputs/android_probe/ocr_regression_20260629-110536/` 显示 5 个启用 ROI 通过、2 个禁用待补。
- 2026-06-29：只读实机探针 `outputs/android_probe/maacore_probe_enhanced_20260629-111420/` 返回 `finalState=maacore_probe_native_ocr_ready`、`actionCount=0`、`coreReady=true`、`controlUnitReady=true`、`evidenceCasesReady=true`、`missingEvidenceCount=2`、`bridgeOcrSucceeded=true`。
- App 设置页新增只读“运行环境预检”入口，用当前控制器模式检查 Shizuku/root shell、`/data/local/tmp`、`Documents/MaaNikke`、目标游戏包、资源目录、证据文件和 MaaCore 相关库；该入口只写日志，不启动游戏、不创建虚拟显示、不点击任务。
- 2026-06-30：爬塔企业塔按 PC MaaNikke `climbtower.json` 逻辑修正。未勾选无限塔挑战时，进入“无限之塔初始选择页”后只点击下方四个企业塔区块；1280x720 基准坐标重校准为 `500,515`、`594,515`、`688,515`、`782,515`。修复初始选择页上方“无限之塔”文字导致的详情页误判，并补强企业塔详情页、白色编队页和蓝色“进入战斗”按钮识别；点击企业塔后加长等待并等待稳定场景，避免转场暗屏过早截图。
- 2026-06-30：正常模式单任务 `claim_climb_tower` 已验证可进入第一场企业塔战斗：OCR 命中 `进入战斗`，点击 `climb_tower_company_1_enter_fight_candidate baseX=760 baseY=670` 后进入 `climb_tower_battle_wait_70s`。当前残留问题是战斗后继续第二个企业塔的收口仍待补强，已观测到 `finalState=climb_tower_company_2_fight_gate_not_confirmed`；后续应优先补“战斗结束后返回/继续下一企业塔”的识别和回退。
- 2026-06-30：构建脚本新增 `-SkipPreviewNativeBuild`，可在本机 NDK `clang++.exe` 被占用或权限异常时复用 `outputs/android_probe/root_ir_probe/libmaanikke_preview_renderer.so` 正规打包 APK；默认构建路径仍会尝试重新编译 native 预览库。
- 2026-06-30：Android MaaNikke 相关源码、资源、方案文档和本机构建缓存已统一迁入 `F:\Codex\Nikke\Nikke_MAA`。后续只在该独立仓库内改 Android MAA；旧目录 `F:\Codex\Nikke\Nikke` 仅保留为历史混合资料库，不再作为 Android MAA 开发入口。

## Git 约定

- 本仓库只提交安卓 MAA 工程源码和必要资源。
- 每轮实机调试稳定后再提交。
- 不提交 `outputs/`、APK、截图、录屏、日志、设备证据或本机密钥。
- 不执行封包、注入、内存读取、登录绕过或反检测逻辑。
