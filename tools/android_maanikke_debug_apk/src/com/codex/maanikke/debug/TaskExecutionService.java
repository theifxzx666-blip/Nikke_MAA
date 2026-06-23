package com.codex.maanikke.debug;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class TaskExecutionService extends Service {
    static final String ACTION_KEEP_ALIVE = "com.codex.maanikke.debug.ACTION_KEEP_ALIVE";
    static final String ACTION_STOP = "com.codex.maanikke.debug.ACTION_STOP";
    static final String ACTION_TEST_NOTIFICATION = "com.codex.maanikke.debug.ACTION_TEST_NOTIFICATION";
    static final String EXTRA_TEXT = "extra_text";
    private static final String CHANNEL_ID = "maanikke_debug_keep_alive";
    private static final int NOTIFICATION_ID = 1001;
    private static final int TEST_NOTIFICATION_ID = 1002;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureChannel();
        if (intent != null && ACTION_TEST_NOTIFICATION.equals(intent.getAction())) {
            String text = intent.getStringExtra(EXTRA_TEXT);
            if (text == null || text.length() == 0) {
                text = "MaaNikke 测试通知";
            }
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(TEST_NOTIFICATION_ID, buildNotification(text, false));
            }
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String text = "MaaNikke 后台运行中";
        if (intent != null) {
            String extra = intent.getStringExtra(EXTRA_TEXT);
            if (extra != null && extra.length() > 0) {
                text = extra;
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification(text, true));
        return START_STICKY;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL_ID, "MaaNikke Debug", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("MaaNikke debug keep alive");
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text, boolean ongoing) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("MaaNikke Debug")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(ongoing)
                .build();
    }
}
