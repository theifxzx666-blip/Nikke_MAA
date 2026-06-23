package com.codex.maanikke.debug;

final class TaskCatalog {
    static final TaskSpec[] PC_TASKS = new TaskSpec[]{
            new TaskSpec("启动游戏", "startgame", "start_game",
                    "游戏启动后，到进入主页大厅这一段过程", true, true, "已验证"),
            new TaskSpec("领取登录奖励", "loginrewards", "login_rewards",
                    "进入大厅页面后，领取月卡及登陆奖励", false, true, "已适配"),
            new TaskSpec("付费商店领取", "payshop", "visit_paid_shop",
                    "付费商店每日、每周、每月的免费钻石领取", false, true, "安全访问"),
            new TaskSpec("道具商店每日购买", "freeshopdaily", "visit_free_shop",
                    "游戏道具商店每日折扣商品购买", true, true, "已适配", new TaskOptionSpec[]{
                    switchOption("是否兑换普通商店道具", "游戏道具商店每日100%折扣商品购买", "No"),
                    switchOption("是否兑换竞技场通用道具", "各类代码，代码手册选择宝箱，企业装备熔炉", "No"),
                    checkboxOption("选择需要兑换的竞技场商店道具", "竞技场商店道具明细", new String[]{
                            "风代码", "火代码", "电代码", "物理代码", "水代码", "代码手册宝箱", "企业装备熔炉"
                    }, new String[]{"风代码", "火代码", "电代码", "物理代码", "水代码", "代码手册宝箱", "企业装备熔炉"}),
                    switchOption("是否兑换躯体标签商店道具", "躯体标签商店每日折扣商品购买", "No"),
                    switchOption("是否兑换废铁商店道具", "钻石、红眼道具1h和2h", "No")
            }),
            new TaskSpec("前哨防御", "outpostdefense", "visit_outpost_defense",
                    "睡醒了", true, true, "已适配", new TaskOptionSpec[]{
                    switchOption("是否使用钻石进行一举歼灭", "默认关闭，关闭后只进行每日免费的一次歼灭", "No"),
                    selectOption("连续歼灭次数", "此处歼灭次数包含每日免费的一次", "1", new String[]{
                            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"
                    })
            }),
            new TaskSpec("派遣公告栏", "dispatchboard", "visit_dispatch_board",
                    "自动领取自动派遣，需解锁自动派遣功能", false, true, "需确认"),
            new TaskSpec("咨询和送礼", "inquiryandgift", "visit_inquiry_and_gift",
                    "需要手动咨询送礼的三个妮姬请收藏至顶端", false, true, "已适配", new TaskOptionSpec[]{
                    inputOption("置顶角色送礼人数", "按置顶角色从左到右送礼，0 为跳过，默认 3 人", "3")
            }),
            new TaskSpec("装备升级", "gearup", "visit_gear_up",
                    "无法指定升级某个装备，仓库里至少有一个未满级装备", false, true, "需确认"),
            new TaskSpec("好友点数收取赠送", "friendpoints", "visit_friend_points",
                    "如任务名", true, true, "已适配"),
            new TaskSpec("社交点招募", "teamrecruit", "visit_team_recruit",
                    "为完成更生馆任务和每日任务", false, true, "需确认"),
            new TaskSpec("模拟室", "simroom", "visit_sim_room",
                    "需打完最后一个模拟室，解锁一键快速模拟通关", false, true, "已适配"),
            new TaskSpec("竞技场", "arena", "visit_arena",
                    "纯自动，请先配好队", false, true, "需确认", new TaskOptionSpec[]{
                    switchOption("特殊竞技场只领取奖励", "开启后，特殊竞技场将不会进行战斗", "No")
            }),
            new TaskSpec("拦截战", "interception", "visit_interception",
                    "目前只打肯boss，暂时不考虑其他", false, true, "需确认", new TaskOptionSpec[]{
                    switchOption("是否周一手操boss战", "开启后，周一会手操进行boss战斗，其他时间正常自动", "No"),
                    selectOption("特殊拦截战boss", "选择特定的boss战斗，其他boss战斗可能会因为识别问题导致失败率较高，请谨慎选择", "克拉肯", new String[]{
                            "克拉肯", "镜像容器", "茵迪维利亚", "过激派", "死神"
                    })
            }),
            new TaskSpec("爬塔", "climbtower", "visit_climb_tower",
                    "每日任务用爬塔流程", false, true, "已适配", new TaskOptionSpec[]{
                    switchOption("无限之塔", "此功能服务于每日任务，有额外无限爬塔需求还请手操", "No"),
                    inputOption("无限之塔爬塔次数", "默认爬一次，此为每日执行次数，建议不要太高", "1"),
                    switchOption("企业塔", "企业塔入口开关", "Yes"),
                    switchOption("是否打满企业塔", "默认关闭，每天只爬一次；打开后每天爬满所有开启的企业塔3次", "No"),
                    switchOption("朝圣者/超标准", "企业塔优先级配置", "Yes"),
                    switchOption("泰特拉", "企业塔优先级配置", "Yes"),
                    switchOption("米西利斯", "企业塔优先级配置", "Yes"),
                    switchOption("极乐净土", "企业塔优先级配置", "Yes")
            }),
            new TaskSpec("每日每周奖励领取", "dailyrewards", "visit_daily_rewards",
                    "包含反叛之路的每日每周奖励领取", true, true, "已适配"),
            new TaskSpec("邮件领取", "claimmail", "visit_mail",
                    "如任务名", true, true, "已适配"),
            new TaskSpec("同步器和循环室", "looproomandsync", "visit_loop_room_and_sync",
                    "如任务名", false, true, "需确认", new TaskOptionSpec[]{
                    switchOption("是否使用时间盒子道具", "打开后会检测是否使用时间盒子；关闭则顺从系统之前的默认状态", "No")
            }),
            new TaskSpec("PASS奖励领取", "claimpassreward", "visit_pass_rewards",
                    "如任务名", false, true, "已适配"),
            new TaskSpec("协同作战", "teambattle", "visit_team_battle",
                    "打满三次，可能不稳定，可选可不选", false, true, "需确认"),
            new TaskSpec("联盟突袭beta", "lianmengtuxi", "visit_union_raid",
                    "不稳定，可选可不选，出错了也不影响每日", false, true, "只读适配"),
            new TaskSpec("更生馆", "gengshengguan", "visit_rehabilitation",
                    "更生馆识别困难，如无必要推荐不勾选", false, true, "需确认"),
            new TaskSpec("结束游戏", "stopgame", "stop_game",
                    "如任务名", false, true, "安全适配"),
            new TaskSpec("test", "test", "test",
                    "测试用，勿选", false, false, "待适配")
    };

