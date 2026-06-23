package com.codex.maanikke.rootprobe;

final class MaaEventLogger {
    private final ProbeLogger logger;

    MaaEventLogger(ProbeLogger logger) {
        this.logger = logger;
    }

    void taskChainStart(String taskName, String detail) {
        event("TaskChain.Start", taskName, "", detail, null);
    }

    void taskChainComplete(String taskName, String state, String detail) {
        event("TaskChain.Completed", taskName, state, detail, null);
    }

    void taskChainError(String taskName, String state, Throwable error) {
        event("TaskChain.Error", taskName, state, "", error);
    }

    void subTaskStart(String taskName, String detail) {
        event("SubTask.Start", taskName, "", detail, null);
    }

    void subTaskComplete(String taskName, String state, String detail) {
        event("SubTask.Completed", taskName, state, detail, null);
    }

    void subTaskError(String taskName, String state, Throwable error) {
        event("SubTask.Error", taskName, state, "", error);
    }

    private void event(String type, String taskName, String state, String detail, Throwable error) {
        StringBuilder builder = new StringBuilder();
        builder.append("maa_event type=").append(type);
        builder.append(" task=").append(safe(taskName));
        if (state != null && state.length() > 0) {
            builder.append(" state=").append(safe(state));
        }
        if (detail != null && detail.length() > 0) {
            builder.append(" detail=").append(safe(detail));
        }
        if (error != null) {
            builder.append(" error=").append(safe(error.getClass().getSimpleName()
                    + ":" + error.getMessage()));
        }
        logger.log(builder.toString());
    }

    private String safe(String value) {
        if (value == null || value.length() == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ') {
                builder.append('_');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
