package dev.jason.gboardpatches.extension.settings;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GboardPatchesSettingsContract {
    public interface Feature {
        String getEntryTitle();

        String getEntrySummary();

        default void openRootEntry(FeatureHost host) {
            openFeature(host, this);
        }

        default boolean isAvailable(Context context) {
            return true;
        }

        default boolean requiresOfflineSpeechLanguages() {
            return false;
        }

        default List<Feature> getNavigationChildren() {
            return Collections.emptyList();
        }

        Screen buildScreen(FeatureHost host);
    }

    public interface Intent {
        void apply(Host host);
    }

    public interface FeatureHost {
        Context getContext();

        default OfflineSpeechLanguages getOfflineSpeechLanguages() {
            return OfflineSpeechLanguages.loading();
        }

        void submit(Intent intent);
    }

    public interface Host extends FeatureHost {
        @Override
        default void submit(Intent intent) {
            if (intent != null) {
                intent.apply(this);
            }
        }

        void refresh();

        void openFeature(Feature feature);

        default void openExternalUrl(String url) {
            throw new UnsupportedOperationException("External URL navigation is unavailable");
        }

        default void openSpeechRecognitionAndSynthesisStoreListing() {
            throw new UnsupportedOperationException(
                    "Speech Recognition & Synthesis store navigation is unavailable");
        }

        default void openLiveTranscribeLanguageManager() {
            throw new UnsupportedOperationException(
                    "Live Transcribe navigation is unavailable");
        }

        default void openTargetSettingsHeader(int headerKeyResourceId) {
            throw new UnsupportedOperationException("Target settings navigation is unavailable");
        }

        void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                StringValueConsumer valueConsumer);

        void showPositiveIntegerDialog(String title, String hint, int initialValue,
                PositiveIntegerConsumer consumer);

        void showTextInputDialog(String title, String hint, String initialValue,
                TextValueConsumer consumer);

        void showCodeEditorDialog(String title, String hint, String initialValue,
                TextValueConsumer consumer);

        void showPreviewDialog(PreviewSpec previewSpec);

        void showManagedDialog(ManagedDialogAction action);

        void createTextDocument(String fileName, String mimeType, String text,
                Runnable completionAction);

        void openTextDocument(String[] mimeTypes, StringValueConsumer valueConsumer);
    }

    public static void refresh(FeatureHost host) {
        submit(host, Host::refresh);
    }

    public static void openFeature(FeatureHost host, Feature feature) {
        submit(host, adapter -> adapter.openFeature(feature));
    }

    public static void openExternalUrl(FeatureHost host, String url) {
        submit(host, adapter -> adapter.openExternalUrl(url));
    }

    public static void openSpeechRecognitionAndSynthesisStoreListing(FeatureHost host) {
        submit(host, Host::openSpeechRecognitionAndSynthesisStoreListing);
    }

    public static void openLiveTranscribeLanguageManager(FeatureHost host) {
        submit(host, Host::openLiveTranscribeLanguageManager);
    }

    public static void openTargetSettingsHeader(FeatureHost host, int headerKeyResourceId) {
        submit(host, adapter -> adapter.openTargetSettingsHeader(headerKeyResourceId));
    }

    public static void showChoiceDialog(FeatureHost host, String title, String[] labels,
            String[] values, String currentValue, String customValue, Runnable customAction,
            StringValueConsumer valueConsumer) {
        submit(host, adapter -> adapter.showChoiceDialog(title, labels, values, currentValue,
                customValue, customAction, valueConsumer));
    }

    public static void showPositiveIntegerDialog(FeatureHost host, String title, String hint,
            int initialValue, PositiveIntegerConsumer consumer) {
        submit(host, adapter ->
                adapter.showPositiveIntegerDialog(title, hint, initialValue, consumer));
    }

    public static void showTextInputDialog(FeatureHost host, String title, String hint,
            String initialValue, TextValueConsumer consumer) {
        submit(host, adapter -> adapter.showTextInputDialog(title, hint, initialValue, consumer));
    }

    public static void showCodeEditorDialog(FeatureHost host, String title, String hint,
            String initialValue, TextValueConsumer consumer) {
        submit(host, adapter -> adapter.showCodeEditorDialog(title, hint, initialValue, consumer));
    }

    public static void showPreviewDialog(FeatureHost host, PreviewSpec previewSpec) {
        submit(host, adapter -> adapter.showPreviewDialog(previewSpec));
    }

    public static void showManagedDialog(FeatureHost host, ManagedDialogAction action) {
        submit(host, adapter -> adapter.showManagedDialog(action));
    }

    public static void createTextDocument(FeatureHost host, String fileName, String mimeType,
            String text, Runnable completionAction) {
        submit(host, adapter ->
                adapter.createTextDocument(fileName, mimeType, text, completionAction));
    }

    public static void openTextDocument(FeatureHost host, String[] mimeTypes,
            StringValueConsumer valueConsumer) {
        submit(host, adapter -> adapter.openTextDocument(mimeTypes, valueConsumer));
    }

    private static void submit(FeatureHost host, Intent intent) {
        if (host != null) {
            host.submit(intent);
        }
    }

    public static final class OfflineSpeechLanguages {
        public enum Status {
            LOADING,
            AVAILABLE,
            UNSUPPORTED,
            UNAVAILABLE,
            ERROR
        }

        private final Status status;
        private final List<String> languageTags;

        private OfflineSpeechLanguages(Status status, List<String> languageTags) {
            this.status = status == null ? Status.ERROR : status;
            List<String> safeLanguageTags = languageTags == null
                    ? Collections.emptyList()
                    : new ArrayList<String>(languageTags);
            this.languageTags = Collections.unmodifiableList(safeLanguageTags);
        }

        public static OfflineSpeechLanguages loading() {
            return new OfflineSpeechLanguages(Status.LOADING, Collections.emptyList());
        }

        public static OfflineSpeechLanguages available(List<String> languageTags) {
            return new OfflineSpeechLanguages(Status.AVAILABLE, languageTags);
        }

        public static OfflineSpeechLanguages unsupported() {
            return new OfflineSpeechLanguages(Status.UNSUPPORTED, Collections.emptyList());
        }

        public static OfflineSpeechLanguages unavailable() {
            return new OfflineSpeechLanguages(Status.UNAVAILABLE, Collections.emptyList());
        }

        public static OfflineSpeechLanguages error() {
            return new OfflineSpeechLanguages(Status.ERROR, Collections.emptyList());
        }

        public Status getStatus() {
            return status;
        }

        public List<String> getLanguageTags() {
            return languageTags;
        }
    }

    public interface StringValueConsumer {
        void accept(String value);
    }

    public interface PositiveIntegerConsumer {
        void accept(int value);
    }

    public interface TextValueConsumer {
        void accept(String value);
    }

    public interface ManagedDialogAction {
        boolean show(Runnable onDismiss);
    }

    public interface ToggleAction {
        void accept(boolean value);
    }

    public enum PreviewLayout {
        SIDE_BY_SIDE,
        STACKED
    }

    public enum SectionStyle {
        DEFAULT,
        ADVANCED
    }

    public enum StatusTone {
        NEUTRAL,
        INFO,
        WARNING
    }

    public enum RefreshScope {
        NONE,
        FULL_SCREEN,
        LIVE_STATUS_ONLY
    }

    public enum PanelStyle {
        CARD,
        FLAT
    }

    public static final class RefreshPolicy {
        private static final RefreshPolicy NONE =
                new RefreshPolicy(0L, RefreshScope.NONE);

        private final long intervalMs;
        private final RefreshScope scope;

        public RefreshPolicy(long intervalMs, RefreshScope scope) {
            this.intervalMs = Math.max(0L, intervalMs);
            this.scope = scope == null ? RefreshScope.NONE : scope;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public RefreshScope getScope() {
            return scope;
        }

        public boolean isLiveStatusOnly() {
            return scope == RefreshScope.LIVE_STATUS_ONLY;
        }

        public static RefreshPolicy none() {
            return NONE;
        }

        public static RefreshPolicy fullScreen(long intervalMs) {
            return new RefreshPolicy(intervalMs, RefreshScope.FULL_SCREEN);
        }

        public static RefreshPolicy liveStatusOnly(long intervalMs) {
            return new RefreshPolicy(intervalMs, RefreshScope.LIVE_STATUS_ONLY);
        }
    }

    public abstract static class PreviewMedia {
        private final String caption;

        protected PreviewMedia(String caption) {
            this.caption = caption;
        }

        public String getCaption() {
            return caption;
        }
    }

    public static final class PreviewImage extends PreviewMedia {
        private final String assetPath;

        public PreviewImage(String assetPath, String caption) {
            super(caption);
            this.assetPath = assetPath;
        }

        public String getAssetPath() {
            return assetPath;
        }
    }

    public static final class PreviewVideo extends PreviewMedia {
        private final String assetPath;

        public static PreviewVideo fromAsset(String assetPath, String caption) {
            return new PreviewVideo(assetPath, caption);
        }

        private PreviewVideo(String assetPath, String caption) {
            super(caption);
            this.assetPath = assetPath;
        }

        public String getAssetPath() {
            return assetPath;
        }
    }

    public static final class PreviewSpec {
        private final String title;
        private final String message;
        private final PreviewLayout layout;
        private final List<PreviewMedia> mediaItems;

        public PreviewSpec(String title, String message, PreviewMedia... mediaItems) {
            this(title, message, PreviewLayout.SIDE_BY_SIDE, Arrays.asList(mediaItems));
        }

        public PreviewSpec(String title, String message, PreviewLayout layout,
                PreviewMedia... mediaItems) {
            this(title, message, layout, Arrays.asList(mediaItems));
        }

        public PreviewSpec(String title, String message, List<? extends PreviewMedia> mediaItems) {
            this(title, message, PreviewLayout.SIDE_BY_SIDE, mediaItems);
        }

        public PreviewSpec(String title, String message, PreviewLayout layout,
                List<? extends PreviewMedia> mediaItems) {
            this.title = title;
            this.message = message;
            this.layout = layout;
            this.mediaItems = Collections.unmodifiableList(new ArrayList<>(mediaItems));
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public PreviewLayout getLayout() {
            return layout;
        }

        public List<PreviewMedia> getMediaItems() {
            return mediaItems;
        }
    }

    public static final class StatusBlock {
        private final String title;
        private final String summary;
        private final StatusTone tone;

        public StatusBlock(String title, String summary) {
            this(title, summary, StatusTone.NEUTRAL);
        }

        public StatusBlock(String title, String summary, StatusTone tone) {
            this.title = title;
            this.summary = summary;
            this.tone = tone == null ? StatusTone.NEUTRAL : tone;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public StatusTone getTone() {
            return tone;
        }
    }

    public static final class Section {
        private final String title;
        private final String description;
        private final SectionStyle style;
        private final List<Row> items;

        public Section(String title, List<? extends Row> items) {
            this(title, null, SectionStyle.DEFAULT, items);
        }

        public Section(String title, String description, SectionStyle style,
                List<? extends Row> items) {
            this.title = title;
            this.description = description;
            this.style = style == null ? SectionStyle.DEFAULT : style;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public SectionStyle getStyle() {
            return style;
        }

        public List<Row> getItems() {
            return items;
        }
    }

    public static final class Screen {
        private final String toolbarTitle;
        private final String headerBadge;
        private final String headerTitle;
        private final String headerSummary;
        private final List<StatusBlock> statusBlocks;
        private final List<Section> sections;
        private final RefreshPolicy refreshPolicy;
        private final PanelStyle panelStyle;

        public Screen(String toolbarTitle, String headerBadge, String headerTitle,
                String headerSummary, List<Row> rows) {
            this(toolbarTitle, headerBadge, headerTitle, headerSummary,
                    Collections.emptyList(),
                    rows.isEmpty()
                            ? Collections.emptyList()
                            : Collections.singletonList(new Section(null, rows)),
                    RefreshPolicy.none(),
                    PanelStyle.FLAT);
        }

        public Screen(String toolbarTitle, String headerBadge, String headerTitle,
                String headerSummary, List<Row> rows, long refreshIntervalMs) {
            this(toolbarTitle, headerBadge, headerTitle, headerSummary,
                    Collections.emptyList(),
                    rows.isEmpty()
                            ? Collections.emptyList()
                            : Collections.singletonList(new Section(null, rows)),
                    refreshIntervalMs > 0L
                            ? RefreshPolicy.fullScreen(refreshIntervalMs)
                            : RefreshPolicy.none(),
                    PanelStyle.FLAT);
        }

        public Screen(String toolbarTitle, String headerBadge, String headerTitle,
                String headerSummary, List<StatusBlock> statusBlocks, List<Section> sections) {
            this(toolbarTitle, headerBadge, headerTitle, headerSummary, statusBlocks, sections,
                    RefreshPolicy.none(),
                    PanelStyle.FLAT);
        }

        public Screen(String toolbarTitle, String headerBadge, String headerTitle,
                String headerSummary, List<StatusBlock> statusBlocks, List<Section> sections,
                RefreshPolicy refreshPolicy) {
            this(toolbarTitle, headerBadge, headerTitle, headerSummary, statusBlocks, sections,
                    refreshPolicy, PanelStyle.FLAT);
        }

        public Screen(String toolbarTitle, String headerBadge, String headerTitle,
                String headerSummary, List<StatusBlock> statusBlocks, List<Section> sections,
                RefreshPolicy refreshPolicy, PanelStyle panelStyle) {
            this.toolbarTitle = toolbarTitle;
            this.headerBadge = headerBadge;
            this.headerTitle = headerTitle;
            this.headerSummary = headerSummary;
            this.statusBlocks = Collections.unmodifiableList(new ArrayList<>(statusBlocks));
            this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
            this.refreshPolicy = refreshPolicy == null ? RefreshPolicy.none() : refreshPolicy;
            this.panelStyle = panelStyle == null ? PanelStyle.FLAT : panelStyle;
        }

        public String getToolbarTitle() {
            return toolbarTitle;
        }

        public String getHeaderBadge() {
            return headerBadge;
        }

        public String getHeaderTitle() {
            return headerTitle;
        }

        public String getHeaderSummary() {
            return headerSummary;
        }

        public List<StatusBlock> getStatusBlocks() {
            return statusBlocks;
        }

        public List<Section> getSections() {
            return sections;
        }

        public RefreshPolicy getRefreshPolicy() {
            return refreshPolicy;
        }

        public PanelStyle getPanelStyle() {
            return panelStyle;
        }

        public long getRefreshIntervalMs() {
            return refreshPolicy.getIntervalMs();
        }

        public List<Row> getRows() {
            List<Row> rows = new ArrayList<>();
            for (Section section : sections) {
                rows.addAll(section.getItems());
            }
            return Collections.unmodifiableList(rows);
        }
    }

    public abstract static class Row {
        private final CharSequence title;
        private final String summary;
        private final boolean enabled;
        private final PreviewSpec previewSpec;

        protected Row(CharSequence title, String summary, boolean enabled) {
            this(title, summary, enabled, null);
        }

        protected Row(CharSequence title, String summary, boolean enabled, PreviewSpec previewSpec) {
            this.title = title;
            this.summary = summary;
            this.enabled = enabled;
            this.previewSpec = previewSpec;
        }

        public CharSequence getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public PreviewSpec getPreviewSpec() {
            return previewSpec;
        }
    }

    public static class NavigationRow extends Row {
        private final Runnable action;
        private final String trailingText;
        private final boolean showChevron;

        public NavigationRow(CharSequence title, String summary, boolean enabled, Runnable action) {
            this(title, summary, null, enabled, action, null, true);
        }

        public NavigationRow(CharSequence title, String summary, String trailingText,
                boolean enabled, Runnable action) {
            this(title, summary, trailingText, enabled, action, null, true);
        }

        public NavigationRow(CharSequence title, String summary, String trailingText,
                boolean enabled, Runnable action, boolean showChevron) {
            this(title, summary, trailingText, enabled, action, null, showChevron);
        }

        public NavigationRow(CharSequence title, String summary, String trailingText,
                boolean enabled, Runnable action, PreviewSpec previewSpec) {
            this(title, summary, trailingText, enabled, action, previewSpec, true);
        }

        public NavigationRow(CharSequence title, String summary, String trailingText,
                boolean enabled, Runnable action, PreviewSpec previewSpec,
                boolean showChevron) {
            super(title, summary, enabled, previewSpec);
            this.action = action;
            this.trailingText = trailingText;
            this.showChevron = showChevron;
        }

        public Runnable getAction() {
            return action;
        }

        public String getTrailingText() {
            return trailingText;
        }

        public boolean shouldShowChevron() {
            return showChevron;
        }
    }

    public static class ToggleRow extends Row {
        private final boolean checked;
        private final ToggleAction toggleAction;

        public ToggleRow(CharSequence title, String summary, boolean enabled, boolean checked,
                ToggleAction toggleAction) {
            this(title, summary, enabled, checked, toggleAction, null);
        }

        public ToggleRow(CharSequence title, String summary, boolean enabled, boolean checked,
                ToggleAction toggleAction, PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
            this.checked = checked;
            this.toggleAction = toggleAction;
        }

        public boolean isChecked() {
            return checked;
        }

        public ToggleAction getToggleAction() {
            return toggleAction;
        }
    }

    public static class SelectorRow extends Row {
        private final String currentValue;
        private final Runnable action;

        public SelectorRow(CharSequence title, String summary, String currentValue,
                boolean enabled, Runnable action) {
            this(title, summary, currentValue, enabled, action, null);
        }

        public SelectorRow(CharSequence title, String summary, String currentValue,
                boolean enabled, Runnable action, PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
            this.currentValue = currentValue;
            this.action = action;
        }

        public String getCurrentValue() {
            return currentValue;
        }

        public Runnable getAction() {
            return action;
        }
    }

    public static class DetailRow extends Row {
        private final boolean monospace;

        public DetailRow(CharSequence title, String summary, boolean enabled) {
            this(title, summary, enabled, false);
        }

        public DetailRow(CharSequence title, String summary, boolean enabled, boolean monospace) {
            super(title, summary, enabled);
            this.monospace = monospace;
        }

        public boolean isMonospace() {
            return monospace;
        }
    }

    public static class CommandRow extends Row {
        private final Runnable action;

        public CommandRow(CharSequence title, String summary, boolean enabled, Runnable action) {
            this(title, summary, enabled, action, null);
        }

        public CommandRow(CharSequence title, String summary, boolean enabled, Runnable action,
                PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
            this.action = action;
        }

        public Runnable getAction() {
            return action;
        }
    }

    public static final class DangerRow extends CommandRow {
        private final String confirmTitle;
        private final String confirmMessage;

        public DangerRow(CharSequence title, String summary, boolean enabled, Runnable action,
                String confirmTitle, String confirmMessage) {
            super(title, summary, enabled, action);
            this.confirmTitle = confirmTitle;
            this.confirmMessage = confirmMessage;
        }

        public String getConfirmTitle() {
            return confirmTitle;
        }

        public String getConfirmMessage() {
            return confirmMessage;
        }
    }

    /**
     * Legacy row types kept during migration to sectioned settings.
     */
    public static final class ActionRow extends Row {
        private final Runnable action;
        private final boolean showChevron;

        public ActionRow(CharSequence title, String summary, boolean enabled, Runnable action) {
            this(title, summary, enabled, true, action, null);
        }

        public ActionRow(CharSequence title, String summary, boolean enabled, boolean showChevron,
                Runnable action) {
            this(title, summary, enabled, showChevron, action, null);
        }

        public ActionRow(CharSequence title, String summary, boolean enabled, Runnable action,
                PreviewSpec previewSpec) {
            this(title, summary, enabled, true, action, previewSpec);
        }

        public ActionRow(CharSequence title, String summary, boolean enabled, boolean showChevron,
                Runnable action, PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
            this.action = action;
            this.showChevron = showChevron;
        }

        public Runnable getAction() {
            return action;
        }

        public boolean shouldShowChevron() {
            return showChevron;
        }
    }

    public static final class InfoRow extends Row {
        public InfoRow(CharSequence title, String summary, boolean enabled) {
            super(title, summary, enabled);
        }

        public InfoRow(CharSequence title, String summary, boolean enabled,
                PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
        }
    }

    public static final class SwitchRow extends Row {
        private final boolean checked;
        private final ToggleAction toggleAction;

        public SwitchRow(CharSequence title, String summary, boolean enabled, boolean checked,
                ToggleAction toggleAction) {
            this(title, summary, enabled, checked, toggleAction, null);
        }

        public SwitchRow(CharSequence title, String summary, boolean enabled, boolean checked,
                ToggleAction toggleAction, PreviewSpec previewSpec) {
            super(title, summary, enabled, previewSpec);
            this.checked = checked;
            this.toggleAction = toggleAction;
        }

        public boolean isChecked() {
            return checked;
        }

        public ToggleAction getToggleAction() {
            return toggleAction;
        }
    }

    private GboardPatchesSettingsContract() {
    }
}
