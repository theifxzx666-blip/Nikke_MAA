package com.codex.maanikke.rootprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.VirtualDisplay;
import android.os.Process;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class MaaNikkeTaskRunner {
    private final String taskName;
    private final String[] workflowSteps;
    private final ProbeLogger logger = new ProbeLogger(ProbeConfig.TASK_LOG_FILE);
    private final MaaEventLogger events = new MaaEventLogger(logger);
    private AndroidShellEnvironment environment;
    private GameLauncher launcher;
    private int displayId = -1;
    private int actionCount = 0;
    private boolean actionSuccess = false;
    private String finalState = "unknown";
    private boolean workflowMode = false;
    private String activeWorkflowStep = null;
    private final Map<String, String> taskOptions = new HashMap<String, String>();

    public MaaNikkeTaskRunner(String taskName) {
        this(taskName, new String[0]);
    }

    public MaaNikkeTaskRunner(String taskName, String[] workflowSteps) {
        this.taskName = taskName == null || taskName.length() == 0 ? "start_game" : taskName;
        this.workflowSteps = workflowSteps == null ? new String[0] : workflowSteps;
    }

    public static void main(String[] args) throws Exception {
        String task = args != null && args.length > 0 ? args[0] : "start_game";
        String[] steps = new String[0];
        if (args != null && args.length > 1) {
            steps = new String[args.length - 1];
            System.arraycopy(args, 1, steps, 0, steps.length);
        }
        new MaaNikkeTaskRunner(task, steps).run();
    }

    private void run() throws Exception {
        resetOutputFiles();
        logger.log("task start name=" + taskName + " pid=" + Process.myPid() + " uid=" + Process.myUid());
        events.taskChainStart(taskName, "pid=" + Process.myPid());
        loadTaskOptions();

        environment = new AndroidShellEnvironment(logger);
        environment.prepare();

        FrameCaptureBackend capture = new FrameCaptureBackend(logger);
        capture.start();
        PreviewFrameServer previewServer = new PreviewFrameServer(capture, logger);
        previewServer.start();

        VirtualDisplay virtualDisplay = null;
        boolean chainCompleted = false;
        String normalizedTaskName = normalizeTaskName(taskName);
        try {
            virtualDisplay = new VirtualDisplayController(logger)
                    .create(environment.getShellContext(), capture.getSurface());
            displayId = virtualDisplay.getDisplay().getDisplayId();
            logger.log("virtual display id=" + displayId
                    + " size=" + ProbeConfig.WIDTH + "x" + ProbeConfig.HEIGHT
                    + " dpi=" + ProbeConfig.DPI);

            launcher = new GameLauncher(environment);
            if ("start_game".equals(normalizedTaskName) || "smoke".equals(normalizedTaskName)) {
                launcher.startOnDisplay(displayId);
            } else if ("stop_game".equals(normalizedTaskName)) {
                launcher.bringToDisplay(displayId);
            } else if ("workflow_daily_safe".equals(normalizedTaskName)) {
                launcher.bringToDisplay(displayId);
            } else {
                launcher.bringToDisplay(displayId);
            }
            capture.awaitFirstFrame(10, TimeUnit.SECONDS);
            recoverBlackVirtualDisplayIfNeeded(capture, normalizedTaskName);

            InputInjector input = new InputInjector(environment.getShellContext(), logger);
            if ("smoke".equals(normalizedTaskName)) {
                runSmokeTask(capture, input);
            } else if ("start_game".equals(normalizedTaskName)) {
                runStartGameTask(capture, input);
            } else if ("workflow_daily_safe".equals(normalizedTaskName)) {
                runDailySafeWorkflow(capture, input);
            } else if ("back_to_home".equals(normalizedTaskName)) {
                runBackToHomeTask(capture, input);
            } else if ("handle_update".equals(normalizedTaskName)) {
                runHandleUpdateTask(capture, input);
            } else if ("claim_mail".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimMailTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_daily_rewards".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimDailyRewardsTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_friend_points".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimFriendPointsTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_outpost_defense".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimOutpostDefenseTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_free_shop".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimFreeShopTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_inquiry_and_gift".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimInquiryAndGiftTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_sim_room".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimSimRoomTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_climb_tower".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimClimbTowerTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_pass_rewards".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimPassRewardsTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("visit_mail".equals(normalizedTaskName)) {
                runVisitMailTask(capture, input);
            } else if ("visit_daily_rewards".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "daily_rewards",
                        ProbeConfig.DAILY_TASK_ICON_X, ProbeConfig.DAILY_TASK_ICON_Y, "daily_task_icon");
            } else if ("visit_friend_points".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "friend_points",
                        ProbeConfig.FRIEND_ICON_X, ProbeConfig.FRIEND_ICON_Y, "friend_icon");
            } else if ("visit_free_shop".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "free_shop",
                        ProbeConfig.SHOP_ENTRY_X, ProbeConfig.SHOP_ENTRY_Y, "shop_entry");
            } else if ("visit_outpost_defense".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "outpost_defense",
                        ProbeConfig.OUTPOST_ENTRY_X, ProbeConfig.OUTPOST_ENTRY_Y, "outpost_entry");
            } else if ("login_rewards".equals(normalizedTaskName)) {
                runLoginRewardsTask(capture, input);
            } else if ("visit_paid_shop".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "paid_shop",
                        ProbeConfig.SHOP_ENTRY_X, ProbeConfig.SHOP_ENTRY_Y, "paid_shop_entry");
            } else if ("visit_dispatch_board".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "dispatch_board",
                        ProbeConfig.DISPATCH_BOARD_ENTRY_X, ProbeConfig.DISPATCH_BOARD_ENTRY_Y, "dispatch_board_entry");
            } else if ("visit_inquiry_and_gift".equals(normalizedTaskName)) {
                runVisitNikkesSubpageTask(capture, input, "inquiry_and_gift",
                        ProbeConfig.NIKKES_ENTRY_X, ProbeConfig.NIKKES_ENTRY_Y, "nikkes_entry");
            } else if ("visit_gear_up".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "gear_up",
                        ProbeConfig.ITEM_BAR_ENTRY_X, ProbeConfig.ITEM_BAR_ENTRY_Y, "item_bar_entry");
            } else if ("visit_team_recruit".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "team_recruit",
                        ProbeConfig.TEAM_RECRUIT_ENTRY_X, ProbeConfig.TEAM_RECRUIT_ENTRY_Y, "team_recruit_entry");
            } else if ("visit_sim_room".equals(normalizedTaskName)) {
                runVisitArkSubpageTask(capture, input, "sim_room",
                        ProbeConfig.SIM_ROOM_ENTRY_X, ProbeConfig.SIM_ROOM_ENTRY_Y, "sim_room_entry");
            } else if ("visit_arena".equals(normalizedTaskName)) {
                runVisitArkSubpageTask(capture, input, "arena",
                        ProbeConfig.ARENA_ENTRY_X, ProbeConfig.ARENA_ENTRY_Y, "arena_entry");
            } else if ("visit_interception".equals(normalizedTaskName)) {
                runVisitArkSubpageTask(capture, input, "interception",
                        ProbeConfig.INTERCEPTION_ENTRY_X, ProbeConfig.INTERCEPTION_ENTRY_Y, "interception_entry");
            } else if ("visit_climb_tower".equals(normalizedTaskName)) {
                runVisitArkSubpageTask(capture, input, "climb_tower",
                        ProbeConfig.CLIMB_TOWER_ENTRY_X, ProbeConfig.CLIMB_TOWER_ENTRY_Y, "climb_tower_entry");
            } else if ("visit_loop_room_and_sync".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "loop_room_and_sync",
                        ProbeConfig.OUTPOST_ENTRY_X, ProbeConfig.OUTPOST_ENTRY_Y, "outpost_for_loop_room_entry");
            } else if ("visit_pass_rewards".equals(normalizedTaskName)) {
                runVisitHomeEntryTask(capture, input, "pass_rewards",
                        ProbeConfig.PASS_ENTRY_X, ProbeConfig.PASS_ENTRY_Y, "pass_entry");
            } else if ("visit_team_battle".equals(normalizedTaskName)) {
                runVisitEventSubpageTask(capture, input, "team_battle",
                        ProbeConfig.TEAM_BATTLE_ENTRY_X, ProbeConfig.TEAM_BATTLE_ENTRY_Y, "team_battle_entry");
            } else if ("visit_union_raid".equals(normalizedTaskName)) {
                runVisitUnionRaidTask(capture, input);
            } else if ("visit_rehabilitation".equals(normalizedTaskName)) {
                runVisitNikkesSubpageTask(capture, input, "rehabilitation",
                        ProbeConfig.REHABILITATION_ENTRY_X, ProbeConfig.REHABILITATION_ENTRY_Y, "rehabilitation_entry");
            } else if ("stop_game".equals(normalizedTaskName)) {
                runStopGameTask(capture);
            } else {
                runPendingAndroidAdapterTask(capture, normalizedTaskName);
            }
            chainCompleted = true;
            events.taskChainComplete(taskName, finalState, "actions=" + actionCount);
            if (!"stop_game".equals(normalizedTaskName) && !workflowContainsStopGame()) {
                writeResult(capture, "preview_keep_alive_0s");
                keepPreviewAlive(capture);
            } else {
                writeResult(capture, "finished");
            }
        } catch (Throwable error) {
            events.taskChainError(taskName, finalState, error);
            throw error;
        } finally {
            if (!chainCompleted) {
                logger.log("task chain released before completed state=" + finalState);
            }
            previewServer.close();
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
            capture.close();
            logger.log("task released");
        }
    }

    private void keepPreviewAlive(FrameCaptureBackend capture) throws Exception {
        logger.log("task completed; keeping virtual display and preview alive until user stops game");
        events.taskChainComplete(taskName, finalState, "preview_keep_alive=true");
        for (int second = 1; second <= ProbeConfig.PREVIEW_KEEP_ALIVE_SECONDS; second++) {
            Thread.sleep(1000);
            if (second % 30 == 0) {
                writeResult(capture, "preview_keep_alive_" + second + "s");
                logger.log("preview keep-alive seconds=" + second
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples());
            }
        }
    }

    private boolean workflowContainsStopGame() {
        for (int i = 0; i < workflowSteps.length; i++) {
            if ("stop_game".equals(normalizeTaskName(workflowSteps[i]))) {
                return true;
            }
        }
        return false;
    }

    private void loadTaskOptions() {
        taskOptions.clear();
        if (!ProbeConfig.TASK_OPTIONS_FILE.exists()) {
            logger.log("task options file missing; using defaults");
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(ProbeConfig.TASK_OPTIONS_FILE), "UTF-8"));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() == 0 || trimmed.startsWith("#")) {
                    continue;
                }
                int index = trimmed.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, index).trim();
                String value = trimmed.substring(index + 1).trim();
                if (key.length() > 0) {
                    taskOptions.put(key, value);
                    count++;
                }
            }
            logger.log("task options loaded count=" + count);
        } catch (Throwable error) {
            logger.log("task options load failed: "
                    + error.getClass().getName() + ": " + error.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private String taskOptionValue(int optionIndex, String defaultValue) {
        String[] candidates = optionTaskNameCandidates();
        for (int i = 0; i < candidates.length; i++) {
            String candidate = candidates[i];
            if (candidate == null || candidate.length() == 0) {
                continue;
            }
            String key = candidate + ".option." + optionIndex;
            String value = taskOptions.get(key);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return defaultValue;
    }

    private boolean taskOptionYes(int optionIndex, boolean defaultValue) {
        String value = taskOptionValue(optionIndex, defaultValue ? "Yes" : "No");
        return "Yes".equalsIgnoreCase(value)
                || "Y".equalsIgnoreCase(value)
                || "On".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "是".equals(value)
                || "开启".equals(value)
                || "启用".equals(value);
    }

    private int taskOptionInt(int optionIndex, int defaultValue, int minValue, int maxValue) {
        String value = taskOptionValue(optionIndex, String.valueOf(defaultValue));
        int parsed = defaultValue;
        try {
            parsed = Integer.parseInt(value);
        } catch (Throwable error) {
            logger.log("task option int parse fallback option=" + optionIndex + " value=" + value);
        }
        if (parsed < minValue) {
            return minValue;
        }
        if (parsed > maxValue) {
            return maxValue;
        }
        return parsed;
    }

    private String[] optionTaskNameCandidates() {
        String context = activeWorkflowStep != null && activeWorkflowStep.length() > 0
                ? activeWorkflowStep
                : normalizeTaskName(taskName);
        String executable = toExecutableTaskName(context);
        String visit = toVisitTaskOptionName(executable);
        String rootExecutable = toExecutableTaskName(normalizeTaskName(taskName));
        String rootVisit = toVisitTaskOptionName(rootExecutable);
        return new String[]{
                context,
                executable,
                visit,
                normalizeTaskName(taskName),
                rootExecutable,
                rootVisit
        };
    }

    private String toVisitTaskOptionName(String executableName) {
        String name = toExecutableTaskName(executableName);
        if ("claim_inquiry_and_gift".equals(name)) {
            return "visit_inquiry_and_gift";
        }
        if ("claim_climb_tower".equals(name)) {
            return "visit_climb_tower";
        }
        if ("claim_sim_room".equals(name)) {
            return "visit_sim_room";
        }
        if ("claim_pass_rewards".equals(name)) {
            return "visit_pass_rewards";
        }
        if ("claim_mail".equals(name)) {
            return "visit_mail";
        }
        if ("claim_daily_rewards".equals(name)) {
            return "visit_daily_rewards";
        }
        if ("claim_friend_points".equals(name)) {
            return "visit_friend_points";
        }
        if ("claim_outpost_defense".equals(name)) {
            return "visit_outpost_defense";
        }
        if ("claim_free_shop".equals(name)) {
            return "visit_free_shop";
        }
        return name;
    }

    private String normalizeTaskName(String name) {
        if ("startgame".equals(name)) {
            return "start_game";
        }
        if ("claimmail".equals(name)) {
            return "visit_mail";
        }
        if ("claimdailyrewards".equals(name)) {
            return "claim_daily_rewards";
        }
        if ("claimfriendpoints".equals(name)) {
            return "claim_friend_points";
        }
        if ("claimoutpostdefense".equals(name)) {
            return "claim_outpost_defense";
        }
        if ("claimpassrewards".equals(name)) {
            return "claim_pass_rewards";
        }
        if ("dailyrewards".equals(name)) {
            return "visit_daily_rewards";
        }
        if ("friendpoints".equals(name)) {
            return "visit_friend_points";
        }
        if ("freeshopdaily".equals(name)) {
            return "visit_free_shop";
        }
        if ("outpostdefense".equals(name)) {
            return "visit_outpost_defense";
        }
        if ("loginrewards".equals(name)) {
            return "login_rewards";
        }
        if ("payshop".equals(name)) {
            return "visit_paid_shop";
        }
        if ("dispatchboard".equals(name)) {
            return "visit_dispatch_board";
        }
        if ("inquiryandgift".equals(name)) {
            return "visit_inquiry_and_gift";
        }
        if ("gearup".equals(name)) {
            return "visit_gear_up";
        }
        if ("teamrecruit".equals(name)) {
            return "visit_team_recruit";
        }
        if ("simroom".equals(name)) {
            return "visit_sim_room";
        }
        if ("arena".equals(name)) {
            return "visit_arena";
        }
        if ("interception".equals(name)) {
            return "visit_interception";
        }
        if ("climbtower".equals(name)) {
            return "visit_climb_tower";
        }
        if ("looproomandsync".equals(name)) {
            return "visit_loop_room_and_sync";
        }
        if ("claimpassreward".equals(name)) {
            return "visit_pass_rewards";
        }
        if ("teambattle".equals(name)) {
            return "visit_team_battle";
        }
        if ("lianmengtuxi".equals(name)) {
            return "visit_union_raid";
        }
        if ("gengshengguan".equals(name)) {
            return "visit_rehabilitation";
        }
        if ("stopgame".equals(name)) {
            return "stop_game";
        }
        return name;
    }

    private boolean isDebugDryRunTask(String name) {
        return name != null && name.startsWith("debug_");
    }

    private String toExecutableTaskName(String name) {
        if (name != null && name.startsWith("debug_")) {
            return name.substring("debug_".length());
        }
        return name;
    }

    private void runPendingAndroidAdapterTask(FrameCaptureBackend capture, String normalizedTaskName) throws Exception {
        boolean hasUsefulFrame = waitForAnyUsefulFrame(capture, 8);
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = "task_pending_android_adapter:" + normalizedTaskName;
        actionSuccess = true;
        logger.log("MaaNikke PC task is present in catalog but Android adapter is pending task="
                + normalizedTaskName + " hasUsefulFrame=" + hasUsefulFrame);
    }

    private void runStopGameTask(FrameCaptureBackend capture) throws Exception {
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        launcher.forceStop();
        Thread.sleep(1000);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = "game_force_stopped";
        actionSuccess = true;
    }

    private void runSmokeTask(FrameCaptureBackend capture, InputInjector input) throws Exception {
        waitForUsefulFrame(capture, 60);
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        tap(input, ProbeConfig.ANNOUNCEMENT_X, ProbeConfig.ANNOUNCEMENT_Y, "announcement_button");
        Thread.sleep(5000);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        actionSuccess = true;
        finalState = "smoke_tap_sent";
    }

    private void runStartGameTask(FrameCaptureBackend capture, InputInjector input) throws Exception {
        boolean hasUsefulFrame = workflowMode
                ? waitForAnyUsefulFrame(capture, 24)
                : waitForUsefulFrame(capture, 75);
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!hasUsefulFrame) {
            if (workflowMode) {
                logger.log("workflow start_game did not receive useful frames; force restart game on display="
                        + displayId);
                launcher.startOnDisplay(displayId);
                capture.awaitFirstFrame(10, TimeUnit.SECONDS);
                hasUsefulFrame = waitForAnyUsefulFrame(capture, 45);
                capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
                copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            }
            if (!hasUsefulFrame) {
                finalState = "no_useful_frame";
                logger.log("start_game stopped because no useful frame appeared");
                return;
            }
        }

        if (workflowMode && capture.getNonBlackFrameCount() > 0) {
            if (isHomeClearVisible(ProbeConfig.TASK_BEFORE_ACTION_FILE)) {
                finalState = "home_clear";
                actionSuccess = true;
                logger.log("workflow start_game detected running game already at home");
                return;
            }
            logger.log("workflow start_game detected existing game frames; wait for stable lobby");
            waitForGameLoadOrEnter(capture, input);
            actionSuccess = "home_clear".equals(finalState)
                    || "home_popup_closed".equals(finalState)
                    || "announcement_closed_by_close".equals(finalState)
                    || "announcement_closed_by_back".equals(finalState)
                    || "network_retry_required".equals(finalState)
                    || "login_required".equals(finalState);
            if (!actionSuccess && capture.getNonBlackFrameCount() > 0
                    && !"announcement_still_visible".equals(finalState)
                    && !"home_popup_still_visible".equals(finalState)
                    && !finalState.startsWith("client_update_external")) {
                finalState = "running_game_detected";
                actionSuccess = true;
            }
            logger.log("workflow start_game attached existing game; state=" + finalState
                    + " success=" + actionSuccess);
            return;
        }

        if (isHomeClearVisible(ProbeConfig.TASK_BEFORE_ACTION_FILE)) {
            finalState = "home_clear";
            actionSuccess = true;
            logger.log("start_game detected running game already at home; skip restart/loading flow");
            return;
        }

        tap(input, ProbeConfig.ANNOUNCEMENT_X, ProbeConfig.ANNOUNCEMENT_Y, "announcement_button");
        Thread.sleep(2800);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_OPEN_FILE);
        copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);

        boolean dialogVisible = isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_OPEN_FILE);
        logger.log("announcement dialog after open=" + dialogVisible);
        if (dialogVisible) {
            closeAnnouncementDialog(capture, input, "after_open");
        } else {
            finalState = "announcement_not_detected_after_open";
        }
        waitForGameLoadOrEnter(capture, input);

        actionSuccess = actionCount > 0
                && !"announcement_still_visible".equals(finalState)
                && !"home_popup_still_visible".equals(finalState)
                && !finalState.startsWith("client_update_external");
    }

    private void runBackToHomeTask(FrameCaptureBackend capture, InputInjector input) throws Exception {
        boolean hasUsefulFrame = waitForAnyUsefulFrame(capture, ProbeConfig.BACK_TO_HOME_WAIT_SECONDS);
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!hasUsefulFrame) {
            finalState = "no_useful_frame";
            logger.log("back_to_home stopped because no useful frame appeared");
            return;
        }

        for (int attempt = 0; attempt <= ProbeConfig.BACK_TO_HOME_MAX_BACKS; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);

            String foreground = getForegroundPackageOnTargetDisplay();
            if (foreground.length() > 0 && !isTargetGamePackage(foreground)) {
                finalState = "client_update_external:" + foreground;
                logger.log("target display left NIKKE during back_to_home, foreground=" + foreground);
                return;
            }

            if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("back_to_home found exit confirm dialog, cancel it");
                cancelExitGameConfirm(capture, input);
            } else if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "home_clear";
                actionSuccess = true;
                writeResult(capture, "back_home_attempt_" + attempt);
                return;
            } else if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("back_to_home found announcement dialog");
                closeAnnouncementDialog(capture, input, "back_to_home");
            } else if (isUpdateDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.UPDATE_CONFIRM_X, ProbeConfig.UPDATE_CONFIRM_Y,
                        "back_to_home_update_confirm");
                Thread.sleep(2200);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_UPDATE_FILE);
                copyFile(ProbeConfig.TASK_AFTER_UPDATE_FILE, ProbeConfig.TASK_FRAME_FILE);
                finalState = "update_confirmed";
            } else if (isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.DOWNLOAD_CONFIRM_X, ProbeConfig.DOWNLOAD_CONFIRM_Y,
                        "back_to_home_download_confirm");
                Thread.sleep(1800);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE);
                copyFile(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE, ProbeConfig.TASK_FRAME_FILE);
                finalState = "download_confirmed";
            } else if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                closeHomePopupIfVisible(capture, input);
            } else if (isMailPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.MAIL_CLOSE_X, ProbeConfig.MAIL_CLOSE_Y,
                        "back_to_home_mail_close");
                Thread.sleep(1200);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_BACK_FILE);
                copyFile(ProbeConfig.TASK_AFTER_BACK_FILE, ProbeConfig.TASK_FRAME_FILE);
            }

            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "home_clear";
                actionSuccess = true;
                writeResult(capture, "back_home_attempt_" + attempt);
                return;
            }

            if (attempt < ProbeConfig.BACK_TO_HOME_MAX_BACKS) {
                if (attempt == 0 || "unknown".equals(finalState) || "download_confirmed".equals(finalState)
                        || "update_confirmed".equals(finalState)) {
                    tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                            "back_to_home_lobby_anchor_" + (attempt + 1));
                } else if (attempt < 3) {
                    tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                            "back_to_home_lobby_retry_" + (attempt + 1));
                } else {
                    tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                            "back_to_home_lobby_final_retry_" + (attempt + 1));
                }
                Thread.sleep(1400);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_BACK_FILE);
                copyFile(ProbeConfig.TASK_AFTER_BACK_FILE, ProbeConfig.TASK_FRAME_FILE);
                writeResult(capture, "back_home_attempt_" + attempt);
            }
        }

        logger.log("back_to_home did not find home after back attempts, bring game to target display");
        try {
            finalState = "unknown";
            launcher.bringToDisplay(displayId);
            Thread.sleep(2500);
            waitForGameLoadOrEnter(capture, input);
        } catch (Throwable error) {
            finalState = "back_to_home_attach_failed";
            logger.log("back_to_home attach fallback failed: "
                    + error.getClass().getName() + ": " + error.getMessage());
        }
        actionSuccess = "home_clear".equals(finalState);
        if (!actionSuccess && !"home_popup_still_visible".equals(finalState)
                && !"login_required".equals(finalState)
                && !"network_retry_required".equals(finalState)
                && !finalState.startsWith("client_update_external")) {
            finalState = "home_not_found";
        }
    }

    private void runHandleUpdateTask(FrameCaptureBackend capture, InputInjector input) throws Exception {
        boolean hasUsefulFrame = waitForAnyUsefulFrame(capture, ProbeConfig.BACK_TO_HOME_WAIT_SECONDS);
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!hasUsefulFrame) {
            finalState = "no_useful_frame";
            logger.log("handle_update stopped because no useful frame appeared");
            return;
        }

        boolean handled = false;
        for (int second = 1; second <= ProbeConfig.UPDATE_SCENE_WAIT_SECONDS; second++) {
            Thread.sleep(1000);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);

            String foreground = getForegroundPackageOnTargetDisplay();
            if (foreground.length() > 0 && !isTargetGamePackage(foreground)) {
                finalState = handled ? "client_update_external:" + foreground : "external_foreground:" + foreground;
                logger.log("handle_update target display left NIKKE, foreground=" + foreground);
                actionSuccess = handled;
                return;
            }

            if (isUpdateDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.UPDATE_CONFIRM_X, ProbeConfig.UPDATE_CONFIRM_Y,
                        "handle_update_confirm");
                Thread.sleep(2200);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_UPDATE_FILE);
                copyFile(ProbeConfig.TASK_AFTER_UPDATE_FILE, ProbeConfig.TASK_FRAME_FILE);
                handled = true;
                finalState = "update_confirmed";
            } else if (isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.DOWNLOAD_CONFIRM_X, ProbeConfig.DOWNLOAD_CONFIRM_Y,
                        "handle_download_confirm");
                Thread.sleep(1800);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE);
                copyFile(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE, ProbeConfig.TASK_FRAME_FILE);
                handled = true;
                finalState = "download_confirmed";
            } else if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("handle_update found announcement dialog, trying to close it first");
                closeAnnouncementDialog(capture, input, "handle_update");
                handled = true;
                finalState = "announcement_closed_for_update";
            } else if (isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                closeHomePopupIfVisible(capture, input);
                if ("home_clear".equals(finalState) || "home_popup_closed".equals(finalState)) {
                    handled = true;
                }
            }

            if ("home_clear".equals(finalState) || isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = handled ? "handled_home_clear" : "no_update_home_clear";
                actionSuccess = true;
                writeResult(capture, "handle_update_home_" + second + "s");
                return;
            }

            if (handled) {
                String afterForeground = getForegroundPackageOnTargetDisplay();
                if (afterForeground.length() > 0 && !isTargetGamePackage(afterForeground)) {
                    finalState = "client_update_external:" + afterForeground;
                    actionSuccess = true;
                    return;
                }
            }

            if (second % 5 == 0 || handled) {
                writeResult(capture, "handle_update_wait_" + second + "s");
                logger.log("handle_update wait seconds=" + second
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples()
                        + " handled=" + handled
                        + " finalState=" + finalState);
            }
        }

        actionSuccess = handled;
        if (!handled) {
            finalState = "no_update_or_download_dialog";
            actionSuccess = true;
        }
    }

    private void runDailySafeWorkflow(FrameCaptureBackend capture, InputInjector input) throws Exception {
        workflowMode = true;
        String[] steps = workflowSteps.length == 0
                ? new String[]{"start_game", "visit_mail", "visit_daily_rewards", "visit_friend_points",
                "visit_free_shop", "visit_outpost_defense"}
                : workflowSteps;
        logger.log("workflow_daily_safe start; MaaNikke-style ordered task queue; steps=" + joinStepsForLog(steps));
        events.taskChainStart("workflow_daily_safe", "steps=" + steps.length);
        for (int i = 0; i < steps.length; i++) {
            runWorkflowStepByName(capture, input, steps[i]);
            if (isWorkflowStopState(finalState)) {
                actionSuccess = true;
                writeResult(capture, "workflow_stopped_" + finalState);
                logger.log("workflow_daily_safe stopped for manual handling after step="
                        + normalizeTaskName(steps[i]) + " state=" + finalState);
                events.taskChainComplete("workflow_daily_safe", finalState,
                        "stopped_for_manual_handling=true step=" + normalizeTaskName(steps[i]));
                return;
            }
        }
        actionSuccess = true;
        finalState = "workflow_daily_safe_completed";
        writeResult(capture, "workflow_finished");
        events.taskChainComplete("workflow_daily_safe", finalState, "steps=" + steps.length);
    }

    private void runWorkflowStepByName(final FrameCaptureBackend capture, final InputInjector input,
                                       final String stepName) throws Exception {
        final String normalizedStepName = normalizeTaskName(stepName);
        if ("start_game".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runStartGameTask(capture, input);
                }
            });
        } else if ("back_to_home".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runBackToHomeTask(capture, input);
                }
            });
        } else if ("handle_update".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runHandleUpdateTask(capture, input);
                }
            });
        } else if ("visit_mail".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitMailTask(capture, input);
                }
            });
        } else if ("claim_mail".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimMailTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_daily_rewards".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimDailyRewardsTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_friend_points".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimFriendPointsTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_outpost_defense".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimOutpostDefenseTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_free_shop".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimFreeShopTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_inquiry_and_gift".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimInquiryAndGiftTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_sim_room".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimSimRoomTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_climb_tower".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimClimbTowerTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_pass_rewards".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimPassRewardsTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("visit_daily_rewards".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "daily_rewards",
                            ProbeConfig.DAILY_TASK_ICON_X, ProbeConfig.DAILY_TASK_ICON_Y, "daily_task_icon");
                }
            });
        } else if ("visit_friend_points".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "friend_points",
                            ProbeConfig.FRIEND_ICON_X, ProbeConfig.FRIEND_ICON_Y, "friend_icon");
                }
            });
        } else if ("visit_free_shop".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "free_shop",
                            ProbeConfig.SHOP_ENTRY_X, ProbeConfig.SHOP_ENTRY_Y, "shop_entry");
                }
            });
        } else if ("visit_outpost_defense".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "outpost_defense",
                            ProbeConfig.OUTPOST_ENTRY_X, ProbeConfig.OUTPOST_ENTRY_Y, "outpost_entry");
                }
            });
        } else if ("login_rewards".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runLoginRewardsTask(capture, input);
                }
            });
        } else if ("visit_paid_shop".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "paid_shop",
                            ProbeConfig.SHOP_ENTRY_X, ProbeConfig.SHOP_ENTRY_Y, "paid_shop_entry");
                }
            });
        } else if ("visit_dispatch_board".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "dispatch_board",
                            ProbeConfig.DISPATCH_BOARD_ENTRY_X, ProbeConfig.DISPATCH_BOARD_ENTRY_Y,
                            "dispatch_board_entry");
                }
            });
        } else if ("visit_inquiry_and_gift".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitNikkesSubpageTask(capture, input, "inquiry_and_gift",
                            ProbeConfig.NIKKES_ENTRY_X, ProbeConfig.NIKKES_ENTRY_Y, "nikkes_entry");
                }
            });
        } else if ("visit_gear_up".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "gear_up",
                            ProbeConfig.ITEM_BAR_ENTRY_X, ProbeConfig.ITEM_BAR_ENTRY_Y, "item_bar_entry");
                }
            });
        } else if ("visit_team_recruit".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "team_recruit",
                            ProbeConfig.TEAM_RECRUIT_ENTRY_X, ProbeConfig.TEAM_RECRUIT_ENTRY_Y,
                            "team_recruit_entry");
                }
            });
        } else if ("visit_sim_room".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitArkSubpageTask(capture, input, "sim_room",
                            ProbeConfig.SIM_ROOM_ENTRY_X, ProbeConfig.SIM_ROOM_ENTRY_Y, "sim_room_entry");
                }
            });
        } else if ("visit_arena".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitArkSubpageTask(capture, input, "arena",
                            ProbeConfig.ARENA_ENTRY_X, ProbeConfig.ARENA_ENTRY_Y, "arena_entry");
                }
            });
        } else if ("visit_interception".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitArkSubpageTask(capture, input, "interception",
                            ProbeConfig.INTERCEPTION_ENTRY_X, ProbeConfig.INTERCEPTION_ENTRY_Y,
                            "interception_entry");
                }
            });
        } else if ("visit_climb_tower".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitArkSubpageTask(capture, input, "climb_tower",
                            ProbeConfig.CLIMB_TOWER_ENTRY_X, ProbeConfig.CLIMB_TOWER_ENTRY_Y,
                            "climb_tower_entry");
                }
            });
        } else if ("visit_loop_room_and_sync".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "loop_room_and_sync",
                            ProbeConfig.OUTPOST_ENTRY_X, ProbeConfig.OUTPOST_ENTRY_Y,
                            "outpost_for_loop_room_entry");
                }
            });
        } else if ("visit_pass_rewards".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitHomeEntryTask(capture, input, "pass_rewards",
                            ProbeConfig.PASS_ENTRY_X, ProbeConfig.PASS_ENTRY_Y, "pass_entry");
                }
            });
        } else if ("visit_team_battle".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitEventSubpageTask(capture, input, "team_battle",
                            ProbeConfig.TEAM_BATTLE_ENTRY_X, ProbeConfig.TEAM_BATTLE_ENTRY_Y,
                            "team_battle_entry");
                }
            });
        } else if ("visit_union_raid".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitUnionRaidTask(capture, input);
                }
            });
        } else if ("visit_rehabilitation".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runVisitNikkesSubpageTask(capture, input, "rehabilitation",
                            ProbeConfig.REHABILITATION_ENTRY_X, ProbeConfig.REHABILITATION_ENTRY_Y,
                            "rehabilitation_entry");
                }
            });
        } else if ("stop_game".equals(normalizedStepName)) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runStopGameTask(capture);
                }
            });
        } else {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runPendingAndroidAdapterTask(capture, normalizedStepName);
                }
            });
        }
    }

    private void runWorkflowStep(FrameCaptureBackend capture, InputInjector input, String stepName,
                                 StepRunner runner) throws Exception {
        String previousState = finalState;
        logger.log("workflow step start name=" + stepName + " previousState=" + previousState);
        events.subTaskStart(stepName, "previousState=" + previousState);
        finalState = "workflow_running:" + stepName;
        actionSuccess = false;
        writeResult(capture, "workflow_" + stepName + "_start");
        String previousWorkflowStep = activeWorkflowStep;
        activeWorkflowStep = stepName;
        try {
            runner.run();
        } catch (Throwable error) {
            events.subTaskError(stepName, finalState, error);
            throw error;
        } finally {
            activeWorkflowStep = previousWorkflowStep;
        }
        logger.log("workflow step finish name=" + stepName
                + " success=" + actionSuccess
                + " finalState=" + finalState
                + " actions=" + actionCount);
        writeResult(capture, "workflow_" + stepName + "_finish");
        if (!actionSuccess && !isNonFatalWorkflowState(finalState)) {
            events.subTaskError(stepName, finalState,
                    new IllegalStateException("workflow step failed state=" + finalState));
            throw new IllegalStateException("workflow step failed: " + stepName + " state=" + finalState);
        }
        events.subTaskComplete(stepName, finalState, "actions=" + actionCount);
        if ("stop_game".equals(stepName)) {
            return;
        }
        if (isWorkflowStopState(finalState)) {
            logger.log("workflow step requires manual handling; skip return home step="
                    + stepName + " state=" + finalState);
            return;
        }
        if (finalState != null && finalState.endsWith("_still_home")) {
            logger.log("workflow step stayed on home; no return action needed for step=" + stepName);
            finalState = "home_clear";
            actionSuccess = true;
            return;
        }
        if (!"home_clear".equals(finalState)) {
            returnToHomeForWorkflow(capture, input, stepName);
        }
    }

    private void returnToHomeForWorkflow(FrameCaptureBackend capture, InputInjector input, String stepName) throws Exception {
        logger.log("workflow return home after step=" + stepName + " state=" + finalState);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "home_clear";
            actionSuccess = true;
            stabilizeHomeAfterWorkflowStep(capture, input, stepName);
            logger.log("workflow return skipped, already home after step=" + stepName);
            return;
        }
        tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                "workflow_lobby_anchor_before_back_" + stepName);
        Thread.sleep(900);
        if (tryFastReturnHomeForWorkflow(capture, input, stepName)) {
            return;
        }
        if ("visit_mail".equals(stepName) && isMailPageVisible(ProbeConfig.TASK_FRAME_FILE)) {
            tap(input, ProbeConfig.MAIL_CLOSE_X, ProbeConfig.MAIL_CLOSE_Y, "workflow_mail_close");
            Thread.sleep(1200);
        } else {
            tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                    "workflow_lobby_anchor_after_" + stepName);
            Thread.sleep(1200);
        }
        if (tryFastReturnHomeForWorkflow(capture, input, stepName)) {
            return;
        }
        runBackToHomeTask(capture, input);
        if ("home_clear".equals(finalState)) {
            stabilizeHomeAfterWorkflowStep(capture, input, stepName);
        }
    }

    private boolean tryFastReturnHomeForWorkflow(FrameCaptureBackend capture, InputInjector input, String stepName)
            throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "home_clear";
                actionSuccess = true;
                stabilizeHomeAfterWorkflowStep(capture, input, stepName);
                logger.log("workflow fast return home success step=" + stepName + " attempt=" + attempt);
                return true;
            }
            if (isMailPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.MAIL_CLOSE_X, ProbeConfig.MAIL_CLOSE_Y,
                        "workflow_fast_mail_close_" + (attempt + 1));
            } else if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                cancelExitGameConfirm(capture, input);
            } else if (closeDebugPreviewDialogIfVisible(capture, input,
                    "workflow_fast_debug_close_" + stepName + "_" + (attempt + 1))) {
                // Dialog was closed by the helper. Let the next loop verify whether we are home.
            } else if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                closeHomePopupIfVisible(capture, input);
            } else if (attempt < 2) {
                tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                        "workflow_fast_lobby_anchor_" + stepName + "_" + (attempt + 1));
            } else {
                tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                        "workflow_fast_lobby_retry_" + stepName + "_" + (attempt + 1));
            }
            Thread.sleep(900);
        }
        return false;
    }

    private void stabilizeHomeAfterWorkflowStep(FrameCaptureBackend capture, InputInjector input, String stepName)
            throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                cancelExitGameConfirm(capture, input);
                continue;
            }
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    && !isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    && !isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                        "workflow_lobby_anchor_" + stepName + "_" + attempt);
                Thread.sleep(320);
                continue;
            }
            if (closeDebugPreviewDialogIfVisible(capture, input,
                    "workflow_stabilize_debug_close_" + stepName + "_" + attempt)) {
                continue;
            }
            if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                closeHomePopupIfVisible(capture, input);
                continue;
            }
            if (!isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("workflow lobby stabilize skipped, not home step=" + stepName
                        + " attempt=" + attempt);
                break;
            }
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            Thread.sleep(900);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "home_clear";
            actionSuccess = true;
        }
    }

    private void ensureHomeForTaskStart(FrameCaptureBackend capture, InputInjector input, String pageName)
            throws Exception {
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "home_clear";
            actionSuccess = true;
            stabilizeHomeAfterWorkflowStep(capture, input, "before_" + pageName);
            Thread.sleep(700);
            return;
        }
        runBackToHomeTask(capture, input);
    }

    private interface StepRunner {
        void run() throws Exception;
    }

    private String joinStepsForLog(String[] steps) {
        if (steps == null || steps.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(steps[i]);
        }
        return builder.toString();
    }

    private boolean isNonFatalWorkflowState(String state) {
        return state != null && (state.startsWith("visit_")
                || state.startsWith("adapter_")
                || state.startsWith("manual_confirm_")
                || state.startsWith("readonly_"));
    }

    private boolean isWorkflowStopState(String state) {
        return "login_required".equals(state)
                || "network_retry_required".equals(state)
                || (state != null && state.indexOf("battle_failed_retry") >= 0)
                || (state != null && state.startsWith("client_update_external:"));
    }

    private void runVisitMailTask(FrameCaptureBackend capture, InputInjector input) throws Exception {
        ensureHomeForTaskStart(capture, input, "mail");
        if (!"home_clear".equals(finalState)) {
            logger.log("visit_mail stopped before mail open, home not clear state=" + finalState);
            actionSuccess = false;
            return;
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        boolean pageOpened = openMailPage(capture, input, "mail_icon_visit_only");
        copyFile(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);

        boolean pageVisible = pageOpened || isMailPageVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE);
        boolean claimVisible = pageVisible && isMailClaimButtonVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE);
        logger.log("visit_mail pageVisible=" + pageVisible + " claimButtonVisible=" + claimVisible
                + " safeMode=no_claim");
        finalState = pageVisible
                ? (claimVisible ? "visit_mail_page_claim_available" : "visit_mail_page_no_claim_button")
                : "visit_mail_page_not_detected";
        actionSuccess = pageVisible;
    }

    private void runVisitHomeEntryTask(FrameCaptureBackend capture, InputInjector input, String pageName,
                                      int x, int y, String label) throws Exception {
        ensureHomeForTaskStart(capture, input, pageName);
        if (!"home_clear".equals(finalState)) {
            logger.log("visit_" + pageName + " stopped before open, home not clear state=" + finalState);
            actionSuccess = false;
            return;
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        tap(input, x, y, label + "_visit_only");
        Thread.sleep(2200);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_OPEN_FILE);
        waitForPageAfterEntry(capture, ProbeConfig.TASK_AFTER_OPEN_FILE, label);
        copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_FRAME_FILE);
        copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);

        boolean stillHome = isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE);
        boolean popupVisible = isHomePopupVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)
                || isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_OPEN_FILE);
        finalState = stillHome
                ? "visit_" + pageName + "_still_home"
                : popupVisible
                ? "visit_" + pageName + "_popup_visible"
                : "visit_" + pageName + "_opened";
        logger.log("visit_" + pageName + " safeMode=no_claim_no_purchase"
                + " stillHome=" + stillHome + " popupVisible=" + popupVisible
                + " finalState=" + finalState);
        actionSuccess = !("visit_" + pageName + "_still_home").equals(finalState);
    }

    private void runClaimDailyRewardsTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        if (!openHomeEntry(capture, input, "daily_rewards", ProbeConfig.DAILY_TASK_ICON_X,
                ProbeConfig.DAILY_TASK_ICON_Y, "daily_task_icon")) {
            return;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "claim_daily_rewards_still_home";
            actionSuccess = false;
            logger.log("claim_daily_rewards stopped, task page did not open");
            return;
        }

        tap(input, ProbeConfig.DAILY_REWARD_DAILY_TAB_X, ProbeConfig.DAILY_REWARD_DAILY_TAB_Y,
                "daily_rewards_daily_tab");
        Thread.sleep(700);
        tapDailyRewardClaimButton(capture, input, "daily_rewards_claim_all_daily", dryRun);

        tap(input, ProbeConfig.DAILY_REWARD_WEEKLY_TAB_X, ProbeConfig.DAILY_REWARD_WEEKLY_TAB_Y,
                "daily_rewards_weekly_tab");
        Thread.sleep(700);
        tapDailyRewardClaimButton(capture, input, "daily_rewards_claim_all_weekly", dryRun);

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = dryRun ? "daily_rewards_red_dot_previewed" : "daily_rewards_claim_attempted";
        actionSuccess = true;
        logger.log("claim_daily_rewards completed dryRun=" + dryRun
                + " with claim-all checks for daily and weekly tabs");
    }

    private void tapDailyRewardClaimButton(FrameCaptureBackend capture, InputInjector input, String label,
                                           boolean dryRun)
            throws Exception {
        if (dryRun) {
            logger.log("debug dry-run skip daily reward claim tap label=" + label);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            return;
        }
        tap(input, ProbeConfig.DAILY_REWARD_CLAIM_ALL_X, ProbeConfig.DAILY_REWARD_CLAIM_ALL_Y, label);
        Thread.sleep(1000);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        closeGenericRewardConfirmIfVisible(capture, input, label + "_confirm",
                ProbeConfig.DAILY_REWARD_CONFIRM_X, ProbeConfig.DAILY_REWARD_CONFIRM_Y);
    }

    private void runClaimFriendPointsTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        if (!openHomeEntry(capture, input, "friend_points", ProbeConfig.FRIEND_ICON_X,
                ProbeConfig.FRIEND_ICON_Y, "friend_icon")) {
            return;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "claim_friend_points_still_home";
            actionSuccess = false;
            logger.log("claim_friend_points stopped, friend page did not open");
            return;
        }

        if (dryRun) {
            logger.log("debug dry-run skip friend one-key claim tap; red dot/page is visible");
        } else {
            tap(input, ProbeConfig.FRIEND_ONE_KEY_CLAIM_X, ProbeConfig.FRIEND_ONE_KEY_CLAIM_Y,
                    "friend_points_one_key_claim");
            Thread.sleep(1000);
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!dryRun) {
            closeGenericRewardConfirmIfVisible(capture, input, "friend_points_confirm",
                    ProbeConfig.FRIEND_CONFIRM_X, ProbeConfig.FRIEND_CONFIRM_Y);
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = dryRun ? "friend_points_red_dot_previewed" : "friend_points_claim_attempted";
        actionSuccess = true;
        logger.log("claim_friend_points completed dryRun=" + dryRun + " one-key claim path");
    }

    private void runClaimOutpostDefenseTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        if (!openHomeEntry(capture, input, "outpost_defense", ProbeConfig.OUTPOST_ENTRY_X,
                ProbeConfig.OUTPOST_ENTRY_Y, "outpost_entry")) {
            return;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "claim_outpost_defense_still_home";
            actionSuccess = false;
            logger.log("claim_outpost_defense stopped, outpost page did not open");
            return;
        }

        tap(input, ProbeConfig.OUTPOST_CLEAN_SWEEP_X, ProbeConfig.OUTPOST_CLEAN_SWEEP_Y,
                "outpost_clean_sweep");
        Thread.sleep(1900);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (dryRun) {
            finalState = "outpost_clean_sweep_red_dot_previewed";
            actionSuccess = true;
            logger.log("debug dry-run stopped before outpost clean-sweep confirm");
            return;
        }
        boolean cleanConfirmVisible = isOutpostCleanConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        for (int attempt = 1; cleanConfirmVisible && attempt <= 3; attempt++) {
            waitForOutpostCleanConfirmReady(capture, attempt);
            tap(input, ProbeConfig.OUTPOST_CLEAN_CONFIRM_X, ProbeConfig.OUTPOST_CLEAN_CONFIRM_Y,
                    "outpost_clean_confirm_" + attempt);
            cleanConfirmVisible = waitForOutpostCleanConfirmDismissed(capture, attempt);
        }
        if (cleanConfirmVisible) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "outpost_clean_confirm_still_visible";
            actionSuccess = false;
            logger.log("claim_outpost_defense stopped, clean-sweep confirm dialog still visible");
            return;
        }
        handleOutpostCleanNoticeIfVisible(capture, input);
        logger.log("outpost clean confirm dialog closed or not visible after clean-sweep tap");
        tap(input, ProbeConfig.OUTPOST_CLEAN_REWARD_X, ProbeConfig.OUTPOST_CLEAN_REWARD_Y,
                "outpost_clean_reward_close");
        Thread.sleep(900);

        tap(input, ProbeConfig.OUTPOST_GET_REWARD_X, ProbeConfig.OUTPOST_GET_REWARD_Y,
                "outpost_get_reward");
        Thread.sleep(1200);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        closeGenericRewardConfirmIfVisible(capture, input, "outpost_reward_confirm",
                ProbeConfig.OUTPOST_REWARD_CONFIRM_X, ProbeConfig.OUTPOST_REWARD_CONFIRM_Y);

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = "outpost_reward_claim_attempted";
        actionSuccess = true;
        logger.log("claim_outpost_defense completed with clean-sweep and get-reward attempts");
    }

    private void handleOutpostCleanNoticeIfVisible(FrameCaptureBackend capture, InputInjector input)
            throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (!isOutpostCleanNoticeVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                return;
            }
            if (attempt == 1) {
                tap(input, ProbeConfig.OUTPOST_NOTICE_DONT_SHOW_X, ProbeConfig.OUTPOST_NOTICE_DONT_SHOW_Y,
                        "outpost_clean_notice_dont_show_today");
                Thread.sleep(350);
            }
            tap(input, ProbeConfig.OUTPOST_NOTICE_CONFIRM_X, ProbeConfig.OUTPOST_NOTICE_CONFIRM_Y,
                    "outpost_clean_notice_confirm_" + attempt);
            Thread.sleep(1200);
        }
    }

    private void waitForOutpostCleanConfirmReady(FrameCaptureBackend capture, int confirmAttempt) throws Exception {
        for (int waitAttempt = 1; waitAttempt <= 4; waitAttempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isOutpostCleanConfirmReady(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("outpost clean confirm ready confirmAttempt=" + confirmAttempt
                        + " waitAttempt=" + waitAttempt);
                return;
            }
            Thread.sleep(650);
        }
    }

    private boolean waitForOutpostCleanConfirmDismissed(FrameCaptureBackend capture, int confirmAttempt)
            throws Exception {
        boolean visible = true;
        for (int waitAttempt = 1; waitAttempt <= 7; waitAttempt++) {
            Thread.sleep(waitAttempt == 1 ? 1200 : 750);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isOutpostCleanNoticeVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("outpost clean confirm advanced to notice confirmAttempt=" + confirmAttempt
                        + " waitAttempt=" + waitAttempt);
                return false;
            }
            visible = isOutpostCleanConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
            if (!visible) {
                logger.log("outpost clean confirm dismissed confirmAttempt=" + confirmAttempt
                        + " waitAttempt=" + waitAttempt);
                return false;
            }
            logger.log("outpost clean confirm still visible confirmAttempt=" + confirmAttempt
                    + " waitAttempt=" + waitAttempt);
        }
        return visible;
    }

    private void runClaimFreeShopTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        if (!openHomeEntry(capture, input, "free_shop", ProbeConfig.SHOP_ENTRY_X,
                ProbeConfig.SHOP_ENTRY_Y, "shop_entry")) {
            return;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "claim_free_shop_still_home";
            actionSuccess = false;
            logger.log("claim_free_shop stopped, shop page did not open");
            return;
        }

        tap(input, ProbeConfig.SHOP_FREE_ITEM_X, ProbeConfig.SHOP_FREE_ITEM_Y,
                "free_shop_daily_discount_item");
        Thread.sleep(900);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);

        if (!isShopPurchaseDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "free_shop_no_safe_purchase_dialog";
            actionSuccess = true;
            logger.log("claim_free_shop stopped after first item tap; no safe purchase dialog visible");
            return;
        }

        if (dryRun) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "free_shop_purchase_dialog_previewed";
            actionSuccess = true;
            logger.log("debug dry-run stopped before free shop quantity/buy confirm");
            return;
        }

        tap(input, ProbeConfig.SHOP_QUANTITY_CONFIRM_X, ProbeConfig.SHOP_QUANTITY_CONFIRM_Y,
                "free_shop_quantity_confirm_candidate");
        Thread.sleep(900);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);

        if (isShopPurchaseDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.SHOP_BUY_CONFIRM_X, ProbeConfig.SHOP_BUY_CONFIRM_Y,
                    "free_shop_buy_confirm_candidate");
            Thread.sleep(1200);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        }

        closeGenericRewardConfirmIfVisible(capture, input, "free_shop_reward_confirm",
                ProbeConfig.SHOP_REWARD_CONFIRM_X, ProbeConfig.SHOP_REWARD_CONFIRM_Y);
        tap(input, ProbeConfig.SHOP_REWARD_CONFIRM_X, ProbeConfig.SHOP_REWARD_CONFIRM_Y,
                "free_shop_reward_close_candidate");
        Thread.sleep(900);

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = "free_shop_purchase_attempted";
        actionSuccess = true;
        logger.log("claim_free_shop completed with daily 100-percent discount item purchase attempt");
    }

    private void runClaimPassRewardsTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        if (!openHomeEntry(capture, input, "pass_rewards", ProbeConfig.PASS_ENTRY_X,
                ProbeConfig.PASS_ENTRY_Y, "pass_entry")) {
            return;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "claim_pass_rewards_still_home";
            actionSuccess = false;
            logger.log("claim_pass_rewards stopped, pass menu did not open");
            return;
        }

        tap(input, ProbeConfig.PASS_REWARD_MENU_X, ProbeConfig.PASS_REWARD_MENU_Y,
                "pass_rewards_menu_first_item");
        Thread.sleep(2200);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);

        tap(input, ProbeConfig.PASS_TASK_TAB_X, ProbeConfig.PASS_TASK_TAB_Y, "pass_task_tab");
        Thread.sleep(700);
        tapPassClaimButton(capture, input, "pass_claim_all_task", dryRun);

        tap(input, ProbeConfig.PASS_REWARD_TAB_X, ProbeConfig.PASS_REWARD_TAB_Y, "pass_reward_tab");
        Thread.sleep(700);
        tapPassClaimButton(capture, input, "pass_claim_all_reward", dryRun);

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = dryRun ? "pass_rewards_red_dot_previewed" : "pass_rewards_claim_attempted";
        actionSuccess = true;
        logger.log("claim_pass_rewards completed dryRun=" + dryRun + " with pass task/reward checks");
    }

    private void tapPassClaimButton(FrameCaptureBackend capture, InputInjector input, String label, boolean dryRun)
            throws Exception {
        if (dryRun) {
            logger.log("debug dry-run skip pass claim tap label=" + label);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            return;
        }
        tap(input, ProbeConfig.PASS_CLAIM_ALL_X, ProbeConfig.PASS_CLAIM_ALL_Y, label);
        Thread.sleep(1000);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        closeGenericRewardConfirmIfVisible(capture, input, label + "_confirm",
                ProbeConfig.PASS_REWARD_CONFIRM_X, ProbeConfig.PASS_REWARD_CONFIRM_Y);
    }

    private void closeGenericRewardConfirmIfVisible(FrameCaptureBackend capture, InputInjector input,
                                                    String label, int x, int y) throws Exception {
        if (isMailRewardConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                || isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                || isUpdateDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, x, y, label);
            Thread.sleep(1000);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        }
    }

    private boolean closeDebugPreviewDialogIfVisible(FrameCaptureBackend capture, InputInjector input,
                                                     String label) throws Exception {
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                && !isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                && !isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            logger.log("workflow debug dialog close skipped because lobby is clear label=" + label);
            return false;
        }
        if (isUpdateDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.UPDATE_CONFIRM_X, ProbeConfig.UPDATE_CONFIRM_Y, label + "_update_confirm");
        } else if (isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.DOWNLOAD_CONFIRM_X, ProbeConfig.DOWNLOAD_CONFIRM_Y, label + "_download_confirm");
        } else if (isMailRewardConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.MAIL_CONFIRM_X, ProbeConfig.MAIL_CONFIRM_Y, label + "_reward_close");
        } else if (isShopPurchaseDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.SHOP_REWARD_CONFIRM_X, ProbeConfig.SHOP_REWARD_CONFIRM_Y,
                    label + "_shop_purchase_close");
        } else if (isOutpostCleanConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.OUTPOST_NOTICE_CONFIRM_X, ProbeConfig.OUTPOST_NOTICE_CONFIRM_Y,
                    label + "_outpost_clean_close");
        } else if (isOutpostCleanNoticeVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.OUTPOST_NOTICE_CONFIRM_X, ProbeConfig.OUTPOST_NOTICE_CONFIRM_Y,
                    label + "_outpost_notice_confirm");
        } else {
            return false;
        }
        Thread.sleep(1000);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        logger.log("workflow closed debug preview dialog label=" + label);
        return true;
    }

    private void runLoginRewardsTask(FrameCaptureBackend capture, InputInjector input) throws Exception {
        ensureHomeForTaskStart(capture, input, "login_rewards");
        if (!"home_clear".equals(finalState)) {
            logger.log("login_rewards stopped before safe popup handling, home not clear state=" + finalState);
            actionSuccess = false;
            return;
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        closeHomePopupIfVisible(capture, input);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if ("home_clear".equals(finalState) || "home_popup_closed".equals(finalState)) {
            finalState = "adapter_login_rewards_safe_popups_handled";
            actionSuccess = true;
        } else {
            finalState = "manual_confirm_login_rewards:" + finalState;
            actionSuccess = true;
        }
        logger.log("login_rewards Android adapter safeMode=popup_only finalState=" + finalState);
    }

    private void runVisitNikkesSubpageTask(FrameCaptureBackend capture, InputInjector input, String pageName,
                                           int subpageX, int subpageY, String subpageLabel) throws Exception {
        if (!openHomeEntry(capture, input, pageName + "_nikkes", ProbeConfig.NIKKES_ENTRY_X,
                ProbeConfig.NIKKES_ENTRY_Y, "nikkes_entry")) {
            return;
        }
        tap(input, subpageX, subpageY, subpageLabel + "_visit_only");
        finishAdapterPageVisit(capture, "visit_" + pageName, "manual_confirm_" + pageName);
    }

    private void runClaimInquiryAndGiftTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        int giftCount = taskOptionInt(0, 3, 0, 3);
        if (!openHomeEntry(capture, input, "inquiry_and_gift_nikkes", ProbeConfig.NIKKES_ENTRY_X,
                ProbeConfig.NIKKES_ENTRY_Y, "nikkes_entry")) {
            return;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "claim_inquiry_and_gift_still_home";
            actionSuccess = false;
            logger.log("claim_inquiry_and_gift stopped, nikkes page did not open");
            return;
        }

        Thread.sleep(2600);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        tap(input, ProbeConfig.NIKKES_INQUIRY_TAB_X, ProbeConfig.NIKKES_INQUIRY_TAB_Y,
                "inquiry_tab");
        Thread.sleep(2200);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            closeHomePopupIfVisible(capture, input);
            tap(input, ProbeConfig.NIKKES_INQUIRY_TAB_X, ProbeConfig.NIKKES_INQUIRY_TAB_Y,
                    "inquiry_tab_retry");
            Thread.sleep(2200);
        }
        tap(input, ProbeConfig.INQUIRY_BATCH_BUTTON_X, ProbeConfig.INQUIRY_BATCH_BUTTON_Y,
                "inquiry_batch_button");
        Thread.sleep(1000);
        if (dryRun) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            logger.log("debug dry-run previewed inquiry batch button; skip confirm");
            if (giftCount > 0) {
                tap(input, ProbeConfig.INQUIRY_CLOSE_X, ProbeConfig.INQUIRY_CLOSE_Y,
                        "inquiry_batch_preview_close");
                Thread.sleep(900);
            }
        } else {
            tap(input, ProbeConfig.INQUIRY_BATCH_CONFIRM_X, ProbeConfig.INQUIRY_BATCH_CONFIRM_Y,
                    "inquiry_batch_confirm");
            Thread.sleep(1800);
            tap(input, ProbeConfig.INQUIRY_NEXT_STEP_X, ProbeConfig.INQUIRY_NEXT_STEP_Y,
                    "inquiry_next_step_or_reward");
            Thread.sleep(900);
            tap(input, ProbeConfig.INQUIRY_CLOSE_X, ProbeConfig.INQUIRY_CLOSE_Y,
                    "inquiry_close_candidate");
            Thread.sleep(700);
        }

        for (int index = 0; index < giftCount; index++) {
            runGiftForTopNikke(capture, input, index, dryRun);
            if (dryRun) {
                break;
            }
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = dryRun ? "inquiry_and_gift_gift_previewed" : "inquiry_and_gift_claim_and_gift_attempted";
        actionSuccess = true;
        logger.log("claim_inquiry_and_gift completed dryRun=" + dryRun
                + " giftCount=" + giftCount);
    }

    private void runGiftForTopNikke(FrameCaptureBackend capture, InputInjector input, int index, boolean dryRun)
            throws Exception {
        int[][] targets = new int[][]{
                {ProbeConfig.INQUIRY_TOP_NIKKE_1_X, ProbeConfig.INQUIRY_TOP_NIKKE_1_Y},
                {ProbeConfig.INQUIRY_TOP_NIKKE_2_X, ProbeConfig.INQUIRY_TOP_NIKKE_2_Y},
                {ProbeConfig.INQUIRY_TOP_NIKKE_3_X, ProbeConfig.INQUIRY_TOP_NIKKE_3_Y}
        };
        int safeIndex = Math.max(0, Math.min(index, targets.length - 1));
        tap(input, targets[safeIndex][0], targets[safeIndex][1],
                "gift_top_nikke_" + (safeIndex + 1));
        Thread.sleep(1500);
        tap(input, ProbeConfig.INQUIRY_GIFT_BUTTON_X, ProbeConfig.INQUIRY_GIFT_BUTTON_Y,
                "gift_button_" + (safeIndex + 1));
        Thread.sleep(1300);
        tap(input, ProbeConfig.INQUIRY_BASIC_GIFT_X, ProbeConfig.INQUIRY_BASIC_GIFT_Y,
                "gift_basic_item_" + (safeIndex + 1));
        Thread.sleep(800);
        tap(input, ProbeConfig.INQUIRY_SEND_GIFT_X, ProbeConfig.INQUIRY_SEND_GIFT_Y,
                "gift_send_button_" + (safeIndex + 1));
        Thread.sleep(1000);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (dryRun) {
            logger.log("debug dry-run stopped before gift confirm index=" + safeIndex);
            return;
        }
        tap(input, ProbeConfig.INQUIRY_SEND_GIFT_CONFIRM_X, ProbeConfig.INQUIRY_SEND_GIFT_CONFIRM_Y,
                "gift_send_confirm_" + (safeIndex + 1));
        Thread.sleep(1600);
        tap(input, ProbeConfig.INQUIRY_GIFT_BACK_X, ProbeConfig.INQUIRY_GIFT_BACK_Y,
                "gift_back_to_inquiry_" + (safeIndex + 1));
        Thread.sleep(1200);
    }

    private void runVisitArkSubpageTask(FrameCaptureBackend capture, InputInjector input, String pageName,
                                       int subpageX, int subpageY, String subpageLabel) throws Exception {
        if (!openArkSubpage(capture, input, pageName, subpageX, subpageY, subpageLabel)) {
            return;
        }
        finishAdapterPageVisit(capture, "visit_" + pageName, "manual_confirm_" + pageName);
    }

    private boolean openArkSubpage(FrameCaptureBackend capture, InputInjector input, String pageName,
                                   int subpageX, int subpageY, String subpageLabel) throws Exception {
        if (!openHomeEntry(capture, input, pageName + "_ark", ProbeConfig.ARK_ENTRY_X,
                ProbeConfig.ARK_ENTRY_Y, "ark_entry")) {
            return false;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "visit_" + pageName + "_ark_still_home";
            actionSuccess = false;
            logger.log("visit_" + pageName + "_ark stopped, ark page did not open");
            return false;
        }
        if (!waitForArkHubBeforeSubpageTap(capture, pageName)) {
            finalState = "visit_" + pageName + "_ark_hub_not_ready";
            actionSuccess = false;
            return false;
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            tap(input, subpageX, subpageY, subpageLabel + "_entry_attempt_" + attempt);
            if (waitForArkSubpageAfterTap(capture, pageName, attempt)) {
                return true;
            }
            logger.log("ark subpage still on hub page=" + pageName + " attempt=" + attempt);
        }
        finalState = "visit_" + pageName + "_ark_subpage_not_opened";
        actionSuccess = false;
        return false;
    }

    private boolean waitForArkHubBeforeSubpageTap(FrameCaptureBackend capture, String pageName) throws Exception {
        for (int attempt = 1; attempt <= 6; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_OPEN_FILE);
            copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isArkHubVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
                logger.log("ark hub ready page=" + pageName + " attempt=" + attempt);
                return true;
            }
            if (isMostlyWhiteOrBlack(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
                logger.log("ark hub loading page=" + pageName + " attempt=" + attempt);
            } else {
                logger.log("ark hub not ready page=" + pageName + " attempt=" + attempt);
            }
            Thread.sleep(850);
        }
        return false;
    }

    private boolean waitForArkSubpageAfterTap(FrameCaptureBackend capture, String pageName, int tapAttempt)
            throws Exception {
        for (int waitAttempt = 1; waitAttempt <= 9; waitAttempt++) {
            Thread.sleep(waitAttempt == 1 ? 1500 : 850);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_OPEN_FILE);
            copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isMostlyWhiteOrBlack(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
                logger.log("ark subpage loading page=" + pageName
                        + " tapAttempt=" + tapAttempt + " waitAttempt=" + waitAttempt);
                continue;
            }
            if (isExpectedArkSubpageVisible(pageName, ProbeConfig.TASK_AFTER_OPEN_FILE)) {
                logger.log("ark target subpage visible page=" + pageName
                        + " tapAttempt=" + tapAttempt + " waitAttempt=" + waitAttempt);
                return true;
            }
            if (!isArkHubVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
                logger.log("ark subpage tap completed page=" + pageName
                        + " tapAttempt=" + tapAttempt + " waitAttempt=" + waitAttempt);
                return true;
            }
            if (waitAttempt >= 4) {
                return false;
            }
            logger.log("ark hub still visible page=" + pageName
                    + " tapAttempt=" + tapAttempt + " waitAttempt=" + waitAttempt);
        }
        return false;
    }

    private boolean isExpectedArkSubpageVisible(String pageName, File frameFile) {
        if ("sim_room".equals(pageName)) {
            return isSimRoomPageVisible(frameFile);
        }
        if ("climb_tower".equals(pageName)) {
            return isClimbTowerPageVisible(frameFile);
        }
        return false;
    }

    private void runClaimSimRoomTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        if (!openArkSubpage(capture, input, "sim_room", ProbeConfig.SIM_ROOM_ENTRY_X,
                ProbeConfig.SIM_ROOM_ENTRY_Y, "sim_room_entry")) {
            return;
        }
        if (dryRun) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "sim_room_red_dot_previewed";
            actionSuccess = true;
            logger.log("debug dry-run stopped on sim room page before quick actions");
            return;
        }
        tap(input, ProbeConfig.SIM_ROOM_START_X, ProbeConfig.SIM_ROOM_START_Y,
                "sim_room_start");
        Thread.sleep(1800);
        tap(input, ProbeConfig.SIM_ROOM_QUICK_X, ProbeConfig.SIM_ROOM_QUICK_Y,
                "sim_room_quick");
        Thread.sleep(1800);
        tap(input, ProbeConfig.SIM_ROOM_SKIP_BUFF_X, ProbeConfig.SIM_ROOM_SKIP_BUFF_Y,
                "sim_room_skip_buff_or_confirm");
        Thread.sleep(1200);
        tap(input, ProbeConfig.SIM_ROOM_END_X, ProbeConfig.SIM_ROOM_END_Y,
                "sim_room_end_candidate");
        Thread.sleep(900);

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = "sim_room_quick_attempted";
        actionSuccess = true;
        logger.log("claim_sim_room completed with start/quick/skip attempts");
    }

    private void runClaimClimbTowerTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        boolean unlimitedEnabled = taskOptionYes(0, false);
        int unlimitedTimes = taskOptionInt(1, 1, 1, 10);
        boolean companyEnabled = taskOptionYes(2, true);
        boolean companyFull = taskOptionYes(3, false);
        boolean[] companyTowers = new boolean[]{
                taskOptionYes(4, true),
                taskOptionYes(5, true),
                taskOptionYes(6, true),
                taskOptionYes(7, true)
        };
        if (!openArkSubpage(capture, input, "climb_tower", ProbeConfig.CLIMB_TOWER_ENTRY_X,
                ProbeConfig.CLIMB_TOWER_ENTRY_Y, "climb_tower_entry")) {
            return;
        }
        int attempted = 0;
        boolean battleFailedRetry = false;
        if (unlimitedEnabled) {
            if (!prepareClimbTowerChoicePage(capture, input, "unlimited")) {
                finalState = "climb_tower_choice_not_ready";
                actionSuccess = false;
                logger.log("claim_climb_tower stopped, tower choice page not ready for unlimited");
                return;
            }
            int count = dryRun ? 1 : unlimitedTimes;
            for (int i = 0; i < count; i++) {
                if (runOneClimbTowerEntry(capture, input, ProbeConfig.CLIMB_TOWER_UNLIMITED_X,
                        ProbeConfig.CLIMB_TOWER_UNLIMITED_Y,
                        "climb_tower_unlimited_" + (i + 1), dryRun)) {
                    attempted++;
                    if (finalState.indexOf("battle_failed_retry") >= 0) {
                        battleFailedRetry = true;
                        break;
                    }
                    if (dryRun) {
                        break;
                    }
                }
            }
        }
        if (!battleFailedRetry && companyEnabled && (!dryRun || attempted == 0)) {
            if (!prepareClimbTowerChoicePage(capture, input, "company")) {
                finalState = "climb_tower_choice_not_ready";
                actionSuccess = false;
                logger.log("claim_climb_tower stopped, tower choice page not ready for company");
                return;
            }
            int[][] companyTargets = new int[][]{
                    {ProbeConfig.CLIMB_TOWER_COMPANY_1_X, ProbeConfig.CLIMB_TOWER_COMPANY_1_Y},
                    {ProbeConfig.CLIMB_TOWER_COMPANY_2_X, ProbeConfig.CLIMB_TOWER_COMPANY_2_Y},
                    {ProbeConfig.CLIMB_TOWER_COMPANY_3_X, ProbeConfig.CLIMB_TOWER_COMPANY_3_Y},
                    {ProbeConfig.CLIMB_TOWER_COMPANY_4_X, ProbeConfig.CLIMB_TOWER_COMPANY_4_Y}
            };
            for (int i = 0; i < companyTargets.length; i++) {
                if (!companyTowers[i]) {
                    continue;
                }
                if (runOneClimbTowerEntry(capture, input, companyTargets[i][0], companyTargets[i][1],
                        "climb_tower_company_" + (i + 1), dryRun)) {
                    attempted++;
                    if (finalState.indexOf("battle_failed_retry") >= 0) {
                        battleFailedRetry = true;
                        break;
                    }
                    if (dryRun || !companyFull) {
                        break;
                    }
                    if (!prepareClimbTowerChoicePage(capture, input, "company_next")) {
                        logger.log("claim_climb_tower company follow-up choice page not ready");
                        break;
                    }
                }
            }
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!battleFailedRetry) {
            finalState = attempted > 0
                    ? (dryRun ? "climb_tower_battle_previewed" : "climb_tower_attempted")
                    : "climb_tower_no_enabled_target";
        }
        actionSuccess = attempted > 0;
        logger.log("claim_climb_tower completed dryRun=" + dryRun
                + " unlimitedEnabled=" + unlimitedEnabled
                + " companyEnabled=" + companyEnabled
                + " companyFull=" + companyFull
                + " attempted=" + attempted
                + " battleFailedRetry=" + battleFailedRetry);
    }

    private boolean runOneClimbTowerEntry(FrameCaptureBackend capture, InputInjector input, int x, int y,
                                          String label, boolean dryRun) throws Exception {
        tap(input, x, y, label + "_entry");
        Thread.sleep(1700);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (dryRun) {
            logger.log("debug dry-run stopped after opening tower target label=" + label);
            return true;
        }
        tap(input, ProbeConfig.CLIMB_TOWER_ENTER_FIGHT_X, ProbeConfig.CLIMB_TOWER_ENTER_FIGHT_Y,
                label + "_enter_fight_candidate");
        if (waitAndHandleClimbTowerBattle(capture, input, label)) {
            return true;
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!isClimbTowerBattleFailedVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.CLIMB_TOWER_BACK_TO_LIST_X, ProbeConfig.CLIMB_TOWER_BACK_TO_LIST_Y,
                    label + "_back_to_list_candidate");
            Thread.sleep(900);
        }
        return true;
    }

    private boolean prepareClimbTowerChoicePage(FrameCaptureBackend capture, InputInjector input, String target)
            throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isClimbTowerChoiceVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("climb tower choice page ready target=" + target + " attempt=" + attempt);
                return true;
            }
            if (isClimbTowerDetailVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isClimbTowerPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.CLIMB_TOWER_SELECTOR_X, ProbeConfig.CLIMB_TOWER_SELECTOR_Y,
                        "climb_tower_selector_" + target + "_" + attempt);
            } else if (isArkHubVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.CLIMB_TOWER_ENTRY_X, ProbeConfig.CLIMB_TOWER_ENTRY_Y,
                        "climb_tower_reopen_from_ark_" + target + "_" + attempt);
            } else {
                tap(input, ProbeConfig.CLIMB_TOWER_SELECTOR_X, ProbeConfig.CLIMB_TOWER_SELECTOR_Y,
                        "climb_tower_selector_guess_" + target + "_" + attempt);
            }
            Thread.sleep(attempt == 1 ? 1200 : 900);
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        boolean ready = isClimbTowerChoiceVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        logger.log("climb tower choice final target=" + target + " ready=" + ready);
        return ready;
    }

    private boolean waitAndHandleClimbTowerBattle(FrameCaptureBackend capture, InputInjector input, String label)
            throws Exception {
        for (int second = 1; second <= ProbeConfig.CLIMB_TOWER_FIGHT_WAIT_SECONDS; second++) {
            Thread.sleep(1000);
            if (second % 3 != 0 && second < 8) {
                continue;
            }
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isClimbTowerBattleFailedVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("climb tower battle failed, retry target label=" + label + " second=" + second);
                tap(input, ProbeConfig.CLIMB_TOWER_RETRY_X, ProbeConfig.CLIMB_TOWER_RETRY_Y,
                        label + "_battle_failed_retry");
                Thread.sleep(1600);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
                copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
                finalState = label + "_battle_failed_retry";
                actionSuccess = true;
                writeResult(capture, "climb_tower_battle_failed_retry");
                return true;
            }
            if (second % 10 == 0) {
                writeResult(capture, "climb_tower_battle_wait_" + second + "s");
            }
        }
        logger.log("climb tower battle wait finished without failed dialog label=" + label);
        return false;
    }

    private void runVisitEventSubpageTask(FrameCaptureBackend capture, InputInjector input, String pageName,
                                         int subpageX, int subpageY, String subpageLabel) throws Exception {
        if (!openHomeEntry(capture, input, pageName + "_event", ProbeConfig.DAILY_TASK_ICON_X,
                ProbeConfig.DAILY_TASK_ICON_Y, "event_or_task_entry")) {
            return;
        }
        tap(input, subpageX, subpageY, subpageLabel + "_visit_only");
        finishAdapterPageVisit(capture, "visit_" + pageName, "manual_confirm_" + pageName);
    }

    private void runVisitUnionRaidTask(FrameCaptureBackend capture, InputInjector input) throws Exception {
        if (!openHomeEntry(capture, input, "union", ProbeConfig.UNION_ENTRY_X,
                ProbeConfig.UNION_ENTRY_Y, "union_entry")) {
            return;
        }
        tap(input, ProbeConfig.UNION_RAID_ENTRY_X, ProbeConfig.UNION_RAID_ENTRY_Y,
                "union_raid_entry_readonly");
        finishAdapterPageVisit(capture, "visit_union_raid", "readonly_union_raid");
    }

    private boolean openHomeEntry(FrameCaptureBackend capture, InputInjector input, String pageName,
                                   int x, int y, String label) throws Exception {
        ensureHomeForTaskStart(capture, input, pageName);
        if (!"home_clear".equals(finalState)) {
            logger.log("visit_" + pageName + " stopped before open, home not clear state=" + finalState);
            actionSuccess = false;
            return false;
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        for (int attempt = 1; attempt <= 3; attempt++) {
            tap(input, x, y, label + "_visit_only_attempt_" + attempt);
            Thread.sleep(attempt == 1 ? 1800 : 2400);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_OPEN_FILE);
            waitForPageAfterEntry(capture, ProbeConfig.TASK_AFTER_OPEN_FILE, label);
            if (!isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
                break;
            }
            logger.log("entry tap still on home label=" + label + " attempt=" + attempt);
            Thread.sleep(700);
        }
        copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_FRAME_FILE);
        copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);
        return true;
    }

    private void finishAdapterPageVisit(FrameCaptureBackend capture, String successPrefix,
                                        String manualPrefix) throws Exception {
        Thread.sleep(2200);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_OPEN_FILE);
        waitForPageAfterEntry(capture, ProbeConfig.TASK_AFTER_OPEN_FILE, successPrefix);
        copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_FRAME_FILE);
        copyFile(ProbeConfig.TASK_AFTER_OPEN_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);
        boolean stillHome = isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE);
        boolean popupVisible = isHomePopupVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)
                || isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_OPEN_FILE);
        finalState = stillHome
                ? successPrefix + "_still_home"
                : popupVisible
                ? manualPrefix + "_popup_visible"
                : manualPrefix + "_opened";
        logger.log(successPrefix + " Android adapter safeMode=visit_only"
                + " stillHome=" + stillHome + " popupVisible=" + popupVisible
                + " finalState=" + finalState);
        actionSuccess = !finalState.endsWith("_still_home");
    }

    private void waitForPageAfterEntry(FrameCaptureBackend capture, File frameFile, String label) throws Exception {
        for (int attempt = 1; attempt <= 6; attempt++) {
            if (!isMostlyWhiteOrBlack(frameFile)) {
                logger.log("entry page appears stable label=" + label + " attempt=" + attempt);
                return;
            }
            Thread.sleep(1000);
            capture.copyLatestFrameTo(frameFile);
            copyFile(frameFile, ProbeConfig.TASK_FRAME_FILE);
            logger.log("entry page still loading label=" + label + " attempt=" + attempt);
        }
    }

    private void runClaimMailTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun) throws Exception {
        ensureHomeForTaskStart(capture, input, "claim_mail");
        if (!"home_clear".equals(finalState)) {
            logger.log("claim_mail stopped before mail open, home not clear state=" + finalState);
            actionSuccess = false;
            return;
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        boolean pageOpened = openMailPage(capture, input, "mail_icon");

        if (!pageOpened && !isMailPageVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE)) {
            finalState = "mail_page_not_detected";
            logger.log("claim_mail mail page not detected after tapping mail icon");
            returnToHomeAfterMail(capture, input);
            actionSuccess = false;
            return;
        }

        boolean claimVisible = isMailClaimButtonVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE);
        if (!claimVisible) {
            finalState = "mail_no_claim_button";
            logger.log("claim_mail page visible but claim button not detected");
            returnToHomeAfterMail(capture, input);
            actionSuccess = true;
            return;
        }

        if (dryRun) {
            finalState = "mail_red_dot_previewed";
            logger.log("debug dry-run mail claim button visible; skip claim-all tap");
            returnToHomeAfterMail(capture, input);
            actionSuccess = true;
            if (!"home_clear".equals(finalState)) {
                finalState = "mail_red_dot_previewed";
            }
            return;
        }

        tap(input, ProbeConfig.MAIL_CLAIM_ALL_X, ProbeConfig.MAIL_CLAIM_ALL_Y, "mail_claim_all");
        Thread.sleep(1600);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_MAIL_CLAIM_FILE);
        copyFile(ProbeConfig.TASK_AFTER_MAIL_CLAIM_FILE, ProbeConfig.TASK_FRAME_FILE);

        if (isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_MAIL_CLAIM_FILE)
                || isUpdateDialogVisible(ProbeConfig.TASK_AFTER_MAIL_CLAIM_FILE)
                || isMailRewardConfirmVisible(ProbeConfig.TASK_AFTER_MAIL_CLAIM_FILE)) {
            tap(input, ProbeConfig.MAIL_CONFIRM_X, ProbeConfig.MAIL_CONFIRM_Y, "mail_confirm");
            Thread.sleep(1400);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_MAIL_CONFIRM_FILE);
            copyFile(ProbeConfig.TASK_AFTER_MAIL_CONFIRM_FILE, ProbeConfig.TASK_FRAME_FILE);
        } else {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_MAIL_CONFIRM_FILE);
            copyFile(ProbeConfig.TASK_AFTER_MAIL_CONFIRM_FILE, ProbeConfig.TASK_FRAME_FILE);
        }

        finalState = "mail_claim_attempted";
        returnToHomeAfterMail(capture, input);
        actionSuccess = true;
        if (!"home_clear".equals(finalState)) {
            finalState = "mail_claim_attempted";
        } else {
            finalState = "mail_claimed_home_clear";
        }
    }

    private boolean openMailPage(FrameCaptureBackend capture, InputInjector input, String label) throws Exception {
        int[][] candidates = new int[][]{
                {ProbeConfig.MAIL_ICON_X, ProbeConfig.MAIL_ICON_Y},
                {1226, 26},
                {1220, 28}
        };
        for (int attempt = 1; attempt <= candidates.length; attempt++) {
            tap(input, candidates[attempt - 1][0], candidates[attempt - 1][1],
                    label + "_attempt_" + attempt);
            Thread.sleep(attempt == 1 ? 2200 : 1800);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE);
            copyFile(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isMailPageVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE)) {
                logger.log("mail page opened label=" + label + " attempt=" + attempt);
                return true;
            }
            if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE)) {
                cancelExitGameConfirm(capture, input);
                continue;
            }
            if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE)
                    || isHomePopupVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE)) {
                copyFile(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE, ProbeConfig.TASK_AFTER_WAIT_FILE);
                closeHomePopupIfVisible(capture, input);
                continue;
            }
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE)) {
                logger.log("mail tap stayed on lobby label=" + label + " attempt=" + attempt);
                Thread.sleep(650);
                continue;
            }
            logger.log("mail page not detected and lobby not confirmed label=" + label + " attempt=" + attempt);
            Thread.sleep(650);
        }
        return false;
    }

    private void returnToHomeAfterMail(FrameCaptureBackend capture, InputInjector input) throws Exception {
        if ("home_clear".equals(finalState)) {
            finalState = "mail_returning";
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "home_clear";
                return;
            }
            if (isMailPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.MAIL_CLOSE_X, ProbeConfig.MAIL_CLOSE_Y,
                        "mail_close_" + (attempt + 1));
                Thread.sleep(1200);
                continue;
            }
            if (isMailRewardConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.MAIL_CONFIRM_X, ProbeConfig.MAIL_CONFIRM_Y,
                        "mail_reward_close_" + (attempt + 1));
                Thread.sleep(1200);
                continue;
            }
            tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                    "mail_lobby_anchor_" + (attempt + 1));
            Thread.sleep(1200);
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_BACK_FILE);
        copyFile(ProbeConfig.TASK_AFTER_BACK_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_BACK_FILE)) {
            finalState = "home_clear";
        } else if (!"mail_no_claim_button".equals(finalState)) {
            finalState = "mail_return_home_not_confirmed";
        }
    }

    private boolean waitForUsefulFrame(FrameCaptureBackend capture, int timeoutSeconds) throws Exception {
        for (int second = 1; second <= timeoutSeconds; second++) {
            Thread.sleep(1000);
            if (second % 5 == 0 || capture.getLastNonZeroSamples() > 2500) {
                writeResult(capture, "waiting_" + second + "s");
                logger.log("wait seconds=" + second
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples());
            }
            if (capture.getLastNonZeroSamples() > 2500) {
                return true;
            }
        }
        return false;
    }

    private void recoverBlackVirtualDisplayIfNeeded(FrameCaptureBackend capture, String normalizedTaskName)
            throws Exception {
        if (capture.getNonBlackFrameCount() > 0 && capture.getLastNonZeroSamples() > 2500) {
            return;
        }
        if (waitForAnyUsefulFrame(capture, 5)) {
            return;
        }
        writeResult(capture, "preview_force_restart_display");
        logger.log("virtual display is still black; force starting game on display=" + displayId);
        launcher.startOnDisplay(displayId);
        capture.awaitFirstFrame(5, TimeUnit.SECONDS);
        waitForAnyUsefulFrame(capture, "start_game".equals(normalizedTaskName) ? 18 : 12);
    }

    private boolean waitForAnyUsefulFrame(FrameCaptureBackend capture, int timeoutSeconds) throws Exception {
        for (int second = 1; second <= timeoutSeconds; second++) {
            Thread.sleep(1000);
            if (second % 5 == 0 || capture.getNonBlackFrameCount() > 0) {
                writeResult(capture, "waiting_" + second + "s");
                logger.log("wait any seconds=" + second
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples());
            }
            if (capture.getNonBlackFrameCount() > 0 && capture.getLastNonZeroSamples() > 1000) {
                return true;
            }
        }
        return false;
    }

    private void tap(InputInjector input, int x, int y, String label) throws Exception {
        logger.log("tap label=" + label + " x=" + x + " y=" + y + " displayId=" + displayId);
        boolean down = input.injectTouch(MotionEvent.ACTION_DOWN, x, y, displayId, true);
        Thread.sleep(120);
        boolean up = input.injectTouch(MotionEvent.ACTION_UP, x, y, displayId, false);
        actionCount++;
        logger.log("tap result label=" + label + " down=" + down + " up=" + up);
    }

    private void pressBack(InputInjector input, String label) throws Exception {
        logger.log("key label=" + label + " keyCode=BACK displayId=" + displayId);
        boolean down = input.injectKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, displayId, true);
        Thread.sleep(80);
        boolean up = input.injectKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, displayId, false);
        actionCount++;
        logger.log("key result label=" + label + " down=" + down + " up=" + up);
    }

    private void tryCloseAnnouncementByTap(FrameCaptureBackend capture, InputInjector input) throws Exception {
        int[][] candidates = new int[][]{
                {ProbeConfig.POPUP_CLOSE_X, ProbeConfig.POPUP_CLOSE_Y},
                {800, 158},
                {795, 160},
                {803, 154}
        };
        for (int index = 0; index < candidates.length; index++) {
            tap(input, candidates[index][0], candidates[index][1], "popup_close_candidate_" + (index + 1));
            Thread.sleep(750);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_CLOSE_FILE);
            if (!isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_CLOSE_FILE)) {
                logger.log("close candidate succeeded index=" + (index + 1));
                return;
            }
        }
    }

    private void closeAnnouncementDialog(FrameCaptureBackend capture, InputInjector input, String reason)
            throws Exception {
        if (shouldTapAnnouncementSevenDayCheckbox()) {
            tap(input, ProbeConfig.POPUP_CHECKBOX_X, ProbeConfig.POPUP_CHECKBOX_Y,
                    "popup_checkbox_7day_" + reason);
            markAnnouncementSevenDayChecked();
            Thread.sleep(650);
        } else {
            logger.log("announcement 7-day checkbox already handled recently; reason=" + reason);
        }
        tryCloseAnnouncementByTap(capture, input);
        boolean stillVisible = isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_CLOSE_FILE);
        logger.log("announcement dialog after close taps=" + stillVisible + " reason=" + reason);
        if (stillVisible) {
            tap(input, ProbeConfig.POPUP_CLOSE_X, ProbeConfig.POPUP_CLOSE_Y,
                    "popup_close_final_" + reason);
            Thread.sleep(900);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_BACK_FILE);
            stillVisible = isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_BACK_FILE);
            logger.log("announcement dialog after final close tap=" + stillVisible + " reason=" + reason);
            finalState = stillVisible ? "announcement_still_visible" : "announcement_closed_by_close_retry";
        } else {
            finalState = "announcement_closed_by_close";
        }
    }

    private boolean shouldTapAnnouncementSevenDayCheckbox() {
        if (!ProbeConfig.ANNOUNCEMENT_SEVEN_DAY_MARKER.exists()) {
            return true;
        }
        long ageMs = System.currentTimeMillis() - ProbeConfig.ANNOUNCEMENT_SEVEN_DAY_MARKER.lastModified();
        return ageMs > 6L * 24L * 60L * 60L * 1000L;
    }

    private void markAnnouncementSevenDayChecked() {
        FileWriter writer = null;
        try {
            writer = new FileWriter(ProbeConfig.ANNOUNCEMENT_SEVEN_DAY_MARKER, false);
            writer.write(String.valueOf(System.currentTimeMillis()));
            writer.write('\n');
        } catch (Throwable error) {
            logger.log("failed to write announcement marker: "
                    + error.getClass().getName() + ": " + error.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void waitForGameLoadOrEnter(FrameCaptureBackend capture, InputInjector input) throws Exception {
        int enterAttempts = 0;
        boolean downloadConfirmed = false;
        boolean updateConfirmed = false;
        for (int second = 1; second <= ProbeConfig.START_GAME_WAIT_SECONDS; second++) {
            Thread.sleep(1000);
            if (second % 10 == 0) {
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
                String foreground = getForegroundPackageOnTargetDisplay();
                if (foreground.length() > 0 && !isTargetGamePackage(foreground)) {
                    finalState = "client_update_external:" + foreground;
                    logger.log("target display left NIKKE, stop task foreground=" + foreground);
                    return;
                }
                if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    cancelExitGameConfirm(capture, input);
                } else if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    finalState = "home_clear";
                    return;
                } else if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                        || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    closeHomePopupIfVisible(capture, input);
                }
                if ("home_clear".equals(finalState) || isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    finalState = "home_clear";
                    return;
                }
                if (isNetworkRetryDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    finalState = "network_retry_required";
                    logger.log("network retry dialog detected, user must retry manually");
                    return;
                }
                if (isLoginPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    finalState = "login_required";
                    logger.log("login page detected, user must log in manually");
                    return;
                }
                if (!updateConfirmed && isUpdateDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    tap(input, ProbeConfig.UPDATE_CONFIRM_X, ProbeConfig.UPDATE_CONFIRM_Y,
                            "update_confirm_button");
                    Thread.sleep(2200);
                    capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_UPDATE_FILE);
                    copyFile(ProbeConfig.TASK_AFTER_UPDATE_FILE, ProbeConfig.TASK_FRAME_FILE);
                    updateConfirmed = true;
                    finalState = "update_confirmed";
                    String afterUpdateForeground = getForegroundPackageOnTargetDisplay();
                    if (afterUpdateForeground.length() > 0
                            && !isTargetGamePackage(afterUpdateForeground)) {
                        finalState = "client_update_external:" + afterUpdateForeground;
                        logger.log("update opened external foreground=" + afterUpdateForeground);
                        return;
                    }
                }
                if (!downloadConfirmed && isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    tap(input, ProbeConfig.DOWNLOAD_CONFIRM_X, ProbeConfig.DOWNLOAD_CONFIRM_Y,
                            "download_confirm_button");
                    Thread.sleep(1800);
                    capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE);
                    copyFile(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE, ProbeConfig.TASK_FRAME_FILE);
                    downloadConfirmed = true;
                    finalState = "download_confirmed";
                }
                writeResult(capture, "loading_wait_" + second + "s");
                logger.log("start_game wait seconds=" + second
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples()
                        + " finalState=" + finalState);
            }
            if (second == 30 || second == 55 || second == 80 || second == 105) {
                tap(input, ProbeConfig.ENTER_GAME_X, ProbeConfig.ENTER_GAME_Y,
                        "enter_game_candidate_" + (++enterAttempts));
                Thread.sleep(1600);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ENTER_FILE);
                copyFile(ProbeConfig.TASK_AFTER_ENTER_FILE, ProbeConfig.TASK_FRAME_FILE);
                if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                    logger.log("announcement dialog returned during enter wait");
                    closeAnnouncementDialog(capture, input, "during_enter");
                } else if (isNetworkRetryDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                    finalState = "network_retry_required";
                    logger.log("network retry dialog detected after enter, user must retry manually");
                    return;
                } else if (isLoginPageVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                    finalState = "login_required";
                    logger.log("login page detected after enter, user must log in manually");
                    return;
                } else if (!updateConfirmed && isUpdateDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                    tap(input, ProbeConfig.UPDATE_CONFIRM_X, ProbeConfig.UPDATE_CONFIRM_Y,
                            "update_confirm_after_enter");
                    Thread.sleep(2200);
                    capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_UPDATE_FILE);
                    copyFile(ProbeConfig.TASK_AFTER_UPDATE_FILE, ProbeConfig.TASK_FRAME_FILE);
                    updateConfirmed = true;
                    finalState = "update_confirmed";
                    String afterUpdateForeground = getForegroundPackageOnTargetDisplay();
                    if (afterUpdateForeground.length() > 0
                            && !isTargetGamePackage(afterUpdateForeground)) {
                        finalState = "client_update_external:" + afterUpdateForeground;
                        logger.log("update opened external foreground=" + afterUpdateForeground);
                        return;
                    }
                } else if (!downloadConfirmed && isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                    tap(input, ProbeConfig.DOWNLOAD_CONFIRM_X, ProbeConfig.DOWNLOAD_CONFIRM_Y,
                            "download_confirm_after_enter");
                    Thread.sleep(1800);
                    capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE);
                    copyFile(ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE, ProbeConfig.TASK_FRAME_FILE);
                    downloadConfirmed = true;
                    finalState = "download_confirmed";
                }
            }
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            cancelExitGameConfirm(capture, input);
        } else {
            closeHomePopupIfVisible(capture, input);
        }
        if (!"announcement_still_visible".equals(finalState)
                && !"home_clear".equals(finalState)
                && !"home_popup_still_visible".equals(finalState)
                && !"login_required".equals(finalState)
                && !"network_retry_required".equals(finalState)
                && !finalState.startsWith("client_update_external")) {
            finalState = downloadConfirmed
                    ? "start_game_waited_after_download_confirm"
                    : updateConfirmed
                    ? "start_game_waited_after_update_confirm"
                    : "home_popup_closed".equals(finalState)
                    ? finalState
                    : enterAttempts > 0 ? "start_game_waited_with_enter_attempts" : finalState;
        }
    }

    private String getForegroundPackageOnTargetDisplay() {
        if (environment == null || displayId < 0) {
            return "";
        }
        try {
            String output = environment.runCommandForOutput(
                    "dumpsys activity activities | grep -A 40 'Display #" + displayId + "' | grep -m 1 'topResumedActivity\\|mResumedActivity\\|ResumedActivity' || true");
            String packageName = extractPackageName(output);
            logger.log("foreground displayId=" + displayId + " package=" + packageName);
            return packageName;
        } catch (Throwable error) {
            logger.log("foreground check failed: " + error.getClass().getName() + ": " + error.getMessage());
            return "";
        }
    }

    private String extractPackageName(String text) {
        if (text == null) {
            return "";
        }
        int index = text.indexOf(" u0 ");
        if (index < 0) {
            index = text.indexOf(" u");
        }
        if (index >= 0) {
            int start = text.indexOf(' ', index + 1);
            if (start >= 0) {
                while (start < text.length() && text.charAt(start) == ' ') {
                    start++;
                }
                int slash = text.indexOf('/', start);
                if (slash > start) {
                    return text.substring(start, slash).trim();
                }
            }
        }
        String targetPackage = launcher == null ? ProbeConfig.DEFAULT_TARGET_PACKAGE : launcher.getPackageName();
        int nikke = text.indexOf(targetPackage);
        if (nikke >= 0) {
            return targetPackage;
        }
        return text.trim();
    }

    private boolean isTargetGamePackage(String packageName) {
        if (launcher != null) {
            return launcher.isNikkePackage(packageName);
        }
        if (packageName == null) {
            return false;
        }
        for (int i = 0; i < ProbeConfig.TARGET_PACKAGE_CANDIDATES.length; i++) {
            if (packageName.equals(ProbeConfig.TARGET_PACKAGE_CANDIDATES[i])) {
                return true;
            }
        }
        return packageName.toLowerCase().contains("nikke");
    }

    private void closeHomePopupIfVisible(FrameCaptureBackend capture, InputInjector input) throws Exception {
        File currentFrame = ProbeConfig.TASK_AFTER_WAIT_FILE;
        if (isExitGameConfirmVisible(currentFrame)) {
            cancelExitGameConfirm(capture, input);
            return;
        }

        boolean noticeVisible = isHomeNoticeListVisible(currentFrame);
        if (!noticeVisible && isHomeClearVisible(currentFrame)) {
            finalState = "home_clear";
            logger.log("home popup close skipped because home is already clear");
            return;
        }

        if (noticeVisible) {
            logger.log("home notice list detected, trying close button");
            tap(input, ProbeConfig.HOME_NOTICE_LIST_CLOSE_X, ProbeConfig.HOME_NOTICE_LIST_CLOSE_Y,
                    "home_notice_list_close");
            Thread.sleep(1200);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE);
            copyFile(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE, ProbeConfig.TASK_FRAME_FILE);
            currentFrame = ProbeConfig.TASK_AFTER_HOME_POPUP_FILE;
            if (isExitGameConfirmVisible(currentFrame)) {
                cancelExitGameConfirm(capture, input);
                return;
            }
            if (isHomeClearVisible(currentFrame)
                    || (!isHomeNoticeListVisible(currentFrame) && !isHomePopupVisible(currentFrame))) {
                markHomeClearOrPopupClosed(currentFrame);
                return;
            }
        }

        if (!isHomePopupVisible(currentFrame)) {
            logger.log("home popup not detected at final wait frame");
            if (isHomeClearVisible(currentFrame)) {
                finalState = "home_clear";
            }
            return;
        }

        logger.log("home popup detected, trying close actions");
        int[][] candidates = new int[][]{
                {ProbeConfig.HOME_POPUP_TOP_RIGHT_X, ProbeConfig.HOME_POPUP_TOP_RIGHT_Y},
                {ProbeConfig.HOME_POPUP_OUTSIDE_LEFT_X, ProbeConfig.HOME_POPUP_OUTSIDE_LEFT_Y},
                {ProbeConfig.HOME_POPUP_OUTSIDE_RIGHT_X, ProbeConfig.HOME_POPUP_OUTSIDE_RIGHT_Y},
                {ProbeConfig.HOME_POPUP_BOTTOM_X, ProbeConfig.HOME_POPUP_BOTTOM_Y},
                {ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y}
        };
        for (int index = 0; index < candidates.length; index++) {
            tap(input, candidates[index][0], candidates[index][1], "home_popup_close_candidate_" + (index + 1));
            Thread.sleep(1000);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE);
            copyFile(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE)) {
                tap(input, ProbeConfig.HOME_NOTICE_LIST_CLOSE_X, ProbeConfig.HOME_NOTICE_LIST_CLOSE_Y,
                        "home_notice_list_close_after_popup_" + (index + 1));
                Thread.sleep(1000);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE);
                copyFile(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE, ProbeConfig.TASK_FRAME_FILE);
            }
            if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE)) {
                cancelExitGameConfirm(capture, input);
                return;
            }
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE)
                    || (!isHomePopupVisible(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE)
                    && !isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE))) {
                markHomeClearOrPopupClosed(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE);
                return;
            }
        }
        finalState = "home_popup_still_visible";
    }

    private void cancelExitGameConfirm(FrameCaptureBackend capture, InputInjector input) throws Exception {
        logger.log("exit game confirm detected, tapping cancel");
        tap(input, ProbeConfig.EXIT_CONFIRM_CANCEL_X, ProbeConfig.EXIT_CONFIRM_CANCEL_Y, "exit_confirm_cancel");
        Thread.sleep(900);
        tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y, "exit_confirm_cancel_lobby_anchor");
        Thread.sleep(600);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE);
        copyFile(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_HOME_POPUP_FILE)) {
            finalState = "home_clear";
        } else {
            finalState = "exit_confirm_cancelled";
        }
    }

    private void markHomeClearOrPopupClosed(File frameFile) {
        if (isHomeClearVisible(frameFile)) {
            finalState = "home_clear";
        } else {
            finalState = "home_popup_closed";
        }
    }

    private boolean isAnnouncementDialogVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("dialog detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int headerHits = countBlueHeaderSamples(bitmap);
            int bodyHits = countWhiteBodySamples(bitmap);
            boolean visible = headerHits >= 18 && bodyHits >= 45;
            logger.log("dialog detect file=" + frameFile.getName()
                    + " headerHits=" + headerHits + " bodyHits=" + bodyHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isDownloadConfirmVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("download detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int headerHits = countDownloadHeaderSamples(bitmap);
            int bodyHits = countDownloadBodySamples(bitmap);
            int buttonHits = countDownloadButtonSamples(bitmap);
            boolean visible = headerHits >= 45 && bodyHits >= 45 && buttonHits >= 15;
            logger.log("download detect file=" + frameFile.getName()
                    + " headerHits=" + headerHits + " bodyHits=" + bodyHits
                    + " buttonHits=" + buttonHits + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isUpdateDialogVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("update detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int headerHits = countDownloadHeaderSamples(bitmap);
            int bodyHits = countDownloadBodySamples(bitmap);
            int buttonHits = countDownloadButtonSamples(bitmap);
            int darkOutsideHits = countHomePopupOutsideDarkSamples(bitmap);
            boolean visible = headerHits >= 35 && bodyHits >= 35 && buttonHits >= 12
                    && darkOutsideHits >= 40;
            logger.log("update detect file=" + frameFile.getName()
                    + " headerHits=" + headerHits
                    + " bodyHits=" + bodyHits
                    + " buttonHits=" + buttonHits
                    + " darkOutsideHits=" + darkOutsideHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isExitGameConfirmVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("exit confirm detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int headerHits = countExitConfirmHeaderSamples(bitmap);
            int bodyHits = countExitConfirmBodySamples(bitmap);
            int cancelHits = countExitConfirmCancelButtonSamples(bitmap);
            int confirmHits = countExitConfirmConfirmButtonSamples(bitmap);
            int darkOutsideHits = countHomePopupOutsideDarkSamples(bitmap);
            boolean visible = headerHits >= 90 && bodyHits >= 80
                    && cancelHits >= 18 && confirmHits >= 18 && darkOutsideHits >= 80;
            logger.log("exit confirm detect file=" + frameFile.getName()
                    + " headerHits=" + headerHits
                    + " bodyHits=" + bodyHits
                    + " cancelHits=" + cancelHits
                    + " confirmHits=" + confirmHits
                    + " darkOutsideHits=" + darkOutsideHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isHomePopupVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("home popup detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int centerWhiteHits = countHomePopupCenterWhiteSamples(bitmap);
            int outsideDarkHits = countHomePopupOutsideDarkSamples(bitmap);
            boolean visible = centerWhiteHits >= 160 && outsideDarkHits >= 60;
            logger.log("home popup detect file=" + frameFile.getName()
                    + " centerWhiteHits=" + centerWhiteHits
                    + " outsideDarkHits=" + outsideDarkHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isHomeNoticeListVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("home notice list detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int headerHits = countHomeNoticeListHeaderSamples(bitmap);
            int bodyHits = countHomeNoticeListBodySamples(bitmap);
            int outsideDarkHits = countHomePopupOutsideDarkSamples(bitmap);
            boolean visible = headerHits >= 120 && bodyHits >= 90 && outsideDarkHits >= 60;
            logger.log("home notice list detect file=" + frameFile.getName()
                    + " headerHits=" + headerHits
                    + " bodyHits=" + bodyHits
                    + " outsideDarkHits=" + outsideDarkHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isHomeClearVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("home clear detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int bottomHits = countHomeBottomNavigationSamples(bitmap);
            int rightHits = countHomeRightMenuSamples(bitmap);
            int featureHits = countHomeFeatureEntrySamples(bitmap);
            int blueHits = countHomeBluePanelSamples(bitmap);
            boolean visible = bottomHits >= 18 && rightHits >= 7 && featureHits >= 30 && blueHits >= 25;
            logger.log("home clear detect file=" + frameFile.getName()
                    + " bottomHits=" + bottomHits
                    + " rightHits=" + rightHits
                    + " featureHits=" + featureHits
                    + " blueHits=" + blueHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isArkHubVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("ark hub detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int simHits = countBrightPanelSamples(bitmap, 470, 295, 565, 455);
            int towerHits = countBrightPanelSamples(bitmap, 705, 160, 815, 330);
            int arenaHits = countBrightPanelSamples(bitmap, 680, 370, 790, 535);
            int visibleCards = 0;
            if (simHits >= 12) {
                visibleCards++;
            }
            if (towerHits >= 18) {
                visibleCards++;
            }
            if (arenaHits >= 18) {
                visibleCards++;
            }
            boolean visible = visibleCards >= 2 || (simHits >= 10 && towerHits >= 14 && arenaHits >= 14);
            logger.log("ark hub detect file=" + frameFile.getName()
                    + " simHits=" + simHits
                    + " towerHits=" + towerHits
                    + " arenaHits=" + arenaHits
                    + " visibleCards=" + visibleCards
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isSimRoomPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("sim room page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int startButtonHits = countBlueButtonSamples(bitmap, 570, 402, 710, 450);
            int bottomNavHits = countBlueButtonSamples(bitmap, 0, 655, 130, 715);
            boolean visible = startButtonHits >= 16 && bottomNavHits >= 8;
            logger.log("sim room page detect file=" + frameFile.getName()
                    + " startButtonHits=" + startButtonHits
                    + " bottomNavHits=" + bottomNavHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isClimbTowerPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("climb tower page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int rankingButtonHits = countBlueButtonSamples(bitmap, 1160, 650, 1270, 715);
            int rewardPanelHits = countBrightPanelSamples(bitmap, 10, 70, 195, 130);
            boolean visible = rankingButtonHits >= 8 && rewardPanelHits >= 12;
            logger.log("climb tower page detect file=" + frameFile.getName()
                    + " rankingButtonHits=" + rankingButtonHits
                    + " rewardPanelHits=" + rewardPanelHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isClimbTowerDetailVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("climb tower detail detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int rankingButtonHits = countBlueButtonSamples(bitmap, 1160, 650, 1270, 715);
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int leftRewardHits = countBrightPanelSamples(bitmap, 5, 65, 195, 130);
            int centerStageHits = countBrightPanelSamples(bitmap, 500, 240, 740, 380);
            boolean visible = rankingButtonHits >= 18 && bottomBackHits >= 18
                    && leftRewardHits >= 10 && centerStageHits >= 45;
            logger.log("climb tower detail detect file=" + frameFile.getName()
                    + " rankingButtonHits=" + rankingButtonHits
                    + " bottomBackHits=" + bottomBackHits
                    + " leftRewardHits=" + leftRewardHits
                    + " centerStageHits=" + centerStageHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isClimbTowerChoiceVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("climb tower choice detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int rankingButtonHits = countBlueButtonSamples(bitmap, 1160, 650, 1270, 715);
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int companyCardHits = countBrightPanelSamples(bitmap, 430, 390, 850, 610);
            int towerCardHits = countBrightPanelSamples(bitmap, 455, 310, 825, 450);
            int listTextHits = countBrightPanelSamples(bitmap, 455, 340, 826, 438);
            boolean detailLike = rankingButtonHits >= 18 && bottomBackHits >= 18;
            boolean visible = !detailLike
                    && (companyCardHits >= 45 || (towerCardHits >= 36 && listTextHits >= 20));
            logger.log("climb tower choice detect file=" + frameFile.getName()
                    + " rankingButtonHits=" + rankingButtonHits
                    + " bottomBackHits=" + bottomBackHits
                    + " companyCardHits=" + companyCardHits
                    + " towerCardHits=" + towerCardHits
                    + " listTextHits=" + listTextHits
                    + " detailLike=" + detailLike
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isClimbTowerBattleFailedVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("climb tower battle failed detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int retryBlueHits = countBlueButtonSamples(bitmap, 690, 620, 850, 690);
            int lowerButtonHits = countBlueButtonSamples(bitmap, 560, 610, 880, 700);
            int centerBrightHits = countBrightPanelSamples(bitmap, 420, 160, 860, 310);
            int darkOutsideHits = countHomePopupOutsideDarkSamples(bitmap);
            boolean visible = darkOutsideHits >= 80 && retryBlueHits >= 18
                    && lowerButtonHits >= 30 && centerBrightHits >= 10;
            logger.log("climb tower battle failed detect file=" + frameFile.getName()
                    + " retryBlueHits=" + retryBlueHits
                    + " lowerButtonHits=" + lowerButtonHits
                    + " centerBrightHits=" + centerBrightHits
                    + " darkOutsideHits=" + darkOutsideHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isLoginPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("login page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int titleHits = countLoginTitleSamples(bitmap);
            int wechatHits = countLoginWechatButtonSamples(bitmap);
            int qqHits = countLoginQqButtonSamples(bitmap);
            boolean visible = titleHits >= 10 && wechatHits >= 12 && qqHits >= 12;
            logger.log("login page detect file=" + frameFile.getName()
                    + " titleHits=" + titleHits
                    + " wechatHits=" + wechatHits
                    + " qqHits=" + qqHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isNetworkRetryDialogVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("network retry detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int headerHits = countNetworkRetryHeaderSamples(bitmap);
            int bodyHits = countNetworkRetryBodySamples(bitmap);
            int buttonHits = countNetworkRetryButtonSamples(bitmap);
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            boolean visible = headerHits >= 35 && bodyHits >= 50 && buttonHits >= 12 && darkHits >= 40;
            logger.log("network retry detect file=" + frameFile.getName()
                    + " headerHits=" + headerHits
                    + " bodyHits=" + bodyHits
                    + " buttonHits=" + buttonHits
                    + " darkHits=" + darkHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isMailPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("mail page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int headerHits = countMailHeaderSamples(bitmap);
            int listHits = countMailListSamples(bitmap);
            int bodyHits = countMailBodySamples(bitmap);
            boolean visible = headerHits >= 25 && bodyHits >= 80 && listHits >= 45;
            logger.log("mail page detect file=" + frameFile.getName()
                    + " headerHits=" + headerHits
                    + " bodyHits=" + bodyHits
                    + " listHits=" + listHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isMailClaimButtonVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("mail claim detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int blueHits = countMailClaimButtonSamples(bitmap);
            boolean visible = blueHits >= 20;
            logger.log("mail claim detect file=" + frameFile.getName()
                    + " blueHits=" + blueHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isMailRewardConfirmVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("mail reward confirm detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int titleHits = countMailRewardTitleSamples(bitmap);
            int itemHits = countMailRewardItemSamples(bitmap);
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int homeBottomHits = countHomeBottomNavigationSamples(bitmap);
            int homeRightHits = countHomeRightMenuSamples(bitmap);
            int homeFeatureHits = countHomeFeatureEntrySamples(bitmap);
            boolean homeVisible = homeBottomHits >= 18 && homeRightHits >= 7 && homeFeatureHits >= 30;
            boolean visible = titleHits >= 8 && itemHits >= 10 && darkHits >= 40;
            if (homeVisible) {
                visible = false;
            }
            logger.log("mail reward confirm detect file=" + frameFile.getName()
                    + " titleHits=" + titleHits
                    + " itemHits=" + itemHits
                    + " darkHits=" + darkHits
                    + " homeVisible=" + homeVisible
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isShopPurchaseDialogVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("shop purchase dialog detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int quantityBlueHits = countBlueButtonSamples(bitmap, 470, 370, 810, 420);
            int buyBlueHits = countBlueButtonSamples(bitmap, 645, 575, 760, 615);
            boolean visible = darkHits >= 35 && (quantityBlueHits >= 18 || buyBlueHits >= 8);
            logger.log("shop purchase dialog detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " quantityBlueHits=" + quantityBlueHits
                    + " buyBlueHits=" + buyBlueHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isOutpostCleanConfirmVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("outpost clean confirm detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int costButtonHits = countBlueButtonSamples(bitmap, 590, 535, 700, 572);
            int confirmButtonHits = countBlueButtonSamples(bitmap, 700, 535, 805, 572);
            int panelHits = countBrightPanelSamples(bitmap, 480, 280, 805, 520);
            int bodyTextHits = countBrightPanelSamples(bitmap, 540, 330, 760, 400);
            boolean visible = darkHits >= 80 && panelHits >= 35 && bodyTextHits >= 18;
            logger.log("outpost clean confirm detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " costButtonHits=" + costButtonHits
                    + " confirmButtonHits=" + confirmButtonHits
                    + " panelHits=" + panelHits
                    + " bodyTextHits=" + bodyTextHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isOutpostCleanConfirmReady(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("outpost clean ready detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int confirmButtonHits = countBlueButtonSamples(bitmap, 700, 535, 805, 572);
            boolean ready = confirmButtonHits >= 18;
            logger.log("outpost clean ready detect file=" + frameFile.getName()
                    + " confirmButtonHits=" + confirmButtonHits
                    + " ready=" + ready);
            return ready;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isOutpostCleanNoticeVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("outpost clean notice detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int headerHits = countBlueButtonSamples(bitmap, 460, 190, 820, 230);
            int whitePanelHits = countBrightPanelSamples(bitmap, 470, 232, 810, 410);
            int confirmButtonHits = countBlueButtonSamples(bitmap, 650, 432, 805, 470);
            boolean visible = darkHits >= 120 && headerHits >= 30
                    && whitePanelHits >= 45 && confirmButtonHits >= 20;
            logger.log("outpost clean notice detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " headerHits=" + headerHits
                    + " whitePanelHits=" + whitePanelHits
                    + " confirmButtonHits=" + confirmButtonHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isMostlyWhiteOrBlack(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("blank/loading detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int total = 0;
            int bright = 0;
            int dark = 0;
            for (int y = 40; y <= 680; y += 40) {
                for (int x = 40; x <= 1240; x += 40) {
                    int color = bitmap.getPixel(x, y);
                    int r = (color >> 16) & 0xff;
                    int g = (color >> 8) & 0xff;
                    int b = color & 0xff;
                    total++;
                    if (r > 230 && g > 230 && b > 230) {
                        bright++;
                    } else if (r < 30 && g < 30 && b < 30) {
                        dark++;
                    }
                }
            }
            boolean loading = total > 0 && (bright > total * 8 / 10 || dark > total * 8 / 10);
            logger.log("blank/loading detect file=" + frameFile.getName()
                    + " bright=" + bright + " dark=" + dark + " total=" + total
                    + " loading=" + loading);
            return loading;
        } finally {
            bitmap.recycle();
        }
    }

    private int countBrightPanelSamples(Bitmap bitmap, int left, int top, int right, int bottom) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, right);
        int maxY = Math.min(bitmap.getHeight() - 1, bottom);
        for (int y = Math.max(0, top); y <= maxY; y += 16) {
            for (int x = Math.max(0, left); x <= maxX; x += 16) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r + g + b > 210 && Math.max(r, Math.max(g, b)) > 80) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countBlueButtonSamples(Bitmap bitmap, int left, int top, int right, int bottom) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, right);
        int maxY = Math.min(bitmap.getHeight() - 1, bottom);
        for (int y = Math.max(0, top); y <= maxY; y += 8) {
            for (int x = Math.max(0, left); x <= maxX; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 110 && r < 90 && b > r + 70 && b > g + 10) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countBlueHeaderSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 140; y <= 178; y += 10) {
            for (int x = 465; x <= 815; x += 20) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 80 && r < 150 && b > r + 45 && b > g + 15) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countWhiteBodySamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 185; y <= 520; y += 25) {
            for (int x = 480; x <= 805; x += 25) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 170 && g > 170 && b > 170) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countDownloadHeaderSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 284; y <= 357; y += 10) {
            for (int x = 460; x <= 820; x += 20) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 80 && r < 150 && b > r + 45 && b > g + 15) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countDownloadBodySamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 323; y <= 438; y += 15) {
            for (int x = 475; x <= 806; x += 25) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 170 && g > 170 && b > 170) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countDownloadButtonSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 450; y <= 486; y += 8) {
            for (int x = 585; x <= 696; x += 15) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 120 && r < 100 && b > r + 60) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countExitConfirmHeaderSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 190; y <= 232; y += 6) {
            for (int x = 460; x <= 820; x += 12) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 95 && r < 120 && b > r + 55 && b > g + 5) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countExitConfirmBodySamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 238; y <= 410; y += 12) {
            for (int x = 470; x <= 810; x += 14) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 175 && g > 175 && b > 175) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countExitConfirmCancelButtonSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 436; y <= 466; y += 5) {
            for (int x = 482; x <= 630; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 210 && g > 210 && b > 210 && Math.abs(r - g) < 25 && Math.abs(g - b) < 25) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countExitConfirmConfirmButtonSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 436; y <= 466; y += 5) {
            for (int x = 650; x <= 800; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 110 && r < 110 && b > r + 55) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomePopupCenterWhiteSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 150; y <= 660; y += 20) {
            for (int x = 460; x <= 820; x += 20) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 185 && g > 185 && b > 185) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomePopupOutsideDarkSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 170; y <= 650; y += 30) {
            for (int x = 60; x <= 330; x += 30) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r < 60 && g < 60 && b < 60) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomeBottomNavigationSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 635; y <= 710; y += 10) {
            for (int x = 450; x <= 830; x += 10) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 145 && g > 145 && b > 145) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomeRightMenuSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 120; y <= 370; y += 10) {
            for (int x = 1210; x <= 1270; x += 10) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 150 && g > 150 && b > 150) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomeFeatureEntrySamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 410; y <= 590; y += 15) {
            for (int x = 250; x <= 980; x += 15) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 165 && g > 165 && b > 165) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomeBluePanelSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 420; y <= 620; y += 12) {
            for (int x = 250; x <= 500; x += 12) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 120 && g > 70 && r < 130 && b > r + 35) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countMailHeaderSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 72; y <= 126; y += 9) {
            for (int x = 470; x <= 815; x += 15) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 80 && r < 150 && b > r + 45 && b > g + 15) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countMailListSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 238; y <= 565; y += 20) {
            for (int x = 480; x <= 805; x += 20) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 125 && g > 125 && b > 125 && Math.abs(r - g) < 55 && Math.abs(g - b) < 55) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countMailBodySamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 130; y <= 635; y += 20) {
            for (int x = 460; x <= 820; x += 20) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 175 && g > 175 && b > 175) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countMailClaimButtonSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 592; y <= 628; y += 6) {
            for (int x = 668; x <= 800; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 140 && g > 95 && r < 140 && b > r + 35) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countMailRewardTitleSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 210; y <= 260; y += 5) {
            for (int x = 590; x <= 690; x += 5) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 185 && g > 185 && b > 185) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countMailRewardItemSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 292; y <= 415; y += 10) {
            for (int x = 480; x <= 800; x += 10) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 90 && g > 90 && b > 90 && (r > 150 || g > 150 || b > 150)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countLoginTitleSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 330; y <= 380; y += 5) {
            for (int x = 570; x <= 705; x += 5) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 190 && g > 190 && b > 190) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countLoginWechatButtonSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 548; y <= 578; y += 5) {
            for (int x = 510; x <= 620; x += 5) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (g > 150 && r < 120 && b < 140 && g > r + 45) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countLoginQqButtonSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 548; y <= 578; y += 5) {
            for (int x = 660; x <= 775; x += 5) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 160 && g > 120 && r < 120 && b > r + 60) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countNetworkRetryHeaderSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 250; y <= 285; y += 5) {
            for (int x = 465; x <= 815; x += 10) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 90 && r < 130 && b > r + 45) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countNetworkRetryBodySamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 285; y <= 420; y += 10) {
            for (int x = 470; x <= 810; x += 15) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 185 && g > 185 && b > 185) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countNetworkRetryButtonSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 450; y <= 488; y += 6) {
            for (int x = 565; x <= 715; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 110 && r < 130 && b > r + 45) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomeNoticeListHeaderSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 52; y <= 136; y += 12) {
            for (int x = 452; x <= 828; x += 12) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 150 && g > 80 && r < 150 && b > r + 45 && b > g + 15) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countHomeNoticeListBodySamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 136; y <= 646; y += 20) {
            for (int x = 452; x <= 828; x += 20) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 185 && g > 185 && b > 185) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private void resetOutputFiles() {
        ProbeConfig.TASK_LOG_FILE.delete();
        ProbeConfig.TASK_RESULT_FILE.delete();
        ProbeConfig.TASK_FRAME_FILE.delete();
        ProbeConfig.TASK_BEFORE_ACTION_FILE.delete();
        ProbeConfig.TASK_AFTER_ACTION_FILE.delete();
        ProbeConfig.TASK_AFTER_OPEN_FILE.delete();
        ProbeConfig.TASK_AFTER_CLOSE_FILE.delete();
        ProbeConfig.TASK_AFTER_BACK_FILE.delete();
        ProbeConfig.TASK_AFTER_WAIT_FILE.delete();
        ProbeConfig.TASK_AFTER_ENTER_FILE.delete();
        ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE.delete();
        ProbeConfig.TASK_AFTER_HOME_POPUP_FILE.delete();
        ProbeConfig.TASK_AFTER_UPDATE_FILE.delete();
        ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE.delete();
        ProbeConfig.TASK_AFTER_MAIL_CLAIM_FILE.delete();
        ProbeConfig.TASK_AFTER_MAIL_CONFIRM_FILE.delete();
        logger.reset();
    }

    private void writeResult(FrameCaptureBackend capture, String phase) {
        try {
            FileWriter writer = new FileWriter(ProbeConfig.TASK_RESULT_FILE, false);
            try {
                writer.write("phase=" + phase + "\n");
                writer.write("task=" + taskName + "\n");
                writer.write("displayId=" + displayId + "\n");
                writer.write("frames=" + capture.getFrameCount() + "\n");
                writer.write("nonBlackFrames=" + capture.getNonBlackFrameCount() + "\n");
                writer.write("lastNonZeroSamples=" + capture.getLastNonZeroSamples() + "\n");
                writer.write("actionCount=" + actionCount + "\n");
                writer.write("actionSuccess=" + actionSuccess + "\n");
                writer.write("finalState=" + finalState + "\n");
                writer.write("targetPackage=" + (launcher == null ? "" : launcher.getPackageName()) + "\n");
                writer.write("targetComponent=" + (launcher == null ? "" : launcher.getComponentName()) + "\n");
                writer.write("targetSource=" + (launcher == null ? "" : launcher.getTargetSource()) + "\n");
                writer.write("frameFile=" + ProbeConfig.TASK_FRAME_FILE.getAbsolutePath() + "\n");
                writer.write("beforeActionFile=" + ProbeConfig.TASK_BEFORE_ACTION_FILE.getAbsolutePath() + "\n");
                writer.write("afterActionFile=" + ProbeConfig.TASK_AFTER_ACTION_FILE.getAbsolutePath() + "\n");
                writer.write("afterOpenFile=" + ProbeConfig.TASK_AFTER_OPEN_FILE.getAbsolutePath() + "\n");
                writer.write("afterCloseFile=" + ProbeConfig.TASK_AFTER_CLOSE_FILE.getAbsolutePath() + "\n");
                writer.write("afterBackFile=" + ProbeConfig.TASK_AFTER_BACK_FILE.getAbsolutePath() + "\n");
                writer.write("afterWaitFile=" + ProbeConfig.TASK_AFTER_WAIT_FILE.getAbsolutePath() + "\n");
                writer.write("afterEnterFile=" + ProbeConfig.TASK_AFTER_ENTER_FILE.getAbsolutePath() + "\n");
                writer.write("afterDownloadConfirmFile=" + ProbeConfig.TASK_AFTER_DOWNLOAD_CONFIRM_FILE.getAbsolutePath() + "\n");
                writer.write("afterHomePopupFile=" + ProbeConfig.TASK_AFTER_HOME_POPUP_FILE.getAbsolutePath() + "\n");
                writer.write("afterUpdateFile=" + ProbeConfig.TASK_AFTER_UPDATE_FILE.getAbsolutePath() + "\n");
                writer.write("afterMailOpenFile=" + ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE.getAbsolutePath() + "\n");
                writer.write("afterMailClaimFile=" + ProbeConfig.TASK_AFTER_MAIL_CLAIM_FILE.getAbsolutePath() + "\n");
                writer.write("afterMailConfirmFile=" + ProbeConfig.TASK_AFTER_MAIL_CONFIRM_FILE.getAbsolutePath() + "\n");
                writer.write("logFile=" + ProbeConfig.TASK_LOG_FILE.getAbsolutePath() + "\n");
            } finally {
                writer.close();
            }
        } catch (Throwable error) {
            logger.log("writeResult failed: " + error.getMessage());
        }
    }

    private void copyFile(File source, File target) {
        if (!source.exists()) {
            logger.log("copy skipped, missing source=" + source.getAbsolutePath());
            return;
        }
        byte[] buffer = new byte[64 * 1024];
        try {
            java.io.FileInputStream input = new java.io.FileInputStream(source);
            try {
                java.io.FileOutputStream output = new java.io.FileOutputStream(target);
                try {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        output.write(buffer, 0, read);
                    }
                } finally {
                    output.close();
                }
            } finally {
                input.close();
            }
        } catch (Throwable error) {
            logger.log("copy failed: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }
}