    private TaskCatalog() {
    }

    static int enabledCount() {
        int count = 0;
        for (int i = 0; i < PC_TASKS.length; i++) {
            if (PC_TASKS[i].enabled) {
                count++;
            }
        }
        return count;
    }

    static TaskSpec findByAndroidTaskId(String taskId) {
        for (int i = 0; i < PC_TASKS.length; i++) {
            if (PC_TASKS[i].androidTaskId.equals(taskId)) {
                return PC_TASKS[i];
            }
        }
        return null;
    }

    private static TaskOptionSpec switchOption(String key, String description, String defaultValue) {
        return new TaskOptionSpec(key, "switch", description, defaultValue, new String[]{"No", "Yes"}, false);
    }

    private static TaskOptionSpec selectOption(String key, String description, String defaultValue, String[] cases) {
        return new TaskOptionSpec(key, "select", description, defaultValue, cases, false);
    }

    private static TaskOptionSpec inputOption(String key, String description, String defaultValue) {
        return new TaskOptionSpec(key, "input", description, defaultValue, new String[0], false);
    }

    private static TaskOptionSpec checkboxOption(String key, String description, String[] cases, String[] defaults) {
        return new TaskOptionSpec(key, "checkbox", description, join(defaults), cases, true);
    }

    private static String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }

    static final class TaskSpec {
        final String name;
        final String pcEntry;
        final String androidTaskId;
        final String description;
        final boolean defaultChecked;
        final boolean enabled;
        final String status;
        final TaskOptionSpec[] options;

        TaskSpec(String name, String pcEntry, String androidTaskId, String description,
                 boolean defaultChecked, boolean enabled, String status) {
            this(name, pcEntry, androidTaskId, description, defaultChecked, enabled, status, new TaskOptionSpec[0]);
        }

        TaskSpec(String name, String pcEntry, String androidTaskId, String description,
                 boolean defaultChecked, boolean enabled, String status, TaskOptionSpec[] options) {
            this.name = name;
            this.pcEntry = pcEntry;
            this.androidTaskId = androidTaskId;
            this.description = description;
            this.defaultChecked = defaultChecked;
            this.enabled = enabled;
            this.status = status;
            this.options = options;
        }
    }

    static final class TaskOptionSpec {
        final String key;
        final String type;
        final String description;
        final String defaultValue;
        final String[] cases;
        final boolean multiple;

        TaskOptionSpec(String key, String type, String description, String defaultValue, String[] cases, boolean multiple) {
            this.key = key;
            this.type = type;
            this.description = description;
            this.defaultValue = defaultValue;
            this.cases = cases;
            this.multiple = multiple;
        }
    }
}
