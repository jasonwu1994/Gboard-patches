package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GboardClipboardUiHookAdapter {
    private final GboardClipboardRuntimeSupport support;
    private final GboardClipboardMaxCountFeature maxCountFeature;
    private final GboardClipboardPreviewLinesFeature previewLinesFeature;
    private final GboardClipboardCountdownFeature countdownFeature;
    private final GboardClipboardCreationTimeFeature creationTimeFeature;
    private final GboardClipboardOrderIndexFeature orderIndexFeature;
    private final GboardClipboardCardPreviewFeature cardPreviewFeature;
    private final GboardClipboardSensitiveContentFeature sensitiveContentFeature;
    private final GboardClipboardLoaderHookAdapter loaderHookAdapter;

    GboardClipboardUiHookAdapter(GboardClipboardRuntimeSupport support,
            GboardClipboardMaxCountFeature maxCountFeature,
            GboardClipboardPreviewLinesFeature previewLinesFeature,
            GboardClipboardCountdownFeature countdownFeature,
            GboardClipboardCreationTimeFeature creationTimeFeature,
            GboardClipboardOrderIndexFeature orderIndexFeature,
            GboardClipboardCardPreviewFeature cardPreviewFeature,
            GboardClipboardSensitiveContentFeature sensitiveContentFeature,
            GboardClipboardLoaderHookAdapter loaderHookAdapter) {
        this.support = support;
        this.maxCountFeature = maxCountFeature;
        this.previewLinesFeature = previewLinesFeature;
        this.countdownFeature = countdownFeature;
        this.creationTimeFeature = creationTimeFeature;
        this.orderIndexFeature = orderIndexFeature;
        this.cardPreviewFeature = cardPreviewFeature;
        this.sensitiveContentFeature = sensitiveContentFeature;
        this.loaderHookAdapter = loaderHookAdapter;
    }

    void beforeItemBind(Object receiver, Object holderObject, int position) {
        cardPreviewFeature.beforeItemBind(receiver, position);
    }

    void afterAdapterTrim(Object receiver) {
        support.runSafely("run clipboard adapter trim", () -> {
            if (receiver == null) {
                return;
            }
            support.registerContextFromReceiver(receiver);
            if (!support.isClipboardEnabled()) {
                return;
            }
            enforceAdapterTrim(receiver);
        });
    }

    void afterItemBind(Object receiver, Object holderObject, int position) {
        cardPreviewFeature.afterItemBind();
        support.runSafely("bind clipboard metadata label", () -> {
            if (receiver == null) {
                return;
            }
            support.registerContextFromReceiver(receiver);
            bindClipboardMetadataLabel(receiver, holderObject, position);
        });
    }

    void onCountdownTick(GboardClipboardRuntimeSupport.CountdownBinding binding) {
        support.runSafely("update clipboard countdown label", () -> {
            TextView view = binding.textView();
            if (binding.disposed || view == null
                    || support.activeCountdownByTextView.get(view) != binding) return;
            if (!view.isAttachedToWindow()) {
                binding.onViewDetachedFromWindow(view);
                return;
            }
            if (!binding.isVisible(view)) {
                view.removeCallbacks(binding);
                binding.wasVisible = false;
                return;
            }
            updateClipboardMetadataLabel(binding);
        });
    }

    private void enforceAdapterTrim(Object receiver) throws Throwable {
        GboardClipboardRuntimeSupport.ReflectionHandles handles =
                support.reflectionHandles(receiver.getClass().getClassLoader());
        List<Object> items = adapterItems(handles, receiver);
        if (items == null || items.isEmpty()) {
            return;
        }
        Context context = support.adapterContext(handles, receiver);
        if (context == null) {
            return;
        }

        GboardClipboardRuntimeSupport.RuntimeSettings settings = support.runtimeSettings();
        int recentCount = handles.adapterRecentCountField.getInt(receiver);
        int pinnedVisibleCount = handles.adapterPinnedVisibleCountField.getInt(receiver);
        int stockGroupCount = distinctTimestampCountForAdapter(handles, items, recentCount);
        int allowedRecentCount = maxCountFeature.computeAdapterRecentLimit(
                handles,
                items,
                recentCount,
                settings.clipboardMaxCount,
                settings.clipboardGroupLimit);

        if (allowedRecentCount < recentCount) {
            for (int index = recentCount; index > allowedRecentCount; index--) {
                items.remove(index);
                handles.adapterNotifyItemRemovedMethod.invoke(receiver, index);
            }
            handles.adapterRecentCountField.setInt(receiver, allowedRecentCount);
            handles.adapterRefreshMethod.invoke(receiver);
        }
        restoreAdapterAfterStockLiveTrim(receiver, handles, context, items,
                handles.adapterRecentCountField.getInt(receiver),
                handles.adapterPinnedVisibleCountField.getInt(receiver));
        scheduleVisibleOrderIndexRefresh(receiver);

        int invocation = support.trimInvocationCount.incrementAndGet();
        support.logInfo(GboardClipboardRuntimeSupport.LOG_PREFIX + " trim#"
                + invocation
                + " stockVisibleMax=" + GboardClipboardRuntimeSupport.STOCK_CLIPBOARD_MAX_COUNT
                + ", stockGroupLimit=" + GboardClipboardRuntimeSupport.STOCK_CLIPBOARD_GROUP_LIMIT
                + ", stockPinnedVisibleCount=" + pinnedVisibleCount
                + ", stockRecentCount=" + recentCount
                + ", stockDistinctRecentGroups=" + stockGroupCount
                + ", overrideVisibleMax=" + settings.clipboardMaxCount
                + ", overrideGroupLimit=" + settings.clipboardGroupLimit
                + ", overrideRecentCount=" + handles.adapterRecentCountField.getInt(receiver)
                + ", recentSample=" + describeAdapterRecent(handles, items,
                        handles.adapterRecentCountField.getInt(receiver), 3));
    }

    private void bindClipboardMetadataLabel(Object receiver, Object holderObject, int position)
            throws Throwable {
        GboardClipboardRuntimeSupport.ReflectionHandles handles =
                support.reflectionHandles(receiver.getClass().getClassLoader());
        if (holderObject == null || !handles.clipItemViewHolderClass.isInstance(holderObject)) {
            return;
        }

        Object labelViewObject = handles.clipItemViewHolderTextField.get(holderObject);
        if (!(labelViewObject instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) labelViewObject;

        List<Object> items = adapterItems(handles, receiver);
        if (items == null || position < 0 || position >= items.size()) {
            clearCountdownBinding(textView, null);
            return;
        }

        Object clip = items.get(position);
        if (clip == null || clip == handles.recentHeader || clip == handles.pinnedHeader
                || clip == handles.specialHeader) {
            clearCountdownBinding(textView, null);
            return;
        }

        long clipId = support.clipId(handles, clip);
        clearCountdownBinding(textView, Long.valueOf(clipId));
        GboardClipboardRuntimeSupport.RuntimeSettings settings = support.runtimeSettings();
        sensitiveContentFeature.apply(handles, textView, clip, settings);
        if (!settings.enabled) {
            previewLinesFeature.restoreStockMaxLines(textView);
            return;
        }
        previewLinesFeature.applyConfiguredMaxLines(textView, position, settings);
        int clipOrder = settings.showOrderIndex
                ? orderIndexFeature.computeClipOrder(
                        handles,
                        items,
                        clip,
                        settings.clipboardOrderIndexMode)
                : -1;
        if (!settings.showExpiryCountdown && !settings.showCreationTime && clipOrder <= 0) {
            return;
        }

        CharSequence currentText = textView.getText();
        String originalText = currentText == null ? "" : currentText.toString();
        GboardClipboardRuntimeSupport.CountdownBinding binding =
                new GboardClipboardRuntimeSupport.CountdownBinding(
                        this,
                        support,
                        handles,
                        textView,
                        originalText,
                        clipId,
                        support.clipTimestamp(handles, clip),
                        support.isPinned(handles, clip),
                        support.isSpecial(handles, clip),
                        settings.clipboardTtlMs,
                        clipOrder);
        support.activeCountdownByTextView.put(textView, binding);
        binding.watch(textView);
        updateClipboardMetadataLabel(binding);
        scheduleVisibleOrderIndexRefresh(receiver, textView);
        support.logLimited(support.countdownBindCount, 30,
                GboardClipboardRuntimeSupport.LOG_PREFIX + " bind position=" + position
                        + ", id=" + clipId
                        + ", order=" + clipOrder
                        + ", ts=" + binding.clipTimestamp
                        + ", pinned=" + binding.pinned
                        + ", special=" + binding.special
                        + ", label=" + formatMetadataLine(handles, binding, settings));
    }

    private void updateClipboardMetadataLabel(GboardClipboardRuntimeSupport.CountdownBinding binding)
            throws Throwable {
        TextView textView = binding.textView();
        if (textView == null || binding.disposed) return;
        GboardClipboardRuntimeSupport.CountdownBinding activeBinding =
                support.activeCountdownByTextView.get(textView);
        if (activeBinding != binding) {
            return;
        }

        GboardClipboardRuntimeSupport.RuntimeSettings settings = support.runtimeSettings();
        if (!settings.enabled) {
            previewLinesFeature.restoreStockMaxLines(textView);
            clearCountdownBinding(textView);
            return;
        }
        previewLinesFeature.applyConfiguredMaxLines(textView, -1, settings);
        if (!settings.showExpiryCountdown && !settings.showCreationTime && binding.clipOrder <= 0) {
            clearCountdownBinding(textView);
            return;
        }

        String metadataLine = formatMetadataLine(binding.handles, binding, settings);
        String renderedText;
        if (metadataLine == null || metadataLine.isEmpty()) {
            renderedText = binding.originalText;
        } else if (binding.originalText.isEmpty()) {
            renderedText = metadataLine;
        } else {
            renderedText = metadataLine + "\n" + binding.originalText;
        }
        binding.renderedText = renderedText;
        if (!TextUtils.equals(textView.getText(), renderedText)) textView.setText(renderedText);

        textView.removeCallbacks(binding);
        if (countdownFeature.shouldContinueUpdates(binding, settings)) {
            textView.postDelayed(binding,
                    GboardClipboardRuntimeSupport.COUNTDOWN_UPDATE_INTERVAL_MS);
        }
    }

    private String formatMetadataLine(GboardClipboardRuntimeSupport.ReflectionHandles handles,
            GboardClipboardRuntimeSupport.CountdownBinding binding,
            GboardClipboardRuntimeSupport.RuntimeSettings settings) throws Throwable {
        String orderToken = orderIndexFeature.formatToken(binding.clipOrder);
        String countdownToken = settings.showExpiryCountdown
                ? countdownFeature.formatToken(handles, binding)
                : null;
        String creationTimeToken = settings.showCreationTime
                ? creationTimeFeature.formatToken(binding.clipTimestamp)
                : null;
        if ((orderToken == null || orderToken.trim().isEmpty())
                && (countdownToken == null || countdownToken.trim().isEmpty())
                && (creationTimeToken == null || creationTimeToken.trim().isEmpty())) {
            return null;
        }

        List<String> lines = new ArrayList<String>(3);
        if (orderToken != null && !orderToken.trim().isEmpty()) {
            lines.add('[' + orderToken + ']');
        }
        if (countdownToken != null && !countdownToken.trim().isEmpty()) {
            lines.add('[' + countdownToken + ']');
        }
        if (creationTimeToken != null && !creationTimeToken.trim().isEmpty()) {
            lines.add('[' + creationTimeToken + ']');
        }
        if (lines.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private String stripMetadataPrefix(CharSequence currentText) {
        if (currentText == null) {
            return "";
        }
        String text = currentText.toString();
        int start = 0;
        boolean consumedAny = false;
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            String line = newline >= 0 ? text.substring(start, newline) : text.substring(start);
            if (!isMetadataLine(line)) {
                break;
            }
            consumedAny = true;
            if (newline < 0) {
                return "";
            }
            start = newline + 1;
        }
        if (!consumedAny) {
            return text;
        }
        return text.substring(start);
    }

    private boolean isMetadataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        int index = 0;
        while (index < line.length()) {
            if (line.charAt(index) != '[') {
                return false;
            }
            int end = line.indexOf(']', index);
            if (end < 0) {
                return false;
            }
            String token = line.substring(index + 1, end);
            if (!isMetadataToken(token)) {
                return false;
            }
            index = end + 1;
            if (index == line.length()) {
                return true;
            }
            if (line.charAt(index) != ' ') {
                return false;
            }
            index++;
        }
        return true;
    }

    private boolean isMetadataToken(String token) {
        return orderIndexFeature.isToken(token)
                || countdownFeature.isToken(token)
                || creationTimeFeature.isToken(token);
    }

    private void clearCountdownBinding(TextView textView) {
        GboardClipboardRuntimeSupport.CountdownBinding previousBinding =
                support.activeCountdownByTextView.get(textView);
        if (previousBinding == null) {
            WeakReference<GboardClipboardRuntimeSupport.CountdownBinding> detached =
                    support.detachedCountdownByTextView.get(textView);
            previousBinding = detached == null ? null : detached.get();
        }
        clearCountdownBinding(textView, previousBinding == null
                ? null
                : Long.valueOf(previousBinding.clipId));
    }

    private void clearCountdownBinding(TextView textView, Long nextClipId) {
        GboardClipboardRuntimeSupport.CountdownBinding previousBinding =
                support.activeCountdownByTextView.remove(textView);
        WeakReference<GboardClipboardRuntimeSupport.CountdownBinding> detached =
                support.detachedCountdownByTextView.remove(textView);
        if (previousBinding == null && detached != null) previousBinding = detached.get();
        if (previousBinding != null) {
            previousBinding.dispose(textView);
            CharSequence currentText = textView.getText();
            // An after-bind callback cannot distinguish a same-ID stock rewrite to identical
            // bytes from a repeated callback. Same-ID restoration keeps repeated binds idempotent.
            if (nextClipId != null && previousBinding.clipId == nextClipId.longValue()
                    && previousBinding.renderedText != null && currentText != null
                    && currentText.toString().contentEquals(previousBinding.renderedText)) {
                textView.setText(previousBinding.originalText);
            }
            return;
        }

        CharSequence currentText = textView.getText();
        String strippedText = stripMetadataPrefix(currentText);
        if (currentText != null && !currentText.toString().contentEquals(strippedText)) {
            textView.setText(strippedText);
        }
    }

    private void scheduleVisibleOrderIndexRefresh(Object receiver) {
        GboardClipboardRuntimeSupport.CountdownBinding binding = firstActiveBinding();
        if (binding == null) {
            return;
        }
        scheduleVisibleOrderIndexRefresh(receiver, binding.textView());
    }

    private void scheduleVisibleOrderIndexRefresh(Object receiver, TextView anchorView) {
        if (receiver == null || anchorView == null) {
            return;
        }
        synchronized (support.orderIndexRefreshStateByAdapter) {
            GboardClipboardRuntimeSupport.OrderIndexRefreshState state =
                    support.orderIndexRefreshStateByAdapter.get(receiver);
            if (state == null) {
                state = new GboardClipboardRuntimeSupport.OrderIndexRefreshState();
                support.orderIndexRefreshStateByAdapter.put(receiver, state);
            }
            if (state.scheduled) {
                state.rerunRequested = true;
                return;
            }
            state.scheduled = true;
        }
        anchorView.post(() -> runVisibleOrderIndexRefresh(receiver));
    }

    private void runVisibleOrderIndexRefresh(Object receiver) {
        support.runSafely("refresh visible clipboard order indices", () -> {
            try {
                if (receiver == null) {
                    return;
                }
                GboardClipboardRuntimeSupport.ReflectionHandles handles =
                        support.reflectionHandles(receiver.getClass().getClassLoader());
                List<Object> items = adapterItems(handles, receiver);
                refreshVisibleOrderIndices(handles, items, support.runtimeSettings());
            } finally {
                finishVisibleOrderIndexRefresh(receiver);
            }
        });
    }

    private void finishVisibleOrderIndexRefresh(Object receiver) {
        TextView retryAnchorView = null;
        synchronized (support.orderIndexRefreshStateByAdapter) {
            GboardClipboardRuntimeSupport.OrderIndexRefreshState state =
                    support.orderIndexRefreshStateByAdapter.get(receiver);
            if (state == null) {
                return;
            }
            if (state.rerunRequested) {
                state.rerunRequested = false;
                retryAnchorView = firstActiveBindingTextView();
                if (retryAnchorView == null) {
                    support.orderIndexRefreshStateByAdapter.remove(receiver);
                    return;
                }
            } else {
                support.orderIndexRefreshStateByAdapter.remove(receiver);
                return;
            }
        }
        retryAnchorView.post(() -> runVisibleOrderIndexRefresh(receiver));
    }

    private void refreshVisibleOrderIndices(
            GboardClipboardRuntimeSupport.ReflectionHandles handles, List<Object> items,
            GboardClipboardRuntimeSupport.RuntimeSettings settings) throws Throwable {
        if (items == null || settings == null || !settings.showOrderIndex) {
            return;
        }

        List<GboardClipboardRuntimeSupport.CountdownBinding> bindings;
        synchronized (support.activeCountdownByTextView) {
            if (support.activeCountdownByTextView.isEmpty()) {
                return;
            }
            bindings = new ArrayList<GboardClipboardRuntimeSupport.CountdownBinding>(
                    support.activeCountdownByTextView.values());
        }

        int visibleClipCount = 0;
        Map<Long, Integer> visibleIndicesByClipId = new HashMap<Long, Integer>();
        for (Object candidate : items) {
            if (candidate == null || candidate == handles.recentHeader
                    || candidate == handles.pinnedHeader
                    || candidate == handles.specialHeader) {
                continue;
            }
            visibleIndicesByClipId.put(
                    Long.valueOf(support.clipId(handles, candidate)),
                    Integer.valueOf(visibleClipCount));
            visibleClipCount++;
        }

        for (GboardClipboardRuntimeSupport.CountdownBinding binding : bindings) {
            if (binding == null || binding.handles != handles) {
                continue;
            }
            Integer visibleIndex = visibleIndicesByClipId.get(Long.valueOf(binding.clipId));
            binding.clipOrder = resolveClipOrder(
                    visibleClipCount,
                    visibleIndex,
                    settings.clipboardOrderIndexMode);
            if (binding.isVisible(binding.textView())) updateClipboardMetadataLabel(binding);
        }
    }

    private int resolveClipOrder(int visibleClipCount, Integer visibleIndex, String orderIndexMode) {
        if (visibleIndex == null || visibleClipCount <= 0) {
            return -1;
        }
        boolean oldestFirst =
                GboardClipboardSettings.CLIPBOARD_ORDER_INDEX_MODE_OLDEST_FIRST.equals(
                        orderIndexMode);
        int clipVisibleIndex = visibleIndex.intValue();
        return oldestFirst
                ? visibleClipCount - clipVisibleIndex
                : clipVisibleIndex + 1;
    }

    private GboardClipboardRuntimeSupport.CountdownBinding firstActiveBinding() {
        synchronized (support.activeCountdownByTextView) {
            for (GboardClipboardRuntimeSupport.CountdownBinding binding
                    : support.activeCountdownByTextView.values()) {
                if (binding != null && binding.textView() != null) {
                    return binding;
                }
            }
        }
        return null;
    }

    private TextView firstActiveBindingTextView() {
        GboardClipboardRuntimeSupport.CountdownBinding binding = firstActiveBinding();
        return binding == null ? null : binding.textView();
    }

    private void restoreAdapterAfterStockLiveTrim(
            Object receiver,
            GboardClipboardRuntimeSupport.ReflectionHandles handles,
            Context context,
            List<Object> items,
            int currentRecentCount,
            int currentPinnedVisibleCount) throws Throwable {
        if (receiver == null || handles == null || context == null || items == null) {
            return;
        }

        GboardClipboardRuntimeSupport.LoaderAssembly assembly =
                loaderHookAdapter.buildAssemblyForContext(handles, context);
        if (assembly == null) {
            return;
        }

        boolean shouldRestore = shouldRestoreAfterStockTrim(
                assembly.visibleRecentCount,
                currentRecentCount,
                assembly.visiblePinnedCount,
                currentPinnedVisibleCount,
                assembly.result.size(),
                items.size());
        if (!shouldRestore) {
            return;
        }

        items.clear();
        items.addAll(assembly.result);
        handles.adapterRecentCountField.setInt(receiver, assembly.visibleRecentCount);
        handles.adapterPinnedVisibleCountField.setInt(receiver, assembly.visiblePinnedCount);
        handles.adapterRefreshMethod.invoke(receiver);
        support.logInfo(GboardClipboardRuntimeSupport.LOG_PREFIX
                + " restore-live-trim recentCount=" + currentRecentCount
                + " -> " + assembly.visibleRecentCount
                + ", pinnedVisibleCount=" + currentPinnedVisibleCount
                + " -> " + assembly.visiblePinnedCount
                + ", itemCount=" + items.size());
    }

    static boolean shouldRestoreAfterStockTrim(
            int expectedRecent, int currentRecent,
            int expectedPinned, int currentPinned,
            int expectedSize, int currentSize) {
        return expectedRecent != currentRecent
                || expectedPinned != currentPinned
                || expectedSize != currentSize;
    }

    private int distinctTimestampCountForAdapter(
            GboardClipboardRuntimeSupport.ReflectionHandles handles, List<Object> items,
            int recentCount) throws Throwable {
        Set<Long> values = new HashSet<Long>();
        for (int i = 1; i <= recentCount && i < items.size(); i++) {
            values.add(Long.valueOf(support.clipTimestamp(handles, items.get(i))));
        }
        return values.size();
    }

    private String describeAdapterRecent(GboardClipboardRuntimeSupport.ReflectionHandles handles,
            List<Object> items, int recentCount, int limit) throws Throwable {
        if (items.isEmpty() || recentCount <= 0) {
            return "[]";
        }
        List<String> rows = new ArrayList<String>();
        for (int i = 1; i <= recentCount && i < items.size() && rows.size() < limit; i++) {
            Object clip = items.get(i);
            rows.add("{id=" + support.clipId(handles, clip)
                    + ", ts=" + support.clipTimestamp(handles, clip)
                    + ", pinned=" + support.isPinned(handles, clip)
                    + ", itemType=" + support.clipItemType(handles, clip)
                    + "}");
        }
        return rows.toString();
    }

    private List<Object> adapterItems(GboardClipboardRuntimeSupport.ReflectionHandles handles,
            Object receiver) throws Throwable {
        return (List<Object>) handles.adapterItemsField.get(receiver);
    }

}
