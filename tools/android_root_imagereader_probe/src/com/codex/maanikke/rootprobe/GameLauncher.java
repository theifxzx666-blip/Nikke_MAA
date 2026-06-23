package com.codex.maanikke.rootprobe;

final class GameLauncher {
    private final AndroidShellEnvironment environment;
    private LaunchTarget launchTarget;

    GameLauncher(AndroidShellEnvironment environment) {
        this.environment = environment;
    }

    void startOnDisplay(int displayId) throws Exception {
        startResolved(displayId, true);
    }

    void bringToDisplay(int displayId) throws Exception {
        startResolved(displayId, false);
    }

    void forceStop() throws Exception {
        LaunchTarget target = resolveTarget();
        environment.runCommand("am force-stop " + shellQuote(target.packageName));
    }

    String getPackageName() {
        try {
            return resolveTarget().packageName;
        } catch (Throwable error) {
            return ProbeConfig.DEFAULT_TARGET_PACKAGE;
        }
    }

    String getComponentName() {
        try {
            return resolveTarget().componentName;
        } catch (Throwable error) {
            return ProbeConfig.DEFAULT_TARGET_PACKAGE + "/" + ProbeConfig.DEFAULT_TARGET_ACTIVITY;
        }
    }

    String getTargetSource() {
        try {
            return resolveTarget().source;
        } catch (Throwable error) {
            return "unresolved";
        }
    }

