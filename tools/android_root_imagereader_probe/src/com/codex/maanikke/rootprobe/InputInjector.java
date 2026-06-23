package com.codex.maanikke.rootprobe;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

final class InputInjector {
    private final Context shellContext;
    private final ProbeLogger logger;
    private long touchDownTime = 0;

    InputInjector(Context shellContext, ProbeLogger logger) {
        this.shellContext = shellContext;
        this.logger = logger;
    }

    boolean injectTouch(int action, int x, int y, int displayId, boolean waitForFinish) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN || touchDownTime == 0) {
            touchDownTime = now;
        }
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = Math.max(0, x);
        coords.y = Math.max(0, y);
        coords.pressure = action == MotionEvent.ACTION_UP ? 0.0f : 1.0f;
        coords.size = 1.0f;

        MotionEvent event = MotionEvent.obtain(
                touchDownTime,
                now,
                action,
                1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords},
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
        );
        try {
            Method setDisplayId = InputEvent.class.getMethod("setDisplayId", int.class);
            setDisplayId.invoke(event, displayId);
            Object inputManager = shellContext.getSystemService(Context.INPUT_SERVICE);
            Method inject = android.hardware.input.InputManager.class
                    .getMethod("injectInputEvent", InputEvent.class, int.class);
            int mode = waitForFinish ? 2 : 0;
            boolean result = (Boolean) inject.invoke(inputManager, event, mode);
            logger.log("injectTouch action=" + action + " displayId=" + displayId + " result=" + result);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchDownTime = 0;
            }
            return result;
        } finally {
            event.recycle();
        }
    }

    boolean injectKey(int keyCode, int action, int displayId, boolean waitForFinish) throws Exception {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(
                now,
                now,
                action,
                keyCode,
                0,
                0,
                -1,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
        );
        Method setDisplayId = InputEvent.class.getMethod("setDisplayId", int.class);
        setDisplayId.invoke(event, displayId);
        Object inputManager = shellContext.getSystemService(Context.INPUT_SERVICE);
        Method inject = android.hardware.input.InputManager.class
                .getMethod("injectInputEvent", InputEvent.class, int.class);
        int mode = waitForFinish ? 2 : 0;
        boolean result = (Boolean) inject.invoke(inputManager, event, mode);
        logger.log("injectKey action=" + action + " keyCode=" + keyCode
                + " displayId=" + displayId + " result=" + result);
        return result;
    }
}
