package com.codex.maanikke.rootprobe;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

final class ProbeConfig {
    static final int BASE_WIDTH = 1280;
    static final int BASE_HEIGHT = 720;
    static final int WIDTH = readIntOption("maanikke.display.width", 1280);
    static final int HEIGHT = readIntOption("maanikke.display.height", 720);
    static final int DPI = readIntOption("maanikke.display.dpi", WIDTH >= 1920 ? 240 : 160);
    static final int MAX_IMAGES = 5;
    static final int CAPTURE_SECONDS = 60;
    static final String PREVIEW_SOCKET_NAME = "maanikke_preview_frame";
    static final int PREVIEW_WIDTH = 960;
    static final int PREVIEW_HEIGHT = 540;
    static final int PREVIEW_JPEG_QUALITY = 58;
    static final int PREVIEW_FRAME_MIN_INTERVAL_MS = 90;
    static final int LEGACY_FRAME_FILE_INTERVAL_MS = 1000;
    static final int PREVIEW_CLIENT_HEARTBEAT_MS = 900;
    static final int PREVIEW_CLIENT_IDLE_TIMEOUT_MS = 30000;
    static final int TOUCH_DOWN_UP_MS = 180;
    static final int TAP_SETTLE_MS = 650;
    static final int KEY_DOWN_UP_MS = 120;
    static final int KEY_SETTLE_MS = 360;
    static final int STABLE_FRAME_MIN_NONZERO_SAMPLES = 2500;
    static final int STABLE_FRAME_REQUIRED_STREAK = 3;
    static final int STABLE_SCENE_WAIT_SECONDS = 6;
    static final int ENTRY_PAGE_WAIT_ATTEMPTS = 8;
    static final int ENTRY_PAGE_WAIT_INTERVAL_MS = 1200;
    static final int HOME_RETURN_SETTLE_MS = 1700;
    static final int ARK_HUB_WAIT_ATTEMPTS = 8;
    static final int ARK_HUB_WAIT_INTERVAL_MS = 1100;
    static final int ARK_SUBPAGE_WAIT_ATTEMPTS = 11;
    static final int ARK_SUBPAGE_FIRST_WAIT_MS = 2200;
    static final int ARK_SUBPAGE_WAIT_INTERVAL_MS = 1100;
    static final String VD_NAME = "MaaNikkeRootIR-" + WIDTH + "x" + HEIGHT;
    static final String DEFAULT_TARGET_PACKAGE = "com.tencent.nikke";
    static final String DEFAULT_TARGET_ACTIVITY = ".default_Activity";
    static final String TARGET_OVERRIDE_FILE = "/data/local/tmp/maanikke_target_package.txt";
    static final String TARGET_COMPONENT_OVERRIDE_FILE = "/data/local/tmp/maanikke_target_component.txt";
    static final String[] TARGET_PACKAGE_CANDIDATES = new String[]{
            "com.tencent.nikke",
            "com.proximabeta.nikke",
            "com.levelinfinite.nikke",
            "com.shiftup.nikke",
            "com.gamamobi.nikke",
            "com.hottag.nikke"
    };

    static final File LOG_FILE = new File("/data/local/tmp/maanikke_root_ir_probe.log");
    static final File FRAME_FILE = new File("/data/local/tmp/maanikke_root_ir_last.png");
    static final File BEFORE_TOUCH_FILE = new File("/data/local/tmp/maanikke_root_ir_before_touch.png");
    static final File AFTER_TOUCH_FILE = new File("/data/local/tmp/maanikke_root_ir_after_touch.png");
    static final File RESULT_FILE = new File("/data/local/tmp/maanikke_root_ir_result.txt");

