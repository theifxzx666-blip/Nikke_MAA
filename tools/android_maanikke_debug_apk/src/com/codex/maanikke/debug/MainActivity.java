package com.codex.maanikke.debug;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final String ASSET_JAR = "maanikke-root-ir-probe.jar";
    private static final String REMOTE_JAR = "/data/local/tmp/maanikke-root-ir-probe.jar";
    private static final String REMOTE_RUNNER = "/data/local/tmp/maanikke_run_probe_env.sh";
    private static final String REMOTE_RESULT = "/data/local/tmp/maanikke_root_ir_result.txt";
    private static final String REMOTE_LOG = "/data/local/tmp/maanikke_root_ir_probe.log";
    private static final String REMOTE_LAST = "/data/local/tmp/maanikke_root_ir_last.png";
    private static final String REMOTE_BEFORE = "/data/local/tmp/maanikke_root_ir_before_touch.png";
    private static final String REMOTE_AFTER = "/data/local/tmp/maanikke_root_ir_after_touch.png";
    private static final String REMOTE_TASK_RESULT = "/data/local/tmp/maanikke_task_result.txt";
    private static final String REMOTE_TASK_LOG = "/data/local/tmp/maanikke_task_runner.log";
    private static final String REMOTE_TASK_FRAME = "/data/local/tmp/maanikke_task_frame.png";
    private static final String REMOTE_TASK_BEFORE = "/data/local/tmp/maanikke_task_before_action.png";
    private static final String REMOTE_TASK_AFTER = "/data/local/tmp/maanikke_task_after_action.png";
    private static final String REMOTE_TASK_AFTER_OPEN = "/data/local/tmp/maanikke_task_after_open.png";
    private static final String REMOTE_TASK_AFTER_CLOSE = "/data/local/tmp/maanikke_task_after_close.png";
    private static final String REMOTE_TASK_AFTER_BACK = "/data/local/tmp/maanikke_task_after_back.png";
    private static final String REMOTE_TASK_AFTER_WAIT = "/data/local/tmp/maanikke_task_after_wait.png";
    private static final String REMOTE_TASK_AFTER_ENTER = "/data/local/tmp/maanikke_task_after_enter.png";
    private static final String REMOTE_TASK_AFTER_DOWNLOAD_CONFIRM = "/data/local/tmp/maanikke_task_after_download_confirm.png";
    private static final String REMOTE_TASK_AFTER_HOME_POPUP = "/data/local/tmp/maanikke_task_after_home_popup.png";
    private static final String REMOTE_TASK_AFTER_UPDATE = "/data/local/tmp/maanikke_task_after_update.png";
    private static final String REMOTE_TASK_AFTER_MAIL_OPEN = "/data/local/tmp/maanikke_task_after_mail_open.png";
    private static final String REMOTE_TASK_AFTER_MAIL_CLAIM = "/data/local/tmp/maanikke_task_after_mail_claim.png";
    private static final String REMOTE_TASK_AFTER_MAIL_CONFIRM = "/data/local/tmp/maanikke_task_after_mail_confirm.png";
    private static final String REMOTE_BACKEND_STDOUT = "/data/local/tmp/maanikke_backend_stdout.log";
    private static final String REMOTE_TASK_OPTIONS = "/data/local/tmp/maanikke_task_options.properties";
    private static final String REMOTE_TARGET_PACKAGE = "/data/local/tmp/maanikke_target_package.txt";
    private static final String REMOTE_TARGET_COMPONENT = "/data/local/tmp/maanikke_target_component.txt";
    private static final String PREVIEW_SOCKET_NAME = "maanikke_preview_frame";
    private static final String PREFS_NAME = "maanikke_debug_prefs";
    private static final String PREF_BACKGROUND_MODE = "background_mode";
    private static final String PREF_DEBUG_MODE = "debug_mode";
    private static final String PREF_BACKEND_MODE = "backend_mode";
    private static final String BACKEND_MODE_ROOT = "root";
    private static final String BACKEND_MODE_SHIZUKU = "shizuku";
    private static final String PREF_TASK_OPTION_PREFIX = "task_option_";
    private static final String PREF_PERMISSION_EXPANDED = "permission_expanded";
    private static final String PREF_SCHEDULE_ENABLED = "schedule_enabled";
    private static final String PREF_SCHEDULE_HOUR = "schedule_hour";
    private static final String PREF_SCHEDULE_MINUTE = "schedule_minute";
    private static final String PREF_SCHEDULE_FORCE_START = "schedule_force_start";
    private static final String PREF_NOTIFY_INTERNAL = "notify_internal";
    private static final String PREF_NOTIFY_POPUP = "notify_popup";
    private static final String PREF_NOTIFY_COMPLETE = "notify_complete";
    private static final String PREF_NOTIFY_ERROR = "notify_error";
    private static final String PREF_NOTIFY_SERVICE_STOP = "notify_service_stop";
    private static final String PREF_NOTIFY_ATTACH_LOG = "notify_attach_log";
    private static final String PREF_NOTIFY_DINGTALK = "notify_dingtalk";
    private static final String PREF_NOTIFY_WEBHOOK = "notify_webhook";
    private static final String PREF_NOTIFY_DINGTALK_WEBHOOK = "notify_dingtalk_webhook";
    private static final String PREF_NOTIFY_DINGTALK_TOKEN = "notify_dingtalk_token";
    private static final String PREF_NOTIFY_DINGTALK_SECRET = "notify_dingtalk_secret";
    private static final String PREF_NOTIFY_CUSTOM_WEBHOOK_URL = "notify_custom_webhook_url";
    private static final String PUBLIC_BASE_DIR = "MaaNikke";
    private static final String PUBLIC_EXPORT_DIR = "exports";
    private static final String PUBLIC_LOG_DIR = "logs";
    private static final String PUBLIC_SCREENSHOT_DIR = "screenshots";
    private static final String PUBLIC_APK_DIR = "apk";
    private static final String TEXT_WAITING_PREVIEW = "\u7b49\u5f85\u5b9e\u65f6\u753b\u9762";
    private static final String TEXT_STARTING_GAME = "\u6b63\u5728\u542f\u52a8\u6e38\u620f";
    private static final String TEXT_STOPPING_GAME = "\u6b63\u5728\u7ed3\u675f\u6e38\u620f";
    private static final String TEXT_CONNECTING_PREVIEW = "\u6b63\u5728\u8fde\u63a5\u5b9e\u65f6\u753b\u9762";
    private static final int SCHEDULE_REQUEST_CODE = 20260618;
    private static final int REQ_SHIZUKU = 1001;
    private static final int REQ_STORAGE = 1002;
    private static final int REQ_NOTIFICATION = 1003;
    private static final String[] NIKKE_PACKAGE_CANDIDATES = new String[]{
            "com.tencent.nikke",
            "com.proximabeta.nikke",
            "com.levelinfinite.nikke",
            "com.shiftup.nikke",
            "com.gamamobi.nikke",
            "com.hottag.nikke"
    };
    private static final int PAGE_HOME = 0;
    private static final int PAGE_TASKS = 1;
    private static final int PAGE_LOGS = 2;
    private static final int PAGE_SETTINGS = 3;
    private static final int TASK_TAB_DAILY = 0;
    private static final int TASK_TAB_AUTO = 1;
    private static final int TASK_TAB_TOOLS = 2;
    private static final int TASK_TAB_LOGS = 3;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean previewRefreshing = new AtomicBoolean(false);
    private final AtomicBoolean previewRenderScheduled = new AtomicBoolean(false);
    private final Object previewFrameLock = new Object();
    private final BitmapFactory.Options previewDecodeOptions = new BitmapFactory.Options();
    private final Runnable previewLoadingTicker = new Runnable() {
        @Override
        public void run() {
            if (previewLoadingOverlay == null || previewLoadingOverlay.getVisibility() != View.VISIBLE) {
                return;
            }
            previewLoadingTick = (previewLoadingTick + 1) % 4;
            updatePreviewLoadingLabel();
            mainHandler.postDelayed(this, 480);
        }
    };
    private TextView statusText;
    private TextView phaseText;
    private TextView frameText;
    private LinearLayout previewLoadingOverlay;
    private TextView previewLoadingText;
    private TextView debugPreviewMarker;
    private TextView taskValueText;
    private TextView displayValueText;
    private TextView framesValueText;
    private TextView stateValueText;
    private TextView actionValueText;
    private TextView activeTaskChip;
    private TextView backgroundModeText;
    private TextView debugModeText;
    private TextView backendModeText;
    private TextView profileModeText;
    private TextView shizukuPermissionText;
    private TextView storagePermissionText;
    private TextView packageListPermissionText;
    private TextView notificationPermissionText;
    private TextView batteryPermissionText;
    private ImageView previewImage;
    private int previewLoadingTick = 0;
    private String previewLoadingBaseText = "";
    private CheckBox backgroundModeCheckBox;
    private CheckBox debugModeCheckBox;
    private volatile boolean backgroundMode = false;
    private volatile boolean debugMode = true;
    private volatile String backendMode = BACKEND_MODE_ROOT;
    private Button startGameButton;
    private Button workflowButton;
    private Button stopButton;
    private Button stopGameButton;
    private Button logExportButton;
    private TextView permissionExpandText;
    private LinearLayout permissionExtraContainer;
    private TextView scheduleStatusText;
    private TextView scheduleTimeText;
    private CheckBox scheduleEnableCheckBox;
    private CheckBox scheduleForceStartCheckBox;
    private CheckBox notifyInternalCheckBox;
    private CheckBox notifyPopupCheckBox;
    private CheckBox notifyCompleteCheckBox;
    private CheckBox notifyErrorCheckBox;
    private CheckBox notifyServiceStopCheckBox;
    private CheckBox notifyAttachLogCheckBox;
    private CheckBox notifyDingTalkCheckBox;
    private CheckBox notifyWebhookCheckBox;
    private EditText notifyDingTalkWebhookInput;
    private EditText notifyDingTalkTokenInput;
    private EditText notifyDingTalkSecretInput;
    private EditText notifyCustomWebhookInput;
    private final List<WorkflowItem> workflowItems = new ArrayList<WorkflowItem>();
    private final List<TextView> navItems = new ArrayList<TextView>();
    private final List<TextView> taskTabViews = new ArrayList<TextView>();
    private final List<TextView> logViews = new ArrayList<TextView>();
    private final List<ScrollView> logScrollViews = new ArrayList<ScrollView>();
    private final StringBuilder logBuffer = new StringBuilder();
    private LinearLayout homePage;
    private LinearLayout taskPage;
    private LinearLayout logPage;
    private LinearLayout settingsPage;
    private LinearLayout taskListContainer;
    private LinearLayout taskConfigPanel;
    private LinearLayout taskAutoPanel;
    private LinearLayout taskToolsPanel;
    private LinearLayout taskInlineLogPanel;
    private LinearLayout actionBar;
    private WorkflowItem draggingWorkflowItem;
    private int currentPage = PAGE_HOME;
    private int currentTaskTab = TASK_TAB_DAILY;
    private volatile boolean running = false;
    private volatile boolean stopRequested = false;
    private volatile boolean previewActive = false;
    private volatile boolean previewTaskMode = false;
    private volatile int previewLoopGeneration = 0;
    private volatile boolean previewHasFrame = false;
    private byte[] pendingPreviewBytes = null;
    private String pendingPreviewInfo = "";
    private String lastPreviewStamp = "";
    private int lastSocketPreviewSeq = -1;
    private long lastSocketPreviewFrameMs = 0;
    private long lastTaskPreviewFallbackMs = 0;
    private long lastPhasePollMs = 0;
    private long lastPreviewAttachCheckMs = 0;
    private String lastTaskResultLogKey = "";
    private String lastProbeResultLogKey = "";
    private String lastBattleFailedNotifyKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        dismissKeyguardIfPossible();
        backgroundMode = getPreferencesStore().getBoolean(PREF_BACKGROUND_MODE, false);
        debugMode = getPreferencesStore().getBoolean(PREF_DEBUG_MODE, true);
        backendMode = getPreferencesStore().getString(PREF_BACKEND_MODE, BACKEND_MODE_ROOT);
        previewDecodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        setContentView(buildView());
        setupShizukuCallbacks();
        append("应用已就绪：已加载 MaaNikke PC 任务目录，Android 已适配 "
                + TaskCatalog.enabledCount() + " / " + TaskCatalog.PC_TASKS.length + " 个入口。");
        refreshPermissionStatus();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if ("com.codex.maanikke.debug.RUN_PROBE".equals(intent.getAction())) {
            runRootProbe();
        } else if ("com.codex.maanikke.debug.RUN_TASK".equals(intent.getAction())) {
            String taskId = intent.getStringExtra("task_id");
            if (taskId != null && isSafeTaskArg(taskId)) {
                runBackendTask(taskId);
            } else {
                append("RUN_TASK missing safe task_id extra.");
            }
        } else if ("com.codex.maanikke.debug.RUN_START_GAME".equals(intent.getAction())) {
            runBackendTask("start_game");
        } else if ("com.codex.maanikke.debug.RUN_STOP_GAME".equals(intent.getAction())) {
            runBackendTask("stop_game");
        } else if ("com.codex.maanikke.debug.RUN_BACK_TO_HOME".equals(intent.getAction())) {
            runBackendTask("back_to_home");
        } else if ("com.codex.maanikke.debug.RUN_HANDLE_UPDATE".equals(intent.getAction())) {
            runBackendTask("handle_update");
        } else if ("com.codex.maanikke.debug.RUN_CLAIM_MAIL".equals(intent.getAction())) {
            runBackendTask("claim_mail");
        } else if ("com.codex.maanikke.debug.RUN_CLAIM_DAILY_REWARDS".equals(intent.getAction())) {
            runBackendTask("claim_daily_rewards");
        } else if ("com.codex.maanikke.debug.RUN_CLAIM_FRIEND_POINTS".equals(intent.getAction())) {
            runBackendTask("claim_friend_points");
        } else if ("com.codex.maanikke.debug.RUN_CLAIM_OUTPOST_DEFENSE".equals(intent.getAction())) {
            runBackendTask("claim_outpost_defense");
        } else if ("com.codex.maanikke.debug.RUN_CLAIM_PASS_REWARDS".equals(intent.getAction())) {
            runBackendTask("claim_pass_rewards");
        } else if ("com.codex.maanikke.debug.RUN_VISIT_MAIL".equals(intent.getAction())) {
            runBackendTask("visit_mail");
        } else if ("com.codex.maanikke.debug.RUN_VISIT_DAILY_REWARDS".equals(intent.getAction())) {
            runBackendTask("visit_daily_rewards");
        } else if ("com.codex.maanikke.debug.RUN_VISIT_FRIEND_POINTS".equals(intent.getAction())) {
            runBackendTask("visit_friend_points");
        } else if ("com.codex.maanikke.debug.RUN_VISIT_FREE_SHOP".equals(intent.getAction())) {
            runBackendTask("visit_free_shop");
        } else if ("com.codex.maanikke.debug.RUN_VISIT_OUTPOST_DEFENSE".equals(intent.getAction())) {
            runBackendTask("visit_outpost_defense");
        } else if ("com.codex.maanikke.debug.RUN_WORKFLOW_DAILY_SAFE".equals(intent.getAction())) {
            boolean scheduled = intent.getBooleanExtra("scheduled", false);
            if (scheduled) {
                updateScheduleAlarmFromPrefs();
            }
            runWorkflow(scheduled);
        } else if (!running && hasActiveBackendTaskQuietly()) {
            switchPage(PAGE_TASKS);
            setStatus("Preview", 0xff2563eb);
            setPhase("实时画面恢复中");
            startPreviewLoop(true, TEXT_CONNECTING_PREVIEW);
        }
    }

    private void dismissKeyguardIfPossible() {
        try {
            KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguard != null) {
                keyguard.requestDismissKeyguard(this, null);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        previewActive = false;
        executor.shutdownNow();
        controlExecutor.shutdownNow();
        previewExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
        if (!running && hasActiveBackendTaskQuietly()) {
            switchPage(PAGE_TASKS);
            setStatus("Preview", 0xff2563eb);
            setPhase("实时画面恢复中");
            startPreviewLoop(true, TEXT_CONNECTING_PREVIEW);
            return;
        }
        if (running && previewActive) {
            restartPreviewConnection("前台恢复，正在重连实时画面。");
        }
    }

    @Override
    protected void onPause() {
        if (running && previewActive) {
            previewLoopGeneration++;
            lastSocketPreviewSeq = -1;
            synchronized (previewFrameLock) {
                pendingPreviewBytes = null;
                pendingPreviewInfo = "";
            }
        }
        super.onPause();
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff7f4ef);

        LinearLayout contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(dp(16), dp(14), dp(16), 0);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        buildHeader(contentHost);

        FrameLayout pageHost = new FrameLayout(this);
        contentHost.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        homePage = scrollPageContainer(pageHost);
        taskPage = pageContainer(pageHost);
        logPage = scrollPageContainer(pageHost);
        settingsPage = scrollPageContainer(pageHost);

        buildHomePage(homePage);
        buildTaskPage(taskPage);
        buildLogPage(logPage);
        buildSettingsPage(settingsPage);

        actionBar = bottomActionBar();
        root.addView(actionBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));
        root.addView(bottomNavigation(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));
        switchPage(PAGE_HOME);
        return root;
    }

    private LinearLayout pageContainer(FrameLayout parent) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, dp(10));
        parent.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return page;
    }

    private LinearLayout scrollPageContainer(FrameLayout parent) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, dp(10));
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        parent.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return page;
    }

    private void buildHeader(LinearLayout content) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("MaaNikke");
        title.setTextSize(25);
        title.setTextColor(0xff151923);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        titleBox.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("MaaMeow 架构适配 / MaaNikke PC 任务目录");
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xff6b7280);
        titleBox.addView(subtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        statusText = pillText("就绪", 0xffe8efff, 0xff2563eb);
        header.addView(statusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
        ));
    }

    private void buildHomePage(LinearLayout content) {
        LinearLayout statusCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams statusCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusCardParams.topMargin = dp(12);
        content.addView(statusCard, statusCardParams);

        TextView statusTitle = sectionLabel("运行概览");
        statusCard.addView(statusTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        phaseText = new TextView(this);
        phaseText.setText("任务空闲");
        phaseText.setTextSize(13);
        phaseText.setTextColor(0xff374151);
        phaseText.setPadding(0, dp(5), 0, dp(7));
        statusCard.addView(phaseText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout metricRow1 = new LinearLayout(this);
        metricRow1.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.addView(metricRow1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        taskValueText = addMetric(metricRow1, "任务", "idle");
        displayValueText = addMetric(metricRow1, "显示", "-");

        framesValueText = addMetric(metricRow1, "帧数", "-");
        actionValueText = addMetric(metricRow1, "动作", "-");
        stateValueText = phaseText;

        LinearLayout baseCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams baseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        baseParams.topMargin = dp(12);
        content.addView(baseCard, baseParams);

        TextView baseTitle = sectionLabel("基础参数");
        baseCard.addView(baseTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addInfoRow(baseCard, "运行模式", "App 内 16:9 虚拟显示");
        addInfoRow(baseCard, "控制器", "root / Shizuku / app_process / ImageReader");
        addInfoRow(baseCard, "显示容器", "1280 x 720 @ 160dpi");
        addInfoRow(baseCard, "目标客户端", "自动解析 NIKKE 包名");
        addInfoRow(baseCard, "资源目录", "assets/MaaSync/MaaResource");
        addInfoRow(baseCard, "安全边界", "登录/网络异常交给用户处理");

        LinearLayout profileCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        profileParams.topMargin = dp(12);
        content.addView(profileCard, profileParams);

        TextView profileTitle = sectionLabel("当前方案");
        profileCard.addView(profileTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        profileModeText = addInfoRow(profileCard, "Profile", buildProfileModeText());
        addInfoRow(profileCard, "任务目录", TaskCatalog.enabledCount() + " / " + TaskCatalog.PC_TASKS.length + " 已启用");
        addInfoRow(profileCard, "截图链路", "LocalSocket JPEG 流式预览");
        addInfoRow(profileCard, "结果分类", "pass / needs_user / fail");

        addAboutCard(content);

        LinearLayout backgroundCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backgroundParams.topMargin = dp(12);
        content.addView(backgroundCard, backgroundParams);

        TextView backgroundTitle = sectionLabel("运行模式");
        backgroundCard.addView(backgroundTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addBackendModeRow(backgroundCard);
        addDebugModeRow(backgroundCard);
        addBackgroundModeRow(backgroundCard);

        LinearLayout permissionCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        permissionParams.topMargin = dp(12);
        content.addView(permissionCard, permissionParams);

        TextView permissionTitle = sectionLabel("权限管理");
        permissionCard.addView(permissionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        shizukuPermissionText = addPermissionRow(permissionCard, "Shizuku 权限管理", "检查中", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestOrOpenShizukuManagement();
            }
        });
        addDivider(permissionCard);
        addActionRow(permissionCard, "Shizuku 通道检测",
                "验证授权、shell 身份和 /data/local/tmp 写入能力。", "检测", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        testShizukuBackendChannel();
                    }
                });
        permissionExpandText = actionText(getPreferencesStore().getBoolean(PREF_PERMISSION_EXPANDED, false)
                ? "收起其他权限" : "展开其他权限");
        permissionExpandText.setGravity(Gravity.CENTER);
        permissionExpandText.setPadding(0, dp(10), 0, dp(2));
        permissionExpandText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setPermissionExpanded(permissionExtraContainer == null
                        || permissionExtraContainer.getVisibility() != View.VISIBLE);
            }
        });
        permissionCard.addView(permissionExpandText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        ));

        permissionExtraContainer = new LinearLayout(this);
        permissionExtraContainer.setOrientation(LinearLayout.VERTICAL);
        permissionCard.addView(permissionExtraContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        storagePermissionText = addPermissionRow(permissionExtraContainer, "存储权限", "检查中", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openStoragePermission();
            }
        });
        packageListPermissionText = addPermissionRow(permissionExtraContainer, "应用安装列表获取", "检查中", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAppDetails();
            }
        });
        notificationPermissionText = addPermissionRow(permissionExtraContainer, "通知权限", "检查中", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openNotificationPermission();
            }
        });
        batteryPermissionText = addPermissionRow(permissionExtraContainer, "后台保活", "检查中", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openBatterySettings();
            }
        });
        setPermissionExpanded(getPreferencesStore().getBoolean(PREF_PERMISSION_EXPANDED, false));
    }

    private void buildTaskPage(LinearLayout content) {
        LinearLayout previewHeader = new LinearLayout(this);
        previewHeader.setOrientation(LinearLayout.HORIZONTAL);
        previewHeader.setGravity(Gravity.CENTER_VERTICAL);
        previewHeader.setPadding(0, dp(12), 0, dp(6));
        content.addView(previewHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView previewTitle = sectionLabel("实时画面");
        previewHeader.addView(previewTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        debugPreviewMarker = pillText("调试", 0xffffeeee, 0xffdc2626);
        debugPreviewMarker.setVisibility(View.GONE);
        LinearLayout.LayoutParams debugMarkerHeaderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(24)
        );
        debugMarkerHeaderParams.leftMargin = dp(8);
        previewHeader.addView(debugPreviewMarker, debugMarkerHeaderParams);

        View previewHeaderSpacer = new View(this);
        previewHeader.addView(previewHeaderSpacer, new LinearLayout.LayoutParams(
                0,
                1,
                1
        ));
        activeTaskChip = pillText("1280 x 720", 0xffedf1fb, 0xff2563eb);
        previewHeader.addView(activeTaskChip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
        ));

        PreviewPane previewShell = new PreviewPane(this, dp(28));
        previewShell.setOrientation(LinearLayout.VERTICAL);
        previewShell.setBackground(roundedBackground(0xffffffff, dp(14), 0x1f000000));
        previewShell.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.addView(previewShell, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout previewFrameHost = new FrameLayout(this);
        previewFrameHost.setBackgroundColor(0xff111111);
        previewShell.addView(previewFrameHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        previewImage = new ImageView(this);
        previewImage.setBackgroundColor(0xff111111);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewFrameHost.addView(previewImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        previewLoadingOverlay = new LinearLayout(this);
        previewLoadingOverlay.setOrientation(LinearLayout.VERTICAL);
        previewLoadingOverlay.setGravity(Gravity.CENTER);
        previewLoadingOverlay.setPadding(dp(14), dp(12), dp(14), dp(12));
        previewLoadingOverlay.setBackground(roundedBackground(0xbf111827, dp(12), 0x33ffffff));
        previewLoadingOverlay.setVisibility(View.GONE);

        ProgressBar previewSpinner = new ProgressBar(this);
        previewSpinner.setIndeterminate(true);
        previewLoadingOverlay.addView(previewSpinner, new LinearLayout.LayoutParams(
                dp(34),
                dp(34)
        ));

        previewLoadingText = new TextView(this);
        previewLoadingText.setText(TEXT_STARTING_GAME);
        previewLoadingText.setTextSize(12);
        previewLoadingText.setTextColor(0xffffffff);
        previewLoadingText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        loadingTextParams.topMargin = dp(8);
        previewLoadingOverlay.addView(previewLoadingText, loadingTextParams);

        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        previewFrameHost.addView(previewLoadingOverlay, loadingParams);

        frameText = new TextView(this);
        frameText.setText("等待实时画面");
        frameText.setTextSize(11);
        frameText.setTextColor(0xff6b7280);
        frameText.setGravity(Gravity.CENTER_VERTICAL);
        frameText.setPadding(dp(8), 0, dp(8), 0);
        frameText.setBackgroundColor(0xfff2f2f2);
        frameText.setText(TEXT_WAITING_PREVIEW);
        previewShell.addView(frameText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)
        ));

        ScrollView taskScroll = new ScrollView(this);
        taskScroll.setFillViewport(false);
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        taskScroll.addView(scrollContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams taskScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        taskScrollParams.topMargin = dp(8);
        content.addView(taskScroll, taskScrollParams);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        scrollContent.addView(tabs, tabsParams);
        tabs.addView(taskTabText("一键日常", TASK_TAB_DAILY), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(taskTabText("自动战斗", TASK_TAB_AUTO), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(taskTabText("小工具", TASK_TAB_TOOLS), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(taskTabText("日志", TASK_TAB_LOGS), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        taskConfigPanel = new LinearLayout(this);
        taskConfigPanel.setOrientation(LinearLayout.VERTICAL);
        scrollContent.addView(taskConfigPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout taskCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams taskCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        taskCardParams.topMargin = dp(12);
        taskConfigPanel.addView(taskCard, taskCardParams);

        TextView taskTitle = sectionLabel("任务配置");
        taskCard.addView(taskTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView taskHint = new TextView(this);
        taskHint.setText("长按左侧排序柄拖动任务；点“明细”查看 PC entry、Android 适配状态和任务选项。");
        taskHint.setTextSize(11);
        taskHint.setTextColor(0xff6b7280);
        taskHint.setPadding(0, dp(5), 0, dp(2));
        taskCard.addView(taskHint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        taskListContainer = new LinearLayout(this);
        taskListContainer.setOrientation(LinearLayout.VERTICAL);
        taskListContainer.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View view, DragEvent event) {
                Object local = event.getLocalState();
                if (!(local instanceof WorkflowItem)) {
                    return false;
                }
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    moveWorkflowItemToLast((WorkflowItem) local);
                    return true;
                }
                if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                    finishTaskDrag();
                    return true;
                }
                return true;
            }
        });
        taskCard.addView(taskListContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        ArrayList<TaskCatalog.TaskSpec> orderedTasks = TaskProfile.load(getPreferencesStore()).orderedTasks();
        for (int i = 0; i < orderedTasks.size(); i++) {
            addWorkflowItem(taskListContainer, orderedTasks.get(i));
        }

        taskAutoPanel = buildAutoBattlePanel();
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        autoParams.topMargin = dp(12);
        scrollContent.addView(taskAutoPanel, autoParams);

        taskToolsPanel = buildToolsPanel();
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        toolsParams.topMargin = dp(12);
        scrollContent.addView(taskToolsPanel, toolsParams);

        taskInlineLogPanel = buildLogCard(dp(180));
        LinearLayout.LayoutParams inlineLogParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inlineLogParams.topMargin = dp(12);
        scrollContent.addView(taskInlineLogPanel, inlineLogParams);
        switchTaskTab(TASK_TAB_DAILY);
    }

    private void buildLogPage(LinearLayout content) {
        content.addView(pageTitle("定时任务"), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout statusCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(6);
        content.addView(statusCard, statusParams);

        scheduleStatusText = addInfoRow(statusCard, "策略状态", buildScheduleStatusText());
        scheduleTimeText = addInfoRow(statusCard, "执行时间", buildScheduleTimeText());
        addInfoRow(statusCard, "任务配置", "使用后台任务页当前勾选顺序");

        LinearLayout strategyCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams strategyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        strategyParams.topMargin = dp(12);
        content.addView(strategyCard, strategyParams);

        scheduleEnableCheckBox = addSwitchRow(strategyCard, "启用定时任务",
                "到点后会通过应用内 Intent 启动默认安全工作流。", PREF_SCHEDULE_ENABLED, false,
                new Runnable() {
                    @Override
                    public void run() {
                        updateScheduleAlarmFromPrefs();
                    }
                });
        addDivider(strategyCard);
        addActionRow(strategyCard, "执行时间", buildScheduleTimeText(), "调整", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openScheduleTimeDialog();
            }
        });
        addDivider(strategyCard);
        scheduleForceStartCheckBox = addSwitchRow(strategyCard, "强制启动",
                "定时触发前先启动 NIKKE，再运行工作流。当前会记录配置，后续随任务链参数使用。",
                PREF_SCHEDULE_FORCE_START, false, new Runnable() {
                    @Override
                    public void run() {
                        refreshScheduleUi();
                    }
                });

        LinearLayout runCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams runParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        runParams.topMargin = dp(12);
        content.addView(runCard, runParams);
        addActionRow(runCard, "立即试跑", "按后台任务页当前勾选顺序执行一次。", "开始", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runWorkflow();
            }
        });
        addDivider(runCard);
        addActionRow(runCard, "保存策略", "保存当前定时开关和时间，并重新注册系统闹钟。", "保存", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateScheduleAlarmFromPrefs();
                append("定时任务配置已保存：" + buildScheduleTimeText());
            }
        });
    }

    private LinearLayout buildAutoBattlePanel() {
        LinearLayout card = cardLayout(0xffffffff, 0x1f000000);
        TextView title = sectionLabel("自动战斗");
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        TextView hint = mutedText("这里只放可单独触发的战斗相关入口；协同作战是限时开放玩法，暂不调整。", 11);
        hint.setPadding(0, dp(5), 0, dp(2));
        card.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addTaskActionRow(card, "模拟室", "visit_sim_room", "需确认，识别不到页面会停止。");
        addDivider(card);
        addTaskActionRow(card, "竞技场", "visit_arena", "需确认，默认只进入任务入口。");
        addDivider(card);
        addTaskActionRow(card, "拦截战", "visit_interception", "需确认，暂不扩展 Boss 策略。");
        addDivider(card);
        addTaskActionRow(card, "爬塔", "visit_climb_tower", "需确认，优先用于每日任务。");
        return card;
    }

    private LinearLayout buildToolsPanel() {
        LinearLayout card = cardLayout(0xffffffff, 0x1f000000);
        TextView title = sectionLabel("小工具");
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        TextView hint = mutedText("这些按钮会直接调用后端单项任务，适合调试导航、更新弹窗和安全页面巡检。", 11);
        hint.setPadding(0, dp(5), 0, dp(2));
        card.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addActionRow(card, "回到首页", "不重启游戏，收敛到大厅锚点。", "执行", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runBackendTask("back_to_home");
            }
        });
        addDivider(card);
        addActionRow(card, "检查更新", "处理资源下载确认；外部商店更新会停止并提示。", "执行", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runBackendTask("handle_update");
            }
        });
        addDivider(card);
        addActionRow(card, "邮箱安全巡检", "只进入邮箱页面，不点击全部领取。", "执行", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runBackendTask("visit_mail");
            }
        });
        addDivider(card);
        addActionRow(card, "导出日志", "保存当前应用日志、后端日志和最后截图。", "导出", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportEvidence();
            }
        });
        return card;
    }

    private String buildScheduleStatusText() {
        return getPreferencesStore().getBoolean(PREF_SCHEDULE_ENABLED, false) ? "已启用" : "未启用";
    }

    private String buildScheduleTimeText() {
        int hour = getPreferencesStore().getInt(PREF_SCHEDULE_HOUR, 4);
        int minute = getPreferencesStore().getInt(PREF_SCHEDULE_MINUTE, 30);
        return String.format(Locale.CHINA, "每天 %02d:%02d", hour, minute);
    }

    private void refreshScheduleUi() {
        if (scheduleStatusText != null) {
            scheduleStatusText.setText(buildScheduleStatusText());
        }
        if (scheduleTimeText != null) {
            scheduleTimeText.setText(buildScheduleTimeText());
        }
        if (scheduleEnableCheckBox != null) {
            scheduleEnableCheckBox.setChecked(getPreferencesStore().getBoolean(PREF_SCHEDULE_ENABLED, false));
        }
        if (scheduleForceStartCheckBox != null) {
            scheduleForceStartCheckBox.setChecked(getPreferencesStore().getBoolean(PREF_SCHEDULE_FORCE_START, false));
        }
    }

    private void openScheduleTimeDialog() {
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(8));

        final EditText hourInput = new EditText(this);
        hourInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        hourInput.setText(String.valueOf(getPreferencesStore().getInt(PREF_SCHEDULE_HOUR, 4)));
        hourInput.setHint("时");
        body.addView(hourInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextSize(20);
        colon.setGravity(Gravity.CENTER);
        body.addView(colon, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));

        final EditText minuteInput = new EditText(this);
        minuteInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minuteInput.setText(String.valueOf(getPreferencesStore().getInt(PREF_SCHEDULE_MINUTE, 30)));
        minuteInput.setHint("分");
        body.addView(minuteInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        new AlertDialog.Builder(this)
                .setTitle("调整执行时间")
                .setView(body)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        int hour = clampInt(readInt(hourInput, 4), 0, 23);
                        int minute = clampInt(readInt(minuteInput, 30), 0, 59);
                        getPreferencesStore().edit()
                                .putInt(PREF_SCHEDULE_HOUR, hour)
                                .putInt(PREF_SCHEDULE_MINUTE, minute)
                                .apply();
                        updateScheduleAlarmFromPrefs();
                    }
                })
                .show();
    }

    private int readInt(EditText input, int fallback) {
        try {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (text.length() == 0) {
                return fallback;
            }
            return Integer.parseInt(text);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private PendingIntent buildSchedulePendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("com.codex.maanikke.debug.RUN_WORKFLOW_DAILY_SAFE");
        intent.putExtra("scheduled", true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, SCHEDULE_REQUEST_CODE, intent, flags);
    }

    private void updateScheduleAlarmFromPrefs() {
        boolean enabled = getPreferencesStore().getBoolean(PREF_SCHEDULE_ENABLED, false);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = buildSchedulePendingIntent();
        if (alarmManager == null) {
            append("定时任务保存失败：系统 AlarmManager 不可用。");
            refreshScheduleUi();
            return;
        }
        if (!enabled) {
            alarmManager.cancel(pendingIntent);
            append("定时任务已关闭。");
            refreshScheduleUi();
            return;
        }
        long triggerAt = computeNextScheduleTimeMillis();
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException error) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            append("精确定时权限不可用，已降级为普通定时。");
        }
        append("定时任务已启用，下次执行：" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(triggerAt)));
        refreshScheduleUi();
    }

    private long computeNextScheduleTimeMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, getPreferencesStore().getInt(PREF_SCHEDULE_HOUR, 4));
        calendar.set(Calendar.MINUTE, getPreferencesStore().getInt(PREF_SCHEDULE_MINUTE, 30));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }

    private void addTaskActionRow(LinearLayout parent, String title, final String taskId, String subtitle) {
        addActionRow(parent, title, subtitle, "执行", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runBackendTask(taskId);
            }
        });
    }

    private void buildSettingsPage(LinearLayout content) {
        content.addView(pageTitle("通知设置"), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout internalCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams internalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        internalParams.topMargin = dp(6);
        content.addView(internalCard, internalParams);
        TextView internalTitle = sectionLabel("内部通知");
        internalCard.addView(internalTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        notifyInternalCheckBox = addSwitchRow(internalCard, "通知提醒",
                "允许任务运行时发送系统通知和前台服务状态。", PREF_NOTIFY_INTERNAL, true, null);
        addDivider(internalCard);
        notifyPopupCheckBox = addSwitchRow(internalCard, "弹出提示",
                "保留应用内日志提示，不额外打断游戏画面。", PREF_NOTIFY_POPUP, false, null);
        addDivider(internalCard);
        addActionRow(internalCard, "发送测试通知", "验证通知权限、通知渠道和前台服务可用性。", "发送", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendTestNotification();
            }
        });

        LinearLayout externalCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams externalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        externalParams.topMargin = dp(12);
        content.addView(externalCard, externalParams);
        TextView externalTitle = sectionLabel("外部通知");
        externalCard.addView(externalTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        notifyCompleteCheckBox = addSwitchRow(externalCard, "任务完成时通知", "", PREF_NOTIFY_COMPLETE, true, null);
        addDivider(externalCard);
        notifyErrorCheckBox = addSwitchRow(externalCard, "任务出错时通知", "", PREF_NOTIFY_ERROR, true, null);
        addDivider(externalCard);
        notifyServiceStopCheckBox = addSwitchRow(externalCard, "服务异常终止时通知", "", PREF_NOTIFY_SERVICE_STOP, false, null);
        addDivider(externalCard);
        notifyAttachLogCheckBox = addSwitchRow(externalCard, "附带日志详情",
                "导出文件保存到 Documents/MaaNikke/exports。", PREF_NOTIFY_ATTACH_LOG, false, null);

        LinearLayout channelCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        channelParams.topMargin = dp(12);
        content.addView(channelCard, channelParams);
        TextView channelTitle = sectionLabel("通知渠道");
        channelCard.addView(channelTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        notifyDingTalkCheckBox = addSwitchRow(channelCard, "钉钉机器人",
                "保存 Webhook / access_token / 加签密钥，密钥只写入本机配置。", PREF_NOTIFY_DINGTALK, false, null);
        addDivider(channelCard);
        notifyDingTalkWebhookInput = addTextInput(channelCard, "钉钉 Webhook",
                "可直接粘贴完整机器人 Webhook。", PREF_NOTIFY_DINGTALK_WEBHOOK, false);
        notifyDingTalkTokenInput = addTextInput(channelCard, "access_token",
                "如果只填 token，会自动拼接钉钉机器人地址。", PREF_NOTIFY_DINGTALK_TOKEN, false);
        notifyDingTalkSecretInput = addTextInput(channelCard, "加签 Secret",
                "未启用加签时可以留空。", PREF_NOTIFY_DINGTALK_SECRET, true);
        addDivider(channelCard);
        notifyWebhookCheckBox = addSwitchRow(channelCard, "自定义 Webhook",
                "保存一个通用 POST JSON Webhook，用于外部通知。", PREF_NOTIFY_WEBHOOK, false, null);
        notifyCustomWebhookInput = addTextInput(channelCard, "自定义 Webhook 地址",
                "任务结果会以 JSON 文本 POST 到这个地址。", PREF_NOTIFY_CUSTOM_WEBHOOK_URL, false);
        addDivider(channelCard);
        addActionRow(channelCard, "保存机器人配置", "写入本机 SharedPreferences，重启应用后仍会回显。", "保存", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveNotificationRobotConfig();
            }
        });
        addDivider(channelCard);
        addActionRow(channelCard, "发送机器人测试", "使用当前保存的钉钉或自定义 Webhook 配置发送一条测试消息。", "测试", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveNotificationRobotConfig();
                sendExternalNotification("MaaNikke 测试通知", true);
            }
        });
        addDivider(channelCard);
        addActionRow(channelCard, "通知权限管理", "打开系统通知权限页面。", "打开", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openNotificationPermission();
            }
        });

        LinearLayout modeCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        modeParams.topMargin = dp(12);
        content.addView(modeCard, modeParams);

        TextView modeTitle = sectionLabel("调试边界");
        modeCard.addView(modeTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addInfoRow(modeCard, "登录页", "login_required");
        addInfoRow(modeCard, "网络异常", "network_retry_required");
        addInfoRow(modeCard, "协同作战", "暂缓");
        addInfoRow(modeCard, "真实领取", "调试模式关闭后启用");

        LinearLayout overrideCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams overrideParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        overrideParams.topMargin = dp(12);
        content.addView(overrideCard, overrideParams);

        TextView overrideTitle = sectionLabel("设备覆盖");
        overrideCard.addView(overrideTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addInfoRow(overrideCard, "包名覆盖", "/data/local/tmp/maanikke_target_package.txt");
        addInfoRow(overrideCard, "组件覆盖", "/data/local/tmp/maanikke_target_component.txt");
        addInfoRow(overrideCard, "后端脚本", "/data/local/tmp/maanikke_run_probe_env.sh");
        addInfoRow(overrideCard, "任务结果", "/data/local/tmp/maanikke_task_result.txt");
    }

    private void addAboutCard(LinearLayout content) {
        LinearLayout aboutCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        aboutParams.topMargin = dp(12);
        content.addView(aboutCard, aboutParams);

        TextView aboutTitle = sectionLabel("关于与致谢");
        aboutCard.addView(aboutTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addInfoRow(aboutCard, "项目性质", "本项目是面向 NIKKE Android 端的本地学习、调试与适配实验项目，用于验证移动端任务编排、虚拟显示、截图和输入链路。");
        addInfoRow(aboutCard, "边界说明", "本项目不是 MaaAssistantArknights、MAA-Meow、MaaNikke 或游戏官方项目，不代表上游作者立场，也不提供登录绕过、封包、注入或反检测能力。");
        addInfoRow(aboutCard, "参考项目", "MaaAssistantArknights / MaaFramework、Aliothmoon/MAA-Meow、Shinarin/MaaNikke。");
        addInfoRow(aboutCard, "资源来源", "任务目录与资源结构参考 MaaNikke PC 端公开资源，并按 Android 调试需求做本地适配。");
        addInfoRow(aboutCard, "敬意与感谢", "向 MAA、MAA-Meow、MaaNikke 的作者、维护者与社区致以敬意和感谢；本项目的很多设计判断来自他们长期沉淀的工程经验。");
    }

    private TextView pillText(String text, int background, int foreground) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(foreground);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(roundedBackground(background, dp(16), 0));
        return view;
    }

    private TextView sectionLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(0xff111827);
        return view;
    }

    private TextView pageTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(26);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(0xff111827);
        view.setPadding(0, dp(16), 0, dp(10));
        return view;
    }

    private TextView actionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(0xff2563eb);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView mutedText(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(0xff6b7280);
        view.setSingleLine(false);
        return view;
    }

    private TextView tabText(String text, boolean selected) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setTextColor(selected ? 0xff2563eb : 0xff6b7280);
        view.setBackground(tabIndicatorBackground(selected));
        return view;
    }

    private android.graphics.drawable.Drawable tabIndicatorBackground(boolean selected) {
        GradientDrawable transparent = new GradientDrawable();
        transparent.setColor(0x00ffffff);
        if (!selected) {
            return transparent;
        }
        GradientDrawable line = new GradientDrawable();
        line.setColor(0xff2563eb);
        LayerDrawable layer = new LayerDrawable(new android.graphics.drawable.Drawable[]{transparent, line});
        layer.setLayerInset(1, 0, dp(45), 0, 0);
        return layer;
    }

    private TextView taskTabText(String text, final int tabId) {
        final TextView view = tabText(text, tabId == currentTaskTab);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                switchTaskTab(tabId);
            }
        });
        taskTabViews.add(view);
        return view;
    }

    private void switchTaskTab(int tabId) {
        currentTaskTab = tabId;
        if (taskConfigPanel != null) {
            taskConfigPanel.setVisibility(tabId == TASK_TAB_DAILY ? View.VISIBLE : View.GONE);
        }
        if (taskAutoPanel != null) {
            taskAutoPanel.setVisibility(tabId == TASK_TAB_AUTO ? View.VISIBLE : View.GONE);
        }
        if (taskToolsPanel != null) {
            taskToolsPanel.setVisibility(tabId == TASK_TAB_TOOLS ? View.VISIBLE : View.GONE);
        }
        if (taskInlineLogPanel != null) {
            taskInlineLogPanel.setVisibility(tabId == TASK_TAB_LOGS ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < taskTabViews.size(); i++) {
            TextView item = taskTabViews.get(i);
            boolean selected = i == tabId;
            item.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setTextColor(selected ? 0xff2563eb : 0xff6b7280);
            item.setBackground(tabIndicatorBackground(selected));
        }
        if (tabId == TASK_TAB_AUTO) {
            append("已切换到自动战斗：请先确认任务状态，未适配入口会安全返回待适配。");
        } else if (tabId == TASK_TAB_TOOLS) {
            append("已切换到小工具：可单独执行回首页、更新检查、邮箱巡检和日志导出。");
        }
    }

    private LinearLayout buildLogCard(int scrollHeight) {
        LinearLayout logCard = cardLayout(0xffffffff, 0x1f000000);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        logCard.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView logTitle = sectionLabel("运行日志");
        header.addView(logTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button export = new Button(this);
        export.setText("导出日志");
        styleButton(export, 0xffffffff, 0xff374151);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportEvidence();
            }
        });
        logExportButton = export;
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
        );
        exportParams.leftMargin = dp(8);
        header.addView(export, exportParams);

        TextView hint = new TextView(this);
        hint.setText("这里会按时间展示任务启动、调试模式、执行步骤、后端状态和最终结果；导出会保存当前日志和后端日志文件。");
        hint.setTextSize(11);
        hint.setTextColor(0xff6b7280);
        hint.setPadding(0, dp(5), 0, dp(2));
        logCard.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView view = new TextView(this);
        view.setTextColor(0xff374151);
        view.setTextSize(11);
        view.setTextIsSelectable(true);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setText(logBuffer.toString());
        logViews.add(view);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(roundedBackground(0xfff4f4f5, dp(8), 0xffe5e7eb));
        scroll.addView(view, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        logScrollViews.add(scroll);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                scrollHeight
        );
        scrollParams.topMargin = dp(8);
        logCard.addView(scroll, scrollParams);
        return logCard;
    }

    private LinearLayout cardLayout(int background, int stroke) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(roundedBackground(background, dp(8), stroke));
        return card;
    }

    private TextView addMetric(LinearLayout row, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(7), dp(8), dp(7));
        box.setBackground(roundedBackground(0xfff7f7f8, dp(8), 0xffe5e7eb));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(10);
        labelView.setTextColor(0xff6b7280);
        box.addView(labelView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTextColor(0xff111827);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setSingleLine(true);
        box.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(8);
        }
        row.addView(box, params);
        return valueView;
    }

    private TextView addInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, 0);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(0xff6b7280);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(12);
        valueView.setTextColor(0xff111827);
        valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        valueView.setSingleLine(false);
        row.addView(valueView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return valueView;
    }

    private CheckBox addSwitchRow(LinearLayout parent, String title, String subtitle, String prefKey,
                                  boolean defaultValue, final Runnable afterChange) {
        final CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(getPreferencesStore().getBoolean(prefKey, defaultValue));
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff3f73d8));
        addSwitchRow(parent, title, subtitle, checkBox, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPreferencesStore().edit().putBoolean(prefKey, checkBox.isChecked()).apply();
                if (afterChange != null) {
                    afterChange.run();
                }
            }
        });
        return checkBox;
    }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle, CheckBox checkBox,
                              View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkBox.performClick();
            }
        });

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(0xff111827);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        if (subtitle != null && subtitle.length() > 0) {
            TextView subtitleView = mutedText(subtitle, 11);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(3);
            copy.addView(subtitleView, subtitleParams);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        checkBox.setOnClickListener(listener);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(54), dp(42));
        checkParams.leftMargin = dp(8);
        row.addView(checkBox, checkParams);
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addActionRow(LinearLayout parent, String title, String subtitle, String action,
                              View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setOnClickListener(listener);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(0xff111827);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        if (subtitle != null && subtitle.length() > 0) {
            TextView subtitleView = mutedText(subtitle, 11);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(3);
            copy.addView(subtitleView, subtitleParams);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView actionView = actionText(action);
        actionView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
        );
        actionParams.leftMargin = dp(10);
        row.addView(actionView, actionParams);
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private EditText addTextInput(LinearLayout parent, String title, String subtitle, String prefKey, boolean password) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(8), 0, dp(8));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(0xff111827);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        block.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (subtitle != null && subtitle.length() > 0) {
            TextView subtitleView = mutedText(subtitle, 11);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(3);
            block.addView(subtitleView, subtitleParams);
        }

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(12);
        input.setTextColor(0xff111827);
        input.setHintTextColor(0xff9ca3af);
        input.setText(getPreferencesStore().getString(prefKey, ""));
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus && view instanceof EditText) {
                    saveTextInputValue(prefKey, (EditText) view);
                }
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    if (view instanceof EditText) {
                        saveTextInputValue(prefKey, (EditText) view);
                    }
                    view.clearFocus();
                    return false;
                }
                return false;
            }
        });
        input.setBackground(roundedBackground(0xfff7f7f8, dp(8), 0xffe5e7eb));
        input.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        inputParams.topMargin = dp(6);
        block.addView(input, inputParams);

        parent.addView(block, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return input;
    }

    private void saveTextInputValue(String prefKey, EditText input) {
        getPreferencesStore().edit()
                .putString(prefKey, input.getText() == null ? "" : input.getText().toString().trim())
                .apply();
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(0xffe5e7eb);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        params.leftMargin = dp(6);
        params.rightMargin = dp(6);
        parent.addView(divider, params);
    }

    private void addBackendModeRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText("控制器模式");
        titleView.setTextSize(14);
        titleView.setTextColor(0xff111827);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = new TextView(this);
        subtitleView.setText("Root 稳定优先；Shizuku 用于无 Root 设备，需要先在 Shizuku 内授权本应用。");
        subtitleView.setTextSize(11);
        subtitleView.setTextColor(0xff6b7280);
        subtitleView.setSingleLine(false);
        copy.addView(subtitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        backendModeText = pillText(buildBackendModeLabel(),
                isShizukuBackend() ? 0xffe8f7f4 : 0xffe8efff,
                isShizukuBackend() ? 0xff0f766e : 0xff2563eb);
        backendModeText.setGravity(Gravity.CENTER);
        backendModeText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleBackendMode();
            }
        });
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(92), dp(32));
        chipParams.leftMargin = dp(8);
        row.addView(backendModeText, chipParams);

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addDebugModeRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        debugModeCheckBox = new CheckBox(this);
        debugModeCheckBox.setChecked(debugMode);
        debugModeCheckBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff38bdf8));
        debugModeCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setDebugMode(debugModeCheckBox.isChecked());
            }
        });
        row.addView(debugModeCheckBox, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText("调试模式");
        titleView.setTextSize(14);
        titleView.setTextColor(0xff111827);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = new TextView(this);
        subtitleView.setText("开启后会进入奖励/购买入口并展示红点，但跳过最后确认点击；关闭后按正常任务执行。");
        subtitleView.setTextSize(11);
        subtitleView.setTextColor(0xff6b7280);
        subtitleView.setSingleLine(false);
        copy.addView(subtitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        debugModeText = pillText(debugMode ? "调试模式" : "正常执行",
                debugMode ? 0xffe8efff : 0xfffff2d5,
                debugMode ? 0xff2563eb : 0xffb45309);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
        );
        chipParams.leftMargin = dp(8);
        row.addView(debugModeText, chipParams);

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addBackgroundModeRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        backgroundModeCheckBox = new CheckBox(this);
        backgroundModeCheckBox.setChecked(backgroundMode);
        backgroundModeCheckBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff38bdf8));
        backgroundModeCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setBackgroundMode(backgroundModeCheckBox.isChecked());
            }
        });
        row.addView(backgroundModeCheckBox, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText("后台运行模式");
        titleView.setTextSize(14);
        titleView.setTextColor(0xff111827);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = new TextView(this);
        subtitleView.setText("勾选后任务运行时使用前台服务保活，并允许界面退到后台。");
        subtitleView.setTextSize(11);
        subtitleView.setTextColor(0xff6b7280);
        subtitleView.setSingleLine(false);
        copy.addView(subtitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        backgroundModeText = pillText(backgroundMode ? "已启用" : "前台调试", backgroundMode ? 0xffe8f7f4 : 0xffe8efff,
                backgroundMode ? 0xff0f766e : 0xff2563eb);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
        );
        chipParams.leftMargin = dp(8);
        row.addView(backgroundModeText, chipParams);

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private TextView addPermissionRow(LinearLayout parent, String title, String status, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        row.setOnClickListener(listener);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(0xff111827);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView hintView = new TextView(this);
        hintView.setText(permissionHint(title));
        hintView.setTextSize(11);
        hintView.setTextColor(0xff6b7280);
        hintView.setSingleLine(false);
        copy.addView(hintView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView chip = pillText(status, 0xffeef2ff, 0xff2563eb);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
        );
        chipParams.leftMargin = dp(8);
        row.addView(chip, chipParams);

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return chip;
    }

    private String permissionHint(String title) {
        if (title.indexOf("Shizuku") >= 0) {
            return "优先使用，用于管理 Shizuku 服务和本应用授权。";
        }
        if (title.indexOf("瀛樺偍") >= 0) {
            return "用于导出日志、截图和任务证据。";
        }
        if (title.indexOf("安装列表") >= 0) {
            return "用于发现 NIKKE 渠道包，root 后端也会兜底枚举。";
        }
        if (title.indexOf("通知") >= 0) {
            return "后台运行时显示前台服务状态。";
        }
        return "减少任务运行时被系统回收的概率。";
    }

    private void setPermissionExpanded(boolean expanded) {
        getPreferencesStore().edit().putBoolean(PREF_PERMISSION_EXPANDED, expanded).apply();
        if (permissionExtraContainer != null) {
            permissionExtraContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (permissionExpandText != null) {
            permissionExpandText.setText(expanded ? "收起其他权限" : "展开其他权限");
        }
    }

    private View taskRow(String title, String subtitle, String state, int stateColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(0xffffffff);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(11);
        subtitleView.setTextColor(0xff94a3b8);
        subtitleView.setSingleLine(false);
        copy.addView(subtitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView chip = pillText(state, stateColor, 0xffffffff);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(26)
        );
        chipParams.leftMargin = dp(8);
        row.addView(chip, chipParams);
        return row;
    }

    private void addWorkflowItem(LinearLayout parent, TaskCatalog.TaskSpec task) {
        WorkflowItem item = new WorkflowItem(task, loadWorkflowChecked(task));
        workflowItems.add(item);
        parent.addView(buildWorkflowRow(item), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private View buildWorkflowRow(final WorkflowItem item) {
        final TaskCatalog.TaskSpec task = item.task;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        row.setBackground(roundedBackground(0x00ffffff, dp(8), 0));
        row.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View view, DragEvent event) {
                Object local = event.getLocalState();
                if (!(local instanceof WorkflowItem)) {
                    return false;
                }
                WorkflowItem source = (WorkflowItem) local;
                if (source == item) {
                    return true;
                }
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        updateDragTarget(item, event.getY() > view.getHeight() / 2f);
                        return true;
                    case DragEvent.ACTION_DRAG_LOCATION:
                        updateDragTarget(item, event.getY() > view.getHeight() / 2f);
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        clearDragTarget(item);
                        return true;
                    case DragEvent.ACTION_DROP:
                        moveWorkflowItem(source, item, event.getY() > view.getHeight() / 2f);
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        finishTaskDrag();
                        return true;
                    default:
                        return true;
                }
            }
        });

        View handle = buildDragHandleView();
        handle.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                startTaskDrag(item, view);
                return true;
            }
        });
        row.addView(handle, new LinearLayout.LayoutParams(dp(42), dp(42)));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(item.checked);
        checkBox.setEnabled(item.enabled);
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(item.enabled ? 0xff38bdf8 : 0xff94a3b8));
        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                item.checked = checkBox.isChecked();
                saveWorkflowChecked(task.androidTaskId, item.checked);
            }
        });
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        checkParams.leftMargin = dp(4);
        row.addView(checkBox, checkParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(task.name);
        titleView.setTextSize(14);
        titleView.setTextColor(item.enabled ? 0xff111827 : 0xff9ca3af);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(task.pcEntry + " 路 " + task.description);
        subtitleView.setTextSize(11);
        subtitleView.setTextColor(0xff6b7280);
        subtitleView.setSingleLine(false);
        copy.addView(subtitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView chip = pillText("●", statusChipBackground(task.status, item.enabled),
                statusChipForeground(task.status, item.enabled));
        chip.setContentDescription("适配状态：" + task.status);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                dp(22),
                dp(22)
        );
        chipParams.leftMargin = dp(8);
        row.addView(chip, chipParams);

        TextView detailButton = pillText("明细", 0xffeef2ff, 0xff2563eb);
        detailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openTaskDetailDialog(item);
            }
        });
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(26)
        );
        detailParams.leftMargin = dp(8);
        row.addView(detailButton, detailParams);

        item.row = row;
        item.checkBox = checkBox;
        item.handleView = handle;
        item.detailView = detailButton;
        item.titleView = titleView;
        item.subtitleView = subtitleView;
        item.statusView = chip;
        return row;
    }

    private int statusChipBackground(String status, boolean enabled) {
        if (!enabled) {
            return 0xffeeeeee;
        }
        if ("已验证".equals(status) || "已适配".equals(status) || "安全适配".equals(status)
                || "安全访问".equals(status)) {
            return 0xffe8f7f4;
        }
        if ("待复测".equals(status) || "修复中".equals(status)) {
            return 0xfffff2d5;
        }
        if ("只读适配".equals(status)) {
            return 0xffeef2ff;
        }
        return 0xfff3f4f6;
    }

    private int statusChipForeground(String status, boolean enabled) {
        if (!enabled) {
            return 0xff9ca3af;
        }
        if ("已验证".equals(status) || "已适配".equals(status) || "安全适配".equals(status)
                || "安全访问".equals(status)) {
            return 0xff0f766e;
        }
        if ("待复测".equals(status) || "修复中".equals(status)) {
            return 0xffb45309;
        }
        if ("只读适配".equals(status)) {
            return 0xff2563eb;
        }
        return 0xff6b7280;
    }

    private View buildDragHandleView() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setPadding(dp(9), dp(9), dp(9), dp(9));
        wrapper.setContentDescription("拖动排序");
        setDragHandleStyle(wrapper, 0xffffffff, 0xffe5e7eb, 0xff6b7280);
        for (int row = 0; row < 3; row++) {
            View bar = buildDragHandleBar(0xff6b7280);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(18), dp(2));
            if (row > 0) {
                barParams.topMargin = dp(4);
            }
            wrapper.addView(bar, barParams);
        }
        return wrapper;
    }

    private View buildDragHandleBar(int color) {
        View bar = new View(this);
        bar.setBackground(roundedBackground(color, dp(1), 0));
        return bar;
    }

    private void setDragHandleStyle(View handle, int background, int stroke, int barColor) {
        if (handle == null) {
            return;
        }
        handle.setBackground(roundedBackground(background, dp(8), stroke));
        setDragHandleBarColor(handle, barColor);
    }

    private void setDragHandleBarColor(View view, int color) {
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setDragHandleBarColor(group.getChildAt(i), color);
            }
            return;
        }
        view.setBackground(roundedBackground(color, dp(1), 0));
    }

    private void moveWorkflowItemToLast(WorkflowItem source) {
        int sourceIndex = workflowItems.indexOf(source);
        if (sourceIndex < 0 || sourceIndex == workflowItems.size() - 1) {
            finishTaskDrag();
            return;
        }
        workflowItems.remove(sourceIndex);
        workflowItems.add(source);
        saveTaskOrder();
        renderWorkflowList();
        finishTaskDrag();
        append("任务顺序已更新：" + source.task.name + " -> 末尾");
    }

    private void renderWorkflowList() {
        if (taskListContainer == null) {
            return;
        }
        taskListContainer.removeAllViews();
        for (int i = 0; i < workflowItems.size(); i++) {
            taskListContainer.addView(buildWorkflowRow(workflowItems.get(i)), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private void startTaskDrag(WorkflowItem item, View view) {
        draggingWorkflowItem = item;
        ClipData data = ClipData.newPlainText("maanikke-task", item.task.androidTaskId);
        View dragView = item.row == null ? view : item.row;
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(dragView);
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        setDragSourceStyle(item, true);
        append("开始拖动任务：" + item.task.name);
        if (Build.VERSION.SDK_INT >= 24) {
            view.startDragAndDrop(data, shadow, item, 0);
        } else {
            view.startDrag(data, shadow, item, 0);
        }
    }

    private void moveWorkflowItem(WorkflowItem source, WorkflowItem target, boolean afterTarget) {
        int sourceIndex = workflowItems.indexOf(source);
        int targetIndex = workflowItems.indexOf(target);
        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) {
            return;
        }
        workflowItems.remove(sourceIndex);
        if (sourceIndex < targetIndex) {
            targetIndex--;
        }
        int insertIndex = afterTarget ? targetIndex + 1 : targetIndex;
        if (insertIndex < 0) {
            insertIndex = 0;
        }
        if (insertIndex > workflowItems.size()) {
            insertIndex = workflowItems.size();
        }
        workflowItems.add(insertIndex, source);
        saveTaskOrder();
        renderWorkflowList();
        finishTaskDrag();
        append("任务顺序已更新：" + source.task.name + (afterTarget ? " -> 后移到 " : " -> 前移到 ") + target.task.name);
    }

    private void updateDragTarget(WorkflowItem target, boolean afterTarget) {
        if (draggingWorkflowItem == null || target == draggingWorkflowItem) {
            return;
        }
        for (int i = 0; i < workflowItems.size(); i++) {
            WorkflowItem item = workflowItems.get(i);
            if (item == draggingWorkflowItem) {
                setDragSourceStyle(item, true);
            } else if (item == target) {
                setDragTargetStyle(item, true, afterTarget);
            } else {
                resetWorkflowRowStyle(item);
            }
        }
    }

    private void clearDragTarget(WorkflowItem target) {
        if (target != null && target != draggingWorkflowItem) {
            resetWorkflowRowStyle(target);
        }
    }

    private void finishTaskDrag() {
        for (int i = 0; i < workflowItems.size(); i++) {
            resetWorkflowRowStyle(workflowItems.get(i));
        }
        draggingWorkflowItem = null;
    }

    private void setDragSourceStyle(WorkflowItem item, boolean dragging) {
        if (item == null || item.row == null) {
            return;
        }
        item.row.animate().alpha(dragging ? 0.45f : 1f).scaleX(dragging ? 0.98f : 1f)
                .scaleY(dragging ? 0.98f : 1f).setDuration(120).start();
        if (Build.VERSION.SDK_INT >= 21) {
            item.row.setElevation(dragging ? dp(6) : 0);
        }
        if (item.handleView != null) {
            setDragHandleStyle(item.handleView, dragging ? 0xffdbeafe : 0xffffffff,
                    dragging ? 0xff2563eb : 0xffe5e7eb,
                    dragging ? 0xff2563eb : 0xff6b7280);
        }
    }

    private void setDragTargetStyle(WorkflowItem item, boolean active, boolean afterTarget) {
        if (item == null || item.row == null) {
            return;
        }
        item.row.animate().alpha(1f).scaleX(active ? 1.015f : 1f)
                .scaleY(active ? 1.015f : 1f).setDuration(100).start();
        if (Build.VERSION.SDK_INT >= 21) {
            item.row.setElevation(active ? dp(4) : 0);
        }
        item.row.setBackground(roundedBackground(active ? 0xffeef2ff : 0x00ffffff, dp(8),
                active ? 0xff2563eb : 0));
        if (item.handleView != null) {
            setDragHandleStyle(item.handleView, 0xffdbeafe, 0xff2563eb, 0xff2563eb);
        }
    }

    private void resetWorkflowRowStyle(WorkflowItem item) {
        if (item == null || item.row == null) {
            return;
        }
        item.row.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();
        if (Build.VERSION.SDK_INT >= 21) {
            item.row.setElevation(0);
        }
        item.row.setBackground(roundedBackground(0x00ffffff, dp(8), 0));
        if (item.handleView != null) {
            setDragHandleStyle(item.handleView, 0xffffffff, 0xffe5e7eb, 0xff6b7280);
        }
    }

    private void saveTaskOrder() {
        TaskProfile.saveOrder(getPreferencesStore(), collectWorkflowTaskIds());
    }

    private boolean loadWorkflowChecked(TaskCatalog.TaskSpec task) {
        return TaskProfile.isChecked(getPreferencesStore(), task);
    }

    private void saveWorkflowChecked(String taskId, boolean checked) {
        TaskProfile.saveChecked(getPreferencesStore(), taskId, checked);
    }

    private String loadTaskOptionValue(String taskId, TaskCatalog.TaskOptionSpec option) {
        return getPreferencesStore().getString(PREF_TASK_OPTION_PREFIX + taskId + "_" + option.key, option.defaultValue);
    }

    private void saveTaskOptionValue(String taskId, TaskCatalog.TaskOptionSpec option, String value) {
        getPreferencesStore().edit().putString(PREF_TASK_OPTION_PREFIX + taskId + "_" + option.key, value).apply();
    }

    private void openTaskDetailDialog(final WorkflowItem item) {
        final TaskCatalog.TaskSpec task = item.task;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(8));

        addInfoRow(body, "PC Entry", task.pcEntry);
        addInfoRow(body, "Android 任务", task.androidTaskId);
        addInfoRow(body, "当前模式会执行", formatTaskName(resolveTaskForCurrentMode(task.androidTaskId)));
        addInfoRow(body, "适配状态", task.status);
        addInfoRow(body, "默认勾选", task.defaultChecked ? "是" : "否");

        TextView optionTitle = sectionLabel("任务明细");
        LinearLayout.LayoutParams optionTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        optionTitleParams.topMargin = dp(12);
        body.addView(optionTitle, optionTitleParams);
        if (running) {
            TextView runningHint = mutedText("当前任务运行中，修改会保存到下一次启动脚本时生效。", 11);
            runningHint.setPadding(0, dp(4), 0, dp(4));
            body.addView(runningHint, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        final ArrayList<OptionWidget> widgets = new ArrayList<OptionWidget>();
        if (task.options.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("无可配置项");
            empty.setTextSize(12);
            empty.setTextColor(0xff6b7280);
            empty.setPadding(0, dp(8), 0, 0);
            body.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        } else {
            for (int i = 0; i < task.options.length; i++) {
                widgets.add(addTaskOptionView(body, task, task.options[i]));
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(task.name);
        builder.setView(scroll);
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                for (int i = 0; i < widgets.size(); i++) {
                    OptionWidget widget = widgets.get(i);
                    saveTaskOptionValue(task.androidTaskId, widget.spec, widget.readValue());
                }
                append("已保存任务明细：" + task.name);
            }
        });
        builder.show();
    }

    private OptionWidget addTaskOptionView(LinearLayout parent, TaskCatalog.TaskSpec task, TaskCatalog.TaskOptionSpec option) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        blockParams.topMargin = dp(10);
        parent.addView(block, blockParams);

        TextView label = new TextView(this);
        label.setText(option.key);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(0xff111827);
        block.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView desc = new TextView(this);
        desc.setText(option.description);
        desc.setTextSize(11);
        desc.setTextColor(0xff6b7280);
        desc.setSingleLine(false);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dp(2);
        block.addView(desc, descParams);

        if ("switch".equals(option.type)) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setChecked("Yes".equalsIgnoreCase(loadTaskOptionValue(task.androidTaskId, option)));
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff38bdf8));
            checkBox.setText(checkBox.isChecked() ? "Yes" : "No");
            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    checkBox.setText(checkBox.isChecked() ? "Yes" : "No");
                }
            });
            block.addView(checkBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return new OptionWidget(option, checkBox, null, null, null);
        }

        if ("select".equals(option.type)) {
            Spinner spinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, option.cases);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            String current = loadTaskOptionValue(task.androidTaskId, option);
            for (int i = 0; i < option.cases.length; i++) {
                if (option.cases[i].equals(current)) {
                    spinner.setSelection(i);
                    break;
                }
            }
            block.addView(spinner, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return new OptionWidget(option, null, spinner, null, null);
        }

        if ("input".equals(option.type)) {
            EditText editText = new EditText(this);
            editText.setText(loadTaskOptionValue(task.androidTaskId, option));
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setTextColor(0xff111827);
            editText.setHint(option.defaultValue);
            block.addView(editText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return new OptionWidget(option, null, null, editText, null);
        }

        LinearLayout choiceBox = new LinearLayout(this);
        choiceBox.setOrientation(LinearLayout.VERTICAL);
        String selectedText = loadTaskOptionValue(task.androidTaskId, option);
        for (int i = 0; i < option.cases.length; i++) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(option.cases[i]);
            checkBox.setChecked(isOptionSelected(selectedText, option.cases[i]));
            choiceBox.addView(checkBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
        block.addView(choiceBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return new OptionWidget(option, null, null, null, choiceBox);
    }

    private boolean isOptionSelected(String selectedText, String candidate) {
        if (selectedText == null || selectedText.length() == 0) {
            return false;
        }
        String[] parts = selectedText.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].trim().equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static final class OptionWidget {
        final TaskCatalog.TaskOptionSpec spec;
        final CheckBox switchCheckBox;
        final Spinner spinner;
        final EditText editText;
        final LinearLayout checkboxGroup;

        OptionWidget(TaskCatalog.TaskOptionSpec spec, CheckBox switchCheckBox, Spinner spinner, EditText editText,
                     LinearLayout checkboxGroup) {
            this.spec = spec;
            this.switchCheckBox = switchCheckBox;
            this.spinner = spinner;
            this.editText = editText;
            this.checkboxGroup = checkboxGroup;
        }

        String readValue() {
            if (switchCheckBox != null) {
                return switchCheckBox.isChecked() ? "Yes" : "No";
            }
            if (spinner != null) {
                Object selected = spinner.getSelectedItem();
                return selected == null ? "" : String.valueOf(selected);
            }
            if (editText != null) {
                String text = editText.getText() == null ? "" : editText.getText().toString().trim();
                return text.length() == 0 ? spec.defaultValue : text;
            }
            if (checkboxGroup != null) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < checkboxGroup.getChildCount(); i++) {
                    View child = checkboxGroup.getChildAt(i);
                    if (child instanceof CheckBox) {
                        CheckBox checkBox = (CheckBox) child;
                        if (checkBox.isChecked()) {
                            if (builder.length() > 0) {
                                builder.append(',');
                            }
                            builder.append(checkBox.getText());
                        }
                    }
                }
                return builder.toString();
            }
            return spec.defaultValue;
        }
    }

    private LinearLayout.LayoutParams weightedButtonParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.leftMargin = leftMargin;
        return params;
    }

    private void styleButton(Button button, int background, int foreground) {
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setTextSize(13);
        int stroke = background == 0xffffffff ? 0xffe5e7eb : 0;
        button.setBackground(roundedBackground(background, dp(8), stroke));
        button.setGravity(Gravity.CENTER);
    }

    private LinearLayout bottomActionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        bar.setBackgroundColor(0xfff7f4ef);

        workflowButton = new Button(this);
        workflowButton.setText("开始任务");
        styleButton(workflowButton, 0xff2563eb, 0xffffffff);
        workflowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runWorkflow();
            }
        });
        bar.addView(workflowButton, weightedButtonParams(0));

        stopButton = new Button(this);
        stopButton.setText("停止任务");
        styleButton(stopButton, 0xffffffff, 0xffff4d3d);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopBackend();
            }
        });
        bar.addView(stopButton, weightedButtonParams(dp(8)));

        startGameButton = new Button(this);
        startGameButton.setText("启动游戏");
        styleButton(startGameButton, 0xffe8efff, 0xff2563eb);
        startGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runBackendTask("start_game");
            }
        });
        bar.addView(startGameButton, weightedButtonParams(dp(8)));

        stopGameButton = new Button(this);
        stopGameButton.setText("结束游戏");
        styleButton(stopGameButton, 0xffffffff, 0xff374151);
        stopGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runBackendTask("stop_game");
            }
        });
        bar.addView(stopGameButton, weightedButtonParams(dp(8)));

        return bar;
    }

    private LinearLayout bottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(4), dp(8), dp(4));
        nav.setBackgroundColor(0xffffffff);
        nav.addView(navItem("首页", PAGE_HOME), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        nav.addView(navItem("后台任务", PAGE_TASKS), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        nav.addView(navItem("定时任务", PAGE_LOGS), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        nav.addView(navItem("通知", PAGE_SETTINGS), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return nav;
    }

    private TextView navItem(String text, final int pageId) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(12);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                switchPage(pageId);
            }
        });
        navItems.add(view);
        return view;
    }

    private void switchPage(int pageId) {
        currentPage = pageId;
        setPageVisible(homePage, pageId == PAGE_HOME);
        setPageVisible(taskPage, pageId == PAGE_TASKS);
        setPageVisible(logPage, pageId == PAGE_LOGS);
        setPageVisible(settingsPage, pageId == PAGE_SETTINGS);
        if (actionBar != null) {
            actionBar.setVisibility(pageId == PAGE_TASKS ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < navItems.size(); i++) {
            TextView item = navItems.get(i);
            boolean selected = i == pageId;
            item.setTextColor(selected ? 0xff2563eb : 0xff9ca3af);
            item.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setBackground(selected ? roundedBackground(0xffe8efff, dp(8), 0) : null);
        }
    }

    private void setPageVisible(LinearLayout page, boolean visible) {
        if (page == null) {
            return;
        }
        View target = page;
        if (page.getParent() instanceof View && page.getParent() instanceof ScrollView) {
            target = (View) page.getParent();
        }
        target.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private GradientDrawable roundedBackground(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private SharedPreferences getPreferencesStore() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void setupShizukuCallbacks() {
        try {
            Shizuku.addBinderReceivedListenerSticky(new Shizuku.OnBinderReceivedListener() {
                @Override
                public void onBinderReceived() {
                    append("Shizuku 服务已连接。");
                    refreshPermissionStatus();
                }
            }, mainHandler);
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode == REQ_SHIZUKU) {
                        append(grantResult == PackageManager.PERMISSION_GRANTED
                                ? "Shizuku 授权成功。"
                                : "Shizuku 授权未通过。");
                        refreshPermissionStatus();
                    }
                }
            }, mainHandler);
        } catch (Throwable error) {
            append("Shizuku 回调初始化失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private boolean isShizukuBackend() {
        return BACKEND_MODE_SHIZUKU.equals(backendMode);
    }

    private String buildBackendModeLabel() {
        return isShizukuBackend() ? "Shizuku" : "Root";
    }

    private void toggleBackendMode() {
        setBackendMode(isShizukuBackend() ? BACKEND_MODE_ROOT : BACKEND_MODE_SHIZUKU);
    }

    private void setBackendMode(String mode) {
        if (!BACKEND_MODE_SHIZUKU.equals(mode)) {
            mode = BACKEND_MODE_ROOT;
        }
        backendMode = mode;
        getPreferencesStore().edit().putString(PREF_BACKEND_MODE, backendMode).apply();
        updateBackendModeUi();
        append("控制器模式已切换为：" + buildBackendModeLabel());
        if (isShizukuBackend() && !hasShizukuPermission()) {
            requestShizukuPermission();
        }
    }

    private boolean ensureBackendReadyForRun() {
        if (!isShizukuBackend()) {
            return true;
        }
        if (!isShizukuBinderAlive()) {
            append("当前为 Shizuku 模式，但服务未连接。请先启动 Shizuku。");
            openShizukuManagement();
            refreshPermissionStatus();
            return false;
        }
        if (!hasShizukuPermission()) {
            append("当前为 Shizuku 模式，但本应用尚未授权。");
            requestShizukuPermission();
            refreshPermissionStatus();
            return false;
        }
        return true;
    }

    private void updateBackendModeUi() {
        if (backendModeText != null) {
            backendModeText.setText(buildBackendModeLabel());
            backendModeText.setTextColor(isShizukuBackend() ? 0xff0f766e : 0xff2563eb);
            backendModeText.setBackground(roundedBackground(
                    isShizukuBackend() ? 0xffe8f7f4 : 0xffe8efff, dp(16), 0));
        }
        if (profileModeText != null) {
            profileModeText.setText(buildProfileModeText());
        }
    }

    private void setBackgroundMode(boolean enabled) {
        backgroundMode = enabled;
        getPreferencesStore().edit().putBoolean(PREF_BACKGROUND_MODE, enabled).apply();
        updateBackgroundModeUi();
        if (enabled) {
            append("Background mode enabled. Tasks will keep a foreground service while running.");
        } else {
            stopKeepAliveService();
            append("Background mode disabled. Tasks will keep the debug UI in front.");
        }
    }

    private void setDebugMode(boolean enabled) {
        debugMode = enabled;
        getPreferencesStore().edit().putBoolean(PREF_DEBUG_MODE, enabled).apply();
        updateDebugModeUi();
        append(enabled
                ? "调试模式已开启：会展示红点和确认入口，但不会真实领取或购买。"
                : "调试模式已关闭：已适配任务会正常领取或购买。");
    }

    private void updateBackgroundModeUi() {
        if (backgroundModeCheckBox != null && backgroundModeCheckBox.isChecked() != backgroundMode) {
            backgroundModeCheckBox.setChecked(backgroundMode);
        }
        if (backgroundModeText != null) {
            backgroundModeText.setText(backgroundMode ? "已启用" : "前台调试");
            backgroundModeText.setTextColor(backgroundMode ? 0xff0f766e : 0xff2563eb);
            backgroundModeText.setBackground(roundedBackground(backgroundMode ? 0xffe8f7f4 : 0xffe8efff, dp(16), 0));
        }
    }

    private void updateDebugModeUi() {
        if (debugModeCheckBox != null && debugModeCheckBox.isChecked() != debugMode) {
            debugModeCheckBox.setChecked(debugMode);
        }
        if (debugModeText != null) {
            debugModeText.setText(debugMode ? "调试模式" : "正常执行");
            debugModeText.setTextColor(debugMode ? 0xff2563eb : 0xffb45309);
            debugModeText.setBackground(roundedBackground(debugMode ? 0xffe8efff : 0xfffff2d5, dp(16), 0));
        }
        if (profileModeText != null) {
            profileModeText.setText(buildProfileModeText());
        }
    }

    private String buildProfileModeText() {
        return (debugMode ? "日常调试模式" : "日常正常执行模式") + " / " + buildBackendModeLabel();
    }

    private void refreshPermissionStatus() {
        updateBackendModeUi();
        updateDebugModeUi();
        updateBackgroundModeUi();
        setPermissionChip(shizukuPermissionText, buildShizukuStatusText(), hasShizukuPermission());
        setPermissionChip(storagePermissionText, hasStoragePermission() ? "已授权" : "去授权", hasStoragePermission());
        setPermissionChip(packageListPermissionText, canQueryNikkePackage() ? "可用" : "需检查", canQueryNikkePackage());
        boolean notificationGranted = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        setPermissionChip(notificationPermissionText, notificationGranted ? "已授权" : "去授权", notificationGranted);
        boolean batteryIgnored = isIgnoringBatteryOptimizations();
        setPermissionChip(batteryPermissionText, batteryIgnored ? "已允许" : "去设置", batteryIgnored);
    }

    private void setPermissionChip(TextView view, String text, boolean ok) {
        if (view == null) {
            return;
        }
        view.setText(text);
        view.setTextColor(ok ? 0xff0f766e : 0xffb45309);
        view.setBackground(roundedBackground(ok ? 0xffe8f7f4 : 0xfffff2d5, dp(16), 0));
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private String buildShizukuStatusText() {
        if (!isPackageInstalled("moe.shizuku.privileged.api")) {
            return "未安装";
        }
        if (!isShizukuBinderAlive()) {
            return "未连接";
        }
        if (hasShizukuPermission()) {
            return "已授权";
        }
        return "去授权";
    }

    private boolean isShizukuBinderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasShizukuPermission() {
        try {
            return isShizukuBinderAlive()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void requestShizukuPermission() {
        try {
            if (!isShizukuBinderAlive()) {
                append("Shizuku 服务未连接，请先启动 Shizuku 服务。");
                openShizukuManagement();
                return;
            }
            if (hasShizukuPermission()) {
                append("Shizuku 已授权。");
                refreshPermissionStatus();
                return;
            }
            Shizuku.requestPermission(REQ_SHIZUKU);
            append("已请求 Shizuku 授权，请在弹窗中允许。");
        } catch (Throwable error) {
            append("请求 Shizuku 授权失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
            openShizukuManagement();
        }
    }

    private void requestOrOpenShizukuManagement() {
        if (hasShizukuPermission()) {
            openShizukuManagement();
            return;
        }
        requestShizukuPermission();
    }

    private void testShizukuBackendChannel() {
        if (running) {
            append("当前任务运行中，结束后再检测 Shizuku 通道。");
            return;
        }
        setButtons(false);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isShizukuBinderAlive()) {
                        append("Shizuku 通道检测：服务未连接，请先启动 Shizuku。");
                        openShizukuManagement();
                        return;
                    }
                    if (!hasShizukuPermission()) {
                        append("Shizuku 通道检测：本应用尚未授权。");
                        requestShizukuPermission();
                        return;
                    }
                    String command = "id; "
                            + "echo maanikke_shizuku_ok > /data/local/tmp/maanikke_shizuku_probe.txt; "
                            + "cat /data/local/tmp/maanikke_shizuku_probe.txt; "
                            + "rm -f /data/local/tmp/maanikke_shizuku_probe.txt";
                    String output = runShizukuShell(command, false).trim();
                    append("Shizuku 通道检测通过：" + compactLogText(output));
                    append("提示：无 Root 设备上的 Shizuku 通常是 shell/ADB 身份，若虚拟显示或输入注入失败，会在任务日志里给出具体原因。");
                } catch (Throwable error) {
                    append("Shizuku 通道检测失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
                } finally {
                    refreshPermissionStatus();
                    setButtons(true);
                }
            }
        });
    }

    private boolean canQueryNikkePackage() {
        PackageManager pm = getPackageManager();
        for (int i = 0; i < NIKKE_PACKAGE_CANDIDATES.length; i++) {
            try {
                PackageInfo ignored = pm.getPackageInfo(NIKKE_PACKAGE_CANDIDATES[i], 0);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openShizukuManagement() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launchIntent != null) {
            startActivity(launchIntent);
            return;
        }
        openPackageDetails("moe.shizuku.privileged.api");
    }

    private void openStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            }
        } catch (Throwable error) {
            openAppDetails();
        }
    }

    private void openNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        } else {
            openAppDetails();
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable error) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) {
                openAppDetails();
            }
        }
    }

    private void openAppDetails() {
        openPackageDetails(getPackageName());
    }

    private void openPackageDetails(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Throwable error) {
            append("打开系统设置失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void startKeepAliveService(String text) {
        Intent intent = new Intent(this, TaskExecutionService.class);
        intent.setAction(TaskExecutionService.ACTION_KEEP_ALIVE);
        intent.putExtra(TaskExecutionService.EXTRA_TEXT, text);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopKeepAliveService() {
        Intent intent = new Intent(this, TaskExecutionService.class);
        intent.setAction(TaskExecutionService.ACTION_STOP);
        startService(intent);
    }

    private void sendTestNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            append("发送测试通知前需要先授予通知权限。");
            openNotificationPermission();
            return;
        }
        postSystemNotification("这是一条 MaaNikke 测试通知。");
        append("测试通知已发送。");
    }

    private void saveNotificationRobotConfig() {
        if (notifyDingTalkWebhookInput != null) {
            notifyDingTalkWebhookInput.clearFocus();
        }
        if (notifyDingTalkTokenInput != null) {
            notifyDingTalkTokenInput.clearFocus();
        }
        if (notifyDingTalkSecretInput != null) {
            notifyDingTalkSecretInput.clearFocus();
        }
        if (notifyCustomWebhookInput != null) {
            notifyCustomWebhookInput.clearFocus();
        }
        String dingTalkWebhook = readInputText(notifyDingTalkWebhookInput);
        String dingTalkToken = readInputText(notifyDingTalkTokenInput);
        String dingTalkSecret = readInputText(notifyDingTalkSecretInput);
        String customWebhook = readInputText(notifyCustomWebhookInput);
        getPreferencesStore().edit()
                .putString(PREF_NOTIFY_DINGTALK_WEBHOOK, dingTalkWebhook)
                .putString(PREF_NOTIFY_DINGTALK_TOKEN, dingTalkToken)
                .putString(PREF_NOTIFY_DINGTALK_SECRET, dingTalkSecret)
                .putString(PREF_NOTIFY_CUSTOM_WEBHOOK_URL, customWebhook)
                .apply();
        append("机器人通知配置已保存。钉钉="
                + (dingTalkWebhook.length() > 0 || dingTalkToken.length() > 0 ? "已配置" : "未配置")
                + "，自定义 Webhook=" + (customWebhook.length() > 0 ? "已配置" : "未配置"));
    }

    private String readInputText(EditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    private void postSystemNotification(String text) {
        if (!getPreferencesStore().getBoolean(PREF_NOTIFY_INTERNAL, true)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            append("通知未发送：缺少通知权限。");
            return;
        }
        Intent intent = new Intent(this, TaskExecutionService.class);
        intent.setAction(TaskExecutionService.ACTION_TEST_NOTIFICATION);
        intent.putExtra(TaskExecutionService.EXTRA_TEXT, text);
        startService(intent);
    }

    private void notifyTaskOutcome(String taskLabel, boolean failed, boolean stopped) {
        String externalMessage;
        if (stopped) {
            if (getPreferencesStore().getBoolean(PREF_NOTIFY_SERVICE_STOP, false)) {
                postSystemNotification("任务已停止：" + taskLabel);
                sendExternalNotification("任务已停止：" + taskLabel, false);
            }
            return;
        }
        if (failed) {
            externalMessage = "任务出错：" + taskLabel;
            if (getPreferencesStore().getBoolean(PREF_NOTIFY_ERROR, true)) {
                postSystemNotification(externalMessage);
                sendExternalNotification(externalMessage, false);
            }
        } else if (getPreferencesStore().getBoolean(PREF_NOTIFY_COMPLETE, true)) {
            externalMessage = "任务完成：" + taskLabel;
            postSystemNotification(externalMessage);
            sendExternalNotification(externalMessage, false);
        }
    }

    private void sendExternalNotification(final String message, final boolean verbose) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> results = new ArrayList<String>();
                SharedPreferences prefs = getPreferencesStore();
                if (prefs.getBoolean(PREF_NOTIFY_DINGTALK, false)) {
                    String webhook = buildDingTalkWebhookUrl(
                            prefs.getString(PREF_NOTIFY_DINGTALK_WEBHOOK, ""),
                            prefs.getString(PREF_NOTIFY_DINGTALK_TOKEN, ""),
                            prefs.getString(PREF_NOTIFY_DINGTALK_SECRET, "")
                    );
                    if (webhook.length() == 0) {
                        results.add("钉钉未配置 webhook/token");
                    } else {
                        results.add(postJson(webhook, buildDingTalkMessageJson(message), "钉钉"));
                    }
                }
                if (prefs.getBoolean(PREF_NOTIFY_WEBHOOK, false)) {
                    String url = prefs.getString(PREF_NOTIFY_CUSTOM_WEBHOOK_URL, "").trim();
                    if (url.length() == 0) {
                        results.add("自定义 Webhook 未配置地址");
                    } else {
                        results.add(postJson(url, buildGenericWebhookJson(message), "自定义 Webhook"));
                    }
                }
                if (verbose || results.size() > 0) {
                    append(results.size() == 0
                            ? "外部通知未发送：未启用钉钉或自定义 Webhook。"
                            : joinStrings(results, "?"));
                }
            }
        });
    }

    private String buildDingTalkWebhookUrl(String webhook, String token, String secret) {
        String url = webhook == null ? "" : webhook.trim();
        if (url.length() == 0 && token != null && token.trim().length() > 0) {
            url = "https://oapi.dingtalk.com/robot/send?access_token=" + token.trim();
        }
        if (url.length() == 0 || secret == null || secret.trim().length() == 0) {
            return url;
        }
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret.trim();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.trim().getBytes("UTF-8"), "HmacSHA256"));
            String sign = URLEncoder.encode(
                    Base64.encodeToString(mac.doFinal(stringToSign.getBytes("UTF-8")), Base64.NO_WRAP),
                    "UTF-8"
            );
            return url + (url.indexOf('?') >= 0 ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Throwable error) {
            append("钉钉签名生成失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
            return url;
        }
    }

    private String postJson(String targetUrl, String payload, String label) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = payload.getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(bytes);
            } finally {
                output.close();
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            return label + " 通知返回 " + code + (body.trim().length() == 0 ? "" : "：" + compactLogText(body));
        } catch (Throwable error) {
            return label + " 通知失败：" + error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildDingTalkMessageJson(String message) {
        return "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapeJson("MaaNikke\n" + message) + "\"}}";
    }

    private String buildGenericWebhookJson(String message) {
        return "{\"source\":\"MaaNikke\",\"text\":\"" + escapeJson(message) + "\",\"time\":\""
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\"}";
    }

    private String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '"') {
                builder.append('\\').append(ch);
            } else if (ch == '\n') {
                builder.append("\\n");
            } else if (ch == '\r') {
                builder.append("\\r");
            } else if (ch == '\t') {
                builder.append("\\t");
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String joinStrings(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(separator);
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private void runRootProbe() {
        if (running) {
            append("已有任务正在运行，请等待结束后再启动探针。");
            return;
        }
        if (!ensureBackendReadyForRun()) {
            return;
        }
        running = true;
        stopRequested = false;
        setStatus("Probe", 0xff1d4ed8);
        setPhase("Root 探针运行中");
        updateMetrics("探针", "-", "-", "-", "采集中");
        startPreviewLoop(false);
        setButtons(false);
        append("开始 Root / ImageReader 探针，预计约 70 秒。");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean failed = false;
                try {
                    prepareBackendJarAndRunner("com.codex.maanikke.rootprobe.RootImageReaderProbe");
                    forceStopDetectedNikke(false);
                    runBackendShell("rm -f " + REMOTE_RESULT + " " + REMOTE_LOG + " " + REMOTE_LAST + " " + REMOTE_BEFORE + " " + REMOTE_AFTER);
                    runBackendRunner();
                    append("探针进程已结束。");
                    readRemoteResult();
                    exportEvidenceInternal();
                    refreshPreviewOnce(REMOTE_LAST, "探针最终画面");
                } catch (Throwable error) {
                    failed = true;
                    append("任务失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
                    readRemoteDiagnosticsQuietly(false);
                    setStatus("Failed", 0xff991b1b);
                } finally {
                    running = false;
                    stopPreviewLoop();
                    if (stopRequested) {
                        setStatus("Stopped", 0xff7f1d1d);
                        setPhase("用户已停止");
                    } else {
                        setStatus("Idle", 0xff1f2937);
                    }
                    notifyTaskOutcome("Root 探针", failed, stopRequested);
                    setButtons(true);
                }
            }
        });
    }

    private void runBackendTask(final String taskName) {
        runBackendTask(taskName, new String[0]);
    }

    private void runWorkflow() {
        runWorkflow(false);
    }

    private void runWorkflow(boolean fromSchedule) {
        boolean forceStart = fromSchedule && getPreferencesStore().getBoolean(PREF_SCHEDULE_FORCE_START, false);
        List<TaskProfile.TaskSelection> selections = collectWorkflowSelections();
        TaskProfile.saveChecks(getPreferencesStore(), selections);
        String[] selectedSteps = TaskProfile.buildBackendSteps(selections, debugMode, forceStart);
        if (selectedSteps.length == 0) {
            append("请至少勾选一个任务。");
            return;
        }
        append(debugMode
                ? "调试模式开启：本次会进入奖励/购买入口并展示红点，但跳过最后确认点击。"
                : "调试模式关闭：本次工作流会执行已适配的真实领取/购买任务。");
        if (fromSchedule) {
            append("定时任务触发：按已保存的后台任务顺序执行。");
        }
        runBackendTask("workflow_daily_safe", selectedSteps);
    }

    private String resolveTaskForCurrentMode(String taskId) {
        return TaskProfile.resolveTaskForMode(taskId, debugMode);
    }

    private ArrayList<String> collectWorkflowTaskIds() {
        ArrayList<String> taskIds = new ArrayList<String>();
        for (int i = 0; i < workflowItems.size(); i++) {
            taskIds.add(workflowItems.get(i).task.androidTaskId);
        }
        return taskIds;
    }

    private ArrayList<TaskProfile.TaskSelection> collectWorkflowSelections() {
        ArrayList<TaskProfile.TaskSelection> selections = new ArrayList<TaskProfile.TaskSelection>();
        for (int i = 0; i < workflowItems.size(); i++) {
            WorkflowItem item = workflowItems.get(i);
            if (item.checkBox != null) {
                item.checked = item.checkBox.isChecked();
            }
            selections.add(new TaskProfile.TaskSelection(
                    item.task.androidTaskId,
                    item.checked,
                    item.enabled
            ));
        }
        return selections;
    }

    private void runBackendTask(final String taskName, final String[] taskArgs) {
        if (running) {
            append("已有任务正在运行。");
            return;
        }
        if (!ensureBackendReadyForRun()) {
            return;
        }
        final String taskLabel = buildTaskLabel(taskName, taskArgs);
        running = true;
        stopRequested = false;
        switchPage(PAGE_TASKS);
        setStatus("Running", 0xff1d4ed8);
        setPhase("任务启动中：" + formatTaskName(taskName));
        updateMetrics(formatTaskName(taskName), "-", "-", "-", "启动中");
        startPreviewLoop(true, getPreviewLoadingTextForTask(taskName));
        setButtons(false);
        if (backgroundMode) {
            startKeepAliveService("任务运行中");
        }
        append("开始执行任务：" + describeTaskLabel(taskName, taskArgs));
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean failed = false;
                try {
                    cleanupStaleBackendProcesses();
                    writeTaskOptionsFile();
                    prepareBackendJarAndRunner("com.codex.maanikke.rootprobe.MaaNikkeTaskRunner " + taskLabel);
                    if ("start_game".equals(taskName)) {
                        forceStopDetectedNikke(false);
                    }
                    runBackendShell("rm -f " + REMOTE_TASK_RESULT + " " + REMOTE_TASK_LOG + " "
                            + REMOTE_TASK_FRAME + " " + REMOTE_TASK_BEFORE + " " + REMOTE_TASK_AFTER + " "
                            + REMOTE_TASK_AFTER_OPEN + " " + REMOTE_TASK_AFTER_CLOSE + " "
                            + REMOTE_TASK_AFTER_BACK + " " + REMOTE_TASK_AFTER_WAIT + " "
                            + REMOTE_TASK_AFTER_ENTER + " " + REMOTE_TASK_AFTER_DOWNLOAD_CONFIRM + " "
                            + REMOTE_TASK_AFTER_HOME_POPUP + " " + REMOTE_TASK_AFTER_UPDATE + " "
                            + REMOTE_TASK_AFTER_MAIL_OPEN + " " + REMOTE_TASK_AFTER_MAIL_CLAIM + " "
                            + REMOTE_TASK_AFTER_MAIL_CONFIRM + " " + REMOTE_BACKEND_STDOUT);
                    Process backend = startBackendShell(REMOTE_RUNNER);
                    Thread.sleep(3500);
                    if (!backgroundMode) {
                        bringDebugUiToFront();
                    }
                    waitForTaskFinished(backend, taskName);
                    append("任务逻辑已完成，实时画面将保持连接：" + describeTaskLabel(taskName, taskArgs));
                    readRemoteResultSummary(true);
                    exportTaskEvidenceInternal(taskName);
                    if ("stop_game".equals(taskName)) {
                        waitForProcess(backend, "backend stop task");
                        cleanupAfterGameStopped();
                    } else {
                        refreshPreviewOnce(REMOTE_TASK_FRAME, "最终画面：" + formatTaskName(taskName));
                        append("实时画面保持中；只有点击“结束游戏”或“停止任务”才会断开。");
                    }
                } catch (Throwable error) {
                    failed = true;
                    append("任务失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
                    readRemoteDiagnosticsQuietly(true);
                    setStatus("Failed", 0xff991b1b);
                } finally {
                    running = false;
                    boolean backendStillActive = !"stop_game".equals(taskName)
                            && !stopRequested
                            && hasActiveBackendTaskQuietly();
                    boolean keepPreview = !"stop_game".equals(taskName)
                            && !stopRequested
                            && (!failed || backendStillActive);
                    if (!keepPreview) {
                        stopPreviewLoop();
                    }
                    if (!backendStillActive) {
                        stopKeepAliveService();
                    } else if (backgroundMode) {
                        startKeepAliveService("任务运行中");
                    }
                    if (stopRequested) {
                        setStatus("Stopped", 0xff7f1d1d);
                        setPhase("用户已停止");
                    } else if (backendStillActive) {
                        setStatus("Running", 0xff1d4ed8);
                        setPhase("后端仍在运行，实时画面保持连接");
                    } else if (keepPreview) {
                        setStatus("Preview", 0xff2563eb);
                        setPhase("实时画面保持中");
                    } else {
                        setStatus("Idle", 0xff1f2937);
                    }
                    notifyTaskOutcome(describeTaskLabel(taskName, taskArgs), failed, stopRequested);
                    setButtons(true);
                }
            }
        });
    }

    private void waitForTaskFinished(Process process, String taskName) throws Exception {
        long startedAt = System.currentTimeMillis();
        boolean wrapperExitLogged = false;
        while (true) {
            if (stopRequested) {
                throw new InterruptedIOException("task stop requested");
            }
            String output = runBackendShellQuiet("cat " + REMOTE_TASK_RESULT + " 2>/dev/null || true");
            String phase = extractResultValue(output, "phase");
            if (isTaskTerminalPhase(phase)) {
                return;
            }
            try {
                int exit = process.exitValue();
                if (!wrapperExitLogged) {
                    wrapperExitLogged = true;
                    append("后端启动进程已返回，继续等待任务结果文件；exit=" + exit);
                }
            } catch (IllegalThreadStateException stillRunning) {
                // The backend stays alive after finishing so preview can continue.
            }
            if (System.currentTimeMillis() - startedAt > getTaskFinishTimeoutMs(taskName)) {
                throw new InterruptedIOException("等待任务完成超时：" + formatTaskName(taskName));
            }
            Thread.sleep(1000);
        }
    }

    private boolean isTaskTerminalPhase(String phase) {
        return "finished".equals(phase)
                || "workflow_finished".equals(phase)
                || (phase != null && phase.startsWith("workflow_stopped_"))
                || (phase != null && phase.startsWith("preview_keep_alive_"));
    }

    private long getTaskFinishTimeoutMs(String taskName) {
        if ("workflow_daily_safe".equals(taskName)) {
            return 20L * 60L * 1000L;
        }
        if ("start_game".equals(taskName)) {
            return 4L * 60L * 1000L;
        }
        return 3L * 60L * 1000L;
    }

    private String buildTaskLabel(String taskName, String[] taskArgs) {
        StringBuilder builder = new StringBuilder(taskName);
        if (taskArgs != null) {
            for (int i = 0; i < taskArgs.length; i++) {
                String arg = taskArgs[i];
                if (arg != null && isSafeTaskArg(arg)) {
                    builder.append(' ').append(arg);
                }
            }
        }
        return builder.toString();
    }

    private String describeTaskLabel(String taskName, String[] taskArgs) {
        StringBuilder builder = new StringBuilder(formatTaskName(taskName));
        if (taskArgs != null && taskArgs.length > 0) {
            builder.append("：");
            int count = 0;
            for (int i = 0; i < taskArgs.length; i++) {
                if (taskArgs[i] == null || taskArgs[i].length() == 0) {
                    continue;
                }
                if (count > 0) {
                    builder.append(" -> ");
                }
                builder.append(formatTaskName(taskArgs[i]));
                count++;
                if (count >= 8 && i < taskArgs.length - 1) {
                    builder.append(" -> 共 ").append(taskArgs.length).append(" 个步骤");
                    break;
                }
            }
            builder.append("。");
        }
        return builder.toString();
    }

    private boolean isSafeTaskArg(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-')) {
                return false;
            }
        }
        return value.length() > 0;
    }

    private void stopBackend() {
        if (!running) {
            append("当前没有正在运行的任务。");
            return;
        }
        stopRequested = true;
        stopPreviewLoop();
        setStatus("Stopping", 0xff92400e);
        setPhase("正在停止后端和游戏");
        stopButton.setEnabled(false);
        controlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    killMaaNikkeBackendProcesses();
                    forceStopDetectedNikke(true);
                    append("停止请求已执行：后端进程和 NIKKE 已停止。");
                } catch (Throwable error) {
                    append("停止失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
                }
            }
        });
    }

    private void cleanupStaleBackendProcesses() {
        try {
            killMaaNikkeBackendProcesses();
            runBackendShellQuiet("rm -f " + REMOTE_TASK_RESULT + " " + REMOTE_TASK_LOG + " "
                    + REMOTE_TASK_FRAME + " " + REMOTE_LAST + " " + REMOTE_BACKEND_STDOUT);
            append("已清理上一次任务的后端进程、结果和画面缓存。");
        } catch (Throwable error) {
            append("清理旧任务缓存失败，继续尝试启动：" + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void cleanupAfterGameStopped() {
        try {
            killMaaNikkeBackendProcesses();
            runBackendShellQuiet("rm -f " + REMOTE_TASK_FRAME + " " + REMOTE_LAST + " " + REMOTE_BEFORE + " " + REMOTE_AFTER);
        } catch (Throwable error) {
            append("结束后清理画面缓存失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        setPhase("游戏已结束");
        updateMetrics("idle", "-", "-", "-", "已重置");
        resetPreviewPane("游戏已结束，实时画面已重置。");
    }

    private boolean hasActiveBackendTaskQuietly() {
        try {
            String processes = runBackendShellQuiet(listMaaNikkeBackendProcessesCommand());
            return processes.trim().length() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Process startBackendShell(String command) throws Exception {
        if (isShizukuBackend()) {
            ensureShizukuReady();
            return newShizukuProcess(new String[]{"sh", "-c", shizukuShellUidCommand(command)});
        }
        return startShell("su 2000 -c " + shellQuote(command));
    }

    private void killMaaNikkeBackendProcesses() throws Exception {
        runBackendShellQuiet(killMaaNikkeBackendProcessesCommand());
    }

    private String maaNikkeBackendProcessPipelineCommand() {
        return "(ps -A -o PID,ARGS 2>/dev/null || ps -A) "
                + "| grep -E 'MaaNikkeTaskRunner|MaaNikkeRootIrProbe|maanikke-root-ir-probe[.]jar' "
                + "| grep -v grep";
    }

    private String listMaaNikkeBackendProcessesCommand() {
        return maaNikkeBackendProcessPipelineCommand() + " || true";
    }

    private String killMaaNikkeBackendProcessesCommand() {
        return maaNikkeBackendProcessPipelineCommand()
                + " | while read pid rest; do "
                + "case \"$pid\" in ''|*[!0-9]*) continue;; esac; "
                + "kill -9 \"$pid\" 2>/dev/null || true; "
                + "done; true";
    }

    private String runBackendShell(String command) throws Exception {
        if (isShizukuBackend()) {
            return runShizukuShell(command, false);
        }
        return runShell("su -c " + shellQuote(command));
    }

    private String runBackendShellQuiet(String command) throws Exception {
        if (isShizukuBackend()) {
            return runShizukuShell(command, true);
        }
        return runShellQuiet("su -c " + shellQuote(command));
    }

    private void ensureShizukuReady() {
        if (!isShizukuBinderAlive()) {
            throw new IllegalStateException("Shizuku 服务未连接");
        }
        if (!hasShizukuPermission()) {
            throw new IllegalStateException("Shizuku 未授权本应用");
        }
    }

    private Process newShizukuProcess(String[] command) throws Exception {
        Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        method.setAccessible(true);
        return (Process) method.invoke(null, command, null, null);
    }

    private String shizukuShellUidCommand(String command) {
        return "if [ \"$(id -u)\" = \"0\" ]; then su 2000 -c " + shellQuote(command)
                + "; else " + command + "; fi";
    }

    private String runShizukuShell(String command, boolean quiet) throws Exception {
        ensureShizukuReady();
        Process process = newShizukuProcess(new String[]{"sh", "-c", command});
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        if (!quiet && stderr.trim().length() > 0) {
            append("Shizuku 输出：" + compactLogText(stderr.trim()));
        }
        if (exit != 0) {
            throw new IllegalStateException("Shizuku exit=" + exit + " command=" + command + " stderr=" + stderr);
        }
        return stdout + stderr;
    }

    private Process startShell(String command) throws Exception {
        return Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
    }

    private void writeRemoteFile(String remotePath, byte[] bytes, String mode) throws Exception {
        String quoted = shellQuote(remotePath);
        Process process = startBackendShell("rm -f " + quoted + " && cat > " + quoted
                + " && chmod " + mode + " " + quoted);
        OutputStream output = process.getOutputStream();
        try {
            output.write(bytes);
            output.flush();
        } finally {
            output.close();
        }
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        if (stderr.trim().length() > 0) {
            append("后端写入输出：" + compactLogText(stderr.trim()));
        }
        if (exit != 0) {
            throw new IllegalStateException("write remote file exit=" + exit
                    + " path=" + remotePath + " stdout=" + stdout + " stderr=" + stderr);
        }
    }

    private void writeTaskOptionsFile() throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("# MaaNikke task options generated by Android UI\n");
        for (int taskIndex = 0; taskIndex < TaskCatalog.PC_TASKS.length; taskIndex++) {
            TaskCatalog.TaskSpec task = TaskCatalog.PC_TASKS[taskIndex];
            for (int optionIndex = 0; optionIndex < task.options.length; optionIndex++) {
                TaskCatalog.TaskOptionSpec option = task.options[optionIndex];
                String value = sanitizeTaskOptionValue(loadTaskOptionValue(task.androidTaskId, option));
                appendTaskOption(builder, task.androidTaskId, optionIndex, value);
                appendTaskOption(builder, TaskProfile.resolveTaskForMode(task.androidTaskId, false), optionIndex, value);
                appendTaskOption(builder, TaskProfile.resolveTaskForMode(task.androidTaskId, true), optionIndex, value);
                if (task.pcEntry != null && task.pcEntry.length() > 0) {
                    appendTaskOption(builder, task.pcEntry, optionIndex, value);
                }
            }
        }
        writeRemoteFile(REMOTE_TASK_OPTIONS, builder.toString().getBytes("UTF-8"), "666");
        append("任务明细参数已同步到后端。");
    }

    private void appendTaskOption(StringBuilder builder, String taskId, int optionIndex, String value) {
        if (taskId == null || taskId.length() == 0) {
            return;
        }
        builder.append(taskId).append(".option.").append(optionIndex).append('=').append(value).append('\n');
    }

    private String sanitizeTaskOptionValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private byte[] readAssetBytes(String assetName) throws Exception {
        InputStream input = getAssets().open(assetName);
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(256 * 1024);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private void waitForProcess(Process process, String label) throws Exception {
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        if (stderr.trim().length() > 0) {
            append("后端输出：" + compactLogText(stderr.trim()));
        }
        if (exit != 0) {
            throw new IllegalStateException(label + " exit=" + exit);
        }
    }

    private void bringDebugUiToFront() {
        try {
            runBackendShellQuiet("am start --display 0 -n com.codex.maanikke.debug/.MainActivity");
            append("已把调试界面带回手机主屏。");
        } catch (Throwable error) {
            append("调试界面回到前台失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void forceStopDetectedNikke(boolean quiet) {
        try {
            StringBuilder command = new StringBuilder();
            command.append("for pkg in ");
            command.append("$(cat ").append(REMOTE_TARGET_PACKAGE).append(" 2>/dev/null | head -n 1) ");
            command.append("$(cat ").append(REMOTE_TARGET_COMPONENT).append(" 2>/dev/null | head -n 1 | sed 's#/.*##') ");
            for (int i = 0; i < NIKKE_PACKAGE_CANDIDATES.length; i++) {
                command.append(NIKKE_PACKAGE_CANDIDATES[i]).append(' ');
            }
            command.append("$(cmd package list packages 2>/dev/null | sed 's/^package://' | grep -i nikke); do ");
            command.append("case \"$pkg\" in ''|.*|*..*|*[!A-Za-z0-9._]*) continue;; esac; ");
            command.append("case \"$pkg\" in com.codex.maanikke.*) continue;; esac; ");
            command.append("case \" $seen \" in *\" $pkg \"*) continue;; esac; ");
            command.append("seen=\"$seen $pkg\"; ");
            command.append("am force-stop \"$pkg\" 2>/dev/null || true; ");
            command.append("echo stopped:$pkg; ");
            command.append("done");
            String output;
            if (quiet) {
                output = runBackendShellQuiet(command.toString());
            } else {
                output = runBackendShell(command.toString());
            }
            if (output.trim().length() > 0) {
                String[] lines = output.split("\\n");
                StringBuilder stopped = new StringBuilder();
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.startsWith("stopped:")) {
                        if (stopped.length() > 0) {
                            stopped.append(", ");
                        }
                        stopped.append(line.substring("stopped:".length()));
                    }
                }
                if (stopped.length() > 0) {
                    append("Stopped NIKKE package(s): " + stopped);
                }
            }
        } catch (Throwable error) {
            append("NIKKE force-stop skipped: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void prepareBackendJarAndRunner(String mainClassAndArgs) throws Exception {
        writeRemoteFile(REMOTE_JAR, readAssetBytes(ASSET_JAR), "444");
        append("后端运行包已写入 /data/local/tmp。");
        writeRemoteRunner(mainClassAndArgs);
    }

    private void writeRemoteRunner(String mainClassAndArgs) throws Exception {
        String script = "#!/system/bin/sh\n"
                + "export TMPDIR=/data/local/tmp\n"
                + "export ANDROID_DATA=/data\n"
                + "export CLASSPATH=" + REMOTE_JAR + "\n"
                + "exec /system/bin/app_process /system/bin " + mainClassAndArgs
                + " >> " + REMOTE_BACKEND_STDOUT + " 2>&1\n";
        writeRemoteFile(REMOTE_RUNNER, script.getBytes("US-ASCII"), "555");
        append("后端启动脚本已写入 /data/local/tmp。");
    }

    private void runBackendRunner() throws Exception {
        Process process = startBackendShell(REMOTE_RUNNER);
        waitForProcess(process, "backend runner");
    }

    private void exportEvidence() {
        if (running) {
            append("请等待当前任务结束后再导出日志和证据。");
            return;
        }
        setButtons(false);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    exportEvidenceInternal();
                } catch (Throwable error) {
                    append("导出失败：" + error.getClass().getSimpleName() + ": " + error.getMessage());
                } finally {
                    setButtons(true);
                }
            }
        });
    }

    private void exportEvidenceInternal() throws Exception {
        File dir = getPublicProjectDir(PUBLIC_EXPORT_DIR);
        ensurePublicProjectDirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        writeAppLogFile(new File(dir, "app-log-" + stamp + ".txt"));
        copyRemote(REMOTE_RESULT, new File(dir, "result-" + stamp + ".txt"));
        copyRemote(REMOTE_LOG, new File(dir, "probe-" + stamp + ".log"));
        copyRemote(REMOTE_TASK_RESULT, new File(dir, "last-task-result-" + stamp + ".txt"));
        copyRemote(REMOTE_TASK_LOG, new File(dir, "last-task-runner-" + stamp + ".log"));
        copyRemote(REMOTE_LAST, new File(dir, "last-" + stamp + ".png"));
        copyRemote(REMOTE_BEFORE, new File(dir, "before-touch-" + stamp + ".png"));
        copyRemote(REMOTE_AFTER, new File(dir, "after-touch-" + stamp + ".png"));
        append("日志文件已导出到：" + dir.getAbsolutePath());
    }

    private void exportTaskEvidenceInternal(String taskName) throws Exception {
        File dir = getPublicProjectDir(PUBLIC_EXPORT_DIR);
        ensurePublicProjectDirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        writeAppLogFile(new File(dir, taskName + "-app-log-" + stamp + ".txt"));
        copyRemote(REMOTE_TASK_RESULT, new File(dir, taskName + "-result-" + stamp + ".txt"));
        copyRemote(REMOTE_TASK_LOG, new File(dir, taskName + "-runner-" + stamp + ".log"));
        copyRemote(REMOTE_TASK_FRAME, new File(dir, taskName + "-frame-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_BEFORE, new File(dir, taskName + "-before-action-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER, new File(dir, taskName + "-after-action-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_OPEN, new File(dir, taskName + "-after-open-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_CLOSE, new File(dir, taskName + "-after-close-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_BACK, new File(dir, taskName + "-after-back-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_WAIT, new File(dir, taskName + "-after-wait-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_ENTER, new File(dir, taskName + "-after-enter-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_DOWNLOAD_CONFIRM, new File(dir, taskName + "-after-download-confirm-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_HOME_POPUP, new File(dir, taskName + "-after-home-popup-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_UPDATE, new File(dir, taskName + "-after-update-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_MAIL_OPEN, new File(dir, taskName + "-after-mail-open-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_MAIL_CLAIM, new File(dir, taskName + "-after-mail-claim-" + stamp + ".png"));
        copyRemote(REMOTE_TASK_AFTER_MAIL_CONFIRM, new File(dir, taskName + "-after-mail-confirm-" + stamp + ".png"));
        append("任务日志和截图已导出到：" + dir.getAbsolutePath());
    }

    private File getPublicProjectDir(String child) {
        File documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        return new File(new File(documents, PUBLIC_BASE_DIR), child);
    }

    private void ensurePublicProjectDirs() throws Exception {
        File base = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                PUBLIC_BASE_DIR);
        File exports = new File(base, PUBLIC_EXPORT_DIR);
        File logs = new File(base, PUBLIC_LOG_DIR);
        File screenshots = new File(base, PUBLIC_SCREENSHOT_DIR);
        File apk = new File(base, PUBLIC_APK_DIR);
        String command = "mkdir -p "
                + shellQuote(exports.getAbsolutePath()) + " "
                + shellQuote(logs.getAbsolutePath()) + " "
                + shellQuote(screenshots.getAbsolutePath()) + " "
                + shellQuote(apk.getAbsolutePath());
        runBackendShell(command);
    }

    private void writeAppLogFile(File target) throws Exception {
        FileOutputStream output = new FileOutputStream(target);
        try {
            output.write(logBuffer.toString().getBytes("UTF-8"));
        } finally {
            output.close();
        }
        runBackendShell("chmod 666 " + shellQuote(target.getAbsolutePath()));
    }

    private void copyRemote(String remote, File target) throws Exception {
        String command = "if [ -f " + shellQuote(remote) + " ]; then cp " + shellQuote(remote) + " "
                + shellQuote(target.getAbsolutePath()) + " && chmod 666 " + shellQuote(target.getAbsolutePath())
                + "; else echo missing:" + remote + "; fi";
        String output = runBackendShell(command);
        // Optional evidence files are often absent for tasks that never entered that page.
    }

    private void readRemoteResult() throws Exception {
        readRemoteResultSummary(false);
    }

    private void readRemoteResultSummary(boolean taskMode) throws Exception {
        String remotePath = taskMode ? REMOTE_TASK_RESULT : REMOTE_RESULT;
        String output = runBackendShell("cat " + remotePath + " 2>/dev/null || true");
        if (output.trim().length() == 0) {
            append("没有读取到后端结果文件。");
            return;
        }
        String phase = extractResultValue(output, "phase");
        String task = extractResultValue(output, "task");
        String display = extractResultValue(output, "displayId");
        String frames = extractResultValue(output, "frames");
        String actions = extractResultValue(output, "actionCount");
        String success = extractResultValue(output, "actionSuccess");
        String state = extractResultValue(output, "finalState");
        notifyBattleFailedIfNeeded(task, state);
        if (taskMode) {
            append("最终结果：" + formatTaskName(task.length() == 0 ? "任务" : task)
                    + "，阶段=" + formatTaskPhase(phase)
                    + "，状态=" + formatTaskState(state)
                    + "，动作=" + (actions.length() == 0 ? "-" : actions)
                    + "，帧数=" + (frames.length() == 0 ? "-" : frames)
                    + "，屏幕=" + (display.length() == 0 ? "-" : display)
                    + (success.length() == 0 ? "" : "，成功=" + success));
        } else {
            append("探针结果：阶段=" + ("finished".equals(phase) ? "已完成" : phase)
                    + "，帧数=" + (frames.length() == 0 ? "-" : frames)
                    + "，屏幕=" + (display.length() == 0 ? "-" : display));
        }
    }

    private void notifyBattleFailedIfNeeded(String task, String state) {
        if (state == null || state.indexOf("battle_failed_retry") < 0) {
            return;
        }
        String key = (task == null ? "" : task) + "|" + state;
        if (key.equals(lastBattleFailedNotifyKey)) {
            return;
        }
        lastBattleFailedNotifyKey = key;
        String message = "战败了快去看看。";
        append("战斗提醒：" + message);
        postSystemNotification(message);
        sendExternalNotification(message, false);
    }

    private void readRemoteDiagnosticsQuietly(boolean taskMode) {
        try {
            String resultPath = taskMode ? REMOTE_TASK_RESULT : REMOTE_RESULT;
            String logPath = taskMode ? REMOTE_TASK_LOG : REMOTE_LOG;
            String result = runBackendShellQuiet("cat " + resultPath + " 2>/dev/null || true");
            if (result.trim().length() > 0) {
                append("最近一次结果：" + compactLogText(result.trim()));
            }
            String log = runBackendShellQuiet("tail -n 80 " + logPath + " 2>/dev/null || true");
            if (log.trim().length() > 0) {
                append("后端日志摘要：" + compactLogText(log.trim()));
            }
            String stdout = runBackendShellQuiet("tail -n 80 " + REMOTE_BACKEND_STDOUT + " 2>/dev/null || true");
            if (stdout.trim().length() > 0) {
                append("后端标准输出：" + compactLogText(stdout.trim()));
            }
        } catch (Throwable ignored) {
        }
    }

    private void startPreviewLoop(final boolean taskMode) {
        startPreviewLoop(taskMode, taskMode ? TEXT_STARTING_GAME : TEXT_CONNECTING_PREVIEW);
    }

    private void startPreviewLoop(final boolean taskMode, String loadingText) {
        previewActive = true;
        previewTaskMode = taskMode;
        final int generation = ++previewLoopGeneration;
        lastPreviewStamp = "";
        lastSocketPreviewSeq = -1;
        lastSocketPreviewFrameMs = System.currentTimeMillis();
        lastTaskPreviewFallbackMs = 0;
        previewHasFrame = false;
        lastPhasePollMs = 0;
        lastTaskResultLogKey = "";
        lastProbeResultLogKey = "";
        lastBattleFailedNotifyKey = "";
        synchronized (previewFrameLock) {
            pendingPreviewBytes = null;
            pendingPreviewInfo = "";
        }
        previewRenderScheduled.set(false);
        showDebugPreviewMarker(taskMode && debugMode);
        showPreviewLoading(loadingText);
        previewExecutor.execute(new Runnable() {
            @Override
            public void run() {
                while (previewActive && generation == previewLoopGeneration) {
                    try {
                        boolean refreshed = refreshPreviewStreamFromSocket(taskMode ? "stream task" : "stream probe", taskMode, generation);
                        if (!refreshed) {
            if (taskMode) {
                maybeRefreshTaskPreviewFallback("task fallback");
                            } else {
                                refreshPreviewInternal(REMOTE_LAST, "live probe");
                            }
                            Thread.sleep(350);
                        }
                        if (previewActive && previewHasFrame
                                && System.currentTimeMillis() - lastSocketPreviewFrameMs > 4200) {
                            showPreviewLoading("实时画面重连中");
                            append("实时画面超过 4 秒未收到新帧，正在重连。");
                            lastSocketPreviewSeq = -1;
                            lastSocketPreviewFrameMs = System.currentTimeMillis();
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastPhasePollMs >= 1000) {
                            lastPhasePollMs = now;
                            if (taskMode) {
                                updatePhaseFromRemoteResult();
                            } else {
                                updateProbePhaseFromRemoteResult();
                            }
                        }
                        keepPreviewStatusAttached(taskMode);
                        Thread.sleep(taskMode ? 180 : 220);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable ignored) {
                        try {
                            if (taskMode) {
                                maybeRefreshTaskPreviewFallback("task reconnect fallback");
                            } else {
                                refreshPreviewInternal(REMOTE_LAST, "live fallback probe");
                            }
                            Thread.sleep(500);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            return;
                        } catch (Throwable fallbackIgnored) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            }
        });
    }

    private void refreshTaskPreviewFallback(String label) throws Exception {
        String[] candidates = new String[]{
                REMOTE_TASK_AFTER_WAIT,
                REMOTE_TASK_AFTER,
                REMOTE_TASK_AFTER_OPEN,
                REMOTE_TASK_FRAME,
                REMOTE_TASK_BEFORE
        };
        for (int i = 0; i < candidates.length; i++) {
            if (refreshPreviewInternalIfAvailable(candidates[i], label + " " + (i + 1), false)) {
                return;
            }
        }
    }

    private void maybeRefreshTaskPreviewFallback(String label) throws Exception {
        long now = System.currentTimeMillis();
        if (now - lastTaskPreviewFallbackMs < 2500) {
            return;
        }
        lastTaskPreviewFallbackMs = now;
        refreshTaskPreviewFallback(label);
    }

    private void keepPreviewStatusAttached(boolean taskMode) {
        long now = System.currentTimeMillis();
        if (now - lastPreviewAttachCheckMs < 1500) {
            return;
        }
        lastPreviewAttachCheckMs = now;
        if (!taskMode) {
            return;
        }
        try {
            String output = runBackendShellQuiet("cat " + REMOTE_TASK_RESULT + " 2>/dev/null || true");
            String phase = extractResultValue(output, "phase");
            if (phase.length() == 0) {
                return;
            }
            if ("finished".equals(phase) || "workflow_finished".equals(phase)
                    || phase.startsWith("preview_keep_alive_")) {
                setStatus("Preview", 0xff2563eb);
            } else {
                setStatus("Running", 0xff1d4ed8);
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopPreviewLoop() {
        previewActive = false;
        showDebugPreviewMarker(false);
        hidePreviewLoading();
    }

    private void restartPreviewConnection(String reason) {
        if (!previewActive) {
            return;
        }
        append(reason);
        previewLoopGeneration++;
        startPreviewLoop(previewTaskMode);
    }

    private void resetPreviewPane(final String message) {
        previewActive = false;
        showDebugPreviewMarker(false);
        previewLoopGeneration++;
        lastPreviewStamp = "";
        lastSocketPreviewSeq = -1;
        lastSocketPreviewFrameMs = 0;
        previewHasFrame = true;
        synchronized (previewFrameLock) {
            pendingPreviewBytes = null;
            pendingPreviewInfo = "";
        }
        previewRenderScheduled.set(false);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                hidePreviewLoadingOnMainThread();
                if (previewImage != null) {
                    previewImage.setImageDrawable(null);
                    previewImage.setBackgroundColor(0xff0f172a);
                }
                if (frameText != null) {
                    frameText.setText(message == null || message.length() == 0 ? TEXT_WAITING_PREVIEW : message);
                }
                if (displayValueText != null) {
                    displayValueText.setText("-");
                }
                if (framesValueText != null) {
                    framesValueText.setText("-");
                }
                if (actionValueText != null) {
                    actionValueText.setText("-");
                }
            }
        });
    }

    private void showPreviewLoading(final String baseText) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (previewLoadingOverlay == null || previewLoadingText == null) {
                    return;
                }
                previewLoadingBaseText = baseText == null || baseText.length() == 0
                        ? TEXT_CONNECTING_PREVIEW
                        : baseText;
                previewLoadingTick = 0;
                updatePreviewLoadingLabel();
                previewLoadingOverlay.setVisibility(View.VISIBLE);
                if (frameText != null) {
                    frameText.setText(previewLoadingBaseText);
                }
                mainHandler.removeCallbacks(previewLoadingTicker);
                mainHandler.postDelayed(previewLoadingTicker, 480);
            }
        });
    }

    private void showDebugPreviewMarker(final boolean visible) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (debugPreviewMarker != null) {
                    debugPreviewMarker.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    private String getPreviewLoadingTextForTask(String taskName) {
        return "stop_game".equals(taskName) ? TEXT_STOPPING_GAME : TEXT_STARTING_GAME;
    }

    private void hidePreviewLoading() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                hidePreviewLoadingOnMainThread();
                if (!previewActive && !previewHasFrame && frameText != null) {
                    frameText.setText(TEXT_WAITING_PREVIEW);
                }
            }
        });
    }

    private void hidePreviewLoadingOnMainThread() {
        mainHandler.removeCallbacks(previewLoadingTicker);
        if (previewLoadingOverlay != null) {
            previewLoadingOverlay.setVisibility(View.GONE);
        }
    }

    private void updatePreviewLoadingLabel() {
        if (previewLoadingText == null) {
            return;
        }
        StringBuilder label = new StringBuilder(previewLoadingBaseText);
        for (int i = 0; i < previewLoadingTick; i++) {
            label.append('.');
        }
        previewLoadingText.setText(label.toString());
    }

    private boolean hasVisiblePreviewContent(Bitmap bitmap, int encodedBytes) {
        if (encodedBytes >= 8192) {
            return true;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
        int xStep = Math.max(1, width / 40);
        int yStep = Math.max(1, height / 24);
        int nonBlackSamples = 0;
        int visibleSamples = 0;
        int strongSamples = 0;
        int totalSamples = 0;
        for (int y = yStep / 2; y < height; y += yStep) {
            for (int x = xStep / 2; x < width; x += xStep) {
                totalSamples++;
                int pixel = bitmap.getPixel(x, y);
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;
                int sum = red + green + blue;
                int max = Math.max(red, Math.max(green, blue));
                int min = Math.min(red, Math.min(green, blue));
                if (sum > 30 || max - min > 10) {
                    nonBlackSamples++;
                }
                if (sum > 120 || max - min > 35) {
                    visibleSamples++;
                    if (sum > 210 || max - min > 60) {
                        strongSamples++;
                    }
                    if (visibleSamples >= 12 && strongSamples >= 3) {
                        return true;
                    }
                }
            }
        }
        return totalSamples > 0
                && ((visibleSamples > totalSamples / 8 && strongSamples >= 4)
                || nonBlackSamples > totalSamples / 18);
    }

    private void refreshPreviewOnce(final String remoteFrame, final String label) {
        previewExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    refreshPreviewInternal(remoteFrame, label);
                } catch (Throwable error) {
                    append("Preview refresh failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
                }
            }
        });
    }

    private void refreshPreviewInternal(String remoteFrame, String label) throws Exception {
        refreshPreviewInternalIfAvailable(remoteFrame, label);
    }

    private boolean refreshPreviewInternalIfAvailable(String remoteFrame, String label) throws Exception {
        return refreshPreviewInternalIfAvailable(remoteFrame, label, true);
    }

    private boolean refreshPreviewInternalIfAvailable(String remoteFrame, String label, boolean useRefreshLock)
            throws Exception {
        boolean locked = false;
        if (useRefreshLock) {
            if (!previewRefreshing.compareAndSet(false, true)) {
                return false;
            }
            locked = true;
        }
        try {
            return refreshPreviewInternalUnlocked(remoteFrame, label);
        } finally {
            if (locked) {
                previewRefreshing.set(false);
            }
        }
    }

    private boolean refreshPreviewInternalUnlocked(String remoteFrame, String label) throws Exception {
        String stamp = getRemoteFrameStamp(remoteFrame);
        if (stamp.length() == 0) {
            return false;
        }
        if (stamp.equals(lastPreviewStamp)) {
            return false;
        }
        final byte[] bytes = readBackendFileBytes(remoteFrame);
        if (bytes.length <= 0) {
            return false;
        }
        lastPreviewStamp = stamp;
        final String info = label + " / " + bytes.length + " bytes / "
                + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        postPreviewFrame(bytes, info);
        return true;
    }

    private boolean refreshPreviewFromSocket(String label) throws Exception {
        if (!previewRefreshing.compareAndSet(false, true)) {
            return true;
        }
        LocalSocket socket = null;
        try {
            socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(
                    PREVIEW_SOCKET_NAME,
                    LocalSocketAddress.Namespace.ABSTRACT
            ));
            socket.setSoTimeout(500);
            InputStream input = socket.getInputStream();
            int seq = readInt(input);
            int length = readInt(input);
            if (length <= 0) {
                return false;
            }
            if (seq == lastSocketPreviewSeq) {
                return true;
            }
            final byte[] bytes = readExactly(input, length);
            if (bytes.length <= 0) {
                return false;
            }
            lastSocketPreviewSeq = seq;
            final String info = label + " / " + bytes.length + " bytes / "
                    + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            postPreviewFrame(bytes, info);
            return true;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
            previewRefreshing.set(false);
        }
    }

    private boolean refreshPreviewStreamFromSocket(String label, boolean taskMode, int generation) throws Exception {
        LocalSocket socket = null;
        try {
            socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(
                    PREVIEW_SOCKET_NAME,
                    LocalSocketAddress.Namespace.ABSTRACT
            ));
            socket.setSoTimeout(1200);
            InputStream input = socket.getInputStream();
            boolean receivedAnyFrame = false;
            long lastFrameMs = System.currentTimeMillis();
            while (previewActive && generation == previewLoopGeneration) {
                int seq;
                int length;
                try {
                    seq = readInt(input);
                    length = readInt(input);
                } catch (InterruptedIOException timeout) {
                    long now = System.currentTimeMillis();
                    if (now - lastFrameMs > 2600) {
                        return receivedAnyFrame;
                    }
                    continue;
                }
                if (length <= 0) {
                    if (System.currentTimeMillis() - lastFrameMs > 2600) {
                        return receivedAnyFrame;
                    }
                    continue;
                }
                final byte[] bytes = readExactly(input, length);
                if (bytes.length != length) {
                    return receivedAnyFrame;
                }
                if (seq == lastSocketPreviewSeq) {
                    if (System.currentTimeMillis() - lastFrameMs > 5000) {
                        return receivedAnyFrame;
                    }
                    continue;
                }
                lastSocketPreviewFrameMs = System.currentTimeMillis();
                lastFrameMs = lastSocketPreviewFrameMs;
                lastSocketPreviewSeq = seq;
                receivedAnyFrame = true;
                final String info = label + " / " + bytes.length + " bytes / "
                        + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                postPreviewFrame(bytes, info, generation);
                long now = System.currentTimeMillis();
                if (now - lastPhasePollMs >= 1000) {
                    lastPhasePollMs = now;
                    if (taskMode) {
                        updatePhaseFromRemoteResult();
                    } else {
                        updateProbePhaseFromRemoteResult();
                    }
                    keepPreviewStatusAttached(taskMode);
                }
            }
            return receivedAnyFrame;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void postPreviewFrame(byte[] bytes, String info) {
        postPreviewFrame(bytes, info, previewLoopGeneration);
    }

    private void postPreviewFrame(byte[] bytes, String info, final int generation) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        synchronized (previewFrameLock) {
            pendingPreviewBytes = bytes;
            pendingPreviewInfo = info == null ? "" : info;
        }
        if (!previewRenderScheduled.compareAndSet(false, true)) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (generation != previewLoopGeneration) {
                    previewRenderScheduled.set(false);
                    return;
                }
                byte[] bytesToRender;
                String infoToRender;
                synchronized (previewFrameLock) {
                    bytesToRender = pendingPreviewBytes;
                    infoToRender = pendingPreviewInfo;
                    pendingPreviewBytes = null;
                    pendingPreviewInfo = "";
                }
                try {
                    if (bytesToRender != null && bytesToRender.length > 0) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(
                                bytesToRender, 0, bytesToRender.length, previewDecodeOptions);
                        if (bitmap != null) {
                            boolean hasVisibleContent = previewHasFrame
                                    || hasVisiblePreviewContent(bitmap, bytesToRender.length);
                            if (hasVisibleContent) {
                                previewImage.setImageBitmap(bitmap);
                                previewHasFrame = true;
                                hidePreviewLoadingOnMainThread();
                                frameText.setText(infoToRender);
                            } else if (frameText != null) {
                                if (!previewHasFrame) {
                                    previewImage.setImageDrawable(null);
                                    previewImage.setBackgroundColor(0xff111111);
                                }
                                frameText.setText(previewLoadingBaseText.length() == 0
                                        ? TEXT_STARTING_GAME
                                        : previewLoadingBaseText);
                            }
                        }
                    }
                } finally {
                    previewRenderScheduled.set(false);
                    boolean hasPendingFrame;
                    synchronized (previewFrameLock) {
                        hasPendingFrame = pendingPreviewBytes != null;
                    }
                    if (hasPendingFrame && previewRenderScheduled.compareAndSet(false, true)) {
                        mainHandler.post(this);
                    }
                }
            }
        });
    }

    private String getRemoteFrameStamp(String remoteFrame) throws Exception {
        String path = shellQuote(remoteFrame);
        String command = "if [ -f " + path + " ]; then "
                + "(stat -c '%s:%Y' " + path + " 2>/dev/null "
                + "|| toybox stat -c '%s:%Y' " + path + " 2>/dev/null "
                + "|| ls -ln " + path + " | awk '{print $5\":\"$6\":\"$7\":\"$8}'); "
                + "fi";
        String output = runBackendShellQuiet(command).trim();
        if (output.length() == 0) {
            return "";
        }
        return remoteFrame + ":" + output;
    }

    private void updatePhaseFromRemoteResult() {
        try {
            String output = runBackendShellQuiet("cat " + REMOTE_TASK_RESULT + " 2>/dev/null || true");
            if (output.trim().length() == 0) {
                return;
            }
            String phase = extractResultValue(output, "phase");
            String task = extractResultValue(output, "task");
            String display = extractResultValue(output, "displayId");
            String state = extractResultValue(output, "finalState");
            String frames = extractResultValue(output, "frames");
            String actions = extractResultValue(output, "actionCount");
            notifyBattleFailedIfNeeded(task, state);
            String stateText = formatTaskState(state);
            String phaseText = formatTaskPhase(phase);
            String taskText = formatTaskName(task.length() == 0 ? "start_game" : task);
            String text = "任务：" + (phaseText.length() == 0 ? "运行中" : phaseText)
                    + " / " + (stateText.length() == 0 ? "..." : stateText);
            setPhase(text);
            updateMetrics(
                    taskText,
                    display.length() == 0 ? "-" : display,
                    frames.length() == 0 ? "-" : frames,
                    actions.length() == 0 ? "-" : actions,
                    stateText.length() == 0 ? "running" : stateText
            );
            logTaskResultIfChanged(taskText, phaseText, display, frames, actions, stateText);
        } catch (Throwable ignored) {
        }
    }

    private void logTaskResultIfChanged(String task, String phase, String display, String frames,
                                        String actions, String state) {
        String key = task + "|" + phase + "|" + display + "|" + frames + "|" + actions + "|" + state;
        if (key.equals(lastTaskResultLogKey)) {
            return;
        }
        lastTaskResultLogKey = key;
        append("运行状态：" + task
                + "，阶段=" + (phase.length() == 0 ? "运行中" : phase)
                + "，屏幕=" + (display.length() == 0 ? "-" : display)
                + "，帧数=" + (frames.length() == 0 ? "-" : frames)
                + "，动作=" + (actions.length() == 0 ? "-" : actions)
                + "，结果=" + (state.length() == 0 ? "运行中" : state));
    }

    private String formatTaskPhase(String phase) {
        if (phase == null || phase.length() == 0) {
            return "";
        }
        if ("finished".equals(phase)) {
            return "已完成";
        }
        if ("workflow_finished".equals(phase)) {
            return "工作流已完成";
        }
        if (phase.startsWith("workflow_") && phase.endsWith("_start")) {
            return "开始步骤 " + formatTaskName(phase.substring("workflow_".length(), phase.length() - "_start".length()));
        }
        if (phase.startsWith("workflow_") && phase.endsWith("_finish")) {
            return "完成步骤 " + formatTaskName(phase.substring("workflow_".length(), phase.length() - "_finish".length()));
        }
        if (phase.startsWith("waiting_")) {
            return "等待游戏画面 " + phase.substring("waiting_".length());
        }
        if (phase.startsWith("loading_wait_")) {
            return "等待加载 " + phase.substring("loading_wait_".length());
        }
        if (phase.startsWith("back_home_attempt_")) {
            return "回首页尝试 " + phase.substring("back_home_attempt_".length());
        }
        if (phase.startsWith("handle_update_wait_")) {
            return "检查更新 " + phase.substring("handle_update_wait_".length());
        }
        if (phase.startsWith("handle_update_home_")) {
            return "首页更新检查 " + phase.substring("handle_update_home_".length());
        }
        return phase;
    }

    private String formatTaskState(String state) {
        if ("login_required".equals(state)) {
            return "需要先在游戏内登录";
        }
        if ("network_retry_required".equals(state)) {
            return "网络或服务器连接失败，请手动重试。";
        }
        if ("home_clear".equals(state)) {
            return "已回到首页";
        }
        if ("workflow_daily_safe_completed".equals(state)) {
            return "工作流已完成";
        }
        if ("visit_mail_page_no_claim_button".equals(state)) {
            return "邮箱页已打开，没有可领取按钮";
        }
        if ("visit_mail_page_claim_available".equals(state)) {
            return "邮箱页已打开，发现可领取按钮但调试模式未领取";
        }
        if ("mail_no_claim_button".equals(state)) {
            return "邮箱页没有可领取奖励";
        }
        if ("mail_claimed_home_clear".equals(state)) {
            return "邮箱奖励已领取并回到首页";
        }
        if ("mail_claim_attempted".equals(state)) {
            return "邮件领取已尝试，请查看实时画面确认。";
        }
        if ("mail_red_dot_previewed".equals(state)) {
            return "邮件发现可领取入口，调试模式已跳过领取。";
        }
        if ("daily_rewards_claim_attempted".equals(state)) {
            return "每日/每周任务奖励已尝试领取。";
        }
        if ("daily_rewards_red_dot_previewed".equals(state)) {
            return "每日/每周任务发现奖励入口，调试模式已跳过领取";
        }
        if ("claim_daily_rewards_still_home".equals(state)) {
            return "每日/每周任务页未打开，未执行领取";
        }
        if ("friend_points_claim_attempted".equals(state)) {
            return "好友点数已尝试收取和赠送。";
        }
        if ("friend_points_red_dot_previewed".equals(state)) {
            return "好友页已打开，调试模式已跳过一键领取。";
        }
        if ("claim_friend_points_still_home".equals(state)) {
            return "好友页未打开，未执行收取赠送。";
        }
        if ("outpost_reward_claim_attempted".equals(state)) {
            return "前哨防御库存奖励已尝试领取。";
        }
        if ("outpost_clean_sweep_red_dot_previewed".equals(state)) {
            return "前哨防御已打开一键歼灭入口，调试模式已跳过确认。";
        }
        if ("claim_outpost_defense_still_home".equals(state)) {
            return "前哨防御页未打开，未执行领取";
        }
        if ("pass_rewards_claim_attempted".equals(state)) {
            return "PASS 奖励已尝试领取。";
        }
        if ("pass_rewards_red_dot_previewed".equals(state)) {
            return "PASS 奖励入口已检查，调试模式已跳过领取。";
        }
        if ("claim_pass_rewards_still_home".equals(state)) {
            return "PASS 页未打开，未执行领取";
        }
        if ("visit_free_shop_opened".equals(state)) {
            return "商店页已打开，未购买";
        }
        if ("free_shop_purchase_dialog_previewed".equals(state)) {
            return "商店购买弹窗已打开，调试模式已跳过购买确认";
        }
        if ("inquiry_and_gift_red_dot_previewed".equals(state)) {
            return "咨询入口已打开，调试模式已跳过批量咨询确认";
        }
        if ("sim_room_red_dot_previewed".equals(state)) {
            return "模拟室页面已打开，调试模式已跳过快速操作。";
        }
        if ("climb_tower_red_dot_previewed".equals(state)) {
            return "爬塔页面已打开，调试模式已跳过进入战斗";
        }
        if ("climb_tower_battle_previewed".equals(state)) {
            return "爬塔目标已打开，调试模式已跳过进入战斗";
        }
        if ("climb_tower_attempted".equals(state)) {
            return "爬塔战斗已尝试执行";
        }
        if ("climb_tower_no_enabled_target".equals(state)) {
            return "爬塔没有启用的目标";
        }
        if ("climb_tower_choice_not_ready".equals(state)) {
            return "爬塔塔选择页未就绪，已停止以避免打错塔";
        }
        if (state != null && state.indexOf("battle_failed_retry") >= 0) {
            return "战败后已点击重新挑战，请查看游戏画面";
        }
        if ("visit_daily_rewards_popup_visible".equals(state)) {
            return "每日/每周任务页已打开，未领取";
        }
        if ("no_update_or_download_dialog".equals(state)) {
            return "没有发现更新或下载确认弹窗。";
        }
        if ("announcement_closed_for_update".equals(state)) {
            return "已关闭公告并完成更新检查。";
        }
        if ("announcement_closed_by_close".equals(state)) {
            return "已关闭公告。";
        }
        if ("announcement_still_visible".equals(state)) {
            return "公告仍在显示，请查看实时画面";
        }
        if (state != null && state.startsWith("workflow_running:")) {
            return "正在执行 " + formatTaskName(state.substring("workflow_running:".length()));
        }
        if (state != null && state.startsWith("manual_confirm_")) {
            return "需要人工确认：" + state.substring("manual_confirm_".length());
        }
        if (state != null && state.startsWith("client_update_external:")) {
            return "客户端更新跳转到外部页面：" + state.substring("client_update_external:".length());
        }
        return state == null ? "" : state;
    }

    private String formatTaskName(String task) {
        if (task == null || task.length() == 0) {
            return "";
        }
        if ("workflow_daily_safe".equals(task)) {
            return "日常工作流";
        }
        if ("start_game".equals(task)) {
            return "启动游戏";
        }
        if ("back_to_home".equals(task)) {
            return "回到首页";
        }
        if ("handle_update".equals(task)) {
            return "处理更新";
        }
        if ("visit_mail".equals(task)) {
            return "访问邮箱";
        }
        if ("claim_mail".equals(task)) {
            return "领取邮箱";
        }
        if ("claim_daily_rewards".equals(task)) {
            return "领取每日/每周奖励";
        }
        if ("claim_friend_points".equals(task)) {
            return "收取赠送好友点";
        }
        if ("claim_outpost_defense".equals(task)) {
            return "领取前哨防御奖励";
        }
        if ("claim_pass_rewards".equals(task)) {
            return "领取 PASS 奖励";
        }
        if ("visit_daily_rewards".equals(task)) {
            return "访问每日/每周奖励";
        }
        if ("visit_friend_points".equals(task)) {
            return "访问好友页";
        }
        if ("visit_free_shop".equals(task)) {
            return "访问免费商店";
        }
        if ("visit_outpost_defense".equals(task)) {
            return "访问前哨防御";
        }
        if ("visit_climb_tower".equals(task) || "claim_climb_tower".equals(task)) {
            return "爬塔";
        }
        if ("stop_game".equals(task)) {
            return "结束游戏";
        }
        return task;
    }

    private void updateProbePhaseFromRemoteResult() {
        try {
            String output = runBackendShellQuiet("cat " + REMOTE_RESULT + " 2>/dev/null || true");
            if (output.trim().length() == 0) {
                return;
            }
            String phase = extractResultValue(output, "phase");
            String display = extractResultValue(output, "displayId");
            String frames = extractResultValue(output, "frames");
            String phaseText = "finished".equals(phase) ? "已完成" : (phase.length() == 0 ? "采集中" : phase);
            setPhase("探针：" + phaseText);
            updateMetrics("探针", display.length() == 0 ? "-" : display, frames.length() == 0 ? "-" : frames, "-", phaseText);
            String key = phaseText + "|" + display + "|" + frames;
            if (!key.equals(lastProbeResultLogKey)) {
                lastProbeResultLogKey = key;
                append("探针状态：阶段=" + phaseText
                        + "，屏幕=" + (display.length() == 0 ? "-" : display)
                        + "，帧数=" + (frames.length() == 0 ? "-" : frames));
            }
        } catch (Throwable ignored) {
        }
    }

    private String extractResultValue(String text, String key) {
        String prefix = key + "=";
        String[] lines = text.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(prefix)) {
                return lines[i].substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private byte[] readFileBytes(File file) throws Exception {
        java.io.FileInputStream input = new java.io.FileInputStream(file);
        try {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return bytes;
        } finally {
            input.close();
        }
    }

    private byte[] readStreamBytes(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(160 * 1024);
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private byte[] readBackendFileBytes(String remotePath) throws Exception {
        String command = "cat " + shellQuote(remotePath) + " 2>/dev/null || true";
        Process process;
        if (isShizukuBackend()) {
            ensureShizukuReady();
            process = newShizukuProcess(new String[]{"sh", "-c", command});
        } else {
            process = startShell("su -c " + shellQuote(command));
        }
        byte[] stdout = readStreamBytes(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("read backend file exit=" + exit
                    + " path=" + remotePath + " stderr=" + stderr);
        }
        return stdout;
    }

    private int readInt(InputStream input) throws Exception {
        byte[] bytes = readExactly(input, 4);
        if (bytes.length != 4) {
            throw new IllegalStateException("short int read");
        }
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    private byte[] readExactly(InputStream input, int length) throws Exception {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(bytes, offset, length - offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        if (offset == length) {
            return bytes;
        }
        byte[] partial = new byte[offset];
        System.arraycopy(bytes, 0, partial, 0, offset);
        return partial;
    }

    private String runShell(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        if (stderr.trim().length() > 0) {
            append("命令输出：" + compactLogText(stderr.trim()));
        }
        if (exit != 0) {
            throw new IllegalStateException("exit=" + exit + " command=" + command);
        }
        return stdout + stderr;
    }

    private String compactLogText(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\r', ' ').replace('\n', ' ').trim();
        while (compact.indexOf("  ") >= 0) {
            compact = compact.replace("  ", " ");
        }
        if (compact.length() > 360) {
            return compact.substring(0, 360) + "...";
        }
        return compact;
    }

    private String runShellQuiet(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("exit=" + exit + " command=" + command + " stderr=" + stderr);
        }
        return stdout + stderr;
    }

    private String readAll(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void append(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                logBuffer.append("[").append(time).append("] ").append(message).append("\n");
                if (logBuffer.length() > 24000) {
                    logBuffer.delete(0, logBuffer.length() - 24000);
                }
                String text = logBuffer.toString();
                for (int i = 0; i < logViews.size(); i++) {
                    logViews.get(i).setText(text);
                }
                for (int i = 0; i < logScrollViews.size(); i++) {
                    final ScrollView scroll = logScrollViews.get(i);
                    scroll.post(new Runnable() {
                        @Override
                        public void run() {
                            scroll.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        });
    }

    private void setStatus(final String text, final int color) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                statusText.setText(text);
                statusText.setTextColor(0xffffffff);
                statusText.setBackground(roundedBackground(color, dp(16), 0));
            }
        });
    }

    private void setPhase(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                phaseText.setText(text);
            }
        });
    }

    private void setButtons(final boolean enabled) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                startGameButton.setEnabled(enabled);
                workflowButton.setEnabled(enabled);
                stopGameButton.setEnabled(enabled);
                if (logExportButton != null) {
                    logExportButton.setEnabled(enabled);
                }
                stopButton.setEnabled(!enabled);
                for (int i = 0; i < workflowItems.size(); i++) {
                    WorkflowItem item = workflowItems.get(i);
                    if (item.checkBox != null) {
                        item.checkBox.setEnabled(enabled && item.enabled);
                    }
                    if (item.handleView != null) {
                        item.handleView.setEnabled(enabled);
                    }
                    if (item.detailView != null) {
                        item.detailView.setEnabled(true);
                        item.detailView.setAlpha(1f);
                    }
                }
                if (backgroundModeCheckBox != null) {
                    backgroundModeCheckBox.setEnabled(enabled);
                }
                if (debugModeCheckBox != null) {
                    debugModeCheckBox.setEnabled(enabled);
                }
                if (backendModeText != null) {
                    backendModeText.setEnabled(enabled);
                    backendModeText.setAlpha(enabled ? 1f : 0.55f);
                }
            }
        });
    }

    private void updateMetrics(final String task, final String display, final String frames, final String actions, final String state) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                taskValueText.setText(task);
                displayValueText.setText(display);
                framesValueText.setText(frames);
                actionValueText.setText(actions);
                stateValueText.setText(state);
                activeTaskChip.setText("16:9  1280x720");
            }
        });
    }

    private static final class PreviewPane extends LinearLayout {
        private final int footerHeight;

        PreviewPane(Context context, int footerHeight) {
            super(context);
            this.footerHeight = footerHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int imageHeight = Math.round(width * 9f / 16f);
            int desiredHeight = imageHeight + footerHeight + getPaddingTop() + getPaddingBottom();
            int exactHeight = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, exactHeight);
        }
    }

    static final class WorkflowItem {
        final TaskCatalog.TaskSpec task;
        final boolean enabled;
        boolean checked;
        LinearLayout row;
        CheckBox checkBox;
        View handleView;
        TextView detailView;
        TextView titleView;
        TextView subtitleView;
        TextView statusView;

        WorkflowItem(TaskCatalog.TaskSpec task, boolean checked) {
            this.task = task;
            this.enabled = task.enabled;
            this.checked = checked;
        }
    }
}