    boolean isNikkePackage(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return false;
        }
        try {
            return packageName.equals(resolveTarget().packageName);
        } catch (Throwable ignored) {
            for (int i = 0; i < ProbeConfig.TARGET_PACKAGE_CANDIDATES.length; i++) {
                if (packageName.equals(ProbeConfig.TARGET_PACKAGE_CANDIDATES[i])) {
                    return true;
                }
            }
            return packageName.toLowerCase().contains("nikke");
        }
    }

    private void startResolved(int displayId, boolean forceStopFirst) throws Exception {
        LaunchTarget target = resolveTarget();
        AndroidShellEnvironment.CommandResult result = startComponent(displayId, target.componentName, forceStopFirst);
        if (!result.isSuccess()) {
            String fallback = target.packageName + "/" + ProbeConfig.DEFAULT_TARGET_ACTIVITY;
            if (!fallback.equals(target.componentName)) {
                environment.runCommandForResult("am force-stop " + shellQuote(target.packageName));
                AndroidShellEnvironment.CommandResult fallbackResult = startComponent(displayId, fallback, forceStopFirst);
                if (fallbackResult.isSuccess()) {
                    launchTarget = new LaunchTarget(target.packageName, fallback, "fallback_default_activity");
                    return;
                }
                throw new IllegalStateException("failed to start NIKKE on display " + displayId
                        + " target=" + target.componentName
                        + " output=" + oneLine(result.combinedOutput())
                        + " fallback=" + fallback
                        + " fallbackOutput=" + oneLine(fallbackResult.combinedOutput()));
            }
            throw new IllegalStateException("failed to start NIKKE on display " + displayId
                    + " target=" + target.componentName
                    + " output=" + oneLine(result.combinedOutput()));
        }
    }

    private AndroidShellEnvironment.CommandResult startComponent(
            int displayId,
            String componentName,
            boolean forceStopFirst
    ) throws Exception {
        StringBuilder command = new StringBuilder("am start ");
        if (forceStopFirst) {
            command.append("-S ");
        }
        command.append("--display ")
                .append(displayId)
                .append(" -n ")
                .append(shellQuote(componentName));
        return environment.runCommandForResult(command.toString());
    }

    private LaunchTarget resolveTarget() throws Exception {
        if (launchTarget != null) {
            return launchTarget;
        }

        String componentOverride = readOverride(ProbeConfig.TARGET_COMPONENT_OVERRIDE_FILE);
        if (isComponentName(componentOverride)) {
            String packageName = componentOverride.substring(0, componentOverride.indexOf('/'));
            launchTarget = new LaunchTarget(packageName, componentOverride, "component_override");
            return launchTarget;
        }

        String packageOverride = readOverride(ProbeConfig.TARGET_OVERRIDE_FILE);
        if (packageOverride.length() > 0) {
            LaunchTarget target = resolvePackage(packageOverride, "package_override");
            if (target != null) {
                launchTarget = target;
                return launchTarget;
            }
            throw new IllegalStateException("NIKKE package override is not installed or has no launcher activity: "
                    + packageOverride);
        }

        for (int i = 0; i < ProbeConfig.TARGET_PACKAGE_CANDIDATES.length; i++) {
            LaunchTarget target = resolvePackage(ProbeConfig.TARGET_PACKAGE_CANDIDATES[i], "candidate");
            if (target != null) {
                launchTarget = target;
                return launchTarget;
            }
        }

        String discovered = discoverInstalledNikkePackage();
        if (discovered.length() > 0) {
            LaunchTarget target = resolvePackage(discovered, "discovered");
            if (target != null) {
                launchTarget = target;
                return launchTarget;
            }
        }

        throw new IllegalStateException("NIKKE package was not found. Install the game on this device, "
                + "or write its package name to " + ProbeConfig.TARGET_OVERRIDE_FILE
                + " / full component to " + ProbeConfig.TARGET_COMPONENT_OVERRIDE_FILE);
    }

    private LaunchTarget resolvePackage(String packageName, String source) throws Exception {
        if (!isPackageInstalled(packageName)) {
            return null;
        }
        String component = resolveLauncherComponent(packageName);
        if (component.length() == 0) {
            component = packageName + "/" + ProbeConfig.DEFAULT_TARGET_ACTIVITY;
        }
        return new LaunchTarget(packageName, component, source);
    }

    private boolean isPackageInstalled(String packageName) throws Exception {
        AndroidShellEnvironment.CommandResult result =
                environment.runCommandForResult("cmd package path " + shellQuote(packageName));
        return result.exitCode == 0 && result.stdout.contains("package:");
    }

    private String resolveLauncherComponent(String packageName) throws Exception {
        String command = "cmd package resolve-activity --brief"
                + " -a android.intent.action.MAIN"
                + " -c android.intent.category.LAUNCHER"
                + " -p " + shellQuote(packageName)
                + " 2>/dev/null | tail -n 1";
        AndroidShellEnvironment.CommandResult result = environment.runCommandForResult(command);
        String component = result.stdout.trim();
        if (isComponentName(component)) {
            return component;
        }

        String monkeyCommand = "monkey -p " + shellQuote(packageName)
                + " -c android.intent.category.LAUNCHER 0 2>/dev/null"
                + " | sed -n 's/.*cmp=\\([^ ]*\\).*/\\1/p' | head -n 1";
        AndroidShellEnvironment.CommandResult monkey = environment.runCommandForResult(monkeyCommand);
        component = monkey.stdout.trim();
        if (isComponentName(component)) {
            return component;
        }

        return "";
    }

    private String discoverInstalledNikkePackage() throws Exception {
        AndroidShellEnvironment.CommandResult result =
                environment.runCommandForResult("cmd package list packages | sed 's/^package://' | grep -i nikke | head -n 1 || true");
        return result.stdout.trim();
    }

    private String readOverride(String path) throws Exception {
        AndroidShellEnvironment.CommandResult result =
                environment.runCommandForResult("cat " + shellQuote(path) + " 2>/dev/null | head -n 1 || true");
        return result.stdout.trim();
    }

    private boolean isComponentName(String value) {
        if (value == null) {
            return false;
        }
        int slash = value.indexOf('/');
        return slash > 0 && slash < value.length() - 1 && value.indexOf(' ') < 0;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String oneLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static final class LaunchTarget {
        final String packageName;
        final String componentName;
        final String source;

        LaunchTarget(String packageName, String componentName, String source) {
            this.packageName = packageName;
            this.componentName = componentName;
            this.source = source;
        }
    }
}
