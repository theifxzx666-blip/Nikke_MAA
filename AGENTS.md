# 项目协作说明

当前目录是独立的安卓端 MaaNikke 项目，不再混放本地资料库、PC 自动化和联盟突袭工具。

## 边界

只在本仓库处理以下内容：

- `tools/android_maanikke_debug_apk/`：Android 调试 APK、UI、任务配置、MAA 资源。
- `tools/android_root_imagereader_probe/`：root/app_process 后端、虚拟显示、ImageReader 抓帧、输入注入。
- `tools/android_virtual_display_probe/`：早期虚拟显示探针，仅作对照。
- `NIKKE_ANDROID_MAA_DESIGN.md`：安卓端设计记录。
- `启动安卓投屏.bat`、`稳定安卓投屏.bat`：本机调试投屏启动脚本。

不要把以下内容加入本仓库：

- GameKee/NIKKE 资料抓取缓存、角色图标、xlsx/html 导出。
- PC 可见窗口自动化脚本。
- 联盟突袭/公会战工具。
- `_ref_*` 外部参考快照。
- APK、录屏、截图、实机日志、`outputs/` 构建与验证证据。

## 当前技术路线

- NIKKE 运行在后端创建的 `1280x720@160dpi` 虚拟显示中。
- 截图链路使用 `ImageReader` / native buffer。
- 输入必须带目标 `displayId`，不要把坐标直接打到物理屏。
- 实时预览使用 `PreviewFrameServer` 的 LocalSocket/JPEG 流式预览，不要退回高频 PNG 文件轮询。
- 后端临时文件使用 `/data/local/tmp/maanikke_*`。
- 用户可见长期文件统一放入 `/storage/emulated/0/Documents/MaaNikke/`。

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

## Git 约定

- 本仓库只提交安卓 MAA 工程源码和必要资源。
- 每轮实机调试稳定后再提交。
- 不提交 `outputs/`、APK、截图、录屏、日志、设备证据或本机密钥。
- 不执行封包、注入、内存读取、登录绕过或反检测逻辑。
