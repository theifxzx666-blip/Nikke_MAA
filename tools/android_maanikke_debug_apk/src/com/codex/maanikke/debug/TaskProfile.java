package com.codex.maanikke.debug;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class TaskProfile {
    static final String PREF_TASK_ORDER = "task_order";
    static final String PREF_TASK_CHECK_PREFIX = "task_checked_";

    private final ArrayList<TaskCatalog.TaskSpec> orderedTasks;
    private final ArrayList<TaskChainNode> nodes;

    private TaskProfile(ArrayList<TaskCatalog.TaskSpec> orderedTasks, ArrayList<TaskChainNode> nodes) {
        this.orderedTasks = orderedTasks;
        this.nodes = nodes;
    }

    static TaskProfile load(SharedPreferences preferences) {
        ArrayList<TaskCatalog.TaskSpec> ordered = loadOrderedTasks(preferences);
        ArrayList<TaskChainNode> nodes = new ArrayList<TaskChainNode>();
        for (int i = 0; i < ordered.size(); i++) {
            TaskCatalog.TaskSpec task = ordered.get(i);
            nodes.add(new TaskChainNode(task, isChecked(preferences, task)));
        }
        return new TaskProfile(ordered, nodes);
    }

    ArrayList<TaskCatalog.TaskSpec> orderedTasks() {
        return new ArrayList<TaskCatalog.TaskSpec>(orderedTasks);
    }

    String[] buildBackendSteps(boolean debugMode, boolean forceStart) {
        ArrayList<String> selected = new ArrayList<String>();
        if (forceStart) {
            selected.add(resolveTaskForMode("start_game", debugMode));
        }
        for (int i = 0; i < nodes.size(); i++) {
            TaskChainNode node = nodes.get(i);
            if (!node.enabled || !node.checked) {
                continue;
            }
            String step = resolveTaskForMode(node.task.androidTaskId, debugMode);
            if (forceStart && "start_game".equals(step)) {
                continue;
            }
            selected.add(step);
        }
        return selected.toArray(new String[selected.size()]);
    }

    static void saveChecks(SharedPreferences preferences, List<TaskSelection> selections) {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < selections.size(); i++) {
            TaskSelection selection = selections.get(i);
            editor.putBoolean(PREF_TASK_CHECK_PREFIX + selection.taskId, selection.checked);
        }
        editor.apply();
    }

    static String[] buildBackendSteps(List<TaskSelection> selections, boolean debugMode, boolean forceStart) {
        ArrayList<String> selected = new ArrayList<String>();
        if (forceStart) {
            selected.add(resolveTaskForMode("start_game", debugMode));
        }
        for (int i = 0; i < selections.size(); i++) {
            TaskSelection selection = selections.get(i);
            if (!selection.enabled || !selection.checked) {
                continue;
            }
            String step = resolveTaskForMode(selection.taskId, debugMode);
            if (forceStart && "start_game".equals(step)) {
                continue;
            }
            selected.add(step);
        }
        return selected.toArray(new String[selected.size()]);
    }

    static void saveOrder(SharedPreferences preferences, List<String> taskIds) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < taskIds.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(taskIds.get(i));
        }
        preferences.edit().putString(PREF_TASK_ORDER, builder.toString()).apply();
    }

    static boolean isChecked(SharedPreferences preferences, TaskCatalog.TaskSpec task) {
        return preferences.getBoolean(PREF_TASK_CHECK_PREFIX + task.androidTaskId, task.defaultChecked);
    }

    static void saveChecked(SharedPreferences preferences, String taskId, boolean checked) {
        preferences.edit().putBoolean(PREF_TASK_CHECK_PREFIX + taskId, checked).apply();
    }

    static String resolveTaskForMode(String taskId, boolean debugMode) {
        if ("visit_mail".equals(taskId)) {
            return debugMode ? "debug_claim_mail" : "claim_mail";
        }
        if ("visit_daily_rewards".equals(taskId)) {
            return debugMode ? "debug_claim_daily_rewards" : "claim_daily_rewards";
        }
        if ("visit_friend_points".equals(taskId)) {
            return debugMode ? "debug_claim_friend_points" : "claim_friend_points";
        }
        if ("visit_outpost_defense".equals(taskId)) {
            return debugMode ? "debug_claim_outpost_defense" : "claim_outpost_defense";
        }
        if ("visit_free_shop".equals(taskId)) {
            return debugMode ? "debug_claim_free_shop" : "claim_free_shop";
        }
        if ("visit_inquiry_and_gift".equals(taskId)) {
            return debugMode ? "debug_claim_inquiry_and_gift" : "claim_inquiry_and_gift";
        }
        if ("visit_sim_room".equals(taskId)) {
            return debugMode ? "debug_claim_sim_room" : "claim_sim_room";
        }
        if ("visit_climb_tower".equals(taskId)) {
            return debugMode ? "debug_claim_climb_tower" : "claim_climb_tower";
        }
        if ("visit_pass_rewards".equals(taskId)) {
            return debugMode ? "debug_claim_pass_rewards" : "claim_pass_rewards";
        }
        return taskId;
    }

    private static ArrayList<TaskCatalog.TaskSpec> loadOrderedTasks(SharedPreferences preferences) {
        ArrayList<TaskCatalog.TaskSpec> ordered = new ArrayList<TaskCatalog.TaskSpec>();
        String savedOrder = preferences.getString(PREF_TASK_ORDER, "");
        ArrayList<String> orderIds = new ArrayList<String>();
        if (savedOrder != null && savedOrder.length() > 0) {
            String[] parts = savedOrder.split(",");
            for (int i = 0; i < parts.length; i++) {
                String taskId = parts[i].trim();
                if (taskId.length() > 0) {
                    orderIds.add(taskId);
                }
            }
        }
        boolean[] added = new boolean[TaskCatalog.PC_TASKS.length];
        for (int i = 0; i < orderIds.size(); i++) {
            TaskCatalog.TaskSpec spec = TaskCatalog.findByAndroidTaskId(orderIds.get(i));
            if (spec == null) {
                continue;
            }
            ordered.add(spec);
            markTaskAdded(added, spec.androidTaskId);
        }
        for (int i = 0; i < TaskCatalog.PC_TASKS.length; i++) {
            TaskCatalog.TaskSpec spec = TaskCatalog.PC_TASKS[i];
            if (!wasTaskAdded(added, spec.androidTaskId)) {
                ordered.add(spec);
                markTaskAdded(added, spec.androidTaskId);
            }
        }
        return ordered;
    }

    private static void markTaskAdded(boolean[] added, String taskId) {
        int index = taskIndex(taskId);
        if (index >= 0 && index < added.length) {
            added[index] = true;
        }
    }

    private static boolean wasTaskAdded(boolean[] added, String taskId) {
        int index = taskIndex(taskId);
        return index >= 0 && index < added.length && added[index];
    }

    private static int taskIndex(String taskId) {
        for (int i = 0; i < TaskCatalog.PC_TASKS.length; i++) {
            if (TaskCatalog.PC_TASKS[i].androidTaskId.equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    static final class TaskChainNode {
        final TaskCatalog.TaskSpec task;
        final boolean checked;
        final boolean enabled;

        TaskChainNode(TaskCatalog.TaskSpec task, boolean checked) {
            this.task = task;
            this.checked = checked;
            this.enabled = task.enabled;
        }
    }

    static final class TaskSelection {
        final String taskId;
        final boolean checked;
        final boolean enabled;

        TaskSelection(String taskId, boolean checked, boolean enabled) {
            this.taskId = taskId;
            this.checked = checked;
            this.enabled = enabled;
        }
    }
}
