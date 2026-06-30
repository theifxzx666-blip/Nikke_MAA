# Android MaaNikke MaaCore 迁移方案

本文用于替代“继续堆 Java 坐标和颜色识别”的旧路线。新的主线参考 MAA-Meow：Android 端负责虚拟显示、截图、输入、权限、日志和 UI；识别、OCR、模板匹配、任务流尽量交给 MaaCore / MaaFramework 与 PC MaaNikke 资源。

## 一、结论

后续安卓端主方案调整为：

1. 短期保留现有 Android Debug APK 作为安全控制壳
2. 停止扩大 Java 里的颜色识别、纯坐标兜底和超长任务函数
3. 新增 MaaCore Android 执行层，先跑通最小任务闭环
4. 复用 PC MaaNikke 的 `interface.json`、`pipeline/task/*.json`、`image/*`、`model/ocr/*`
5. 参数设置以 PC MaaNikke 语义为准，Android UI 只做展示、保存和传参
6. 技术实现尽量贴近安卓牛 / MAA-Meow，尤其是 APK 内置 native core/control unit、Shizuku/root 后台执行、资源同步、日志和诊断；识别证据与任务知识继续使用 NIKKE MAA / PC MaaNikke 的截图、模板、OCR 词表和 pipeline，不新增独立手机素材库

## 二、目标架构

```text
Android UI
  - 任务勾选
  - 参数设置
  - 调试模式
  - 日志/截图/证据导出
        |
        v
Android Runner
  - 创建 1280x720 虚拟显示
  - 启动/接管 NIKKE
  - ImageReader 截图
  - displayId 输入注入
  - 截图与动作证据
        |
        v
MaaCore / MaaFramework
  - 读取 PC MaaNikke 资源
  - OCR / TemplateMatch
  - pipeline next/on_error/retry
  - option override
        |
        v
NIKKE 虚拟显示
```

## 三、分阶段计划

### P0：止血与安全收口

目标：在 MaaCore 接入前，当前 APK 只做安全预览和少量低风险任务。

要做：

1. 调试模式保持默认开启
2. 购买、领取、咨询确认、进入战斗、扫荡等动作必须有明确页面确认
3. 识别不到目标按钮时停，不继续点相邻坐标
4. normal 模式不再做完整每日回归，只允许单任务、单功能验证
5. 每个异常都输出截图、日志、finalState，不再靠肉眼猜

当前拦截战特殊约束：

1. 只识别到橙色“每周快速战斗/快速战斗”按钮才允许点击扫荡
2. 识别到红色“进入战斗/挑战 BOSS”且未识别到扫荡时必须停止
3. 调试模式只停在 boss/扫荡按钮预览，不点扫荡

### P1：MaaCore 最小闭环

目标：先不追求全任务，只证明 Android 上能用 MaaCore 资源识别并执行一个安全任务。

要做：

1. 引入 Android 可用的 MaaCore native 库
2. 建立 Java/Kotlin 到 MaaCore 的最小 JNI/JNA 调用层
3. 实现 Android Controller：
   - `screencap` 来源接 ImageReader 最新帧
   - `click/swipe/key` 走当前 displayId 输入注入
   - 所有动作写入日志和截图证据
4. 加载 PC MaaNikke resource 目录
5. 跑通只读或调试任务，例如：
   - 回大厅页面识别
   - 邮件页/Pass 页只读识别
   - 拦截战页 OCR “快速战斗”只读识别

验收：

1. MaaCore 能读取同一张 Android 截图并产出 OCR 或模板命中日志
2. MaaCore 能调用 Android Controller 点击一个安全入口
3. 调试模式下能停在目标页并导出识别结果

当前探针状态：

