# 简化采集说明

这次把模板改成“出问题就直接记”的版本，不追求工程化字段。

## 你只需要记 8 项

1. `任务`
2. `模式`
3. `勾了启动游戏没`
4. `期望停在哪`
5. `实际停在哪`
6. `有没有点错`
7. `截图路径`
8. `日志路径`

如果还有补充，再写到 `备注`。

## 最低证据要求

每个异常样本至少保留：

1. 一张出问题时的截图
2. 对应的 `task_runner.log`

够用的话，再补：

1. 标注点击区域的图
2. `result.txt`

## 文件怎么用

- [`sample_collection_template.csv`](F:/Codex/Nikke/Nikke/outputs/android_probe/sample_collection_template.csv)
  - 最适合连续记录多个异常
- [`sample_collection_template.md`](F:/Codex/Nikke/Nikke/outputs/android_probe/sample_collection_template.md)
  - 最适合单个复杂问题
- [`simple_sample_examples.md`](F:/Codex/Nikke/Nikke/outputs/android_probe/simple_sample_examples.md)
  - 直接看例子照着写

## 推荐命名

- 文件夹：`outputs/android_probe/failure_日期-任务-简短问题/`
- 例子：`outputs/android_probe/failure_20260624-claim_free_shop-no-refresh/`

## PC 端素材能不能复用

可以复用，而且很值得复用，但要分层看：

### 可以直接拿来当参考的

1. PC 端任务拆分
   - `C:/Users/Administrator/Downloads/MaaNikke-win-x86_64-v2.0.8/resource/base/pipeline/task/freeshopdaily.json`
   - `.../dispatchboard.json`
   - `.../inquiryandgift.json`
   - `.../interception.json`
   - `.../outpostdefense.json`
   - `.../climbtower.json`
2. PC 端页面模板图
   - `resource/base/image/freeshopdaily/`
   - `resource/base/image/dispatchboard/`
   - `resource/base/image/inquiryandgift/`
   - `resource/base/image/interception/`
   - `resource/base/image/outpostdefense/`
   - `resource/base/image/tower/`
3. OCR 相关资源
   - `resource/base/model/ocr/det.onnx`
   - `resource/base/model/ocr/rec.onnx`
   - `resource/base/model/ocr/keys.txt`

### 适合怎么复用

1. 复用任务名和页面名
2. 复用“这一页该识别什么按钮/标题”
3. 复用模板图命名和分类
4. 复用 OCR 目标词

### 不能直接照搬的

1. 不能默认 PC 图就一定能直接匹配手机截图
2. 不能把 PC pipeline 直接当 Android 执行逻辑
3. 不能省掉 Android 端自己的 ROI、等待和返回路径

一句话说：PC 端这批素材很适合拿来做“采集清单、命名规范、识别锚点参考”，但 Android 端还得自己做实机校准。
