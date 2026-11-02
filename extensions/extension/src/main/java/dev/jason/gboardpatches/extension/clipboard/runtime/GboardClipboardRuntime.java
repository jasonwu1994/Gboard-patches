package dev.jason.gboardpatches.extension.clipboard;

@SuppressWarnings("unused")
public final class GboardClipboardRuntime {
    public static final String SETTINGS_PREF_FILE = "gboard_clipboard";
    public static final String LEGACY_SETTINGS_PREF_FILE = "gboard_clipboard_retention";

    private static final GboardClipboardRuntimeSupport SUPPORT =
            new GboardClipboardRuntimeSupport();
    private static final GboardClipboardRetentionFeature RETENTION_FEATURE =
            new GboardClipboardRetentionFeature(SUPPORT);
    private static final GboardClipboardMaxCountFeature MAX_COUNT_FEATURE =
            new GboardClipboardMaxCountFeature(SUPPORT);
    private static final GboardClipboardPreviewLinesFeature PREVIEW_LINES_FEATURE =
            new GboardClipboardPreviewLinesFeature(SUPPORT);
    private static final GboardClipboardCountdownFeature COUNTDOWN_FEATURE =
            new GboardClipboardCountdownFeature(SUPPORT, RETENTION_FEATURE);
    private static final GboardClipboardCreationTimeFeature CREATION_TIME_FEATURE =
            new GboardClipboardCreationTimeFeature(SUPPORT);
    private static final GboardClipboardOrderIndexFeature ORDER_INDEX_FEATURE =
            new GboardClipboardOrderIndexFeature(SUPPORT);
    private static final GboardClipboardColumnCountFeature COLUMN_COUNT_FEATURE =
            new GboardClipboardColumnCountFeature(SUPPORT);
    private static final GboardClipboardCardPreviewFeature CARD_PREVIEW_FEATURE =
            new GboardClipboardCardPreviewFeature(SUPPORT);
    private static final GboardClipboardSensitiveContentFeature SENSITIVE_CONTENT_FEATURE =
            new GboardClipboardSensitiveContentFeature(SUPPORT);
    private static final GboardClipboardLoaderHookAdapter LOADER_HOOK_ADAPTER =
            new GboardClipboardLoaderHookAdapter(SUPPORT, RETENTION_FEATURE, MAX_COUNT_FEATURE);
    private static final GboardClipboardPruneHookAdapter PRUNE_HOOK_ADAPTER =
            new GboardClipboardPruneHookAdapter(SUPPORT, RETENTION_FEATURE, MAX_COUNT_FEATURE);
    private static final GboardClipboardUiHookAdapter UI_HOOK_ADAPTER =
            new GboardClipboardUiHookAdapter(SUPPORT, MAX_COUNT_FEATURE, PREVIEW_LINES_FEATURE,
                    COUNTDOWN_FEATURE, CREATION_TIME_FEATURE, ORDER_INDEX_FEATURE,
                    CARD_PREVIEW_FEATURE, SENSITIVE_CONTENT_FEATURE, LOADER_HOOK_ADAPTER);
    private static final GboardClipboardColumnCountHookAdapter COLUMN_COUNT_HOOK_ADAPTER =
            new GboardClipboardColumnCountHookAdapter(COLUMN_COUNT_FEATURE);

    private GboardClipboardRuntime() {
    }

    public static boolean isClipboardEnabled() {
        return SUPPORT.isClipboardEnabled();
    }

    public static boolean shouldShowExpiryCountdown() {
        return SUPPORT.shouldShowExpiryCountdown();
    }

    public static boolean shouldShowCreationTime() {
        return SUPPORT.shouldShowCreationTime();
    }

    public static int configuredRetentionTtlMinutes() {
        return SUPPORT.configuredRetentionTtlMinutes();
    }

    public static int configuredMaxCount() {
        return SUPPORT.configuredMaxCount();
    }

    public static int configuredPreviewLines() {
        return SUPPORT.configuredPreviewLines();
    }

    public static int configuredColumnCount() {
        return SUPPORT.configuredColumnCount();
    }

    public static boolean defaultShowExpiryCountdown() {
        return true;
    }

    public static boolean defaultShowCreationTime() {
        return false;
    }

    public static int defaultRetentionTtlMinutes() {
        return GboardClipboardRuntimeSupport.DEFAULT_TTL_MINUTES;
    }

    public static int defaultMaxCount() {
        return GboardClipboardRuntimeSupport.DEFAULT_MAX_COUNT;
    }

    public static int defaultPreviewLines() {
        return GboardClipboardRuntimeSupport.DEFAULT_PREVIEW_LINES;
    }

    public static void registerApplicationContext(android.content.Context context) {
        SUPPORT.registerApplicationContext(context);
    }

    public static Object maybeBuildLoaderResult(Object receiver) {
        return LOADER_HOOK_ADAPTER.maybeBuildLoaderResult(receiver);
    }

    public static boolean handleCustomPrune(Object receiver) {
        return PRUNE_HOOK_ADAPTER.handleCustomPrune(receiver);
    }

    public static void afterAdapterTrim(Object receiver) {
        UI_HOOK_ADAPTER.afterAdapterTrim(receiver);
    }

    public static void beforeItemBind(Object receiver, Object holderObject, int position) {
        UI_HOOK_ADAPTER.beforeItemBind(receiver, holderObject, position);
    }

    public static void abortItemBind() { CARD_PREVIEW_FEATURE.afterItemBind(); }

    public static void afterItemBind(Object receiver, Object holderObject, int position) {
        UI_HOOK_ADAPTER.afterItemBind(receiver, holderObject, position);
    }

    public static int resolveColumnCount(Object receiver,int stock) {
        return GboardClipboardGridRuntime.resolve(receiver,stock,SUPPORT);
    }
    public static void afterLayout(Object receiver) { GboardClipboardGridRuntime.afterLayout(receiver,SUPPORT); }

    public static Integer resolveColumnCountOverride(Object receiver) {
        return COLUMN_COUNT_HOOK_ADAPTER.resolveColumnCountOverride(receiver);
    }
}
