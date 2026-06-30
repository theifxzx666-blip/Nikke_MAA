package com.codex.maanikke.debug;

import android.graphics.Bitmap;
import android.view.Surface;

final class NativePreviewRenderer {
    private static volatile boolean libraryLoaded;

    static {
        loadLibrary(null);
    }

    private NativePreviewRenderer() {
    }

    static synchronized boolean loadLibrary(String absolutePath) {
        if (libraryLoaded) {
            return true;
        }
        try {
            if (absolutePath != null && absolutePath.length() > 0) {
                System.load(absolutePath);
            } else {
                System.loadLibrary("maanikke_preview_renderer");
            }
            libraryLoaded = true;
        } catch (Throwable ignored) {
            libraryLoaded = false;
        }
        return libraryLoaded;
    }

    static boolean setSurface(Surface surface) {
        return libraryLoaded && nativeSetSurface(surface);
    }

    static void clearSurface() {
        if (!libraryLoaded) {
            return;
        }
        nativeClearSurface();
    }

    static boolean renderBitmap(Bitmap bitmap) {
        return libraryLoaded && nativeRenderBitmap(bitmap);
    }

    private static native boolean nativeSetSurface(Surface surface);

    private static native void nativeClearSurface();

    private static native boolean nativeRenderBitmap(Bitmap bitmap);
}
