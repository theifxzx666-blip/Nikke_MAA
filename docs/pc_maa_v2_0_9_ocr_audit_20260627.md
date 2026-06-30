# PC MaaNikke v2.0.9 OCR 检查记录

检查对象：

- PC 端目录：`C:/Users/Administrator/Desktop/MaaNikke-win-x86_64-v2.0.9`
- 安卓端工作区：`F:/Codex/Nikke/Nikke`
- 检查日期：`2026-06-27`

## 结论

1. v2.0.9 的任务列表和 option 数量与 v2.0.8 一致：`23` 个任务、`19` 个 option、`29` 个 task pipeline JSON。
2. 高频问题任务的 pipeline 没有变：`freeshopdaily`、`outpostdefense`、`dispatchboard`、`inquiryandgift`、`interception`、`climbtower` 均未相对 v2.0.8 修改。
3. 有实质变化的任务集中在启动和回大厅：
   - `startgame.json`
   - `backtohomepage.json`
   - `claimpassreward.json`
   - `loginrewards.json` 只有编辑器元数据变化，去掉 `$__mpe_*` 后语义不变。
4. OCR 模型完整，PC 端使用 PaddleOCR v5 mobile det/rec：
   - `resource/base/model/ocr/det.onnx`
   - `resource/base/model/ocr/rec.onnx`
   - `resource/base/model/ocr/keys.txt`
5. PC pipeline 里 OCR 是主力识别方式，不是辅助项。本轮统计到：
   - OCR 节点：`348`
   - TemplateMatch 节点：`117`
   - Custom recognition 节点：`3`，都在 `test.json`，正式任务基本不依赖 custom recognition。
6. 安卓端当前 MaaCore bridge 已能跑拦截战 `quickbattle` 只读 OCR，但仍是硬编码探针；下一步应改成通用 `roi + expected` OCR probe，再逐步替换 Java 颜色/坐标判定。

## v2.0.9 相对 v2.0.8 的有效变化

### `startgame.json`

- `clickstart` OCR ROI 从 `[563,537,149,63]` 放宽到 `[531,513,231,122]`，更适合启动页文字位置波动。
- `kehuzhongxin` 增加 `timeout: 600000`，并增加 `startgame_back` 兜底。
- `startgame` 的 next 增加 `[JumpBack]backtohomepage`，更适合“已经在游戏内/大厅附近”时收敛。
- `updategame` 去掉 `on_error: ["startgame"]`，避免更新弹窗识别失败后反复回启动主流程。

### `backtohomepage.json`

- `backtohomepage` next 增加 `clickstart`，可从启动页“点击开始”继续收敛。
- `clickclaim_done` 识别不到“已领取”时，会走 `clickclaim_done_back` 按返回键退出。
- 仍保留底部大厅按钮 OCR 节点 `homepagebuttom2`：
  - ROI：`[612,675,56,40]`
  - expected：`["大厅"]`
  - 这点和用户反馈一致，回大厅应该优先点底部大厅按钮，而不是点立绘。

### `claimpassreward.json`

- `3pass_open`、`3pass_claim` 增加 `timeout: 10000`。
- `checkexchangepass_back` 增加 `post_delay: 500`。
- 和当前安卓高频问题无直接冲突，后续可低优先级同步。

## OCR 密度最高的任务

| 任务文件 | OCR | TemplateMatch | 迁移优先级 |
| --- | ---: | ---: | --- |
| `climbtower.json` | 27 | 3 | 高，当前爬塔循环问题适合用 OCR 状态门修 |
| `inquiryandgift.json` | 26 | 7 | 高，咨询/送礼流程主要靠按钮文案 |
| `freeshopdaily.json` | 22 | 21 | 高，但涉及购买，必须 debug/只读先行 |
| `outpostdefense.json` | 18 | 4 | 高，领取奖励和歼灭顺序适合 OCR 拆开 |
| `backtohomepage.json` | 14 | 3 | 高，所有任务都依赖回大厅 |
| `interception.json` | 14 | 3 | 高，已做 quickbattle OCR 试点 |
| `dispatchboard.json` | 6 | 4 | 高，按钮少，适合作为通用 OCR probe 第二个落地点 |
| `claimmail.json` | 3 | 2 | 中高，低风险，可用于验证 OCR 通用化 |

