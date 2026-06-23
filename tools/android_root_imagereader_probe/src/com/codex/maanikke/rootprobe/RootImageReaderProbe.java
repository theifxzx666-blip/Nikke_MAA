package com.codex.maanikke.rootprobe;

import android.hardware.display.VirtualDisplay;
import android.os.Process;
import android.view.MotionEvent;

import java.io.FileWriter;
import java.util.concurrent.TimeUnit;

public final class RootImageReaderProbe {
    private final ProbeLogger logger = new ProbeLogger(ProbeConfig.LOG_FILE);
    private int displayId = -1;
    private boolean touchProbeAttempted = false;
    private boolean touchDownResult = false;
    private boolean touchUpResult = false;
    private long touchBeforeNonZeroSamples = 0;
    private long touchAfterNonZeroSamples = 0;

    public static void main(String[] args) throws Exception {
        new RootImageReaderProbe().run();
    }

    private void run() throws Exception {
        resetOutputFiles();
        logger.log("start pid=" + Process.myPid() + " uid=" + Process.myUid());

        AndroidShellEnvironment environment = new AndroidShellEnvironment(logger);
        environment.prepare();

        FrameCaptureBackend capture = new FrameCaptureBackend(logger);
        capture.start();
        PreviewFrameServer previewServer = new PreviewFrameServer(capture, logger);
        previewServer.start();

        VirtualDisplay virtualDisplay = null;
        try {
            virtualDisplay = new VirtualDisplayController(logger)
                    .create(environment.getShellContext(), capture.getSurface());
            displayId = virtualDisplay.getDisplay().getDisplayId();
            logger.log("virtual display id=" + displayId
                    + " size=" + ProbeConfig.WIDTH + "x" + ProbeConfig.HEIGHT
                    + " dpi=" + ProbeConfig.DPI);

            new GameLauncher(environment).startOnDisplay(displayId);
            capture.awaitFirstFrame(10, TimeUnit.SECONDS);

            InputInjector input = new InputInjector(environment.getShellContext(), logger);
            for (int i = 0; i < ProbeConfig.CAPTURE_SECONDS; i += 5) {
                Thread.sleep(5000);
                maybeRunTouchProbe(capture, input, i + 5);
                writeResult(capture, "capturing_" + (i + 5) + "s");
                logger.log("progress seconds=" + (i + 5)
                        + " frames=" + capture.getFrameCount()
                        + " nonBlackFrames=" + capture.getNonBlackFrameCount()
                        + " lastNonZeroSamples=" + capture.getLastNonZeroSamples());
            }
            writeResult(capture, "finished");
        } finally {
            previewServer.close();
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
            capture.close();
            logger.log("released");
        }
    }

    private void resetOutputFiles() {
        ProbeConfig.LOG_FILE.delete();
        ProbeConfig.FRAME_FILE.delete();
        ProbeConfig.BEFORE_TOUCH_FILE.delete();
        ProbeConfig.AFTER_TOUCH_FILE.delete();
        ProbeConfig.RESULT_FILE.delete();
        logger.reset();
    }

    private void maybeRunTouchProbe(FrameCaptureBackend capture, InputInjector input, int seconds) {
        if (touchProbeAttempted || displayId < 0) {
            return;
        }
        if (seconds < 25 || capture.getLastNonZeroSamples() < 2500) {
            return;
        }
        touchProbeAttempted = true;
        touchBeforeNonZeroSamples = capture.getLastNonZeroSamples();
        capture.copyLatestFrameTo(ProbeConfig.BEFORE_TOUCH_FILE);
        logger.log("touch probe start at seconds=" + seconds
                + " x=" + ProbeConfig.TOUCH_X
                + " y=" + ProbeConfig.TOUCH_Y
                + " beforeNonZero=" + touchBeforeNonZeroSamples);
        try {
            touchDownResult = input.injectTouch(
                    MotionEvent.ACTION_DOWN,
                    ProbeConfig.TOUCH_X,
                    ProbeConfig.TOUCH_Y,
                    displayId,
                    true
            );
            Thread.sleep(120);
            touchUpResult = input.injectTouch(
                    MotionEvent.ACTION_UP,
                    ProbeConfig.TOUCH_X,
                    ProbeConfig.TOUCH_Y,
                    displayId,
                    false
            );
            Thread.sleep(5000);
            touchAfterNonZeroSamples = capture.getLastNonZeroSamples();
            capture.copyLatestFrameTo(ProbeConfig.AFTER_TOUCH_FILE);
            logger.log("touch probe done down=" + touchDownResult
                    + " up=" + touchUpResult
                    + " afterNonZero=" + touchAfterNonZeroSamples);
        } catch (Throwable error) {
            logger.log("touch probe failed: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private void writeResult(FrameCaptureBackend capture, String phase) {
        try {
            FileWriter writer = new FileWriter(ProbeConfig.RESULT_FILE, false);
            try {
                writer.write("phase=" + phase + "\n");
                writer.write("displayId=" + displayId + "\n");
                writer.write("frames=" + capture.getFrameCount() + "\n");
                writer.write("nonBlackFrames=" + capture.getNonBlackFrameCount() + "\n");
                writer.write("lastNonZeroSamples=" + capture.getLastNonZeroSamples() + "\n");
                writer.write("touchProbeAttempted=" + touchProbeAttempted + "\n");
                writer.write("touchX=" + ProbeConfig.TOUCH_X + "\n");
                writer.write("touchY=" + ProbeConfig.TOUCH_Y + "\n");
                writer.write("touchDownResult=" + touchDownResult + "\n");
                writer.write("touchUpResult=" + touchUpResult + "\n");
                writer.write("touchBeforeNonZeroSamples=" + touchBeforeNonZeroSamples + "\n");
                writer.write("touchAfterNonZeroSamples=" + touchAfterNonZeroSamples + "\n");
                writer.write("frameFile=" + ProbeConfig.FRAME_FILE.getAbsolutePath() + "\n");
                writer.write("beforeTouchFile=" + ProbeConfig.BEFORE_TOUCH_FILE.getAbsolutePath() + "\n");
                writer.write("afterTouchFile=" + ProbeConfig.AFTER_TOUCH_FILE.getAbsolutePath() + "\n");
                writer.write("logFile=" + ProbeConfig.LOG_FILE.getAbsolutePath() + "\n");
            } finally {
                writer.close();
            }
        } catch (Throwable error) {
            logger.log("writeResult failed: " + error.getMessage());
        }
    }
}
