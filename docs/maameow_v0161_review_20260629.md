# MAA-Meow v0.16.1 对 Android MaaNikke 的借鉴评估

日期：2026-06-29

参考对象：

- GitHub：`https://github.com/Aliothmoon/MAA-Meow`
- 本地 APK：`C:/Users/Administrator/Downloads/MaaMeow-v0.16.1-x64.apk`

## 结论

MAA-Meow v0.16.1 对本项目最有价值的启发不是继续补坐标或补截图，而是三件事：

1. 把 OCR 和模板识别尽快交给 MaaCore / MaaFramework，并优先跟进 NCNN OCR 模型链路。
2. 把 Shizuku 做成完整的可诊断控制通道，而不是只做“能不能授权”的按钮。
3. 把资源、模型、证据和版本清单一起打入 APK，再同步到公开工作目录，保证离线可复现和版本一致。

## APK 结构观察

`MaaMeow-v0.16.1-x64.apk` 中观察到：

- native core：
  - `lib/x86_64/libMaaCore.so`
  - `lib/x86_64/libMaaAndroidNativeControlUnit.so`
  - `lib/x86_64/libMaaUtils.so`
  - `lib/x86_64/libopencv_world4.so`
  - `lib/x86_64/libonnxruntime.so`
- 内置资源：
  - `assets/MaaSync/asset_manifest.json`
  - `assets/MaaSync/MaaResource/...`
  - `assets/shizuku.apk`
- NCNN OCR 模型：
  - `assets/MaaSync/MaaResource/PaddleOCR/det/det.ncnn.*`
  - `assets/MaaSync/MaaResource/PaddleOCR/rec/rec.ncnn.*`
  - `assets/MaaSync/MaaResource/PaddleCharOCR/det/det.ncnn.*`
  - `assets/MaaSync/MaaResource/PaddleCharOCR/rec/rec.ncnn.*`

这说明它已经把“控制壳 + MaaCore + control unit + OCR 模型 + 资源清单”作为完整交付包处理。

## 可直接借鉴

### 1. NCNN OCR 模型链路

本项目当前内置的是 PC MaaNikke `resource/base/model/ocr/*.onnx`，并且 native 探针还处在过渡阶段。MAA-Meow v0.16.1 的 NCNN 模型更适合 Android 端长期路线：

- Android 上推理开销更低，适合多节点 OCR。
- 更适合在任务执行过程中做多次小 ROI OCR，而不是每次都承受 ONNX Runtime 的重成本。
- 后续可把拦截战、派遣、邮件、Pass 这类文案明确的节点作为 NCNN OCR 先行验证对象。

建议：不要直接替换现有 ONNX 资源；先在 `MaaCoreProbe` 增加“模型类型/路径探测”，同时兼容 `PaddleOCR/*.ncnn.*` 与旧 `model/ocr/*.onnx`。

### 2. MaaCore / control unit 成套打包

MAA-Meow APK 内置 `libMaaCore.so` 与 `libMaaAndroidNativeControlUnit.so`。本项目当前能加载 MaaFramework / bridge，也能做 ImageReader PNG -> OCR 过渡，但真正的 Android control unit 仍是缺口。

建议：

- 后续资源包结构增加 `assets/MaaSync/MaaLib/<abi>/` 或等价目录。
- 首次启动同步到 `/storage/emulated/0/Documents/MaaNikke/lib/` 和 `/data/local/tmp/maacore/`。
- `MaaCoreProbe` 输出 `coreReady`、`controlUnitReady`、`ocrModelReady`、`resourceVersion`，不要只输出一个模糊的 ready/fail。

### 3. Shizuku 体验与诊断

MAA-Meow 这版的 Shizuku 优化值得借鉴为“流程状态机”：

- Shizuku 快捷入口。
- 服务连接/授权/身份切换状态分开显示。
- 按 Android 版本和权限身份选择运行策略。
- 未满足条件时给出明确引导，不进入任务死循环。

本项目已有 Shizuku 授权、通道检测和管理入口，但还可以补：

- 显示当前 Shizuku UID / shell 身份 / root 身份。
- 任务开始前做能力矩阵检查：虚拟显示、截图、输入注入、公开目录写入、`/data/local/tmp` 写入。
- 如果游戏未安装，直接给出 `game_not_installed`，不要循环启动。

### 4. 后台与日志体验

MAA-Meow 提到后台静音、深色模式日志和 ViewModel 层优化。本项目当前是 Java Activity + 后台服务的轻量方案，不需要大重构，但可以借鉴两个低风险点：

- 日志分级更清晰：`session / task / step / recognition / action / result`。
- 长任务 UI 状态由后台任务结果驱动，避免 Activity 重建后状态丢失。

## 关于“把截图证据打进 APK 是否能提升准确度”

结论：单纯把截图证据打进 APK，不会直接提升线上识别率；真正提升的是以下三点：

1. 资源版本一致：APK 内置资源避免用户少拷、错拷、旧资源污染。
2. 离线回归稳定：同一批证据截图可以反复跑 OCR / 模板匹配回归。
3. ROI 与模板质量可追踪：每张证据图都有来源、分辨率、任务、标注和日期。

如果这些截图只是“放在 APK 里但运行时不用”，准确度不会变。要提升可靠性，应该把证据图纳入：

- OCR ROI 回归集。
- 模板匹配回归集。
- 每个任务的 page gate 验证。
- 异常时自动导出“原图 + ROI 裁剪 + OCR 文本 + 命中模板 + 点击坐标”。

## 本项目当前差距

已完成：

- `assets/MaaSync/MaaResource/resource/base` 已能打入 APK。
- `assets/MaaSync/OcrEvidence` 已能打入 APK，并同步到 `/storage/emulated/0/Documents/MaaNikke/resource/evidence`。
- 当前证据图是 `1280x720`，与虚拟显示分辨率一致。
- `MaaCoreProbe` 已能加载 native bridge 并做文件截图 OCR 过渡。

缺口：

- 还没内置完整 MaaCore Android native 库和 control unit。
- 还没有 NCNN OCR 模型路径兼容。
- 证据图数量偏少，主要覆盖邮件、咨询、派遣，未覆盖拦截扫荡、商店刷新、前哨奖励、爬塔等高风险节点。
- 证据图还没有自动 ROI 裁剪输出和 OCR expected 文本清单。

## 推荐下一步

优先级从高到低：

1. `MaaCoreProbe` 增加 NCNN 模型路径探测，兼容 `PaddleOCR` / `PaddleCharOCR` 目录。
2. 建一个 `outputs/android_probe/ocr_regression_cases.json`，记录任务、原图、ROI、expected 文本、是否允许误识别。
3. 将现有 `OcrEvidence/manifest.json` 扩展字段：`resolution`、`roi`、`expected_text`、`source_display_id`、`capture_state`。
4. 对高风险任务补清晰证据图：拦截战可扫荡按钮、拦截战无次数灰态、商店免费刷新、派遣全部派遣、前哨一键歼灭、咨询批量完成。
5. 后续再跟进 MaaCore v6.13.0 / NCNN 真实执行，不要一次性替换现有稳定调试壳。

