package dev.jason.gboardpatches.extension.settings;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.BuildConfig;
import dev.jason.gboardpatches.extension.backuprestore.GboardExportFileNames;
import dev.jason.gboardpatches.extension.settings.keyboardpreview.GboardKeyboardPreviewController;

public final class GboardPatchesSettingsActivity extends Activity
        implements GboardPatchesSettingsContract.Host {
    private static final String TAG = "GboardPatches";
    public static final String EXTRA_NAVIGATION_PATH =
            "dev.jason.gboardpatches.extension.extra.PATCHES_NAVIGATION_PATH";
    private static final String ENTER_PREF_HEADER_EXTRA = "ENTER_PREF_HEADER";
    private static final String GBOARD_SETTINGS_ACTIVITY_CLASS =
            "com.google.android.apps.inputmethod.latin.preference.SettingsActivity";
    private static final String SUPPORTED_DEVELOPER_OPTIONS_TARGET_VERSION =
            "18.5.4.993608717-release-arm64-v8a";
    private static final String GBOARD_PACKAGE_STABLE =
            "com.google.android.inputmethod.latin";
    private static final String GBOARD_PACKAGE_JASON_DEV =
            "com.google.android.inputmethod.latin.jason.dev";
    private static final String GBOARD_PACKAGE_REVERSED_DEV =
            "dev.jason.com.google.android.inputmethod.latin";
    private static final String LIVE_TRANSCRIBE_PACKAGE_NAME =
            "com.google.audio.hearing.visualization.accessibility.scribe";
    private static final String SPEECH_SERVICES_PACKAGE_NAME =
            "com.google.android.tts";
    private static final String SPEECH_SERVICES_RECOGNITION_SERVICE_CLASS_NAME =
            "com.google.android.apps.speech.tts.googletts.service."
                    + "GoogleTTSRecognitionService";
    private static final String SPEECH_SERVICES_PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.google.android.tts";
    private static final String LIVE_TRANSCRIBE_PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.google.audio.hearing.visualization.accessibility.scribe";
    private static final String PLAY_STORE_PACKAGE_NAME = "com.android.vending";
    private static final long OFFLINE_SPEECH_LANGUAGE_QUERY_TIMEOUT_MS = 10_000L;
    private static final long RESTART_ICON_ROTATION_DURATION_MS = 700L;
    private static final long RESTART_DELAY_MS = 500L;
    private static final long RESTART_CRASH_RECOVERY_RESTORE_DELAY_MS = 2000L;
    private static final int REQUEST_CREATE_TEXT_DOCUMENT = 0x4742;
    private static final int REQUEST_OPEN_TEXT_DOCUMENT = 0x4743;
    private static final int REQUEST_OPEN_DOCUMENT_TREE = 0x4746;
    private static final int REQUEST_RUNTIME_PERMISSION = 0x4747;
    private static final int REQUEST_CREATE_BINARY_DOCUMENT = 0x4748;
    private static final int REQUEST_OPEN_BINARY_DOCUMENT = 0x4749;
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final int TOOLBAR_SCROLLED_THRESHOLD_DP = 24;
    private static final long TOOLBAR_SCROLL_TRANSITION_MS = 200L;
    private static final int SEGMENT_OUTER_RADIUS_DP = 24;
    private static final int SEGMENT_INNER_RADIUS_DP = 6;
    private static final int SEGMENT_GAP_DP = 2;
    private static final long SEGMENT_PRESS_ANIMATION_MS = 200L;
    private static final float DISABLED_CONTENT_ALPHA = 0.38f;
    private static final float PRESSED_ACCENT_FRACTION = 0.09f;
    private static final float RIPPLE_ACCENT_ALPHA = 0.05f;
    private static final float TOOLBAR_SCROLLED_ACCENT_FRACTION = 0.06f;
    private static final int LARGE_TITLE_LONG_THRESHOLD = 6;
    private static final float LARGE_TITLE_SP = 36f;
    private static final float LARGE_TITLE_LONG_SP = 28f;
    private static final float ROW_TITLE_SP = 16f;
    private static final float ROW_SUMMARY_SP = 13.5f;
    private static final int NO_SCROLL_POSITION_REQUESTED = -1;
    private static final String TOOLBAR_TITLE_PATCHES = "Patches";
    private static final String ABOUT_AUTHOR_URL = "https://github.com/jasonwu1994";
    private static final String ABOUT_PATCH_REPOSITORY_URL =
            "https://github.com/jasonwu1994/Gboard-patches";
    private static final String DOCUMENT_TYPE_FALLBACK = "text/plain";
    private static final String DOCUMENT_PICKER_FAILED = "Unable to open file picker.";
    private static final String DOCUMENT_WRITE_FAILED = "Failed to export file.";
    private static final String DOCUMENT_READ_FAILED = "Failed to import file.";
    private Palette palette;
    private LinearLayout toolbarView;
    private TextView toolbarTitleView;
    private LinearLayout toolbarActionsView;
    private View restartButton;
    private TextView restartBadgeView;
    private int restartPendingCount;
    private final GboardRestartPendingChanges.Listener restartPendingListener =
            pendingCount -> postToDecorView(() -> applyRestartPendingCount(pendingCount, true));
    private ObjectAnimator restartButtonAnimator;
    private View headerCardView;
    private boolean toolbarScrolledState;
    private boolean toolbarTitleShown;
    private boolean toolbarTitleForced;
    private ValueAnimator toolbarColorAnimator;
    private TextView headerTitleView;
    private TextView headerSummaryView;
    private LinearLayout panelContainer;
    private ScrollView contentScrollView;
    private LinearLayout contentColumn;
    private LinearLayout screenActionContainer;
    private GboardKeyboardPreviewController keyboardPreviewController;
    private List<GboardPatchesSettingsContract.Feature> features;
    private final GboardPatchesSettingsOrchestrator<GboardPatchesSettingsContract.Feature,
            GboardPatchesSettingsContract.Intent>
            settingsOrchestrator =
            new GboardPatchesSettingsOrchestrator<GboardPatchesSettingsContract.Feature,
                    GboardPatchesSettingsContract.Intent>();
    private final GboardPatchesSettingsScrollState scrollState =
            new GboardPatchesSettingsScrollState();
    private boolean featuresInitialized;
    private Object backInvokedCallback;
    private final Handler screenRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable screenRefreshRunnable = this::refresh;
    private final Runnable deferredRenderRunnable = this::initializeFeaturesAndRenderSafely;
    private final ExecutorService screenBuildExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "GboardPatchesScreenBuild");
                thread.setDaemon(true);
                return thread;
            });
    private final ExecutorService backgroundStateExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "GboardPatchesBackgroundState");
                thread.setDaemon(true);
                return thread;
            });
    private final Object screenBuildLock = new Object();
    private int requestedScrollYOnNextScreenApply = 0;
    private boolean initialFeatureFromIntentHandled;
    private PendingTextDocumentWrite pendingTextDocumentWrite;
    private GboardPatchesSettingsContract.StringValueConsumer pendingTextDocumentReader;
    private PendingBinaryDocumentWrite pendingBinaryDocumentWrite;
    private GboardPatchesSettingsContract.BinaryDocumentConsumer pendingBinaryDocumentReader;
    private GboardPatchesSettingsContract.StringValueConsumer pendingDocumentTreeConsumer;
    private GboardPatchesSettingsContract.Feature lifecycleVisibleFeature;
    private boolean featureLifecycleEnabled;
    private GboardPatchesSettingsContract.BooleanValueConsumer pendingPermissionConsumer;
    private volatile GboardPatchesSettingsContract.OfflineSpeechLanguages offlineSpeechLanguages =
            GboardPatchesSettingsContract.OfflineSpeechLanguages.loading();
    private SpeechRecognizer offlineSpeechRecognizer;
    private int offlineSpeechLanguageQueryGeneration;
    private Runnable offlineSpeechLanguageQueryTimeoutRunnable;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(GboardSettingsLocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(resolveActivityTheme());
        super.onCreate(savedInstanceState);
        try {
            scheduleRestartCrashRecoveryGuardRestore();
            palette = Palette.forConfiguration(getResources().getConfiguration());
            features = Collections.emptyList();
            featuresInitialized = false;
            configureWindow();
            View contentView = buildContentView();
            setContentView(contentView);
            installWindowInsetsHandling(contentView);
            registerBackCallback();
        } catch (Throwable throwable) {
            showFatalFallbackScreen("Failed to initialize patches settings activity", throwable);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        setRestartButtonPending(false);
        initialFeatureFromIntentHandled = false;
        scheduleDeferredRender();
    }

    @Override
    protected void onResume() {
        super.onResume();
        featureLifecycleEnabled = true;
        GboardRestartPendingChanges.addListener(restartPendingListener);
        applyRestartPendingCount(GboardRestartPendingChanges.pendingCount(), false);
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.resume()));
    }

    @Override
    protected void onPause() {
        featureLifecycleEnabled = false;
        GboardRestartPendingChanges.removeListener(restartPendingListener);
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.pause()));
        cancelDeferredRender();
        cancelOfflineSpeechLanguageQuery();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        featureLifecycleEnabled = false;
        GboardRestartPendingChanges.removeListener(restartPendingListener);
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.pause()));
        setRestartButtonPending(false);
        cancelDeferredRender();
        cancelOfflineSpeechLanguageQuery();
        unregisterBackCallback();
        detachKeyboardPreviewSafely();
        screenBuildExecutor.shutdownNow();
        backgroundStateExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        if (!navigateBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void submit(GboardPatchesSettingsContract.Intent intent) {
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.featureIntent(intent)));
    }

    @Override
    public GboardPatchesSettingsContract.OfflineSpeechLanguages getOfflineSpeechLanguages() {
        return offlineSpeechLanguages;
    }

    @Override
    public void refresh() {
        initializeFeaturesAndRenderSafely();
    }

    public void onManagedDialogShown() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(this::onManagedDialogShown);
            return;
        }
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.modalShown()));
    }

    public void onManagedDialogDismissed() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(this::onManagedDialogDismissed);
            return;
        }
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.modalDismissed()));
    }

    private void onKeyboardPreviewDismissed() {
        onManagedDialogDismissed();
        var feature = settingsOrchestrator.snapshot().getCurrent();
        if (feature != null && feature.updatesDuringKeyboardPreview()) refresh();
    }

    @Override
    public void openFeature(GboardPatchesSettingsContract.Feature feature) {
        if (feature == null) {
            return;
        }
        GboardPatchesSettingsOrchestrator.State<GboardPatchesSettingsContract.Feature> state =
                settingsOrchestrator.snapshot();
        scrollState.enterFeature(state.getCurrent() == null, currentScrollY());
        requestScrollPositionOnNextScreenApply(0);
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.open(feature)));
    }

    @Override
    public void openTargetSettingsHeader(int headerKeyResourceId) {
        if (headerKeyResourceId == 0) {
            return;
        }
        String currentPackage = getPackageName();
        if (!isSupportedTargetPackage(currentPackage)) {
            showUnsupportedDeveloperOptionsPackageDialog(currentPackage, headerKeyResourceId);
            return;
        }
        if (tryLaunchTargetSettingsHeader(currentPackage, headerKeyResourceId, false)) {
            return;
        }
        throw new ActivityNotFoundException(
                "No supported Gboard Developer options entry is available");
    }

    private boolean tryLaunchTargetSettingsHeader(String packageName, int headerKeyResourceId) {
        return tryLaunchTargetSettingsHeader(packageName, headerKeyResourceId, false);
    }

    private boolean tryLaunchTargetSettingsHeader(String packageName, int headerKeyResourceId,
            boolean allowUnknownVersion) {
        String targetVersionName = targetPackageVersionName(packageName);
        if (!allowUnknownVersion
                && !isSupportedDeveloperOptionsTargetVersion(targetVersionName)) {
            return false;
        }

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, GBOARD_SETTINGS_ACTIVITY_CLASS));
        intent.putExtra(ENTER_PREF_HEADER_EXTRA, headerKeyResourceId);
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            return false;
        }
    }

    private void showUnsupportedDeveloperOptionsPackageDialog(String packageName,
            int headerKeyResourceId) {
        String safePackageName = packageName == null ? "" : packageName;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(GboardSettingsText.get(
                        this, R.string.gboard_patches_developer_options_package_warning_title))
                .setMessage(GboardSettingsText.format(
                        this,
                        R.string.gboard_patches_developer_options_package_warning_message,
                        safePackageName))
                .setNegativeButton(GboardSettingsText.get(this, R.string.gboard_patches_dialog_close), null)
                .setPositiveButton(GboardSettingsText.get(
                        this, R.string.gboard_patches_developer_options_try_anyway),
                        (ignored, which) -> tryLaunchUnverifiedDeveloperOptions(
                                safePackageName,
                                headerKeyResourceId))
                .create();
        dialog.setOnDismissListener(ignored -> onManagedDialogDismissed());
        try {
            dialog.show();
            onManagedDialogShown();
            tintDialogButtons(dialog);
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to show unsupported Gboard package warning", throwable);
            showDeveloperOptionsLaunchFailure(safePackageName);
        }
    }

    private void tryLaunchUnverifiedDeveloperOptions(String packageName,
            int headerKeyResourceId) {
        try {
            if (!tryLaunchTargetSettingsHeader(packageName, headerKeyResourceId, true)) {
                showDeveloperOptionsLaunchFailure(packageName);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to open Developer options for " + packageName, throwable);
            showDeveloperOptionsLaunchFailure(packageName);
        }
    }

    private void showDeveloperOptionsLaunchFailure(String packageName) {
        Toast.makeText(
                this,
                GboardSettingsText.format(
                        this,
                        R.string.gboard_patches_developer_options_launch_failed,
                        packageName),
                Toast.LENGTH_LONG)
                .show();
    }

    private String targetPackageVersionName(String packageName) {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException | SecurityException exception) {
            return null;
        }
    }

    private List<String> targetSettingsPackages() {
        Set<String> packageNames = new LinkedHashSet<String>();
        String currentPackage = getPackageName();
        if (currentPackage != null && !currentPackage.isEmpty()) {
            packageNames.add(currentPackage);
        }
        packageNames.add(GBOARD_PACKAGE_STABLE);
        packageNames.add(GBOARD_PACKAGE_JASON_DEV);
        packageNames.add(GBOARD_PACKAGE_REVERSED_DEV);
        return new ArrayList<String>(packageNames);
    }

    static boolean isSupportedDeveloperOptionsTargetVersion(String versionName) {
        return SUPPORTED_DEVELOPER_OPTIONS_TARGET_VERSION.equals(versionName);
    }

    private static boolean isSupportedTargetPackage(String packageName) {
        return GBOARD_PACKAGE_STABLE.equals(packageName)
                || GBOARD_PACKAGE_JASON_DEV.equals(packageName)
                || GBOARD_PACKAGE_REVERSED_DEV.equals(packageName);
    }

    @Override
    public void showChoiceDialog(String title, String[] labels, String[] values,
            String currentValue, String customValue, Runnable customAction,
            GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        int checkedIndex = resolveCheckedIndex(values, currentValue);
        final Runnable[] pendingSelectionAction = new Runnable[1];
        Dialog dialog = buildBottomSheetDialog();
        LinearLayout sheet = buildBottomSheetContainer();
        sheet.setPadding(0, 0, 0, dp(24));
        sheet.addView(buildBottomSheetHandle(dp(14)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        titleView.setTypeface(mediumTypeface());
        titleView.setTextColor(palette.textPrimary);
        titleView.setPadding(dp(24), 0, dp(24), dp(10));
        sheet.addView(titleView);

        LinearLayout optionsLayout = new LinearLayout(this);
        optionsLayout.setOrientation(LinearLayout.VERTICAL);
        int optionCount = Math.min(labels.length, values.length);
        for (int index = 0; index < optionCount; index++) {
            String selectedValue = values[index];
            boolean selected = index == checkedIndex;

            LinearLayout optionRow = new LinearLayout(this);
            optionRow.setOrientation(LinearLayout.HORIZONTAL);
            optionRow.setGravity(Gravity.CENTER_VERTICAL);
            optionRow.setPadding(dp(24), dp(14), dp(24), dp(14));
            optionRow.setMinimumHeight(dp(56));
            optionRow.setClickable(true);
            optionRow.setFocusable(true);
            optionRow.setSelected(selected);
            optionRow.setBackground(buildRippleDrawable(0));

            FrameLayout radioFrame = new FrameLayout(this);
            GradientDrawable radioOuter = new GradientDrawable();
            radioOuter.setShape(GradientDrawable.OVAL);
            radioOuter.setColor(Color.TRANSPARENT);
            radioOuter.setStroke(dp(2), selected ? palette.accent : palette.textSecondary);
            radioFrame.setBackground(radioOuter);
            if (selected) {
                View innerDot = new View(this);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(palette.accent);
                innerDot.setBackground(dot);
                radioFrame.addView(innerDot,
                        new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));
            }
            LinearLayout.LayoutParams radioParams =
                    new LinearLayout.LayoutParams(dp(20), dp(20));
            radioParams.rightMargin = dp(16);
            optionRow.addView(radioFrame, radioParams);

            TextView labelView = new TextView(this);
            labelView.setText(labels[index]);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            labelView.setTextColor(palette.textPrimary);
            optionRow.addView(labelView, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            optionRow.setOnClickListener(view -> {
                pendingSelectionAction[0] = () -> runSafely(
                        "handle choice dialog selection",
                        () -> {
                            if (customValue != null
                                    && customAction != null
                                    && customValue.equals(selectedValue)) {
                                customAction.run();
                            } else {
                                valueConsumer.accept(selectedValue);
                                renderCurrentScreenSafely();
                            }
                        });
                dialog.dismiss();
            });
            optionsLayout.addView(optionRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        sheet.addView(buildCappedScrollView(optionsLayout, 0.6f));

        dialog.setContentView(sheet, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setOnDismissListener(ignored -> {
            Runnable action = pendingSelectionAction[0];
            pendingSelectionAction[0] = null;
            onManagedDialogDismissed();
            if (action == null) {
                return;
            }
            postToDecorView(action);
        });
        try {
            dialog.show();
            onManagedDialogShown();
            matchSheetWindow(dialog);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show choice dialog", throwable);
        }
    }

    private Dialog buildBottomSheetDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.36f);
            window.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        dialog.setOnShowListener(ignored -> matchSheetWindow(dialog));
        return dialog;
    }

    private LinearLayout buildBottomSheetContainer() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.surface);
        float radius = dp(28);
        background.setCornerRadii(new float[] {
                radius, radius, radius, radius, 0f, 0f, 0f, 0f });
        sheet.setBackground(background);
        return sheet;
    }

    private View buildBottomSheetHandle(int bottomMargin) {
        View handle = new View(this);
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.textTertiary);
        background.setCornerRadius(dp(2));
        handle.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(10);
        params.bottomMargin = bottomMargin;
        handle.setLayoutParams(params);
        return handle;
    }

    private ScrollView buildCappedScrollView(View content, float maxScreenFraction) {
        int maxHeight = Math.round(
                getResources().getDisplayMetrics().heightPixels * maxScreenFraction);
        ScrollView scrollView = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        return scrollView;
    }

    private void matchSheetWindow(Dialog dialog) {
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) {
            return;
        }
        boolean bottomSheet =
                (window.getAttributes().gravity & Gravity.BOTTOM) == Gravity.BOTTOM;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                bottomSheet
                        ? ViewGroup.LayoutParams.WRAP_CONTENT
                        : ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void showMultiChoiceDialog(String title, String[] labels, String[] values,
            boolean[] initiallySelected,
            String positiveLabel,
            GboardPatchesSettingsContract.StringListConsumer valueConsumer) {
        if (labels == null || values == null || initiallySelected == null
                || labels.length != values.length
                || labels.length != initiallySelected.length
                || valueConsumer == null) {
            return;
        }
        boolean[] selected = initiallySelected.clone();
        final Runnable[] pendingAction = new Runnable[1];
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(labels, selected,
                        (ignored, which, checked) -> {
                            if (which >= 0 && which < selected.length) {
                                selected[which] = checked;
                            }
                        })
                .setPositiveButton(positiveLabel, null)
                .setNegativeButton(text(R.string.gboard_patches_dialog_cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            tintDialogButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                List<String> chosen = new ArrayList<String>();
                for (int index = 0; index < values.length; index++) {
                    if (selected[index]) {
                        chosen.add(values[index]);
                    }
                }
                if (chosen.isEmpty()) {
                    return;
                }
                pendingAction[0] = () -> runSafely(
                        "handle multi-choice dialog selection",
                        () -> valueConsumer.accept(chosen));
                dialog.dismiss();
            });
        });
        dialog.setOnDismissListener(ignored -> {
            Runnable action = pendingAction[0];
            pendingAction[0] = null;
            onManagedDialogDismissed();
            if (action != null) {
                postToDecorView(action);
            }
        });
        try {
            dialog.show();
            onManagedDialogShown();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show multi-choice dialog", throwable);
        }
    }

    @Override
    public void showPositiveIntegerDialog(String title, String hint, int initialValue,
            GboardPatchesSettingsContract.PositiveIntegerConsumer consumer) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(hint);
        input.setText(Integer.toString(initialValue));
        input.setSelectAllOnFocus(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(text(R.string.gboard_patches_dialog_save), null)
                .setNegativeButton(
                        text(R.string.gboard_patches_dialog_cancel),
                        null)
                .create();
        dialog.setOnShowListener(ignored -> {
            tintDialogButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                int value = parsePositiveInteger(input.getText().toString());
                if (value <= 0) {
                    input.setError(text(R.string.gboard_patches_dialog_error_positive));
                    return;
                }
                try {
                    consumer.accept(value);
                    dialog.dismiss();
                    renderCurrentScreenSafely();
                } catch (IllegalArgumentException exception) {
                    input.setError(exception.getMessage());
                } catch (Throwable throwable) {
                    Log.w(TAG, "Failed to persist positive integer setting", throwable);
                    input.setError(text(R.string.gboard_patches_dialog_error_save_failed));
                }
            });
        });
        dialog.setOnDismissListener(ignored -> onManagedDialogDismissed());
        try {
            dialog.show();
            onManagedDialogShown();
            input.requestFocus();
            input.setSelection(input.getText().length());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show positive integer dialog", throwable);
        }
    }

    @Override
    public void showTextInputDialog(String title, String hint, String initialValue,
            GboardPatchesSettingsContract.TextValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(hint);
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelectAllOnFocus(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(text(R.string.gboard_patches_dialog_save), null)
                .setNegativeButton(
                        text(R.string.gboard_patches_dialog_cancel),
                        null)
                .create();
        dialog.setOnShowListener(ignored -> {
            tintDialogButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    consumer.accept(input.getText() == null ? "" : input.getText().toString());
                    dialog.dismiss();
                    renderCurrentScreenSafely();
                } catch (IllegalArgumentException exception) {
                    input.setError(exception.getMessage());
                } catch (Throwable throwable) {
                    Log.w(TAG, "Failed to persist text setting", throwable);
                    input.setError(text(R.string.gboard_patches_dialog_error_save_failed));
                }
            });
        });
        dialog.setOnDismissListener(ignored -> onManagedDialogDismissed());
        try {
            dialog.show();
            onManagedDialogShown();
            input.requestFocus();
            input.setSelection(input.getText().length());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show text input dialog", throwable);
        }
    }

    @Override
    public void showPreviewDialog(GboardPatchesSettingsContract.PreviewSpec previewSpec) {
        showPreviewSheet(null, previewSpec);
    }

    private void showPreviewSheet(GboardPatchesSettingsContract.Row rowModel,
            GboardPatchesSettingsContract.PreviewSpec previewSpec) {
        if (previewSpec == null) {
            return;
        }
        Dialog dialog = buildBottomSheetDialog();
        LinearLayout sheet = buildBottomSheetContainer();
        sheet.addView(buildBottomSheetHandle(dp(8)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(24), 0, dp(8), dp(4));
        TextView titleView = new TextView(this);
        titleView.setText(previewSpec.getTitle());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        titleView.setTypeface(mediumTypeface());
        titleView.setTextColor(palette.textPrimary);
        head.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton closeButton = buildIconButton(GboardPatchesIcons.CLOSE,
                palette.textSecondary, text(R.string.gboard_patches_preview_viewer_close));
        closeButton.setOnClickListener(view -> dialog.dismiss());
        head.addView(closeButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        sheet.addView(head);

        String message = previewSpec.getMessage();
        if (!TextUtils.isEmpty(message)) {
            TextView messageView = new TextView(this);
            messageView.setText(message);
            messageView.setTextColor(palette.textSecondary);
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            messageView.setLineSpacing(0f, 1.15f);
            messageView.setPadding(dp(24), 0, dp(24), dp(8));
            sheet.addView(messageView);
        }

        LinearLayout cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        cardsContainer.setPadding(dp(20), dp(4), dp(20), dp(4));
        List<GboardPatchesSettingsContract.PreviewMedia> previewMedia =
                previewSpec.getMediaItems();
        List<VideoView> previewVideoViews = new ArrayList<VideoView>();
        boolean sideBySide = previewSpec.getLayout()
                == GboardPatchesSettingsContract.PreviewLayout.SIDE_BY_SIDE
                && previewMedia.size() > 1;
        if (!sideBySide) {
            for (int index = 0; index < previewMedia.size(); index++) {
                int mediaIndex = index;
                View card = buildPreviewMediaCard(previewSpec.getTitle(), previewMedia.get(index),
                        false, previewVideoViews,
                        () -> showFullScreenViewer(previewSpec, mediaIndex, previewVideoViews));
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.topMargin = index == 0 ? 0 : dp(12);
                cardsContainer.addView(card, cardParams);
            }
        } else {
            for (int index = 0; index < previewMedia.size(); index += 2) {
                LinearLayout previewRow = new LinearLayout(this);
                previewRow.setOrientation(LinearLayout.HORIZONTAL);
                previewRow.setBaselineAligned(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = index == 0 ? 0 : dp(12);
                previewRow.setLayoutParams(rowParams);

                int leftIndex = index;
                View leftCard = buildPreviewMediaCard(previewSpec.getTitle(),
                        previewMedia.get(index), true, previewVideoViews,
                        () -> showFullScreenViewer(previewSpec, leftIndex, previewVideoViews));
                LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                leftParams.rightMargin = dp(6);
                previewRow.addView(leftCard, leftParams);

                if (index + 1 < previewMedia.size()) {
                    int rightIndex = index + 1;
                    View rightCard = buildPreviewMediaCard(previewSpec.getTitle(),
                            previewMedia.get(index + 1), true, previewVideoViews,
                            () -> showFullScreenViewer(
                                    previewSpec, rightIndex, previewVideoViews));
                    LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    rightParams.leftMargin = dp(6);
                    previewRow.addView(rightCard, rightParams);
                } else {
                    previewRow.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
                }
                cardsContainer.addView(previewRow);
            }
        }
        sheet.addView(buildCappedScrollView(cardsContainer, 0.65f));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(dp(20), dp(16), dp(20), dp(24));
        TextView actionButton = new TextView(this);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        actionButton.setTypeface(mediumTypeface());
        actionButton.setClickable(true);
        actionButton.setFocusable(true);
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setCornerRadius(dp(24));
        actionBackground.setColor(palette.masterSwitchContainer);
        actionButton.setText(text(R.string.gboard_patches_preview_done));
        actionButton.setTextColor(palette.onMasterSwitchContainer);
        actionButton.setOnClickListener(view -> dialog.dismiss());
        actionButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(palette.pressedOverlay), actionBackground, null));
        foot.addView(actionButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        sheet.addView(foot);

        dialog.setContentView(sheet, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setOnDismissListener(ignored -> {
            stopPreviewVideos(previewVideoViews);
            onManagedDialogDismissed();
        });
        try {
            dialog.show();
            onManagedDialogShown();
            matchSheetWindow(dialog);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show preview sheet", throwable);
        }
    }

    private void showFullScreenViewer(GboardPatchesSettingsContract.PreviewSpec previewSpec,
            int initialIndex, List<VideoView> parentVideoViews) {
        if (previewSpec == null || previewSpec.getMediaItems().isEmpty()) {
            return;
        }
        for (VideoView parentVideo : parentVideoViews) {
            try {
                parentVideo.pause();
            } catch (Throwable ignored) {
            }
        }
        List<GboardPatchesSettingsContract.PreviewMedia> items = previewSpec.getMediaItems();
        int[] currentIndex = new int[] { Math.max(0, Math.min(initialIndex, items.size() - 1)) };

        Dialog viewer = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window window = viewer.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }
        viewer.setOnShowListener(ignored -> matchSheetWindow(viewer));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(8), dp(16), dp(8));
        ImageButton closeButton = buildIconButton(GboardPatchesIcons.CLOSE, Color.WHITE,
                text(R.string.gboard_patches_preview_viewer_close));
        closeButton.setOnClickListener(view -> viewer.dismiss());
        topBar.addView(closeButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView titleView = new TextView(this);
        titleView.setText(previewSpec.getTitle());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTextColor(Color.WHITE);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(dp(8), 0, dp(8), 0);
        topBar.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView pageView = new TextView(this);
        pageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        pageView.setTextColor(Color.argb(178, 255, 255, 255));
        topBar.addView(pageView);
        root.addView(topBar);

        FrameLayout stage = new FrameLayout(this);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        stageParams.setMargins(dp(12), dp(12), dp(12), dp(12));
        root.addView(stage, stageParams);

        TextView captionView = new TextView(this);
        captionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        captionView.setTextColor(Color.WHITE);
        captionView.setGravity(Gravity.CENTER);
        captionView.setPadding(dp(20), dp(8), dp(20), dp(8));
        root.addView(captionView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(0, dp(8), 0, dp(32));
        ImageButton previousButton = buildIconButton(GboardPatchesIcons.CHEVRON_LEFT,
                Color.WHITE, text(R.string.gboard_patches_preview_viewer_previous));
        ImageButton nextButton = buildIconButton(GboardPatchesIcons.CHEVRON_RIGHT,
                Color.WHITE, text(R.string.gboard_patches_preview_viewer_next));
        LinearLayout.LayoutParams previousParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        previousParams.setMargins(dp(20), 0, dp(20), 0);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        nextParams.setMargins(dp(20), 0, dp(20), 0);
        navBar.addView(previousButton, previousParams);
        navBar.addView(nextButton, nextParams);
        root.addView(navBar);

        List<VideoView> viewerVideos = new ArrayList<VideoView>();
        Runnable updateStage = () -> {
            stopPreviewVideos(viewerVideos);
            viewerVideos.clear();
            stage.removeAllViews();
            int index = currentIndex[0];
            int total = items.size();
            pageView.setText(total > 1 ? (index + 1) + " / " + total : "");
            pageView.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
            navBar.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
            previousButton.setEnabled(index > 0);
            previousButton.setAlpha(index > 0 ? 1f : 0.3f);
            nextButton.setEnabled(index < total - 1);
            nextButton.setAlpha(index < total - 1 ? 1f : 0.3f);
            GboardPatchesSettingsContract.PreviewMedia media = items.get(index);
            captionView.setText(media.getCaption() != null ? media.getCaption() : "");
            View content = null;
            if (media instanceof GboardPatchesSettingsContract.PreviewImage image) {
                Bitmap bitmap = decodePreviewBitmap(image.getAssetPath());
                if (bitmap != null) {
                    ImageView imageView = new ImageView(this);
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setImageBitmap(bitmap);
                    content = imageView;
                }
            } else if (media instanceof GboardPatchesSettingsContract.PreviewVideo video) {
                content = buildPreviewVideoView(previewSpec.getTitle(), video, false,
                        viewerVideos);
            }
            if (content != null) {
                stage.addView(content, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER));
            }
        };
        previousButton.setOnClickListener(view -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                runSafely("show previous preview", updateStage);
            }
        });
        nextButton.setOnClickListener(view -> {
            if (currentIndex[0] < items.size() - 1) {
                currentIndex[0]++;
                runSafely("show next preview", updateStage);
            }
        });
        runSafely("show preview viewer page", updateStage);

        viewer.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        viewer.setOnDismissListener(ignored -> {
            stopPreviewVideos(viewerVideos);
            for (VideoView parentVideo : parentVideoViews) {
                try {
                    parentVideo.start();
                } catch (Throwable ignoredResume) {
                }
            }
            onManagedDialogDismissed();
        });
        try {
            viewer.show();
            onManagedDialogShown();
            matchSheetWindow(viewer);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show preview viewer", throwable);
        }
    }

    private void stopPreviewVideos(List<VideoView> videoViews) {
        for (VideoView videoView : videoViews) {
            try {
                videoView.stopPlayback();
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to stop preview video playback", throwable);
            }
        }
    }

    private ImageButton buildIconButton(int icon, int color, String contentDescription) {
        ImageButton button = new ImageButton(this);
        button.setImageDrawable(createIcon(icon, color));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(buildRippleDrawable(dp(999)));
        button.setContentDescription(contentDescription);
        return button;
    }

    @Override
    public void showManagedDialog(GboardPatchesSettingsContract.ManagedDialogAction action) {
        if (action == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> showManagedDialog(action));
            return;
        }
        Throwable throwable = GboardManagedDialogRunner.run(
                action,
                this::onManagedDialogShown,
                this::onManagedDialogDismissed);
        if (throwable != null) {
            Log.w(TAG, "Failed to show managed settings dialog", throwable);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void createTextDocument(String fileName, String mimeType, String text,
            Runnable completionAction) {
        pendingTextDocumentWrite = new PendingTextDocumentWrite(
                text != null ? text : "",
                completionAction);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setType(normalizeMimeType(mimeType));
        if (fileName != null && !fileName.trim().isEmpty()) {
            intent.putExtra(Intent.EXTRA_TITLE,
                    GboardExportFileNames.timestamped(fileName.trim()));
        }
        try {
            startActivityForResult(intent, REQUEST_CREATE_TEXT_DOCUMENT);
        } catch (ActivityNotFoundException ignored) {
            pendingTextDocumentWrite = null;
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            pendingTextDocumentWrite = null;
            Log.w(TAG, "Failed to launch document create picker", throwable);
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void openTextDocument(String[] mimeTypes,
            GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        if (valueConsumer == null) {
            return;
        }
        pendingTextDocumentReader = valueConsumer;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        String[] normalizedMimeTypes = normalizeMimeTypes(mimeTypes);
        intent.setType(normalizedMimeTypes.length == 1
                ? normalizedMimeTypes[0]
                : "*/*");
        if (normalizedMimeTypes.length > 1) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, normalizedMimeTypes);
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_TEXT_DOCUMENT);
        } catch (ActivityNotFoundException ignored) {
            pendingTextDocumentReader = null;
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            pendingTextDocumentReader = null;
            Log.w(TAG, "Failed to launch document open picker", throwable);
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void createBinaryDocument(String fileName, String mimeType, byte[] data,
            Runnable completionAction) {
        pendingBinaryDocumentWrite = new PendingBinaryDocumentWrite(
                data == null ? new byte[0] : data.clone(), completionAction);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .setType(normalizeMimeType(mimeType));
        if (fileName != null && !fileName.isBlank()) {
            intent.putExtra(Intent.EXTRA_TITLE,
                    GboardExportFileNames.timestamped(fileName.trim()));
        }
        try {
            startActivityForResult(intent, REQUEST_CREATE_BINARY_DOCUMENT);
        } catch (Throwable throwable) {
            pendingBinaryDocumentWrite = null;
            Log.w(TAG, "Failed to launch binary document create picker", throwable);
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void openBinaryDocument(String[] mimeTypes,
            GboardPatchesSettingsContract.BinaryDocumentConsumer documentConsumer) {
        if (documentConsumer == null) {
            return;
        }
        pendingBinaryDocumentReader = documentConsumer;
        String[] normalizedMimeTypes = normalizeMimeTypes(mimeTypes);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                .setType(normalizedMimeTypes.length == 1 ? normalizedMimeTypes[0] : "*/*");
        if (normalizedMimeTypes.length > 1) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, normalizedMimeTypes);
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_BINARY_DOCUMENT);
        } catch (Throwable throwable) {
            pendingBinaryDocumentReader = null;
            Log.w(TAG, "Failed to launch binary document open picker", throwable);
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void requestTargetRestart() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(this::requestTargetRestart);
            return;
        }
        try { requestGboardRestart(); }
        catch (Throwable unavailable) { handleRestartFailure("Failed to request target restart", unavailable); }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void openDocumentTree(String initialTreeUri,
            GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        if (valueConsumer == null) {
            return;
        }
        pendingDocumentTreeConsumer = valueConsumer;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && initialTreeUri != null && initialTreeUri.startsWith("content://")) {
            try {
                intent.putExtra("android.provider.extra.INITIAL_URI", Uri.parse(initialTreeUri));
            } catch (Throwable ignored) {
                // The picker can still open without an initial location.
            }
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE);
        } catch (ActivityNotFoundException ignored) {
            pendingDocumentTreeConsumer = null;
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            pendingDocumentTreeConsumer = null;
            Log.w(TAG, "Failed to launch document tree picker", throwable);
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void requestRuntimePermission(String permission,
            GboardPatchesSettingsContract.BooleanValueConsumer resultConsumer) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> requestRuntimePermission(permission, resultConsumer));
            return;
        }
        if (permission == null || permission.isBlank() || resultConsumer == null) {
            return;
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            resultConsumer.accept(true);
            return;
        }
        if (pendingPermissionConsumer != null) {
            resultConsumer.accept(false);
            return;
        }
        pendingPermissionConsumer = resultConsumer;
        try {
            requestPermissions(new String[]{permission}, REQUEST_RUNTIME_PERMISSION);
        } catch (Throwable throwable) {
            pendingPermissionConsumer = null;
            Log.w(TAG, "Failed to request runtime permission " + permission, throwable);
            resultConsumer.accept(false);
        }
    }

    @Override
    public void openAllFilesAccessSettings(String unavailableMessage) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> openAllFilesAccessSettings(unavailableMessage));
            return;
        }
        Intent appIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to launch all files access settings", throwable);
                Toast.makeText(this, unavailableMessage == null ? "" : unavailableMessage,
                        Toast.LENGTH_LONG).show();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to launch app all files access settings", throwable);
            Toast.makeText(this, unavailableMessage == null ? "" : unavailableMessage,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void openBatteryOptimizationSettings(String unavailableMessage) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> openBatteryOptimizationSettings(unavailableMessage));
            return;
        }
        Intent appIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException ignored) {
            openBatteryOptimizationSettingsFallback(unavailableMessage);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to launch app battery optimization settings", throwable);
            openBatteryOptimizationSettingsFallback(unavailableMessage);
        }
    }

    private void openBatteryOptimizationSettingsFallback(String unavailableMessage) {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to launch battery optimization settings", throwable);
            Toast.makeText(this, unavailableMessage == null ? "" : unavailableMessage,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void showMessage(String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> showMessage(message));
            return;
        }
        Toast.makeText(this, message == null ? "" : message, Toast.LENGTH_LONG).show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CREATE_TEXT_DOCUMENT) {
            handleCreateTextDocumentResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_OPEN_TEXT_DOCUMENT) {
            handleOpenTextDocumentResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_OPEN_DOCUMENT_TREE) {
            handleOpenDocumentTreeResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_CREATE_BINARY_DOCUMENT) {
            handleCreateBinaryDocumentResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_OPEN_BINARY_DOCUMENT) {
            handleOpenBinaryDocumentResult(resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RUNTIME_PERMISSION) {
            return;
        }
        GboardPatchesSettingsContract.BooleanValueConsumer consumer = pendingPermissionConsumer;
        pendingPermissionConsumer = null;
        if (consumer != null) {
            consumer.accept(grantResults != null
                    && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    private View buildPreviewMediaCard(String dialogTitle,
            GboardPatchesSettingsContract.PreviewMedia previewMedia, boolean compact,
            List<VideoView> previewVideoViews, Runnable onCardClick) {
        int cardRadius = dp(compact ? 20 : 24);
        int mediaRadius = dp(compact ? 12 : 16);
        int inset = dp(compact ? 8 : 12);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(inset, inset, inset, dp(compact ? 10 : 12));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(palette.previewCard);
        cardBackground.setCornerRadius(cardRadius);
        card.setBackground(new RippleDrawable(
                ColorStateList.valueOf(palette.pressedOverlay), cardBackground, null));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(text(R.string.gboard_patches_preview_open_fullscreen));
        card.setOnClickListener(view -> runSafely("open preview viewer", onCardClick));

        View previewContent = null;
        if (previewMedia instanceof GboardPatchesSettingsContract.PreviewImage previewImage) {
            previewContent = buildPreviewImageView(dialogTitle, previewImage, compact);
        } else if (previewMedia instanceof GboardPatchesSettingsContract.PreviewVideo previewVideo) {
            previewContent = buildPreviewVideoView(
                    dialogTitle,
                    previewVideo,
                    compact,
                    previewVideoViews);
        }

        FrameLayout frame = new FrameLayout(this);
        GradientDrawable frameBackground = new GradientDrawable();
        frameBackground.setColor(palette.mediaBackground);
        frameBackground.setCornerRadius(mediaRadius);
        frame.setBackground(frameBackground);
        GradientDrawable frameOutline = new GradientDrawable();
        frameOutline.setColor(Color.TRANSPARENT);
        frameOutline.setCornerRadius(mediaRadius);
        frameOutline.setStroke(Math.max(1, dp(1)), palette.previewMediaStroke);
        frame.setForeground(frameOutline);
        frame.setClipToOutline(true);

        boolean isImage = previewContent instanceof ImageView;
        if (previewContent != null) {
            ViewGroup.LayoutParams previewLayoutParams = previewContent.getLayoutParams();
            frame.addView(previewContent, new FrameLayout.LayoutParams(
                    previewLayoutParams != null || !isImage
                            ? ViewGroup.LayoutParams.MATCH_PARENT
                            : ViewGroup.LayoutParams.WRAP_CONTENT,
                    previewLayoutParams != null
                            ? previewLayoutParams.height
                            : ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        } else {
            TextView errorView = new TextView(this);
            errorView.setText(text(R.string.gboard_patches_preview_load_failed));
            errorView.setTextColor(Color.argb(200, 255, 255, 255));
            errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            errorView.setGravity(Gravity.CENTER);
            frame.addView(errorView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(compact ? 96 : 140),
                    Gravity.CENTER));
        }
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                isImage ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        frameParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(frame, frameParams);

        String caption = previewMedia.getCaption();
        if (caption != null && !caption.isEmpty()) {
            TextView captionView = new TextView(this);
            captionView.setText(caption);
            captionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 13f : 14f);
            captionView.setTypeface(mediumTypeface());
            captionView.setTextColor(palette.textPrimary);
            captionView.setGravity(Gravity.CENTER_HORIZONTAL);
            captionView.setLineSpacing(0f, 1.15f);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            captionParams.topMargin = dp(compact ? 8 : 10);
            card.addView(captionView, captionParams);
        }
        return card;
    }

    private View buildPreviewImageView(String dialogTitle,
            GboardPatchesSettingsContract.PreviewImage previewImage, boolean compact) {
        Bitmap bitmap = decodePreviewBitmap(previewImage.getAssetPath());
        if (bitmap == null) {
            return null;
        }
        ImageView imageView = new AspectFitImageView(this, compact ? dp(260) : dp(400));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(bitmap);
        String caption = previewImage.getCaption();
        imageView.setContentDescription(
                caption != null && !caption.isEmpty() ? caption : dialogTitle);
        return imageView;
    }

    private View buildPreviewVideoView(String dialogTitle,
            GboardPatchesSettingsContract.PreviewVideo previewVideo, boolean compact,
            List<VideoView> previewVideoViews) {
        Uri videoUri = resolvePreviewVideoUri(previewVideo);
        if (videoUri == null) {
            return null;
        }
        FrameLayout videoContainer = new FrameLayout(this);
        videoContainer.setForegroundGravity(Gravity.CENTER);
        videoContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                compact ? dp(160) : dp(240)));

        VideoView videoView = new VideoView(this);
        videoView.setVideoURI(videoUri);
        videoView.setContentDescription(dialogTitle);
        videoView.setOnPreparedListener(mediaPlayer ->
                preparePreviewVideo(mediaPlayer, videoView, videoContainer, compact));
        videoView.setOnErrorListener((mediaPlayer, what, extra) -> {
            Log.w(TAG, "Failed to prepare preview video asset: " + previewVideo.getAssetPath()
                    + ", what=" + what + ", extra=" + extra);
            videoContainer.post(() -> showPreviewVideoLoadError(videoContainer));
            return true;
        });
        videoContainer.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        previewVideoViews.add(videoView);
        return videoContainer;
    }

    private void preparePreviewVideo(MediaPlayer mediaPlayer, VideoView videoView,
            FrameLayout videoContainer, boolean compact) {
        try {
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setLooping(true);
            updatePreviewVideoContainerHeight(
                    videoContainer,
                    mediaPlayer.getVideoWidth(),
                    mediaPlayer.getVideoHeight(),
                    compact);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to configure preview video playback", throwable);
        }
        try {
            videoView.start();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to start preview video playback", throwable);
        }
    }

    private void updatePreviewVideoContainerHeight(FrameLayout videoContainer, int videoWidth,
            int videoHeight, boolean compact) {
        if (videoContainer == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        int maxHeight = compact ? dp(220) : dp(420);
        int minHeight = compact ? dp(96) : dp(140);
        videoContainer.post(() -> {
            int containerWidth = videoContainer.getWidth();
            if (containerWidth <= 0 && videoContainer.getParent() instanceof View parent) {
                containerWidth = parent.getWidth();
            }
            if (containerWidth <= 0) {
                int displayWidth = videoContainer.getResources().getDisplayMetrics().widthPixels;
                containerWidth = compact
                        ? (displayWidth - dp(50)) / 2
                        : displayWidth - dp(40);
            }
            int targetHeight = Math.round(
                    (float) containerWidth * ((float) videoHeight / (float) videoWidth));
            targetHeight = Math.max(minHeight, Math.min(maxHeight, targetHeight));
            ViewGroup.LayoutParams layoutParams = videoContainer.getLayoutParams();
            if (layoutParams == null || layoutParams.height == targetHeight
                    || layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                return;
            }
            layoutParams.height = targetHeight;
            videoContainer.setLayoutParams(layoutParams);
            if (videoContainer.getParent() instanceof View parent) {
                parent.requestLayout();
            }
        });
    }

    private Bitmap decodePreviewBitmap(String assetPath) {
        try (InputStream inputStream = getAssets().open(assetPath)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to load preview asset: " + assetPath, throwable);
            return null;
        }
    }

    private Uri resolvePreviewVideoUri(GboardPatchesSettingsContract.PreviewVideo previewVideo) {
        if (previewVideo == null) {
            return null;
        }
        return resolvePreviewVideoAssetUri(previewVideo.getAssetPath());
    }

    private Uri resolvePreviewVideoAssetUri(String assetPath) {
        if (assetPath == null || assetPath.isEmpty()) {
            return null;
        }
        File cacheFile = materializePreviewVideoAsset(assetPath);
        if (cacheFile == null) {
            return null;
        }
        return Uri.fromFile(cacheFile);
    }

    private File materializePreviewVideoAsset(String assetPath) {
        try (InputStream inputStream = getAssets().open(assetPath)) {
            File cacheDirectory = new File(getCacheDir(), "preview-videos");
            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
                Log.w(TAG, "Failed to create preview video cache directory: "
                        + cacheDirectory.getAbsolutePath());
                return null;
            }
            File outputFile = new File(cacheDirectory, sanitizePreviewAssetFileName(assetPath));
            try (FileOutputStream outputStream = new FileOutputStream(outputFile, false)) {
                byte[] buffer = new byte[8192];
                while (true) {
                    int read = inputStream.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
            return outputFile;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to materialize preview video asset: " + assetPath, throwable);
            return null;
        }
    }

    private static String sanitizePreviewAssetFileName(String assetPath) {
        String normalized = assetPath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        if (fileName.isEmpty()) {
            return "preview-video.mp4";
        }
        return fileName;
    }

    private void showPreviewVideoLoadError(FrameLayout videoContainer) {
        if (videoContainer == null) {
            return;
        }
        videoContainer.removeAllViews();

        TextView errorView = new TextView(this);
        errorView.setText(text(R.string.gboard_patches_preview_load_failed));
        errorView.setTextColor(Color.argb(200, 255, 255, 255));
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        errorView.setGravity(Gravity.CENTER);

        videoContainer.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
    }

    private void handleCreateTextDocumentResult(int resultCode, Intent data) {
        PendingTextDocumentWrite pendingWrite = pendingTextDocumentWrite;
        pendingTextDocumentWrite = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || pendingWrite == null) {
            return;
        }
        try {
            writeTextDocument(data.getData(), pendingWrite.text);
            if (pendingWrite.completionAction != null) {
                pendingWrite.completionAction.run();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to write selected document", throwable);
            Toast.makeText(this, DOCUMENT_WRITE_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleOpenTextDocumentResult(int resultCode, Intent data) {
        GboardPatchesSettingsContract.StringValueConsumer reader = pendingTextDocumentReader;
        pendingTextDocumentReader = null;
        Uri uri = selectedDocumentUri(data);
        if (resultCode != RESULT_OK || uri == null || reader == null) {
            return;
        }
        try {
            reader.accept(readTextDocument(uri));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to read selected document", throwable);
            Toast.makeText(this, DOCUMENT_READ_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleCreateBinaryDocumentResult(int resultCode, Intent data) {
        PendingBinaryDocumentWrite pendingWrite = pendingBinaryDocumentWrite;
        pendingBinaryDocumentWrite = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || pendingWrite == null) {
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) {
                throw new java.io.IOException("Content resolver returned null output stream");
            }
            output.write(pendingWrite.data);
            output.flush();
            if (pendingWrite.completionAction != null) {
                pendingWrite.completionAction.run();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to write selected binary document", throwable);
            Toast.makeText(this, DOCUMENT_WRITE_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleOpenBinaryDocumentResult(int resultCode, Intent data) {
        GboardPatchesSettingsContract.BinaryDocumentConsumer reader =
                pendingBinaryDocumentReader;
        pendingBinaryDocumentReader = null;
        Uri uri = selectedDocumentUri(data);
        if (resultCode != RESULT_OK || uri == null || reader == null) {
            return;
        }
        try {
            reader.accept(new GboardPatchesSettingsContract.BinaryDocument(
                    queryDisplayName(uri), getContentResolver().getType(uri),
                    readBinaryDocument(uri)));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to read selected binary document", throwable);
            Toast.makeText(this, DOCUMENT_READ_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    private static Uri selectedDocumentUri(Intent data) {
        if (data == null) {
            return null;
        }
        Uri direct = data.getData();
        if (direct != null) {
            return direct;
        }
        ClipData clipData = data.getClipData();
        if (clipData == null || clipData.getItemCount() == 0) {
            return null;
        }
        ClipData.Item first = clipData.getItemAt(0);
        return first == null ? null : first.getUri();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the URI path below.
        }
        return uri.getLastPathSegment();
    }

    private byte[] readBinaryDocument(Uri uri) throws java.io.IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new java.io.IOException("Content resolver returned null input stream");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void handleOpenDocumentTreeResult(int resultCode, Intent data) {
        GboardPatchesSettingsContract.StringValueConsumer consumer = pendingDocumentTreeConsumer;
        pendingDocumentTreeConsumer = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || consumer == null) {
            return;
        }
        Uri treeUri = data.getData();
        int grantFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((grantFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(treeUri, grantFlags);
            consumer.accept(treeUri.toString());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to persist selected document tree", throwable);
            Toast.makeText(this, DOCUMENT_PICKER_FAILED, Toast.LENGTH_SHORT).show();
        }
    }

    private void writeTextDocument(Uri uri, String text) throws java.io.IOException {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt")) {
            if (outputStream == null) {
                throw new java.io.IOException("Content resolver returned null output stream.");
            }
            outputStream.write((text != null ? text : "").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    private String readTextDocument(Uri uri) throws java.io.IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new java.io.IOException("Content resolver returned null input stream.");
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String normalizeMimeType(String mimeType) {
        return mimeType != null && !mimeType.trim().isEmpty()
                ? mimeType.trim()
                : DOCUMENT_TYPE_FALLBACK;
    }

    private static String[] normalizeMimeTypes(String[] mimeTypes) {
        if (mimeTypes == null || mimeTypes.length == 0) {
            return new String[] { DOCUMENT_TYPE_FALLBACK };
        }
        List<String> normalized = new ArrayList<String>();
        for (String mimeType : mimeTypes) {
            if (mimeType != null && !mimeType.trim().isEmpty()) {
                normalized.add(mimeType.trim());
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(DOCUMENT_TYPE_FALLBACK);
        }
        return normalized.toArray(new String[0]);
    }

    private void configureWindow() {
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(palette.windowBackground);
            window.setNavigationBarColor(palette.windowBackground);
        }
        configureSystemBarsAppearance(window);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            configureSystemBarsAppearance(getWindow());
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to configure patches settings system bars", throwable);
        }
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBarsAppearance(Window window) {
        if (window == null || palette == null) {
            return;
        }
        boolean lightBars = !palette.nightMode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars ? mask : 0, mask);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                lightFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decorView.setSystemUiVisibility(lightBars ? flags | lightFlags : flags & ~lightFlags);
        }
    }

    private int resolveActivityTheme() {
        boolean nightMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return nightMode
                ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar;
    }

    private View buildContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(palette.windowBackground);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(shell);

        shell.addView(buildToolbar());

        contentScrollView = new ScrollView(this);
        contentScrollView.setClipToPadding(false);
        contentScrollView.setFillViewport(true);
        contentScrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        contentScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        contentColumn = new LinearLayout(this);
        contentColumn.setOrientation(LinearLayout.VERTICAL);
        contentColumn.setPadding(dp(16), 0, dp(16), dp(16));
        contentColumn.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        headerCardView = buildLargeTitleBlock();
        contentColumn.addView(headerCardView);
        contentColumn.addView(buildPanelCard());

        contentScrollView.addView(contentColumn);
        contentScrollView.setOnScrollChangeListener(
                (view, scrollX, scrollY, oldScrollX, oldScrollY) ->
                        updateToolbarChrome(scrollY > dp(TOOLBAR_SCROLLED_THRESHOLD_DP)));
        shell.addView(contentScrollView);

        screenActionContainer = new LinearLayout(this);
        screenActionContainer.setOrientation(LinearLayout.VERTICAL);
        screenActionContainer.setPadding(dp(16), dp(8), dp(16), dp(16));
        screenActionContainer.setVisibility(View.GONE);
        shell.addView(screenActionContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        keyboardPreviewController = new GboardKeyboardPreviewController(
                this,
                this::targetSettingsPackages,
                this::onManagedDialogShown,
                this::onKeyboardPreviewDismissed,
                palette.accentContainer,
                palette.onAccentContainer);
        keyboardPreviewController.attachTo(root);
        return root;
    }

    private View buildToolbar() {
        toolbarView = new LinearLayout(this);
        toolbarView.setOrientation(LinearLayout.HORIZONTAL);
        toolbarView.setGravity(Gravity.CENTER_VERTICAL);
        toolbarView.setBackgroundColor(palette.windowBackground);
        toolbarView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(TOOLBAR_HEIGHT_DP)));
        toolbarView.setPadding(dp(4), 0, dp(12), 0);

        View backButton = buildBackButton();

        toolbarTitleView = new TextView(this);
        toolbarTitleView.setTextColor(palette.textPrimary);
        toolbarTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        toolbarTitleView.setTypeface(Typeface.DEFAULT);
        toolbarTitleView.setSingleLine(true);
        toolbarTitleView.setEllipsize(TextUtils.TruncateAt.END);
        toolbarTitleView.setAlpha(0f);
        toolbarTitleView.setTranslationY(dp(6));
        toolbarTitleView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        toolbarView.addView(backButton);
        toolbarView.addView(toolbarTitleView);
        toolbarActionsView = new LinearLayout(this);
        toolbarActionsView.setOrientation(LinearLayout.HORIZONTAL);
        toolbarActionsView.setGravity(Gravity.CENTER_VERTICAL);
        toolbarView.addView(toolbarActionsView);
        toolbarView.addView(buildRestartButton());
        return toolbarView;
    }

    private View buildRestartButton() {
        String restartLabel = text(R.string.gboard_patches_restart_action);
        FrameLayout restartButtonContainer = new FrameLayout(this);
        restartButtonContainer.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        restartButton = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.8f);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float density = getResources().getDisplayMetrics().density;
                float iconSize = density * 24f;
                float left = (getWidth() - iconSize) / 2f;
                float top = (getHeight() - iconSize) / 2f;
                float scale = iconSize / 24f;

                paint.setColor(restartPendingCount > 0 ? palette.restartPending : palette.accent);
                path.reset();
                path.moveTo(left + (4.48f * scale), top + (14.5f * scale));
                path.cubicTo(
                        left + (5.6f * scale), top + (17.7f * scale),
                        left + (8.65f * scale), top + (20f * scale),
                        left + (12.25f * scale), top + (20f * scale));
                path.cubicTo(
                        left + (16.8f * scale), top + (20f * scale),
                        left + (20.5f * scale), top + (16.3f * scale),
                        left + (20.5f * scale), top + (11.75f * scale));
                path.cubicTo(
                        left + (20.5f * scale), top + (7.2f * scale),
                        left + (16.8f * scale), top + (3.5f * scale),
                        left + (12.25f * scale), top + (3.5f * scale));
                path.cubicTo(
                        left + (8.55f * scale), top + (3.5f * scale),
                        left + (5.55f * scale), top + (5.95f * scale),
                        left + (4.5f * scale), top + (9.5f * scale));
                canvas.drawPath(path, paint);

                path.reset();
                path.moveTo(left + (4.5f * scale), top + (4.5f * scale));
                path.lineTo(left + (4.5f * scale), top + (9.5f * scale));
                path.lineTo(left + (9.5f * scale), top + (9.5f * scale));
                canvas.drawPath(path, paint);
            }
        };
        restartButton.setContentDescription(restartLabel);
        restartButton.setBackground(buildRippleDrawable(dp(24)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            restartButton.setTooltipText(restartLabel);
        } else {
            restartButton.setOnLongClickListener(view -> {
                try {
                    Toast.makeText(this, String.valueOf(view.getContentDescription()),
                            Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
                return true;
            });
        }
        restartButton.setOnClickListener(view -> requestGboardRestart());
        restartButtonContainer.addView(restartButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        restartBadgeView = buildRestartBadge();
        restartButtonContainer.addView(restartBadgeView);
        applyRestartPendingCount(GboardRestartPendingChanges.pendingCount(), false);
        return restartButtonContainer;
    }

    private TextView buildRestartBadge() {
        TextView badge = new TextView(this);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        badge.setTextColor(palette.onRestartPending);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setMinWidth(dp(18));
        badge.setMinHeight(dp(18));
        badge.setPadding(dp(5), 0, dp(5), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.restartPending);
        background.setCornerRadius(dp(9));
        background.setStroke(dp(2), palette.windowBackground);
        badge.setBackground(background);
        badge.setClickable(false);
        badge.setFocusable(false);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(18), Gravity.TOP | Gravity.END);
        params.topMargin = dp(5);
        params.rightMargin = dp(3);
        badge.setLayoutParams(params);
        return badge;
    }

    private void applyRestartPendingCount(int pendingCount, boolean animate) {
        try {
            restartPendingCount = Math.max(0, pendingCount);
            if (restartButton == null || restartBadgeView == null) {
                return;
            }
            boolean hasPending = restartPendingCount > 0;
            restartButton.invalidate();
            String label = hasPending
                    ? GboardSettingsText.quantity(this,
                            R.plurals.gboard_patches_restart_action_pending,
                            restartPendingCount)
                    : text(R.string.gboard_patches_restart_action);
            restartButton.setContentDescription(label);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                restartButton.setTooltipText(label);
            }
            boolean wasVisible = restartBadgeView.getVisibility() == View.VISIBLE;
            if (!hasPending) {
                restartBadgeView.animate().cancel();
                restartBadgeView.setVisibility(View.GONE);
                return;
            }
            restartBadgeView.setText(restartPendingCount > 99
                    ? "99+"
                    : Integer.toString(restartPendingCount));
            restartBadgeView.setVisibility(View.VISIBLE);
            if (animate && !wasVisible) {
                restartBadgeView.setScaleX(0.4f);
                restartBadgeView.setScaleY(0.4f);
                restartBadgeView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(new OvershootInterpolator(2f))
                        .start();
            } else if (!animate) {
                restartBadgeView.setScaleX(1f);
                restartBadgeView.setScaleY(1f);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to update pending restart badge", throwable);
        }
    }

    private void scheduleRestartCrashRecoveryGuardRestore() {
        try {
            Context applicationContext = getApplicationContext();
            Context restoreContext = applicationContext != null ? applicationContext : this;
            boolean scheduled = screenRefreshHandler.postDelayed(
                    () -> restoreRestartCrashRecoveryGuard(restoreContext),
                    RESTART_CRASH_RECOVERY_RESTORE_DELAY_MS);
            if (!scheduled) {
                Log.w(TAG, "Failed to schedule Gboard crash recovery cleanup restoration");
            }
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to defer Gboard crash recovery cleanup restoration", throwable);
            } catch (Throwable ignored) {
                // The restart guard must never escape into the host app.
            }
        }
    }

    private void restoreRestartCrashRecoveryGuard(Context context) {
        try {
            if (!GboardRestartCrashRecoveryGuard.restoreIfPending(context)) {
                Log.w(TAG, "Failed to restore Gboard crash recovery cleanup setting");
            }
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to run Gboard crash recovery cleanup restoration", throwable);
            } catch (Throwable ignored) {
                // The restart guard must never escape into the host app.
            }
        }
    }

    private void requestGboardRestart() {
        try {
            GboardPatchesSettingsOrchestrator.State<GboardPatchesSettingsContract.Feature> state =
                    settingsOrchestrator.snapshot();
            ArrayList<String> navigationPath = GboardPatchesNavigationPath.capture(
                    state.getBackStack(), state.getCurrent());

            Context applicationContext = getApplicationContext();
            Context restartContext = applicationContext != null ? applicationContext : this;
            if (!GboardRestartCrashRecoveryGuard.prepare(restartContext)) {
                handleRestartFailure("Failed to prepare safe Gboard restart", null);
                return;
            }
            setRestartButtonPending(true);
            boolean scheduled = screenRefreshHandler.postDelayed(
                    () -> coldRestartGboard(restartContext, navigationPath),
                    RESTART_DELAY_MS);
            if (!scheduled) {
                handleRestartFailure("Failed to schedule Gboard restart", null);
            }
        } catch (Throwable throwable) {
            handleRestartFailure("Failed to request Gboard restart", throwable);
        }
    }

    private void coldRestartGboard(Context context, ArrayList<String> navigationPath) {
        try {
            ComponentName patchesComponent =
                    new ComponentName(context, GboardPatchesSettingsActivity.class);
            Intent restartIntent = Intent.makeRestartActivityTask(patchesComponent);
            restartIntent.putStringArrayListExtra(EXTRA_NAVIGATION_PATH, navigationPath);
            restartIntent.setPackage(context.getPackageName());
            context.startActivity(restartIntent);
            System.exit(0);
        } catch (Throwable throwable) {
            handleRestartFailure("Failed to cold restart Gboard", throwable);
        }
    }

    private void setRestartButtonPending(boolean pending) {
        try {
            if (!pending) {
                stopRestartButtonAnimation();
            }
            if (restartButton == null) {
                return;
            }
            restartButton.setEnabled(!pending);
            restartButton.setAlpha(1f);
            if (pending) {
                startRestartButtonAnimation();
            }
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to update Gboard restart action", throwable);
            } catch (Throwable ignored) {
                // A restart failure must never escape into the host app.
            }
        }
    }

    private void startRestartButtonAnimation() {
        stopRestartButtonAnimation();
        if (restartButton == null) {
            return;
        }
        restartButtonAnimator = ObjectAnimator.ofFloat(
                restartButton,
                View.ROTATION,
                0f,
                -360f);
        restartButtonAnimator.setDuration(RESTART_ICON_ROTATION_DURATION_MS);
        restartButtonAnimator.setInterpolator(new LinearInterpolator());
        restartButtonAnimator.setRepeatCount(ValueAnimator.INFINITE);
        restartButtonAnimator.start();
    }

    private void stopRestartButtonAnimation() {
        if (restartButtonAnimator != null) {
            restartButtonAnimator.cancel();
            restartButtonAnimator = null;
        }
        if (restartButton != null) {
            restartButton.setRotation(0f);
        }
    }

    private void handleRestartFailure(String message, Throwable throwable) {
        try {
            if (!GboardRestartCrashRecoveryGuard.restoreIfPending(this)) {
                Log.w(TAG, "Failed to roll back Gboard restart guard");
            }
        } catch (Throwable ignored) {
            // A restart failure must never escape into the host app.
        }
        try {
            Log.w(TAG, message, throwable);
        } catch (Throwable ignored) {
            // A restart failure must never escape into the host app.
        }
        setRestartButtonPending(false);
        try {
            showRestartFailure();
        } catch (Throwable notificationThrowable) {
            try {
                Log.w(TAG, "Failed to show Gboard restart error", notificationThrowable);
            } catch (Throwable ignored) {
                // A restart failure must never escape into the host app.
            }
        }
    }

    private void showRestartFailure() {
        Toast.makeText(
                this,
                text(R.string.gboard_patches_restart_failed),
                Toast.LENGTH_LONG)
                .show();
    }

    private View buildBackButton() {
        View backButton = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(getResources().getDisplayMetrics().density * 2.15f);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float density = getResources().getDisplayMetrics().density;
                float iconSize = density * 24f;
                float left = (getWidth() - iconSize) / 2f;
                float top = (getHeight() - iconSize) / 2f;
                float scale = iconSize / 24f;

                paint.setColor(palette.textPrimary);
                path.reset();
                path.moveTo(left + (19.5f * scale), top + (12f * scale));
                path.lineTo(left + (7.8f * scale), top + (12f * scale));
                path.moveTo(left + (12.9f * scale), top + (7f * scale));
                path.lineTo(left + (7.8f * scale), top + (12f * scale));
                path.lineTo(left + (12.9f * scale), top + (17f * scale));
                canvas.drawPath(path, paint);
            }
        };
        backButton.setContentDescription(text(R.string.gboard_patches_navigate_up));
        backButton.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        backButton.setBackground(buildRippleDrawable(dp(24)));
        backButton.setOnClickListener(view -> goBackOrFinish());
        return backButton;
    }

    private View buildLargeTitleBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(8), dp(18), dp(8), dp(8));
        block.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        headerTitleView = new TextView(this);
        headerTitleView.setTextColor(palette.textPrimary);
        headerTitleView.setTypeface(Typeface.DEFAULT);
        headerTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, LARGE_TITLE_SP);
        headerTitleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            headerTitleView.setAccessibilityHeading(true);
        }

        headerSummaryView = new TextView(this);
        headerSummaryView.setTextColor(palette.textSecondary);
        headerSummaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        headerSummaryView.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(8);
        headerSummaryView.setLayoutParams(summaryParams);

        block.addView(headerTitleView);
        block.addView(headerSummaryView);
        return block;
    }

    private View buildPanelCard() {
        panelContainer = new LinearLayout(this);
        panelContainer.setOrientation(LinearLayout.VERTICAL);
        panelContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return panelContainer;
    }

    private boolean isShowingRootScreen() {
        return settingsOrchestrator.snapshot().getCurrent() == null;
    }

    private void updateToolbarChrome(boolean scrolled) {
        if (toolbarView == null || toolbarTitleView == null) {
            return;
        }
        boolean showTitle = scrolled || toolbarTitleForced;
        if (showTitle != toolbarTitleShown) {
            toolbarTitleShown = showTitle;
            toolbarTitleView.animate().cancel();
            toolbarTitleView.animate()
                    .alpha(showTitle ? 1f : 0f)
                    .translationY(showTitle ? 0f : dp(6))
                    .setDuration(TOOLBAR_SCROLL_TRANSITION_MS)
                    .start();
        }
        if (scrolled == toolbarScrolledState) {
            return;
        }
        toolbarScrolledState = scrolled;
        if (toolbarColorAnimator != null) {
            toolbarColorAnimator.cancel();
        }
        toolbarColorAnimator = ValueAnimator.ofArgb(
                toolbarBackgroundColor(!scrolled),
                toolbarBackgroundColor(scrolled));
        toolbarColorAnimator.setDuration(TOOLBAR_SCROLL_TRANSITION_MS);
        toolbarColorAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            if (toolbarView != null) {
                toolbarView.setBackgroundColor(color);
            }
            applyStatusBarColor(color);
        });
        toolbarColorAnimator.start();
    }

    private int toolbarBackgroundColor(boolean scrolled) {
        return scrolled
                ? blendColors(palette.windowBackground, palette.accent,
                        TOOLBAR_SCROLLED_ACCENT_FRACTION)
                : palette.windowBackground;
    }

    @SuppressWarnings("deprecation")
    private void applyStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(color);
        }
    }

    private void goBackOrFinish() {
        if (navigateToRootIfNeeded()) {
            return;
        }
        finish();
    }

    @Override
    public void openExternalUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Ignore devices without a visible browser handler.
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to open external URL: " + url, throwable);
        }
    }

    @Override
    public void openSpeechRecognitionAndSynthesisStoreListing() {
        if (openGooglePlayListing(
                SPEECH_SERVICES_PACKAGE_NAME,
                SPEECH_SERVICES_PLAY_STORE_URL)) {
            return;
        }
        showSafeToast(
                R.string.gboard_patches_advanced_voice_speech_services_open_failed);
    }

    @Override
    public void openLiveTranscribeLanguageManager() {
        Intent launchIntent = null;
        try {
            launchIntent = getPackageManager().getLaunchIntentForPackage(
                    LIVE_TRANSCRIBE_PACKAGE_NAME);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to resolve Live Transcribe launcher", throwable);
        }
        if (tryStartActivity(launchIntent)) {
            return;
        }
        if (openGooglePlayListing(
                LIVE_TRANSCRIBE_PACKAGE_NAME,
                LIVE_TRANSCRIBE_PLAY_STORE_URL)) {
            return;
        }
        showSafeToast(
                R.string.gboard_patches_advanced_voice_live_transcribe_open_failed);
    }

    private void showSafeToast(int resourceId) {
        try {
            String message = GboardSettingsText.get(this, resourceId);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to show navigation error message", throwable);
            } catch (Throwable ignored) {
                // A patch error must never escape into the host app.
            }
        }
    }

    private boolean openGooglePlayListing(String packageName, String webUrl) {
        Intent marketIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageName));
        marketIntent.setPackage(PLAY_STORE_PACKAGE_NAME);
        if (tryStartActivity(marketIntent)) {
            return true;
        }
        return tryStartActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
    }

    private boolean tryStartActivity(Intent intent) {
        if (intent == null) {
            return false;
        }
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            Log.w(TAG, "Activity navigation attempt failed: " + intent, exception);
            return false;
        } catch (Throwable throwable) {
            Log.w(TAG, "Unexpected activity navigation failure: " + intent, throwable);
            return false;
        }
    }

    private void refreshOfflineSpeechLanguagesForCurrentFeature() {
        GboardPatchesSettingsContract.Feature currentFeature =
                settingsOrchestrator.snapshot().getCurrent();
        if (currentFeature != null && currentFeature.requiresOfflineSpeechLanguages()) {
            queryOfflineSpeechLanguages();
            return;
        }
        offlineSpeechLanguageQueryGeneration++;
        cancelOfflineSpeechLanguageQueryTimeout();
        destroyOfflineSpeechRecognizer();
    }

    private void queryOfflineSpeechLanguages() {
        offlineSpeechLanguageQueryGeneration++;
        cancelOfflineSpeechLanguageQueryTimeout();
        destroyOfflineSpeechRecognizer();
        offlineSpeechLanguages = GboardPatchesSettingsContract.OfflineSpeechLanguages.loading();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            offlineSpeechLanguages =
                    GboardPatchesSettingsContract.OfflineSpeechLanguages.unsupported();
            renderCurrentScreenSafely();
            return;
        }
        ComponentName recognitionService = new ComponentName(
                SPEECH_SERVICES_PACKAGE_NAME,
                SPEECH_SERVICES_RECOGNITION_SERVICE_CLASS_NAME);
        if (!isRecognitionServiceAvailable(recognitionService)) {
            offlineSpeechLanguages =
                    GboardPatchesSettingsContract.OfflineSpeechLanguages.unavailable();
            renderCurrentScreenSafely();
            return;
        }

        final int queryGeneration = offlineSpeechLanguageQueryGeneration;
        try {
            offlineSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(
                    this,
                    recognitionService);
            Intent recognitionIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognitionIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            offlineSpeechLanguageQueryTimeoutRunnable = () -> {
                if (queryGeneration != offlineSpeechLanguageQueryGeneration) {
                    return;
                }
                Log.w(TAG, "Speech Services recognition support query timed out");
                completeOfflineSpeechLanguageQuery(
                        queryGeneration,
                        GboardPatchesSettingsContract.OfflineSpeechLanguages.error());
            };
            screenRefreshHandler.postDelayed(
                    offlineSpeechLanguageQueryTimeoutRunnable,
                    OFFLINE_SPEECH_LANGUAGE_QUERY_TIMEOUT_MS);
            offlineSpeechRecognizer.checkRecognitionSupport(
                    recognitionIntent,
                    getMainExecutor(),
                    new RecognitionSupportCallback() {
                        @Override
                        public void onSupportResult(RecognitionSupport recognitionSupport) {
                            if (queryGeneration != offlineSpeechLanguageQueryGeneration) {
                                return;
                            }
                            List<String> installedLanguages = recognitionSupport == null
                                    ? Collections.emptyList()
                                    : recognitionSupport.getInstalledOnDeviceLanguages();
                            Log.i(TAG, "Speech Services installed offline languages from "
                                    + recognitionService.flattenToShortString()
                                    + ": " + installedLanguages);
                            completeOfflineSpeechLanguageQuery(
                                    queryGeneration,
                                    GboardPatchesSettingsContract.OfflineSpeechLanguages.available(
                                            installedLanguages));
                        }

                        @Override
                        public void onError(int error) {
                            if (queryGeneration != offlineSpeechLanguageQueryGeneration) {
                                return;
                            }
                            Log.w(TAG,
                                    "Speech Services recognition support callback reported error "
                                            + error
                                            + "; waiting for timeout or a later support result");
                        }
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to query installed on-device speech languages", throwable);
            completeOfflineSpeechLanguageQuery(
                    queryGeneration,
                    GboardPatchesSettingsContract.OfflineSpeechLanguages.error());
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isRecognitionServiceAvailable(ComponentName recognitionService) {
        try {
            android.content.pm.ServiceInfo serviceInfo = getPackageManager().getServiceInfo(
                    recognitionService,
                    0);
            return serviceInfo.enabled
                    && serviceInfo.applicationInfo != null
                    && serviceInfo.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException exception) {
            Log.w(TAG, "Speech Services recognition service is unavailable: "
                    + recognitionService.flattenToShortString());
            return false;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to inspect Speech Services recognition service: "
                    + recognitionService.flattenToShortString(), throwable);
            return false;
        }
    }

    private void completeOfflineSpeechLanguageQuery(
            int queryGeneration,
            GboardPatchesSettingsContract.OfflineSpeechLanguages result) {
        if (queryGeneration != offlineSpeechLanguageQueryGeneration) {
            return;
        }
        offlineSpeechLanguageQueryGeneration++;
        cancelOfflineSpeechLanguageQueryTimeout();
        offlineSpeechLanguages = result;
        destroyOfflineSpeechRecognizer();
        renderCurrentScreenSafely();
    }

    private void cancelOfflineSpeechLanguageQuery() {
        offlineSpeechLanguageQueryGeneration++;
        cancelOfflineSpeechLanguageQueryTimeout();
        destroyOfflineSpeechRecognizer();
    }

    private void cancelOfflineSpeechLanguageQueryTimeout() {
        if (offlineSpeechLanguageQueryTimeoutRunnable == null) {
            return;
        }
        screenRefreshHandler.removeCallbacks(offlineSpeechLanguageQueryTimeoutRunnable);
        offlineSpeechLanguageQueryTimeoutRunnable = null;
    }

    private void destroyOfflineSpeechRecognizer() {
        if (offlineSpeechRecognizer == null) {
            return;
        }
        try {
            offlineSpeechRecognizer.destroy();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to destroy on-device speech recognizer", throwable);
        } finally {
            offlineSpeechRecognizer = null;
        }
    }

    private boolean navigateToRootIfNeeded() {
        return !navigateBack();
    }

    private boolean navigateBack() {
        GboardPatchesSettingsOrchestrator.State<GboardPatchesSettingsContract.Feature> state =
                settingsOrchestrator.snapshot();
        if (state.getCurrent() != null) {
            boolean returningToRoot = state.getBackStack().isEmpty();
            requestScrollPositionOnNextScreenApply(
                    scrollState.leaveFeature(returningToRoot));
        }
        return applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.back()));
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
            Object callback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[] { callbackClass },
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("hashCode".equals(method.getName())) {
                                return Integer.valueOf(System.identityHashCode(proxy));
                            }
                            if ("equals".equals(method.getName())) {
                                Object other = args != null && args.length > 0 ? args[0] : null;
                                return Boolean.valueOf(proxy == other);
                            }
                            if ("toString".equals(method.getName())) {
                                return "GboardPatchesOnBackInvokedCallbackProxy";
                            }
                        }
                        if ("onBackInvoked".equals(method.getName())) {
                            if (!navigateToRootIfNeeded()) {
                                finish();
                            }
                        }
                        return null;
                    });
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher")
                    .invoke(this);
            if (dispatcher == null) {
                return;
            }
            int priorityDefault = dispatcherClass.getField("PRIORITY_DEFAULT").getInt(null);
            dispatcherClass.getMethod("registerOnBackInvokedCallback", int.class, callbackClass)
                    .invoke(dispatcher, priorityDefault, callback);
            backInvokedCallback = callback;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to register platform back callback", throwable);
            backInvokedCallback = null;
        }
    }

    private void unregisterBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backInvokedCallback == null) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher")
                    .invoke(this);
            if (dispatcher != null) {
                dispatcherClass.getMethod("unregisterOnBackInvokedCallback", callbackClass)
                        .invoke(dispatcher, backInvokedCallback);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to unregister platform back callback", throwable);
        }
        backInvokedCallback = null;
    }

    private void installWindowInsetsHandling(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            try {
                updateToolbarInsets(resolveTopInset(insets));
                int bottomInset = resolveBottomInset(insets);
                if (contentScrollView != null) {
                    int bottomPadding = keyboardPreviewController == null
                            ? bottomInset
                            : keyboardPreviewController.contentBottomPadding(bottomInset);
                    contentScrollView.setPadding(0, 0, 0, bottomPadding);
                }
                if (screenActionContainer != null) {
                    screenActionContainer.setPadding(
                            dp(16), dp(8), dp(16), dp(16) + bottomInset);
                }
                if (keyboardPreviewController != null) {
                    keyboardPreviewController.updateSafeAreaInsets(resolveRightInset(insets),
                            bottomInset);
                }
            } catch (Throwable throwable) {
                try {
                    Log.w(TAG, "Failed to apply patches settings insets", throwable);
                } catch (Throwable ignored) {
                    // Insets are optional UI polish and must not affect the settings host.
                }
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void scheduleDeferredRender() {
        if (settingsOrchestrator.snapshot().isFatal()) {
            return;
        }
        View anchor = panelContainer != null ? panelContainer : getWindow().getDecorView();
        if (anchor == null) {
            initializeFeaturesAndRenderSafely();
            return;
        }
        anchor.removeCallbacks(deferredRenderRunnable);
        anchor.post(deferredRenderRunnable);
    }

    private void cancelDeferredRender() {
        View anchor = panelContainer != null ? panelContainer : getWindow().getDecorView();
        if (anchor != null) {
            anchor.removeCallbacks(deferredRenderRunnable);
        }
    }

    private void postToDecorView(Runnable action) {
        if (action == null) {
            return;
        }
        View anchor = panelContainer != null ? panelContainer : getWindow().getDecorView();
        if (anchor == null) {
            action.run();
            return;
        }
        anchor.post(action);
    }

    private void initializeFeaturesAndRenderSafely() {
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.requestRender()));
    }

    private boolean applyOrchestration(
            GboardPatchesSettingsOrchestrator.Transition<
                    GboardPatchesSettingsContract.Feature,
                    GboardPatchesSettingsContract.Intent> transition) {
        boolean exitRequested = false;
        for (GboardPatchesSettingsOrchestrator.Effect<
                GboardPatchesSettingsContract.Feature,
                GboardPatchesSettingsContract.Intent> effect : transition.getEffects()) {
            switch (effect.getKind()) {
                case REQUEST_RENDER:
                    enqueueScreenBuild(effect.getGeneration());
                    break;
                case REFRESH_DEPENDENCIES:
                    refreshOfflineSpeechLanguagesForCurrentFeature();
                    break;
                case SCROLL_TO_TOP:
                    if (requestedScrollYOnNextScreenApply == NO_SCROLL_POSITION_REQUESTED) {
                        requestScrollPositionOnNextScreenApply(0);
                    }
                    break;
                case SCHEDULE_REFRESH:
                    scheduleScreenRefresh(effect.getDelayMs());
                    break;
                case CANCEL_REFRESH:
                    cancelScheduledScreenRefresh();
                    break;
                case CANCEL_PENDING_RENDER:
                    cancelDeferredRender();
                    break;
                case EXECUTE_INTENT:
                    GboardPatchesSettingsContract.Intent intent = effect.getPayload();
                    if (intent != null) {
                        intent.apply(this);
                    }
                    break;
                case EXIT:
                    exitRequested = true;
                    break;
                default:
                    break;
            }
        }
        syncVisibleFeatureLifecycle();
        return exitRequested;
    }

    private void syncVisibleFeatureLifecycle() {
        GboardPatchesSettingsContract.Feature target = featureLifecycleEnabled
                ? settingsOrchestrator.snapshot().getCurrent()
                : null;
        if (target == lifecycleVisibleFeature) {
            return;
        }
        GboardPatchesSettingsContract.Feature previous = lifecycleVisibleFeature;
        lifecycleVisibleFeature = target;
        if (previous != null) {
            try {
                previous.onHidden(this);
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to hide settings feature", throwable);
            }
        }
        if (target != null) {
            try {
                target.onVisible(this);
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to show settings feature", throwable);
            }
        }
    }

    private void enqueueScreenBuild(int buildGeneration) {
        try {
            screenBuildExecutor.execute(() -> buildAndApplyCurrentScreen(buildGeneration));
        } catch (RejectedExecutionException ignored) {
            // Activity is already tearing down.
        }
    }

    private static boolean containsEffect(
            GboardPatchesSettingsOrchestrator.Transition<?, ?> transition,
            GboardPatchesSettingsOrchestrator.EffectKind kind) {
        for (GboardPatchesSettingsOrchestrator.Effect<?, ?> effect : transition.getEffects()) {
            if (effect.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    private void ensureFeaturesInitialized() {
        if (featuresInitialized) {
            return;
        }
        synchronized (screenBuildLock) {
            if (featuresInitialized) {
                return;
            }
            try {
                features = GboardPatchesSettingsFeatureRegistry.features(this);
            } catch (Throwable throwable) {
                throw new IllegalStateException(
                        "Failed to resolve patches settings features",
                        throwable);
            }
            featuresInitialized = true;
        }
    }

    private void buildAndApplyCurrentScreen(int buildGeneration) {
        if (settingsOrchestrator.snapshot().isFatal()) {
            return;
        }

        GboardPatchesSettingsContract.Screen screen;
        GboardPatchesSettingsContract.Feature featureSnapshot =
                settingsOrchestrator.snapshot().getCurrent();
        try {
            ensureFeaturesInitialized();
            if (openInitialFeatureFromIntentIfNeeded()) {
                return;
            }
            featureSnapshot = settingsOrchestrator.snapshot().getCurrent();
            screen = featureSnapshot == null
                    ? buildRootScreen()
                    : featureSnapshot.buildScreen(this);
        } catch (IllegalStateException exception) {
            runOnUiThread(() -> {
                if (!isLatestScreenBuild(buildGeneration)
                        || settingsOrchestrator.snapshot().isFatal()
                        || isFinishing()) {
                    return;
                }
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                showFatalFallbackScreen(exception.getMessage(), cause);
            });
            return;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render settings screen", throwable);
            screen = buildFeatureErrorScreen(settingsOrchestrator.snapshot().getCurrent());
        }

        GboardPatchesSettingsContract.Feature appliedFeature = featureSnapshot;
        GboardPatchesSettingsContract.Screen finalScreen = screen;
        runOnUiThread(() -> {
            if (isFinishing()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && isDestroyed())) {
                return;
            }
            GboardPatchesSettingsOrchestrator.Transition<
                    GboardPatchesSettingsContract.Feature,
                    GboardPatchesSettingsContract.Intent> renderTransition =
                    settingsOrchestrator.accept(
                            GboardPatchesSettingsOrchestrator.Event.renderReady(
                                    buildGeneration,
                                    finalScreen.getRefreshIntervalMs()));
            if (!containsEffect(renderTransition,
                    GboardPatchesSettingsOrchestrator.EffectKind.APPLY_RENDER)
                    || settingsOrchestrator.snapshot().isFatal()) {
                return;
            }
            try {
                applyScreen(finalScreen);
                applyOrchestration(renderTransition);
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to apply settings screen", throwable);
                try {
                    GboardPatchesSettingsOrchestrator.Transition<
                            GboardPatchesSettingsContract.Feature,
                            GboardPatchesSettingsContract.Intent> recoveryTransition =
                            settingsOrchestrator.accept(
                                    GboardPatchesSettingsOrchestrator.Event.renderFailed(
                                            buildGeneration));
                    if (!containsEffect(recoveryTransition,
                            GboardPatchesSettingsOrchestrator.EffectKind.APPLY_RECOVERY)) {
                        return;
                    }
                    GboardPatchesSettingsContract.Screen recoveryScreen =
                            buildFeatureErrorScreen(appliedFeature);
                    GboardPatchesSettingsOrchestrator.Transition<
                            GboardPatchesSettingsContract.Feature,
                            GboardPatchesSettingsContract.Intent> recoveryReady =
                            settingsOrchestrator.accept(
                                    GboardPatchesSettingsOrchestrator.Event.recoveryReady(
                                            buildGeneration,
                                            recoveryScreen.getRefreshIntervalMs()));
                    if (!containsEffect(recoveryReady,
                            GboardPatchesSettingsOrchestrator.EffectKind.APPLY_RECOVERY)) {
                        return;
                    }
                    applyScreen(recoveryScreen);
                    applyOrchestration(recoveryReady);
                } catch (Throwable fallbackThrowable) {
                    Log.w(TAG, "Failed to apply feature error screen", fallbackThrowable);
                    try {
                        fallbackThrowable.addSuppressed(throwable);
                    } catch (Throwable ignored) {
                    }
                    showFatalFallbackScreen("Failed to apply settings screen", fallbackThrowable);
                }
            }
        });
    }

    private boolean isLatestScreenBuild(int buildGeneration) {
        return buildGeneration == settingsOrchestrator.snapshot().getRenderGeneration();
    }

    private void updateToolbarInsets(int topInset) {
        if (toolbarView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = toolbarView.getLayoutParams();
        int targetHeight = dp(TOOLBAR_HEIGHT_DP) + topInset;
        if (layoutParams.height != targetHeight) {
            layoutParams.height = targetHeight;
            toolbarView.setLayoutParams(layoutParams);
        }
        toolbarView.setPadding(dp(4), topInset, dp(12), 0);
    }

    private int resolveTopInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
            return systemBarsInsets.top;
        }
        return insets.getSystemWindowInsetTop();
    }

    private int resolveBottomInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
            return systemBarsInsets.bottom;
        }
        return insets.getSystemWindowInsetBottom();
    }

    private int resolveRightInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
            return systemBarsInsets.right;
        }
        return insets.getSystemWindowInsetRight();
    }

    private void renderCurrentScreenSafely() {
        initializeFeaturesAndRenderSafely();
    }

    private void applyScreen(GboardPatchesSettingsContract.Screen screen) {
        boolean rootScreen = isShowingRootScreen();
        observeRestartRequiredRows(screen);
        toolbarTitleView.setText(screen.getToolbarTitle());
        applyToolbarActions(screen.getToolbarActions());
        boolean fullBleed = screen.isFullBleed();
        headerCardView.setVisibility(fullBleed ? View.GONE : View.VISIBLE);
        contentColumn.setPadding(
                fullBleed ? 0 : dp(16),
                0,
                fullBleed ? 0 : dp(16),
                fullBleed ? 0 : dp(16));
        applyLargeTitleText(headerTitleView,
                TextUtils.isEmpty(screen.getHeaderTitle())
                        ? screen.getToolbarTitle()
                        : screen.getHeaderTitle());
        headerSummaryView.setText(screen.getHeaderSummary());
        headerSummaryView.setVisibility(
                TextUtils.isEmpty(screen.getHeaderSummary()) ? View.GONE : View.VISIBLE);
        applyPanelStyle(screen.getPanelStyle());
        panelContainer.removeAllViews();
        List<GboardPatchesSettingsContract.Section> sections = screen.getSections();
        GboardPatchesSettingsContract.Row masterToggle =
                rootScreen ? null : findMasterToggle(sections);
        if (masterToggle != null && screen.isMasterToggleFirst()) {
            panelContainer.addView(buildMasterSwitchCard(masterToggle, screen.getToolbarTitle()));
        }
        for (GboardPatchesSettingsContract.StatusBlock statusBlock : screen.getStatusBlocks()) {
            panelContainer.addView(createStatusBlockView(statusBlock));
        }
        if (masterToggle != null && !screen.isMasterToggleFirst()) {
            panelContainer.addView(buildMasterSwitchCard(masterToggle, screen.getToolbarTitle()));
        }
        for (int index = 0; index < sections.size(); index++) {
            GboardPatchesSettingsContract.Section section = sections.get(index);
            if (index == 0 && masterToggle != null) {
                List<GboardPatchesSettingsContract.Row> rest =
                        new ArrayList<GboardPatchesSettingsContract.Row>(section.getItems());
                rest.remove(0);
                if (!rest.isEmpty()) {
                    String title = section.getTitle();
                    if (title == null || title.isEmpty()) {
                        title = text(R.string.gboard_patches_section_settings);
                    }
                    panelContainer.addView(createSectionView(
                            new GboardPatchesSettingsContract.Section(
                                    title, section.getDescription(), section.getStyle(), rest)));
                }
            } else {
                panelContainer.addView(createSectionView(section));
            }
        }

        screenActionContainer.removeAllViews();
        GboardPatchesSettingsContract.CommandRow primaryAction = screen.getPrimaryAction();
        if (primaryAction == null) {
            screenActionContainer.setVisibility(View.GONE);
        } else {
            View primaryActionView = createSectionView(
                    new GboardPatchesSettingsContract.Section(
                            null, Collections.singletonList(primaryAction)));
            if (primaryActionView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
                margins.topMargin = 0;
            }
            screenActionContainer.addView(primaryActionView);
            screenActionContainer.setVisibility(View.VISIBLE);
        }
        toolbarTitleForced = fullBleed;
        int requestedScrollY = consumeRequestedScrollPositionOnNextScreenApply();
        if (requestedScrollY >= 0) {
            scrollContentToPositionAfterLayout(requestedScrollY);
        }
        updateToolbarChrome(currentScrollY() > dp(TOOLBAR_SCROLLED_THRESHOLD_DP));
    }

    private void observeRestartRequiredRows(GboardPatchesSettingsContract.Screen screen) {
        if (screen == null) {
            return;
        }
        String localeTag = currentLocaleTag();
        for (GboardPatchesSettingsContract.Section section : screen.getSections()) {
            for (GboardPatchesSettingsContract.Row row : section.getItems()) {
                observeRestartRequiredRow(row, localeTag);
                GboardPatchesSettingsContract.RowDecoration decoration = row.getDecoration();
                if (decoration != null) {
                    observeRestartRequiredRow(decoration.getEntryToggle(), localeTag);
                }
            }
        }
    }

    private static void observeRestartRequiredRow(GboardPatchesSettingsContract.Row row,
            String localeTag) {
        if (row == null || !row.isRestartRequired()) {
            return;
        }
        GboardRestartPendingChanges.observe(row.getRestartKey(), restartValueOf(row), localeTag);
    }

    static String restartValueOf(GboardPatchesSettingsContract.Row row) {
        if (row instanceof GboardPatchesSettingsContract.ToggleRow toggleRow) {
            return Boolean.toString(toggleRow.isChecked());
        }
        if (row instanceof GboardPatchesSettingsContract.SwitchRow switchRow) {
            return Boolean.toString(switchRow.isChecked());
        }
        if (row instanceof GboardPatchesSettingsContract.SelectorRow selectorRow) {
            return selectorRow.getCurrentValue() == null ? "" : selectorRow.getCurrentValue();
        }
        return null;
    }

    private String currentLocaleTag() {
        try {
            return getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static GboardPatchesSettingsContract.Row findMasterToggle(
            List<GboardPatchesSettingsContract.Section> sections) {
        if (sections.isEmpty() || sections.get(0).getItems().isEmpty()) {
            return null;
        }
        GboardPatchesSettingsContract.Row row = sections.get(0).getItems().get(0);
        if (toggleActionOf(row) == null) {
            return null;
        }
        Boolean explicit = row.isMasterToggleExplicit();
        return explicit == null || explicit ? row : null;
    }

    private View buildMasterSwitchCard(GboardPatchesSettingsContract.Row rowModel,
            String screenTitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(76));
        card.setPadding(dp(20), dp(16), dp(20), dp(16));

        int onCardColor = palette.onMasterSwitchContainer;
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.masterSwitchContainer);
        background.setCornerRadius(dp(28));
        card.setBackground(new RippleDrawable(
                ColorStateList.valueOf(blendAlpha(onCardColor, 0.10f)), background, null));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(4);
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textColumn.setLayoutParams(textParams);
        textColumn.setPadding(0, 0, dp(16), 0);

        TextView titleView = new TextView(this);
        String title = rowModel.getTitle() == null ? "" : rowModel.getTitle().toString();
        String screenName = screenTitle == null ? "" : screenTitle;
        if (title.equals("啟用") || title.length() <= 2) {
            title = "使用" + screenName;
        } else if (title.startsWith("啟用")) {
            title = "使用" + title.substring(2);
        } else if (title.equalsIgnoreCase("Enable")) {
            title = "Use " + screenName;
        } else if (title.startsWith("Enable ")) {
            title = "Use " + title.substring(7);
        }
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        titleView.setTypeface(mediumTypeface());
        titleView.setTextColor(onCardColor);
        textColumn.addView(titleView);

        if (!TextUtils.isEmpty(rowModel.getSummary())) {
            TextView summaryView = new TextView(this);
            summaryView.setText(rowModel.getSummary());
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            summaryView.setTextColor(blendAlpha(onCardColor, 0.80f));
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            summaryParams.topMargin = dp(2);
            textColumn.addView(summaryView, summaryParams);
        }
        View previewButton = buildPreviewButton(rowModel);
        if (previewButton instanceof LinearLayout previewPill) {
            GradientDrawable pillBackground = new GradientDrawable();
            pillBackground.setColor(blendAlpha(onCardColor, 0.12f));
            pillBackground.setCornerRadius(dp(16));
            previewPill.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(blendAlpha(onCardColor, 0.12f)),
                    pillBackground, null));
            if (previewPill.getChildAt(0) instanceof ImageView iconView) {
                iconView.setImageTintList(ColorStateList.valueOf(onCardColor));
            }
            if (previewPill.getChildAt(1) instanceof TextView labelView) {
                labelView.setTextColor(onCardColor);
            }
            textColumn.addView(previewPill);
        }
        card.addView(textColumn);

        Switch switchView = new Switch(this);
        applyMasterSwitchStyle(switchView);
        switchView.setChecked(isRowChecked(rowModel));
        switchView.setEnabled(rowModel.isEnabled());
        switchView.setAlpha(rowModel.isEnabled() ? 1f : DISABLED_CONTENT_ALPHA);
        switchView.setFocusable(false);
        switchView.setClickable(false);
        card.addView(switchView);
        card.setOnClickListener(view -> {
            if (rowModel.isEnabled()) {
                switchView.toggle();
                runToggleInBackground(rowModel, switchView.isChecked(), "toggle master switch");
            }
        });
        return card;
    }

    private void applyToolbarActions(
            List<GboardPatchesSettingsContract.ToolbarAction> actions) {
        if (toolbarActionsView == null) {
            return;
        }
        toolbarActionsView.removeAllViews();
        if (actions == null) {
            return;
        }
        for (GboardPatchesSettingsContract.ToolbarAction action : actions) {
            if (action != null) {
                toolbarActionsView.addView(buildToolbarActionButton(action));
            }
        }
    }

    private View buildToolbarActionButton(GboardPatchesSettingsContract.ToolbarAction action) {
        View button = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.8f);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (action.getIcon() != GboardPatchesSettingsContract.ToolbarIcon.DELETE) {
                    return;
                }
                float density = getResources().getDisplayMetrics().density;
                float left = (getWidth() - density * 24f) / 2f;
                float top = (getHeight() - density * 24f) / 2f;
                paint.setColor(palette.textPrimary);
                canvas.drawLine(left + 5f * density, top + 6f * density,
                        left + 19f * density, top + 6f * density, paint);
                canvas.drawLine(left + 9f * density, top + 3.5f * density,
                        left + 15f * density, top + 3.5f * density, paint);
                canvas.drawRoundRect(left + 7f * density, top + 8f * density,
                        left + 17f * density, top + 21f * density,
                        1.5f * density, 1.5f * density, paint);
                canvas.drawLine(left + 10f * density, top + 11f * density,
                        left + 10f * density, top + 18f * density, paint);
                canvas.drawLine(left + 14f * density, top + 11f * density,
                        left + 14f * density, top + 18f * density, paint);
            }
        };
        button.setContentDescription(action.getContentDescription());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        button.setBackground(buildRippleDrawable(dp(24)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            button.setTooltipText(action.getContentDescription());
        }
        button.setOnClickListener(view -> runSafely(
                "handle toolbar action", action.getAction()));
        return button;
    }

    private void applyPanelStyle(GboardPatchesSettingsContract.PanelStyle panelStyle) {
        if (panelContainer == null) {
            return;
        }
        panelContainer.setBackground(null);
        panelContainer.setPadding(0, 0, 0, 0);
    }

    private void openFeaturePath(GboardPatchesSettingsContract.Feature... featurePath) {
        if (featurePath == null || featurePath.length == 0) {
            return;
        }
        List<GboardPatchesSettingsContract.Feature> sanitizedPath =
                new ArrayList<GboardPatchesSettingsContract.Feature>();
        for (GboardPatchesSettingsContract.Feature feature : featurePath) {
            if (feature != null) {
                sanitizedPath.add(feature);
            }
        }
        if (sanitizedPath.isEmpty()) {
            return;
        }
        scrollState.resetForDirectPath(sanitizedPath.size() - 1);
        requestScrollPositionOnNextScreenApply(0);
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.replacePath(sanitizedPath)));
    }

    private boolean openInitialFeatureFromIntentIfNeeded() {
        if (initialFeatureFromIntentHandled) {
            return false;
        }
        initialFeatureFromIntentHandled = true;
        Intent intent = getIntent();
        if (intent == null) {
            return false;
        }
        if (restoreNavigationPathFromIntent(intent)) {
            return true;
        }
        return false;
    }

    private boolean restoreNavigationPathFromIntent(Intent intent) {
        if (!intent.hasExtra(EXTRA_NAVIGATION_PATH)) {
            return false;
        }
        ArrayList<String> pathIds = intent.getStringArrayListExtra(EXTRA_NAVIGATION_PATH);
        intent.removeExtra(EXTRA_NAVIGATION_PATH);
        List<GboardPatchesSettingsContract.Feature> resolvedPath =
                GboardPatchesNavigationPath.resolve(this, features, pathIds);
        if (pathIds == null
                || pathIds.isEmpty()
                || resolvedPath.size() != pathIds.size()) {
            return false;
        }
        runOnUiThread(() -> {
            scrollState.resetForDirectPath(resolvedPath.size() - 1);
            requestScrollPositionOnNextScreenApply(0);
            applyOrchestration(settingsOrchestrator.accept(
                    GboardPatchesSettingsOrchestrator.Event.replacePath(resolvedPath)));
        });
        return true;
    }

    private int currentScrollY() {
        return contentScrollView == null ? 0 : contentScrollView.getScrollY();
    }

    private void requestScrollPositionOnNextScreenApply(int scrollY) {
        requestedScrollYOnNextScreenApply = Math.max(0, scrollY);
    }

    private int consumeRequestedScrollPositionOnNextScreenApply() {
        int requestedScrollY = requestedScrollYOnNextScreenApply;
        requestedScrollYOnNextScreenApply = NO_SCROLL_POSITION_REQUESTED;
        return requestedScrollY;
    }

    private void scrollContentToPositionAfterLayout(int scrollY) {
        if (contentScrollView == null) {
            return;
        }
        int targetScrollY = Math.max(0, scrollY);
        contentScrollView.scrollTo(0, targetScrollY);
        contentScrollView.post(() -> {
            if (contentScrollView == null) {
                return;
            }
            contentScrollView.scrollTo(0, targetScrollY);
        });
    }

    private void scheduleScreenRefresh(long refreshIntervalMs) {
        cancelScheduledScreenRefresh();
        if (refreshIntervalMs <= 0L) {
            return;
        }
        screenRefreshHandler.postDelayed(screenRefreshRunnable, refreshIntervalMs);
    }

    private void cancelScheduledScreenRefresh() {
        screenRefreshHandler.removeCallbacks(screenRefreshRunnable);
    }

    private void showFatalFallbackScreen(String reason, Throwable throwable) {
        settingsOrchestrator.accept(GboardPatchesSettingsOrchestrator.Event.fatal());
        cancelScheduledScreenRefresh();
        setRestartButtonPending(false);
        Log.e(TAG, reason, throwable);
        detachKeyboardPreviewSafely();
        if (palette == null) {
            palette = Palette.forConfiguration(getResources().getConfiguration());
        }
        toolbarView = null;
        toolbarTitleView = null;
        toolbarActionsView = null;
        restartButton = null;
        restartBadgeView = null;
        headerCardView = null;
        headerTitleView = null;
        headerSummaryView = null;
        panelContainer = null;
        contentScrollView = null;
        contentColumn = null;
        screenActionContainer = null;
        setContentView(buildFatalFallbackView());
    }

    private View buildFatalFallbackView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(palette.windowBackground);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView titleView = new TextView(this);
        titleView.setText(text(R.string.gboard_patches_fatal_fallback_title));
        titleView.setTextColor(palette.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView summaryView = new TextView(this);
        summaryView.setText(text(R.string.gboard_patches_fatal_fallback_summary));
        summaryView.setTextColor(palette.textSecondary);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        summaryView.setGravity(Gravity.CENTER_HORIZONTAL);
        summaryView.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(12);

        root.addView(titleView);
        root.addView(summaryView, summaryParams);
        return root;
    }

    private void detachKeyboardPreviewSafely() {
        GboardKeyboardPreviewController controller = keyboardPreviewController;
        keyboardPreviewController = null;
        if (controller == null) return;
        try {
            controller.detach();
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to detach keyboard preview", throwable);
            } catch (Throwable ignored) {
                // Activity teardown must continue even if diagnostics fail.
            }
        }
    }

    private GboardPatchesSettingsContract.Screen buildRootScreen() {
        return createRootScreen(
                this,
                features,
                BuildConfig.PATCH_AUTHOR,
                BuildConfig.PATCH_VERSION,
                () -> openExternalUrl(ABOUT_AUTHOR_URL),
                () -> openExternalUrl(ABOUT_PATCH_REPOSITORY_URL));
    }

    static GboardPatchesSettingsContract.Screen createRootScreen(
            GboardPatchesSettingsContract.FeatureHost host,
            List<? extends GboardPatchesSettingsContract.Feature> rootFeatures,
            String aboutAuthor,
            String patchVersion,
            Runnable authorAction,
            Runnable patchRepositoryAction) {
        List<GboardPatchesSettingsContract.Row> featureRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        Context context = host == null ? null : host.getContext();
        for (GboardPatchesSettingsContract.Feature feature : rootFeatures) {
            GboardPatchesSettingsContract.NavigationRow navigationRow =
                    new GboardPatchesSettingsContract.NavigationRow(
                            feature.getEntryTitle(),
                            feature.getEntrySummary(),
                            true,
                            () -> feature.openRootEntry(host));
            if (feature.getEntryIcon() != GboardPatchesIcons.NONE) {
                navigationRow.setDecoration(new GboardPatchesSettingsContract.RowDecoration()
                        .leadingIcon(feature.getEntryIcon(), feature.getEntryTone()));
            }
            featureRows.add(navigationRow);
        }
        List<GboardPatchesSettingsContract.Row> preferenceRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        preferenceRows.add(new GboardPatchesSettingsContract.SelectorRow(
                GboardSettingsText.get(context, R.string.gboard_patches_language_title),
                GboardSettingsText.get(context, R.string.gboard_patches_language_summary),
                currentLanguageLabel(context),
                true,
                () -> showLanguageDialog(host))
                .setDecoration(new GboardPatchesSettingsContract.RowDecoration()
                        .leadingIcon(GboardPatchesIcons.TRANSLATE, "slate")));
        List<GboardPatchesSettingsContract.Row> aboutRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        aboutRows.add(new GboardPatchesSettingsContract.CommandRow(
                GboardSettingsText.get(context, R.string.gboard_patches_about_author_title),
                aboutAuthor,
                true,
                authorAction)
                .setDecoration(new GboardPatchesSettingsContract.RowDecoration()
                        .leadingIcon(GboardPatchesIcons.PERSON, "slate")));
        aboutRows.add(new GboardPatchesSettingsContract.CommandRow(
                GboardSettingsText.get(context,
                        R.string.gboard_patches_about_patch_version_title),
                patchVersion,
                true,
                patchRepositoryAction)
                .setDecoration(new GboardPatchesSettingsContract.RowDecoration()
                        .leadingIcon(GboardPatchesIcons.TAG, "slate")));
        String rootTitle = GboardSettingsText.get(context, R.string.gboard_patches_activity_title);
        return new GboardPatchesSettingsContract.Screen(
                rootTitle,
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge),
                rootTitle,
                GboardSettingsText.get(context, R.string.gboard_patches_header_summary),
                Collections.emptyList(),
                Arrays.asList(
                        new GboardPatchesSettingsContract.Section(
                                GboardSettingsText.get(context,
                                        R.string.gboard_patches_section_features),
                                featureRows),
                        new GboardPatchesSettingsContract.Section(
                                GboardSettingsText.get(context,
                                        R.string.gboard_patches_section_preferences),
                                preferenceRows),
                        new GboardPatchesSettingsContract.Section(
                                GboardSettingsText.get(context,
                                        R.string.gboard_patches_section_about),
                                aboutRows)));
    }

    private static void showLanguageDialog(GboardPatchesSettingsContract.FeatureHost host) {
        if (host == null) {
            return;
        }
        Context context = host.getContext();
        GboardPatchesSettingsContract.showChoiceDialog(host,
                GboardSettingsText.get(context, R.string.gboard_patches_language_title),
                new String[] {
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_language_system_label),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_language_english_label),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_language_traditional_chinese_label)
                },
                new String[] {
                        GboardSettingsLocaleManager.LANGUAGE_SYSTEM,
                        GboardSettingsLocaleManager.LANGUAGE_ENGLISH,
                        GboardSettingsLocaleManager.LANGUAGE_TRADITIONAL_CHINESE
                },
                context == null
                        ? GboardSettingsLocaleManager.LANGUAGE_SYSTEM
                        : GboardSettingsLocaleManager.readLanguagePreference(
                                GboardPatchesSettings.preferences(context)),
                "",
                () -> {
                },
                value -> {
                    if (context == null) {
                        return;
                    }
                    GboardSettingsLocaleManager.writeLanguagePreference(context, value);
                    if (context instanceof Activity activity) {
                        activity.recreate();
                    } else {
                        GboardPatchesSettingsContract.refresh(host);
                    }
                });
    }

    private static String currentLanguageLabel(Context context) {
        SharedPreferences preferences = context == null
                ? null
                : GboardPatchesSettings.preferences(context);
        String preference = GboardSettingsLocaleManager.readLanguagePreference(preferences);
        if (GboardSettingsLocaleManager.LANGUAGE_TRADITIONAL_CHINESE.equals(preference)) {
            return GboardSettingsText.get(context,
                    R.string.gboard_patches_language_traditional_chinese_label);
        }
        if (GboardSettingsLocaleManager.LANGUAGE_ENGLISH.equals(preference)) {
            return GboardSettingsText.get(context,
                    R.string.gboard_patches_language_english_label);
        }
        String effectiveLanguage = GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                preference,
                Locale.getDefault());
        String effectiveLabel =
                GboardSettingsLocaleManager.LANGUAGE_TRADITIONAL_CHINESE.equals(effectiveLanguage)
                        ? GboardSettingsText.get(context,
                                R.string.gboard_patches_language_traditional_chinese_label)
                        : GboardSettingsText.get(context,
                                R.string.gboard_patches_language_english_label);
        return GboardSettingsText.format(context,
                R.string.gboard_patches_language_system_value,
                effectiveLabel);
    }

    private GboardPatchesSettingsContract.Screen buildFeatureErrorScreen(
            GboardPatchesSettingsContract.Feature feature) {
        String toolbarTitle = feature != null
                ? feature.getEntryTitle()
                : TOOLBAR_TITLE_PATCHES;
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                text(R.string.gboard_patches_error_row_title),
                text(R.string.gboard_patches_error_row_summary),
                GboardPatchesSettingsContract.StatusTone.WARNING));
        return new GboardPatchesSettingsContract.Screen(
                toolbarTitle,
                text(R.string.gboard_patches_header_badge),
                text(R.string.gboard_patches_error_header_title),
                text(R.string.gboard_patches_error_header_summary),
                statusBlocks,
                Collections.emptyList());
    }

    private View createRowView(GboardPatchesSettingsContract.Row row) {
        if (row instanceof GboardPatchesSettingsContract.CustomViewRow customViewRow) {
            return customViewRow.getViewFactory().create(this);
        }
        if (row instanceof GboardPatchesSettingsContract.ToggleRow toggleRow) {
            return createToggleRow(toggleRow);
        }
        if (row instanceof GboardPatchesSettingsContract.NavigationRow navigationRow) {
            return createNavigationRow(navigationRow);
        }
        if (row instanceof GboardPatchesSettingsContract.SelectorRow selectorRow) {
            return createSelectorRow(selectorRow);
        }
        if (row instanceof GboardPatchesSettingsContract.DetailRow detailRow) {
            return createDetailRow(detailRow);
        }
        if (row instanceof GboardPatchesSettingsContract.DangerRow dangerRow) {
            return createDangerRow(dangerRow);
        }
        if (row instanceof GboardPatchesSettingsContract.CommandRow commandRow) {
            return createCommandRow(commandRow);
        }
        if (row instanceof GboardPatchesSettingsContract.SwitchRow switchRow) {
            GboardPatchesSettingsContract.ToggleRow toggleRow =
                    new GboardPatchesSettingsContract.ToggleRow(
                            switchRow.getTitle(),
                            switchRow.getSummary(),
                            switchRow.isEnabled(),
                            switchRow.isChecked(),
                            switchRow.getToggleAction(),
                            switchRow.getPreviewSpec());
            toggleRow.setDecoration(switchRow.getDecoration());
            return createToggleRow(toggleRow);
        }
        if (row instanceof GboardPatchesSettingsContract.ActionRow actionRow) {
            if (actionRow.shouldShowChevron()) {
                return createNavigationRow(new GboardPatchesSettingsContract.NavigationRow(
                        actionRow.getTitle(),
                        actionRow.getSummary(),
                        null,
                        actionRow.isEnabled(),
                        actionRow.getAction(),
                        actionRow.getPreviewSpec()));
            }
            return createCommandRow(new GboardPatchesSettingsContract.CommandRow(
                    actionRow.getTitle(),
                    actionRow.getSummary(),
                    actionRow.isEnabled(),
                    actionRow.getAction(),
                    actionRow.getPreviewSpec()));
        }
        if (row instanceof GboardPatchesSettingsContract.InfoRow infoRow) {
            return createDetailRow(new GboardPatchesSettingsContract.DetailRow(
                    infoRow.getTitle(),
                    infoRow.getSummary(),
                    infoRow.isEnabled(),
                    isLikelyMonospace(infoRow.getSummary())));
        }
        throw new IllegalArgumentException("Unsupported row type: " + row.getClass().getName());
    }

    private View createToggleRow(GboardPatchesSettingsContract.ToggleRow rowModel) {
        LinearLayout row = buildBaseRow();
        addLeadingIcon(row, rowModel);
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        addPreviewBelowSummary(textColumn, rowModel);
        row.addView(textColumn);

        Switch switchView = new Switch(this);
        switchView.setShowText(false);
        applyMaterialSwitchStyle(switchView);
        switchView.setChecked(rowModel.isChecked());
        switchView.setOnCheckedChangeListener((buttonView, isChecked) ->
                runToggleInBackground(rowModel, isChecked, "toggle switch row"));
        row.addView(switchView, trailingLayoutParams(0));
        bindSwitchRowState(row, titleView, summaryView, switchView, rowModel.isEnabled());
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("toggle switch row", switchView::toggle);
            }
        });
        return row;
    }

    private View createNavigationRow(GboardPatchesSettingsContract.NavigationRow rowModel) {
        GboardPatchesSettingsContract.RowDecoration decoration = rowModel.getDecoration();
        if (decoration != null && decoration.getEntryToggle() != null) {
            return createSplitNavigationRow(rowModel, decoration.getEntryToggle());
        }
        LinearLayout row = buildBaseRow();
        addLeadingIcon(row, rowModel);
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        addPreviewBelowSummary(textColumn, rowModel);
        row.addView(textColumn);
        TextView valueView = null;
        if (rowModel.getTrailingText() != null && !rowModel.getTrailingText().isEmpty()) {
            valueView = buildTrailingValue(rowModel.getTrailingText());
            row.addView(valueView, trailingLayoutParams(dp(10)));
        }
        ImageView chevronView = null;
        if (rowModel.shouldShowChevron()) {
            chevronView = buildChevronView();
            row.addView(chevronView);
        }
        bindActionRowState(row, titleView, summaryView, chevronView, rowModel.isEnabled());
        if (valueView != null) {
            valueView.setAlpha(rowModel.isEnabled() ? 1f : DISABLED_CONTENT_ALPHA);
        }
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("handle navigation row", rowModel.getAction());
            }
        });
        return row;
    }

    private View createSplitNavigationRow(GboardPatchesSettingsContract.NavigationRow rowModel,
            GboardPatchesSettingsContract.ToggleRow entryToggle) {
        LinearLayout row = buildBaseRow();
        row.setPadding(0, 0, 0, 0);

        LinearLayout navigationPart = new LinearLayout(this);
        navigationPart.setOrientation(LinearLayout.HORIZONTAL);
        navigationPart.setGravity(Gravity.CENTER_VERTICAL);
        navigationPart.setPadding(dp(16), dp(16), 0, dp(16));
        navigationPart.setMinimumHeight(dp(72));
        navigationPart.setClickable(true);
        navigationPart.setFocusable(true);
        navigationPart.setBackground(buildRippleDrawable(0));
        LinearLayout.LayoutParams navigationParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        navigationParams.gravity = Gravity.CENTER_VERTICAL;
        navigationPart.setLayoutParams(navigationParams);
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        navigationPart.addView(textColumn);
        navigationPart.setOnClickListener(view -> {
            if (rowModel.isEnabled()) {
                runSafely("navigate to feature", rowModel.getAction());
            }
        });
        row.addView(navigationPart);

        View divider = new View(this);
        divider.setBackgroundColor(blendAlpha(palette.textSecondary, 0.3f));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(36));
        dividerParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(divider, dividerParams);

        LinearLayout switchPart = new LinearLayout(this);
        switchPart.setOrientation(LinearLayout.HORIZONTAL);
        switchPart.setGravity(Gravity.CENTER);
        switchPart.setPadding(dp(14), 0, dp(16), 0);
        switchPart.setMinimumHeight(dp(72));
        switchPart.setClickable(true);
        switchPart.setFocusable(true);
        switchPart.setBackground(buildRippleDrawable(0));
        switchPart.setContentDescription(entryToggle.getTitle());
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.gravity = Gravity.CENTER_VERTICAL;
        switchPart.setLayoutParams(switchParams);

        Switch switchView = new Switch(this);
        applyMaterialSwitchStyle(switchView);
        switchView.setChecked(entryToggle.isChecked());
        switchView.setEnabled(entryToggle.isEnabled());
        switchView.setAlpha(entryToggle.isEnabled() ? 1f : DISABLED_CONTENT_ALPHA);
        switchView.setFocusable(false);
        switchView.setClickable(false);
        switchPart.addView(switchView);
        switchPart.setOnClickListener(view -> {
            if (entryToggle.isEnabled()) {
                switchView.toggle();
                runToggleInBackground(entryToggle, switchView.isChecked(),
                        "toggle feature entry switch");
            }
        });
        row.addView(switchPart);
        return row;
    }

    private View createSelectorRow(GboardPatchesSettingsContract.SelectorRow rowModel) {
        LinearLayout row = buildBaseRow();
        addLeadingIcon(row, rowModel);
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        addPreviewBelowSummary(textColumn, rowModel);
        row.addView(textColumn);
        TextView valueView = null;
        if (rowModel.getCurrentValue() != null && !rowModel.getCurrentValue().isEmpty()) {
            valueView = buildTrailingValue(rowModel.getCurrentValue());
            row.addView(valueView, trailingLayoutParams(dp(8)));
        }
        ImageView chevronView = buildChevronView();
        row.addView(chevronView);
        bindActionRowState(row, titleView, summaryView, chevronView, rowModel.isEnabled());
        if (valueView != null) {
            valueView.setAlpha(rowModel.isEnabled() ? 1f : DISABLED_CONTENT_ALPHA);
        }
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("handle selector row", rowModel.getAction());
            }
        });
        return row;
    }

    private View createDetailRow(GboardPatchesSettingsContract.DetailRow rowModel) {
        LinearLayout row = buildDetailRowContainer();
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), rowModel.isMonospace());
        row.addView(titleView);
        row.addView(summaryView);
        titleView.setTextColor(primaryTextColor(rowModel.isEnabled()));
        summaryView.setTextColor(secondaryTextColor(rowModel.isEnabled()));
        row.setEnabled(rowModel.isEnabled());
        return row;
    }

    private View createCommandRow(GboardPatchesSettingsContract.CommandRow rowModel) {
        LinearLayout row = buildBaseRow();
        addLeadingIcon(row, rowModel);
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        addPreviewBelowSummary(textColumn, rowModel);
        row.addView(textColumn);
        bindActionRowState(row, titleView, summaryView, null, rowModel.isEnabled());
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("handle command row", rowModel.getAction());
            }
        });
        return row;
    }

    private View createDangerRow(GboardPatchesSettingsContract.DangerRow rowModel) {
        View row = createCommandRow(rowModel);
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                showConfirmDialog(
                        rowModel.getConfirmTitle(),
                        rowModel.getConfirmMessage(),
                        rowModel.getAction());
            }
        });
        return row;
    }

    private View createStatusBlockView(GboardPatchesSettingsContract.StatusBlock block) {
        LinearLayout card = buildStatusCard(block.getTone());
        card.addView(buildStatusTitle(block.getTitle(), block.getTone()));
        card.addView(buildStatusSummary(block.getSummary(), block.getTone()));
        return card;
    }

    private View createSectionView(GboardPatchesSettingsContract.Section section) {
        if (section.getStyle() == GboardPatchesSettingsContract.SectionStyle.FULL_BLEED) {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (section.getTitle() != null && !section.getTitle().isEmpty()) {
                container.addView(buildSectionTitle(section.getTitle()));
            }
            if (section.getDescription() != null && !section.getDescription().isEmpty()) {
                container.addView(buildSectionSummary(section.getDescription(), false));
            }
            for (GboardPatchesSettingsContract.Row item : section.getItems()) {
                container.addView(createRowView(item));
            }
            return container;
        }
        boolean advanced =
                section.getStyle() == GboardPatchesSettingsContract.SectionStyle.ADVANCED;
        LinearLayout container = buildSectionColumn();
        if (section.getTitle() != null && !section.getTitle().isEmpty()) {
            container.addView(buildSectionTitle(section.getTitle()));
        }
        if (section.getDescription() != null && !section.getDescription().isEmpty()) {
            container.addView(buildSectionSummary(section.getDescription(), false));
        }
        List<GboardPatchesSettingsContract.Row> items = section.getItems();
        if (items.isEmpty()) {
            return container;
        }
        LinearLayout list = buildSegmentList();
        int count = items.size();
        for (int index = 0; index < count; index++) {
            GboardPatchesSettingsContract.Row item = items.get(index);
            View rowView = createRowView(item);
            if (item instanceof GboardPatchesSettingsContract.CustomViewRow) {
                applySegmentGap(rowView, index);
            } else {
                applySegmentPosition(rowView, index, count, advanced);
            }
            if (item instanceof GboardPatchesSettingsContract.SearchRow) applySearchFieldStyle(rowView);
            list.addView(rowView);
        }
        container.addView(list);
        return container;
    }

    private void runToggleInBackground(GboardPatchesSettingsContract.Row rowModel,
            boolean checked, String operationName) {
        GboardPatchesSettingsContract.ToggleAction action = toggleActionOf(rowModel);
        if (action == null) {
            return;
        }
        try {
            backgroundStateExecutor.execute(() -> {
                try {
                    action.accept(checked);
                } catch (Throwable throwable) {
                    Log.w(TAG, "Failed to " + operationName, throwable);
                }
                postToDecorView(this::renderCurrentScreenSafely);
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private static GboardPatchesSettingsContract.ToggleAction toggleActionOf(
            GboardPatchesSettingsContract.Row row) {
        if (row instanceof GboardPatchesSettingsContract.ToggleRow toggleRow) {
            return toggleRow.getToggleAction();
        }
        if (row instanceof GboardPatchesSettingsContract.SwitchRow switchRow) {
            return switchRow.getToggleAction();
        }
        return null;
    }

    private static boolean isRowChecked(GboardPatchesSettingsContract.Row row) {
        if (row instanceof GboardPatchesSettingsContract.ToggleRow toggleRow) {
            return toggleRow.isChecked();
        }
        if (row instanceof GboardPatchesSettingsContract.SwitchRow switchRow) {
            return switchRow.isChecked();
        }
        return false;
    }

    private void bindSwitchRowState(LinearLayout row, TextView titleView, TextView summaryView,
            Switch switchView, boolean enabled) {
        row.setEnabled(enabled);
        row.setClickable(enabled);
        switchView.setEnabled(enabled);
        switchView.setAlpha(enabled ? 1f : DISABLED_CONTENT_ALPHA);
        titleView.setTextColor(primaryTextColor(enabled));
        summaryView.setTextColor(secondaryTextColor(enabled));
        row.setAlpha(1f);
    }

    private void bindActionRowState(LinearLayout row, TextView titleView, TextView summaryView,
            View chevronView, boolean enabled) {
        row.setEnabled(enabled);
        row.setClickable(enabled);
        titleView.setTextColor(primaryTextColor(enabled));
        summaryView.setTextColor(secondaryTextColor(enabled));
        if (chevronView != null) {
            chevronView.setAlpha(enabled ? 0.7f : DISABLED_CONTENT_ALPHA);
        }
        row.setAlpha(1f);
    }

    private int primaryTextColor(boolean enabled) {
        return enabled
                ? palette.textPrimary
                : blendAlpha(palette.textPrimary, DISABLED_CONTENT_ALPHA);
    }

    private int secondaryTextColor(boolean enabled) {
        return enabled
                ? summaryTextColor()
                : blendAlpha(summaryTextColor(), DISABLED_CONTENT_ALPHA);
    }

    private int summaryTextColor() {
        return blendColors(palette.textSecondary, palette.textTertiary, 0.5f);
    }

    private void applyMaterialSwitchStyle(Switch switchView) {
        applySwitchDrawables(switchView, new GboardPatchesSwitchDrawables.Colors(
                palette.accent,
                blendColors(palette.surface, palette.textSecondary, 0.18f),
                palette.textSecondary,
                palette.onAccent,
                palette.textSecondary,
                palette.accent));
    }

    private void applyMasterSwitchStyle(Switch switchView) {
        applySwitchDrawables(switchView, new GboardPatchesSwitchDrawables.Colors(
                palette.masterSwitchTrackOn,
                palette.masterSwitchTrackOff,
                palette.masterSwitchOutlineOff,
                palette.masterSwitchThumbOn,
                palette.masterSwitchOutlineOff,
                palette.masterSwitchTrackOn));
    }

    private void applySwitchDrawables(Switch switchView,
            GboardPatchesSwitchDrawables.Colors colors) {
        float density = getResources().getDisplayMetrics().density;
        switchView.setShowText(false);
        switchView.setSwitchMinWidth(dp(GboardPatchesSwitchDrawables.TRACK_WIDTH_DP));
        switchView.setThumbDrawable(new GboardPatchesSwitchDrawables.ThumbDrawable(
                density, colors));
        switchView.setTrackDrawable(new GboardPatchesSwitchDrawables.TrackDrawable(
                density, colors));
    }

    private void runSafely(String operationName, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to " + operationName, throwable);
            renderCurrentScreenSafely();
        }
    }

    private LinearLayout buildBaseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setBackground(buildSegmentBackground(0, 1, false));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout buildDetailRowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setBackground(buildSegmentBackground(0, 1, false));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout buildSectionColumn() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        container.setLayoutParams(params);
        return container;
    }

    private LinearLayout buildSegmentList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return list;
    }

    private void applySegmentPosition(View rowView, int index, int count, boolean advanced) {
        rowView.setBackground(buildSegmentBackground(index, count, advanced));
        applySegmentGap(rowView, index);
    }

    private void applySegmentGap(View rowView, int index) {
        ViewGroup.LayoutParams existing = rowView.getLayoutParams();
        LinearLayout.LayoutParams params = existing instanceof LinearLayout.LayoutParams linear
                ? linear
                : new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = index == 0 ? 0 : dp(SEGMENT_GAP_DP);
        rowView.setLayoutParams(params);
    }

    private void applySearchFieldStyle(View rowView) {
        GradientDrawable field = new GradientDrawable();
        field.setColor(palette.accentContainer);
        field.setCornerRadius(dp(28));
        field.setStroke(dp(1), blendAlpha(palette.accent, 0.6f));
        rowView.setBackground(new RippleDrawable(
                ColorStateList.valueOf(blendAlpha(palette.accent, RIPPLE_ACCENT_ALPHA)), field, null));
    }

    private Drawable buildSegmentBackground(int index, int count, boolean advanced) {
        int baseColor = advanced ? palette.advancedContainer : palette.surface;
        SegmentShapeDrawable shape = new SegmentShapeDrawable(
                baseColor,
                blendColors(baseColor, palette.accent, PRESSED_ACCENT_FRACTION),
                segmentRadii(index, count),
                dp(SEGMENT_OUTER_RADIUS_DP),
                SEGMENT_PRESS_ANIMATION_MS);
        return new RippleDrawable(
                ColorStateList.valueOf(blendAlpha(palette.accent, RIPPLE_ACCENT_ALPHA)),
                shape,
                null);
    }

    private float[] segmentRadii(int index, int count) {
        float outer = dp(SEGMENT_OUTER_RADIUS_DP);
        float inner = dp(SEGMENT_INNER_RADIUS_DP);
        float top = index <= 0 ? outer : inner;
        float bottom = index >= count - 1 ? outer : inner;
        return new float[] { top, top, top, top, bottom, bottom, bottom, bottom };
    }

    private LinearLayout buildRowTextColumn(boolean trailingSpacing) {
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textColumn.setLayoutParams(textParams);
        textColumn.setPadding(0, 0, trailingSpacing ? dp(16) : 0, 0);
        return textColumn;
    }

    private TextView buildRowTitle(CharSequence text) {
        TextView titleView = new TextView(this);
        titleView.setTextColor(palette.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TITLE_SP);
        titleView.setTypeface(mediumTypeface());
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setText(text);
        return titleView;
    }

    private TextView buildRowSummary(String text, boolean monospace) {
        TextView summaryView = new TextView(this);
        summaryView.setTextColor(summaryTextColor());
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_SUMMARY_SP);
        summaryView.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        summaryView.setLayoutParams(params);
        summaryView.setText(text);
        summaryView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        if (monospace || isLikelyMonospace(text)) {
            summaryView.setTypeface(Typeface.MONOSPACE);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            summaryView.setLineSpacing(0f, 1.0f);
        }
        return summaryView;
    }

    private TextView buildSectionTitle(String text) {
        TextView titleView = new TextView(this);
        titleView.setText(text);
        titleView.setTextColor(palette.accent);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        titleView.setTypeface(mediumTypeface());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        params.bottomMargin = dp(8);
        titleView.setLayoutParams(params);
        return titleView;
    }

    private TextView buildSectionSummary(String text, boolean monospace) {
        TextView summaryView = new TextView(this);
        summaryView.setText(text);
        summaryView.setTextColor(palette.textSecondary);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        params.bottomMargin = dp(10);
        summaryView.setLayoutParams(params);
        summaryView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        if (monospace || isLikelyMonospace(text)) {
            summaryView.setTypeface(Typeface.MONOSPACE);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        }
        return summaryView;
    }

    private LinearLayout buildStatusCard(GboardPatchesSettingsContract.StatusTone tone) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(toneBackground(tone));
        background.setCornerRadius(dp(SEGMENT_OUTER_RADIUS_DP));
        card.setBackground(background);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private TextView buildStatusTitle(String text, GboardPatchesSettingsContract.StatusTone tone) {
        TextView titleView = new TextView(this);
        titleView.setText(text);
        titleView.setTextColor(toneTitleColor(tone));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(mediumTypeface());
        titleView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        return titleView;
    }

    private TextView buildStatusSummary(String text,
            GboardPatchesSettingsContract.StatusTone tone) {
        TextView summaryView = new TextView(this);
        summaryView.setText(text);
        summaryView.setTextColor(toneSummaryColor(tone));
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        summaryView.setLayoutParams(params);
        summaryView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        if (isLikelyMonospace(text)) {
            summaryView.setTypeface(Typeface.MONOSPACE);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        }
        return summaryView;
    }

    private void applyLargeTitleText(TextView titleView, CharSequence text) {
        CharSequence resolved = text == null ? "" : text;
        titleView.setText(resolved);
        int length = Character.codePointCount(resolved, 0, resolved.length());
        boolean longTitle = length > LARGE_TITLE_LONG_THRESHOLD;
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                longTitle ? LARGE_TITLE_LONG_SP : LARGE_TITLE_SP);
        titleView.setLineSpacing(0f, longTitle ? 1.1f : 1.0f);
    }

    private TextView buildTrailingValue(String text) {
        TextView valueView = new TextView(this);
        valueView.setText(text);
        valueView.setTextColor(palette.accent);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        valueView.setTypeface(mediumTypeface());
        valueView.setGravity(Gravity.CENTER_VERTICAL);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setMaxWidth(dp(160));
        return valueView;
    }

    private void addLeadingIcon(LinearLayout row, GboardPatchesSettingsContract.Row rowModel) {
        GboardPatchesSettingsContract.RowDecoration decoration = rowModel.getDecoration();
        if (decoration == null || decoration.getLeadingIcon() == GboardPatchesIcons.NONE) {
            return;
        }
        View icon = buildLeadingIcon(decoration.getLeadingIcon(), decoration.getLeadingTone());
        if (icon != null) {
            row.addView(icon);
        }
    }

    private View buildLeadingIcon(int icon, String tone) {
        Drawable drawable = createIcon(icon, palette.toneOn(tone));
        if (drawable == null) {
            return null;
        }
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        frameParams.gravity = Gravity.CENTER_VERTICAL;
        frameParams.rightMargin = dp(16);
        frame.setLayoutParams(frameParams);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(palette.toneContainer(tone));
        frame.setBackground(background);
        frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(drawable);
        frame.addView(iconView, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        return frame;
    }

    private Drawable createIcon(int icon, int color) {
        return GboardPatchesIcons.create(icon, color, getResources().getDisplayMetrics().density);
    }

    private ImageView buildChevronView() {
        ImageView chevronView = new ImageView(this);
        chevronView.setImageDrawable(
                createIcon(GboardPatchesIcons.CHEVRON_RIGHT, palette.textSecondary));
        chevronView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        chevronView.setAlpha(0.7f);
        chevronView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(20), dp(20));
        params.gravity = Gravity.CENTER_VERTICAL;
        chevronView.setLayoutParams(params);
        return chevronView;
    }

    private void addPreviewBelowSummary(LinearLayout textColumn,
            GboardPatchesSettingsContract.Row rowModel) {
        View previewButton = buildPreviewButton(rowModel);
        if (previewButton != null) {
            textColumn.addView(previewButton);
        }
    }

    private View buildPreviewButton(GboardPatchesSettingsContract.Row rowModel) {
        GboardPatchesSettingsContract.PreviewSpec previewSpec = rowModel.getPreviewSpec();
        if (previewSpec == null) {
            return null;
        }
        LinearLayout previewButton = new LinearLayout(this);
        previewButton.setOrientation(LinearLayout.HORIZONTAL);
        previewButton.setGravity(Gravity.CENTER_VERTICAL);
        previewButton.setClickable(true);
        previewButton.setFocusable(true);
        previewButton.setPadding(dp(8), 0, dp(12), 0);
        previewButton.setMinimumHeight(dp(32));
        GradientDrawable background = new GradientDrawable();
        background.setColor(blendAlpha(palette.accent, 0.13f));
        background.setCornerRadius(dp(16));
        previewButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(palette.pressedOverlay), background, null));

        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(createIcon(
                previewSpec.hasVideo() ? GboardPatchesIcons.PLAY : GboardPatchesIcons.IMAGE,
                palette.accent));
        iconView.setImageTintList(ColorStateList.valueOf(palette.accent));
        previewButton.addView(iconView, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView labelView = new TextView(this);
        labelView.setText(text(R.string.gboard_patches_preview_label));
        labelView.setTextColor(palette.accent);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        labelView.setTypeface(mediumTypeface());
        labelView.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(4);
        previewButton.addView(labelView, labelParams);

        previewButton.setContentDescription(GboardSettingsText.format(this,
                R.string.gboard_patches_preview_row_description, rowModel.getTitle()));
        previewButton.setOnClickListener(view -> runSafely("show row preview",
                () -> showPreviewSheet(rowModel, previewSpec)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        buttonParams.topMargin = dp(10);
        buttonParams.gravity = Gravity.START;
        previewButton.setLayoutParams(buttonParams);
        return previewButton;
    }

    private LinearLayout.LayoutParams trailingLayoutParams(int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = rightMargin;
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    private boolean isLikelyMonospace(String summaryText) {
        return summaryText != null
                && summaryText.contains("\n")
                && (summaryText.contains("|--")
                || summaryText.contains("`--")
                || summaryText.contains("├─")
                || summaryText.contains("└─")
                || summaryText.contains("\n  |")
                || summaryText.startsWith("[PC]")
                || summaryText.startsWith("[This device]")
                || summaryText.startsWith("http://")
                || summaryText.startsWith("https://"));
    }

    private int toneBackground(GboardPatchesSettingsContract.StatusTone tone) {
        if (tone == GboardPatchesSettingsContract.StatusTone.WARNING) {
            return palette.warningContainer;
        }
        if (tone == GboardPatchesSettingsContract.StatusTone.INFO) {
            return palette.accentContainer;
        }
        return palette.surface;
    }

    private int toneTitleColor(GboardPatchesSettingsContract.StatusTone tone) {
        if (tone == GboardPatchesSettingsContract.StatusTone.WARNING) {
            return palette.warning;
        }
        if (tone == GboardPatchesSettingsContract.StatusTone.INFO) {
            return palette.onAccentContainer;
        }
        return palette.textPrimary;
    }

    private int toneSummaryColor(GboardPatchesSettingsContract.StatusTone tone) {
        if (tone == GboardPatchesSettingsContract.StatusTone.WARNING) {
            return palette.warning;
        }
        if (tone == GboardPatchesSettingsContract.StatusTone.INFO) {
            return palette.onAccentContainer;
        }
        return palette.textSecondary;
    }

    private static Typeface mediumTypeface() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private static int blendColors(int base, int overlay, float overlayFraction) {
        return GboardPatchesSwitchDrawables.blend(base, overlay, overlayFraction);
    }

    private void showConfirmDialog(String title, String message, Runnable confirmAction) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(text(R.string.gboard_patches_confirm_action),
                        (dialogInterface, which) ->
                        runSafely("confirm settings action", confirmAction))
                .setNegativeButton(
                        text(R.string.gboard_patches_dialog_cancel),
                        null)
                .create();
        dialog.setOnDismissListener(ignored -> onManagedDialogDismissed());
        try {
            dialog.show();
            onManagedDialogShown();
            tintDialogButtons(dialog);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show confirmation dialog", throwable);
        }
    }

    private void tintDialogButtons(AlertDialog dialog) {
        TextView positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(palette.accent);
        }
        TextView negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negativeButton != null) {
            negativeButton.setTextColor(palette.accent);
        }
        TextView neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutralButton != null) {
            neutralButton.setTextColor(palette.accent);
        }
    }

    private int parsePositiveInteger(String value) {
        if (value == null) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int resolveCheckedIndex(String[] values, String currentValue) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentValue)) {
                return i;
            }
        }
        return -1;
    }

    private Drawable buildRippleDrawable(int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusDp);
        return new RippleDrawable(
                ColorStateList.valueOf(palette.pressedOverlay),
                null,
                mask);
    }

    private int blendAlpha(int color, float alphaFraction) {
        int alpha = Math.round(Color.alpha(color) * alphaFraction);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private String text(int resId) {
        return GboardSettingsText.get(this, resId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PendingTextDocumentWrite {
        final String text;
        final Runnable completionAction;

        PendingTextDocumentWrite(String text, Runnable completionAction) {
            this.text = text;
            this.completionAction = completionAction;
        }
    }

    private static final class PendingBinaryDocumentWrite {
        final byte[] data;
        final Runnable completionAction;

        PendingBinaryDocumentWrite(byte[] data, Runnable completionAction) {
            this.data = data;
            this.completionAction = completionAction;
        }
    }

    private static final class Palette {
        final boolean nightMode;
        final int windowBackground;
        final int surface;
        final int surfaceAlt;
        final int surfaceStroke;
        final int infoContainer;
        final int warning;
        final int warningContainer;
        final int advancedContainer;
        final int danger;
        final int textPrimary;
        final int textSecondary;
        final int textTertiary;
        final int textDisabled;
        final int accent;
        final int onAccent;
        final int accentContainer;
        final int onAccentContainer;
        final int pressedOverlay;
        final int mediaBackground;
        final int masterSwitchContainer;
        final int onMasterSwitchContainer;
        final int masterSwitchTrackOn;
        final int masterSwitchThumbOn;
        final int masterSwitchTrackOff;
        final int masterSwitchOutlineOff;
        final int previewCard;
        final int previewMediaStroke;
        final int restartPending;
        final int onRestartPending;

        private Palette(boolean nightMode) {
            this.nightMode = nightMode;
            windowBackground = pick("#FFEEF1F7", "#FF0F1318");
            surface = pick("#FFFFFFFF", "#FF1D2126");
            surfaceAlt = pick("#FFE3E7EE", "#FF2A2E34");
            surfaceStroke = pick("#1F44474E", "#1FE2E2E6");
            textPrimary = pick("#FF191C20", "#FFE2E2E6");
            textSecondary = pick("#FF44474E", "#FFC4C6D0");
            textTertiary = pick("#FF74777F", "#FF8E9099");
            textDisabled = textTertiary;
            accent = pick("#FF0B57D0", "#FFA8C7FA");
            onAccent = pick("#FFFFFFFF", "#FF062E6F");
            accentContainer = pick("#FFB6CCFE", "#FF435174");
            onAccentContainer = pick("#FF132A56", "#FFD6E2FF");
            infoContainer = accentContainer;
            pressedOverlay = pick("#170B57D0", "#17A8C7FA");
            warning = pick("#FF8C5000", "#FFFFB95C");
            warningContainer = pick("#FFFFDDB3", "#FF4A2800");
            danger = pick("#FFB3261E", "#FFF2B8B5");
            mediaBackground = pick("#FF101114", "#FF050506");
            advancedContainer = pick("#FFF7F8FB", "#FF191D22");
            masterSwitchContainer = pick("#FFB6CCFE", "#FF435174");
            onMasterSwitchContainer = pick("#FF132A56", "#FFD6E2FF");
            masterSwitchTrackOn = pick("#FF475D92", "#FFB7C6EF");
            masterSwitchThumbOn = pick("#FFFFFFFF", "#FF304061");
            masterSwitchTrackOff = pick("#FFE0E2EE", "#FF2E3A57");
            masterSwitchOutlineOff = pick("#FF787B84", "#FF8E93A6");
            previewCard = pick("#FFEEF1F7", "#FF2A2E35");
            previewMediaStroke = pick("#24000000", "#33FFFFFF");
            restartPending = pick("#FFC55300", "#FFFFB874");
            onRestartPending = pick("#FFFFFFFF", "#FF4A2800");
        }

        static Palette forConfiguration(Configuration configuration) {
            boolean nightMode = configuration != null
                    && (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            return new Palette(nightMode);
        }

        int toneContainer(String tone) {
            String[] colors = toneColors(tone);
            return Color.parseColor(nightMode ? colors[2] : colors[0]);
        }

        int toneOn(String tone) {
            String[] colors = toneColors(tone);
            return Color.parseColor(nightMode ? colors[3] : colors[1]);
        }

        private int pick(String light, String dark) {
            return Color.parseColor(nightMode ? dark : light);
        }

        private static String[] toneColors(String tone) {
            String key = tone == null ? "" : tone.toLowerCase(Locale.ROOT);
            switch (key) {
                case "blue":
                    return new String[] { "#FFD3E3FD", "#FF0842A0", "#FF0842A0", "#FFD3E3FD" };
                case "violet":
                    return new String[] { "#FFE9DDFF", "#FF4F378B", "#FF4F378B", "#FFE9DDFF" };
                case "green":
                    return new String[] { "#FFC4EED0", "#FF0F5223", "#FF0F5223", "#FFC4EED0" };
                case "amber":
                    return new String[] { "#FFFFDF99", "#FF5C4200", "#FF5C4200", "#FFFFDF99" };
                case "teal":
                    return new String[] { "#FFB3EBF2", "#FF004F58", "#FF004F58", "#FFB3EBF2" };
                case "pink":
                    return new String[] { "#FFFFD8E4", "#FF7D1D3F", "#FF7D1D3F", "#FFFFD8E4" };
                case "red":
                    return new String[] { "#FFFFDAD6", "#FF8C1D18", "#FF8C1D18", "#FFFFDAD6" };
                default:
                    return new String[] { "#FFDEE3EB", "#FF2F3A44", "#FF333B44", "#FFDEE3EB" };
            }
        }
    }

    private static final class SegmentShapeDrawable extends GradientDrawable {
        private final float[] baseRadii;
        private final float[] currentRadii = new float[8];
        private final float pressedRadius;
        private final int baseColor;
        private final int pressedColor;
        private final long durationMs;
        private ValueAnimator animator;
        private float progress;
        private boolean pressed;

        SegmentShapeDrawable(int baseColor, int pressedColor, float[] baseRadii,
                float pressedRadius, long durationMs) {
            this.baseColor = baseColor;
            this.pressedColor = pressedColor;
            this.baseRadii = baseRadii.clone();
            this.pressedRadius = pressedRadius;
            this.durationMs = durationMs;
            setShape(GradientDrawable.RECTANGLE);
            applyProgress(0f);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            super.onStateChange(state);
            boolean nextPressed = false;
            boolean enabled = false;
            if (state != null) {
                for (int value : state) {
                    if (value == android.R.attr.state_pressed) {
                        nextPressed = true;
                    } else if (value == android.R.attr.state_enabled) {
                        enabled = true;
                    }
                }
            }
            nextPressed = nextPressed && enabled;
            if (nextPressed == pressed) {
                return false;
            }
            pressed = nextPressed;
            animateTo(pressed ? 1f : 0f);
            return true;
        }

        private void animateTo(float target) {
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(durationMs);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation ->
                    applyProgress((float) animation.getAnimatedValue()));
            animator.start();
        }

        private void applyProgress(float value) {
            progress = value;
            for (int index = 0; index < currentRadii.length; index++) {
                currentRadii[index] = baseRadii[index]
                        + ((pressedRadius - baseRadii[index]) * value);
            }
            setCornerRadii(currentRadii.clone());
            setColor(blendColors(baseColor, pressedColor, value));
        }
    }

    private static final class AspectFitImageView extends ImageView {
        private final int maxHeightPx;

        AspectFitImageView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            Drawable drawable = getDrawable();
            int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
            if (drawable == null
                    || drawable.getIntrinsicWidth() <= 0
                    || drawable.getIntrinsicHeight() <= 0
                    || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                    || availableWidth <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            float ratio = (float) drawable.getIntrinsicHeight() / drawable.getIntrinsicWidth();
            int width = availableWidth;
            int height = Math.round(width * ratio);
            if (maxHeightPx > 0 && height > maxHeightPx) {
                height = maxHeightPx;
                width = Math.max(1, Math.round(height / ratio));
            }
            setMeasuredDimension(width, height);
        }
    }
}
