package com.codex.maanikke.debug;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;

public final class PreviewRenderUserService extends IPreviewRenderService.Stub {
    private static final File STATUS_FILE = new File("/data/local/tmp/maanikke_preview_user_service_status.txt");

    private final Object lock = new Object();
    private volatile boolean running;
    private volatile String status = "idle";
    private Thread renderThread;
    private Surface currentSurface;
    private boolean surfacePending;
    private String framePath;
    private int minIntervalMs = 120;
    private long lastModified = -1;
    private long lastLength = -1;

    public PreviewRenderUserService() {
        NativePreviewRenderer.loadLibrary("/data/local/tmp/libmaanikke_preview_renderer.so");
        setStatus("created");
    }

    public PreviewRenderUserService(String[] args) {
        this();
    }

    @Override
    public IBinder asBinder() {
        return this;
    }

    @Override
    public boolean setSurface(Surface surface) {
        synchronized (lock) {
            if (currentSurface != null && currentSurface != surface) {
                currentSurface.release();
            }
            currentSurface = surface;
            surfacePending = true;
            setStatus(surface == null ? "surface_missing" : "surface_pending");
            return surface != null;
        }
    }

    @Override
    public void clearSurface() {
        synchronized (lock) {
            NativePreviewRenderer.clearSurface();
            if (currentSurface != null) {
                currentSurface.release();
                currentSurface = null;
            }
            surfacePending = false;
            setStatus("surface_cleared");
        }
    }

    @Override
    public boolean startFilePreview(String path, int intervalMs) {
        synchronized (lock) {
            framePath = path;
            minIntervalMs = Math.max(60, intervalMs);
            lastModified = -1;
            lastLength = -1;
            if (running) {
                setStatus("file_preview_running");
                return true;
            }
            running = true;
            renderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    runFilePreviewLoop();
                }
            }, "maanikke-preview-user-service-render");
            renderThread.start();
            setStatus("file_preview_started");
            return true;
        }
    }

    @Override
    public void stopFilePreview() {
        running = false;
        Thread thread;
        synchronized (lock) {
            thread = renderThread;
            renderThread = null;
            setStatus("file_preview_stopped");
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(800);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void shutdown() {
        destroy();
        setStatus("shutdown");
    }

    public void destroy() {
        stopFilePreview();
        clearSurface();
    }

    private void runFilePreviewLoop() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        while (running) {
            try {
                String path = framePath;
                Surface surface;
                synchronized (lock) {
                    surface = currentSurface;
                    if (surfacePending && surface != null) {
                        boolean ready = NativePreviewRenderer.setSurface(surface);
                        surfacePending = !ready;
                        setStatus(ready ? "surface_ready" : "surface_failed");
                        if (!ready) {
                            sleepQuietly(minIntervalMs);
                            continue;
                        }
                    }
                }
                if (path == null || path.length() == 0) {
                    setStatus("waiting_frame_path");
                    sleepQuietly(minIntervalMs);
                    continue;
                }
                File frame = new File(path);
                long modified = frame.lastModified();
                long length = frame.length();
                if (modified <= 0 || length <= 0 || (modified == lastModified && length == lastLength)) {
                    sleepQuietly(minIntervalMs);
                    continue;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if (bitmap == null) {
                    setStatus("decode_failed");
                    sleepQuietly(minIntervalMs);
                    continue;
                }
                boolean rendered;
                synchronized (lock) {
                    rendered = NativePreviewRenderer.renderBitmap(bitmap);
                }
                bitmap.recycle();
                if (rendered) {
                    lastModified = modified;
                    lastLength = length;
                    setStatus("rendered:" + length + ":" + modified);
                } else {
                    setStatus("render_failed");
                }
                sleepQuietly(minIntervalMs);
            } catch (Throwable error) {
                setStatus("error:" + error.getClass().getSimpleName() + ":" + error.getMessage());
                sleepQuietly(300);
            }
        }
    }

    private void setStatus(String value) {
        status = value;
        writeStatus(value);
    }

    private static void writeStatus(String value) {
        try {
            FileOutputStream output = new FileOutputStream(STATUS_FILE, false);
            try {
                output.write((System.currentTimeMillis() + " " + value + "\n").getBytes("UTF-8"));
            } finally {
                output.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
