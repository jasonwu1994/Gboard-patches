package dev.jason.gboardpatches.extension.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
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
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import dev.jason.gboardpatches.extension.clipboard.GboardClipboardSettingsFeature;

public final class GboardPatchesSettingsActivity extends Activity
        implements GboardPatchesSettingsContract.Host {
    private static final String TAG = "GboardPatches";
    private static final String ACTION_QS_TILE_PREFERENCES =
            "android.service.quicksettings.action.QS_TILE_PREFERENCES";
    private static final String EXTRA_OPEN_WEB_CLIPBOARD =
            "dev.jason.gboardpatches.extension.extra.OPEN_WEB_CLIPBOARD";
    private static final String ENTER_PREF_HEADER_EXTRA = "ENTER_PREF_HEADER";
    private static final String GBOARD_SETTINGS_ACTIVITY_CLASS =
            "com.google.android.apps.inputmethod.latin.preference.SettingsActivity";
    private static final String SUPPORTED_DEVELOPER_OPTIONS_TARGET_VERSION =
            "17.7.7.932364120-release-arm64-v8a";
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
    private static final int REQUEST_CREATE_TEXT_DOCUMENT = 0x4742;
    private static final int REQUEST_OPEN_TEXT_DOCUMENT = 0x4743;
    private static final int TOOLBAR_HEIGHT_DP = 56;
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
    private TextView headerBadgeView;
    private TextView headerTitleView;
    private TextView headerSummaryView;
    private LinearLayout panelContainer;
    private ScrollView contentScrollView;
    private LinearLayout contentColumn;
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
        initialFeatureFromIntentHandled = false;
        scheduleDeferredRender();
    }

    public static Intent createWebClipboardSettingsIntent(Context context) {
        Intent intent = new Intent(context, GboardPatchesSettingsActivity.class);
        intent.putExtra(EXTRA_OPEN_WEB_CLIPBOARD, true);
        return intent;
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.resume()));
    }

    @Override
    protected void onPause() {
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.pause()));
        cancelDeferredRender();
        cancelOfflineSpeechLanguageQuery();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        applyOrchestration(settingsOrchestrator.accept(
                GboardPatchesSettingsOrchestrator.Event.pause()));
        cancelDeferredRender();
        cancelOfflineSpeechLanguageQuery();
        unregisterBackCallback();
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
        for (String packageName : targetSettingsPackages()) {
            if (tryLaunchTargetSettingsHeader(packageName, headerKeyResourceId)) {
                return;
            }
        }
        throw new ActivityNotFoundException(
                "No supported Gboard Developer options entry is available");
    }

    private boolean tryLaunchTargetSettingsHeader(String packageName, int headerKeyResourceId) {
        String targetVersionName = targetPackageVersionName(packageName);
        if (!isSupportedDeveloperOptionsTargetVersion(targetVersionName)) {
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
        if (isSupportedTargetPackage(currentPackage)) {
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checkedIndex, (dialogInterface, which) -> {
                    if (which < 0 || which >= values.length) {
                        return;
                    }
                    String selectedValue = values[which];
                    pendingSelectionAction[0] = () -> runSafely(
                            "handle choice dialog selection",
                            () -> {
                                if (customValue.equals(selectedValue)) {
                                    customAction.run();
                                } else {
                                    valueConsumer.accept(selectedValue);
                                    renderCurrentScreenSafely();
                                }
                            });
                    dialogInterface.dismiss();
                })
                .setNegativeButton(text(R.string.gboard_patches_dialog_cancel), null)
                .create();
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
            tintDialogButtons(dialog);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show choice dialog", throwable);
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
    public void showCodeEditorDialog(String title, String hint, String initialValue,
            GboardPatchesSettingsContract.TextValueConsumer consumer) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(hint);
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelectAllOnFocus(false);
        input.setGravity(Gravity.TOP);
        input.setBackground(null);
        input.setPadding(0, 0, 0, 0);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        input.setTypeface(Typeface.MONOSPACE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        LinearLayout inputWrapper = new LinearLayout(this);
        inputWrapper.setPadding(dp(12), dp(12), dp(12), dp(12));
        inputWrapper.setBackground(buildCardDrawable(palette.surfaceAlt, palette.surfaceStroke, dp(12)));
        inputWrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(480)));

        container.addView(inputWrapper);

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
                    Log.w(TAG, "Failed to persist code setting", throwable);
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
            Log.w(TAG, "Failed to show code editor dialog", throwable);
        }
    }

    @Override
    public void showPreviewDialog(GboardPatchesSettingsContract.PreviewSpec previewSpec) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        String message = previewSpec.getMessage();
        if (message != null && !message.isEmpty()) {
            TextView messageView = new TextView(this);
            messageView.setText(message);
            messageView.setTextColor(palette.textSecondary);
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            messageView.setLineSpacing(0f, 1.1f);
            container.addView(messageView);
        }

        List<GboardPatchesSettingsContract.PreviewMedia> previewMedia =
                previewSpec.getMediaItems();
        List<VideoView> previewVideoViews = new ArrayList<VideoView>();
        boolean sideBySide = previewSpec.getLayout()
                == GboardPatchesSettingsContract.PreviewLayout.SIDE_BY_SIDE;
        if (!sideBySide || previewMedia.size() <= 1) {
            boolean firstImage = true;
            for (GboardPatchesSettingsContract.PreviewMedia previewItem : previewMedia) {
                View imageCard = buildPreviewMediaCard(
                        previewSpec.getTitle(),
                        previewItem,
                        false,
                        previewVideoViews);
                LinearLayout.LayoutParams imageCardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                imageCardParams.topMargin = firstImage ? dp(16) : dp(12);
                imageCard.setLayoutParams(imageCardParams);
                container.addView(imageCard);
                firstImage = false;
            }
        } else {
            for (int index = 0; index < previewMedia.size(); index += 2) {
                LinearLayout previewRow = new LinearLayout(this);
                previewRow.setOrientation(LinearLayout.HORIZONTAL);
                previewRow.setBaselineAligned(false);
                LinearLayout.LayoutParams previewRowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                previewRowParams.topMargin = index == 0 ? dp(16) : dp(12);
                previewRow.setLayoutParams(previewRowParams);

                View leftCard = buildPreviewMediaCard(
                        previewSpec.getTitle(),
                        previewMedia.get(index),
                        true,
                        previewVideoViews);
                LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
                leftParams.rightMargin = dp(6);
                previewRow.addView(leftCard, leftParams);

                if (index + 1 < previewMedia.size()) {
                    View rightCard = buildPreviewMediaCard(
                            previewSpec.getTitle(),
                            previewMedia.get(index + 1),
                            true,
                            previewVideoViews);
                    LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f);
                    rightParams.leftMargin = dp(6);
                    previewRow.addView(rightCard, rightParams);
                } else {
                    View spacer = new View(this);
                    previewRow.addView(spacer, new LinearLayout.LayoutParams(
                            0,
                            0,
                            1f));
                }

                container.addView(previewRow);
            }
        }
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(previewSpec.getTitle())
                .setView(scrollView)
                .setPositiveButton(text(R.string.gboard_patches_dialog_close), null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            for (VideoView previewVideoView : previewVideoViews) {
                try {
                    previewVideoView.stopPlayback();
                } catch (Throwable throwable) {
                    Log.w(TAG, "Failed to stop preview video playback", throwable);
                }
            }
            onManagedDialogDismissed();
        });
        try {
            dialog.show();
            onManagedDialogShown();
            tintDialogButtons(dialog);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to show preview dialog", throwable);
        }
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
            intent.putExtra(Intent.EXTRA_TITLE, fileName.trim());
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CREATE_TEXT_DOCUMENT) {
            handleCreateTextDocumentResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_OPEN_TEXT_DOCUMENT) {
            handleOpenTextDocumentResult(resultCode, data);
        }
    }

    private View buildPreviewMediaCard(String dialogTitle,
            GboardPatchesSettingsContract.PreviewMedia previewMedia, boolean compact,
            List<VideoView> previewVideoViews) {
        LinearLayout imageCard = new LinearLayout(this);
        imageCard.setOrientation(LinearLayout.VERTICAL);
        imageCard.setGravity(Gravity.CENTER_HORIZONTAL);
        imageCard.setBackground(buildCardDrawable(
                palette.surfaceAlt,
                palette.surfaceStroke,
                dp(24)));
        imageCard.setPadding(
                compact ? dp(10) : dp(16),
                compact ? dp(12) : dp(16),
                compact ? dp(10) : dp(16),
                compact ? dp(12) : dp(16));

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

        if (previewContent != null) {
            ViewGroup.LayoutParams previewLayoutParams = previewContent.getLayoutParams();
            if (previewLayoutParams != null) {
                imageCard.addView(previewContent, previewLayoutParams);
            } else {
                imageCard.addView(previewContent, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        } else {
            TextView errorView = new TextView(this);
            errorView.setText(text(R.string.gboard_patches_preview_load_failed));
            errorView.setTextColor(palette.textSecondary);
            errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            imageCard.addView(errorView);
        }

        String caption = previewMedia.getCaption();
        if (caption != null && !caption.isEmpty()) {
            TextView captionView = new TextView(this);
            captionView.setText(caption);
            captionView.setTextColor(palette.textPrimary);
            captionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 12f : 13f);
            captionView.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            captionParams.topMargin = compact ? dp(8) : dp(12);
            captionView.setLayoutParams(captionParams);
            captionView.setGravity(Gravity.CENTER_HORIZONTAL);
            imageCard.addView(captionView);
        }

        return imageCard;
    }

    private View buildPreviewImageView(String dialogTitle,
            GboardPatchesSettingsContract.PreviewImage previewImage, boolean compact) {
        Bitmap bitmap = decodePreviewBitmap(previewImage.getAssetPath());
        if (bitmap == null) {
            return null;
        }
        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setMaxHeight(compact ? dp(220) : dp(420));
        imageView.setImageBitmap(bitmap);
        imageView.setContentDescription(dialogTitle);
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
                compact ? dp(220) : dp(420)));

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
            if (containerWidth <= 0) {
                return;
            }
            int targetHeight = Math.round(
                    (float) containerWidth * ((float) videoHeight / (float) videoWidth));
            targetHeight = Math.max(minHeight, Math.min(maxHeight, targetHeight));
            ViewGroup.LayoutParams layoutParams = videoContainer.getLayoutParams();
            if (layoutParams == null || layoutParams.height == targetHeight) {
                return;
            }
            layoutParams.height = targetHeight;
            videoContainer.setLayoutParams(layoutParams);
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
        errorView.setTextColor(palette.textSecondary);
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
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || reader == null) {
            return;
        }
        try {
            reader.accept(readTextDocument(data.getData()));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to read selected document", throwable);
            Toast.makeText(this, DOCUMENT_READ_FAILED, Toast.LENGTH_SHORT).show();
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
    }

    private int resolveActivityTheme() {
        boolean nightMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return nightMode
                ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar;
    }

    private View buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.windowBackground);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildToolbar());

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
        contentColumn.setPadding(dp(16), dp(8), dp(16), dp(16));
        contentColumn.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        contentColumn.addView(buildHeaderCard());
        contentColumn.addView(buildPanelCard());

        contentScrollView.addView(contentColumn);
        root.addView(contentScrollView);
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
        toolbarTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        toolbarTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        toolbarTitleView.setSingleLine(true);
        toolbarTitleView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        toolbarView.addView(backButton);
        toolbarView.addView(toolbarTitleView);
        return toolbarView;
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

    private View buildHeaderCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(buildCardDrawable(palette.surfaceAlt, palette.surfaceStroke, dp(24)));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        headerBadgeView = new TextView(this);
        headerBadgeView.setAllCaps(true);
        headerBadgeView.setTextColor(palette.accent);
        headerBadgeView.setTypeface(Typeface.DEFAULT_BOLD);
        headerBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);

        headerTitleView = new TextView(this);
        headerTitleView.setTextColor(palette.textPrimary);
        headerTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        headerTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(8);
        headerTitleView.setLayoutParams(titleParams);

        headerSummaryView = new TextView(this);
        headerSummaryView.setTextColor(palette.textSecondary);
        headerSummaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        headerSummaryView.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(6);
        headerSummaryView.setLayoutParams(summaryParams);

        card.addView(headerBadgeView);
        card.addView(headerTitleView);
        card.addView(headerSummaryView);
        return card;
    }

    private View buildPanelCard() {
        panelContainer = new LinearLayout(this);
        panelContainer.setOrientation(LinearLayout.VERTICAL);
        panelContainer.setBackground(buildCardDrawable(
                palette.surface,
                palette.surfaceStroke,
                dp(28)));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.topMargin = dp(12);
        panelContainer.setLayoutParams(panelParams);
        panelContainer.setPadding(0, dp(6), 0, dp(6));
        return panelContainer;
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
            updateToolbarInsets(resolveTopInset(insets));
            if (contentScrollView != null) {
                contentScrollView.setPadding(0, 0, 0, resolveBottomInset(insets));
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
        return exitRequested;
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

    private void renderCurrentScreenSafely() {
        initializeFeaturesAndRenderSafely();
    }

    private void applyScreen(GboardPatchesSettingsContract.Screen screen) {
        toolbarTitleView.setText(screen.getToolbarTitle());
        headerBadgeView.setText(screen.getHeaderBadge());
        headerTitleView.setText(screen.getHeaderTitle());
        headerSummaryView.setText(screen.getHeaderSummary());
        headerSummaryView.setVisibility(
                TextUtils.isEmpty(screen.getHeaderSummary()) ? View.GONE : View.VISIBLE);
        applyPanelStyle(screen.getPanelStyle());
        panelContainer.removeAllViews();
        for (GboardPatchesSettingsContract.StatusBlock statusBlock : screen.getStatusBlocks()) {
            panelContainer.addView(createStatusBlockView(statusBlock));
        }
        for (GboardPatchesSettingsContract.Section section : screen.getSections()) {
            panelContainer.addView(createSectionView(section));
        }
        int requestedScrollY = consumeRequestedScrollPositionOnNextScreenApply();
        if (requestedScrollY >= 0) {
            scrollContentToPositionAfterLayout(requestedScrollY);
        }
    }

    private void applyPanelStyle(GboardPatchesSettingsContract.PanelStyle panelStyle) {
        if (panelContainer == null) {
            return;
        }
        GboardPatchesSettingsContract.PanelStyle resolvedStyle =
                panelStyle == null ? GboardPatchesSettingsContract.PanelStyle.CARD : panelStyle;
        if (resolvedStyle == GboardPatchesSettingsContract.PanelStyle.FLAT) {
            panelContainer.setBackground(null);
            panelContainer.setPadding(0, 0, 0, 0);
        } else {
            panelContainer.setBackground(buildCardDrawable(
                    palette.surface,
                    palette.surfaceStroke,
                    dp(28)));
            panelContainer.setPadding(0, dp(6), 0, dp(6));
        }
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
        boolean tilePreferencesIntent = ACTION_QS_TILE_PREFERENCES.equals(intent.getAction());
        boolean openWebClipboard = intent.getBooleanExtra(EXTRA_OPEN_WEB_CLIPBOARD, false);
        if (!tilePreferencesIntent && !openWebClipboard) {
            return false;
        }
        GboardClipboardSettingsFeature clipboardFeature = findClipboardFeature();
        if (clipboardFeature == null) {
            return false;
        }
        GboardPatchesSettingsContract.Feature webClipboardFeature =
                clipboardFeature.getWebClipboardFeature();
        runOnUiThread(() -> {
            List<GboardPatchesSettingsContract.Feature> featurePath =
                    Arrays.asList(clipboardFeature, webClipboardFeature);
            scrollState.resetForDirectPath(featurePath.size() - 1);
            requestScrollPositionOnNextScreenApply(0);
            applyOrchestration(settingsOrchestrator.accept(
                    GboardPatchesSettingsOrchestrator.Event.replacePath(featurePath)));
        });
        return true;
    }

    private GboardClipboardSettingsFeature findClipboardFeature() {
        for (GboardPatchesSettingsContract.Feature feature : features) {
            if (GboardClipboardSettingsFeature.class.isInstance(feature)) {
                return (GboardClipboardSettingsFeature) feature;
            }
        }
        return null;
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
        Log.e(TAG, reason, throwable);
        if (palette == null) {
            palette = Palette.forConfiguration(getResources().getConfiguration());
        }
        toolbarView = null;
        toolbarTitleView = null;
        headerBadgeView = null;
        headerTitleView = null;
        headerSummaryView = null;
        panelContainer = null;
        contentScrollView = null;
        contentColumn = null;
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
            featureRows.add(new GboardPatchesSettingsContract.NavigationRow(
                    feature.getEntryTitle(),
                    feature.getEntrySummary(),
                    true,
                    () -> feature.openRootEntry(host)));
        }
        List<GboardPatchesSettingsContract.Row> preferenceRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        preferenceRows.add(new GboardPatchesSettingsContract.SelectorRow(
                GboardSettingsText.get(context, R.string.gboard_patches_language_title),
                GboardSettingsText.get(context, R.string.gboard_patches_language_summary),
                currentLanguageLabel(context),
                true,
                () -> showLanguageDialog(host)));
        List<GboardPatchesSettingsContract.Row> aboutRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        aboutRows.add(new GboardPatchesSettingsContract.CommandRow(
                GboardSettingsText.get(context, R.string.gboard_patches_about_author_title),
                aboutAuthor,
                true,
                authorAction));
        aboutRows.add(new GboardPatchesSettingsContract.CommandRow(
                GboardSettingsText.get(context,
                        R.string.gboard_patches_about_patch_version_title),
                patchVersion,
                true,
                patchRepositoryAction));
        return new GboardPatchesSettingsContract.Screen(
                GboardSettingsText.get(context, R.string.gboard_patches_activity_title),
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge),
                GboardSettingsText.get(context, R.string.gboard_patches_header_title),
                GboardSettingsText.get(context, R.string.gboard_patches_header_summary),
                Collections.emptyList(),
                Arrays.asList(
                        new GboardPatchesSettingsContract.Section(
                                GboardSettingsText.get(context,
                                        R.string.gboard_patches_section_features),
                                featureRows),
                        new GboardPatchesSettingsContract.Section(
                                GboardSettingsText.get(context,
                                        R.string.gboard_patches_section_about),
                                aboutRows),
                        new GboardPatchesSettingsContract.Section(
                                GboardSettingsText.get(context,
                                        R.string.gboard_patches_section_preferences),
                                preferenceRows)));
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
            return createToggleRow(new GboardPatchesSettingsContract.ToggleRow(
                    switchRow.getTitle(),
                    switchRow.getSummary(),
                    switchRow.isEnabled(),
                    switchRow.isChecked(),
                    switchRow.getToggleAction(),
                    switchRow.getPreviewSpec()));
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
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        LinearLayout toggleSupportLine = buildToggleSupportLine(rowModel);
        if (toggleSupportLine != null) {
            textColumn.addView(toggleSupportLine);
        }

        Switch switchView = new Switch(this);
        switchView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        switchView.setShowText(false);
        applySwitchTint(switchView);
        switchView.setChecked(rowModel.isChecked());
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                backgroundStateExecutor.execute(() -> {
                    try {
                        rowModel.getToggleAction().accept(isChecked);
                    } catch (Throwable throwable) {
                        Log.w(TAG, "Failed to toggle switch row", throwable);
                    }
                    postToDecorView(this::renderCurrentScreenSafely);
                });
            } catch (RejectedExecutionException ignored) {
                // Activity is already tearing down.
            }
        });

        row.addView(textColumn);
        row.addView(switchView);
        bindSwitchRowState(row, titleView, summaryView, toggleSupportLine, switchView,
                rowModel.isEnabled());
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("toggle switch row", switchView::toggle);
            }
        });
        return row;
    }

    private View createNavigationRow(GboardPatchesSettingsContract.NavigationRow rowModel) {
        LinearLayout row = buildBaseRow();
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        row.addView(textColumn);
        if (rowModel.getTrailingText() != null && !rowModel.getTrailingText().isEmpty()) {
            row.addView(buildTrailingValue(rowModel.getTrailingText()), trailingLayoutParams(dp(10)));
        }
        View previewButton = buildPreviewButton(rowModel);
        if (previewButton != null) {
            row.addView(previewButton, trailingLayoutParams(dp(10)));
        }
        TextView chevronView = null;
        if (rowModel.shouldShowChevron()) {
            chevronView = buildChevronView();
            row.addView(chevronView);
        }
        bindActionRowState(row, titleView, summaryView, chevronView, rowModel.isEnabled());
        row.setOnClickListener(view -> {
            if (row.isEnabled()) {
                runSafely("handle navigation row", rowModel.getAction());
            }
        });
        return row;
    }

    private View createSelectorRow(GboardPatchesSettingsContract.SelectorRow rowModel) {
        LinearLayout row = buildBaseRow();
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        LinearLayout selectorSupportLine = buildSelectorSupportLine(rowModel);
        if (selectorSupportLine != null) {
            textColumn.addView(selectorSupportLine);
        }
        row.addView(textColumn);
        TextView chevronView = buildChevronView();
        row.addView(chevronView);
        bindSelectorRowState(row, titleView, summaryView, selectorSupportLine, chevronView,
                rowModel.isEnabled());
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
        titleView.setTextColor(rowModel.isEnabled() ? palette.textPrimary : palette.textDisabled);
        summaryView.setTextColor(rowModel.isEnabled() ? palette.textSecondary : palette.textDisabled);
        row.setAlpha(rowModel.isEnabled() ? 1f : 0.92f);
        return row;
    }

    private View createCommandRow(GboardPatchesSettingsContract.CommandRow rowModel) {
        LinearLayout row = buildBaseRow();
        LinearLayout textColumn = buildRowTextColumn(true);
        TextView titleView = buildRowTitle(rowModel.getTitle());
        TextView summaryView = buildRowSummary(rowModel.getSummary(), false);
        textColumn.addView(titleView);
        textColumn.addView(summaryView);
        row.addView(textColumn);
        View previewButton = buildPreviewButton(rowModel);
        if (previewButton != null) {
            row.addView(previewButton, trailingLayoutParams(dp(10)));
        }
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
        LinearLayout card = buildSectionContainer(false);
        card.setBackground(buildCardDrawable(
                toneBackground(block.getTone()),
                palette.surfaceStroke,
                dp(20)));
        card.addView(buildSectionTitle(block.getTitle()));
        card.addView(buildSectionSummary(block.getSummary(), false));
        return card;
    }

    private View createSectionView(GboardPatchesSettingsContract.Section section) {
        LinearLayout container = buildSectionContainer(
                section.getStyle() == GboardPatchesSettingsContract.SectionStyle.ADVANCED);
        if (section.getTitle() != null && !section.getTitle().isEmpty()) {
            container.addView(buildSectionTitle(section.getTitle()));
        }
        if (section.getDescription() != null && !section.getDescription().isEmpty()) {
            container.addView(buildSectionSummary(section.getDescription(), false));
        }
        boolean first = true;
        for (GboardPatchesSettingsContract.Row item : section.getItems()) {
            View rowView = createRowView(item);
            if (!first) {
                LinearLayout.LayoutParams params =
                        (LinearLayout.LayoutParams) rowView.getLayoutParams();
                params.topMargin = dp(6);
                rowView.setLayoutParams(params);
            }
            container.addView(rowView);
            first = false;
        }
        return container;
    }

    private void bindSwitchRowState(LinearLayout row, TextView titleView, TextView summaryView,
            LinearLayout supportLine, Switch switchView, boolean enabled) {
        row.setEnabled(enabled);
        row.setClickable(enabled);
        switchView.setEnabled(enabled);
        titleView.setTextColor(enabled ? palette.textPrimary : palette.textDisabled);
        summaryView.setTextColor(enabled ? palette.textSecondary : palette.textDisabled);
        if (supportLine != null) {
            for (int index = 0; index < supportLine.getChildCount(); index++) {
                View child = supportLine.getChildAt(index);
                child.setEnabled(enabled);
                child.setAlpha(enabled ? 1f : 0.9f);
            }
        }
        row.setAlpha(enabled ? 1f : 0.92f);
    }

    private void applySwitchTint(Switch switchView) {
        int[][] states = new int[][] {
                new int[] { android.R.attr.state_enabled, android.R.attr.state_checked },
                new int[] { android.R.attr.state_enabled, -android.R.attr.state_checked },
                new int[] { -android.R.attr.state_enabled, android.R.attr.state_checked },
                new int[] { -android.R.attr.state_enabled, -android.R.attr.state_checked }
        };
        int[] thumbColors = new int[] {
                palette.accent,
                palette.textPrimary,
                blendAlpha(palette.accent, 0.5f),
                blendAlpha(palette.textDisabled, 0.85f)
        };
        int[] trackColors = new int[] {
                blendAlpha(palette.accent, 0.5f),
                blendAlpha(palette.textSecondary, 0.45f),
                blendAlpha(palette.accent, 0.28f),
                blendAlpha(palette.textDisabled, 0.3f)
        };
        switchView.setThumbTintList(new ColorStateList(states, thumbColors));
        switchView.setTrackTintList(new ColorStateList(states, trackColors));
    }

    private void runSafely(String operationName, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to " + operationName, throwable);
            renderCurrentScreenSafely();
        }
    }

    private void bindActionRowState(LinearLayout row, TextView titleView, TextView summaryView,
            TextView chevronView, boolean enabled) {
        row.setEnabled(enabled);
        row.setClickable(enabled);
        titleView.setTextColor(enabled ? palette.textPrimary : palette.textDisabled);
        summaryView.setTextColor(enabled ? palette.textSecondary : palette.textDisabled);
        if (chevronView != null) {
            chevronView.setTextColor(enabled ? palette.textSecondary : palette.textDisabled);
        }
        row.setAlpha(enabled ? 1f : 0.92f);
    }

    private void bindSelectorRowState(LinearLayout row, TextView titleView, TextView summaryView,
            LinearLayout supportLine, TextView chevronView, boolean enabled) {
        bindActionRowState(row, titleView, summaryView, chevronView, enabled);
        if (supportLine != null) {
            for (int index = 0; index < supportLine.getChildCount(); index++) {
                View child = supportLine.getChildAt(index);
                child.setEnabled(enabled);
                child.setAlpha(enabled ? 1f : 0.9f);
            }
        }
    }

    private LinearLayout buildBaseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setBackground(buildRippleDrawable(dp(18)));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout buildDetailRowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout buildSectionContainer(boolean advanced) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(buildCardDrawable(
                advanced ? palette.advancedContainer : palette.surface,
                palette.surfaceStroke,
                dp(20)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        container.setLayoutParams(params);
        container.setPadding(dp(12), dp(12), dp(12), dp(12));
        return container;
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
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setText(text);
        return titleView;
    }

    private TextView buildRowSummary(String text, boolean monospace) {
        TextView summaryView = new TextView(this);
        summaryView.setTextColor(palette.textSecondary);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
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
        titleView.setTextColor(palette.textSecondary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setAllCaps(true);
        return titleView;
    }

    private TextView buildSectionSummary(String text, boolean monospace) {
        TextView summaryView = new TextView(this);
        summaryView.setText(text);
        summaryView.setTextColor(palette.textSecondary);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        summaryView.setLayoutParams(params);
        summaryView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        if (monospace || isLikelyMonospace(text)) {
            summaryView.setTypeface(Typeface.MONOSPACE);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        }
        return summaryView;
    }

    private TextView buildTrailingValue(String text) {
        TextView valueView = new TextView(this);
        valueView.setText(text);
        valueView.setTextColor(palette.accent);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.CENTER_VERTICAL);
        return valueView;
    }

    private LinearLayout buildSelectorSupportLine(
            GboardPatchesSettingsContract.SelectorRow rowModel) {
        boolean hasCurrentValue =
                rowModel.getCurrentValue() != null && !rowModel.getCurrentValue().isEmpty();
        View previewButton = buildPreviewButton(rowModel);
        if (!hasCurrentValue && previewButton == null) {
            return null;
        }

        LinearLayout supportLine = new LinearLayout(this);
        supportLine.setOrientation(LinearLayout.HORIZONTAL);
        supportLine.setGravity(Gravity.CENTER_VERTICAL);
        supportLine.setBaselineAligned(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        supportLine.setLayoutParams(params);

        if (hasCurrentValue) {
            TextView labelView = buildSupportLineLabel(
                    text(R.string.gboard_patches_current_value_label));
            supportLine.addView(labelView);
            supportLine.addView(buildCurrentValueChip(rowModel.getCurrentValue()),
                    supportChipLayoutParams(0));
        }
        if (previewButton != null) {
            supportLine.addView(previewButton, supportChipLayoutParams(hasCurrentValue ? dp(8) : 0));
        }
        return supportLine;
    }

    private LinearLayout buildToggleSupportLine(
            GboardPatchesSettingsContract.ToggleRow rowModel) {
        View previewButton = buildPreviewButton(rowModel);
        if (previewButton == null) {
            return null;
        }

        LinearLayout supportLine = new LinearLayout(this);
        supportLine.setOrientation(LinearLayout.HORIZONTAL);
        supportLine.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        supportLine.setLayoutParams(params);
        supportLine.addView(previewButton);
        return supportLine;
    }

    private TextView buildSupportLineLabel(String text) {
        TextView labelView = new TextView(this);
        labelView.setText(text);
        labelView.setTextColor(palette.textSecondary);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setAllCaps(true);
        return labelView;
    }

    private TextView buildCurrentValueChip(String text) {
        TextView chipView = new TextView(this);
        chipView.setText(text);
        chipView.setTextColor(palette.accent);
        chipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        chipView.setTypeface(Typeface.DEFAULT_BOLD);
        chipView.setMaxLines(1);
        chipView.setEllipsize(TextUtils.TruncateAt.END);
        chipView.setMaxWidth(dp(200));
        chipView.setPadding(dp(12), dp(7), dp(12), dp(7));
        chipView.setBackground(buildChipDrawable(palette.infoContainer, palette.surfaceStroke));
        return chipView;
    }

    private TextView buildChevronView() {
        TextView chevronView = new TextView(this);
        chevronView.setText("\u203a");
        chevronView.setTextColor(palette.textSecondary);
        chevronView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        chevronView.setTypeface(Typeface.DEFAULT_BOLD);
        chevronView.setGravity(Gravity.CENTER_VERTICAL);
        return chevronView;
    }

    private View buildPreviewButton(GboardPatchesSettingsContract.Row rowModel) {
        GboardPatchesSettingsContract.PreviewSpec previewSpec = rowModel.getPreviewSpec();
        if (previewSpec == null) {
            return null;
        }
        TextView previewButton = new TextView(this);
        previewButton.setText(text(R.string.gboard_patches_preview_label));
        previewButton.setTextColor(palette.accent);
        previewButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        previewButton.setTypeface(Typeface.DEFAULT_BOLD);
        previewButton.setPadding(dp(10), dp(6), dp(10), dp(6));
        previewButton.setBackground(buildChipDrawable(palette.infoContainer, palette.surfaceStroke));
        previewButton.setOnClickListener(view ->
                runSafely("show row preview", () -> showPreviewDialog(previewSpec)));
        return previewButton;
    }

    private LinearLayout.LayoutParams trailingLayoutParams(int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = rightMargin;
        return params;
    }

    private LinearLayout.LayoutParams supportChipLayoutParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = leftMargin;
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
            return palette.infoContainer;
        }
        return palette.surfaceAlt;
    }

    private Drawable buildChipDrawable(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(999));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
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

    private Drawable buildCardDrawable(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
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

    private static final class Palette {
        final int windowBackground;
        final int surface;
        final int surfaceAlt;
        final int surfaceStroke;
        final int infoContainer;
        final int warningContainer;
        final int advancedContainer;
        final int textPrimary;
        final int textSecondary;
        final int textDisabled;
        final int accent;
        final int pressedOverlay;

        Palette(int windowBackground, int surface, int surfaceAlt, int surfaceStroke,
                int infoContainer, int warningContainer, int advancedContainer, int textPrimary,
                int textSecondary, int textDisabled, int accent, int pressedOverlay) {
            this.windowBackground = windowBackground;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.surfaceStroke = surfaceStroke;
            this.infoContainer = infoContainer;
            this.warningContainer = warningContainer;
            this.advancedContainer = advancedContainer;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textDisabled = textDisabled;
            this.accent = accent;
            this.pressedOverlay = pressedOverlay;
        }

        static Palette forConfiguration(Configuration configuration) {
            boolean nightMode = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            if (nightMode) {
                return new Palette(
                        Color.parseColor("#FF0F1217"),
                        Color.parseColor("#FF161B22"),
                        Color.parseColor("#FF121821"),
                        Color.parseColor("#1FFFFFFF"),
                        Color.parseColor("#172554"),
                        Color.parseColor("#3F2D16"),
                        Color.parseColor("#111A2A"),
                        Color.parseColor("#FFF3F5F8"),
                        Color.parseColor("#FFAAB3C0"),
                        Color.parseColor("#FF657181"),
                        Color.parseColor("#FF8AB4F8"),
                        Color.parseColor("#1F8AB4F8"));
            }
            return new Palette(
                    Color.parseColor("#F5F7FB"),
                    Color.parseColor("#FFFFFFFF"),
                    Color.parseColor("#EEF3FB"),
                    Color.parseColor("#140F172A"),
                    Color.parseColor("#E8F0FE"),
                    Color.parseColor("#FEF3C7"),
                    Color.parseColor("#F7F9FC"),
                    Color.parseColor("#FF101828"),
                    Color.parseColor("#FF5F6B7A"),
                    Color.parseColor("#FFB6BFCC"),
                    Color.parseColor("#FF1A73E8"),
                    Color.parseColor("#141A73E8"));
        }
    }
}

