package com.codex.maanikke.rootprobe;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ProbeLogger {
    private final File logFile;

    ProbeLogger(File logFile) {
        this.logFile = logFile;
    }

    void reset() {
        logFile.delete();
    }

    void log(String message) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()) + " " + message;
        try {
            FileWriter writer = new FileWriter(logFile, true);
            try {
                writer.write(line);
                writer.write('\n');
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
            // Keep stdout working even if file logging fails.
        }
    }
}
