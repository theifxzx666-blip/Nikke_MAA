package com.codex.maanikke.rootprobe;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class FrameCaptureBackend implements ImageReader.OnImageAvailableListener {
    private final ProbeLogger logger;
    private final AtomicInteger frameCount = new AtomicInteger();
    private final AtomicInteger nonBlackFrameCount = new AtomicInteger();
    private final CountDownLatch firstFrame = new CountDownLatch(1);
    private final HandlerThread handlerThread = new HandlerThread("root-ir-capture");
    private final Object frameFileLock = new Object();
    private final Object latestJpegLock = new Object();
    private final Object readerLock = new Object();
    private ImageReader reader;
    private byte[] latestJpeg = new byte[0];
    private long latestJpegTimeMs = 0;
    private long latestJpegSequence = 0;
    private long lastLegacyFrameFileMs = 0;
    private long lastNonBlackFrameLogMs = 0;
    private volatile long lastNonZeroSamples = 0;
    private volatile boolean closed = false;

    FrameCaptureBackend(ProbeLogger logger) {
        this.logger = logger;
    }

    void start() {
        handlerThread.start();
        reader = createImageReader();
        reader.setOnImageAvailableListener(this, new Handler(handlerThread.getLooper()));
    }

    Surface getSurface() {
        if (reader == null) {
            throw new IllegalStateException("capture backend is not started");
        }
        return reader.getSurface();
    }

    boolean awaitFirstFrame(long timeout, TimeUnit unit) throws InterruptedException {
        return firstFrame.await(timeout, unit);
    }

    int getFrameCount() {
        return frameCount.get();
    }

    int getNonBlackFrameCount() {
        return nonBlackFrameCount.get();
    }

    long getLastNonZeroSamples() {
        return lastNonZeroSamples;
    }

    void copyLatestFrameTo(File target) {
        copyFile(ProbeConfig.FRAME_FILE, target);
    }

    byte[] getLatestJpeg() {
        synchronized (latestJpegLock) {
            if (latestJpeg.length == 0) {
                return new byte[0];
            }
            return Arrays.copyOf(latestJpeg, latestJpeg.length);
        }
    }

    long getLatestJpegSequence() {
        synchronized (latestJpegLock) {
            return latestJpegSequence;
        }
    }

    long getLatestJpegTimeMs() {
        synchronized (latestJpegLock) {
            return latestJpegTimeMs;
        }
    }

    void close() {
        synchronized (readerLock) {
            closed = true;
            if (reader != null) {
                try {
                    reader.setOnImageAvailableListener(null, null);
                } catch (Throwable error) {
                    logger.log("clear image listener failed: " + error.getClass().getName() + ": " + error.getMessage());
                }
                reader.close();
                reader = null;
            }
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join(2000);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        synchronized (readerLock) {
            if (closed) {
                return;
            }
            Image image = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image == null) {
                    return;
                }
                int count = frameCount.incrementAndGet();
                long nonZero = updateLatestFrame(image);
                lastNonZeroSamples = nonZero;
                if (nonZero > 0) {
                    int nonBlack = nonBlackFrameCount.incrementAndGet();
                    long now = System.currentTimeMillis();
                    if (nonBlack <= 3 || now - lastNonBlackFrameLogMs >= 3000) {
                        logger.log("non-black frame detected count=" + nonBlack
                                + " frame=" + count + " nonZeroSamples=" + nonZero);
                        lastNonBlackFrameLogMs = now;
                    }
                }
                firstFrame.countDown();
                if (count % 30 == 0 || count <= 3) {
                    logger.log("frame=" + count + " nonZeroSamples=" + nonZero);
                }
            } catch (Throwable error) {
                logger.log("onImageAvailable failed: " + error.getClass().getName() + ": " + error.getMessage());
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    private ImageReader createImageReader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            long usage = HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE;
            return ImageReader.newInstance(
                    ProbeConfig.WIDTH,
                    ProbeConfig.HEIGHT,
                    PixelFormat.RGBA_8888,
                    ProbeConfig.MAX_IMAGES,
                    usage
            );
        }
        return ImageReader.newInstance(
                ProbeConfig.WIDTH,
                ProbeConfig.HEIGHT,
                PixelFormat.RGBA_8888,
                ProbeConfig.MAX_IMAGES
        );
    }

    private long updateLatestFrame(Image image) throws Exception {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * ProbeConfig.WIDTH;
        int bitmapWidth = ProbeConfig.WIDTH + Math.max(0, rowPadding / Math.max(1, pixelStride));

        ByteBuffer sample = buffer.duplicate();
        long nonZero = 0;
        int step = Math.max(4, sample.capacity() / 4096);
        for (int i = 0; i < sample.capacity(); i += step) {
            if ((sample.get(i) & 0xff) != 0) {
                nonZero++;
            }
        }

        long now = System.currentTimeMillis();
        boolean shouldUpdatePreview = now - latestJpegTimeMs >= ProbeConfig.PREVIEW_FRAME_MIN_INTERVAL_MS;
        boolean shouldWriteLegacyFile = now - lastLegacyFrameFileMs >= ProbeConfig.LEGACY_FRAME_FILE_INTERVAL_MS;
        if (!shouldUpdatePreview && !shouldWriteLegacyFile) {
            return nonZero;
        }

        buffer.rewind();
        Bitmap full = Bitmap.createBitmap(bitmapWidth, ProbeConfig.HEIGHT, Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buffer);
        Bitmap cropped = full;
        if (bitmapWidth != ProbeConfig.WIDTH) {
            cropped = Bitmap.createBitmap(full, 0, 0, ProbeConfig.WIDTH, ProbeConfig.HEIGHT);
            full.recycle();
        }
        try {
            if (shouldUpdatePreview) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(160 * 1024);
                Bitmap preview = cropped;
                if (cropped.getWidth() != ProbeConfig.PREVIEW_WIDTH
                        || cropped.getHeight() != ProbeConfig.PREVIEW_HEIGHT) {
                    preview = Bitmap.createScaledBitmap(
                            cropped,
                            ProbeConfig.PREVIEW_WIDTH,
                            ProbeConfig.PREVIEW_HEIGHT,
                            true
                    );
                }
                try {
                    preview.compress(Bitmap.CompressFormat.JPEG, ProbeConfig.PREVIEW_JPEG_QUALITY, output);
                } finally {
                    if (preview != cropped) {
                        preview.recycle();
                    }
                }
                synchronized (latestJpegLock) {
                    latestJpeg = output.toByteArray();
                    latestJpegTimeMs = now;
                    latestJpegSequence++;
                }
            }
            if (shouldWriteLegacyFile) {
                writeBitmapPng(cropped, ProbeConfig.FRAME_FILE);
                lastLegacyFrameFileMs = now;
            }
        } finally {
            cropped.recycle();
        }
        return nonZero;
    }

    private void writeBitmapPng(Bitmap bitmap, File target) throws Exception {
        synchronized (frameFileLock) {
            FileOutputStream output = new FileOutputStream(target);
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally {
                output.close();
            }
        }
    }

    private void copyFile(File source, File target) {
        if (!source.exists()) {
            logger.log("copy skipped, missing source=" + source.getAbsolutePath());
            return;
        }
        synchronized (frameFileLock) {
            byte[] buffer = new byte[64 * 1024];
            try {
                java.io.FileInputStream input = new java.io.FileInputStream(source);
                try {
                    FileOutputStream output = new FileOutputStream(target);
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
}