    static final File TASK_LOG_FILE = new File("/data/local/tmp/maanikke_task_runner.log");
    static final File TASK_RESULT_FILE = new File("/data/local/tmp/maanikke_task_result.txt");
    static final File TASK_FRAME_FILE = new File("/data/local/tmp/maanikke_task_frame.png");
    static final File TASK_BEFORE_ACTION_FILE = new File("/data/local/tmp/maanikke_task_before_action.png");
    static final File TASK_AFTER_ACTION_FILE = new File("/data/local/tmp/maanikke_task_after_action.png");
    static final File TASK_AFTER_OPEN_FILE = new File("/data/local/tmp/maanikke_task_after_open.png");
    static final File TASK_AFTER_CLOSE_FILE = new File("/data/local/tmp/maanikke_task_after_close.png");
    static final File TASK_AFTER_BACK_FILE = new File("/data/local/tmp/maanikke_task_after_back.png");
    static final File TASK_AFTER_WAIT_FILE = new File("/data/local/tmp/maanikke_task_after_wait.png");
    static final File TASK_AFTER_ENTER_FILE = new File("/data/local/tmp/maanikke_task_after_enter.png");
    static final File TASK_AFTER_DOWNLOAD_CONFIRM_FILE = new File("/data/local/tmp/maanikke_task_after_download_confirm.png");
    static final File TASK_AFTER_HOME_POPUP_FILE = new File("/data/local/tmp/maanikke_task_after_home_popup.png");
    static final File TASK_AFTER_UPDATE_FILE = new File("/data/local/tmp/maanikke_task_after_update.png");
    static final File TASK_AFTER_MAIL_OPEN_FILE = new File("/data/local/tmp/maanikke_task_after_mail_open.png");
    static final File TASK_AFTER_MAIL_CLAIM_FILE = new File("/data/local/tmp/maanikke_task_after_mail_claim.png");
    static final File TASK_AFTER_MAIL_CONFIRM_FILE = new File("/data/local/tmp/maanikke_task_after_mail_confirm.png");
    static final File TASK_AFTER_GIFT_CONFIRM_FILE = new File("/data/local/tmp/maanikke_task_after_gift_confirm.png");
    static final File TASK_OPTIONS_FILE = new File("/data/local/tmp/maanikke_task_options.properties");
    static final File DAILY_ACTION_LEDGER_FILE =
            new File("/storage/emulated/0/Documents/MaaNikke/logs/daily-action-ledger.tsv");
    static final File MAACORE_PROBE_REPORT_FILE = new File("/data/local/tmp/maanikke_maacore_probe_report.txt");
    static final String[] MAACORE_RESOURCE_CANDIDATES = new String[]{
            "/storage/emulated/0/Documents/MaaNikke/resource/base",
            "/storage/emulated/0/Documents/MaaNikke/resource",
            "/data/local/tmp/maanikke_resource/base",
            "/data/local/tmp/maanikke_resource"
    };
    static final String[] MAACORE_LIBRARY_CANDIDATES = new String[]{
            "/data/local/tmp/libMaaCore.so",
            "/data/local/tmp/libMaaFramework.so",
            "/data/local/tmp/maacore/libMaaCore.so",
            "/data/local/tmp/maacore/libMaaFramework.so",
            "/storage/emulated/0/Documents/MaaNikke/lib/libMaaCore.so",
            "/storage/emulated/0/Documents/MaaNikke/lib/libMaaFramework.so",
            "/storage/emulated/0/Documents/MaaNikke/maa/libMaaCore.so",
            "/storage/emulated/0/Documents/MaaNikke/maa/libMaaFramework.so"
    };
    static final String[] MAACORE_CONTROL_UNIT_CANDIDATES = new String[]{
            "/data/local/tmp/maacore/libMaaAndroidNativeControlUnit.so",
            "/data/local/tmp/libMaaAndroidNativeControlUnit.so",
            "/storage/emulated/0/Documents/MaaNikke/lib/libMaaAndroidNativeControlUnit.so",
            "/storage/emulated/0/Documents/MaaNikke/maa/libMaaAndroidNativeControlUnit.so"
    };
    static final String[] MAACORE_BRIDGE_CANDIDATES = new String[]{
            "/data/local/tmp/libmaanikke_maacore_bridge.so",
            "/storage/emulated/0/Documents/MaaNikke/lib/libmaanikke_maacore_bridge.so"
    };
    static final String[] MAACORE_RESOURCE_VERSION_CANDIDATES = new String[]{
            "/storage/emulated/0/Documents/MaaNikke/resource/resource-version.txt",
            "/storage/emulated/0/Documents/MaaNikke/resource/base/resource-version.txt",
            "/data/local/tmp/maanikke_resource/resource-version.txt",
            "/data/local/tmp/maanikke_resource/base/resource-version.txt"
    };
    static final String[] MAACORE_EVIDENCE_CASES_CANDIDATES = new String[]{
            "/storage/emulated/0/Documents/MaaNikke/resource/evidence/ocr_regression_cases.json",
            "/storage/emulated/0/Documents/MaaNikke/resource/OcrEvidence/ocr_regression_cases.json",
            "/data/local/tmp/maanikke_resource/evidence/ocr_regression_cases.json",
            "/data/local/tmp/maanikke_resource/OcrEvidence/ocr_regression_cases.json"
    };
    static final String[] MAACORE_NCNN_OCR_RELATIVE_CANDIDATES = new String[]{
            "PaddleOCR",
            "PaddleCharOCR",
            "base/PaddleOCR",
            "base/PaddleCharOCR",
            "resource/PaddleOCR",
            "resource/PaddleCharOCR"
    };
    static final File ANNOUNCEMENT_SEVEN_DAY_MARKER =
            new File("/data/local/tmp/maanikke_announcement_7day_checked.txt");

