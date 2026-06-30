# Android <- PC MAA 任务映射预版

这份文档的目的不是复述 PC 端已有内容，而是把 PC MaaNikke 里已经稳定的任务结构，翻成安卓端后续改造时能直接对照的工作表。

当前判断：

1. PC 端 `16:9` 素材、任务结构、参数定义已经足够完整
2. 安卓端不需要全量重采素材
3. 安卓端后续重点从“继续扩写 Java 坐标和颜色识别”调整为“接入 MaaCore / MaaFramework 执行层”
4. 技术路线参考安卓牛 / MAA-Meow：APK 内置 MaaCore/控制单元/资源，Shizuku/root 后台执行，UI 只做状态、配置和诊断；识别素材、截图证据和任务语义继续以 NIKKE MAA / PC MaaNikke 为准
5. 当前 APK 继续负责：
   - 虚拟显示
   - ImageReader 截图
   - displayId 输入注入
   - 调试模式
   - 日志和证据导出
6. MaaCore / MaaFramework 后续负责：
   - OCR
   - TemplateMatch
   - pipeline 分支
   - option override
   - 重试与回退

详细迁移方案见：

- `outputs/android_probe/android_maacore_migration_plan.md`

## 一、可直接复用的 PC 端资料

### 1. 任务入口与参数定义

- `C:/Users/Administrator/Downloads/MaaNikke-win-x86_64-v2.0.8/interface.json`
- `C:/Users/Administrator/Downloads/MaaNikke-win-x86_64-v2.0.8/config/instances/default.json`

用途：

1. 复用任务名、描述、默认勾选
2. 复用 option 结构
3. 复用参数对 pipeline 的覆盖思路

### 2. 任务流程

- `C:/Users/Administrator/Downloads/MaaNikke-win-x86_64-v2.0.8/resource/base/pipeline/default_pipeline.json`
- `C:/Users/Administrator/Downloads/MaaNikke-win-x86_64-v2.0.8/resource/base/pipeline/task/*.json`

用途：

1. 复用任务拆分方式
2. 复用页面进入顺序
3. 复用分支结构、回退结构、结束结构

### 3. 页面模板图

- `resource/base/image/outpostdefense/`
- `resource/base/image/freeshopdaily/`
- `resource/base/image/inquiryandgift/`
- `resource/base/image/dispatchboard/`
- `resource/base/image/interception/`
- `resource/base/image/tower/`

用途：

1. 复用页面锚点命名
2. 复用按钮命名
3. 复用 ROI 候选区域

### 4. OCR 模型

- `resource/base/model/ocr/det.onnx`
- `resource/base/model/ocr/rec.onnx`
- `resource/base/model/ocr/keys.txt`

用途：

1. 复用 OCR 词表基线
2. 复用文字识别目标词

## 二、安卓端改造原则

安卓端不直接照搬 PC 端窗口控制逻辑，但要尽量复用 PC 端 MaaNikke 的“规格”和 Maa 资源：

1. PC 端定义任务结构
2. MaaCore / MaaFramework 负责识别、OCR、模板匹配和 pipeline 执行
3. Android APK 负责截图、点击、等待、证据和调试模式
4. 当前代码仍按 `1280x720` 虚拟显示基准维护过渡期坐标与 ROI
5. 所有关键动作前后都要有截图状态确认

注意：

1. 用户当前屏幕是 `1920x1080`，但安卓后端当前配置仍是 `1280x720@160dpi`
2. 代码里的坐标常量来自 `ProbeConfig.WIDTH = 1280`、`ProbeConfig.HEIGHT = 720`
3. 如果后续切换到 `1920x1080`，建议新增比例适配层，而不是直接手改所有坐标
4. 等比换算关系为：
   - `x_1920 = x_1280 * 1.5`
   - `y_1080 = y_720 * 1.5`

迁移期每个任务都要拆成 4 段：

1. 进入目标页
2. 确认当前页正确
3. 执行关键动作
4. 确认动作结果并回大厅

长期目标不是把这 4 段都写成 Java 分支，而是把“确认当前页、关键动作、结果确认、异常回退”迁到 Maa pipeline 节点里；Android Java 只保留 controller 和少量安全兜底。

## 三、高频任务映射

### 1. 前哨防御

- PC 任务入口：`outpostdefense`
- PC 图片目录：`resource/base/image/outpostdefense/`
- PC 参数：
  - `是否使用钻石进行一举歼灭`
  - `连续歼灭次数`

安卓端当前重点：

1. 先领取累积奖励
2. 再做免费/付费歼灭
3. 任务结束后稳定回大厅

安卓端应拆分的关键页：

1. 前哨主页
2. 累积奖励可领取页
3. 一举歼灭确认页
4. 歼灭完成返回页

安卓端参数响应：

1. `关闭钻石歼灭`
   - 只执行免费一次
2. `开启钻石歼灭`
   - 执行免费一次 + 指定连续次数

已知风险：

1. 先后顺序错会导致漏领累积奖励
2. 歼灭后页面动画未结束就继续点击

建议改造：

1. 把“领取奖励”和“歼灭”拆成两个子步骤
2. 每一步后都确认是否回到前哨主页

### 2. 商店免费刷新

- PC 任务入口：`freeshopdaily`
- PC 图片目录：`resource/base/image/freeshopdaily/`
- PC 参数：
  - `是否兑换普通商店道具`
  - `是否兑换竞技场通用道具`
  - `选择需要兑换的竞技场商店道具`
  - `是否兑换躯体标签商店道具`
  - `是否兑换废铁商店道具`

