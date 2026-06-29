package com.codex.maanikke.rootprobe;

final class MaaCoreNativeBridge {
    private MaaCoreNativeBridge() {
    }

    static String runProbe(
            int displayId,
            String frameworkPath,
            String controlUnitPath,
            String resourcePath,
            String framePath,
            String ocrParamJson) {
        return nativeRunProbe(displayId, frameworkPath, controlUnitPath, resourcePath, framePath, ocrParamJson);
    }

    static void load(String bridgePath) {
        System.load(bridgePath);
    }

    private static native String nativeRunProbe(
            int displayId,
            String frameworkPath,
            String controlUnitPath,
            String resourcePath,
            String framePath,
            String ocrParamJson);
}