1. `MaaCoreProbe` 入口已接入 APK：任务 id 为 `maacore_probe`，intent 为 `com.codex.maanikke.debug.RUN_MAACORE_PROBE`，App 小工具页有 “MaaCore探针” 按钮。
2. 2026-06-26 实机验证通过控制壳最小探针：能创建虚拟显示、启动/接管游戏、等待 ImageReader 帧、解码 `1280x720` 截图并导出报告；本轮无点击动作，`actionCount=0`。
3. 初始证据目录：`outputs/android_probe/maacore_probe_stub_20260626-192844/`；结果为 `frameDecoded=true`，`resourceReady=false`，`libraryReady=false`。
4. 已将 PC MaaNikke v2.0.9 的 `resource/base` 推送到 `/storage/emulated/0/Documents/MaaNikke/resource/base`，复测证据目录：`outputs/android_probe/maacore_probe_missing_library_20260626-193714/`；结果为 `finalState=maacore_probe_missing_library`，`resourceReady=true`，`pipelineReady=true`，`ocrModelReady=true`，`resourcePayloadReady=true`，`libraryReady=false`。
5. 已下载 MaaFramework 官方 Android aarch64 v5.11.1 包，将 `bin` 目录同步到 `/storage/emulated/0/Documents/MaaNikke/lib` 和 `/data/local/tmp/maacore`；探针已兼容旧名 `libMaaCore.so` 与新名 `libMaaFramework.so`。
6. native load check 已通过，证据目录：`outputs/android_probe/maacore_probe_native_load_ready_20260626-200349/`；结果为 `finalState=maacore_probe_ready_for_native_call`，`libraryPath=/data/local/tmp/maacore/libMaaFramework.so`，`nativeLoadReady=true`，`nativeLoadError=`。
7. 本轮截图停在游戏启动免责声明页，不是拦截战页，所以 `quickBattleRoiVisibleByJavaFallback=false` 属于预期结果。
8. 探针日志显示虚拟显示从创建到稳定非黑帧约 17 秒；后续 MaaCore 调用前必须先等到可解码帧和页面稳定，不能在页面未加载完时采样。
9. native bridge 已打通 `MaaCustomController` 文件截图识别链路；AndroidNativeController 仍因外部 control-unit 缺少 `GetLockedPixels` / `DispatchInputMessage` 等导出而不可直接接管真实屏幕，当前采用 ImageReader 导出的 PNG 喂 MaaFramework OCR 作为过渡方案。
10. 2026-06-26 拦截战页验证通过：`debug_claim_interception` 能进入异常个体 BOSS 详情页，MaaCore OCR 在 PC pipeline 的 `quickbattle` ROI `[642,582,193,55]` 上识别到“每周快速战斗”，证据见 `outputs/android_probe/debug_interception_no_attempts_probe_20260626-214159/`。
11. 同一证据显示“每周快速战斗”按钮为灰态不可点击，且下方红色 `进入战斗` 是禁区；过渡期 normal 模式必须只在颜色按钮态确认可点击时才扫荡，OCR 命中但按钮灰态时返回 `interception_quick_battle_disabled*`，不得点击挑战/进入战斗。
12. 2026-06-29 `MaaCoreProbe` 增强为资源/证据/桥接综合只读报告：证据见 `outputs/android_probe/maacore_probe_enhanced_20260629-111420/`，结果为 `finalState=maacore_probe_native_ocr_ready`、`actionCount=0`、`coreReady=true`、`controlUnitReady=true`、`evidenceCasesReady=true`、`missingEvidenceCount=2`、`bridgeOcrSucceeded=true`。
13. 本地 OCR 证据回归工具已接入：`tools/android_maanikke_debug_apk/scripts/validate_ocr_evidence.ps1` 会校验 JSON、图片尺寸、ROI 边界并裁剪 ROI 到 `outputs/android_probe/ocr_regression_*`；当前证据 `outputs/android_probe/ocr_regression_20260629-110536/` 显示 5 个启用 ROI 通过、2 个禁用待补。
14. App 设置页新增只读“运行环境预检”入口，用当前控制器模式检查 Shizuku/root shell、`/data/local/tmp`、`Documents/MaaNikke`、目标游戏包、资源目录、证据文件和 MaaCore 相关库；该入口只写日志，不启动游戏、不创建虚拟显示、不点击任务。

### P2：PC MaaNikke 资源接入

目标：让 Android 端不再手写大部分任务识别逻辑，而是执行 PC MaaNikke 的任务规格。

要做：

1. 建立资源目录同步规则：
   - `resource/base/pipeline/default_pipeline.json`
   - `resource/base/pipeline/task/*.json`
   - `resource/base/image/*`
   - `resource/base/model/ocr/*`
2. 建立 option 映射：
   - Android UI 参数 -> PC `interface.json` option
   - option -> pipeline override
3. 建立 Android 专用 override：
   - 坐标基准仍为 `1280x720`
   - 必要时只覆盖 ROI/坐标，不复制整条 pipeline
4. 将高频任务逐个迁移：
   - 邮件、Pass、好友点数
   - 前哨防御
   - 商店免费刷新/免费商品
   - 派遣公告栏
   - 咨询和送礼
   - 拦截战
   - 爬塔

验收：

1. 每个任务都有“PC pipeline 节点 -> Android 运行结果”的对照日志
2. 参数勾选能明确改变 pipeline override，而不是只改变 Java 分支
3. 失败时能指出卡在哪个 pipeline 节点、识别命中了什么、缺了什么