安卓端当前重点：

1. 商店入口识别稳定
2. 免费刷新按钮识别稳定
3. 购买和刷新分离处理

安卓端应拆分的关键页：

1. 商店主页
2. 免费刷新前页面
3. 免费刷新确认弹窗
4. 各子商店页

安卓端参数响应：

1. 普通商店开关
2. 竞技场商店开关 + 多选商品
3. 躯体标签商店开关
4. 废铁商店开关

已知风险：

1. 商品售罄时提前结束，导致免费刷新没执行
2. 刷新按钮点后没确认是否出现弹窗

建议改造：

1. 把“购买商品”和“免费刷新”分成两条明确分支
2. 免费刷新必须以“弹出确认框”为成功判定

### 3. 咨询和送礼

- PC 任务入口：`inquiryandgift`
- PC 图片目录：`resource/base/image/inquiryandgift/`
- PC 参数：无显式 option，但描述里有固定行为约束

PC 描述重点：

1. 置顶的 3 个角色手动咨询/送礼
2. 其他角色走批量咨询/送礼

安卓端当前重点：

1. 批量咨询能正常跑完
2. 送礼链路完整
3. 结束后能稳定退出咨询页并回大厅

安卓端应拆分的关键页：

1. 咨询主页
2. 批量咨询确认页
3. 角色单独咨询页
4. 送礼确认页
5. 退出回主页过程

已知风险：

1. 咨询完成后停留在子页面
2. 送礼后没走完整返回链

建议改造：

1. 批量咨询和送礼拆成独立可观察步骤
2. 任务结束必须走统一 `back_to_home`

### 4. 派遣公告栏

- PC 任务入口：`dispatchboard`
- PC 图片目录：`resource/base/image/dispatchboard/`
- PC 参数：无

安卓端当前重点：

1. 一键领取
2. 一键派遣
3. 成功后退回大厅

安卓端应拆分的关键页：

1. 派遣主页
2. 可领取状态页
3. 一键派遣确认页

已知风险：

1. 误点单个派遣项
2. 没识别到一键派遣按钮

建议改造：

1. 先判断是否存在“一键领取”
2. 再判断是否存在“一键派遣”
3. 两步都做完再退出

### 5. 拦截战

- PC 任务入口：`interception`
- PC 图片目录：`resource/base/image/interception/`
- PC 参数：
  - `是否周一手操boss战`
  - `特殊拦截战boss`

安卓端当前重点：

1. 进入正确 boss 页面
2. 正常识别快速战斗/扫荡入口
3. 结束后回大厅

安卓端应拆分的关键页：

1. 拦截战主页
2. boss 选择/确认页
3. 快速战斗确认页
4. 结算或返回页

安卓端参数响应：

1. 周一是否走特殊逻辑
2. boss 目标词选择

已知风险：

1. 进入关卡页后没执行扫荡
2. boss 识别词和实际页面不一致

建议改造：

1. 把“进入 boss”和“执行快速战斗”拆成两段
2. 用 boss 名称作为识别词，不再只靠固定坐标盲点

### 6. 爬塔

- PC 任务入口：`climbtower`
- PC 图片目录：`resource/base/image/tower/`
- PC 参数：
  - `无限之塔`
  - `无限之塔爬塔次数`
  - `企业塔`
  - `是否打满企业塔`
  - `朝圣者/超标准`
  - `泰特拉`
  - `米西利斯`
  - `极乐净土`

安卓端当前重点：

1. 能区分塔选择页和塔详情页
2. 进错页时能左下返回
3. 不再在选择页和详情页之间循环

安卓端应拆分的关键页：

1. 爬塔主页
2. 塔类型选择页
3. 无限之塔详情页
4. 企业塔详情页
5. 战斗预览页

安卓端参数响应：

1. 无限之塔开关
2. 无限之塔次数
3. 企业塔开关
4. 企业塔优先级和启用项
5. 是否打满企业塔

已知风险：

1. 把详情页误当选择页
2. 进入错误塔后继续循环

建议改造：

1. 选择页与详情页必须有不同锚点
2. 发现状态不对先走左下返回，再重新判断

## 四、通用任务骨架

除任务差异外，安卓端建议统一复用下面的骨架：

1. `start_game`
2. `back_to_home`
3. `task_entry`
4. `task_page_check`
5. `task_action`
6. `task_result_check`
7. `back_to_home`
8. `stop_game` 或保持会话

## 五、参数层建议

后续安卓端不要把参数散落在代码分支里，建议统一收敛成表驱动：

1. 任务启停
2. 子分支启停
3. 目标识别词切换
4. 次数配置
5. normal/debug 行为差异

优先复用 PC 端 `interface.json` 的这些信息：

1. option 名称
2. default case
3. case -> pipeline_override 的关系

安卓端可以先不完全照搬 `pipeline_override` 语法，但要保留同样的语义。

## 六、参数 -> 安卓行为映射预版

这一节的目的，是把 PC 端 `option` 和安卓端实际动作对应起来，避免后续又回到“参数有了，但脚本不知道该怎么响应该参数”的状态。

### 1. 前哨防御

#### `是否使用钻石进行一举歼灭`

- PC 语义：
  - `No` -> 关闭 `spenddiamondstocleansweep`，开启 `freecleansweep`
  - `Yes` -> 开启 `spenddiamondstocleansweep`，关闭 `freecleansweep`

