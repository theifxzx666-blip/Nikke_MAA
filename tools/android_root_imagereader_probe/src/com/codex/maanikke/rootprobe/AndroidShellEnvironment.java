package com.codex.maanikke.rootprobe;

import android.app.Application;
import android.app.Instrumentation;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Looper;
import android.os.Process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class AndroidShellEnvironment {
    private final ProbeLogger logger;
    private Object activityThread;
    private Class<?> activityThreadClass;
    private Context shellContext;

    AndroidShellEnvironment(ProbeLogger logger) {
        this.logger = logger;
    }

    void prepare() throws Exception {
        disableHiddenApiChecks();
        prepareActivityThread();
        shellContext = createShellContext();
    }

    Context getShellContext() {
        if (shellContext == null) {
            throw new IllegalStateException("shell context is not prepared");
        }
        return shellContext;
    }

    void runCommand(String command) throws Exception {
        runCommandForOutput(command);
    }

    String runCommandForOutput(String command) throws Exception {
        CommandResult result = runCommandForResult(command);
        return result.stdout;
    }

    CommandResult runCommandForResult(String command) throws Exception {
        logger.log("exec: " + command);
        java.lang.Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        logger.log("exec exit=" + exit + " stdout=" + stdout.trim() + " stderr=" + stderr.trim());
        return new CommandResult(exit, stdout, stderr);
    }

    private Context createShellContext() throws Exception {
        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        Context systemContext = (Context) getSystemContext.invoke(activityThread);
        if (systemContext == null) {
            throw new IllegalStateException("system context is null");
        }
        return new ShellContext(systemContext);
    }

    private void prepareActivityThread() throws Exception {
        prepareMainLooperIfNeeded();
        activityThreadClass = Class.forName("android.app.ActivityThread");
        Constructor<?> ctor = activityThreadClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        activityThread = ctor.newInstance();

        Field current = activityThreadClass.getDeclaredField("sCurrentActivityThread");
        current.setAccessible(true);
        current.set(null, activityThread);

        Field systemThread = activityThreadClass.getDeclaredField("mSystemThread");
        systemThread.setAccessible(true);
        systemThread.setBoolean(activityThread, true);

        fillConfigurationController();
        fillAppInfo();
        fillAppContext();
        logger.log("ActivityThread workaround prepared");
    }

    private void prepareMainLooperIfNeeded() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Field main = Looper.class.getDeclaredField("sMainLooper");
        main.setAccessible(true);
        if (main.get(null) == null) {
            main.set(null, Looper.myLooper());
        }
    }

    private void fillConfigurationController() {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            Class<?> configurationControllerClass = Class.forName("android.app.ConfigurationController");
            Class<?> activityThreadInternalClass = Class.forName("android.app.ActivityThreadInternal");
            Constructor<?> ctor = configurationControllerClass.getDeclaredConstructor(activityThreadInternalClass);
            ctor.setAccessible(true);
            Object controller = ctor.newInstance(activityThread);
            Field field = activityThreadClass.getDeclaredField("mConfigurationController");
            field.setAccessible(true);
            field.set(activityThread, controller);
            logger.log("ConfigurationController workaround prepared");
        } catch (Throwable error) {
            logger.log("ConfigurationController workaround skipped: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private void fillAppInfo() {
        try {
            Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> ctor = appBindDataClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object appBindData = ctor.newInstance();
            ApplicationInfo appInfo = new ApplicationInfo();
            appInfo.packageName = "com.android.shell";
            Field appInfoField = appBindDataClass.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            appInfoField.set(appBindData, appInfo);
            Field boundApplication = activityThreadClass.getDeclaredField("mBoundApplication");
            boundApplication.setAccessible(true);
            boundApplication.set(activityThread, appBindData);
            logger.log("AppInfo workaround prepared");
        } catch (Throwable error) {
            logger.log("AppInfo workaround skipped: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private void fillAppContext() {
        try {
            Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
            getSystemContext.setAccessible(true);
            Context systemContext = (Context) getSystemContext.invoke(activityThread);
            Application app = Instrumentation.newApplication(Application.class, new ShellContext(systemContext));
            Field initialApplication = activityThreadClass.getDeclaredField("mInitialApplication");
            initialApplication.setAccessible(true);
            initialApplication.set(activityThread, app);
            logger.log("AppContext workaround prepared");
        } catch (Throwable error) {
            logger.log("AppContext workaround skipped: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private String readAll(java.io.InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    static final class CommandResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        String combinedOutput() {
            if (stderr.length() == 0) {
                return stdout;
            }
            if (stdout.length() == 0) {
                return stderr;
            }
            return stdout + "\n" + stderr;
        }

        boolean isSuccess() {
            return exitCode == 0 && !looksLikeAmStartFailure(combinedOutput());
        }

        private boolean looksLikeAmStartFailure(String text) {
            if (text == null) {
                return false;
            }
            String lower = text.toLowerCase();
            return lower.contains("error:")
                    || lower.contains("exception")
                    || lower.contains("not found")
                    || lower.contains("does not exist")
                    || lower.contains("unable to resolve intent")
                    || lower.contains("activity class") && lower.contains("does not exist");
        }
    }

    private void disableHiddenApiChecks() {
        try {
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
            Method setHiddenApiExemptions = vmRuntime.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            Object runtime = getRuntime.invoke(null);
            setHiddenApiExemptions.invoke(runtime, (Object) new String[]{"L"});
            logger.log("hidden api exemptions applied");
        } catch (Throwable error) {
            logger.log("hidden api exemption failed: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private static final class ShellContext extends ContextWrapper {
        ShellContext(Context base) {
            super(base);
        }

        @Override
        public String getPackageName() {
            return "com.android.shell";
        }

        @Override
        public String getOpPackageName() {
            return "com.android.shell";
        }

        @Override
        public int checkCallingPermission(String permission) {
            return android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public int checkSelfPermission(String permission) {
            return android.content.pm.PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public AttributionSource getAttributionSource() {
            if (Build.VERSION.SDK_INT >= 31) {
                AttributionSource.Builder builder = new AttributionSource.Builder(Process.SHELL_UID);
                builder.setPackageName("com.android.shell");
                return builder.build();
            }
            return super.getAttributionSource();
        }
    }
}