    static final int TOUCH_X = 32;
    static final int TOUCH_Y = 86;

    static int scaleX(int baseX) {
        return Math.round(baseX * (WIDTH / (float) BASE_WIDTH));
    }

    static int scaleY(int baseY) {
        return Math.round(baseY * (HEIGHT / (float) BASE_HEIGHT));
    }

    static int scaleWidth(int baseWidth) {
        return Math.max(1, Math.round(baseWidth * (WIDTH / (float) BASE_WIDTH)));
    }

    static int scaleHeight(int baseHeight) {
        return Math.max(1, Math.round(baseHeight * (HEIGHT / (float) BASE_HEIGHT)));
    }

    private static int readIntOption(String key, int defaultValue) {
        File file = new File("/data/local/tmp/maanikke_task_options.properties");
        if (!file.isFile()) {
            return defaultValue;
        }
        Properties properties = new Properties();
        try {
            FileInputStream input = new FileInputStream(file);
            try {
                properties.load(input);
            } finally {
                input.close();
            }
            String raw = properties.getProperty(key, "");
            if (raw.trim().length() == 0) {
                return defaultValue;
            }
            int value = Integer.parseInt(raw.trim());
            if (value > 0) {
                return value;
            }
        } catch (Throwable ignored) {
        }
        return defaultValue;
    }

