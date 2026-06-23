package com.codex.maanikke.probe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

public final class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "MaaNikkeVDProbe";
    private static final int VD_WIDTH = 1280;
    private static final int VD_HEIGHT = 720;
    private static final int VD_DPI = 160;

    private TextureView preview;
    private TextView status;
    private VirtualDisplay virtualDisplay;
    private Surface virtualSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildView());
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        releaseVirtualDisplay();
        super.onDestroy();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        createVirtualDisplay(texture);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        log("preview resized: " + width + "x" + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        releaseVirtualDisplay();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // No-op. This callback confirms frames are reaching the preview.
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(24, 18, 24, 18);
        root.setBackgroundColor(Color.rgb(18, 22, 28));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setText("waiting for 1280x720 surface...");
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        preview = new TextureView(this);
        preview.setSurfaceTextureListener(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(960, 540);
        previewParams.topMargin = 16;
        previewParams.bottomMargin = 16;
        root.addView(preview, previewParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button recreate = new Button(this);
        recreate.setText("Recreate");
        recreate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                releaseVirtualDisplay();
                SurfaceTexture texture = preview.getSurfaceTexture();
                if (texture != null) {
                    createVirtualDisplay(texture);
                }
            }
        });
        buttons.addView(recreate);

        Button release = new Button(this);
        release.setText("Release");
        release.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                releaseVirtualDisplay();
            }
        });
        buttons.addView(release);

        Button capture = new Button(this);
        capture.setText("Capture");
        capture.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                capturePreview();
            }
        });
        buttons.addView(capture);

        root.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return root;
    }

    private void createVirtualDisplay(SurfaceTexture texture) {
        releaseVirtualDisplay();
        try {
            texture.setDefaultBufferSize(VD_WIDTH, VD_HEIGHT);
            virtualSurface = new Surface(texture);

            DisplayManager displayManager = getSystemService(DisplayManager.class);
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
            virtualDisplay = displayManager.createVirtualDisplay(
                    "MaaNikkeProbe-1280x720",
                    VD_WIDTH,
                    VD_HEIGHT,
                    VD_DPI,
                    virtualSurface,
                    flags
            );

            if (virtualDisplay == null) {
                log("createVirtualDisplay returned null");
                return;
            }

            int displayId = virtualDisplay.getDisplay().getDisplayId();
            log("created displayId=" + displayId + " size=" + VD_WIDTH + "x" + VD_HEIGHT + " dpi=" + VD_DPI);
        } catch (Throwable error) {
            log("failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void releaseVirtualDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (virtualSurface != null) {
            virtualSurface.release();
            virtualSurface = null;
        }
        log("released");
    }

    private void log(String message) {
        String line = TAG + ": " + message;
        Log.i(TAG, message);
        if (status != null) {
            status.setText(line);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("capture", false)) {
            preview.postDelayed(new Runnable() {
                @Override
                public void run() {
                    capturePreview();
                }
            }, 800);
        }
    }

    private void capturePreview() {
        try {
            Bitmap bitmap = preview.getBitmap(VD_WIDTH, VD_HEIGHT);
            if (bitmap == null) {
                log("capture failed: bitmap is null");
                return;
            }

            File dir = getExternalFilesDir(null);
            if (dir == null) {
                dir = getFilesDir();
            }
            File file = new File(dir, "maanikke_probe_preview.png");
            FileOutputStream output = new FileOutputStream(file);
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally {
                output.close();
                bitmap.recycle();
            }
            log("captured preview: " + file.getAbsolutePath());
        } catch (Throwable error) {
            log("capture failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }
}