## 建议优先迁移的 OCR 节点

### 回大厅

| 节点 | ROI | expected | 用途 |
| --- | --- | --- | --- |
| `homepagebuttom2` | `[612,675,56,40]` | `大厅` | 从各种二级页收敛回大厅 |
| `clickclaim` / `clickclaim_do` | 全屏 | `点击领取`、`点击继续`、`立刻领取`、`全部领取` | 弹窗领取兜底 |
| `goback` | 全屏 | `返回`、`关闭` | 弹窗/页面关闭兜底 |
| `clickstart` | `[531,513,231,122]` | `点击开始`、`点击`、`开始` | 已在启动页时继续进入游戏 |

### 派遣公告栏

| 节点 | ROI | expected | 安卓用途 |
| --- | --- | --- | --- |
| `checkdispatchboard` | `[482,88,112,41]` | `派遣公告栏` | 确认页面 |
| `claimall` | `[702,575,110,56]` | `全部领取` | 领取按钮状态 |
| `dispatchall` | `[582,580,118,54]` | `全部派遣` | 一键派遣按钮状态 |
| `nothingdispatch` | 全屏 | `没有可进行` | 无可派遣收口 |

### 咨询和送礼

| 节点 | ROI | expected | 安卓用途 |
| --- | --- | --- | --- |
| `intonikkes2` | `[452,625,86,87]` | `妮姬` | 进入妮姬页面 |
| `checkinquiry` | `[556,52,168,39]` | `咨询` | 咨询页确认 |
| `1keytoinquiry` | `[1176,650,100,57]` | `批量咨询` | 右下角批量咨询入口 |
| `click1keytoinquiry` | `[726,601,114,55]` | `批量咨询` | 批量咨询弹窗确认 |
| `confirm1keytoinquiry` | `[623,363,225,154]` | `确认` | 批量咨询二次确认 |
| `finish1keytoinquiry` | 全屏 | `点击进行下一步` | 批量咨询完成 |
| `checksendgift` | `[575,82,141,40]` | `送礼` | 送礼页确认 |
| `notimestoinquiry` | `[547,343,209,37]` | `剩余咨询次数不足`、`当前没有可咨询` | 无次数收口 |

### 拦截战

| 节点 | ROI | expected | 安卓用途 |
| --- | --- | --- | --- |
| `arkinterception` | `[764,401,279,155]` | `方舟` | 方舟入口确认 |
| `intointerception` | `[528,589,146,83]` | `拦截战` | 拦截战入口 |
| `checkinterception` | 全屏 | `剩余拦截次数`、`剩余拦截`、`拦截次数` | 拦截页确认 |
| `interceptionboss` | `[562,489,155,74]` | `克拉肯` 等 boss 名 | boss 页面/目标确认 |
| `quickbattle` | `[642,582,193,55]` | `快速战斗` | 扫荡按钮确认 |
| `cannotquick` | 全屏 | `每日至少`、`游玩1次后` | 不允许扫荡时停止 |
| `enterbattle` | 全屏 | `进入战斗` | 高风险禁区，debug/无扫荡时不要点 |

### 商店

| 节点 | ROI | expected | 安卓用途 |
| --- | --- | --- | --- |
| `checkinmall` | `[0,0,100,60]` | `百货商店` | 商店总页确认 |
| `checkinbasicshop` | `[42,238,214,75]` | `普通商店` | 普通商店分支 |
| `0zuanpay` | `[72,348,111,177]` | `100%` | 0 钻免费商品 |
| `confirm0zuanpay2` | 全屏 | `点击领取奖励`、`点击领取` | 免费领取结果 |
| `confirmfreeshoprefresh` | `[656,438,145,25]` | `确认` | 免费刷新确认弹窗 |
| `confirmjjcshopping3` / `confirmscrapshopping3` / `bodytagshopping3` | `[401,233,469,279]` | `不足`、`资金不足`、`道具不足` | 购买失败安全收口 |

