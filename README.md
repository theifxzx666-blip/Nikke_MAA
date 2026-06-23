# Nikke MAA Android

这是安卓端 MaaNikke 自动化实验项目，仓库只保存 Android APK、root/ImageReader 后端、虚拟显示探针、MAA 资源与调试启动脚本。

本仓库从本地混合工作区 `F:\Codex\Nikke\Nikke` 拆出。GameKee 资料库、PC 可见窗口自动化、联盟突袭工具、截图录屏证据和构建产物都不属于这里。

## 目录

| 路径 | 说明 |
| --- | --- |
| `tools/android_maanikke_debug_apk/` | 手机端 APK 主工程、UI、任务入口、MAA 资源同步目录 |
| `tools/android_root_imagereader_probe/` | root/app_process 后端、虚拟显示、ImageReader 抓帧和输入注入 |
| `tools/android_virtual_display_probe/` | 早期虚拟显示探针，保留作对照 |
| `NIKKE_ANDROID_MAA_DESIGN.md` | 安卓端方案设计记录 |
| `启动安卓投屏.bat` | 启动调试 App 并打开物理屏投屏 |
| `稳定安卓投屏.bat` | 低负载守护式物理屏投屏 |

## 构建

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tools\android_maanikke_debug_apk\build_debug_apk.ps1
```

默认输出：

```text
outputs/android_probe/apk/MaaNikkeAndroidDebug.apk
outputs/android_probe/root_ir_probe/maanikke-root-ir-probe.jar
```

## 安装与调试

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 install -r .\outputs\android_probe\apk\MaaNikkeAndroidDebug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 push .\outputs\android_probe\root_ir_probe\maanikke-root-ir-probe.jar /data/local/tmp/maanikke-root-ir-probe.jar
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2fb37497 shell am start -n com.codex.maanikke.debug/.MainActivity
```

完整流程验证遵守：先调试模式、再单任务真实点击、最后完整真实流程。协同作战是限时开放玩法，暂缓适配。
