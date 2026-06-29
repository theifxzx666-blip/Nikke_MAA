package com.codex.maanikke.rootprobe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.VirtualDisplay;
import android.os.Process;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

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
        logTaskContractSnapshot("loaded");

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
            } else if ("maacore_probe".equals(normalizedTaskName)) {
                runMaaCoreProbeTask(capture);
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
            } else if ("claim_dispatch_board".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimDispatchBoardTask(capture, input, isDebugDryRunTask(normalizedTaskName));
            } else if ("claim_interception".equals(toExecutableTaskName(normalizedTaskName))) {
                runClaimInterceptionTask(capture, input, isDebugDryRunTask(normalizedTaskName));
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
            if (!"stop_game".equals(normalizedTaskName)
                    && !"maacore_probe".equals(normalizedTaskName)
                    && !workflowContainsStopGame()) {
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

    private void logTaskContractSnapshot(String stage) {
        logger.log("task contract stage=" + stage
                + " rootTask=" + normalizeTaskName(taskName)
                + " executable=" + toExecutableTaskName(normalizeTaskName(taskName))
                + " debugDryRun=" + isDebugDryRunTask(normalizeTaskName(taskName))
                + " workflowMode=" + workflowMode
                + " workflowSteps=" + joinStepsForLog(workflowSteps)
                + " optionCandidates=" + joinStepsForLog(optionTaskNameCandidates())
                + " activeOptions=" + buildRelevantOptionsSummary());
    }

    private String buildRelevantOptionsSummary() {
        String[] candidates = optionTaskNameCandidates();
        ArrayList<String> matches = new ArrayList<String>();
        for (Map.Entry<String, String> entry : taskOptions.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() == 0) {
                continue;
            }
            for (int i = 0; i < candidates.length; i++) {
                String candidate = candidates[i];
                if (candidate == null || candidate.length() == 0) {
                    continue;
                }
                String prefix = candidate + ".option.";
                if (key.startsWith(prefix)) {
                    matches.add(key + "=" + entry.getValue());
                    break;
                }
            }
        }
        if (matches.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                builder.append(';');
            }
            builder.append(matches.get(i));
        }
        return builder.toString();
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

    private String taskOptionString(int optionIndex, String defaultValue) {
        String value = taskOptionValue(optionIndex, defaultValue);
        return value == null || value.length() == 0 ? defaultValue : value;
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
        if ("claim_dispatch_board".equals(name)) {
            return "visit_dispatch_board";
        }
        if ("claim_interception".equals(name)) {
            return "visit_interception";
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
                ? waitForAnyUsefulFrame(capture, ProbeConfig.START_GAME_WORKFLOW_ATTACH_WAIT_SECONDS)
                : waitForUsefulFrame(capture, 75);
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!hasUsefulFrame) {
            if (workflowMode) {
                logger.log("workflow start_game did not receive useful frames; force restart game on display="
                        + displayId);
                launcher.startOnDisplay(displayId);
                capture.awaitFirstFrame(10, TimeUnit.SECONDS);
                hasUsefulFrame = waitForAnyUsefulFrame(capture,
                        ProbeConfig.START_GAME_WORKFLOW_RESTART_WAIT_SECONDS);
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
        runBackToHomeTask(capture, input, true);
    }

    private void runBackToHomeTask(FrameCaptureBackend capture, InputInjector input,
                                   boolean allowAttachFallback) throws Exception {
        boolean hasUsefulFrame = waitForAnyUsefulFrame(capture, ProbeConfig.BACK_TO_HOME_WAIT_SECONDS);
        if (hasUsefulFrame) {
            waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS, "back_to_home_initial");
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_BEFORE_ACTION_FILE);
        copyFile(ProbeConfig.TASK_BEFORE_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!hasUsefulFrame) {
            finalState = "no_useful_frame";
            logger.log("back_to_home stopped because no useful frame appeared");
            return;
        }

        for (int attempt = 0; attempt <= ProbeConfig.BACK_TO_HOME_MAX_BACKS; attempt++) {
            waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS, "back_to_home_attempt_" + attempt);
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
            } else if (isStartPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("back_to_home detected start page, try enter game first");
                if (enterGameFromStartPage(capture, input, "back_to_home")) {
                    if ("home_clear".equals(finalState)) {
                        actionSuccess = true;
                        writeResult(capture, "back_home_attempt_" + attempt);
                        return;
                    }
                    if ("network_retry_required".equals(finalState)
                            || "login_required".equals(finalState)
                            || (finalState != null && finalState.startsWith("client_update_external"))) {
                        return;
                    }
                }
            } else if (isLoginPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "login_required";
                actionSuccess = false;
                logger.log("back_to_home detected login page, user must log in manually");
                return;
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
            } else if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapInquiryHome(capture, input, "back_to_home_inquiry_home");
            } else if (isEventRewardPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapLeftBottomHome(capture, input, "back_to_home_event_home");
            } else if (isKnownBottomHomePageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapBottomHome(capture, input, "back_to_home_bottom_home");
            } else if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                closeHomePopupIfVisible(capture, input);
            } else if (isMailPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.MAIL_CLOSE_X, ProbeConfig.MAIL_CLOSE_Y,
                        "back_to_home_mail_close");
                Thread.sleep(1200);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_BACK_FILE);
                copyFile(ProbeConfig.TASK_AFTER_BACK_FILE, ProbeConfig.TASK_FRAME_FILE);
            } else if (isShopPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.SHOP_BACK_X, ProbeConfig.SHOP_BACK_Y,
                        "back_to_home_shop_back");
                Thread.sleep(1500);
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
                waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS,
                        "back_to_home_after_lobby_tap_" + attempt);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_BACK_FILE);
                copyFile(ProbeConfig.TASK_AFTER_BACK_FILE, ProbeConfig.TASK_FRAME_FILE);
                writeResult(capture, "back_home_attempt_" + attempt);
            }
        }

        if (!allowAttachFallback) {
            finalState = "home_not_ready";
            actionSuccess = false;
            logger.log("back_to_home stopped before attach fallback because caller disallowed start/attach wait");
            return;
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
        } else if ("claim_dispatch_board".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimDispatchBoardTask(capture, input, isDebugDryRunTask(normalizedStepName));
                }
            });
        } else if ("claim_interception".equals(toExecutableTaskName(normalizedStepName))) {
            runWorkflowStep(capture, input, normalizedStepName, new StepRunner() {
                @Override
                public void run() throws Exception {
                    runClaimInterceptionTask(capture, input, isDebugDryRunTask(normalizedStepName));
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
        Thread.sleep(1200);
        if (tryFastReturnHomeForWorkflow(capture, input, stepName)) {
            return;
        }
        if ("visit_mail".equals(stepName) && isMailPageVisible(ProbeConfig.TASK_FRAME_FILE)) {
            tap(input, ProbeConfig.MAIL_CLOSE_X, ProbeConfig.MAIL_CLOSE_Y, "workflow_mail_close");
            Thread.sleep(1200);
        } else if (isShopPageVisible(ProbeConfig.TASK_FRAME_FILE)) {
            tap(input, ProbeConfig.SHOP_BACK_X, ProbeConfig.SHOP_BACK_Y,
                    "workflow_shop_back_" + stepName);
            Thread.sleep(1500);
        } else if (isInquiryPageVisible(ProbeConfig.TASK_FRAME_FILE)) {
            tapInquiryHome(capture, input, "workflow_inquiry_home_" + stepName);
        } else if (isEventRewardPageVisible(ProbeConfig.TASK_FRAME_FILE)) {
            tapLeftBottomHome(capture, input, "workflow_event_home_" + stepName);
        } else if (isKnownBottomHomePageVisible(ProbeConfig.TASK_FRAME_FILE)) {
            tapBottomHome(capture, input, "workflow_bottom_home_" + stepName);
        } else {
            tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                    "workflow_lobby_anchor_after_" + stepName);
            Thread.sleep(1200);
        }
        if (tryFastReturnHomeForWorkflow(capture, input, stepName)) {
            return;
        }
        runBackToHomeTask(capture, input, false);
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
            } else if (isShopPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.SHOP_BACK_X, ProbeConfig.SHOP_BACK_Y,
                        "workflow_fast_shop_back_" + stepName + "_" + (attempt + 1));
            } else if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapInquiryHome(capture, input,
                        "workflow_fast_inquiry_home_" + stepName + "_" + (attempt + 1));
            } else if (isEventRewardPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapLeftBottomHome(capture, input,
                        "workflow_fast_event_home_" + stepName + "_" + (attempt + 1));
            } else if (isKnownBottomHomePageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapBottomHome(capture, input,
                        "workflow_fast_bottom_home_" + stepName + "_" + (attempt + 1));
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
            Thread.sleep(1200);
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
            if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapInquiryHome(capture, input, "workflow_stabilize_inquiry_home_" + stepName + "_" + attempt);
                continue;
            }
            if (isEventRewardPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapLeftBottomHome(capture, input, "workflow_stabilize_event_home_" + stepName + "_" + attempt);
                continue;
            }
            if (isKnownBottomHomePageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapBottomHome(capture, input, "workflow_stabilize_bottom_home_" + stepName + "_" + attempt);
                continue;
            }
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    && !isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    && !isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.HOME_LOBBY_X, ProbeConfig.HOME_LOBBY_Y,
                        "workflow_lobby_anchor_" + stepName + "_" + attempt);
                Thread.sleep(650);
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
        waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS, "ensure_home_" + pageName);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "home_clear";
            actionSuccess = true;
            stabilizeHomeAfterWorkflowStep(capture, input, "before_" + pageName);
            Thread.sleep(700);
            return;
        }
        if (isStartPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            logger.log("ensure_home detected start page, try enter game first pageName=" + pageName);
            enterGameFromStartPage(capture, input, "ensure_home_" + pageName);
            if ("home_clear".equals(finalState)) {
                actionSuccess = true;
                stabilizeHomeAfterWorkflowStep(capture, input, "before_" + pageName);
                Thread.sleep(700);
            }
            return;
        }
        if (isLoginPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "login_required";
            actionSuccess = false;
            logger.log("ensure_home stopped because login page is visible pageName=" + pageName);
            return;
        }
        if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tapInquiryHome(capture, input, "ensure_home_inquiry_home_" + pageName);
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "home_clear";
                actionSuccess = true;
                stabilizeHomeAfterWorkflowStep(capture, input, "before_" + pageName);
                Thread.sleep(700);
                return;
            }
        }
        if (isEventRewardPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tapLeftBottomHome(capture, input, "ensure_home_event_home_" + pageName);
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "home_clear";
                actionSuccess = true;
                stabilizeHomeAfterWorkflowStep(capture, input, "before_" + pageName);
                Thread.sleep(700);
                return;
            }
        }
        if (isKnownBottomHomePageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tapBottomHome(capture, input, "ensure_home_bottom_home_" + pageName);
            if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "home_clear";
                actionSuccess = true;
                stabilizeHomeAfterWorkflowStep(capture, input, "before_" + pageName);
                Thread.sleep(700);
                return;
            }
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
        boolean useDiamondCleanSweep = taskOptionYes(0, false);
        int cleanSweepTimes = taskOptionInt(1, 1, 1, 11);
        int maxCleanConfirmAttempts = useDiamondCleanSweep ? cleanSweepTimes : 1;
        logger.log("claim_outpost_defense options useDiamondCleanSweep=" + useDiamondCleanSweep
                + " cleanSweepTimes=" + cleanSweepTimes
                + " maxCleanConfirmAttempts=" + maxCleanConfirmAttempts);
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

        tap(input, ProbeConfig.OUTPOST_GET_REWARD_X, ProbeConfig.OUTPOST_GET_REWARD_Y,
                "outpost_get_reward_first");
        Thread.sleep(1800);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (dryRun) {
            finalState = "outpost_reward_button_previewed";
            actionSuccess = true;
            logger.log("debug dry-run stopped after opening outpost reward button");
            return;
        }
        closeGenericRewardConfirmIfVisible(capture, input, "outpost_reward_confirm_first",
                ProbeConfig.OUTPOST_REWARD_CONFIRM_X, ProbeConfig.OUTPOST_REWARD_CONFIRM_Y);
        tap(input, ProbeConfig.OUTPOST_REWARD_CONFIRM_X, ProbeConfig.OUTPOST_REWARD_CONFIRM_Y,
                "outpost_reward_close_candidate_first");
        Thread.sleep(1400);

        tap(input, ProbeConfig.OUTPOST_CLEAN_SWEEP_X, ProbeConfig.OUTPOST_CLEAN_SWEEP_Y,
                "outpost_clean_sweep");
        Thread.sleep(2400);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        boolean cleanConfirmVisible = isOutpostCleanConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        for (int attempt = 1; cleanConfirmVisible && attempt <= maxCleanConfirmAttempts; attempt++) {
            waitForOutpostCleanConfirmReady(capture, attempt);
            tap(input, ProbeConfig.OUTPOST_CLEAN_CONFIRM_X, ProbeConfig.OUTPOST_CLEAN_CONFIRM_Y,
                    "outpost_clean_confirm_" + attempt);
            cleanConfirmVisible = waitForOutpostCleanConfirmDismissed(capture, attempt);
        }
        if (cleanConfirmVisible) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (!useDiamondCleanSweep) {
                finalState = "outpost_diamond_clean_confirm_skipped";
                actionSuccess = true;
                logger.log("claim_outpost_defense stopped before extra diamond clean-sweep confirmations");
                return;
            }
            finalState = "outpost_clean_confirm_still_visible";
            actionSuccess = false;
            logger.log("claim_outpost_defense stopped, clean-sweep confirm dialog still visible");
            return;
        }
        handleOutpostCleanNoticeIfVisible(capture, input);
        logger.log("outpost clean confirm dialog closed or not visible after clean-sweep tap");
        tap(input, ProbeConfig.OUTPOST_CLEAN_REWARD_X, ProbeConfig.OUTPOST_CLEAN_REWARD_Y,
                "outpost_clean_reward_close");
        Thread.sleep(1400);

        tap(input, ProbeConfig.OUTPOST_GET_REWARD_X, ProbeConfig.OUTPOST_GET_REWARD_Y,
                "outpost_get_reward_after_sweep");
        Thread.sleep(1400);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        closeGenericRewardConfirmIfVisible(capture, input, "outpost_reward_confirm_after_sweep",
                ProbeConfig.OUTPOST_REWARD_CONFIRM_X, ProbeConfig.OUTPOST_REWARD_CONFIRM_Y);

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = "outpost_reward_claim_attempted";
        actionSuccess = true;
        logger.log("claim_outpost_defense completed with get-reward first and clean-sweep attempts");
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
        boolean basicShopEnabled = taskOptionYes(0, false);
        boolean arenaShopEnabled = taskOptionYes(1, false);
        String arenaShopItems = taskOptionString(2, "");
        boolean bodyTagShopEnabled = taskOptionYes(3, false);
        boolean scrapShopEnabled = taskOptionYes(4, false);
        logger.log("claim_free_shop options basicShopEnabled=" + basicShopEnabled
                + " arenaShopEnabled=" + arenaShopEnabled
                + " arenaShopItems=" + arenaShopItems
                + " bodyTagShopEnabled=" + bodyTagShopEnabled
                + " scrapShopEnabled=" + scrapShopEnabled);
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
        if (!isShopPageVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "free_shop_page_not_confirmed";
            actionSuccess = false;
            logger.log("claim_free_shop stopped, shop page visual confirmation failed");
            return;
        }
        if (!basicShopEnabled && !arenaShopEnabled && !bodyTagShopEnabled && !scrapShopEnabled) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "free_shop_all_branches_disabled";
            actionSuccess = true;
            logger.log("claim_free_shop stopped because every shop branch is disabled");
            return;
        }

        boolean handledAnyBranch = false;
        if (basicShopEnabled) {
            handledAnyBranch = true;
            tap(input, ProbeConfig.SHOP_TAB_BASIC_X, ProbeConfig.SHOP_TAB_BASIC_Y, "free_shop_tab_basic");
            Thread.sleep(1000);
            tap(input, ProbeConfig.SHOP_FREE_ITEM_X, ProbeConfig.SHOP_FREE_ITEM_Y,
                    "free_shop_daily_discount_item");
            Thread.sleep(1400);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);

            boolean purchaseDialogVisible = isShopPurchaseDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
            if (dryRun && purchaseDialogVisible) {
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
                copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
                finalState = "free_shop_purchase_dialog_previewed";
                actionSuccess = true;
                logger.log("debug dry-run stopped before free shop quantity/buy confirm");
                return;
            }

            if (purchaseDialogVisible) {
                tap(input, ProbeConfig.SHOP_QUANTITY_CONFIRM_X, ProbeConfig.SHOP_QUANTITY_CONFIRM_Y,
                        "free_shop_quantity_confirm_candidate");
                Thread.sleep(1400);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);

                if (isShopPurchaseDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    tap(input, ProbeConfig.SHOP_BUY_CONFIRM_X, ProbeConfig.SHOP_BUY_CONFIRM_Y,
                            "free_shop_buy_confirm_candidate");
                    Thread.sleep(1800);
                    capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                    copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
                }

                closeGenericRewardConfirmIfVisible(capture, input, "free_shop_reward_confirm",
                        ProbeConfig.SHOP_REWARD_CONFIRM_X, ProbeConfig.SHOP_REWARD_CONFIRM_Y);
                tap(input, ProbeConfig.SHOP_REWARD_CONFIRM_X, ProbeConfig.SHOP_REWARD_CONFIRM_Y,
                        "free_shop_reward_close_candidate");
                Thread.sleep(1400);
            } else {
                logger.log("free shop purchase dialog not visible; item may already be sold out, continue to refresh");
            }

            tap(input, ProbeConfig.SHOP_FREE_REFRESH_X, ProbeConfig.SHOP_FREE_REFRESH_Y,
                    "free_shop_refresh_candidate");
            Thread.sleep(1500);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (dryRun) {
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
                copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
                finalState = purchaseDialogVisible
                        ? "free_shop_purchase_and_refresh_previewed"
                        : "free_shop_refresh_previewed";
                actionSuccess = true;
                logger.log("debug dry-run stopped after opening free shop refresh candidate");
                return;
            }
            if (isShopPurchaseDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isUpdateDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.SHOP_REFRESH_CONFIRM_X, ProbeConfig.SHOP_REFRESH_CONFIRM_Y,
                        "free_shop_refresh_confirm_candidate");
                Thread.sleep(1900);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            } else {
                logger.log("free shop refresh confirm not visible after refresh tap; skip confirm");
            }
        }

        StringBuilder unsupportedBranches = new StringBuilder();
        if (arenaShopEnabled) {
            handledAnyBranch = true;
            tap(input, ProbeConfig.SHOP_TAB_ARENA_X, ProbeConfig.SHOP_TAB_ARENA_Y, "free_shop_tab_arena");
            Thread.sleep(1000);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            logger.log("free shop arena branch enabled items=" + arenaShopItems
                    + " parsedItems=" + normalizeSelectedShopItems(arenaShopItems));
            appendUnsupportedShopBranch(unsupportedBranches, "arena");
        }
        if (bodyTagShopEnabled) {
            handledAnyBranch = true;
            tap(input, ProbeConfig.SHOP_TAB_BODY_TAG_X, ProbeConfig.SHOP_TAB_BODY_TAG_Y, "free_shop_tab_body_tag");
            Thread.sleep(1000);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            logger.log("free shop body-tag branch enabled");
            appendUnsupportedShopBranch(unsupportedBranches, "body_tag");
        }
        if (scrapShopEnabled) {
            handledAnyBranch = true;
            tap(input, ProbeConfig.SHOP_TAB_SCRAP_X, ProbeConfig.SHOP_TAB_SCRAP_Y, "free_shop_tab_scrap");
            Thread.sleep(1000);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            logger.log("free shop scrap branch enabled");
            appendUnsupportedShopBranch(unsupportedBranches, "scrap");
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (unsupportedBranches.length() > 0) {
            finalState = "free_shop_partial_support_pending_" + unsupportedBranches;
        } else {
            finalState = handledAnyBranch
                    ? "free_shop_purchase_and_refresh_attempted"
                    : "free_shop_all_branches_disabled";
        }
        actionSuccess = true;
        logger.log("claim_free_shop completed with branch controls handledAnyBranch=" + handledAnyBranch
                + " unsupportedBranches=" + unsupportedBranches);
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
        boolean taskClaimAvailable = tapPassClaimButton(capture, input, "pass_claim_all_task", dryRun);

        tap(input, ProbeConfig.PASS_REWARD_TAB_X, ProbeConfig.PASS_REWARD_TAB_Y, "pass_reward_tab");
        Thread.sleep(700);
        boolean rewardClaimAvailable = tapPassClaimButton(capture, input, "pass_claim_all_reward", dryRun);

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!taskClaimAvailable && !rewardClaimAvailable) {
            finalState = "pass_rewards_no_claim_available";
        } else {
            finalState = dryRun ? "pass_rewards_claim_available_previewed" : "pass_rewards_claim_attempted";
        }
        actionSuccess = true;
        logger.log("claim_pass_rewards completed dryRun=" + dryRun
                + " taskClaimAvailable=" + taskClaimAvailable
                + " rewardClaimAvailable=" + rewardClaimAvailable);
    }

    private boolean tapPassClaimButton(FrameCaptureBackend capture, InputInjector input, String label, boolean dryRun)
            throws Exception {
        if (!waitForPassClaimButtonVisible(capture, label)) {
            logger.log("pass claim button not visible, skip label=" + label);
            return false;
        }
        if (dryRun) {
            logger.log("debug dry-run skip pass claim tap label=" + label);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            return true;
        }
        tap(input, ProbeConfig.PASS_CLAIM_ALL_X, ProbeConfig.PASS_CLAIM_ALL_Y, label);
        Thread.sleep(1200);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        closeGenericRewardConfirmIfVisible(capture, input, label + "_confirm",
                ProbeConfig.PASS_REWARD_CONFIRM_X, ProbeConfig.PASS_REWARD_CONFIRM_Y);
        return true;
    }

    private boolean waitForPassClaimButtonVisible(FrameCaptureBackend capture, String label) throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isPassClaimButtonVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("pass claim button visible label=" + label + " attempt=" + attempt);
                return true;
            }
            Thread.sleep(attempt == 1 ? 350 : 650);
        }
        return false;
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
        } else if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            closeHomePopupIfVisible(capture, input);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            logger.log("workflow closed home popup while looking for debug preview label=" + label);
            return true;
        } else if (isOutpostCleanNoticeVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.OUTPOST_NOTICE_CONFIRM_X, ProbeConfig.OUTPOST_NOTICE_CONFIRM_Y,
                    label + "_outpost_notice_confirm");
        } else if (isOutpostCleanConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.OUTPOST_NOTICE_CONFIRM_X, ProbeConfig.OUTPOST_NOTICE_CONFIRM_Y,
                    label + "_outpost_clean_close");
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

    private void runClaimDispatchBoardTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        if (!openHomeEntry(capture, input, "dispatch_board", ProbeConfig.DISPATCH_BOARD_ENTRY_X,
                ProbeConfig.DISPATCH_BOARD_ENTRY_Y, "dispatch_board_entry")) {
            return;
        }
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_OPEN_FILE)) {
            finalState = "claim_dispatch_board_still_home";
            actionSuccess = false;
            logger.log("claim_dispatch_board stopped, dispatch page did not open");
            return;
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        boolean dispatchPageVisible = isDispatchBoardPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        boolean dispatchPageOcrHit = probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "dispatchboard.checkdispatchboard", new int[]{482, 88, 112, 41},
                new String[]{"派遣公告栏"});
        if (!dispatchPageVisible && !dispatchPageOcrHit) {
            finalState = "dispatch_board_page_not_confirmed";
            actionSuccess = false;
            logger.log("claim_dispatch_board stopped, dispatch page visual confirmation failed");
            return;
        }
        boolean claimVisible = isDispatchBoardClaimButtonVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        boolean claimOcrHit = probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "dispatchboard.claimall", new int[]{702, 575, 110, 56},
                new String[]{"全部领取"});
        boolean dispatchVisible = isDispatchBoardDispatchButtonVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        boolean dispatchOcrHit = probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "dispatchboard.dispatchall", new int[]{582, 580, 118, 54},
                new String[]{"全部派遣"});
        logger.log("dispatch board initial buttons claimVisible=" + claimVisible
                + " claimOcrHit=" + claimOcrHit
                + " dispatchVisible=" + dispatchVisible
                + " dispatchOcrHit=" + dispatchOcrHit);
        if (dryRun) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "dispatch_board_buttons_previewed";
            actionSuccess = true;
            logger.log("debug dry-run stopped on dispatch board page before claim/dispatch all"
                    + " claimVisible=" + claimVisible
                    + " claimOcrHit=" + claimOcrHit
                    + " dispatchVisible=" + dispatchVisible
                    + " dispatchOcrHit=" + dispatchOcrHit);
            return;
        }
        if (!claimVisible && !dispatchVisible) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "dispatch_board_no_claim_or_dispatch_button";
            actionSuccess = true;
            logger.log("claim_dispatch_board stopped because no actionable dispatch button is visible");
            return;
        }

        boolean claimedAny = false;
        if (claimVisible) {
            tap(input, ProbeConfig.DISPATCH_CLAIM_ALL_X, ProbeConfig.DISPATCH_CLAIM_ALL_Y,
                    "dispatch_claim_all");
            Thread.sleep(1800);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            closeGenericRewardConfirmIfVisible(capture, input, "dispatch_claim_all_reward_confirm",
                    ProbeConfig.DISPATCH_CONFIRM_X, ProbeConfig.DISPATCH_CONFIRM_Y);
            claimedAny = true;
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        }

        dispatchVisible = isDispatchBoardDispatchButtonVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        dispatchOcrHit = probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "dispatchboard.dispatchall_after_claim", new int[]{582, 580, 118, 54},
                new String[]{"全部派遣"});
        if (!dispatchVisible) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = claimedAny
                    ? "dispatch_board_claim_only_attempted"
                    : "dispatch_board_nothing_to_dispatch";
            actionSuccess = true;
            logger.log("claim_dispatch_board completed without dispatch-all because dispatch button is not visible"
                    + " dispatchOcrHit=" + dispatchOcrHit);
            return;
        }

        tap(input, ProbeConfig.DISPATCH_ALL_X, ProbeConfig.DISPATCH_ALL_Y,
                "dispatch_all");
        Thread.sleep(1700);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isDispatchBoardDispatchConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.DISPATCH_CONFIRM_X, ProbeConfig.DISPATCH_CONFIRM_Y,
                    "dispatch_all_confirm_candidate");
            Thread.sleep(2000);
        } else {
            logger.log("dispatch board dispatch confirm dialog not visible after dispatch-all tap");
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        finalState = "dispatch_board_claim_and_dispatch_attempted";
        actionSuccess = true;
        logger.log("claim_dispatch_board completed with claim-all and dispatch-all attempts");
    }

    private void runClaimInquiryAndGiftTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        int giftCount = taskOptionInt(0, 1, 0, 3);
        logger.log("claim_inquiry_and_gift options giftCount=" + giftCount
                + " flow=enter_nikkes_then_inquiry_then_batch_then_first_gift");
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
        probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "inquiryandgift.checkinquiry", new int[]{556, 52, 168, 39},
                new String[]{"咨询"});
        if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            closeHomePopupIfVisible(capture, input);
            tap(input, ProbeConfig.NIKKES_INQUIRY_TAB_X, ProbeConfig.NIKKES_INQUIRY_TAB_Y,
                    "inquiry_tab_retry");
            Thread.sleep(2200);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                    "inquiryandgift.checkinquiry_retry", new int[]{556, 52, 168, 39},
                    new String[]{"咨询"});
        }
        probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "inquiryandgift.1keytoinquiry", new int[]{1176, 650, 100, 57},
                new String[]{"批量咨询"});
        tap(input, ProbeConfig.INQUIRY_BATCH_BUTTON_X, ProbeConfig.INQUIRY_BATCH_BUTTON_Y,
                "inquiry_batch_button");
        Thread.sleep(1600);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "inquiryandgift.click1keytoinquiry", new int[]{726, 601, 114, 55},
                new String[]{"批量咨询"});
        probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "inquiryandgift.confirm1keytoinquiry", new int[]{623, 363, 225, 154},
                new String[]{"确认"});
        if (dryRun) {
            logger.log("debug dry-run previewed inquiry batch confirm; skip batch consult confirmation");
            if (giftCount > 0) {
                tap(input, ProbeConfig.INQUIRY_CLOSE_X, ProbeConfig.INQUIRY_CLOSE_Y,
                        "inquiry_batch_preview_close");
                Thread.sleep(900);
                runGiftForTopNikke(capture, input, 0, true);
            }
        } else {
            tap(input, ProbeConfig.INQUIRY_BATCH_CONFIRM_X, ProbeConfig.INQUIRY_BATCH_CONFIRM_Y,
                    "inquiry_batch_confirm");
            Thread.sleep(2400);
            for (int attempt = 1; attempt <= 3; attempt++) {
                tap(input, ProbeConfig.INQUIRY_NEXT_STEP_X, ProbeConfig.INQUIRY_NEXT_STEP_Y,
                        "inquiry_next_step_or_reward_" + attempt);
                Thread.sleep(1400);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
                if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    break;
                }
            }
            tap(input, ProbeConfig.INQUIRY_CLOSE_X, ProbeConfig.INQUIRY_CLOSE_Y,
                    "inquiry_close_candidate");
            Thread.sleep(1500);
            for (int index = 0; index < giftCount; index++) {
                runGiftForTopNikke(capture, input, index, false);
            }
        }

        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!dryRun) {
            tapInquiryHome(capture, input, "inquiry_final_home");
            if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapInquiryHome(capture, input, "inquiry_final_home_retry");
            }
        }
        if (!"home_clear".equals(finalState)) {
            runBackToHomeTask(capture, input, false);
        }
        if ("home_clear".equals(finalState)) {
            finalState = dryRun ? "inquiry_and_gift_previewed_home_clear"
                    : "inquiry_and_gift_claim_and_gift_home_clear";
            actionSuccess = true;
        } else {
            finalState = dryRun ? "inquiry_and_gift_previewed_home_not_confirmed"
                    : "inquiry_and_gift_claim_and_gift_home_not_confirmed";
            actionSuccess = false;
        }
        logger.log("claim_inquiry_and_gift completed dryRun=" + dryRun
                + " giftCount=" + giftCount + " finalState=" + finalState);
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
        Thread.sleep(2100);
        tap(input, ProbeConfig.INQUIRY_GIFT_BUTTON_X, ProbeConfig.INQUIRY_GIFT_BUTTON_Y,
                "gift_button_" + (safeIndex + 1));
        Thread.sleep(1900);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_WAIT_FILE,
                "inquiryandgift.checksendgift_" + (safeIndex + 1), new int[]{575, 82, 141, 40},
                new String[]{"送礼"});
        tap(input, ProbeConfig.INQUIRY_BASIC_GIFT_X, ProbeConfig.INQUIRY_BASIC_GIFT_Y,
                "gift_basic_item_" + (safeIndex + 1));
        Thread.sleep(1400);
        if (dryRun) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            tap(input, ProbeConfig.INQUIRY_GIFT_BACK_X, ProbeConfig.INQUIRY_GIFT_BACK_Y,
                    "gift_debug_back_from_preview_" + (safeIndex + 1));
            Thread.sleep(1600);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            tapInquiryHome(capture, input, "gift_debug_home_from_preview_" + (safeIndex + 1));
            if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapInquiryHome(capture, input, "gift_debug_home_from_preview_retry_" + (safeIndex + 1));
            }
            waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS,
                    "gift_debug_home_settle_" + (safeIndex + 1));
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            logger.log("debug dry-run previewed gift page and skipped send-gift index=" + safeIndex);
            return;
        }
        tap(input, ProbeConfig.INQUIRY_SEND_GIFT_X, ProbeConfig.INQUIRY_SEND_GIFT_Y,
                "gift_send_button_" + (safeIndex + 1));
        Thread.sleep(1600);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        tap(input, ProbeConfig.INQUIRY_SEND_GIFT_CONFIRM_X, ProbeConfig.INQUIRY_SEND_GIFT_CONFIRM_Y,
                "gift_send_confirm_" + (safeIndex + 1));
        Thread.sleep(2300);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_GIFT_CONFIRM_FILE);
        copyFile(ProbeConfig.TASK_AFTER_GIFT_CONFIRM_FILE, ProbeConfig.TASK_FRAME_FILE);
        closeGenericRewardConfirmIfVisible(capture, input, "gift_reward_confirm_" + (safeIndex + 1),
                ProbeConfig.INQUIRY_SEND_GIFT_CONFIRM_X, ProbeConfig.INQUIRY_SEND_GIFT_CONFIRM_Y);
        tap(input, ProbeConfig.INQUIRY_GIFT_BACK_X, ProbeConfig.INQUIRY_GIFT_BACK_Y,
                "gift_back_to_inquiry_" + (safeIndex + 1));
        Thread.sleep(1800);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.INQUIRY_GIFT_BACK_X, ProbeConfig.INQUIRY_GIFT_BACK_Y,
                    "gift_back_to_inquiry_retry_" + (safeIndex + 1));
            Thread.sleep(1600);
        }
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
            int tapX = subpageX;
            int tapY = subpageY;
            if ("arena".equals(pageName) && attempt > 1) {
                tapX = ProbeConfig.ARENA_ENTRY_CONFIRM_X;
                tapY = ProbeConfig.ARENA_ENTRY_CONFIRM_Y;
            }
            tap(input, tapX, tapY, subpageLabel + "_entry_attempt_" + attempt);
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
        if ("interception".equals(pageName)) {
            return isInterceptionPageVisible(frameFile);
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

    private void runMaaCoreProbeTask(FrameCaptureBackend capture) throws Exception {
        logger.log("maacore probe start: stub controller check only, no MaaCore native call yet");
        boolean usefulFrame = waitForAnyUsefulFrame(capture, 8);
        Thread.sleep(1200);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);

        Bitmap bitmap = BitmapFactory.decodeFile(ProbeConfig.TASK_AFTER_ACTION_FILE.getAbsolutePath());
        int width = -1;
        int height = -1;
        boolean frameDecoded = false;
        boolean quickBattleVisible = false;
        boolean challengeBossVisible = false;
        if (bitmap != null) {
            try {
                width = bitmap.getWidth();
                height = bitmap.getHeight();
                frameDecoded = width > 0 && height > 0;
            } finally {
                bitmap.recycle();
            }
            quickBattleVisible = isInterceptionQuickBattleVisible(ProbeConfig.TASK_AFTER_ACTION_FILE);
            challengeBossVisible = isInterceptionChallengeBossVisible(ProbeConfig.TASK_AFTER_ACTION_FILE);
        }

        String resourcePath = firstExistingDirectory(ProbeConfig.MAACORE_RESOURCE_CANDIDATES);
        String libraryPath = firstExistingFile(ProbeConfig.MAACORE_LIBRARY_CANDIDATES);
        String controlUnitPath = firstExistingFile(ProbeConfig.MAACORE_CONTROL_UNIT_CANDIDATES);
        String bridgePath = firstExistingFile(ProbeConfig.MAACORE_BRIDGE_CANDIDATES);
        String resourceVersionPath = firstExistingFile(ProbeConfig.MAACORE_RESOURCE_VERSION_CANDIDATES);
        String resourceVersion = sanitizeReportText(readTextFileLimited(resourceVersionPath, 200));
        EvidenceCasesStatus evidenceStatus = inspectEvidenceCases();
        boolean resourceReady = resourcePath.length() > 0;
        boolean libraryReady = libraryPath.length() > 0;
        boolean controlUnitReady = controlUnitPath.length() > 0;
        boolean bridgeReady = bridgePath.length() > 0;
        boolean pipelineReady = resourceReady && deviceFileExists(resourcePath + "/pipeline/task/interception.json");
        OcrModelStatus ocrStatus = detectOcrModelStatus(resourcePath);
        boolean ocrModelReady = ocrStatus.ready;
        boolean resourcePayloadReady = resourceReady && pipelineReady && ocrModelReady;
        String nativeLoadError = "";
        boolean nativeLoadReady = false;
        if (libraryReady) {
            nativeLoadError = tryLoadMaaNativeLibraries(libraryPath);
            nativeLoadReady = nativeLoadError.length() == 0;
        }
        boolean coreReady = resourcePayloadReady && libraryReady && nativeLoadReady;
        String bridgeReport = "";
        String bridgeError = "";
        boolean bridgeLoadReady = false;
        boolean bridgeCallReady = false;
        boolean bridgeCachedImage = false;
        boolean bridgeOcrSucceeded = false;
        if (resourcePayloadReady && libraryReady && nativeLoadReady && controlUnitReady && bridgeReady) {
            try {
                MaaCoreNativeBridge.load(bridgePath);
                bridgeLoadReady = true;
                bridgeReport = MaaCoreNativeBridge.runProbe(displayId, libraryPath, controlUnitPath, resourcePath,
                        ProbeConfig.TASK_AFTER_ACTION_FILE.getAbsolutePath(),
                        buildMaaCoreOcrParamJson(new int[]{642, 582, 193, 55},
                                new String[]{"快速战斗", "每周快速战斗"}));
                bridgeCallReady = bridgeReport.indexOf("bridgeLoaded=true") >= 0
                        && (bridgeReport.indexOf("androidControllerCreated=true") >= 0
                        || bridgeReport.indexOf("fileControllerCreated=true") >= 0);
                bridgeCachedImage = bridgeReport.indexOf("androidCachedImage=true") >= 0
                        || bridgeReport.indexOf("fileCachedImage=true") >= 0;
                bridgeOcrSucceeded = bridgeReport.indexOf("androidSucceeded=true") >= 0
                        || bridgeReport.indexOf("fileSucceeded=true") >= 0;
            } catch (Throwable error) {
                bridgeError = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
                bridgeError = bridgeError.replace('\n', ' ').replace('\r', ' ');
            }
        }
        String nextStep;
        if (!resourcePayloadReady && !libraryReady) {
            nextStep = "push PC MaaNikke resource/base and MaaCore/MaaFramework Android library, then replace stub with native OCR/template call";
        } else if (!resourcePayloadReady) {
            nextStep = "push complete PC MaaNikke resource/base, then run native OCR/template probe";
        } else if (!libraryReady) {
            nextStep = "push MaaCore/MaaFramework Android library, then replace stub with native OCR/template call";
        } else if (!nativeLoadReady) {
            nextStep = "fix MaaCore/MaaFramework native library load path/dependencies, then replace stub with native OCR/template call";
        } else if (!controlUnitReady) {
            nextStep = "push libMaaAndroidNativeControlUnit.so, then rerun native controller probe";
        } else if (!bridgeReady) {
            nextStep = "push libmaanikke_maacore_bridge.so, then rerun native OCR/template probe";
        } else if (!bridgeCallReady) {
            nextStep = "fix MaaCore native bridge/controller initialization; check bridgeReport and backend stdout";
        } else if (!bridgeCachedImage) {
            nextStep = "fix MaaAndroidNativeController screencap for current displayId before OCR migration";
        } else if (!bridgeOcrSucceeded) {
            nextStep = "native bridge works; tune OCR params/resource node and validate quickbattle on interception page";
        } else {
            nextStep = "native OCR bridge ready; validate quickbattle page and begin low-risk task migration";
        }

        String report = "maacore_probe_version=stub_p1\n"
                + "displayId=" + displayId + "\n"
                + "virtualDisplay=" + ProbeConfig.WIDTH + "x" + ProbeConfig.HEIGHT + "@" + ProbeConfig.DPI + "\n"
                + "usefulFrame=" + usefulFrame + "\n"
                + "frameDecoded=" + frameDecoded + "\n"
                + "frameFile=" + ProbeConfig.TASK_AFTER_ACTION_FILE.getAbsolutePath() + "\n"
                + "frameWidth=" + width + "\n"
                + "frameHeight=" + height + "\n"
                + "coreReady=" + coreReady + "\n"
                + "resourceReady=" + resourceReady + "\n"
                + "resourcePath=" + resourcePath + "\n"
                + "resourceVersionPath=" + resourceVersionPath + "\n"
                + "resourceVersion=" + resourceVersion + "\n"
                + "libraryReady=" + libraryReady + "\n"
                + "libraryPath=" + libraryPath + "\n"
                + "controlUnitReady=" + controlUnitReady + "\n"
                + "controlUnitPath=" + controlUnitPath + "\n"
                + "bridgeReady=" + bridgeReady + "\n"
                + "bridgePath=" + bridgePath + "\n"
                + "pipelineReady=" + pipelineReady + "\n"
                + "ocrModelReady=" + ocrModelReady + "\n"
                + "ocrModelType=" + ocrStatus.type + "\n"
                + "ocrModelPath=" + ocrStatus.path + "\n"
                + "ocrModelDetail=" + ocrStatus.detail + "\n"
                + "resourcePayloadReady=" + resourcePayloadReady + "\n"
                + "evidenceCasesReady=" + evidenceStatus.ready + "\n"
                + "evidenceCasesPath=" + evidenceStatus.path + "\n"
                + "evidenceTotalCases=" + evidenceStatus.totalCases + "\n"
                + "evidenceEnabledCases=" + evidenceStatus.enabledCases + "\n"
                + "evidenceDisabledCases=" + evidenceStatus.disabledCases + "\n"
                + "evidencePendingImageCases=" + evidenceStatus.pendingImageCases + "\n"
                + "missingEvidenceCount=" + evidenceStatus.missingEvidenceCount + "\n"
                + "evidenceDetail=" + evidenceStatus.detail + "\n"
                + "nativeLoadReady=" + nativeLoadReady + "\n"
                + "nativeLoadError=" + nativeLoadError + "\n"
                + "bridgeLoadReady=" + bridgeLoadReady + "\n"
                + "bridgeCallReady=" + bridgeCallReady + "\n"
                + "bridgeCachedImage=" + bridgeCachedImage + "\n"
                + "bridgeOcrSucceeded=" + bridgeOcrSucceeded + "\n"
                + "bridgeError=" + bridgeError + "\n"
                + "quickBattleRoiVisibleByJavaFallback=" + quickBattleVisible + "\n"
                + "challengeBossVisibleByJavaFallback=" + challengeBossVisible + "\n"
                + "next=" + nextStep + "\n"
                + "bridgeReportBegin\n"
                + bridgeReport
                + "bridgeReportEnd\n";
        writeTextFile(ProbeConfig.MAACORE_PROBE_REPORT_FILE, report);
        logger.log(report.replace('\n', ';'));

        if (bridgeOcrSucceeded) {
            finalState = "maacore_probe_native_ocr_ready";
        } else if (bridgeCallReady && bridgeCachedImage) {
            finalState = "maacore_probe_native_bridge_ready";
        } else if (resourcePayloadReady && libraryReady && nativeLoadReady && controlUnitReady && bridgeReady) {
            finalState = "maacore_probe_native_bridge_failed";
        } else if (resourcePayloadReady && libraryReady && nativeLoadReady) {
            finalState = "maacore_probe_ready_for_native_call";
        } else if (resourcePayloadReady && libraryReady) {
            finalState = "maacore_probe_library_load_failed";
        } else if (resourcePayloadReady) {
            finalState = "maacore_probe_missing_library";
        } else if (libraryReady) {
            finalState = "maacore_probe_missing_resource_payload";
        } else {
            finalState = "maacore_probe_missing_resource_payload_and_library";
        }
        actionSuccess = frameDecoded;
    }

    private OcrModelStatus detectOcrModelStatus(String resourcePath) {
        if (resourcePath == null || resourcePath.length() == 0) {
            return new OcrModelStatus(false, "none", "", "resource path missing");
        }
        String ncnnPath = firstReadyNcnnOcrDirectory(resourcePath);
        if (ncnnPath.length() > 0) {
            return new OcrModelStatus(true, "ncnn", ncnnPath,
                    "PaddleOCR/PaddleCharOCR det+rec ncnn model detected");
        }
        String onnxPath = resourcePath + "/model/ocr";
        boolean onnxReady = deviceFileExists(onnxPath + "/det.onnx")
                && deviceFileExists(onnxPath + "/rec.onnx")
                && deviceFileExists(onnxPath + "/keys.txt");
        if (onnxReady) {
            return new OcrModelStatus(true, "onnx", onnxPath,
                    "PC MaaNikke ONNX OCR model detected");
        }
        String legacyPath = resourcePath + "/model/ocr";
        boolean legacyNcnnReady = deviceFileExists(legacyPath + "/rec.param")
                && deviceFileExists(legacyPath + "/rec.bin");
        if (legacyNcnnReady) {
            return new OcrModelStatus(true, "legacy_ncnn", legacyPath,
                    "legacy flat rec.param/rec.bin model detected");
        }
        return new OcrModelStatus(false, "none", "",
                "missing OCR model: expected ONNX model/ocr or NCNN PaddleOCR/PaddleCharOCR");
    }

    private String firstReadyNcnnOcrDirectory(String resourcePath) {
        for (int i = 0; i < ProbeConfig.MAACORE_NCNN_OCR_RELATIVE_CANDIDATES.length; i++) {
            String relative = ProbeConfig.MAACORE_NCNN_OCR_RELATIVE_CANDIDATES[i];
            String path = resourcePath + "/" + relative;
            boolean detReady = deviceFileExists(path + "/det/det.ncnn.bin")
                    && deviceFileExists(path + "/det/det.ncnn.param");
            boolean recReady = deviceFileExists(path + "/rec/rec.ncnn.bin")
                    && deviceFileExists(path + "/rec/rec.ncnn.param")
                    && deviceFileExists(path + "/rec/keys.txt");
            if (detReady && recReady) {
                return path;
            }
        }
        return "";
    }

    private static final class OcrModelStatus {
        final boolean ready;
        final String type;
        final String path;
        final String detail;

        OcrModelStatus(boolean ready, String type, String path, String detail) {
            this.ready = ready;
            this.type = type == null ? "" : type;
            this.path = path == null ? "" : path;
            this.detail = detail == null ? "" : detail;
        }
    }

    private EvidenceCasesStatus inspectEvidenceCases() {
        String path = firstExistingFile(ProbeConfig.MAACORE_EVIDENCE_CASES_CANDIDATES);
        if (path.length() == 0) {
            return new EvidenceCasesStatus(false, "", 0, 0, 0, 0, 0,
                    "ocr_regression_cases.json missing");
        }
        try {
            String raw = readTextFileLimited(path, 128 * 1024);
            JSONObject root = new JSONObject(raw);
            JSONArray cases = root.optJSONArray("cases");
            if (cases == null) {
                return new EvidenceCasesStatus(false, path, 0, 0, 0, 0, 0,
                        "cases array missing");
            }

            File evidenceRoot = new File(path).getParentFile();
            int totalCases = cases.length();
            int enabledCases = 0;
            int disabledCases = 0;
            int pendingImageCases = 0;
            int enabledMissingImages = 0;

            for (int i = 0; i < totalCases; i++) {
                JSONObject item = cases.optJSONObject(i);
                if (item == null) {
                    enabledMissingImages++;
                    continue;
                }
                boolean enabled = item.optBoolean("enabled", true);
                String image = item.optString("image", "");
                boolean pendingImage = "pending".equals(image);
                if (enabled) {
                    enabledCases++;
                    boolean imageMissing = image.length() == 0 || pendingImage
                            || evidenceRoot == null
                            || !new File(evidenceRoot, image).exists();
                    if (imageMissing) {
                        enabledMissingImages++;
                    }
                } else {
                    disabledCases++;
                }
                if (pendingImage) {
                    pendingImageCases++;
                }
            }

            int missingEvidenceCount = disabledCases + enabledMissingImages;
            boolean ready = totalCases > 0 && enabledCases > 0 && enabledMissingImages == 0;
            String detail = "enabledMissingImages=" + enabledMissingImages;
            return new EvidenceCasesStatus(ready, path, totalCases, enabledCases, disabledCases,
                    pendingImageCases, missingEvidenceCount, detail);
        } catch (Throwable error) {
            return new EvidenceCasesStatus(false, path, 0, 0, 0, 0, 0,
                    sanitizeReportText(error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage())));
        }
    }

    private static final class EvidenceCasesStatus {
        final boolean ready;
        final String path;
        final int totalCases;
        final int enabledCases;
        final int disabledCases;
        final int pendingImageCases;
        final int missingEvidenceCount;
        final String detail;

        EvidenceCasesStatus(boolean ready, String path, int totalCases, int enabledCases,
                            int disabledCases, int pendingImageCases, int missingEvidenceCount,
                            String detail) {
            this.ready = ready;
            this.path = path == null ? "" : path;
            this.totalCases = totalCases;
            this.enabledCases = enabledCases;
            this.disabledCases = disabledCases;
            this.pendingImageCases = pendingImageCases;
            this.missingEvidenceCount = missingEvidenceCount;
            this.detail = detail == null ? "" : detail;
        }
    }

    private String tryLoadMaaNativeLibraries(String libraryPath) {
        File libraryFile = new File(libraryPath);
        File dir = libraryFile.getParentFile();
        if (dir == null) {
            return "library parent directory missing";
        }
        String[] loadOrder = new String[]{
                "libc++_shared.so",
                "libonnxruntime.so",
                "libopencv_world4.so",
                "libfastdeploy_ppocr.so",
                "libMaaUtils.so",
                "libMaaFramework.so"
        };
        try {
            for (String name : loadOrder) {
                File file = new File(dir, name);
                if (file.isFile()) {
                    System.load(file.getAbsolutePath());
                    logger.log("maacore native load ok path=" + file.getAbsolutePath());
                }
            }
            if (!"libMaaFramework.so".equals(libraryFile.getName()) && libraryFile.isFile()) {
                System.load(libraryFile.getAbsolutePath());
                logger.log("maacore native load ok path=" + libraryFile.getAbsolutePath());
            }
            return "";
        } catch (Throwable t) {
            String message = t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage());
            return message.replace('\n', ' ').replace('\r', ' ');
        }
    }

    private String runMaaCoreOcrProbeIfReady(File frameFile, String context, int[] roi, String[] expected) {
        String resourcePath = firstExistingDirectory(ProbeConfig.MAACORE_RESOURCE_CANDIDATES);
        String libraryPath = firstExistingFile(ProbeConfig.MAACORE_LIBRARY_CANDIDATES);
        String controlUnitPath = firstExistingFile(ProbeConfig.MAACORE_CONTROL_UNIT_CANDIDATES);
        String bridgePath = firstExistingFile(ProbeConfig.MAACORE_BRIDGE_CANDIDATES);
        boolean resourceReady = resourcePath.length() > 0;
        boolean libraryReady = libraryPath.length() > 0;
        boolean controlUnitReady = controlUnitPath.length() > 0;
        boolean bridgeReady = bridgePath.length() > 0;
        boolean pipelineReady = resourceReady && deviceFileExists(resourcePath + "/pipeline/task/interception.json");
        OcrModelStatus ocrStatus = detectOcrModelStatus(resourcePath);
        boolean ocrModelReady = ocrStatus.ready;
        boolean resourcePayloadReady = resourceReady && pipelineReady && ocrModelReady;
        String nativeLoadError = "";
        boolean nativeLoadReady = false;
        if (libraryReady) {
            nativeLoadError = tryLoadMaaNativeLibraries(libraryPath);
            nativeLoadReady = nativeLoadError.length() == 0;
        }
        String bridgeReport = "";
        String bridgeError = "";
        String ocrParamJson = buildMaaCoreOcrParamJson(roi, expected);
        if (resourcePayloadReady && libraryReady && nativeLoadReady && controlUnitReady && bridgeReady
                && frameFile != null && frameFile.exists()) {
            try {
                MaaCoreNativeBridge.load(bridgePath);
                bridgeReport = MaaCoreNativeBridge.runProbe(displayId, libraryPath, controlUnitPath, resourcePath,
                        frameFile.getAbsolutePath(), ocrParamJson);
            } catch (Throwable error) {
                bridgeError = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
                bridgeError = bridgeError.replace('\n', ' ').replace('\r', ' ');
            }
        }
        String safeContext = context == null ? "" : context.replace('\n', ' ').replace('\r', ' ');
        String report = "maacore_probe_version=generic_ocr_p1\n"
                + "context=" + safeContext + "\n"
                + "displayId=" + displayId + "\n"
                + "frameFile=" + (frameFile == null ? "" : frameFile.getAbsolutePath()) + "\n"
                + "ocrParamJson=" + ocrParamJson + "\n"
                + "resourceReady=" + resourceReady + "\n"
                + "resourcePath=" + resourcePath + "\n"
                + "libraryReady=" + libraryReady + "\n"
                + "libraryPath=" + libraryPath + "\n"
                + "controlUnitReady=" + controlUnitReady + "\n"
                + "controlUnitPath=" + controlUnitPath + "\n"
                + "bridgeReady=" + bridgeReady + "\n"
                + "bridgePath=" + bridgePath + "\n"
                + "pipelineReady=" + pipelineReady + "\n"
                + "ocrModelReady=" + ocrModelReady + "\n"
                + "ocrModelType=" + ocrStatus.type + "\n"
                + "ocrModelPath=" + ocrStatus.path + "\n"
                + "ocrModelDetail=" + ocrStatus.detail + "\n"
                + "resourcePayloadReady=" + resourcePayloadReady + "\n"
                + "nativeLoadReady=" + nativeLoadReady + "\n"
                + "nativeLoadError=" + nativeLoadError + "\n"
                + "bridgeError=" + bridgeError + "\n"
                + "bridgeReportBegin\n"
                + bridgeReport
                + "bridgeReportEnd\n";
        writeTextFile(ProbeConfig.MAACORE_PROBE_REPORT_FILE, report);
        logger.log("maacore generic ocr probe context=" + safeContext
                + " ready=" + (resourcePayloadReady && libraryReady && nativeLoadReady && controlUnitReady && bridgeReady)
                + " fileHit=" + isMaaCoreOcrHit(report)
                + " bridgeError=" + bridgeError);
        return report;
    }

    private String runMaaCoreQuickBattleProbeIfReady(File frameFile, String context) {
        return runMaaCoreOcrProbeIfReady(frameFile, context, new int[]{642, 582, 193, 55},
                new String[]{"快速战斗", "每周快速战斗"});
    }

    private boolean isMaaCoreOcrHit(String report) {
        return report != null && (report.indexOf("fileHit=true") >= 0
                || report.indexOf("androidHit=true") >= 0);
    }

    private boolean probeMaaCoreOcrForLog(File frameFile, String context, int[] roi, String[] expected) {
        String report = runMaaCoreOcrProbeIfReady(frameFile, context, roi, expected);
        boolean hit = isMaaCoreOcrHit(report);
        logger.log("maacore ocr gate context=" + context
                + " hit=" + hit
                + " expected=" + joinStepsForLog(expected)
                + " roi=" + formatRoiForLog(roi));
        return hit;
    }

    private String formatRoiForLog(int[] roi) {
        if (roi == null || roi.length < 4) {
            return "full";
        }
        return roi[0] + "," + roi[1] + "," + roi[2] + "," + roi[3];
    }

    private String buildMaaCoreOcrParamJson(int[] roi, String[] expected) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"recognition\":\"OCR\"");
        if (roi != null && roi.length >= 4) {
            builder.append(",\"roi\":[")
                    .append(roi[0]).append(',')
                    .append(roi[1]).append(',')
                    .append(roi[2]).append(',')
                    .append(roi[3]).append(']');
        }
        if (expected != null && expected.length > 0) {
            builder.append(",\"expected\":[");
            boolean first = true;
            for (String item : expected) {
                if (item == null) {
                    continue;
                }
                if (!first) {
                    builder.append(',');
                }
                builder.append('"').append(escapeJson(item)).append('"');
                first = false;
            }
            builder.append(']');
        }
        builder.append('}');
        return builder.toString();
    }

    private String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') {
                builder.append('\\').append(ch);
            } else if (ch == '\n') {
                builder.append("\\n");
            } else if (ch == '\r') {
                builder.append("\\r");
            } else if (ch == '\t') {
                builder.append("\\t");
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private boolean isMaaCoreInterceptionNoAttemptsHit(String report) {
        return report != null && (report.indexOf("0/3") >= 0
                || report.indexOf("０/３") >= 0
                || report.indexOf("剩余挑战次数") >= 0
                || report.indexOf("剩余拦截次数") >= 0);
    }

    private void runClaimInterceptionTask(FrameCaptureBackend capture, InputInjector input, boolean dryRun)
            throws Exception {
        boolean manualBossOnMonday = taskOptionYes(0, false);
        String bossName = normalizeInterceptionBossName(taskOptionString(1, "克拉肯"));
        int bossIndex = interceptionBossIndex(bossName);
        logger.log("claim_interception options manualBossOnMonday=" + manualBossOnMonday
                + " bossName=" + bossName + " bossIndex=" + bossIndex);
        if (!openArkSubpage(capture, input, "interception", ProbeConfig.INTERCEPTION_ENTRY_X,
                ProbeConfig.INTERCEPTION_ENTRY_Y, "interception_entry")) {
            return;
        }
        if (manualBossOnMonday && isTodayMonday()) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            finalState = "interception_manual_boss_required";
            actionSuccess = true;
            logger.log("claim_interception stopped on Monday because manual boss option is enabled");
            return;
        }
        tap(input, ProbeConfig.INTERCEPTION_ANOMALY_X, ProbeConfig.INTERCEPTION_ANOMALY_Y,
                "interception_anomaly_tab");
        Thread.sleep(1800);
        for (int i = 0; i < bossIndex; i++) {
            tap(input, ProbeConfig.INTERCEPTION_BOSS_CHANGE_X, ProbeConfig.INTERCEPTION_BOSS_CHANGE_Y,
                    "interception_boss_change_" + bossName + "_" + (i + 1));
            Thread.sleep(900);
        }
        waitForStableScene(capture, 5, "interception_boss_detail_" + bossName);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isInterceptionTeamPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "interception_unexpected_team_page_no_sweep";
            actionSuccess = false;
            logger.log("claim_interception stopped because team page is visible before sweep detection;"
                    + " avoid challenge or formation mis-tap");
            return;
        }
        String quickBattleOcrReport = runMaaCoreQuickBattleProbeIfReady(
                ProbeConfig.TASK_AFTER_WAIT_FILE, "interception_boss_detail_" + bossName);
        boolean quickBattleOcrHit = isMaaCoreOcrHit(quickBattleOcrReport);
        boolean noAttemptsOcrHit = isMaaCoreInterceptionNoAttemptsHit(quickBattleOcrReport);
        if (dryRun) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
            boolean sweepVisible = isInterceptionQuickBattleVisible(ProbeConfig.TASK_AFTER_ACTION_FILE);
            boolean challengeVisible = isInterceptionChallengeBossVisible(ProbeConfig.TASK_AFTER_ACTION_FILE);
            boolean disabledQuickVisible = isInterceptionDisabledQuickBattleVisible(ProbeConfig.TASK_AFTER_ACTION_FILE);
            boolean teamPageVisible = isInterceptionTeamPageVisible(ProbeConfig.TASK_AFTER_ACTION_FILE);
            if (sweepVisible) {
                finalState = "interception_sweep_button_previewed";
            } else if (teamPageVisible) {
                finalState = "interception_unexpected_team_page_no_sweep";
            } else if (noAttemptsOcrHit) {
                finalState = "interception_no_remaining_attempts_previewed";
            } else if (quickBattleOcrHit) {
                finalState = "interception_quick_battle_disabled_previewed";
            } else if (challengeVisible) {
                finalState = "interception_challenge_boss_previewed_no_sweep";
            } else if (disabledQuickVisible) {
                finalState = "interception_quick_battle_disabled_no_sweep";
            } else {
                finalState = "interception_boss_previewed_no_sweep_button";
            }
            actionSuccess = !teamPageVisible;
            logger.log("debug dry-run stopped on interception boss detail before any sweep/battle tap"
                    + " sweepVisible=" + sweepVisible
                    + " challengeVisible=" + challengeVisible
                    + " disabledQuickVisible=" + disabledQuickVisible
                    + " teamPageVisible=" + teamPageVisible
                    + " maaCoreQuickBattleOcrHit=" + quickBattleOcrHit
                    + " maaCoreNoAttemptsOcrHit=" + noAttemptsOcrHit);
            return;
        }

        int sweepCount = 0;
        for (int attempt = 1; attempt <= ProbeConfig.INTERCEPTION_SWEEP_MAX_ATTEMPTS; attempt++) {
            if (isInterceptionTeamPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = "interception_unexpected_team_page_no_sweep";
                logger.log("interception sweep loop stopped because team page is visible attempt=" + attempt);
                break;
            }
            boolean sweepVisible = isInterceptionQuickBattleVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
            boolean challengeVisible = isInterceptionChallengeBossVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
            boolean disabledQuickVisible = isInterceptionDisabledQuickBattleVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
            if (!sweepVisible) {
                finalState = sweepCount > 0
                        ? "interception_quick_battle_all_attempts_used"
                        : (noAttemptsOcrHit
                                ? "interception_no_remaining_attempts"
                                : (quickBattleOcrHit
                                        ? "interception_quick_battle_disabled"
                                        : (challengeVisible
                                ? "interception_challenge_boss_visible_no_sweep"
                                : (disabledQuickVisible
                                        ? "interception_quick_battle_disabled_no_sweep"
                                        : "interception_quick_battle_not_visible"))));
                logger.log("interception sweep loop stopped before tap attempt=" + attempt
                        + " sweepVisible=false challengeVisible=" + challengeVisible
                        + " disabledQuickVisible=" + disabledQuickVisible
                        + " maaCoreQuickBattleOcrHit=" + quickBattleOcrHit
                        + " maaCoreNoAttemptsOcrHit=" + noAttemptsOcrHit);
                break;
            }
            tap(input, ProbeConfig.INTERCEPTION_SWEEP_BUTTON_X, ProbeConfig.INTERCEPTION_SWEEP_BUTTON_Y,
                    "interception_sweep_button_" + attempt);
            Thread.sleep(1800);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isInterceptionNoAttemptsVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                finalState = sweepCount > 0
                        ? "interception_quick_battle_all_attempts_used"
                        : "interception_quick_battle_no_attempts";
                logger.log("interception sweep stopped because no attempts dialog/page is visible attempt=" + attempt);
                break;
            }
            if (isInterceptionSweepConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isDownloadConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isUpdateDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isMailRewardConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.INTERCEPTION_CONFIRM_X, ProbeConfig.INTERCEPTION_CONFIRM_Y,
                        "interception_sweep_confirm_" + attempt);
                Thread.sleep(2300);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            } else {
                boolean enteredChallenge = !isInterceptionQuickBattleVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                        && !isInterceptionChallengeBossVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                        && !isInterceptionSweepConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
                finalState = enteredChallenge
                        ? "interception_possible_challenge_boss_mistap"
                        : "interception_sweep_confirm_missing";
                logger.log("interception sweep confirm not detected after sweep tap attempt=" + attempt
                        + " enteredChallengeSuspected=" + enteredChallenge);
                break;
            }
            if (isInterceptionRewardConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isMailRewardConfirmVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.INTERCEPTION_REWARD_CONFIRM_X, ProbeConfig.INTERCEPTION_REWARD_CONFIRM_Y,
                        "interception_reward_confirm_" + attempt);
                Thread.sleep(1800);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            } else {
                logger.log("interception reward confirm not visible after sweep confirm attempt=" + attempt);
            }
            sweepCount++;
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ACTION_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ACTION_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (finalState == null || finalState.length() == 0 || finalState.startsWith("workflow_running")) {
            finalState = sweepCount > 0
                    ? "interception_quick_battle_swept_" + sweepCount
                    : "interception_quick_battle_not_available";
        }
        actionSuccess = true;
        logger.log("claim_interception completed with anomaly/boss/quick-battle loop bossName="
                + bossName + " sweepCount=" + sweepCount + " finalState=" + finalState);
    }

    private boolean isTodayMonday() {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
    }

    private String normalizeInterceptionBossName(String value) {
        if (value == null) {
            return "克拉肯";
        }
        String trimmed = value.trim();
        if ("镜像容器".equals(trimmed)
                || "茵迪维利亚".equals(trimmed)
                || "过激派".equals(trimmed)
                || "死神".equals(trimmed)
                || "克拉肯".equals(trimmed)) {
            return trimmed;
        }
        logger.log("unknown interception boss option value=" + value + ", fallback to 克拉肯");
        return "克拉肯";
    }

    private int interceptionBossIndex(String bossName) {
        String[] order = new String[]{"克拉肯", "镜像容器", "茵迪维利亚", "过激派", "死神"};
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(bossName)) {
                return i;
            }
        }
        return 0;
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
        if (finishClimbTowerDryRunIfVisible(capture, dryRun, "after_open")) {
            return;
        }
        int attempted = 0;
        boolean battleFailedRetry = false;
        if (unlimitedEnabled) {
            int prepareState = prepareClimbTowerChoicePage(capture, input, "unlimited", dryRun);
            if (prepareState == CLIMB_TOWER_PREPARE_PREVIEWED) {
                finalState = "climb_tower_battle_previewed";
                actionSuccess = true;
                logger.log("debug dry-run stopped on climb tower preview before unlimited entry");
                return;
            }
            if (prepareState != CLIMB_TOWER_PREPARE_READY) {
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
            int prepareState = prepareClimbTowerChoicePage(capture, input, "company", dryRun);
            if (prepareState == CLIMB_TOWER_PREPARE_PREVIEWED) {
                finalState = "climb_tower_battle_previewed";
                actionSuccess = true;
                logger.log("debug dry-run stopped on climb tower preview before company entry");
                return;
            }
            if (prepareState != CLIMB_TOWER_PREPARE_READY) {
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
                    if (prepareClimbTowerChoicePage(capture, input, "company_next", false)
                            != CLIMB_TOWER_PREPARE_READY) {
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

    private static final int CLIMB_TOWER_PREPARE_NOT_READY = 0;
    private static final int CLIMB_TOWER_PREPARE_READY = 1;
    private static final int CLIMB_TOWER_PREPARE_PREVIEWED = 2;

    private boolean finishClimbTowerDryRunIfVisible(FrameCaptureBackend capture, boolean dryRun, String stage)
            throws Exception {
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!dryRun) {
            return false;
        }
        if (isClimbTowerDetailVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                || isClimbTowerPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);
            finalState = "climb_tower_battle_previewed";
            actionSuccess = true;
            logger.log("debug dry-run stopped on climb tower page before fight entry stage=" + stage);
            return true;
        }
        Thread.sleep(900);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isClimbTowerDetailVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                || isClimbTowerPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);
            finalState = "climb_tower_battle_previewed";
            actionSuccess = true;
            logger.log("debug dry-run stopped on climb tower page after settle stage=" + stage);
            return true;
        }
        return false;
    }

    private boolean runOneClimbTowerEntry(FrameCaptureBackend capture, InputInjector input, int x, int y,
                                          String label, boolean dryRun) throws Exception {
        tap(input, x, y, label + "_entry");
        Thread.sleep(2300);
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
            Thread.sleep(1500);
        }
        return true;
    }

    private int prepareClimbTowerChoicePage(FrameCaptureBackend capture, InputInjector input, String target,
                                           boolean dryRun)
            throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isClimbTowerChoiceVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("climb tower choice page ready target=" + target + " attempt=" + attempt);
                return CLIMB_TOWER_PREPARE_READY;
            }
            if (isClimbTowerDetailVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isClimbTowerPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                if (dryRun) {
                    copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_AFTER_ACTION_FILE);
                    logger.log("climb tower dry-run preview ready target=" + target + " attempt=" + attempt);
                    return CLIMB_TOWER_PREPARE_PREVIEWED;
                }
                if (attempt == 1 || isClimbTowerDetailVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    tap(input, ProbeConfig.CLIMB_TOWER_CHOICE_BACK_X, ProbeConfig.CLIMB_TOWER_CHOICE_BACK_Y,
                            "climb_tower_choice_back_" + target + "_" + attempt);
                    Thread.sleep(1600);
                    capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
                    copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
                    if (isClimbTowerChoiceVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                        logger.log("climb tower returned to choice by bottom-left back target="
                                + target + " attempt=" + attempt);
                        return CLIMB_TOWER_PREPARE_READY;
                    }
                }
                tap(input, ProbeConfig.CLIMB_TOWER_SELECTOR_X, ProbeConfig.CLIMB_TOWER_SELECTOR_Y,
                        "climb_tower_selector_" + target + "_" + attempt);
            } else if (isArkHubVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tap(input, ProbeConfig.CLIMB_TOWER_ENTRY_X, ProbeConfig.CLIMB_TOWER_ENTRY_Y,
                        "climb_tower_reopen_from_ark_" + target + "_" + attempt);
            } else {
                tap(input, ProbeConfig.CLIMB_TOWER_SELECTOR_X, ProbeConfig.CLIMB_TOWER_SELECTOR_Y,
                        "climb_tower_selector_guess_" + target + "_" + attempt);
            }
            Thread.sleep(attempt == 1 ? 1800 : 1400);
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        boolean ready = isClimbTowerChoiceVisible(ProbeConfig.TASK_AFTER_WAIT_FILE);
        logger.log("climb tower choice final target=" + target + " ready=" + ready);
        return ready ? CLIMB_TOWER_PREPARE_READY : CLIMB_TOWER_PREPARE_NOT_READY;
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
        boolean mailPageOcrHit = probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE,
                "claimmail.checkmail", new int[]{569, 55, 159, 97},
                new String[]{"邮箱"});

        if (!pageOpened && !isMailPageVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE) && !mailPageOcrHit) {
            finalState = "mail_page_not_detected";
            logger.log("claim_mail mail page not detected after tapping mail icon");
            returnToHomeAfterMail(capture, input);
            actionSuccess = false;
            return;
        }

        boolean claimVisible = isMailClaimButtonVisible(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE);
        boolean claimOcrHit = probeMaaCoreOcrForLog(ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE,
                "claimmail.claimthings", new int[]{593, 541, 252, 135},
                new String[]{"全部领取"});
        logger.log("claim_mail claim gate claimVisible=" + claimVisible
                + " claimOcrHit=" + claimOcrHit);
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
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (!isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            logger.log("mail open guard found non-home page label=" + label + ", trying home convergence");
            if (isEventRewardPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapLeftBottomHome(capture, input, "mail_guard_event_home_" + label);
            } else if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapInquiryHome(capture, input, "mail_guard_inquiry_home_" + label);
            } else if (isKnownBottomHomePageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                tapBottomHome(capture, input, "mail_guard_bottom_home_" + label);
            } else if (isHomeNoticeListVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)
                    || isHomePopupVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                closeHomePopupIfVisible(capture, input);
            }
            if (!isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                runBackToHomeTask(capture, input, false);
            }
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
            copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (!isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                logger.log("mail open guard could not confirm home label=" + label
                        + " state=" + finalState);
                copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_AFTER_MAIL_OPEN_FILE);
                return false;
            }
        }
        int[][] candidates = new int[][]{
                {ProbeConfig.MAIL_ICON_X, ProbeConfig.MAIL_ICON_Y},
                {1228, 26},
                {1226, 26},
                {1220, 28},
                {1230, 32}
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
            if (second % 5 == 0 || capture.getLastNonZeroSamples() > ProbeConfig.STABLE_FRAME_MIN_NONZERO_SAMPLES) {
                writeResult(capture, "waiting_" + second + "s");
                logger.log("wait seconds=" + second
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples());
            }
            if (capture.getLastNonZeroSamples() > ProbeConfig.STABLE_FRAME_MIN_NONZERO_SAMPLES) {
                return waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS, "wait_useful_frame");
            }
        }
        return false;
    }

    private void recoverBlackVirtualDisplayIfNeeded(FrameCaptureBackend capture, String normalizedTaskName)
            throws Exception {
        if (capture.getNonBlackFrameCount() > 0
                && capture.getLastNonZeroSamples() > ProbeConfig.STABLE_FRAME_MIN_NONZERO_SAMPLES) {
            return;
        }
        if (waitForAnyUsefulFrame(capture, 5)) {
            return;
        }
        writeResult(capture, "preview_force_restart_display");
        if ("start_game".equals(normalizedTaskName) || "smoke".equals(normalizedTaskName)) {
            logger.log("virtual display is still black; force starting game on display=" + displayId);
            launcher.startOnDisplay(displayId);
        } else {
            logger.log("virtual display is still black; bring running game to display without force start task="
                    + normalizedTaskName + " display=" + displayId);
            launcher.bringToDisplay(displayId);
        }
        capture.awaitFirstFrame(5, TimeUnit.SECONDS);
        waitForAnyUsefulFrame(capture, "start_game".equals(normalizedTaskName) ? 18 : 12);
    }

    private boolean waitForAnyUsefulFrame(FrameCaptureBackend capture, int timeoutSeconds) throws Exception {
        int stableStreak = 0;
        for (int second = 1; second <= timeoutSeconds; second++) {
            Thread.sleep(1000);
            if (second % 5 == 0 || capture.getNonBlackFrameCount() > 0) {
                writeResult(capture, "waiting_" + second + "s");
                logger.log("wait any seconds=" + second
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples());
            }
            if (capture.getNonBlackFrameCount() > 0
                    && capture.getLastNonZeroSamples() > ProbeConfig.STABLE_FRAME_MIN_NONZERO_SAMPLES) {
                stableStreak++;
                if (stableStreak >= ProbeConfig.STABLE_FRAME_REQUIRED_STREAK) {
                    return true;
                }
            } else {
                stableStreak = 0;
            }
        }
        return false;
    }

    private boolean waitForStableScene(FrameCaptureBackend capture, int timeoutSeconds, String reason)
            throws Exception {
        int stableStreak = 0;
        long lastFrameCount = -1;
        long lastNonZeroSamples = -1;
        for (int second = 1; second <= timeoutSeconds; second++) {
            Thread.sleep(1000);
            long frameCount = capture.getFrameCount();
            boolean frameAdvanced = frameCount > lastFrameCount;
            lastFrameCount = frameCount;
            long nonZeroSamples = capture.getLastNonZeroSamples();
            boolean similarToLast = lastNonZeroSamples >= 0
                    && Math.abs(nonZeroSamples - lastNonZeroSamples) <= 220;
            lastNonZeroSamples = nonZeroSamples;
            boolean stableCandidate = capture.getNonBlackFrameCount() > 0
                    && nonZeroSamples > ProbeConfig.STABLE_FRAME_MIN_NONZERO_SAMPLES
                    && (frameAdvanced || similarToLast);
            if (stableCandidate) {
                stableStreak++;
            } else {
                stableStreak = 0;
            }
            logger.log("stable scene wait reason=" + reason
                    + " second=" + second
                    + " frames=" + frameCount
                    + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                    + " lastNonZeroSamples=" + nonZeroSamples
                    + " frameAdvanced=" + frameAdvanced
                    + " similarToLast=" + similarToLast
                    + " stableStreak=" + stableStreak);
            if (stableStreak >= ProbeConfig.STABLE_FRAME_REQUIRED_STREAK) {
                return true;
            }
        }
        return false;
    }

    private void tap(InputInjector input, int x, int y, String label) throws Exception {
        logger.log("tap label=" + label + " x=" + x + " y=" + y + " displayId=" + displayId);
        boolean down = input.injectTouch(MotionEvent.ACTION_DOWN, x, y, displayId, true);
        Thread.sleep(ProbeConfig.TOUCH_DOWN_UP_MS);
        boolean up = input.injectTouch(MotionEvent.ACTION_UP, x, y, displayId, false);
        actionCount++;
        logger.log("tap result label=" + label + " down=" + down + " up=" + up);
        Thread.sleep(ProbeConfig.TAP_SETTLE_MS);
    }

    private void pressBack(InputInjector input, String label) throws Exception {
        logger.log("key label=" + label + " keyCode=BACK displayId=" + displayId);
        boolean down = input.injectKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, displayId, true);
        Thread.sleep(ProbeConfig.KEY_DOWN_UP_MS);
        boolean up = input.injectKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, displayId, false);
        actionCount++;
        logger.log("key result label=" + label + " down=" + down + " up=" + up);
        Thread.sleep(ProbeConfig.KEY_SETTLE_MS);
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
        boolean startPageBackAttempted = false;
        boolean downloadConfirmed = false;
        boolean updateConfirmed = false;
        for (int second = 1; second <= ProbeConfig.START_GAME_WAIT_SECONDS; second++) {
            Thread.sleep(1000);
            if (second % 10 == 0) {
                waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS,
                        "wait_for_game_load_" + second);
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
                } else if (isInquiryPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    tapInquiryHome(capture, input, "loading_inquiry_home_" + second);
                } else if (isEventRewardPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    tapLeftBottomHome(capture, input, "loading_event_home_" + second);
                } else if (isKnownBottomHomePageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    tapBottomHome(capture, input, "loading_bottom_home_" + second);
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
                if (isStartPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
                    boolean handledByBackFallback = false;
                    if (!startPageBackAttempted && enterAttempts >= 2) {
                        startPageBackAttempted = tryStartPageBackFallback(capture, input,
                                "during_wait_" + second);
                        handledByBackFallback = startPageBackAttempted;
                    }
                    if ("home_clear".equals(finalState)
                            || "network_retry_required".equals(finalState)
                            || "login_required".equals(finalState)
                            || (finalState != null && finalState.startsWith("client_update_external"))) {
                        return;
                    }
                    if (!handledByBackFallback) {
                        logger.log("start page still visible during wait, tap enter game again");
                        tap(input, ProbeConfig.ENTER_GAME_X, ProbeConfig.ENTER_GAME_Y,
                                "enter_game_retry_during_wait_" + second);
                        Thread.sleep(1600);
                        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ENTER_FILE);
                        copyFile(ProbeConfig.TASK_AFTER_ENTER_FILE, ProbeConfig.TASK_FRAME_FILE);
                        if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                            closeAnnouncementDialog(capture, input, "during_wait_retry");
                        }
                    }
                    continue;
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
                waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS,
                        "enter_game_candidate_" + enterAttempts);
                capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ENTER_FILE);
                copyFile(ProbeConfig.TASK_AFTER_ENTER_FILE, ProbeConfig.TASK_FRAME_FILE);
                if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                    logger.log("announcement dialog returned during enter wait");
                    closeAnnouncementDialog(capture, input, "during_enter");
                } else if (isStartPageVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                    if (!startPageBackAttempted && enterAttempts >= 2) {
                        startPageBackAttempted = tryStartPageBackFallback(capture, input,
                                "after_enter_attempt_" + enterAttempts);
                    } else {
                        logger.log("start page still visible after enter attempt, continue waiting");
                    }
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

    private boolean tryStartPageBackFallback(FrameCaptureBackend capture, InputInjector input, String reason)
            throws Exception {
        logger.log("start page fallback: press back once before another enter attempt reason=" + reason);
        pressBack(input, "start_page_back_fallback_" + reason);
        Thread.sleep(900);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_BACK_FILE);
        copyFile(ProbeConfig.TASK_AFTER_BACK_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isExitGameConfirmVisible(ProbeConfig.TASK_AFTER_BACK_FILE)) {
            logger.log("start page fallback opened exit confirm, cancel it reason=" + reason);
            cancelExitGameConfirm(capture, input);
        }
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "home_clear";
            actionSuccess = true;
            return true;
        }
        if (isNetworkRetryDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "network_retry_required";
            logger.log("network retry dialog detected after start-page back fallback reason=" + reason);
            return true;
        }
        if (isLoginPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "login_required";
            logger.log("login page detected after start-page back fallback reason=" + reason);
            return true;
        }
        if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            closeAnnouncementDialog(capture, input, "start_page_back_fallback_" + reason);
            return true;
        }
        if (isStartPageVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            tap(input, ProbeConfig.ENTER_GAME_X, ProbeConfig.ENTER_GAME_Y,
                    "enter_game_after_back_fallback_" + reason);
            Thread.sleep(1800);
            capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ENTER_FILE);
            copyFile(ProbeConfig.TASK_AFTER_ENTER_FILE, ProbeConfig.TASK_FRAME_FILE);
            if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
                closeAnnouncementDialog(capture, input, "after_back_fallback_enter_" + reason);
            }
        }
        return true;
    }

    private boolean enterGameFromStartPage(FrameCaptureBackend capture, InputInjector input, String reason)
            throws Exception {
        tap(input, ProbeConfig.ENTER_GAME_X, ProbeConfig.ENTER_GAME_Y,
                "enter_game_from_start_page_" + reason);
        Thread.sleep(2200);
        waitForStableScene(capture, ProbeConfig.STABLE_SCENE_WAIT_SECONDS,
                "enter_game_from_start_page_" + reason);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_ENTER_FILE);
        copyFile(ProbeConfig.TASK_AFTER_ENTER_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isAnnouncementDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
            logger.log("start page enter returned announcement dialog reason=" + reason);
            closeAnnouncementDialog(capture, input, "start_page_" + reason);
        } else if (isNetworkRetryDialogVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
            finalState = "network_retry_required";
            logger.log("network retry dialog detected after start page enter reason=" + reason);
            return true;
        } else if (isLoginPageVisible(ProbeConfig.TASK_AFTER_ENTER_FILE)) {
            finalState = "login_required";
            logger.log("login page detected after start page enter reason=" + reason);
            return true;
        }
        waitForGameLoadOrEnter(capture, input);
        return true;
    }

    private String getForegroundPackageOnTargetDisplay() {
        if (environment == null || displayId < 0) {
            return "";
        }
        try {
            String output = environment.runCommandForOutput(
                    "dumpsys activity activities | grep -A 120 'Display #" + displayId + "' || true");
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
        int packageNameIndex = text.indexOf("packageName=");
        if (packageNameIndex >= 0) {
            int start = packageNameIndex + "packageName=".length();
            int end = start;
            while (end < text.length()) {
                char ch = text.charAt(end);
                if (Character.isWhitespace(ch) || ch == '/' || ch == ',' || ch == '}' || ch == ')') {
                    break;
                }
                end++;
            }
            if (end > start) {
                return text.substring(start, end).trim();
            }
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
        if (isInquiryPageVisible(currentFrame)) {
            tapInquiryHome(capture, input, "home_popup_inquiry_home");
            return;
        }
        if (isKnownBottomHomePageVisible(currentFrame)) {
            tapBottomHome(capture, input, "home_popup_bottom_home");
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

    private void tapInquiryHome(FrameCaptureBackend capture, InputInjector input, String label) throws Exception {
        logger.log("inquiry page detected, tapping bottom home label=" + label);
        tap(input, ProbeConfig.INQUIRY_HOME_X, ProbeConfig.INQUIRY_HOME_Y, label);
        Thread.sleep(1500);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "home_clear";
        } else {
            finalState = "inquiry_home_attempted";
        }
    }

    private void tapBottomHome(FrameCaptureBackend capture, InputInjector input, String label) throws Exception {
        logger.log("known bottom-nav page detected, tapping home label=" + label);
        tap(input, ProbeConfig.BOTTOM_HOME_X, ProbeConfig.BOTTOM_HOME_Y, label);
        Thread.sleep(1500);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "home_clear";
        } else {
            finalState = "bottom_home_attempted";
        }
    }

    private void tapLeftBottomHome(FrameCaptureBackend capture, InputInjector input, String label) throws Exception {
        logger.log("left-bottom home button detected, tapping home label=" + label);
        tap(input, ProbeConfig.LEFT_BOTTOM_HOME_X, ProbeConfig.LEFT_BOTTOM_HOME_Y, label);
        Thread.sleep(1800);
        capture.copyLatestFrameTo(ProbeConfig.TASK_AFTER_WAIT_FILE);
        copyFile(ProbeConfig.TASK_AFTER_WAIT_FILE, ProbeConfig.TASK_FRAME_FILE);
        if (isHomeClearVisible(ProbeConfig.TASK_AFTER_WAIT_FILE)) {
            finalState = "home_clear";
        } else {
            finalState = "left_bottom_home_attempted";
        }
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
            int variantCenterBrightHits = countBrightPanelSamples(bitmap, 360, 60, 930, 620);
            int variantLeftDarkHits = countDarkPanelSamples(bitmap, 0, 0, 250, 719);
            int variantRightDarkHits = countDarkPanelSamples(bitmap, 1030, 0, 1279, 719);
            int variantBottomTextHits = countBrightPanelSamples(bitmap, 520, 620, 760, 715);
            int variantBottomTextLooseHits = countBrightPanelSamples(bitmap, 520, 620, 760, 715, 5, 140);
            int variantBottomNavBrightHits = countBrightPanelSamples(bitmap, 480, 620, 800, 719, 5, 140);
            boolean classicVisible = bottomHits >= 18 && rightHits >= 7 && featureHits >= 30 && blueHits >= 25;
            boolean variantVisible = bottomHits >= 12
                    && variantCenterBrightHits >= 1200
                    && variantLeftDarkHits >= 900
                    && variantRightDarkHits >= 900
                    && variantBottomTextHits >= 40;
            boolean posterLobbyVisible = bottomHits >= 12
                    && variantCenterBrightHits >= 1800
                    && variantLeftDarkHits >= 900
                    && variantRightDarkHits >= 900
                    && variantBottomTextLooseHits >= 140
                    && variantBottomNavBrightHits >= 180;
            boolean visible = classicVisible || variantVisible || posterLobbyVisible;
            logger.log("home clear detect file=" + frameFile.getName()
                    + " bottomHits=" + bottomHits
                    + " rightHits=" + rightHits
                    + " featureHits=" + featureHits
                    + " blueHits=" + blueHits
                    + " variantCenterBrightHits=" + variantCenterBrightHits
                    + " variantLeftDarkHits=" + variantLeftDarkHits
                    + " variantRightDarkHits=" + variantRightDarkHits
                    + " variantBottomTextHits=" + variantBottomTextHits
                    + " variantBottomTextLooseHits=" + variantBottomTextLooseHits
                    + " variantBottomNavBrightHits=" + variantBottomNavBrightHits
                    + " classicVisible=" + classicVisible
                    + " variantVisible=" + variantVisible
                    + " posterLobbyVisible=" + posterLobbyVisible
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

    private boolean isKnownBottomHomePageVisible(File frameFile) {
        boolean visible = isSimRoomPageVisible(frameFile)
                || isClimbTowerPageVisible(frameFile)
                || isClimbTowerDetailVisible(frameFile)
                || isInterceptionPageVisible(frameFile)
                || isNikkesListPageVisible(frameFile);
        logger.log("known bottom-home page detect file=" + frameFile.getName() + " visible=" + visible);
        return visible;
    }

    private boolean isInterceptionPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int orangeFrameHits = countOrangePanelSamples(bitmap, 430, 70, 850, 450);
            int centerPanelHits = countBrightPanelSamples(bitmap, 430, 300, 850, 620);
            int lowerDarkHits = countDarkPanelSamples(bitmap, 430, 460, 850, 590);
            int battleButtonHits = countBlueButtonSamples(bitmap, 440, 585, 650, 635);
            boolean classicVisible = bottomBackHits >= 35
                    && orangeFrameHits >= 80
                    && lowerDarkHits >= 350
                    && (centerPanelHits >= 100 || battleButtonHits >= 50);
            boolean strongBattleVisible = bottomBackHits >= 35
                    && lowerDarkHits >= 350
                    && centerPanelHits >= 120
                    && battleButtonHits >= 80;
            boolean visible = classicVisible || strongBattleVisible;
            logger.log("interception page detect file=" + frameFile.getName()
                    + " bottomBackHits=" + bottomBackHits
                    + " orangeFrameHits=" + orangeFrameHits
                    + " centerPanelHits=" + centerPanelHits
                    + " lowerDarkHits=" + lowerDarkHits
                    + " battleButtonHits=" + battleButtonHits
                    + " classicVisible=" + classicVisible
                    + " strongBattleVisible=" + strongBattleVisible
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInterceptionQuickBattleVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception quick battle detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int quickButtonHits = countOrangePanelSamples(bitmap, 642, 582, 835, 637);
            int weeklySweepHits = countInterceptionWeeklySweepButtonSamples(bitmap);
            int challengeHits = countRedPanelSamples(bitmap, 642, 637, 835, 700);
            int centerPanelHits = countBrightPanelSamples(bitmap, 430, 300, 850, 620);
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            boolean visible = quickButtonHits >= 45
                    && weeklySweepHits >= 40
                    && challengeHits < 120
                    && (centerPanelHits >= 100 || bottomBackHits >= 20);
            logger.log("interception quick battle detect file=" + frameFile.getName()
                    + " quickButtonHits=" + quickButtonHits
                    + " weeklySweepHits=" + weeklySweepHits
                    + " challengeHits=" + challengeHits
                    + " centerPanelHits=" + centerPanelHits
                    + " bottomBackHits=" + bottomBackHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInterceptionChallengeBossVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception challenge boss detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int challengeHits = countRedPanelSamples(bitmap, 642, 637, 835, 700);
            int lowerWarningHits = countRedPanelSamples(bitmap, 650, 690, 830, 715);
            int centerPanelHits = countBrightPanelSamples(bitmap, 430, 300, 850, 620);
            boolean visible = challengeHits >= 80 && centerPanelHits >= 80;
            logger.log("interception challenge boss detect file=" + frameFile.getName()
                    + " challengeHits=" + challengeHits
                    + " lowerWarningHits=" + lowerWarningHits
                    + " centerPanelHits=" + centerPanelHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInterceptionDisabledQuickBattleVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception disabled quick battle detect skipped, cannot decode "
                    + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int quickButtonHits = countOrangePanelSamples(bitmap, 642, 582, 835, 637);
            int weeklySweepHits = countInterceptionWeeklySweepButtonSamples(bitmap);
            int quickBrightHits = countBrightPanelSamples(bitmap, 642, 582, 835, 637);
            int quickDarkHits = countDarkPanelSamples(bitmap, 642, 582, 835, 637);
            int challengeHits = countRedPanelSamples(bitmap, 642, 637, 835, 700);
            int centerPanelHits = countBrightPanelSamples(bitmap, 430, 300, 850, 620);
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            boolean visible = bottomBackHits >= 35
                    && centerPanelHits >= 100
                    && quickBrightHits >= 55
                    && quickDarkHits >= 25
                    && quickButtonHits < 25
                    && weeklySweepHits < 25
                    && challengeHits < 45;
            logger.log("interception disabled quick battle detect file=" + frameFile.getName()
                    + " quickButtonHits=" + quickButtonHits
                    + " weeklySweepHits=" + weeklySweepHits
                    + " quickBrightHits=" + quickBrightHits
                    + " quickDarkHits=" + quickDarkHits
                    + " challengeHits=" + challengeHits
                    + " centerPanelHits=" + centerPanelHits
                    + " bottomBackHits=" + bottomBackHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInterceptionTeamPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception team page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int saveButtonHits = countBlueButtonSamples(bitmap, 1160, 660, 1275, 710);
            int autoButtonHits = countBlueButtonSamples(bitmap, 1060, 660, 1170, 710);
            int centerWhiteHits = countBrightPanelSamples(bitmap, 360, 250, 930, 650);
            int teamTabHits = countBrightPanelSamples(bitmap, 455, 275, 825, 330);
            int lowerDarkHits = countDarkPanelSamples(bitmap, 430, 460, 850, 590);
            boolean visible = bottomBackHits >= 35
                    && saveButtonHits >= 25
                    && autoButtonHits >= 20
                    && centerWhiteHits >= 900
                    && lowerDarkHits < 80;
            logger.log("interception team page detect file=" + frameFile.getName()
                    + " bottomBackHits=" + bottomBackHits
                    + " saveButtonHits=" + saveButtonHits
                    + " autoButtonHits=" + autoButtonHits
                    + " centerWhiteHits=" + centerWhiteHits
                    + " teamTabHits=" + teamTabHits
                    + " lowerDarkHits=" + lowerDarkHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInterceptionSweepConfirmVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception sweep confirm detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int panelHits = countBrightPanelSamples(bitmap, 450, 250, 835, 535);
            int confirmHits = countBlueButtonSamples(bitmap, 650, 430, 830, 480)
                    + countBlueButtonSamples(bitmap, 650, 535, 830, 610);
            boolean visible = darkHits >= 80 && panelHits >= 45 && confirmHits >= 14;
            logger.log("interception sweep confirm detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " panelHits=" + panelHits
                    + " confirmHits=" + confirmHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInterceptionRewardConfirmVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception reward confirm detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int titleHits = countMailRewardTitleSamples(bitmap);
            int itemHits = countMailRewardItemSamples(bitmap);
            int centerPanelHits = countBrightPanelSamples(bitmap, 390, 160, 890, 575);
            boolean visible = darkHits >= 40
                    && ((titleHits >= 8 && itemHits >= 8) || centerPanelHits >= 80);
            logger.log("interception reward confirm detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " titleHits=" + titleHits
                    + " itemHits=" + itemHits
                    + " centerPanelHits=" + centerPanelHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInterceptionNoAttemptsVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("interception no attempts detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int panelHits = countBrightPanelSamples(bitmap, 440, 240, 840, 500);
            int quickButtonHits = countBlueButtonSamples(bitmap, 640, 580, 850, 635);
            int confirmHits = countBlueButtonSamples(bitmap, 650, 430, 830, 480)
                    + countBlueButtonSamples(bitmap, 650, 535, 830, 610);
            boolean visible = darkHits >= 70 && panelHits >= 45 && quickButtonHits < 8 && confirmHits >= 8;
            logger.log("interception no attempts detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " panelHits=" + panelHits
                    + " quickButtonHits=" + quickButtonHits
                    + " confirmHits=" + confirmHits
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

    private boolean isStartPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("start page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int titleHits = countLoginTitleSamples(bitmap);
            int bottomGlowHits = countStartPageBottomGlowSamples(bitmap);
            int lowerTextHits = countBrightPanelSamples(bitmap, 520, 575, 760, 635);
            int leftMenuHits = countBrightPanelSamples(bitmap, 0, 70, 90, 360);
            int centerTitleHits = countBrightPanelSamples(bitmap, 560, 330, 725, 420);
            int leftMenuStrongHits = countBrightPanelSamples(bitmap, 5, 60, 70, 360, 4, 150);
            int centerLogoHits = countBrightPanelSamples(bitmap, 560, 300, 720, 430, 4, 170);
            int nikkesCardHits = countNikkesCardSamples(bitmap);
            int bottomNavHits = countNikkesBottomNavigationSamples(bitmap);
            boolean classicStartVisible = titleHits >= 100 && bottomGlowHits >= 8 && lowerTextHits >= 12;
            boolean announcementStartVisible = leftMenuHits >= 12 && centerTitleHits >= 40 && titleHits >= 60;
            boolean darkSideStartVisible = leftMenuStrongHits >= 60
                    && centerLogoHits >= 180
                    && titleHits >= 90
                    && bottomGlowHits <= 6;
            boolean nikkesListLike = nikkesCardHits >= 180 && bottomNavHits >= 30;
            boolean visible = !nikkesListLike
                    && (classicStartVisible || announcementStartVisible || darkSideStartVisible);
            logger.log("start page detect file=" + frameFile.getName()
                    + " titleHits=" + titleHits
                    + " bottomGlowHits=" + bottomGlowHits
                    + " lowerTextHits=" + lowerTextHits
                    + " leftMenuHits=" + leftMenuHits
                    + " centerTitleHits=" + centerTitleHits
                    + " leftMenuStrongHits=" + leftMenuStrongHits
                    + " centerLogoHits=" + centerLogoHits
                    + " nikkesCardHits=" + nikkesCardHits
                    + " bottomNavHits=" + bottomNavHits
                    + " nikkesListLike=" + nikkesListLike
                    + " classicStartVisible=" + classicStartVisible
                    + " announcementStartVisible=" + announcementStartVisible
                    + " darkSideStartVisible=" + darkSideStartVisible
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

    private boolean isEventRewardPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("event reward page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int backBlueHits = countBlueButtonSamples(bitmap, 0, 650, 90, 715);
            int homeBlueHits = countBlueButtonSamples(bitmap, 86, 650, 140, 715);
            int pinkHeaderHits = countPinkPanelSamples(bitmap, 480, 35, 805, 190);
            int pinkClaimHits = countPinkPanelSamples(bitmap, 635, 635, 815, 705);
            int rewardGridHits = countBrightPanelSamples(bitmap, 495, 185, 800, 655);
            int rightHomeMenuHits = countHomeRightMenuSamples(bitmap);
            boolean leftHomeButtonsVisible = backBlueHits >= 24 && homeBlueHits >= 12;
            boolean eventPinkVisible = pinkHeaderHits >= 55 || pinkClaimHits >= 30;
            boolean rewardBoardVisible = rewardGridHits >= 180;
            boolean visible = leftHomeButtonsVisible
                    && eventPinkVisible
                    && rewardBoardVisible
                    && rightHomeMenuHits < 20;
            logger.log("event reward page detect file=" + frameFile.getName()
                    + " backBlueHits=" + backBlueHits
                    + " homeBlueHits=" + homeBlueHits
                    + " pinkHeaderHits=" + pinkHeaderHits
                    + " pinkClaimHits=" + pinkClaimHits
                    + " rewardGridHits=" + rewardGridHits
                    + " rightHomeMenuHits=" + rightHomeMenuHits
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

    private boolean isShopPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("shop page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int whiteBodyHits = countBrightPanelSamples(bitmap, 60, 250, 1260, 700);
            int orangeTabHits = countOrangePanelSamples(bitmap, 0, 250, 60, 580);
            int bottomBlueHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int itemCardHits = countBrightPanelSamples(bitmap, 70, 340, 1150, 520);
            int redListHits = countRedPanelSamples(bitmap, 60, 190, 1210, 620);
            int homeBottomHits = countHomeBottomNavigationSamples(bitmap);
            int homeRightHits = countHomeRightMenuSamples(bitmap);
            int homeFeatureHits = countHomeFeatureEntrySamples(bitmap);
            boolean homeVisible = homeBottomHits >= 18 && homeRightHits >= 7 && homeFeatureHits >= 30;
            boolean classicVisible = whiteBodyHits >= 800
                    && orangeTabHits >= 30
                    && bottomBlueHits >= 20
                    && itemCardHits >= 180;
            boolean whiteShopVisible = redListHits < 120
                    && whiteBodyHits >= 1800
                    && itemCardHits >= 550;
            boolean visible = classicVisible || whiteShopVisible;
            logger.log("shop page detect file=" + frameFile.getName()
                    + " whiteBodyHits=" + whiteBodyHits
                    + " orangeTabHits=" + orangeTabHits
                    + " bottomBlueHits=" + bottomBlueHits
                    + " itemCardHits=" + itemCardHits
                    + " redListHits=" + redListHits
                    + " homeVisible=" + homeVisible
                    + " classicVisible=" + classicVisible
                    + " whiteShopVisible=" + whiteShopVisible
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInquiryDetailPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("inquiry detail detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int cardHits = countBrightPanelSamples(bitmap, 430, 360, 850, 660);
            int giftButtonHits = countOrangePanelSamples(bitmap, 460, 500, 640, 630);
            int affectionRedHits = countRedPanelSamples(bitmap, 450, 370, 835, 500);
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int bottomRedHits = countRedPanelSamples(bitmap, 660, 635, 830, 690);
            boolean visible = cardHits >= 360
                    && bottomBackHits >= 18
                    && (giftButtonHits >= 80 || affectionRedHits >= 45 || bottomRedHits >= 20);
            logger.log("inquiry detail detect file=" + frameFile.getName()
                    + " cardHits=" + cardHits
                    + " giftButtonHits=" + giftButtonHits
                    + " affectionRedHits=" + affectionRedHits
                    + " bottomBackHits=" + bottomBackHits
                    + " bottomRedHits=" + bottomRedHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInquiryListPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("inquiry list detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int whiteBodyHits = countBrightPanelSamples(bitmap, 60, 150, 1260, 700);
            int redListHits = countRedPanelSamples(bitmap, 60, 190, 1210, 620);
            int todayButtonHits = countBlueButtonSamples(bitmap, 340, 240, 450, 285)
                    + countBlueButtonSamples(bitmap, 1120, 240, 1220, 285);
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int topWhiteHits = countStrictWhiteSamples(bitmap, 450, 110, 840, 180);
            int shopTabHits = countOrangePanelSamples(bitmap, 0, 250, 60, 580);
            boolean visible = whiteBodyHits >= 1800
                    && redListHits >= 180
                    && todayButtonHits >= 12
                    && bottomBackHits >= 35
                    && topWhiteHits >= 80
                    && shopTabHits < 8;
            logger.log("inquiry list detect file=" + frameFile.getName()
                    + " whiteBodyHits=" + whiteBodyHits
                    + " redListHits=" + redListHits
                    + " todayButtonHits=" + todayButtonHits
                    + " bottomBackHits=" + bottomBackHits
                    + " topWhiteHits=" + topWhiteHits
                    + " shopTabHits=" + shopTabHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isInquiryPageVisible(File frameFile) {
        return isInquiryDetailPageVisible(frameFile) || isInquiryListPageVisible(frameFile);
    }

    private boolean isNikkesListPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("nikkes list detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int cardHits = countNikkesCardSamples(bitmap);
            int bottomNavHits = countNikkesBottomNavigationSamples(bitmap);
            int topFilterHits = countStrictWhiteSamples(bitmap, 440, 125, 835, 165);
            int rightButtonHits = countStrictWhiteSamples(bitmap, 1160, 70, 1270, 105);
            boolean visible = cardHits >= 180 && bottomNavHits >= 30
                    && (topFilterHits >= 12 || rightButtonHits >= 8);
            logger.log("nikkes list detect file=" + frameFile.getName()
                    + " cardHits=" + cardHits
                    + " bottomNavHits=" + bottomNavHits
                    + " topFilterHits=" + topFilterHits
                    + " rightButtonHits=" + rightButtonHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isDispatchBoardPageVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("dispatch board page detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int bottomBackHits = countBlueButtonSamples(bitmap, 0, 650, 130, 715);
            int claimButtonHits = countBlueButtonSamples(bitmap, 690, 585, 820, 635);
            int dispatchButtonHits = countBlueButtonSamples(bitmap, 570, 585, 710, 635);
            int centerBrightHits = countBrightPanelSamples(bitmap, 230, 115, 1060, 585);
            int darkOverlayHits = countHomePopupOutsideDarkSamples(bitmap);
            int homeBottomHits = countHomeBottomNavigationSamples(bitmap);
            int homeFeatureHits = countHomeFeatureEntrySamples(bitmap);
            boolean homeVisible = homeBottomHits >= 18 && homeFeatureHits >= 30;
            boolean classicVisible = !homeVisible
                    && bottomBackHits >= 18
                    && centerBrightHits >= 220
                    && (claimButtonHits >= 8 || dispatchButtonHits >= 8);
            boolean strongButtonVisible = centerBrightHits >= 600
                    && (claimButtonHits >= 20 || dispatchButtonHits >= 12);
            boolean modalVisible = darkOverlayHits >= 80
                    && centerBrightHits >= 600
                    && (claimButtonHits >= 4 || dispatchButtonHits >= 6);
            boolean visible = classicVisible || strongButtonVisible || modalVisible;
            logger.log("dispatch board page detect file=" + frameFile.getName()
                    + " bottomBackHits=" + bottomBackHits
                    + " claimButtonHits=" + claimButtonHits
                    + " dispatchButtonHits=" + dispatchButtonHits
                    + " centerBrightHits=" + centerBrightHits
                    + " darkOverlayHits=" + darkOverlayHits
                    + " homeVisible=" + homeVisible
                    + " classicVisible=" + classicVisible
                    + " strongButtonVisible=" + strongButtonVisible
                    + " modalVisible=" + modalVisible
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isDispatchBoardClaimOrDispatchVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("dispatch board button detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int claimButtonHits = countBlueButtonSamples(bitmap, 690, 585, 820, 635);
            int dispatchButtonHits = countBlueButtonSamples(bitmap, 570, 585, 710, 635);
            boolean visible = claimButtonHits >= 6 || dispatchButtonHits >= 6;
            logger.log("dispatch board button detect file=" + frameFile.getName()
                    + " claimButtonHits=" + claimButtonHits
                    + " dispatchButtonHits=" + dispatchButtonHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isDispatchBoardClaimButtonVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("dispatch board claim button detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int claimButtonHits = countBlueButtonSamples(bitmap, 690, 585, 820, 635);
            boolean visible = claimButtonHits >= 6;
            logger.log("dispatch board claim button detect file=" + frameFile.getName()
                    + " claimButtonHits=" + claimButtonHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isDispatchBoardDispatchButtonVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("dispatch board dispatch button detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int dispatchButtonHits = countBlueButtonSamples(bitmap, 570, 585, 710, 635);
            boolean visible = dispatchButtonHits >= 6;
            logger.log("dispatch board dispatch button detect file=" + frameFile.getName()
                    + " dispatchButtonHits=" + dispatchButtonHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isDispatchBoardDispatchConfirmVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("dispatch board confirm detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int darkHits = countHomePopupOutsideDarkSamples(bitmap);
            int confirmButtonHits = countBlueButtonSamples(bitmap, 585, 585, 805, 635);
            int panelHits = countBrightPanelSamples(bitmap, 455, 300, 825, 525);
            boolean visible = darkHits >= 45 && confirmButtonHits >= 18 && panelHits >= 60;
            logger.log("dispatch board confirm detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " confirmButtonHits=" + confirmButtonHits
                    + " panelHits=" + panelHits
                    + " visible=" + visible);
            return visible;
        } finally {
            bitmap.recycle();
        }
    }

    private String normalizeSelectedShopItems(String value) {
        if (value == null) {
            return "";
        }
        String[] parts = value.split(",");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String item = parts[i] == null ? "" : parts[i].trim();
            if (item.length() == 0) {
                continue;
            }
            if (builder.indexOf(item) >= 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(item);
        }
        return builder.toString();
    }

    private void appendUnsupportedShopBranch(StringBuilder builder, String branchName) {
        if (builder.length() > 0) {
            builder.append('_');
        }
        builder.append(branchName);
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

    private boolean isPassClaimButtonVisible(File frameFile) {
        Bitmap bitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
        if (bitmap == null) {
            logger.log("pass claim button detect skipped, cannot decode " + frameFile.getAbsolutePath());
            return false;
        }
        try {
            int blueHits = countBlueButtonSamples(bitmap, 520, 640, 780, 695);
            int orangeHits = countOrangePanelSamples(bitmap, 520, 640, 780, 695);
            int bottomBrightHits = countBrightPanelSamples(bitmap, 500, 628, 805, 705);
            int redPointHits = countRedPanelSamples(bitmap, 1120, 55, 1275, 260);
            int homeBottomHits = countHomeBottomNavigationSamples(bitmap);
            int homeRightHits = countHomeRightMenuSamples(bitmap);
            int homeFeatureHits = countHomeFeatureEntrySamples(bitmap);
            boolean homeVisible = homeBottomHits >= 18 && homeRightHits >= 7 && homeFeatureHits >= 30;
            boolean buttonEnabled = blueHits >= 16 || orangeHits >= 24;
            boolean visible = !homeVisible && buttonEnabled && bottomBrightHits >= 16;
            logger.log("pass claim button detect file=" + frameFile.getName()
                    + " blueHits=" + blueHits
                    + " orangeHits=" + orangeHits
                    + " bottomBrightHits=" + bottomBrightHits
                    + " redPointHits=" + redPointHits
                    + " homeVisible=" + homeVisible
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
            int homeBottomHits = countHomeBottomNavigationSamples(bitmap);
            int homeRightHits = countHomeRightMenuSamples(bitmap);
            int homeFeatureHits = countHomeFeatureEntrySamples(bitmap);
            int shopTabHits = countOrangePanelSamples(bitmap, 0, 250, 60, 580);
            int shopItemCardHits = countBrightPanelSamples(bitmap, 70, 340, 1150, 520);
            int shopWhiteBodyHits = countBrightPanelSamples(bitmap, 60, 250, 1260, 700);
            int shopRedListHits = countRedPanelSamples(bitmap, 60, 190, 1210, 620);
            boolean homeVisible = homeBottomHits >= 18 && homeRightHits >= 7 && homeFeatureHits >= 30;
            boolean shopVisible = (shopTabHits >= 30 && shopItemCardHits >= 140)
                    || (shopRedListHits < 120 && shopWhiteBodyHits >= 1800 && shopItemCardHits >= 550);
            boolean buttonSignature = confirmButtonHits >= 16 || (costButtonHits >= 10 && confirmButtonHits >= 10);
            boolean panelSignature = panelHits >= 35 && bodyTextHits >= 18;
            boolean visible = darkHits >= 80 && panelSignature && buttonSignature
                    && !homeVisible && !shopVisible;
            logger.log("outpost clean confirm detect file=" + frameFile.getName()
                    + " darkHits=" + darkHits
                    + " costButtonHits=" + costButtonHits
                    + " confirmButtonHits=" + confirmButtonHits
                    + " panelHits=" + panelHits
                    + " bodyTextHits=" + bodyTextHits
                    + " homeVisible=" + homeVisible
                    + " shopVisible=" + shopVisible
                    + " shopWhiteBodyHits=" + shopWhiteBodyHits
                    + " shopRedListHits=" + shopRedListHits
                    + " buttonSignature=" + buttonSignature
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

    private int countOrangePanelSamples(Bitmap bitmap, int left, int top, int right, int bottom) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, right);
        int maxY = Math.min(bitmap.getHeight() - 1, bottom);
        for (int y = Math.max(0, top); y <= maxY; y += 8) {
            for (int x = Math.max(0, left); x <= maxX; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r >= 170 && g >= 70 && g <= 190 && b <= 90) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countInterceptionWeeklySweepButtonSamples(Bitmap bitmap) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, 835);
        int maxY = Math.min(bitmap.getHeight() - 1, 637);
        for (int y = 582; y <= maxY; y += 8) {
            for (int x = 642; x <= maxX; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                boolean amber = r >= 185 && g >= 105 && g <= 205 && b <= 115
                        && r > g + 20 && g > b + 25;
                if (amber) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countRedPanelSamples(Bitmap bitmap, int left, int top, int right, int bottom) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, right);
        int maxY = Math.min(bitmap.getHeight() - 1, bottom);
        for (int y = Math.max(0, top); y <= maxY; y += 8) {
            for (int x = Math.max(0, left); x <= maxX; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r >= 170 && g <= 110 && b <= 130 && r > g + 50 && r > b + 40) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countPinkPanelSamples(Bitmap bitmap, int left, int top, int right, int bottom) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, right);
        int maxY = Math.min(bitmap.getHeight() - 1, bottom);
        for (int y = Math.max(0, top); y <= maxY; y += 8) {
            for (int x = Math.max(0, left); x <= maxX; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r >= 185 && g >= 65 && g <= 185 && b >= 115 && b <= 235
                        && r > g + 35 && r >= b - 5) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countDarkPanelSamples(Bitmap bitmap, int left, int top, int right, int bottom) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, right);
        int maxY = Math.min(bitmap.getHeight() - 1, bottom);
        for (int y = Math.max(0, top); y <= maxY; y += 8) {
            for (int x = Math.max(0, left); x <= maxX; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r < 50 && g < 50 && b < 50) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countStrictWhiteSamples(Bitmap bitmap, int left, int top, int right, int bottom) {
        int hits = 0;
        int maxX = Math.min(bitmap.getWidth() - 1, right);
        int maxY = Math.min(bitmap.getHeight() - 1, bottom);
        for (int y = Math.max(0, top); y <= maxY; y += 8) {
            for (int x = Math.max(0, left); x <= maxX; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 220 && g > 220 && b > 220) {
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

    private int countNikkesCardSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 200; y <= 610; y += 8) {
            for (int x = 45; x <= 1235; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                boolean gold = r >= 170 && g >= 120 && g <= 215 && b <= 90;
                boolean orange = r >= 180 && g >= 80 && g <= 170 && b <= 80;
                if (gold || orange) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countNikkesBottomNavigationSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 635; y <= 708; y += 8) {
            for (int x = 450; x <= 820; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r >= 120 && g >= 120 && b >= 120 && Math.abs(r - g) <= 35 && Math.abs(g - b) <= 35) {
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

    private int countStartPageBottomGlowSamples(Bitmap bitmap) {
        int hits = 0;
        for (int y = 628; y <= 705; y += 8) {
            for (int x = 35; x <= 155; x += 8) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (b > 170 && g > 120 && r < 120 && b > r + 70) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private int countBrightPanelSamples(Bitmap bitmap, int x1, int y1, int x2, int y2,
                                        int step, int threshold) {
        int hits = 0;
        for (int y = y1; y <= y2; y += step) {
            for (int x = x1; x <= x2; x += step) {
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > threshold && g > threshold && b > threshold) {
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
        archivePreviousTaskArtifacts();
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
        ProbeConfig.MAACORE_PROBE_REPORT_FILE.delete();
        logger.reset();
    }

    private void archivePreviousTaskArtifacts() {
        if (!ProbeConfig.TASK_RESULT_FILE.exists() && !ProbeConfig.TASK_LOG_FILE.exists()) {
            return;
        }
        File archiveDir = new File("/storage/emulated/0/Documents/MaaNikke/exports");
        try {
            if (!archiveDir.exists() && !archiveDir.mkdirs()) {
                logger.log("previous task artifact archive skipped, cannot create "
                        + archiveDir.getAbsolutePath());
                return;
            }
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(new java.util.Date());
            copyFile(ProbeConfig.TASK_RESULT_FILE,
                    new File(archiveDir, "previous-task-result-" + stamp + ".txt"));
            copyFile(ProbeConfig.TASK_LOG_FILE,
                    new File(archiveDir, "previous-task-runner-" + stamp + ".log"));
        } catch (Throwable error) {
            logger.log("previous task artifact archive failed: "
                    + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private void writeResult(FrameCaptureBackend capture, String phase) {
        try {
            FileWriter writer = new FileWriter(ProbeConfig.TASK_RESULT_FILE, false);
            try {
                writer.write("phase=" + phase + "\n");
                writer.write("task=" + taskName + "\n");
                writer.write("normalizedTask=" + normalizeTaskName(taskName) + "\n");
                writer.write("executableTask=" + toExecutableTaskName(normalizeTaskName(taskName)) + "\n");
                writer.write("debugDryRun=" + isDebugDryRunTask(normalizeTaskName(taskName)) + "\n");
                writer.write("workflowMode=" + workflowMode + "\n");
                writer.write("workflowSteps=" + joinStepsForLog(workflowSteps) + "\n");
                writer.write("activeWorkflowStep=" + (activeWorkflowStep == null ? "" : activeWorkflowStep) + "\n");
                writer.write("optionCandidates=" + joinStepsForLog(optionTaskNameCandidates()) + "\n");
                writer.write("resolvedOptions=" + buildRelevantOptionsSummary() + "\n");
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
                writer.write("maaCoreProbeReportFile=" + ProbeConfig.MAACORE_PROBE_REPORT_FILE.getAbsolutePath() + "\n");
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

    private String firstExistingDirectory(String[] candidates) {
        for (int i = 0; i < candidates.length; i++) {
            String path = candidates[i];
            if (deviceDirectoryExists(path)) {
                return path;
            }
        }
        return "";
    }

    private String firstExistingFile(String[] candidates) {
        for (int i = 0; i < candidates.length; i++) {
            String path = candidates[i];
            if (deviceFileExists(path)) {
                return path;
            }
        }
        return "";
    }

    private boolean deviceDirectoryExists(String path) {
        try {
            AndroidShellEnvironment.CommandResult result = environment.runCommandForResult(
                    "[ -d " + shellQuote(path) + " ]");
            return result.exitCode == 0;
        } catch (Throwable error) {
            logger.log("device directory check failed path=" + path + " error=" + error.getMessage());
            return false;
        }
    }

    private boolean deviceFileExists(String path) {
        try {
            AndroidShellEnvironment.CommandResult result = environment.runCommandForResult(
                    "[ -f " + shellQuote(path) + " ]");
            return result.exitCode == 0;
        } catch (Throwable error) {
            logger.log("device file check failed path=" + path + " error=" + error.getMessage());
            return false;
        }
    }

    private String readTextFileLimited(String path, int maxChars) {
        if (path == null || path.length() == 0) {
            return "";
        }
        FileInputStream input = null;
        InputStreamReader reader = null;
        try {
            input = new FileInputStream(path);
            reader = new InputStreamReader(input, "UTF-8");
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            while (builder.length() < maxChars) {
                int remaining = maxChars - builder.length();
                int read = reader.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (Throwable error) {
            logger.log("read text file failed path=" + path
                    + " error=" + error.getClass().getName() + ": " + error.getMessage());
            return "";
        } finally {
            closeQuietly(reader);
            closeQuietly(input);
        }
    }

    private String sanitizeReportText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable ignored) {
            // Ignore cleanup failures.
        }
    }

    private void writeTextFile(File target, String text) {
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileWriter writer = new FileWriter(target, false);
            try {
                writer.write(text == null ? "" : text);
            } finally {
                writer.close();
            }
        } catch (Throwable error) {
            logger.log("write text file failed target=" + target.getAbsolutePath()
                    + " error=" + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