- 安卓端建议语义：
  - `No`
    - 只执行免费一举歼灭
    - 不进入任何钻石确认链路
  - `Yes`
    - 执行免费一举歼灭
    - 再进入钻石歼灭链路

- 安卓端最少确认点：
  1. 是否已进入前哨主页
  2. 是否出现免费歼灭入口
  3. 若启用钻石歼灭，是否出现钻石确认弹窗

#### `连续歼灭次数`

- PC 语义：
  - 用 `spenddiamondstocleaning.max_hit` 控制连续次数

- 安卓端建议语义：
  - 仅在 `是否使用钻石进行一举歼灭 = Yes` 时生效
  - 次数包含每日免费的一次

- 安卓端执行规则建议：
  1. 若值为 `1`
     - 只执行免费一次
  2. 若值大于 `1`
     - 免费一次 + 额外执行 `N-1` 次钻石歼灭

### 2. 商店

#### `是否兑换普通商店道具`

- PC 语义：
  - 控制 `basicshopstart.enabled`

- 安卓端建议语义：
  - `Yes` -> 进入普通商店分支，处理每日免费/折扣目标商品
  - `No` -> 完全跳过普通商店

#### `是否兑换竞技场通用道具`

- PC 语义：
  - 控制 `arenashopstart.enabled`
  - 开启后再展开子选项 `选择需要兑换的竞技场商店道具`

- 安卓端建议语义：
  - `Yes` -> 进入竞技场商店分支
  - `No` -> 完全跳过竞技场商店

#### `选择需要兑换的竞技场商店道具`

- PC 语义：
  - 复选项逐个打开具体节点：
    - `arenashopfengcode`
    - `arenashophuocode`
    - `arenashopdiancode`
    - `arenashopwucode`
    - `arenashopshuicode`
    - `arenashopcodebook`
    - `arenashopcopgearforge`

- 安卓端建议语义：
  - 只购买被勾选的商品
  - 未勾选商品即使看到也不点击

- 安卓端执行建议：
  1. 用商品名或对应图标作为目标锚点
  2. 找到后再判断是否可购买
  3. 买完一项后回当前商店页继续判断下一项

#### `是否兑换躯体标签商店道具`

- PC 语义：
  - 控制 `bodytagshopstart.enabled`

- 安卓端建议语义：
  - `Yes` -> 进入躯体标签商店分支
  - `No` -> 跳过

#### `是否兑换废铁商店道具`

- PC 语义：
  - 控制 `scrapshopstart.enabled`

- 安卓端建议语义：
  - `Yes` -> 进入废铁商店分支
  - `No` -> 跳过

#### 商店任务补充规则

- 免费刷新不建议作为独立参数，先保持为商店任务内固定动作
- 但安卓端必须把“商品购买”和“免费刷新”拆成两个结果点：
  1. 商品处理完成
  2. 免费刷新确认弹窗出现

### 3. 拦截战

#### `是否周一手操boss战`

- PC 语义：
  - 通过 `interception_checkweekday_off/on` 控制周一特殊逻辑

- 安卓端建议语义：
  - `No` -> 周一也走正常自动链路
  - `Yes` -> 周一切到特殊链路，其余日期正常自动

- 安卓端当前建议：
  - 在还没完全补齐手操逻辑前，保守处理：
    - `Yes` 时只记录“需要手操”并停止在目标页
    - `No` 时才继续自动快速战斗/扫荡

#### `特殊拦截战boss`

- PC 语义：
  - 改写 `interceptionboss.expected`
  - 可选：
    - `克拉肯`
    - `镜像容器`
    - `茵迪维利亚`
    - `过激派`
    - `死神`

- 安卓端建议语义：
  - 该参数直接控制 boss 识别目标词
  - 不再写死只打某一个 boss

- 安卓端最少确认点：
  1. 当前是否在 boss 选择页
  2. 当前识别到的 boss 是否与参数一致
  3. 进入后是否出现快速战斗/扫荡入口

### 4. 爬塔

#### `无限之塔`

- PC 语义：
  - `No` -> 禁用 `intounlimittower`、`isunlimittower`
  - `Yes` -> 启用这两个节点，并展开 `无限之塔爬塔次数`

- 安卓端建议语义：
  - `No` -> 完全跳过无限之塔
  - `Yes` -> 进入无限之塔分支

#### `无限之塔爬塔次数`

- PC 语义：
  - 用 `unlimittowertimes.max_hit` 控制次数

- 安卓端建议语义：
  - 进入无限之塔后，按指定次数重复执行“进入 -> 战斗预览 -> 完成/返回”

- 安卓端当前建议：
  - 先保守只支持整数正数
  - 每次循环前都重新确认当前仍在无限之塔正确页面

#### `企业塔`

- PC 语义：
  - 控制 `intocoptower`、`iscoptower`
  - 开启后再展开企业塔相关子参数

- 安卓端建议语义：
  - `No` -> 完全跳过企业塔
  - `Yes` -> 进入企业塔分支

#### `是否打满企业塔`

- PC 语义：
  - 控制 `isfullcoptower.enabled`

- 安卓端建议语义：
  - `No` -> 每日只打一次符合优先级的企业塔
  - `Yes` -> 每日尽量打满所有已开启企业塔

#### `朝圣者/超标准` / `泰特拉` / `米西利斯` / `极乐净土`

- PC 语义：
  - 分别控制：
    - `coptower1`
    - `coptower2`
    - `coptower3`
    - `coptower4`

