# 简单示例

下面这 3 条就是推荐写法，基本照抄就行。

## 示例 1：免费商店没弹确认

- 任务：`claim_free_shop`
- 模式：`normal`
- 勾启动游戏：`否`
- 期望停在哪：`免费刷新确认弹窗`
- 实际停在哪：`商店主页`
- 有没有点错：`不确定`
- 截图路径：`outputs/android_probe/failure_20260624-claim_free_shop-no-refresh/task_frame.png`
- 日志路径：`outputs/android_probe/failure_20260624-claim_free_shop-no-refresh/task_runner.log`
- 备注：`免费刷新次数还在，脚本像是没点到刷新按钮`

## 示例 2：拦截战没进快速战斗

- 任务：`claim_interception`
- 模式：`normal`
- 勾启动游戏：`否`
- 期望停在哪：`快速战斗确认页`
- 实际停在哪：`拦截战主页`
- 有没有点错：`没有`
- 截图路径：`outputs/android_probe/failure_20260624-claim_interception-no-quick-battle/task_frame.png`
- 日志路径：`outputs/android_probe/failure_20260624-claim_interception-no-quick-battle/task_runner.log`
- 备注：`可能没识别到快速战斗入口，或者等待不够`

## 示例 3：爬塔进错页然后循环

- 任务：`claim_climb_tower`
- 模式：`debug`
- 勾启动游戏：`否`
- 期望停在哪：`无限之塔战斗预览页`
- 实际停在哪：`塔选择页和详情页之间来回跳`
- 有没有点错：`有`
- 截图路径：`outputs/android_probe/failure_20260624-claim_climb_tower-loop/task_frame.png`
- 日志路径：`outputs/android_probe/failure_20260624-claim_climb_tower-loop/task_runner.log`
- 备注：`发现不对时可点左下返回，看看能不能回到塔类型选择页`