### 前哨防御

| 节点 | ROI | expected | 安卓用途 |
| --- | --- | --- | --- |
| `checkoutpostdefense` | `[417,41,262,130]` | `防御前哨基地` | 前哨页确认 |
| `getreward` | `[603,554,229,112]` | `获得奖励` | 累积奖励入口 |
| `confirmgetreward` | `[456,326,366,322]` | `点击领取奖励`、`领取奖励` | 累积奖励确认 |
| `notimereward` | `[484,413,327,166]` | `目前没有获得` | 无奖励收口 |
| `freecleansweep` | `[460,560,191,115]` | `一举歼灭`、`歼灭` | 免费歼灭入口 |
| `comfirmspenddiamondstocleaning` | `[514,244,255,161]` | `是否消耗珠宝`、`免费珠宝不足` | 钻石风险弹窗 |

### 爬塔

| 节点 | ROI | expected | 安卓用途 |
| --- | --- | --- | --- |
| `intoarktower` | `[783,398,230,160]` | `方舟` | 方舟入口 |
| `intotower` | `[705,288,127,49]` | `无限之塔` | 无限塔入口 |
| `checkintounlimittower` | `[534,572,209,95]` | `无限之塔`、`无限` | 选择无限塔后的确认 |
| `climbingtowerpage` | `[698,309,125,33]` | `通关阵容` | 战斗详情页确认 |
| `climbtower_intofight` | `[661,632,193,74]` | `进入战斗` | 高风险动作，debug 只预览 |
| `notowerchance` | `[484,324,311,71]` | `次数限制`、`超出`、`限制` | 无次数收口 |
| `exittower` | OCR expected 空 + `Esc` | - | 异常循环时返回选择页 |

## 安卓端当前差距

1. `MaaCoreNativeBridge` 的 `run_ocr_probe` 仍硬编码：
   - ROI：`[642,582,193,55]`
   - expected：`["快速战斗","每周快速战斗"]`
   - 这只能服务拦截战 quickbattle，不能复用 PC pipeline 的其他 OCR 节点。
2. `MaaNikkeTaskRunner` 里已有不少页面判断仍是颜色采样：
   - 拦截战按钮状态
   - 派遣按钮可见性
   - 咨询列表/详情页
   - 商店页/购买弹窗
   - 前哨/爬塔页面
3. 颜色采样可以继续作为“安全门”，但不适合作为主识别。推荐顺序是：
   - OCR 确认页面和按钮文案
   - 颜色采样确认按钮是否可点击
   - 坐标点击只作为最后动作

## 下一步建议

### P0：通用 OCR probe

把当前 `runMaaCoreQuickBattleProbeIfReady` 拆成通用接口：

```text
runMaaCoreOcrProbe(frameFile, context, roi, expected)
```

返回内容至少包含：

- `ready`
- `hit`
- `text/detail`
- `box`
- `roi`
- `expected`
- `context`

这样就能直接把 PC pipeline 里的 OCR 节点逐个搬到安卓端 debug gate。

### P1：先接低风险只读 OCR gate

建议顺序：

1. `backtohomepage.homepagebuttom2`
2. `dispatchboard.checkdispatchboard / claimall / dispatchall`
3. `claimmail.checkmail / claimthings`
4. `inquiryandgift.1keytoinquiry / confirm1keytoinquiry`
5. `interception.checkinterception / interceptionboss / quickbattle / cannotquick`

### P2：再接高风险动作的双门确认

涉及真实消耗或进战斗的动作，必须满足：

1. OCR 命中文案
2. 颜色/形态确认按钮可点击
3. 非 debug 模式
4. 参数允许

适用对象：

- 商店购买/刷新
- 前哨钻石歼灭
- 拦截战扫荡
- 爬塔进入战斗

### P3：最终目标

最终不要继续在 Java 里堆每个任务的颜色判断。目标应是：

1. Android APK 负责虚拟显示、截图、输入、调试模式、证据导出。
2. MaaCore/MaaFramework 负责 OCR、TemplateMatch、pipeline、option override。
3. Java runner 只保留安全兜底和任务调度。