- 安卓端建议语义：
  - 这些开关直接决定企业塔候选列表
  - 候选列表顺序建议继续沿用 PC 描述中的优先级：
    - 朝圣者/超标准
    - 泰特拉
    - 米西利斯
    - 极乐净土

- 安卓端执行建议：
  1. 先生成本轮允许尝试的企业塔列表
  2. 逐项判断入口是否开放
  3. 找到可进入项后再进入详情页

### 5. 咨询和送礼

PC `interface.json` 没有把这部分拆成 option，但安卓端仍建议预留内部行为开关：

1. 是否执行批量咨询
2. 是否执行送礼
3. 遇到未结束对话是否继续等待

这一块即使暂时不做用户可见参数，也建议在安卓端内部做成可配置项，方便后续调试。

### 6. 派遣公告栏

PC `interface.json` 没有显式 option，安卓端建议内部保留这两个默认行为：

1. 是否先一键领取
2. 是否再一键派遣

如果后面发现用户有细分需求，再升成可见参数。

## 七、建议的安卓参数数据结构

如果后续要把参数正式落到安卓端，建议不要继续散落在代码里，可先统一成类似结构：

```json
{
  "outpostdefense": {
    "use_diamond_cleansweep": false,
    "cleansweep_times": 1
  },
  "freeshopdaily": {
    "basic_shop": true,
    "arena_shop": true,
    "arena_shop_items": ["火代码", "电代码", "水代码", "代码手册宝箱"],
    "body_tag_shop": false,
    "scrap_shop": false
  },
  "interception": {
    "manual_boss_on_monday": false,
    "boss_name": "克拉肯"
  },
  "climbtower": {
    "unlimit_tower": true,
    "unlimit_tower_times": 1,
    "corp_tower": false,
    "corp_tower_full": false,
    "corp_tower_targets": ["朝圣者/超标准", "泰特拉"]
  }
}
```

## 八、任务落地表模板

后续每个高频任务，建议都按下面这 1 张小表去补全。这样一轮下来，安卓端就会从“能跑”慢慢变成“可维护、可验证、可回归”。

| 字段 | 含义 |
| --- | --- |
| `task_id` | 安卓端任务 ID |
| `pc_entry` | PC 端对应入口 |
| `entry_page` | 任务开始时预期所在页面 |
| `page_anchor` | 用来确认当前页的锚点 |
| `action_anchor` | 用来确认目标按钮/目标项的锚点 |
| `success_signal` | 动作成功后最小确认信号 |
| `fallback_path` | 当前页不对时怎么回退 |
| `options_used` | 本任务会读取哪些参数 |
| `roi/tap_todo` | 后续需要补的 ROI / 坐标 |
| `known_risk` | 已知高风险点 |

## 九、高频任务落地表预版

下面这几张表，是为了让后续安卓端代码改造可以直接对着填，不用再从自然语言段落里自己抽字段。

### 1. 前哨防御

| 字段 | 预版内容 |
| --- | --- |
| `task_id` | `claim_outpost_defense` |
| `pc_entry` | `outpostdefense` |
| `entry_page` | 大厅 -> 前哨入口 |
| `page_anchor` | 前哨主页标题、底部前哨页签、累积奖励区域 |
| `action_anchor` | 累积奖励按钮、一举歼灭按钮、钻石确认按钮 |
| `success_signal` | 奖励已领取或歼灭完成后重新回到前哨主页 |
| `fallback_path` | 页面不对 -> 左下返回 / 底部大厅 -> 重新进前哨 |
| `options_used` | `是否使用钻石进行一举歼灭`、`连续歼灭次数` |
| `roi/tap_todo` | 补前哨主页 ROI、奖励区域 ROI、歼灭确认 ROI |
| `known_risk` | 顺序错导致漏领奖励；歼灭动画未结束继续点击 |

### 2. 商店

| 字段 | 预版内容 |
| --- | --- |
| `task_id` | `claim_free_shop` |
| `pc_entry` | `freeshopdaily` |
| `entry_page` | 大厅 -> 商店主页 |
| `page_anchor` | 商店标题、页签、免费刷新按钮 |
| `action_anchor` | 各子商店入口、目标商品、免费刷新确认按钮 |
| `success_signal` | 商品处理完成且出现免费刷新确认弹窗，或已确认当日无可执行项 |
| `fallback_path` | 子商店页不对 -> 返回商店主页；未知页 -> 回大厅后重进商店 |
| `options_used` | 普通商店、竞技场商店、竞技场商品多选、躯体标签商店、废铁商店 |
| `roi/tap_todo` | 商店主页 ROI、刷新按钮 ROI、竞技场商品候选 ROI |
| `known_risk` | 商品售罄导致提前退出；没把“刷新确认出现”当成功条件 |

### 3. 咨询和送礼

| 字段 | 预版内容 |
| --- | --- |
| `task_id` | `claim_inquiry_and_gift` |
| `pc_entry` | `inquiryandgift` |
| `entry_page` | 大厅 -> 咨询主页 |
| `page_anchor` | 咨询主页标题、批量咨询入口、角色列表区域 |
| `action_anchor` | 批量咨询按钮、角色咨询按钮、送礼按钮、确认按钮 |
| `success_signal` | 批量咨询完成 / 送礼完成后成功退回大厅 |
| `fallback_path` | 子页面不对 -> 左下返回；多层子页异常 -> 连续返回直到大厅 |
| `options_used` | 当前无显式用户参数，建议内部保留 `batch_consult`、`send_gift` |
| `roi/tap_todo` | 批量咨询确认 ROI、送礼确认 ROI、退出按钮 ROI |
| `known_risk` | 送礼后停在子页；对话未结束时误返回 |