### P3：稳定化

目标：从“能跑”变成“可维护、可回归”。

要做：

1. 建固定截图回归集，并用 `tools/android_maanikke_debug_apk/scripts/validate_ocr_evidence.ps1` 做 ROI 越界和裁剪回归
2. 建单任务 dry-run 回归
3. 建调试模式完整链路回归
4. 建 normal 单任务白名单
5. 每次 PC MaaNikke 更新后自动比对：
   - interface option 变化
   - task pipeline 变化
   - image/model 文件变化

## 四、开发边界

继续保留：

1. 当前虚拟显示与 ImageReader 截图链路
2. displayId 输入注入
3. 实时预览
4. 日志导出和截图证据
5. 调试模式语义

逐步减少：

1. Java 颜色采样识别
2. 纯坐标页面判断
3. 超长任务函数
4. Android 端单独维护的任务语义

不做：

1. 封包、注入、内存读取
2. 登录绕过
3. 未识别页面连续点击
4. normal 模式全流程冒险验证

## 五、下一步推荐

下一步不要继续全任务调参，先完成 P1 最小闭环的 native/resource 部分：

1. 补 Android NDK/CMake 或等价 native 构建链，用 C/C++ 薄桥接入 `MaaCustomControllerCallbacks`
2. 把 `MaaCoreProbe` 从 load check 扩展为真实 native 调用：读取当前 ImageReader 截图，输出 OCR/模板命中日志
3. 再拿拦截战页面继续验证 `quickbattle`：当前 MaaFramework 已能在同 ROI 读到“每周快速战斗”，下一步要等有可扫荡次数时确认按钮颜色可点击和确认弹窗；红色“挑战 BOSS/进入战斗”继续是 no-click 停止信号
4. 再迁移一个低风险只读任务，例如 Pass/邮件页面识别

做到这一步后，再决定是完整迁移到 MaaCore 执行，还是保留 Android Java Runner 只作为过渡层。

## 六、MAA-Meow v0.16.1 借鉴点

2026-06-29 已检查 `MaaMeow-v0.16.1-x64.apk`。该版本对本项目最有价值的借鉴点如下：

1. APK 内置 native core 与 control unit：`libMaaCore.so`、`libMaaAndroidNativeControlUnit.so`、`libMaaUtils.so`。
2. APK 内置 NCNN OCR 模型：`PaddleOCR/det/*.ncnn.*`、`PaddleOCR/rec/*.ncnn.*`、`PaddleCharOCR/*/*.ncnn.*`。
3. APK 内置完整 `assets/MaaSync/MaaResource` 和 `asset_manifest.json`，资源版本由包内清单统一管理。
4. APK 内置 `assets/shizuku.apk`，同时强化 Shizuku 引导、服务切换和权限状态提示。

对 Android MaaNikke 的落地建议：

1. 保留当前 `assets/MaaSync/MaaResource/resource/base` 和 `assets/MaaSync/OcrEvidence` 的同步方式，但把证据图从“随包保存”升级为“可自动回归”的 OCR/模板用例。
2. `MaaCoreProbe` 后续增加 NCNN 模型路径探测，兼容 `PaddleOCR` / `PaddleCharOCR`，不要只检测旧 ONNX 目录。
3. 后续如果引入 MaaCore v6.x，需要把 native core、control unit、OCR 模型、资源版本清单成套升级，不要只替换单个 `.so`。
4. Shizuku 侧补充能力矩阵：服务连接、授权状态、当前 UID、虚拟显示能力、截图能力、输入注入能力、公开目录写入、`/data/local/tmp` 写入。

完整评估见 `outputs/android_probe/maameow_v0161_review_20260629.md`。

2026-06-29 迭代落地：

1. `MaaCoreProbe` 报告新增 `ocrModelType`、`ocrModelPath`、`ocrModelDetail`，可区分 `onnx`、`ncnn`、`legacy_ncnn`、`none`。
2. `ProbeConfig.MAACORE_NCNN_OCR_RELATIVE_CANDIDATES` 增加 `PaddleOCR` / `PaddleCharOCR` 及常见相对路径，用于后续接入安卓牛式 NCNN OCR 资源。
3. `tools/android_maanikke_debug_apk/assets/MaaSync/OcrEvidence/manifest.json` 升级到 v2，补充分辨率、capture_state、PC pipeline 节点、ROI 和 expected_text。
4. 新增 `outputs/android_probe/ocr_regression_cases.json` 与 APK 内 `assets/MaaSync/OcrEvidence/ocr_regression_cases.json`，作为后续 OCR/模板回归用例入口。
