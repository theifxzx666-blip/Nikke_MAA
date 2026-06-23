package com.codex.maanikke.rootprobe;

import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.view.Surface;

import java.lang.reflect.Constructor;

final class VirtualDisplayController {
    private final ProbeLogger logger;

    VirtualDisplayController(ProbeLogger logger) {
        this.logger = logger;
    }

    VirtualDisplay create(Context context, Surface surface) throws Exception {
        int fullFlags = buildMeowFlags();
        try {
            logger.log("try createVirtualDisplay flags=0x" + Integer.toHexString(fullFlags));
            return createWithFlags(context, surface, fullFlags);
        } catch (Throwable error) {
            logger.log("full flags failed: " + error.getClass().getName() + ": " + error.getMessage());
            int fallbackFlags = 1 | 2 | 8;
            logger.log("try fallback flags=0x" + Integer.toHexString(fallbackFlags));
            return createWithFlags(context, surface, fallbackFlags);
        }
    }

    private VirtualDisplay createWithFlags(Context context, Surface surface, int flags) throws Exception {
        Constructor<android.hardware.display.DisplayManager> ctor =
                android.hardware.display.DisplayManager.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        android.hardware.display.DisplayManager dm = ctor.newInstance(context);
        return dm.createVirtualDisplay(
                ProbeConfig.VD_NAME,
                ProbeConfig.WIDTH,
                ProbeConfig.HEIGHT,
                ProbeConfig.DPI,
                surface,
                flags
        );
    }

    private int buildMeowFlags() {
        int flags = 0;
        flags |= 1;          // PUBLIC
        flags |= 1 << 1;     // PRESENTATION
        flags |= 1 << 3;     // OWN_CONTENT_ONLY
        flags |= 1 << 6;     // SUPPORTS_TOUCH
        flags |= 1 << 8;     // DESTROY_CONTENT_ON_REMOVAL
        if (Build.VERSION.SDK_INT >= 33) {
            flags |= 1 << 10; // TRUSTED
            flags |= 1 << 11; // OWN_DISPLAY_GROUP
            flags |= 1 << 12; // ALWAYS_UNLOCKED
            flags |= 1 << 13; // TOUCH_FEEDBACK_DISABLED
        }
        if (Build.VERSION.SDK_INT >= 34) {
            flags |= 1 << 14; // OWN_FOCUS
            flags |= 1 << 15; // DEVICE_DISPLAY_GROUP
            flags |= 1 << 16; // STEAL_TOP_FOCUS_DISABLED
        }
        return flags;
    }
}