### 4. 派遣公告栏

| 字段 | 预版内容 |
| --- | --- |
| `task_id` | `claim_dispatch_board` |
| `pc_entry` | `dispatchboard` |
| `entry_page` | 大厅 -> 派遣公告栏 |
| `page_anchor` | 派遣主页标题、一键领取区域、一键派遣区域 |
| `action_anchor` | 一键领取按钮、一键派遣按钮、确认按钮 |
| `success_signal` | 一键领取和一键派遣都完成，页面无待处理高亮 |
| `fallback_path` | 派遣详情页误入 -> 返回主页；未知页 -> 回大厅重进 |
| `options_used` | 当前无显式用户参数 |
| `roi/tap_todo` | 一键领取 ROI、一键派遣 ROI、成功后空状态 ROI |
| `known_risk` | 误点单个派遣项；没区分“一键领取”和“一键派遣” |

### 5. 拦截战

| 字段 | 预版内容 |
| --- | --- |
| `task_id` | `claim_interception` |
| `pc_entry` | `interception` |
| `entry_page` | 大厅 -> 拦截战主页 |
| `page_anchor` | 拦截战标题、boss 名称区域、快速战斗入口 |
| `action_anchor` | 目标 boss 文本、快速战斗按钮、确认按钮 |
| `success_signal` | 已进入目标 boss 并完成快速战斗/扫荡，随后回大厅 |
| `fallback_path` | boss 页不对 -> 返回 boss 选择页；未知页 -> 回大厅重进 |
| `options_used` | `是否周一手操boss战`、`特殊拦截战boss` |
| `roi/tap_todo` | boss 名称 ROI、快速战斗 ROI、确认 ROI |
| `known_risk` | boss 识别词错误；进关卡页后没执行扫荡 |

### 6. 爬塔

| 字段 | 预版内容 |
| --- | --- |
| `task_id` | `claim_climb_tower` |
| `pc_entry` | `climbtower` |
| `entry_page` | 大厅 -> 爬塔主页 |
| `page_anchor` | 爬塔标题、塔类型选择区域、详情页标题 |
| `action_anchor` | 无限之塔入口、企业塔入口、战斗预览入口、左下返回 |
| `success_signal` | 进入目标塔详情或战斗预览页，完成当次操作后正确返回 |
| `fallback_path` | 详情页不对 -> 左下返回到选择页；未知页 -> 回大厅重进 |
| `options_used` | 无限之塔、无限之塔次数、企业塔、是否打满企业塔、企业塔目标开关 |
| `roi/tap_todo` | 选择页 ROI、详情页 ROI、返回按钮 ROI、目标塔文本 ROI |
| `known_risk` | 选择页和详情页误判；错误塔循环往返 |

## 十、当前最缺的不是素材，而是 4 类可执行信息

后续如果继续完善，不建议再优先补大批截图，而是优先把下面 4 类内容填进每个任务表：

1. `page_anchor`
   - 到底拿什么判断“我现在在这个页面”
2. `success_signal`
   - 到底看到什么，才算这一步真的成功
3. `fallback_path`
   - 识别错页时，是返回、回大厅还是重进
4. `roi/tap_todo`
   - 当前还没固化的 ROI 和坐标有哪些

## 十一、当前安卓实现对照

本节把安卓端当前代码里已经确认的真实信息填进来。来源主要是：

- `tools/android_root_imagereader_probe/src/com/codex/maanikke/rootprobe/ProbeConfig.java`
- `tools/android_root_imagereader_probe/src/com/codex/maanikke/rootprobe/MaaNikkeTaskRunner.java`

### 1. 当前通用坐标基准

| 项 | 当前值 |
| --- | --- |
| 虚拟显示 | `1280x720@160dpi` |
| 坐标基准 | `ProbeConfig.WIDTH = 1280`、`ProbeConfig.HEIGHT = 720` |
| 点击节奏 | `TOUCH_DOWN_UP_MS = 180`、`TAP_SETTLE_MS = 320` |
| 按键节奏 | `KEY_DOWN_UP_MS = 120`、`KEY_SETTLE_MS = 260` |
| 回大厅等待 | `BACK_TO_HOME_WAIT_SECONDS = 30` |
| 爬塔战斗等待 | `CLIMB_TOWER_FIGHT_WAIT_SECONDS = 75` |

### 2. 前哨防御当前实现

| 字段 | 当前代码事实 |
| --- | --- |
| 入口 | `openHomeEntry(..., OUTPOST_ENTRY_X=386, OUTPOST_ENTRY_Y=590)` |
| 首次领奖 | `OUTPOST_GET_REWARD=(720,615)` |
| 一举歼灭 | `OUTPOST_CLEAN_SWEEP=(558,616)` |
| 歼灭确认 | `OUTPOST_CLEAN_CONFIRM=(744,553)` |
| 通知不再提示 | `OUTPOST_NOTICE_DONT_SHOW=(609,385)` |
| 通知确认 | `OUTPOST_NOTICE_CONFIRM=(724,451)` |
| 歼灭奖励关闭 | `OUTPOST_CLEAN_REWARD=(640,360)` |
| 奖励确认 | `OUTPOST_REWARD_CONFIRM=(640,500)` |
| 当前识别 | `isOutpostCleanConfirmVisible`、`isOutpostCleanConfirmReady`、`isOutpostCleanNoticeVisible` |
| dry run finalState | `outpost_reward_button_previewed` |
| normal finalState | `outpost_reward_claim_attempted`、`outpost_clean_confirm_still_visible`、`outpost_diamond_clean_confirm_skipped` |
| 当前缺口 | 已接入 `是否使用钻石进行一举歼灭`、`连续歼灭次数`；当前仍是“免费歼灭后按次数尝试确认”，尚未区分“免费一次 + 额外钻石次数”的更细状态 |