    static final int ANNOUNCEMENT_X = 32;
    static final int ANNOUNCEMENT_Y = 86;
    static final int POPUP_CLOSE_X = 797;
    static final int POPUP_CLOSE_Y = 157;
    static final int POPUP_CHECKBOX_X = 486;
    static final int POPUP_CHECKBOX_Y = 539;
    static final int ENTER_GAME_X = 640;
    static final int ENTER_GAME_Y = 590;
    static final int DOWNLOAD_CONFIRM_X = 640;
    static final int DOWNLOAD_CONFIRM_Y = 468;
    static final int UPDATE_CONFIRM_X = 640;
    static final int UPDATE_CONFIRM_Y = 468;
    static final int HOME_POPUP_TOP_RIGHT_X = 835;
    static final int HOME_POPUP_TOP_RIGHT_Y = 50;
    static final int HOME_POPUP_OUTSIDE_LEFT_X = 330;
    static final int HOME_POPUP_OUTSIDE_LEFT_Y = 360;
    static final int HOME_POPUP_OUTSIDE_RIGHT_X = 950;
    static final int HOME_POPUP_OUTSIDE_RIGHT_Y = 360;
    static final int HOME_POPUP_BOTTOM_X = 640;
    static final int HOME_POPUP_BOTTOM_Y = 685;
    static final int HOME_LOBBY_X = 108;
    static final int HOME_LOBBY_Y = 675;
    static final int HOME_NOTICE_LIST_CLOSE_X = 804;
    static final int HOME_NOTICE_LIST_CLOSE_Y = 74;
    static final int EXIT_CONFIRM_CANCEL_X = 555;
    static final int EXIT_CONFIRM_CANCEL_Y = 452;
    static final int MAIL_ICON_X = 1232;
    static final int MAIL_ICON_Y = 24;
    static final int MAIL_CLAIM_ALL_X = 735;
    static final int MAIL_CLAIM_ALL_Y = 612;
    static final int MAIL_CONFIRM_X = 640;
    static final int MAIL_CONFIRM_Y = 500;
    static final int MAIL_CLOSE_X = 806;
    static final int MAIL_CLOSE_Y = 96;
    static final int DAILY_TASK_ICON_X = 1244;
    static final int DAILY_TASK_ICON_Y = 166;
    static final int DAILY_REWARD_CLAIM_ALL_X = 760;
    static final int DAILY_REWARD_CLAIM_ALL_Y = 650;
    static final int DAILY_REWARD_CONFIRM_X = 640;
    static final int DAILY_REWARD_CONFIRM_Y = 500;
    static final int DAILY_REWARD_DAILY_TAB_X = 512;
    static final int DAILY_REWARD_DAILY_TAB_Y = 132;
    static final int DAILY_REWARD_WEEKLY_TAB_X = 610;
    static final int DAILY_REWARD_WEEKLY_TAB_Y = 132;
    static final int FRIEND_ICON_X = 1244;
    static final int FRIEND_ICON_Y = 230;
    static final int FRIEND_RECEIVE_POINTS_X = 758;
    static final int FRIEND_RECEIVE_POINTS_ALT_X = 790;
    static final int FRIEND_RECEIVE_POINTS_FIRST_Y = 302;
    static final int FRIEND_RECEIVE_POINTS_ROW_GAP = 76;
    static final int FRIEND_SEND_POINTS_X = 748;
    static final int FRIEND_SEND_POINTS_Y = 613;
    static final int FRIEND_ONE_KEY_CLAIM_X = 748;
    static final int FRIEND_ONE_KEY_CLAIM_Y = 613;
    static final int FRIEND_CONFIRM_X = 640;
    static final int FRIEND_CONFIRM_Y = 452;
    static final int SHOP_ENTRY_X = 380;
    static final int SHOP_ENTRY_Y = 492;
    static final int SHOP_FREE_ITEM_X = 128;
    static final int SHOP_FREE_ITEM_Y = 432;
    static final int SHOP_QUANTITY_CONFIRM_X = 640;
    static final int SHOP_QUANTITY_CONFIRM_Y = 396;
    static final int SHOP_BUY_CONFIRM_X = 704;
    static final int SHOP_BUY_CONFIRM_Y = 594;
    static final int SHOP_REWARD_CONFIRM_X = 640;
    static final int SHOP_REWARD_CONFIRM_Y = 452;
    static final int SHOP_FREE_REFRESH_X = 156;
    static final int SHOP_FREE_REFRESH_Y = 326;
    static final int SHOP_REFRESH_CONFIRM_X = 730;
    static final int SHOP_REFRESH_CONFIRM_Y = 452;
    static final int SHOP_BACK_X = 22;
    static final int SHOP_BACK_Y = 677;
    static final int SHOP_TAB_BASIC_X = 30;
    static final int SHOP_TAB_BASIC_Y = 300;
    static final int SHOP_TAB_ARENA_X = 30;
    static final int SHOP_TAB_ARENA_Y = 370;
    static final int SHOP_TAB_BODY_TAG_X = 30;
    static final int SHOP_TAB_BODY_TAG_Y = 440;
    static final int SHOP_TAB_SCRAP_X = 30;
    static final int SHOP_TAB_SCRAP_Y = 510;
    static final int OUTPOST_ENTRY_X = 386;
    static final int OUTPOST_ENTRY_Y = 590;
    static final int OUTPOST_GET_REWARD_X = 720;
    static final int OUTPOST_GET_REWARD_Y = 615;
    static final int OUTPOST_CLEAN_SWEEP_X = 558;
    static final int OUTPOST_CLEAN_SWEEP_Y = 616;
    static final int OUTPOST_CLEAN_CONFIRM_X = 744;
    static final int OUTPOST_CLEAN_CONFIRM_Y = 553;
    static final int OUTPOST_NOTICE_DONT_SHOW_X = 609;
    static final int OUTPOST_NOTICE_DONT_SHOW_Y = 385;
    static final int OUTPOST_NOTICE_CONFIRM_X = 724;
    static final int OUTPOST_NOTICE_CONFIRM_Y = 451;
    static final int OUTPOST_CLEAN_REWARD_X = 640;
    static final int OUTPOST_CLEAN_REWARD_Y = 360;
    static final int OUTPOST_REWARD_CONFIRM_X = 640;
    static final int OUTPOST_REWARD_CONFIRM_Y = 500;
    static final int DISPATCH_BOARD_ENTRY_X = 464;
    static final int DISPATCH_BOARD_ENTRY_Y = 582;
    static final int DISPATCH_CLAIM_ALL_X = 755;
    static final int DISPATCH_CLAIM_ALL_Y = 610;
    static final int DISPATCH_ALL_X = 640;
    static final int DISPATCH_ALL_Y = 610;
    static final int DISPATCH_CONFIRM_X = 640;
    static final int DISPATCH_CONFIRM_Y = 610;
    static final int NIKKES_ENTRY_X = 495;
    static final int NIKKES_ENTRY_Y = 668;
    static final int NIKKES_INQUIRY_TAB_X = 1215;
    static final int NIKKES_INQUIRY_TAB_Y = 88;
    static final int INQUIRY_TOP_NIKKE_1_X = 255;
    static final int INQUIRY_TOP_NIKKE_1_Y = 242;
    static final int INQUIRY_TOP_NIKKE_2_X = 645;
    static final int INQUIRY_TOP_NIKKE_2_Y = 242;
    static final int INQUIRY_TOP_NIKKE_3_X = 1020;
    static final int INQUIRY_TOP_NIKKE_3_Y = 242;
    static final int INQUIRY_BATCH_BUTTON_X = 1226;
    static final int INQUIRY_BATCH_BUTTON_Y = 676;
    static final int INQUIRY_BATCH_CONFIRM_X = 640;
    static final int INQUIRY_BATCH_CONFIRM_Y = 452;
    static final int INQUIRY_NEXT_STEP_X = 640;
    static final int INQUIRY_NEXT_STEP_Y = 560;
    static final int INQUIRY_CLOSE_X = 792;
    static final int INQUIRY_CLOSE_Y = 676;
    static final int INQUIRY_GIFT_BUTTON_X = 545;
    static final int INQUIRY_GIFT_BUTTON_Y = 581;
    static final int INQUIRY_BASIC_GIFT_X = 510;
    static final int INQUIRY_BASIC_GIFT_Y = 372;
    static final int INQUIRY_SEND_GIFT_X = 780;
    static final int INQUIRY_SEND_GIFT_Y = 650;
    static final int INQUIRY_SEND_GIFT_CONFIRM_X = 640;
    static final int INQUIRY_SEND_GIFT_CONFIRM_Y = 452;
    static final int INQUIRY_GIFT_BACK_X = 32;
    static final int INQUIRY_GIFT_BACK_Y = 675;
    static final int INQUIRY_HOME_X = 108;
    static final int INQUIRY_HOME_Y = 675;
    static final int LEFT_BOTTOM_HOME_X = 108;
    static final int LEFT_BOTTOM_HOME_Y = 675;
    static final int BOTTOM_HOME_X = 640;
    static final int BOTTOM_HOME_Y = 675;
    static final int REHABILITATION_ENTRY_X = 74;
    static final int REHABILITATION_ENTRY_Y = 86;
    static final int ITEM_BAR_ENTRY_X = 712;
    static final int ITEM_BAR_ENTRY_Y = 658;
    static final int TEAM_RECRUIT_ENTRY_X = 785;
    static final int TEAM_RECRUIT_ENTRY_Y = 672;
    static final int ARK_ENTRY_X = 904;
    static final int ARK_ENTRY_Y = 474;
    static final int ARENA_ENTRY_X = 739;
    static final int ARENA_ENTRY_Y = 444;
    static final int ARENA_ENTRY_CONFIRM_X = 750;
    static final int ARENA_ENTRY_CONFIRM_Y = 528;
    static final int SIM_ROOM_ENTRY_X = 520;
    static final int SIM_ROOM_ENTRY_Y = 382;
    static final int SIM_ROOM_START_X = 640;
    static final int SIM_ROOM_START_Y = 420;
    static final int SIM_ROOM_QUICK_X = 735;
    static final int SIM_ROOM_QUICK_Y = 635;
    static final int SIM_ROOM_SKIP_BUFF_X = 640;
    static final int SIM_ROOM_SKIP_BUFF_Y = 640;
    static final int SIM_ROOM_END_X = 640;
    static final int SIM_ROOM_END_Y = 517;
    static final int INTERCEPTION_ENTRY_X = 590;
    static final int INTERCEPTION_ENTRY_Y = 564;
    static final int INTERCEPTION_ANOMALY_X = 790;
    static final int INTERCEPTION_ANOMALY_Y = 654;
    static final int INTERCEPTION_BOSS_CHANGE_X = 728;
    static final int INTERCEPTION_BOSS_CHANGE_Y = 259;
    static final int INTERCEPTION_KRAKEN_X = 640;
    static final int INTERCEPTION_KRAKEN_Y = 570;
    static final int INTERCEPTION_QUICK_BATTLE_X = 735;
    static final int INTERCEPTION_QUICK_BATTLE_Y = 610;
    static final int INTERCEPTION_SWEEP_BUTTON_X = 735;
    static final int INTERCEPTION_SWEEP_BUTTON_Y = 600;
    static final int INTERCEPTION_CHALLENGE_BOSS_X = 735;
    static final int INTERCEPTION_CHALLENGE_BOSS_Y = 663;
    static final int INTERCEPTION_CONFIRM_X = 735;
    static final int INTERCEPTION_CONFIRM_Y = 452;
    static final int INTERCEPTION_REWARD_CONFIRM_X = 640;
    static final int INTERCEPTION_REWARD_CONFIRM_Y = 452;
    static final int INTERCEPTION_SWEEP_MAX_ATTEMPTS = 4;
    static final int CLIMB_TOWER_ENTRY_X = 760;
    static final int CLIMB_TOWER_ENTRY_Y = 250;
    static final int CLIMB_TOWER_SELECTOR_X = 103;
    static final int CLIMB_TOWER_SELECTOR_Y = 48;
    static final int CLIMB_TOWER_CHOICE_BACK_X = 45;
    static final int CLIMB_TOWER_CHOICE_BACK_Y = 680;
    static final int CLIMB_TOWER_UNLIMITED_X = 640;
    static final int CLIMB_TOWER_UNLIMITED_Y = 300;
    static final int CLIMB_TOWER_COMPANY_1_X = 783;
    static final int CLIMB_TOWER_COMPANY_1_Y = 529;
    static final int CLIMB_TOWER_COMPANY_2_X = 687;
    static final int CLIMB_TOWER_COMPANY_2_Y = 531;
    static final int CLIMB_TOWER_COMPANY_3_X = 595;
    static final int CLIMB_TOWER_COMPANY_3_Y = 524;
    static final int CLIMB_TOWER_COMPANY_4_X = 498;
    static final int CLIMB_TOWER_COMPANY_4_Y = 518;
    static final int CLIMB_TOWER_ENTER_FIGHT_X = 760;
    static final int CLIMB_TOWER_ENTER_FIGHT_Y = 670;
    static final int CLIMB_TOWER_BACK_TO_LIST_X = 990;
    static final int CLIMB_TOWER_BACK_TO_LIST_Y = 270;
    static final int CLIMB_TOWER_RETRY_X = 760;
    static final int CLIMB_TOWER_RETRY_Y = 650;
    static final int LOOP_ROOM_ENTRY_X = 726;
    static final int LOOP_ROOM_ENTRY_Y = 613;
    static final int SYNC_ROOM_ENTRY_X = 552;
    static final int SYNC_ROOM_ENTRY_Y = 613;
    static final int PASS_ENTRY_X = 1145;
    static final int PASS_ENTRY_Y = 123;
    static final int PASS_REWARD_MENU_X = 1168;
    static final int PASS_REWARD_MENU_Y = 124;
    static final int PASS_TASK_TAB_X = 600;
    static final int PASS_TASK_TAB_Y = 238;
    static final int PASS_REWARD_TAB_X = 600;
    static final int PASS_REWARD_TAB_Y = 278;
    static final int PASS_CLAIM_ALL_X = 645;
    static final int PASS_CLAIM_ALL_Y = 668;
    static final int PASS_REWARD_CONFIRM_X = 640;
    static final int PASS_REWARD_CONFIRM_Y = 500;
    static final int UNION_ENTRY_X = 1245;
    static final int UNION_ENTRY_Y = 286;
    static final int UNION_RAID_ENTRY_X = 640;
    static final int UNION_RAID_ENTRY_Y = 594;
    static final int TEAM_BATTLE_ENTRY_X = 96;
    static final int TEAM_BATTLE_ENTRY_Y = 250;
    static final int START_GAME_WORKFLOW_ATTACH_WAIT_SECONDS = 60;
    static final int START_GAME_WORKFLOW_RESTART_WAIT_SECONDS = 75;
    static final int START_GAME_WAIT_SECONDS = 110;
    static final int BACK_TO_HOME_WAIT_SECONDS = 30;
    static final int BACK_TO_HOME_MAX_BACKS = 5;
    static final int UPDATE_SCENE_WAIT_SECONDS = 45;
    static final int CLAIM_MAIL_WAIT_SECONDS = 18;
    static final int PREVIEW_KEEP_ALIVE_SECONDS = 21600;
    static final int CLIMB_TOWER_FIGHT_WAIT_SECONDS = 75;

    private ProbeConfig() {
    }
}