### 3. 商店当前实现

| 字段 | 当前代码事实 |
| --- | --- |
| 入口 | `openHomeEntry(..., SHOP_ENTRY_X=380, SHOP_ENTRY_Y=492)` |
| 免费/折扣商品 | `SHOP_FREE_ITEM=(128,432)` |
| 数量确认 | `SHOP_QUANTITY_CONFIRM=(640,396)` |
| 购买确认 | `SHOP_BUY_CONFIRM=(704,594)` |
| 奖励确认 | `SHOP_REWARD_CONFIRM=(640,452)` |
| 免费刷新 | `SHOP_FREE_REFRESH=(156,326)` |
| 刷新确认 | `SHOP_REFRESH_CONFIRM=(730,452)` |
| 返回商店/底部返回 | `SHOP_BACK=(22,677)` |
| 当前识别 | `isShopPageVisible`、`isShopPurchaseDialogVisible`；v2.0.9 已补 `whiteShopVisible` 白底商品卡强判定，避免当前商店页没有橙色页签/底部蓝采样时误判未进入商店 |
| dry run finalState | `free_shop_purchase_dialog_previewed`、`free_shop_refresh_previewed`、`free_shop_purchase_and_refresh_previewed` |
| normal finalState | `free_shop_purchase_and_refresh_attempted`、`free_shop_page_not_confirmed`、`free_shop_all_branches_disabled` |
| 当前缺口 | 已接入普通商店/竞技场商店/躯体标签/废铁商店的第一层分支控制；当前竞技场/躯体标签/废铁仍以切页与日志记录为主，尚未完成细粒度商品点击与购买确认 |

### 4. 派遣公告栏当前实现

| 字段 | 当前代码事实 |
| --- | --- |
| 入口 | `openHomeEntry(..., DISPATCH_BOARD_ENTRY_X=464, DISPATCH_BOARD_ENTRY_Y=582)` |
| 一键领取 | `DISPATCH_CLAIM_ALL=(755,610)` |
| 一键派遣 | `DISPATCH_ALL=(640,610)` |
| 派遣确认 | `DISPATCH_CONFIRM=(640,610)` |
| 当前识别 | `isDispatchBoardPageVisible`、`isDispatchBoardClaimButtonVisible`、`isDispatchBoardDispatchButtonVisible`、`isDispatchBoardDispatchConfirmVisible`；v2.0.9 已补 `strongButtonVisible`，按钮强命中时不再被大厅采样误杀 |
| dry run finalState | `dispatch_board_buttons_previewed` |
| normal finalState | `dispatch_board_claim_and_dispatch_attempted`、`dispatch_board_page_not_confirmed` |
| 当前缺口 | 已补派遣主页视觉确认、领取/派遣按钮可见性日志和强按钮判定；仍缺完成空状态判定，以及更细的“领取完成后是否还需要一键派遣”分支结果 |

### 5. 咨询和送礼当前实现

| 字段 | 当前代码事实 |
| --- | --- |
| 入口 | `openHomeEntry(..., NIKKES_ENTRY_X=495, NIKKES_ENTRY_Y=668)` |
| 咨询页签 | `NIKKES_INQUIRY_TAB=(1215,88)` |
| 批量咨询 | `INQUIRY_BATCH_BUTTON=(1226,676)` |
| 批量咨询确认 | `INQUIRY_BATCH_CONFIRM=(640,452)` |
| 下一步/奖励继续 | `INQUIRY_NEXT_STEP=(640,560)` |
| 咨询弹窗关闭 | `INQUIRY_CLOSE=(792,676)` |
| 顶部 3 个角色 | `(255,242)`、`(645,242)`、`(1020,242)` |
| 送礼按钮 | `INQUIRY_GIFT_BUTTON=(640,598)` |
| 基础礼物 | `INQUIRY_BASIC_GIFT=(510,372)` |
| 发送礼物 | `INQUIRY_SEND_GIFT=(780,650)` |
| 送礼确认 | `INQUIRY_SEND_GIFT_CONFIRM=(640,452)` |
| 礼物页返回 | `INQUIRY_GIFT_BACK=(32,675)` |
| 底部大厅 | `INQUIRY_HOME=(108,675)` |
| 当前识别 | `isInquiryDetailPageVisible`、`isInquiryListPageVisible`、`isInquiryPageVisible` |
| 参数读取 | `giftCount = taskOptionInt(0, 3, 0, 3)` |
| dry run finalState | `inquiry_and_gift_gift_previewed` |
| normal finalState | `inquiry_and_gift_claim_and_gift_attempted` |
| 当前缺口 | PC 端没有显式咨询参数，但安卓端已有 `option.0` 控制送礼数量；文档/界面需明确该参数语义 |

### 6. 拦截战当前实现

| 字段 | 当前代码事实 |
| --- | --- |
| 方舟入口 | `ARK_ENTRY=(904,474)` |
| 拦截战入口 | `INTERCEPTION_ENTRY=(590,564)` |
| 异常个体页签 | `INTERCEPTION_ANOMALY=(790,654)` |
| 克拉肯候选 | `INTERCEPTION_KRAKEN=(640,570)` |
| 快速战斗/扫荡 | 过渡期坐标 `INTERCEPTION_SWEEP_BUTTON=(735,600)`，必须先识别橙色按钮 ROI `642,582,193,55`；不能仅凭拦截战页面可见就点击 |
| 挑战 BOSS 禁区 | 红色挑战按钮约在 `INTERCEPTION_CHALLENGE_BOSS=(735,663)`；如果只识别到红色挑战按钮而没有橙色扫荡按钮，必须停止 |
| 确认 | `INTERCEPTION_CONFIRM=(735,452)` |
| 当前识别 | `isArkHubVisible`、`isInterceptionPageVisible`、`isInterceptionQuickBattleVisible`、`isInterceptionChallengeBossVisible`；过渡期已把扫荡按钮改为橙色 ROI 判定，并把红色挑战按钮作为停止信号 |
| MaaCore OCR | 已接入只读旁路探针，使用 PC pipeline `quickbattle` ROI `[642,582,193,55]`，证据 `outputs/android_probe/debug_interception_no_attempts_probe_20260626-214159/` 中识别到“每周快速战斗” |
| dry run finalState | `interception_sweep_button_previewed`、`interception_quick_battle_disabled_previewed`、`interception_no_remaining_attempts_previewed`、`interception_challenge_boss_previewed_no_sweep`、`interception_boss_previewed_no_sweep_button` |
| normal finalState | `interception_quick_battle_swept_*`、`interception_quick_battle_disabled`、`interception_no_remaining_attempts`、`interception_challenge_boss_visible_no_sweep`、`interception_quick_battle_not_visible`、`interception_manual_boss_required` |
| 当前缺口 | 已接入 `是否周一手操boss战`、`特殊拦截战boss`，并补强拦截页识别；MaaCore OCR 已能读出 `quickbattle` 文案，但当前设备显示按钮灰态/剩余次数不可扫荡，所以 normal 继续只允许“按钮颜色可点击 + OCR/页面确认”同时成立才点扫荡，红色 `进入战斗/挑战 BOSS` 仍为禁区 |

### 7. 爬塔当前实现

| 字段 | 当前代码事实 |
| --- | --- |
| 方舟入口 | `ARK_ENTRY=(904,474)` |
| 爬塔入口 | `CLIMB_TOWER_ENTRY=(760,250)` |
| 左上选择器 | `CLIMB_TOWER_SELECTOR=(103,48)` |
| 左下返回 | `CLIMB_TOWER_CHOICE_BACK=(45,680)` |
| 无限之塔 | `CLIMB_TOWER_UNLIMITED=(640,300)` |
| 企业塔 1-4 | `(783,529)`、`(687,531)`、`(595,524)`、`(498,518)` |
| 进入战斗 | `CLIMB_TOWER_ENTER_FIGHT=(760,670)` |
| 返回列表候选 | `CLIMB_TOWER_BACK_TO_LIST=(990,270)` |
| 战斗失败重试 | `CLIMB_TOWER_RETRY=(760,650)` |
| 当前识别 | `isClimbTowerPageVisible`、`isClimbTowerDetailVisible`、`isClimbTowerChoiceVisible`、`isClimbTowerBattleFailedVisible` |
| 参数读取 | `option.0` 无限之塔、`option.1` 无限次数、`option.2` 企业塔、`option.3` 是否打满、`option.4-7` 企业塔开关 |
| dry run finalState | `climb_tower_battle_previewed` |
| normal finalState | `climb_tower_attempted`、`climb_tower_no_enabled_target`、`*_battle_failed_retry` |
| 当前缺口 | 企业塔坐标顺序和 PC 描述优先级需要最终核对；战斗成功后的完成信号仍偏弱，主要靠等待和返回候选 |

## 十二、当前实现缺口排序

按收益和风险排序，建议先补下面这些：

1. MaaCore Android 最小闭环
   - 新主线：先接入 MaaCore / MaaFramework，让 Android 截图进入 OCR/模板识别链路；拦截战 `quickbattle` 是第一批适合验证的 OCR 节点
2. 拦截战参数和页面识别
   - 已完成第一版：把 `特殊拦截战boss` 映射到安卓目标切换顺序，并支持 `是否周一手操boss战`；v2.0.9 已补强战斗按钮页面识别
   - 2026-06-26 修正：扫荡按钮必须按橙色 `快速战斗` ROI 识别；红色 `挑战 BOSS/进入战斗` 是禁区，识别到红按钮但没识别到扫荡时停止
   - 2026-06-26 补充：MaaCore OCR 已能在 `quickbattle` ROI 读到“每周快速战斗”；但本轮实机按钮为灰态，因此脚本按 `interception_quick_battle_disabled*` 安全停止，不点红色进入战斗
3. 前哨参数落地
   - 已完成第一版：让 `是否使用钻石进行一举歼灭`、`连续歼灭次数` 影响确认次数
4. 商店参数落地
   - 已完成第一层分支控制：普通商店、竞技场、躯体标签、废铁开关会真实影响执行路径
5. 派遣页识别补强
   - 已完成主页确认、按钮可见性日志和强按钮判定；下一步是补完成空状态判定
6. 咨询参数说明统一
   - 把安卓 `option.0 = 送礼数量 0-3` 明确写进任务描述和参数表
7. 爬塔成功信号补强
   - 进一步区分选择页、详情页、战斗预览、战斗失败弹窗

## 十三、建议的施工顺序

如果接下来继续推进安卓端代码，我建议按这个顺序落：

1. 先做 MaaCore Android 最小闭环
   - 能加载 PC MaaNikke resource
   - 能读取 Android ImageReader 截图
   - 能跑 OCR 或 TemplateMatch
   - 能通过 Android controller 点击一个安全入口
2. 再做拦截战 `quickbattle` 对照验证
   - PC pipeline 里 `quickbattle` OCR ROI 是 `642,582,193,55`
   - Android 先用同一区域识别“快速战斗”
   - Java 颜色识别只保留为 P0 安全兜底
3. 然后迁移低风险任务
   - 邮件、Pass、好友点数
   - 每个任务先做调试模式/只读识别，再开放 normal 单任务
4. 再迁移高风险任务
   - 前哨、商店、派遣、咨询、拦截、爬塔
   - 每个任务都要明确最终确认点和停止点
5. 最后才恢复完整 daily normal 回归

过渡期仍然可以给 `前哨 / 商店 / 派遣 / 拦截 / 爬塔 / 咨询` 六个任务补 `page_anchor`、`success_signal`、`fallback_path`，但这些只作为 P0 止血，不再作为长期主工程。

## 十四、下一步建议

下一轮建议按这个顺序继续：

1. `MaaCoreProbe` stub 已完成：能触发 APK/intent，能创建虚拟显示、接管 NIKKE、解码 ImageReader 截图并导出报告，证据见 `outputs/android_probe/maacore_probe_stub_20260626-192844/`
2. PC MaaNikke v2.0.9 的 `resource/base` 已推送到设备 `/storage/emulated/0/Documents/MaaNikke/resource/base`，探针确认 `resourceReady=true`、`pipelineReady=true`、`ocrModelReady=true`、`resourcePayloadReady=true`，证据见 `outputs/android_probe/maacore_probe_missing_library_20260626-193714/`
3. MaaFramework 官方 Android aarch64 v5.11.1 已下载并同步到设备；探针已兼容 `libMaaFramework.so`，并确认 `nativeLoadReady=true`，证据见 `outputs/android_probe/maacore_probe_native_load_ready_20260626-200349/`
4. 下一步先补 Android NDK/CMake 或等价 native 构建链，再把探针扩展为真实 OCR/模板调用
5. 再拿拦截战页面验证 `quickbattle`：MaaFramework 已能识别“每周快速战斗”，下一次要在有可扫荡次数时验证橙色/可点击按钮和扫荡确认弹窗；红色“挑战 BOSS/进入战斗”必须继续是 no-click 停止信号
6. 再把一个低风险任务迁到 MaaFramework pipeline 执行，例如 Pass/邮件只读识别
7. 之后才继续 normal 单任务验收：前哨、商店、派遣、拦截分开跑，不直接全量 normal 链路

当前结论：

1. PC 端资料已经够用
2. 安卓端暂时不缺素材
3. 安卓端当前已完成 MaaCore/MaaFramework 控制壳探针、资源同步和 native load check，但还没实现真实 OCR/模板调用，不能恢复 normal 全流程
4. 过渡期现已接上拦截战/前哨参数、派遣主页识别、派遣/拦截强按钮判定、商店第一层分支控制；这些作为 P0 兜底保留

## 2026-06-24 实机补充

1. 商店任务
   - 普通商店已能真实响应开关，负责免费商品与免费刷新链路。
   - 竞技场商店、躯体标签商店、废铁商店的开关已能真实改变执行分支，但商品级购买尚未补完。
   - 当前对这些未补完分支，后端会返回 `free_shop_partial_support_pending_*`，明确标记“已进入分支但未视为完整购物成功”。

2. 派遣任务
   - 已改为先判定 `全部领取` / `全部派遣` 按钮是否真实可见再执行。
   - 若只有领取按钮，会返回 `dispatch_board_claim_only_attempted`。
   - 若两个按钮都不可见，会返回 `dispatch_board_no_claim_or_dispatch_button`。

3. 当前实机最大阻塞点不在单任务本体
   - 2026-06-24 这轮正常模式定向验证时，设备现场停在《胜利女神》登录起始页，而不是大厅。
   - 因此 `back_to_home` 与 `ensureHomeForTaskStart` 会被现场状态污染，旧日志看起来像“回大厅失败”，但本质是“任务开始前就不在大厅态”。

4. 已落地的状态边界修正
   - `back_to_home` 检测到登录页时，直接返回 `login_required`。
   - `ensureHomeForTaskStart` 检测到登录页时，直接停止，不再继续点大厅锚点。
   - 登录页识别已从“标题 + QQ/微信按钮都命中”放宽到“标题高命中即可判定”，用于优先把 `login_required` 边界拦出来。

5. 本轮证据
   - `outputs/android_probe/task_after_wait_latest.png`
   - `outputs/android_probe/task_after_wait_latest2.png`
   - 上述两张图都属于登录起始页现场，不是大厅页。

6. 后续继续全任务确认前的前置条件
   - 账号已登录。
   - 游戏能正常进入大厅。
   - 在此基础上再继续派遣、商店、前哨、拦截等单任务与整链路验证。
