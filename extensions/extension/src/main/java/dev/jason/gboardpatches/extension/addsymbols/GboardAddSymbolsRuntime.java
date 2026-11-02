package dev.jason.gboardpatches.extension.addsymbols;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"unused", "unchecked"})
public final class GboardAddSymbolsRuntime {
    private static final String TAG = "GboardAddSymbols";
    private static final long PENDING_FLOW_WINDOW_MS = 5_000L;
    private static final long PENDING_STOCK_EMOTICON_TAB_SWITCH_WINDOW_MS = 2_500L;

    private static final String CUSTOM_CATEGORY_RECENTS = "RECENTS";
    private static final String[] CUSTOM_EMOTICON_CATEGORY_KEYS = buildCustomCategoryKeys();
    private static final String[] CUSTOM_EMOTICON_CATEGORY_LABELS_ZH = {
            "最近",
            "熱門",
            "常用",
            "排版",
            "人物",
            "動物",
            "星號",
            "箭頭",
            "圖形",
            "數學",
            "語言",
            "貨幣"
    };
    private static final String[] CUSTOM_EMOTICON_CATEGORY_LABELS_EN = {
            "Recents",
            "Popular",
            "Common",
            "Typography",
            "People",
            "Animals",
            "Stars",
            "Arrows",
            "Shapes",
            "Mathematics",
            "Language",
            "Currency"
    };

    private static final String EMOTICON_EXTENSION_INTERFACE_CLASS =
            "com.google.android.apps.inputmethod.libs.expression.extension.IEmoticonExtension";

    private static final int SWITCH_KEYBOARD_KEYCODE = -0x2714;
    private static final int CUSTOM_EMOTICON_RECENTS_HEADER_ICON_RES_ID = 0x7f0804c6;
    private static final int CUSTOM_EMOTICON_RECENTS_HEADER_CONTENT_DESCRIPTION_RES_ID =
            0x7f140508;

    private static final String STOCK_RICH_SYMBOL_KEYBOARD_DATA = "rich_symbol";
    private static final String STOCK_EMOTICON_KEYBOARD_DATA = "emoticon";
    private static final String JASONDEV_SYMBOL_KEYBOARD_DATA = "jasondev_symbol";

    private static final String CUSTOM_SYMBOL_RECENTS_PREF = "gboardpatches_gboard_symbol_recents";
    private static final String CUSTOM_SYMBOL_RECENTS_KEY = "jasondev_symbol_recents";
    private static final String CUSTOM_SYMBOL_LAST_CATEGORY_KEY = "jasondev_symbol_last_category";
    private static final String CUSTOM_SYMBOL_RECENTS_DELIMITER = "\u0001";
    private static final int CUSTOM_SYMBOL_RECENTS_LIMIT = 48;

    private static final int CUSTOM_EMOTICON_SPAN_COUNT = 8;
    private static final int CUSTOM_EMOTICON_ITEM_VIEW_TYPE = 0x4843;
    private static final int STOCK_EMOTICON_ITEM_LAYOUT_RES_ID = 0x7f0e00e6;
    private static final int STOCK_EMOTICON_ITEM_TEXT_COLOR_ATTR_RES_ID = 0x7f0404bd;
    private static final int CUSTOM_EMOTICON_ITEM_MARGIN_DP = 0;
    private static final int CUSTOM_EMOTICON_ITEM_PADDING_HORIZONTAL_DP = 0;
    private static final int CUSTOM_EMOTICON_ITEM_PADDING_VERTICAL_DP = 0;
    private static final int CUSTOM_EMOTICON_FALLBACK_TEXT_MIN_SIZE_DP = 30;
    private static final float CUSTOM_EMOTICON_ITEM_ICON_SIZE_HEIGHT_RATIO = 0.92f;
    private static final float CUSTOM_EMOTICON_ITEM_GLYPH_HORIZONTAL_MARGIN_RATIO = 0.04f;
    private static final float CUSTOM_EMOTICON_ITEM_GLYPH_VERTICAL_MARGIN_RATIO = 0.04f;
    private static final float CUSTOM_EMOTICON_ITEM_GLYPH_TEXT_SCALE_X = 1f;
    private static final int CUSTOM_EMOTICON_HEADER_START_EDGE_VIEW_ID = 0x7f0b05e2;

    private static final Map<ClassLoader, Handles> REFLECTION_BY_LOADER =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> ACTIVE_CUSTOM_EMOTICON_KEYBOARDS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> CURRENT_CUSTOM_EMOTICON_RENDER_MODE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, String> CURRENT_CUSTOM_EMOTICON_CATEGORY_BY_KEYBOARD =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> CUSTOM_EMOTICON_RECYCLER_ADAPTERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> CUSTOM_EMOTICON_CONFIGURED_ITEM_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, android.content.res.ColorStateList>
            STOCK_EMOTICON_TEXT_COLORS_BY_THEME =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final AtomicBoolean ACTIVE_CUSTOM_EMOTICON_SESSION =
            new AtomicBoolean(false);
    private static final AtomicReference<Object> CURRENT_CUSTOM_EMOTICON_SESSION_KEYBOARD =
            new AtomicReference<>();
    private static final AtomicReference<Object> PENDING_STOCK_EMOTICON_TAB_SWITCH_KEYBOARD =
            new AtomicReference<>();
    private static final AtomicReference<Boolean> PENDING_STOCK_EMOTICON_TAB_SWITCH_RENDER_MODE =
            new AtomicReference<>();
    private static final AtomicBoolean PENDING_STOCK_EMOTICON_TAB_SWITCH_ACTIVE_SESSION =
            new AtomicBoolean(false);
    private static final AtomicReference<String> LAST_NON_RECENTS_CUSTOM_EMOTICON_CATEGORY =
            new AtomicReference<>(GboardAddSymbolsDataset.defaultCategoryKey());
    private static final Map<String, AtomicInteger> WARNING_LOG_COUNTS =
            new ConcurrentHashMap<>();

    private static long pendingCustomFlowUntilUptimeMs;
    private static long pendingStockEmoticonTabSwitchUntilUptimeMs;

    private GboardAddSymbolsRuntime() {
    }

    public static Object appendCustomCorpusItem(Object corpusList) {
        if (corpusList == null) {
            return null;
        }
        try {
            ClassLoader classLoader = corpusList.getClass().getClassLoader();
            if (classLoader == null) {
                return corpusList;
            }
            Handles handles = handles(classLoader);
            Object customKeyboardType = handles.buildKeyboardType(JASONDEV_SYMBOL_KEYBOARD_DATA);
            boolean hasCustomCorpus = containsExpressionCorpusKeyboardType(
                    handles, corpusList, customKeyboardType);
            Object normalizedCorpusList = removeExpressionCorpusKeyboardType(
                    handles, corpusList, STOCK_RICH_SYMBOL_KEYBOARD_DATA);
            if (hasCustomCorpus && normalizedCorpusList == corpusList) {
                logInfo("appendCustomCorpusItem: custom corpus already present, list="
                        + describeExpressionCorpusList(handles, corpusList));
                return corpusList;
            }

            Object appended = normalizedCorpusList;
            if (!hasCustomCorpus) {
                Object customCorpus = buildCustomExpressionCorpusItem(
                        handles, customKeyboardType, classLoader);
                appended = appendImmutableList(handles, normalizedCorpusList, customCorpus);
            }
            logInfo("appendCustomCorpusItem: hasCustom=" + hasCustomCorpus
                    + ", before=" + describeExpressionCorpusList(handles, corpusList)
                    + ", after=" + describeExpressionCorpusList(handles, appended));
            return appended;
        } catch (Throwable throwable) {
            logWarn("appendCustomCorpusItem failed", throwable);
            return corpusList;
        }
    }

    public static void ensureExtensionProviderMapping(Object extensionManager, Object requestedType) {
        if (extensionManager == null || requestedType == null) {
            return;
        }
        try {
            ClassLoader classLoader = extensionManager.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            Handles handles = handles(classLoader);
            String requestedTypeName = extractKeyboardTypeName(handles, requestedType);
            if (!shouldManageCustomSymbolProviderRequest(requestedTypeName)) {
                return;
            }
            Object mappedClass = lookupExtensionProviderClass(handles, extensionManager, requestedType);
            if (mappedClass != null) {
                logInfo("ensureExtensionProviderMapping: existing mapping for "
                        + requestedTypeName + " -> " + mappedClass);
                return;
            }
            Class<?> providerClass = Class.forName(
                    EMOTICON_EXTENSION_INTERFACE_CLASS, false, classLoader);
            injectExtensionProviderMapping(handles, extensionManager, requestedType, providerClass);
            logInfo("ensureExtensionProviderMapping: injected mapping for "
                    + requestedTypeName + " -> " + providerClass.getName());
        } catch (Throwable throwable) {
            logWarn("ensureExtensionProviderMapping failed", throwable);
        }
    }

    public static Object rewriteProviderRequestType(Object requestedType) {
        if (requestedType == null) {
            return null;
        }
        try {
            ClassLoader classLoader = requestedType.getClass().getClassLoader();
            if (classLoader == null) {
                return requestedType;
            }
            Handles handles = handles(classLoader);
            String requestedTypeName = extractKeyboardTypeName(handles, requestedType);
            if (!isCustomSymbolKeyboardType(requestedTypeName)) {
                if (requestedTypeName != null && !requestedTypeName.isBlank()) {
                    clearCurrentCustomEmoticonSessionKeyboard();
                    clearPendingCustomFlow();
                }
                return requestedType;
            }
            markPendingCustomFlow();
            logInfo("rewriteProviderRequestType: " + requestedTypeName + " -> "
                    + STOCK_EMOTICON_KEYBOARD_DATA);
            return handles.buildKeyboardType(STOCK_EMOTICON_KEYBOARD_DATA);
        } catch (Throwable throwable) {
            logWarn("rewriteProviderRequestType failed", throwable);
            return requestedType;
        }
    }

    public static Object bridgeProviderReceiver(
            Object providerImpl,
            Object originalKeyboardType,
            Object receiver) {
        if (providerImpl == null || originalKeyboardType == null || receiver == null) {
            return receiver;
        }
        try {
            ClassLoader classLoader = providerImpl.getClass().getClassLoader();
            if (classLoader == null) {
                return receiver;
            }
            Handles handles = handles(classLoader);
            String originalTypeName = extractKeyboardTypeName(handles, originalKeyboardType);
            if (!isCustomSymbolKeyboardType(originalTypeName)
                    || !handles.keyboardProviderReceiverInterfaceClass.isInstance(receiver)) {
                return receiver;
            }
            Object rewrittenKeyboardType = handles.buildKeyboardType(STOCK_EMOTICON_KEYBOARD_DATA);
            logInfo("bridgeProviderReceiver: original=" + originalTypeName
                    + ", rewritten=" + extractKeyboardTypeName(handles, rewrittenKeyboardType)
                    + ", receiver=" + receiver.getClass().getName());
            InvocationHandler handler = new KeyboardProviderReceiverBridge(
                    handles, originalKeyboardType, rewrittenKeyboardType, receiver);
            return Proxy.newProxyInstance(
                    handles.keyboardProviderReceiverInterfaceClass.getClassLoader(),
                    new Class<?>[] { handles.keyboardProviderReceiverInterfaceClass },
                    handler);
        } catch (Throwable throwable) {
            logWarn("bridgeProviderReceiver failed", throwable);
            return receiver;
        }
    }

    public static Object rewriteProviderWrapperKeyboardType(Object keyboardType) {
        return rewriteKeyboardTypeIfNeeded(keyboardType, true, false);
    }

    public static Object rewriteMetricsKeyboardType(Object keyboardType) {
        return rewriteKeyboardTypeIfNeeded(keyboardType, false, true);
    }

    public static Object rewriteNavigationKeyboardType(Object keyboardType) {
        if (keyboardType == null) {
            return null;
        }
        try {
            ClassLoader classLoader = keyboardType.getClass().getClassLoader();
            if (classLoader == null) {
                return keyboardType;
            }
            Handles handles = handles(classLoader);
            String keyboardTypeName = extractKeyboardTypeName(handles, keyboardType);
            if (isCustomSymbolKeyboardType(keyboardTypeName)) {
                Object rewrittenKeyboardType = handles.buildKeyboardType(JASONDEV_SYMBOL_KEYBOARD_DATA);
                logInfo("rewriteNavigationKeyboardType: custom->canonical, from="
                        + keyboardTypeName + ", to="
                        + extractKeyboardTypeName(handles, rewrittenKeyboardType));
                return rewrittenKeyboardType;
            }
            if (!isStockEmoticonKeyboardType(keyboardTypeName)) {
                return keyboardType;
            }
            boolean aliasToCustom = shouldAliasStockEmoticonBackToCustomForSession(
                    keyboardTypeName);
            Object rewrittenKeyboardType = handles.buildKeyboardType(
                    aliasToCustom ? JASONDEV_SYMBOL_KEYBOARD_DATA : STOCK_EMOTICON_KEYBOARD_DATA);
            logInfo("rewriteNavigationKeyboardType: stock->"
                    + (aliasToCustom ? "custom" : "stock")
                    + ", from=" + keyboardTypeName
                    + ", to=" + extractKeyboardTypeName(handles, rewrittenKeyboardType)
                    + ", pendingCustomFlow=" + hasPendingCustomFlow()
                    + ", pendingStockEmoticonTabSwitch=" + hasPendingStockEmoticonTabSwitch()
                    + ", activeCustomSession=" + ACTIVE_CUSTOM_EMOTICON_SESSION.get());
            return rewrittenKeyboardType;
        } catch (Throwable throwable) {
            logWarn("rewriteNavigationKeyboardType failed", throwable);
            return keyboardType;
        }
    }

    public static void onExpressionCorpusFooterTabClick(Object footerTabClickConsumer,
            Object corpusItem) {
        if (footerTabClickConsumer == null || corpusItem == null) {
            return;
        }
        try {
            restorePendingStockEmoticonTabSwitchStateIfExpired();
            ClassLoader classLoader = footerTabClickConsumer.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            Handles handles = handles(classLoader);
            if (!handles.expressionCorpusItemClass.isInstance(corpusItem)) {
                return;
            }
            Object keyboardType = handles.expressionCorpusItemKeyboardTypeField.get(corpusItem);
            String keyboardTypeName = extractKeyboardTypeName(handles, keyboardType);
            if (!isStockEmoticonKeyboardType(keyboardTypeName)
                    || !ACTIVE_CUSTOM_EMOTICON_SESSION.get()) {
                return;
            }
            markPendingStockEmoticonTabSwitch();
            logInfo("onExpressionCorpusFooterTabClick: selectedType=" + keyboardTypeName
                    + ", pendingCustomFlow=" + hasPendingCustomFlow()
                    + ", activeCustomSession=" + ACTIVE_CUSTOM_EMOTICON_SESSION.get()
                    + ", pendingSourceKeyboard="
                    + (PENDING_STOCK_EMOTICON_TAB_SWITCH_KEYBOARD.get() != null));
        } catch (Throwable throwable) {
            logWarn("onExpressionCorpusFooterTabClick failed", throwable);
        }
    }

    public static void onKeyboardReady(
            Object keyboardWrapper,
            Object keyboard,
            Object keyboardViewDef,
            Object keyboardType) {
        if (keyboard == null || keyboardType == null) {
            return;
        }
        try {
            ClassLoader classLoader = keyboard.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            Handles handles = handles(classLoader);
            String keyboardTypeName = extractKeyboardTypeName(handles, keyboardType);
            boolean isActiveCustomSession = isCustomSymbolKeyboardType(keyboardTypeName)
                    && ACTIVE_CUSTOM_EMOTICON_KEYBOARDS.containsKey(keyboard);
            boolean pendingStockEmoticonTabSwitch = hasPendingStockEmoticonTabSwitch();
            ACTIVE_CUSTOM_EMOTICON_SESSION.set(isActiveCustomSession);
            updateCurrentCustomEmoticonRenderMode(handles, keyboard, keyboardTypeName);
            logInfo("onKeyboardReady: type=" + keyboardTypeName
                    + ", activeCustomSession=" + isActiveCustomSession
                    + ", keyboard=" + keyboard.getClass().getName());
            if (isActiveCustomSession) {
                rememberCurrentCustomEmoticonSessionKeyboard(keyboard);
                clearPendingStockEmoticonTabSwitch();
                clearPendingCustomFlow();
                return;
            }
            if (pendingStockEmoticonTabSwitch && isStockEmoticonKeyboardType(keyboardTypeName)) {
                clearPendingStockEmoticonTabSwitch();
            }
            clearCurrentCustomEmoticonSessionKeyboard();
            if (isEmoticonKeyboardInstance(handles, keyboard)) {
                restoreStockEmoticonRecyclerLayout(handles, keyboard);
            }
        } catch (Throwable throwable) {
            logWarn("onKeyboardReady failed", throwable);
        }
    }

    public static void onEmoticonKeyboardConstructed(Object keyboard) {
        if (keyboard == null || !hasPendingCustomFlow()) {
            return;
        }
        ACTIVE_CUSTOM_EMOTICON_KEYBOARDS.put(keyboard, Boolean.TRUE);
        rememberCurrentCustomEmoticonSessionKeyboard(keyboard);
    }

    public static void onEmoticonBodyReady(Object keyboard) {
        if (keyboard == null) {
            return;
        }
        try {
            ClassLoader classLoader = keyboard.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            Handles handles = handles(classLoader);
            if (!isEmoticonKeyboardInstance(handles, keyboard)) {
                return;
            }
            if (isCustomEmoticonRenderMode(keyboard)) {
                maybeInitializeCustomEmoticonBody(handles, keyboard);
            } else {
                restoreStockEmoticonRecyclerLayout(handles, keyboard);
            }
        } catch (Throwable throwable) {
            logWarn("onEmoticonBodyReady failed", throwable);
        }
    }

    public static int resolveSelectedCategoryIndex(Object keyboard, Object categoryList) {
        if (!isActiveCustomEmoticonKeyboard(keyboard)) {
            return -1;
        }
        try {
            ClassLoader classLoader = keyboard.getClass().getClassLoader();
            if (classLoader == null) {
                return -1;
            }
            Handles handles = handles(classLoader);
            return clampCustomEmoticonCategoryIndex(resolveCustomEmoticonSelectedIndex(handles, keyboard));
        } catch (Throwable throwable) {
            logWarn("resolveSelectedCategoryIndex failed", throwable);
            return -1;
        }
    }

    public static String overrideCategoryNameFromIndex(Object keyboard, int categoryIndex) {
        if (!isActiveCustomEmoticonKeyboard(keyboard)) {
            return null;
        }
        int index = clampCustomHeaderBuilderCategoryIndex(categoryIndex);
        return CUSTOM_EMOTICON_CATEGORY_KEYS[index];
    }

    public static boolean handleCategoryChange(Object keyboard, int categoryIndex, int reason) {
        if (!isActiveCustomEmoticonKeyboard(keyboard)) {
            return false;
        }
        try {
            ClassLoader classLoader = keyboard.getClass().getClassLoader();
            if (classLoader == null) {
                return false;
            }
            Handles handles = handles(classLoader);
            int index = clampCustomEmoticonCategoryIndex(categoryIndex);
            rememberCurrentCustomEmoticonSessionKeyboard(keyboard);
            rememberCustomEmoticonCategory(handles, keyboard, CUSTOM_EMOTICON_CATEGORY_KEYS[index]);
            return true;
        } catch (Throwable throwable) {
            logWarn("handleCategoryChange failed", throwable);
            return false;
        }
    }

    public static boolean handleCategoryBind(Object keyboard, Object recyclerView, String categoryName) {
        if (keyboard == null || recyclerView == null || categoryName == null
                || !isActiveCustomEmoticonKeyboard(keyboard)) {
            return false;
        }
        try {
            ClassLoader classLoader = keyboard.getClass().getClassLoader();
            if (classLoader == null) {
                return false;
            }
            Handles handles = handles(classLoader);
            if (!handles.emoticonRecyclerViewClass.isInstance(recyclerView)) {
                return false;
            }
            List<String> symbols = resolveCustomEmoticonCategorySymbols(handles, keyboard, categoryName);
            if (symbols == null) {
                return false;
            }
            int customCategoryIndex = clampCustomEmoticonCategoryIndex(
                    mapToCustomEmoticonCategoryIndex(categoryName));
            String customCategoryKey = CUSTOM_EMOTICON_CATEGORY_KEYS[customCategoryIndex];
            rememberCurrentCustomEmoticonSessionKeyboard(keyboard);
            CURRENT_CUSTOM_EMOTICON_CATEGORY_BY_KEYBOARD.put(keyboard, customCategoryKey);
            rememberCustomEmoticonCategory(handles, keyboard, customCategoryKey);
            ensureCustomEmoticonRecyclerAdapterAttached(handles, keyboard, recyclerView);
            handles.emoticonRecyclerSetItemsMethod.invoke(recyclerView, symbols);
            configureCustomEmoticonRecyclerLayout(handles, recyclerView);
            Object emptyState = handles.emoticonEmptyStateField.get(keyboard);
            boolean hasSymbols = !symbols.isEmpty();
            if (recyclerView instanceof View) {
                ((View) recyclerView).setVisibility(hasSymbols ? View.VISIBLE : View.GONE);
            }
            if (emptyState instanceof View) {
                ((View) emptyState).setVisibility(hasSymbols ? View.GONE : View.VISIBLE);
            }
            return true;
        } catch (Throwable throwable) {
            logWarn("handleCategoryBind failed", throwable);
            return false;
        }
    }

    public static boolean bindCustomViewHolder(Object adapter, Object viewHolder, int position) {
        if (adapter == null || viewHolder == null
                || !CUSTOM_EMOTICON_RECYCLER_ADAPTERS.containsKey(adapter)) {
            return false;
        }
        try {
            Handles handles = handles(adapter.getClass().getClassLoader());
            Object itemView = handles.recyclerViewViewHolderItemViewField.get(viewHolder);
            if (!(itemView instanceof View)) {
                return false;
            }
            List<?> items = (List<?>) handles.emoticonRecyclerAdapterItemsField.get(adapter);
            if (items == null || position < 0 || position >= items.size()) {
                return false;
            }
            Object value = items.get(position);
            if (!(value instanceof String) || ((String) value).isBlank()) {
                return false;
            }
            String symbol = (String) value;
            if (!(itemView instanceof View view)) {
                return false;
            }
            if (view instanceof CustomEmoticonGlyphView glyphView) {
                glyphView.bindSymbol(symbol);
            } else if (view instanceof TextView textView) {
                textView.setContentDescription(symbol);
                textView.setIncludeFontPadding(false);
                textView.setTypeface(Typeface.DEFAULT);
                textView.setAllCaps(false);
                textView.setGravity(android.view.Gravity.CENTER);
                textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                textView.setSingleLine(true);
                textView.setMinLines(1);
                textView.setMaxLines(1);
                textView.setLineSpacing(0f, 1f);
                textView.setTextColor(resolveCustomEmoticonTextColors(textView.getContext()));
                textView.setTextScaleX(CUSTOM_EMOTICON_ITEM_GLYPH_TEXT_SCALE_X);
                textView.setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        resolveCustomEmoticonTextSizePx(textView));
                textView.setText(symbol);
            } else {
                view.setContentDescription(symbol);
            }
            Object clickConsumer = handles.emoticonRecyclerAdapterClickConsumerField.get(adapter);
            view.setOnClickListener(clicked -> invokeConsumerAccept(handles, clickConsumer, symbol));
            tightenCustomEmoticonItemView(handles, viewHolder);
            return true;
        } catch (Throwable throwable) {
            logWarn("bindCustomViewHolder failed", throwable);
            return false;
        }
    }

    public static int resolveCustomViewType(Object adapter, int position) {
        if (adapter == null || !CUSTOM_EMOTICON_RECYCLER_ADAPTERS.containsKey(adapter)) {
            return -1;
        }
        return CUSTOM_EMOTICON_ITEM_VIEW_TYPE;
    }

    public static Object createCustomViewHolder(
            Object adapter,
            Object parentObject,
            int requestedViewType) {
        if (adapter == null
                || !(parentObject instanceof ViewGroup parent)
                || !CUSTOM_EMOTICON_RECYCLER_ADAPTERS.containsKey(adapter)) {
            return null;
        }
        try {
            ClassLoader classLoader = adapter.getClass().getClassLoader();
            if (classLoader == null) {
                return null;
            }
            Handles handles = handles(classLoader);
            Object itemViewHolder = createCustomEmoticonViewHolder(handles, adapter, parent);
            if (itemViewHolder == null) {
                return null;
            }
            tightenCustomEmoticonItemView(handles, itemViewHolder);
            return itemViewHolder;
        } catch (Throwable throwable) {
            logWarn("createCustomViewHolder failed", throwable);
            return null;
        }
    }

    private static void maybeInitializeCustomEmoticonBody(Handles handles, Object keyboard)
            throws Throwable {
        Object recyclerView = handles.emoticonBodyRecyclerField.get(keyboard);
        if (recyclerView == null || !handles.emoticonRecyclerViewClass.isInstance(recyclerView)) {
            return;
        }
        applyCustomEmoticonCategoryIdentity(handles, keyboard);
        configureCustomEmoticonRecyclerLayout(handles, recyclerView);
        ensureCustomEmoticonRecyclerAdapterAttached(handles, keyboard, recyclerView);
        String initialCustomCategory = resolveInitialCustomEmoticonCategory(handles, keyboard);
        handles.emoticonSetEmoticonsMethod.invoke(keyboard, recyclerView, initialCustomCategory);
        int customCategoryIndex = clampCustomEmoticonCategoryIndex(
                mapToCustomEmoticonCategoryIndex(initialCustomCategory));
        String customCategoryKey = CUSTOM_EMOTICON_CATEGORY_KEYS[customCategoryIndex];
        CURRENT_CUSTOM_EMOTICON_CATEGORY_BY_KEYBOARD.put(keyboard, customCategoryKey);
        rememberCustomEmoticonCategory(handles, keyboard, customCategoryKey);
    }

    private static void ensureCustomEmoticonRecyclerAdapterAttached(
            Handles handles,
            Object keyboard,
            Object recyclerView) throws Throwable {
        if (handles == null || keyboard == null || recyclerView == null
                || !handles.emoticonRecyclerViewClass.isInstance(recyclerView)) {
            return;
        }
        Object adapter = handles.recyclerViewAdapterField.get(recyclerView);
        if (adapter != null && CUSTOM_EMOTICON_RECYCLER_ADAPTERS.containsKey(adapter)) {
            markCustomEmoticonRecyclerAdapter(handles, recyclerView, true);
            return;
        }
        Object descriptionProvider = handles.emoticonDescriptionProviderField.get(keyboard);
        Object itemClickConsumer = handles.emoticonItemClickConsumerConstructor.newInstance(
                keyboard, Integer.valueOf(7));
        handles.emoticonRecyclerAttachAdapterMethod.invoke(
                recyclerView, descriptionProvider, itemClickConsumer);
        markCustomEmoticonRecyclerAdapter(handles, recyclerView, true);
    }

    private static void configureCustomEmoticonRecyclerLayout(Handles handles, Object recyclerView)
            throws Throwable {
        configureEmoticonRecyclerLayout(handles, recyclerView, CUSTOM_EMOTICON_SPAN_COUNT, true);
        markCustomEmoticonRecyclerAdapter(handles, recyclerView, true);
    }

    private static void restoreStockEmoticonRecyclerLayout(Handles handles, Object keyboard)
            throws Throwable {
        if (handles == null || keyboard == null) {
            return;
        }
        Object recyclerView = handles.emoticonBodyRecyclerField.get(keyboard);
        if (recyclerView == null || !handles.emoticonRecyclerViewClass.isInstance(recyclerView)) {
            return;
        }
        int stockSpanCount = handles.emoticonRecyclerSpanCountField.getInt(recyclerView);
        configureEmoticonRecyclerLayout(handles, recyclerView, stockSpanCount, false);
        markCustomEmoticonRecyclerAdapter(handles, recyclerView, false);
    }

    private static void configureEmoticonRecyclerLayout(
            Handles handles,
            Object recyclerView,
            int spanCount,
            boolean custom) throws Throwable {
        if (handles == null || recyclerView == null
                || !handles.supportRecyclerViewClass.isInstance(recyclerView)) {
            return;
        }
        Object layoutManager = handles.recyclerViewLayoutManagerField.get(recyclerView);
        if (layoutManager == null || !handles.gridLayoutManagerClass.isInstance(layoutManager)) {
            return;
        }
        handles.gridLayoutManagerSetSpanCountMethod.invoke(layoutManager, Integer.valueOf(spanCount));
        if (custom) {
            relaxRecyclerViewBoundsForCustomEmoticon(recyclerView);
        }
        if (recyclerView instanceof View) {
            ((View) recyclerView).requestLayout();
            ((View) recyclerView).invalidate();
        }
        if (!custom) {
            Object adapter = handles.recyclerViewAdapterField.get(recyclerView);
            if (adapter != null) {
                CUSTOM_EMOTICON_RECYCLER_ADAPTERS.remove(adapter);
            }
        }
    }

    private static void markCustomEmoticonRecyclerAdapter(
            Handles handles,
            Object recyclerView,
            boolean custom) throws Throwable {
        if (handles == null || recyclerView == null
                || !handles.supportRecyclerViewClass.isInstance(recyclerView)) {
            return;
        }
        Object adapter = handles.recyclerViewAdapterField.get(recyclerView);
        if (adapter == null) {
            return;
        }
        if (custom) {
            CUSTOM_EMOTICON_RECYCLER_ADAPTERS.put(adapter, Boolean.TRUE);
            configureCustomEmoticonRecyclerAdapterDensity(handles, recyclerView, adapter);
        } else {
            CUSTOM_EMOTICON_RECYCLER_ADAPTERS.remove(adapter);
        }
    }

    private static void configureCustomEmoticonRecyclerAdapterDensity(
            Handles handles,
            Object recyclerView,
            Object adapter) throws Throwable {
        if (!(recyclerView instanceof View)
                || !handles.emoticonRecyclerAdapterClass.isInstance(adapter)) {
            return;
        }
        Context context = ((View) recyclerView).getContext();
        int spacingPx = dpToPx(context, CUSTOM_EMOTICON_ITEM_MARGIN_DP);
        handles.emoticonRecyclerAdapterItemSpacingField.setInt(adapter, spacingPx);
    }

    private static Object createCustomEmoticonViewHolder(
            Handles handles,
            Object adapter,
            ViewGroup parent) throws Throwable {
        if (handles == null || adapter == null || parent == null
                || handles.recyclerViewViewHolderConstructor == null) {
            return null;
        }
        CustomEmoticonGlyphView itemView = new CustomEmoticonGlyphView(parent.getContext());
        Context context = itemView.getContext();
        int itemHeightPx = handles.emoticonRecyclerAdapterItemHeightField.getInt(adapter);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                itemHeightPx > 0 ? itemHeightPx : ViewGroup.LayoutParams.WRAP_CONTENT);
        int marginPx = dpToPx(context, CUSTOM_EMOTICON_ITEM_MARGIN_DP);
        layoutParams.setMargins(marginPx, 0, marginPx, 0);
        itemView.setLayoutParams(layoutParams);
        itemView.setBackgroundColor(Color.TRANSPARENT);
        itemView.setPadding(0, 0, 0, 0);
        itemView.setPaddingRelative(0, 0, 0, 0);
        itemView.setMinimumWidth(0);
        itemView.setMinimumHeight(0);
        itemView.setFocusable(true);
        itemView.setClickable(true);
        itemView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return handles.recyclerViewViewHolderConstructor.newInstance(itemView);
    }

    private static void tightenCustomEmoticonItemView(
            Handles handles,
            Object itemViewHolder) throws Throwable {
        if (handles == null || itemViewHolder == null
                || !handles.recyclerViewViewHolderClass.isInstance(itemViewHolder)) {
            return;
        }
        Object itemView = handles.recyclerViewViewHolderItemViewField.get(itemViewHolder);
        if (!(itemView instanceof View view)) {
            return;
        }
        if (Boolean.TRUE.equals(CUSTOM_EMOTICON_CONFIGURED_ITEM_VIEWS.get(view))) {
            return;
        }
        Context context = view.getContext();
        int horizontalPaddingPx = dpToPx(context, CUSTOM_EMOTICON_ITEM_PADDING_HORIZONTAL_DP);
        int verticalPaddingPx = dpToPx(context, CUSTOM_EMOTICON_ITEM_PADDING_VERTICAL_DP);
        view.setClipToOutline(false);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setPadding(horizontalPaddingPx, verticalPaddingPx,
                horizontalPaddingPx, verticalPaddingPx);
        view.setPaddingRelative(horizontalPaddingPx, verticalPaddingPx,
                horizontalPaddingPx, verticalPaddingPx);
        view.setMinimumWidth(0);
        view.setMinimumHeight(0);
        view.setBackgroundColor(Color.TRANSPARENT);
        if (view instanceof TextView textView) {
            textView.setMinWidth(0);
            textView.setMinHeight(0);
            textView.setMinimumWidth(0);
            textView.setMinimumHeight(0);
            textView.setIncludeFontPadding(true);
            textView.setGravity(android.view.Gravity.CENTER);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
            textView.setText("");
            textView.setTextScaleX(1f);
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams marginLayoutParams) {
            int marginPx = dpToPx(context, CUSTOM_EMOTICON_ITEM_MARGIN_DP);
            if (marginLayoutParams.leftMargin != marginPx
                    || marginLayoutParams.topMargin != 0
                    || marginLayoutParams.rightMargin != marginPx
                    || marginLayoutParams.bottomMargin != 0) {
                marginLayoutParams.setMargins(marginPx, 0, marginPx, 0);
                view.setLayoutParams(marginLayoutParams);
            }
        }
        CUSTOM_EMOTICON_CONFIGURED_ITEM_VIEWS.put(view, Boolean.TRUE);
        view.requestLayout();
        view.invalidate();
    }

    private static android.content.res.ColorStateList resolveCustomEmoticonTextColors(
            Context context) {
        if (context == null) {
            return android.content.res.ColorStateList.valueOf(Color.WHITE);
        }
        Object themeKey = context.getTheme();
        if (themeKey != null) {
            android.content.res.ColorStateList cached =
                    STOCK_EMOTICON_TEXT_COLORS_BY_THEME.get(themeKey);
            if (cached != null) {
                return cached;
            }
        }
        android.content.res.ColorStateList textColors =
                resolveStockEmoticonTextColorsFromLayout(context);
        if (textColors == null) {
            textColors = resolveThemeColorStateList(context,
                    STOCK_EMOTICON_ITEM_TEXT_COLOR_ATTR_RES_ID);
        }
        if (textColors == null) {
            textColors = resolveThemeColorStateList(context, android.R.attr.textColorPrimary);
        }
        if (textColors == null) {
            textColors = android.content.res.ColorStateList.valueOf(Color.WHITE);
        }
        if (themeKey != null) {
            STOCK_EMOTICON_TEXT_COLORS_BY_THEME.put(themeKey, textColors);
        }
        return textColors;
    }

    private static android.content.res.ColorStateList resolveStockEmoticonTextColorsFromLayout(
            Context context) {
        if (context == null) {
            return null;
        }
        try {
            View stockItemView = android.view.LayoutInflater.from(context)
                    .inflate(STOCK_EMOTICON_ITEM_LAYOUT_RES_ID, null, false);
            if (stockItemView instanceof TextView textView) {
                android.content.res.ColorStateList textColors = textView.getTextColors();
                if (textColors != null) {
                    return textColors;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to theme attr lookup when the stock item layout cannot be inflated.
        }
        return null;
    }

    private static android.content.res.ColorStateList resolveThemeColorStateList(
            Context context,
            int attrResId) {
        if (context == null) {
            return null;
        }
        android.content.res.TypedArray typedArray = context.obtainStyledAttributes(
                new int[] { attrResId });
        try {
            android.content.res.ColorStateList colorStateList = typedArray.getColorStateList(0);
            if (colorStateList != null) {
                return colorStateList;
            }
            if (!typedArray.hasValue(0)) {
                return null;
            }
            return android.content.res.ColorStateList.valueOf(
                    typedArray.getColor(0, Color.WHITE));
        } finally {
            typedArray.recycle();
        }
    }

    private static float resolveCustomEmoticonTextSizePx(TextView textView) {
        if (textView == null) {
            return 0f;
        }
        Context context = textView.getContext();
        ViewGroup.LayoutParams layoutParams = textView.getLayoutParams();
        int itemHeightPx = 0;
        if (layoutParams != null && layoutParams.height > 0) {
            itemHeightPx = layoutParams.height;
        } else if (textView.getMeasuredHeight() > 0) {
            itemHeightPx = textView.getMeasuredHeight();
        } else if (textView.getHeight() > 0) {
            itemHeightPx = textView.getHeight();
        }
        int itemWidthPx = 0;
        if (layoutParams != null && layoutParams.width > 0) {
            itemWidthPx = layoutParams.width;
        } else if (textView.getMeasuredWidth() > 0) {
            itemWidthPx = textView.getMeasuredWidth();
        } else if (textView.getWidth() > 0) {
            itemWidthPx = textView.getWidth();
        }
        float fallbackPx = dpToPx(context, CUSTOM_EMOTICON_FALLBACK_TEXT_MIN_SIZE_DP);
        if (itemHeightPx <= 0 && itemWidthPx <= 0) {
            return fallbackPx;
        }
        if (itemHeightPx <= 0) {
            itemHeightPx = itemWidthPx;
        }
        if (itemWidthPx <= 0) {
            itemWidthPx = itemHeightPx;
        }
        float availableWidth = itemWidthPx
                * (1f - (CUSTOM_EMOTICON_ITEM_GLYPH_HORIZONTAL_MARGIN_RATIO * 2f));
        float availableHeight = itemHeightPx
                * (1f - (CUSTOM_EMOTICON_ITEM_GLYPH_VERTICAL_MARGIN_RATIO * 2f));
        availableHeight = Math.min(availableHeight,
                itemHeightPx * CUSTOM_EMOTICON_ITEM_ICON_SIZE_HEIGHT_RATIO);
        float glyphSidePx = Math.max(1f, Math.min(availableWidth, availableHeight));
        return Math.max(fallbackPx, glyphSidePx * 0.94f);
    }

    private static void relaxRecyclerViewBoundsForCustomEmoticon(Object recyclerView) {
        if (!(recyclerView instanceof View)) {
            return;
        }
        View view = (View) recyclerView;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams =
                    (ViewGroup.MarginLayoutParams) layoutParams;
            if (marginLayoutParams.leftMargin != 0 || marginLayoutParams.rightMargin != 0) {
                marginLayoutParams.leftMargin = 0;
                marginLayoutParams.rightMargin = 0;
                view.setLayoutParams(marginLayoutParams);
            }
        }
        if (view.getPaddingLeft() != 0 || view.getPaddingRight() != 0) {
            view.setPadding(0, view.getPaddingTop(), 0, view.getPaddingBottom());
            view.setPaddingRelative(0, view.getPaddingTop(), 0, view.getPaddingBottom());
        }
        restoreAncestorClipping(view);
    }

    private static void restoreAncestorClipping(View view) {
        if (view == null) {
            return;
        }
        View current = view;
        int depth = 0;
        if (current instanceof ViewGroup) {
            ((ViewGroup) current).setClipChildren(true);
            ((ViewGroup) current).setClipToPadding(true);
        }
        while (current.getParent() instanceof ViewGroup && depth < 8) {
            ViewGroup parent = (ViewGroup) current.getParent();
            parent.setClipChildren(true);
            parent.setClipToPadding(true);
            current = parent;
            depth++;
        }
    }

    private static void invokeConsumerAccept(Handles handles, Object consumer, Object value) {
        if (handles == null || consumer == null || !handles.consumerClass.isInstance(consumer)) {
            return;
        }
        try {
            handles.consumerAcceptMethod.invoke(consumer, value);
        } catch (Throwable throwable) {
            logWarn("invokeConsumerAccept failed", throwable);
        }
    }

    public static void onHeaderCallbackAfter(Object receiver) {
        if (receiver == null) {
            return;
        }
        try {
            ClassLoader classLoader = receiver.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            Handles handles = handles(classLoader);
            if (!isActiveCustomEmoticonKeyboard(receiver)) {
                return;
            }
            rememberCurrentCustomEmoticonSessionKeyboard(receiver);
            applyCustomEmoticonCategoryIdentity(handles, receiver);
            Object recyclerView = handles.emoticonBodyRecyclerField.get(receiver);
            if (recyclerView != null && handles.emoticonRecyclerViewClass.isInstance(recyclerView)) {
                markCustomEmoticonRecyclerAdapter(handles, recyclerView, true);
                configureCustomEmoticonRecyclerLayout(handles, recyclerView);
            }
            applyCustomEmoticonHeader(handles, receiver);
        } catch (Throwable throwable) {
            logWarn("onHeaderCallbackAfter failed", throwable);
        }
    }

    public static boolean shouldSkipHeaderStartEdgeUpdate(Object headerView) {
        if (!hasTrackedCustomEmoticonSessionKeyboard() || !(headerView instanceof View)) {
            return false;
        }
        return ((View) headerView).findViewById(CUSTOM_EMOTICON_HEADER_START_EDGE_VIEW_ID) == null;
    }

    public static boolean shouldSkipDetachedViewTransform(
            Object matrixObject,
            Object fromViewObject,
            Object toViewObject) {
        if (!hasTrackedCustomEmoticonSessionKeyboard()
                || !(matrixObject instanceof android.graphics.Matrix)
                || !(fromViewObject instanceof View)
                || !(toViewObject instanceof View)) {
            return false;
        }
        android.graphics.Matrix matrix = (android.graphics.Matrix) matrixObject;
        View fromView = (View) fromViewObject;
        View toView = (View) toViewObject;
        if (fromView.isAttachedToWindow() && toView.isAttachedToWindow()) {
            return false;
        }
        matrix.reset();
        return true;
    }

    public static void onEmoticonRecyclerAdapterConstructed(
            Object adapter, Object clickConsumer) {
        if (adapter == null || clickConsumer == null) {
            return;
        }
        try {
            ClassLoader classLoader = clickConsumer.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            Handles handles = handles(classLoader);
            Object keyboard = resolveCustomEmoticonHistoryWriteKeyboard(handles, clickConsumer);
            if (keyboard != null) {
                CUSTOM_EMOTICON_RECYCLER_ADAPTERS.put(adapter, Boolean.TRUE);
            }
        } catch (Throwable throwable) {
            logWarn("onEmoticonRecyclerAdapterConstructed failed", throwable);
        }
    }

    public static boolean interceptHistoryWrite(
            Object consumer, Object historyManager, String symbol) {
        if (consumer == null || historyManager == null || symbol == null || symbol.isBlank()) {
            return false;
        }
        try {
            ClassLoader classLoader = consumer.getClass().getClassLoader();
            if (classLoader == null) {
                return false;
            }
            Handles handles = handles(classLoader);
            return interceptHistoryWriteForConsumer(consumer, historyManager, symbol,
                    handles.emoticonItemClickConsumerClass,
                    handles.emoticonItemClickConsumerCaseField,
                    handles.emoticonItemClickConsumerReceiverField,
                    handles.emoticonKeyboardContextField, handles.emoticonHistoryField);
        } catch (Throwable throwable) {
            logWarn("interceptHistoryWrite failed", throwable);
            return false;
        }
    }

    static boolean interceptHistoryWriteForConsumer(Object consumer, Object historyManager,
            String symbol, Class<?> consumerType, Field callbackCaseField, Field receiverField,
            Field contextField, Field sinkField) throws IllegalAccessException {
        if (consumer == null || !consumerType.isInstance(consumer) || historyManager == null
                || symbol == null || symbol.isBlank()) return false;
        Object callbackCase = callbackCaseField.get(consumer);
        if (!isCustomEmoticonHistoryConsumerCase(callbackCase)) return false;
        Object keyboard = receiverField.get(consumer);
        if (!isCustomEmoticonRenderMode(keyboard)) return false;
        Object expectedSink = sinkField.get(keyboard);
        Object value = contextField.get(keyboard);
        if (!(value instanceof Context context)) return false;
        return GboardCustomSymbolHistoryRedirect.write(callbackCase, true,
                expectedSink, historyManager, () -> {
                    try {
                        storeCustomRecentSymbol(context, symbol);
                    } catch (Throwable failure) {
                        logWarn("custom history persistence failed", failure);
                    }
                });
    }

    private static Object rewriteKeyboardTypeIfNeeded(
            Object keyboardType,
            boolean toCustomWhenPending,
            boolean toStockWhenCustom) {
        if (keyboardType == null) {
            return null;
        }
        try {
            ClassLoader classLoader = keyboardType.getClass().getClassLoader();
            if (classLoader == null) {
                return keyboardType;
            }
            Handles handles = handles(classLoader);
            String keyboardTypeName = extractKeyboardTypeName(handles, keyboardType);
            if (isCustomSymbolKeyboardType(keyboardTypeName)) {
                if (toStockWhenCustom) {
                    Object rewrittenKeyboardType = handles.buildKeyboardType(STOCK_EMOTICON_KEYBOARD_DATA);
                    logInfo("rewriteKeyboardTypeIfNeeded: custom->stock, from="
                            + keyboardTypeName + ", to="
                            + extractKeyboardTypeName(handles, rewrittenKeyboardType));
                    return rewrittenKeyboardType;
                }
                if (toCustomWhenPending) {
                    Object rewrittenKeyboardType = handles.buildKeyboardType(JASONDEV_SYMBOL_KEYBOARD_DATA);
                    logInfo("rewriteKeyboardTypeIfNeeded: custom->canonical, from="
                            + keyboardTypeName + ", to="
                            + extractKeyboardTypeName(handles, rewrittenKeyboardType));
                    return rewrittenKeyboardType;
                }
                return keyboardType;
            }
            if (toCustomWhenPending && isStockEmoticonKeyboardType(keyboardTypeName)) {
                if (hasPendingCustomFlow()) {
                    Object rewrittenKeyboardType = handles.buildKeyboardType(JASONDEV_SYMBOL_KEYBOARD_DATA);
                    logInfo("rewriteKeyboardTypeIfNeeded: stock->custom, from="
                            + keyboardTypeName + ", to="
                            + extractKeyboardTypeName(handles, rewrittenKeyboardType));
                    return rewrittenKeyboardType;
                }
                Object rewrittenKeyboardType = handles.buildKeyboardType(STOCK_EMOTICON_KEYBOARD_DATA);
                logInfo("rewriteKeyboardTypeIfNeeded: stock->canonical stock, from="
                        + keyboardTypeName + ", to="
                        + extractKeyboardTypeName(handles, rewrittenKeyboardType));
                return rewrittenKeyboardType;
            }
            return keyboardType;
        } catch (Throwable throwable) {
            logWarn("rewriteKeyboardTypeIfNeeded failed", throwable);
            return keyboardType;
        }
    }

    private static Object rewriteKeyboardTypeToCustom(Object keyboardType) {
        if (keyboardType == null) {
            return null;
        }
        try {
            ClassLoader classLoader = keyboardType.getClass().getClassLoader();
            if (classLoader == null) {
                return keyboardType;
            }
            Handles handles = handles(classLoader);
            if (!isStockEmoticonKeyboardType(extractKeyboardTypeName(handles, keyboardType))) {
                return keyboardType;
            }
            return handles.buildKeyboardType(JASONDEV_SYMBOL_KEYBOARD_DATA);
        } catch (Throwable throwable) {
            logWarn("rewriteKeyboardTypeToCustom failed", throwable);
            return keyboardType;
        }
    }

    private static void applyCustomEmoticonCategoryIdentity(Handles handles, Object receiver)
            throws Throwable {
        if (handles == null || receiver == null) {
            return;
        }
        Object[] entries = new Object[CUSTOM_EMOTICON_CATEGORY_KEYS.length * 2];
        for (int index = 0; index < CUSTOM_EMOTICON_CATEGORY_KEYS.length; index++) {
            entries[index * 2] = Integer.valueOf(index);
            entries[index * 2 + 1] = CUSTOM_EMOTICON_CATEGORY_KEYS[index];
        }
        Object categoryMap = handles.integerStringMapConstructor.newInstance(
                entries, Integer.valueOf(CUSTOM_EMOTICON_CATEGORY_KEYS.length));
        handles.emoticonCategoryNameMapField.set(receiver, categoryMap);
    }

    private static void applyCustomEmoticonHeader(Handles handles, Object receiver)
            throws Throwable {
        Object headerController = handles.emoticonHeaderControllerField.get(receiver);
        if (headerController == null) {
            return;
        }
        int selectedIndex = clampCustomEmoticonCategoryIndex(
                resolveCustomEmoticonSelectedIndex(handles, receiver));
        Object model = buildCustomEmoticonHeaderModel(handles, receiver, selectedIndex);
        handles.headerControllerSetModelMethod.invoke(headerController, model);
        syncCustomEmoticonHeaderSelection(handles, receiver, selectedIndex);
        logInfo("applyCustomEmoticonHeader: selectedIndex=" + selectedIndex
                + ", selectedKey=" + CUSTOM_EMOTICON_CATEGORY_KEYS[selectedIndex]);
    }

    private static int resolveCustomEmoticonSelectedIndex(Handles handles, Object receiver)
            throws Throwable {
        return mapToCustomEmoticonCategoryIndex(resolveInitialCustomEmoticonCategory(handles, receiver));
    }

    private static String resolveInitialCustomEmoticonCategory(Handles handles, Object receiver)
            throws Throwable {
        String currentCategory = CURRENT_CUSTOM_EMOTICON_CATEGORY_BY_KEYBOARD.get(receiver);
        if (isValidCustomEmoticonCategoryKey(currentCategory)) {
            if (!CUSTOM_CATEGORY_RECENTS.equals(currentCategory)) {
                return currentCategory;
            }
            Context context = extractCustomEmoticonContext(handles, receiver);
            if (hasCustomRecentSymbols(context)) {
                return currentCategory;
            }
        }
        Context context = extractCustomEmoticonContext(handles, receiver);
        String storedCategory = loadStoredCustomLastCategory(context);
        if (storedCategory != null) {
            return storedCategory;
        }
        String rememberedCategory = LAST_NON_RECENTS_CUSTOM_EMOTICON_CATEGORY.get();
        if (rememberedCategory != null && !rememberedCategory.isBlank()
                && !CUSTOM_CATEGORY_RECENTS.equals(rememberedCategory)) {
            return rememberedCategory;
        }
        if (hasCustomRecentSymbols(context)) {
            return CUSTOM_CATEGORY_RECENTS;
        }
        return GboardAddSymbolsDataset.defaultCategoryKey();
    }

    private static void rememberCustomEmoticonCategory(
            Handles handles,
            Object receiver,
            String categoryKey) throws Throwable {
        if (categoryKey == null || categoryKey.isBlank()) {
            return;
        }
        CURRENT_CUSTOM_EMOTICON_CATEGORY_BY_KEYBOARD.put(receiver, categoryKey);
        rememberLastNonRecentsCustomEmoticonCategory(categoryKey);
        if (CUSTOM_CATEGORY_RECENTS.equals(categoryKey)) {
            return;
        }
        Context context = extractCustomEmoticonContext(handles, receiver);
        if (context == null) {
            return;
        }
        context.getSharedPreferences(CUSTOM_SYMBOL_RECENTS_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(CUSTOM_SYMBOL_LAST_CATEGORY_KEY, categoryKey)
                .apply();
    }

    private static String loadStoredCustomLastCategory(Context context) {
        if (context == null) {
            return null;
        }
        String stored = context.getSharedPreferences(CUSTOM_SYMBOL_RECENTS_PREF, Context.MODE_PRIVATE)
                .getString(CUSTOM_SYMBOL_LAST_CATEGORY_KEY, "");
        if (!isValidCustomEmoticonCategoryKey(stored)
                || CUSTOM_CATEGORY_RECENTS.equals(stored)) {
            return null;
        }
        return stored;
    }

    private static boolean hasCustomRecentSymbols(Context context) {
        return context != null && !loadCustomRecentSymbols(context).isEmpty();
    }

    private static void rememberLastNonRecentsCustomEmoticonCategory(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()
                || CUSTOM_CATEGORY_RECENTS.equals(categoryKey)) {
            return;
        }
        LAST_NON_RECENTS_CUSTOM_EMOTICON_CATEGORY.set(categoryKey);
    }

    private static boolean isValidCustomEmoticonCategoryKey(String categoryKey) {
        return CUSTOM_CATEGORY_RECENTS.equals(categoryKey)
                || GboardAddSymbolsDataset.isKnownCategoryKey(categoryKey);
    }

    private static int clampCustomEmoticonCategoryIndex(int categoryIndex) {
        if (categoryIndex < 0 || categoryIndex >= CUSTOM_EMOTICON_CATEGORY_KEYS.length) {
            return 1;
        }
        return categoryIndex;
    }

    private static int clampCustomHeaderBuilderCategoryIndex(int categoryIndex) {
        if (categoryIndex < 0) {
            return 0;
        }
        if (categoryIndex >= CUSTOM_EMOTICON_CATEGORY_KEYS.length) {
            return CUSTOM_EMOTICON_CATEGORY_KEYS.length - 1;
        }
        return categoryIndex;
    }

    private static int mapToCustomEmoticonCategoryIndex(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return 1;
        }
        if (CUSTOM_CATEGORY_RECENTS.equals(categoryName)) {
            return 0;
        }
        int datasetIndex = GboardAddSymbolsDataset.indexOf(categoryName);
        if (datasetIndex >= 0) {
            return datasetIndex + 1;
        }
        return 1;
    }

    private static Object buildCustomEmoticonHeaderModel(
            Handles handles,
            Object receiver,
            int selectedIndex) throws Throwable {
        Object modelBuilder = handles.headerModelBuilderFactoryMethod.invoke(null);
        Object startItem = buildCustomEmoticonHeaderItem(handles, receiver, selectedIndex);
        handles.headerModelBuilderStartItemField.set(modelBuilder, startItem);
        for (int index = 0; index < CUSTOM_EMOTICON_CATEGORY_KEYS.length; index++) {
            Object headerItem = buildCustomEmoticonHeaderItem(handles, receiver, index);
            handles.headerModelBuilderAddMiddleItemMethod.invoke(modelBuilder, headerItem);
        }
        Object selectedPosition = handles.headerSelectedPositionConstructor.newInstance(
                handles.headerSelectedPositionMiddleEnum, Integer.valueOf(selectedIndex));
        handles.headerModelBuilderSelectedPositionField.set(modelBuilder, selectedPosition);
        return handles.headerModelBuilderBuildMethod.invoke(modelBuilder);
    }

    private static Object buildCustomEmoticonHeaderItem(
            Handles handles,
            Object receiver,
            int categoryIndex) throws Throwable {
        Object itemBuilder = handles.headerItemBuilderFactoryMethod.invoke(null);
        String canonicalKey = CUSTOM_EMOTICON_CATEGORY_KEYS[categoryIndex];
        applyCustomEmoticonHeaderItemContent(handles, receiver, itemBuilder, categoryIndex);
        Object callbackInfo = handles.headerCallbackInfoConstructor.newInstance(
                Integer.valueOf(SWITCH_KEYBOARD_KEYCODE), canonicalKey);
        handles.headerItemBuilderCallbackField.set(itemBuilder, callbackInfo);
        return handles.headerItemBuilderBuildMethod.invoke(itemBuilder);
    }

    private static void syncCustomEmoticonHeaderSelection(
            Handles handles,
            Object receiver,
            int selectedIndex) throws Throwable {
        Object headerScrollController = handles.emoticonHeaderScrollControllerField.get(receiver);
        if (headerScrollController == null) {
            return;
        }
        Object startBehavior = handles.headerScrollBehaviorField.get(headerScrollController);
        if (startBehavior == null) {
            return;
        }
        handles.headerScrollSelectedIndexField.set(startBehavior, Integer.valueOf(selectedIndex));
    }

    private static List<String> resolveCustomEmoticonCategorySymbols(
            Handles handles,
            Object receiver,
            String categoryName) throws Throwable {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        int customCategoryIndex = mapToCustomEmoticonCategoryIndex(categoryName);
        if (customCategoryIndex == 0) {
            Context context = extractCustomEmoticonContext(handles, receiver);
            if (context != null) {
                return loadCustomRecentSymbols(context);
            }
            return Collections.emptyList();
        }
        List<String> symbols = GboardAddSymbolsDataset.symbolsFor(categoryName);
        return symbols.isEmpty()
                ? GboardAddSymbolsDataset.symbolsFor(GboardAddSymbolsDataset.defaultCategoryKey())
                : symbols;
    }

    private static Context extractCustomEmoticonContext(Handles handles, Object receiver)
            throws Throwable {
        if (handles == null || receiver == null) {
            return null;
        }
        Object context = handles.emoticonKeyboardContextField.get(receiver);
        return context instanceof Context ? (Context) context : null;
    }

    private static Locale resolveCustomEmoticonLocale(Handles handles, Object receiver) {
        try {
            Context context = extractCustomEmoticonContext(handles, receiver);
            if (context == null || context.getResources() == null) {
                return Locale.ENGLISH;
            }
            Configuration configuration = context.getResources().getConfiguration();
            if (configuration == null) {
                return Locale.ENGLISH;
            }
            if (!configuration.getLocales().isEmpty()) {
                Locale locale = configuration.getLocales().get(0);
                if (locale != null) {
                    return locale;
                }
            }
            Locale locale = configuration.locale;
            return locale != null ? locale : Locale.ENGLISH;
        } catch (Throwable ignored) {
            return Locale.ENGLISH;
        }
    }

    private static String resolveCustomEmoticonCategoryLabel(String categoryKey, Locale locale) {
        try {
            String[] labels = shouldUseChineseCategoryLabels(locale)
                    ? CUSTOM_EMOTICON_CATEGORY_LABELS_ZH
                    : CUSTOM_EMOTICON_CATEGORY_LABELS_EN;
            if (CUSTOM_CATEGORY_RECENTS.equals(categoryKey)) {
                return labels[0];
            }
            int datasetIndex = GboardAddSymbolsDataset.indexOf(categoryKey);
            if (datasetIndex < 0) {
                return categoryKey != null ? categoryKey : "";
            }
            return labels[datasetIndex + 1];
        } catch (Throwable ignored) {
            return resolveEnglishCustomEmoticonCategoryLabel(categoryKey);
        }
    }

    private static String resolveEnglishCustomEmoticonCategoryLabel(String categoryKey) {
        if (CUSTOM_CATEGORY_RECENTS.equals(categoryKey)) {
            return CUSTOM_EMOTICON_CATEGORY_LABELS_EN[0];
        }
        int datasetIndex = GboardAddSymbolsDataset.indexOf(categoryKey);
        if (datasetIndex < 0) {
            return categoryKey != null ? categoryKey : "";
        }
        return CUSTOM_EMOTICON_CATEGORY_LABELS_EN[datasetIndex + 1];
    }

    private static boolean shouldUseChineseCategoryLabels(Locale locale) {
        if (locale == null) {
            return false;
        }
        String languageTag = locale.toLanguageTag();
        if (languageTag == null || languageTag.isBlank()) {
            languageTag = locale.getLanguage();
        }
        if (languageTag == null || languageTag.isBlank()) {
            return false;
        }
        String normalized = languageTag.replace('_', '-').trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("zh") || normalized.startsWith("cmn");
    }

    private static void storeCustomRecentSymbol(Context context, String symbol) {
        if (context == null || symbol == null || symbol.isBlank()) {
            return;
        }
        List<String> recents = loadCustomRecentSymbols(context);
        recents.remove(symbol);
        recents.add(0, symbol);
        while (recents.size() > CUSTOM_SYMBOL_RECENTS_LIMIT) {
            recents.remove(recents.size() - 1);
        }
        context.getSharedPreferences(CUSTOM_SYMBOL_RECENTS_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(CUSTOM_SYMBOL_RECENTS_KEY,
                        String.join(CUSTOM_SYMBOL_RECENTS_DELIMITER, recents))
                .apply();
    }

    private static List<String> loadCustomRecentSymbols(Context context) {
        List<String> result = new ArrayList<>();
        if (context == null) {
            return result;
        }
        String stored = context.getSharedPreferences(CUSTOM_SYMBOL_RECENTS_PREF, Context.MODE_PRIVATE)
                .getString(CUSTOM_SYMBOL_RECENTS_KEY, "");
        if (stored == null || stored.isBlank()) {
            return result;
        }
        String[] items = stored.split(java.util.regex.Pattern.quote(CUSTOM_SYMBOL_RECENTS_DELIMITER));
        for (String item : items) {
            if (item != null && !item.isBlank() && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static Object resolveCustomEmoticonHistoryWriteKeyboard(Handles handles, Object consumer)
            throws Throwable {
        if (handles == null || consumer == null
                || !handles.emoticonItemClickConsumerClass.isInstance(consumer)) {
            return null;
        }
        Object callbackCase = handles.emoticonItemClickConsumerCaseField.get(consumer);
        if (!isCustomEmoticonHistoryConsumerCase(callbackCase)) {
            return null;
        }
        Object keyboard = handles.emoticonItemClickConsumerReceiverField.get(consumer);
        return isCustomEmoticonRenderMode(keyboard) ? keyboard : null;
    }

    static boolean isCustomEmoticonHistoryConsumerCase(Object callbackCase) {
        return callbackCase instanceof Integer && ((Integer) callbackCase).intValue() == 7;
    }

    private static void applyCustomEmoticonHeaderItemContent(
            Handles handles,
            Object receiver,
            Object itemBuilder,
            int categoryIndex) throws Throwable {
        if (categoryIndex == 0) {
            Object imageBuilder = handles.headerImageBuilderFactoryMethod.invoke(null);
            handles.headerImageBuilderSetIconResMethod.invoke(imageBuilder,
                    Integer.valueOf(CUSTOM_EMOTICON_RECENTS_HEADER_ICON_RES_ID));
            handles.headerImageBuilderSetContentDescriptionResMethod.invoke(imageBuilder,
                    Integer.valueOf(CUSTOM_EMOTICON_RECENTS_HEADER_CONTENT_DESCRIPTION_RES_ID));
            handles.headerImageBuilderSizeField.set(imageBuilder,
                    handles.headerImageSizeSmallEnum);
            Object imagePayload = handles.headerImageBuilderBuildMethod.invoke(imageBuilder);
            handles.headerItemBuilderImageField.set(itemBuilder, imagePayload);
            handles.headerItemBuilderContentTypeField.set(itemBuilder,
                    handles.headerItemContentTypeImageResourceEnum);
            return;
        }

        Object textBuilder = handles.headerTextBuilderFactoryMethod.invoke(null);
        String displayLabel;
        try {
            Locale locale = resolveCustomEmoticonLocale(handles, receiver);
            displayLabel = resolveCustomEmoticonCategoryLabel(
                    CUSTOM_EMOTICON_CATEGORY_KEYS[categoryIndex],
                    locale);
        } catch (Throwable ignored) {
            displayLabel = resolveEnglishCustomEmoticonCategoryLabel(
                    CUSTOM_EMOTICON_CATEGORY_KEYS[categoryIndex]);
        }
        handles.headerTextBuilderSetTextMethod.invoke(textBuilder, displayLabel);
        handles.headerTextBuilderSetContentDescriptionMethod.invoke(textBuilder, displayLabel);
        Object textPayload = handles.headerTextBuilderBuildMethod.invoke(textBuilder);
        handles.headerItemBuilderContentField.set(itemBuilder, textPayload);
        handles.headerItemBuilderContentTypeField.set(itemBuilder,
                handles.headerItemContentTypeMiddleEnum);
    }

    private static boolean containsExpressionCorpusKeyboardType(
            Handles handles,
            Object corpusList,
            Object expectedKeyboardType) throws Throwable {
        if (!(corpusList instanceof Iterable<?>)) {
            return false;
        }
        for (Object corpusItem : (Iterable<?>) corpusList) {
            if (corpusItem == null || !handles.expressionCorpusItemClass.isInstance(corpusItem)) {
                continue;
            }
            Object keyboardType = handles.expressionCorpusItemKeyboardTypeField.get(corpusItem);
            String keyboardTypeName = extractKeyboardTypeName(handles, keyboardType);
            if (isCustomSymbolKeyboardType(keyboardTypeName)
                    || java.util.Objects.equals(expectedKeyboardType, keyboardType)) {
                return true;
            }
        }
        return false;
    }

    private static Object removeExpressionCorpusKeyboardType(
            Handles handles,
            Object corpusList,
            String keyboardTypeNameToRemove) throws Throwable {
        if (!(corpusList instanceof Iterable<?>)) {
            return corpusList;
        }
        ArrayList<Object> updated = new ArrayList<>();
        boolean removed = false;
        for (Object corpusItem : (Iterable<?>) corpusList) {
            if (corpusItem != null && handles.expressionCorpusItemClass.isInstance(corpusItem)) {
                Object keyboardType = handles.expressionCorpusItemKeyboardTypeField.get(corpusItem);
                String keyboardTypeName = extractKeyboardTypeName(handles, keyboardType);
                if (keyboardTypeNameToRemove.equals(keyboardTypeName)) {
                    removed = true;
                    continue;
                }
            }
            updated.add(corpusItem);
        }
        if (!removed) {
            return corpusList;
        }
        return handles.immutableListCopyMethod.invoke(null, updated);
    }

    private static Object buildCustomExpressionCorpusItem(
            Handles handles,
            Object customKeyboardType,
            ClassLoader classLoader) throws Throwable {
        Class<?> emoticonExtensionClass = Class.forName(
                EMOTICON_EXTENSION_INTERFACE_CLASS, false, classLoader);
        Object template = handles.richSymbolCorpusProviderItemField.get(null);
        if (template == null || !handles.expressionCorpusItemClass.isInstance(template)) {
            throw new IllegalStateException("Missing target rich-symbol corpus template");
        }
        Object builder = handles.expressionCorpusItemBuilderCopyConstructor.newInstance(template);
        handles.expressionCorpusItemBuilderDefaultKeyboardTypeMethod.invoke(builder, customKeyboardType);
        Object eligibleKeyboardTypes = handles.immutableListCopyMethod.invoke(
                null, Collections.singletonList(customKeyboardType));
        handles.expressionCorpusItemBuilderEligibleKeyboardTypesField.set(builder, eligibleKeyboardTypes);
        handles.expressionCorpusItemBuilderExtensionClassField.set(builder, emoticonExtensionClass);
        return handles.expressionCorpusItemBuilderBuildMethod.invoke(builder);
    }

    private static Object appendImmutableList(Handles handles, Object source, Object appended)
            throws Throwable {
        ArrayList<Object> updated = new ArrayList<>();
        if (source instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) source) {
                updated.add(item);
            }
        }
        updated.add(appended);
        return handles.immutableListCopyMethod.invoke(null, updated);
    }

    private static Object lookupExtensionProviderClass(
            Handles handles,
            Object receiver,
            Object requestedType) throws Throwable {
        Object providerMap = handles.extensionManagerKeyboardProviderMapField.get(receiver);
        if (providerMap instanceof Map<?, ?>) {
            return ((Map<?, ?>) providerMap).get(requestedType);
        }
        Method getMethod = providerMap != null
                ? providerMap.getClass().getMethod("get", Object.class)
                : null;
        return getMethod == null ? null : getMethod.invoke(providerMap, requestedType);
    }

    private static void injectExtensionProviderMapping(
            Handles handles,
            Object receiver,
            Object requestedType,
            Class<?> providerClass) throws Throwable {
        Object providerMap = handles.extensionManagerKeyboardProviderMapField.get(receiver);
        Map<Object, Object> mutableMap = copyMap(providerMap);
        if (mutableMap.containsKey(requestedType)) {
            return;
        }
        mutableMap.put(requestedType, providerClass);
        handles.extensionManagerKeyboardProviderMapField.set(receiver, buildImmutableMap(handles, mutableMap));
    }

    private static Map<Object, Object> copyMap(Object source) throws Throwable {
        if (source instanceof Map<?, ?>) {
            Map<Object, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) source).entrySet()) {
                copy.put(entry.getKey(), entry.getValue());
            }
            return copy;
        }
        Map<Object, Object> copy = new HashMap<>();
        if (source == null) {
            return copy;
        }
        Method entrySetMethod = source.getClass().getMethod("entrySet");
        Object entriesObject = entrySetMethod.invoke(source);
        if (entriesObject instanceof Iterable<?>) {
            for (Object entryObject : (Iterable<?>) entriesObject) {
                if (!(entryObject instanceof Map.Entry<?, ?>)) {
                    continue;
                }
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private static Object buildImmutableMap(Handles handles, Map<Object, Object> source)
            throws Throwable {
        Object builder = handles.immutableMapBuilderConstructor.newInstance(
                Integer.valueOf(source.size()));
        handles.immutableMapBuilderPutMethod.invoke(builder, source);
        return handles.immutableMapBuilderBuildMethod.invoke(builder);
    }

    private static Handles handles(ClassLoader classLoader) throws Throwable {
        synchronized (REFLECTION_BY_LOADER) {
            Handles cached = REFLECTION_BY_LOADER.get(classLoader);
            if (cached != null) {
                return cached;
            }
            Handles created = new Handles(classLoader);
            REFLECTION_BY_LOADER.put(classLoader, created);
            return created;
        }
    }

    private static boolean isCustomSymbolKeyboardType(String keyboardTypeName) {
        return JASONDEV_SYMBOL_KEYBOARD_DATA.equals(keyboardTypeName);
    }

    private static boolean isStockEmoticonKeyboardType(String keyboardTypeName) {
        return STOCK_EMOTICON_KEYBOARD_DATA.equals(keyboardTypeName);
    }

    private static boolean shouldManageCustomSymbolProviderRequest(String keyboardTypeName) {
        return isCustomSymbolKeyboardType(keyboardTypeName);
    }

    private static boolean shouldAliasStockEmoticonBackToCustomForSession(
            String keyboardTypeName) {
        return isStockEmoticonKeyboardType(keyboardTypeName)
                && !hasPendingStockEmoticonTabSwitch()
                && (hasPendingCustomFlow() || ACTIVE_CUSTOM_EMOTICON_SESSION.get());
    }

    private static void markPendingCustomFlow() {
        clearPendingStockEmoticonTabSwitch();
        pendingCustomFlowUntilUptimeMs = SystemClock.uptimeMillis() + PENDING_FLOW_WINDOW_MS;
    }

    private static boolean hasPendingCustomFlow() {
        if (SystemClock.uptimeMillis() > pendingCustomFlowUntilUptimeMs) {
            pendingCustomFlowUntilUptimeMs = 0L;
            return false;
        }
        return pendingCustomFlowUntilUptimeMs != 0L;
    }

    private static void clearPendingCustomFlow() {
        pendingCustomFlowUntilUptimeMs = 0L;
    }

    private static void rememberCurrentCustomEmoticonSessionKeyboard(Object keyboard) {
        if (keyboard != null) {
            CURRENT_CUSTOM_EMOTICON_SESSION_KEYBOARD.set(keyboard);
        }
    }

    private static void clearCurrentCustomEmoticonSessionKeyboard() {
        CURRENT_CUSTOM_EMOTICON_SESSION_KEYBOARD.set(null);
        ACTIVE_CUSTOM_EMOTICON_SESSION.set(false);
    }

    private static boolean hasTrackedCustomEmoticonSessionKeyboard() {
        restorePendingStockEmoticonTabSwitchStateIfExpired();
        Object keyboard = CURRENT_CUSTOM_EMOTICON_SESSION_KEYBOARD.get();
        return keyboard != null
                && ACTIVE_CUSTOM_EMOTICON_KEYBOARDS.containsKey(keyboard)
                && isCustomEmoticonRenderMode(keyboard);
    }

    private static void markPendingStockEmoticonTabSwitch() {
        Object sessionKeyboard = CURRENT_CUSTOM_EMOTICON_SESSION_KEYBOARD.get();
        PENDING_STOCK_EMOTICON_TAB_SWITCH_KEYBOARD.set(sessionKeyboard);
        PENDING_STOCK_EMOTICON_TAB_SWITCH_ACTIVE_SESSION.set(
                ACTIVE_CUSTOM_EMOTICON_SESSION.get());
        if (sessionKeyboard != null) {
            Boolean previousRenderMode = CURRENT_CUSTOM_EMOTICON_RENDER_MODE.get(sessionKeyboard);
            if (previousRenderMode == null) {
                previousRenderMode =
                        Boolean.valueOf(ACTIVE_CUSTOM_EMOTICON_KEYBOARDS.containsKey(sessionKeyboard));
            }
            PENDING_STOCK_EMOTICON_TAB_SWITCH_RENDER_MODE.set(previousRenderMode);
            CURRENT_CUSTOM_EMOTICON_RENDER_MODE.put(sessionKeyboard, Boolean.FALSE);
        } else {
            PENDING_STOCK_EMOTICON_TAB_SWITCH_RENDER_MODE.set(null);
        }
        ACTIVE_CUSTOM_EMOTICON_SESSION.set(false);
        pendingStockEmoticonTabSwitchUntilUptimeMs =
                SystemClock.uptimeMillis() + PENDING_STOCK_EMOTICON_TAB_SWITCH_WINDOW_MS;
    }

    private static boolean hasPendingStockEmoticonTabSwitch() {
        restorePendingStockEmoticonTabSwitchStateIfExpired();
        return pendingStockEmoticonTabSwitchUntilUptimeMs != 0L;
    }

    private static void clearPendingStockEmoticonTabSwitch() {
        pendingStockEmoticonTabSwitchUntilUptimeMs = 0L;
        PENDING_STOCK_EMOTICON_TAB_SWITCH_KEYBOARD.set(null);
        PENDING_STOCK_EMOTICON_TAB_SWITCH_RENDER_MODE.set(null);
        PENDING_STOCK_EMOTICON_TAB_SWITCH_ACTIVE_SESSION.set(false);
    }

    private static void restorePendingStockEmoticonTabSwitchState() {
        pendingStockEmoticonTabSwitchUntilUptimeMs = 0L;
        Object sessionKeyboard = PENDING_STOCK_EMOTICON_TAB_SWITCH_KEYBOARD.getAndSet(null);
        Boolean renderMode = PENDING_STOCK_EMOTICON_TAB_SWITCH_RENDER_MODE.getAndSet(null);
        boolean activeSession = PENDING_STOCK_EMOTICON_TAB_SWITCH_ACTIVE_SESSION.getAndSet(false);
        if (sessionKeyboard != null) {
            if (renderMode != null) {
                CURRENT_CUSTOM_EMOTICON_RENDER_MODE.put(sessionKeyboard, renderMode);
            } else {
                CURRENT_CUSTOM_EMOTICON_RENDER_MODE.remove(sessionKeyboard);
            }
        }
        ACTIVE_CUSTOM_EMOTICON_SESSION.set(activeSession);
    }

    private static void restorePendingStockEmoticonTabSwitchStateIfExpired() {
        long pendingUntilUptimeMs = pendingStockEmoticonTabSwitchUntilUptimeMs;
        if (pendingUntilUptimeMs == 0L) {
            return;
        }
        if (pendingUntilUptimeMs < SystemClock.uptimeMillis()) {
            restorePendingStockEmoticonTabSwitchState();
        }
    }

    private static void updateCurrentCustomEmoticonRenderMode(
            Handles handles,
            Object keyboard,
            String keyboardTypeName) {
        if (!isEmoticonKeyboardInstance(handles, keyboard)) {
            return;
        }
        CURRENT_CUSTOM_EMOTICON_RENDER_MODE.put(
                keyboard, Boolean.valueOf(isCustomSymbolKeyboardType(keyboardTypeName)));
    }

    private static boolean isCustomEmoticonRenderMode(Object keyboard) {
        if (keyboard == null) {
            return false;
        }
        restorePendingStockEmoticonTabSwitchStateIfExpired();
        Boolean explicit = CURRENT_CUSTOM_EMOTICON_RENDER_MODE.get(keyboard);
        if (explicit != null) {
            return explicit.booleanValue();
        }
        return ACTIVE_CUSTOM_EMOTICON_KEYBOARDS.containsKey(keyboard);
    }

    private static boolean isActiveCustomEmoticonKeyboard(Object keyboard) {
        return keyboard != null
                && ACTIVE_CUSTOM_EMOTICON_KEYBOARDS.containsKey(keyboard)
                && isCustomEmoticonRenderMode(keyboard);
    }

    private static boolean isEmoticonKeyboardInstance(Handles handles, Object keyboard) {
        return handles != null && keyboard != null
                && handles.emoticonKeyboardClass.isInstance(keyboard);
    }

    private static String extractKeyboardTypeName(Handles handles, Object keyboardType) {
        if (keyboardType == null || handles == null) {
            return null;
        }
        try {
            Object name = handles.keyboardTypeNameField.get(keyboardType);
            return name instanceof String ? (String) name : null;
        } catch (Throwable throwable) {
            logWarn("extractKeyboardTypeName failed", throwable);
            return null;
        }
    }

    private static int dpToPx(Context context, int dp) {
        if (context == null) {
            return dp;
        }
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static String[] buildCustomCategoryKeys() {
        String[] keys = new String[GboardAddSymbolsDataset.CATEGORY_KEYS.length + 1];
        keys[0] = CUSTOM_CATEGORY_RECENTS;
        System.arraycopy(GboardAddSymbolsDataset.CATEGORY_KEYS, 0,
                keys, 1, GboardAddSymbolsDataset.CATEGORY_KEYS.length);
        return keys;
    }

    private static String describeExpressionCorpusList(Handles handles, Object corpusList) {
        if (!(corpusList instanceof Iterable<?>)) {
            return "<non-iterable>";
        }
        List<String> values = new ArrayList<>();
        try {
            for (Object corpusItem : (Iterable<?>) corpusList) {
                if (corpusItem == null || !handles.expressionCorpusItemClass.isInstance(corpusItem)) {
                    values.add(String.valueOf(corpusItem));
                } else {
                    Object keyboardType = handles.expressionCorpusItemKeyboardTypeField.get(corpusItem);
                    values.add(extractKeyboardTypeName(handles, keyboardType));
                }
                if (values.size() >= 12) {
                    break;
                }
            }
        } catch (Throwable throwable) {
            return "<error:" + throwable.getClass().getSimpleName() + ">";
        }
        return values.toString();
    }

    private static final class CustomEmoticonGlyphView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Rect bounds = new Rect();
        private final RectF glyphBounds = new RectF();
        private final android.content.res.ColorStateList textColors;
        private String symbol;
        private boolean glyphLayoutDirty = true;
        private float cachedDrawX;
        private float cachedDrawY;
        private float cachedTextSizePx;
        private int cachedLayoutWidth = -1;
        private int cachedLayoutHeight = -1;

        CustomEmoticonGlyphView(Context context) {
            super(context);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextScaleX(CUSTOM_EMOTICON_ITEM_GLYPH_TEXT_SCALE_X);
            textColors = resolveCustomEmoticonTextColors(context);
            applyResolvedGlyphColor();
        }

        void bindSymbol(String value) {
            if (!java.util.Objects.equals(symbol, value)) {
                symbol = value;
                markGlyphLayoutDirty();
            }
            applyResolvedGlyphColor();
            paint.setTextScaleX(CUSTOM_EMOTICON_ITEM_GLYPH_TEXT_SCALE_X);
            setContentDescription(value);
            invalidate();
        }

        boolean hasSymbol() {
            return symbol != null && !symbol.isBlank();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!hasSymbol()) {
                return;
            }
            applyResolvedGlyphColor();
            if (!ensureGlyphLayout()) {
                return;
            }
            paint.setTextScaleX(CUSTOM_EMOTICON_ITEM_GLYPH_TEXT_SCALE_X);
            paint.setTextSize(cachedTextSizePx);
            canvas.drawText(symbol, cachedDrawX, cachedDrawY, paint);
        }

        private RectF buildCenteredSquareGlyphBounds(int viewWidth, int viewHeight) {
            float availableWidth = viewWidth
                    * (1f - (CUSTOM_EMOTICON_ITEM_GLYPH_HORIZONTAL_MARGIN_RATIO * 2f));
            float availableHeight = viewHeight
                    * (1f - (CUSTOM_EMOTICON_ITEM_GLYPH_VERTICAL_MARGIN_RATIO * 2f));
            availableHeight = Math.min(availableHeight,
                    viewHeight * CUSTOM_EMOTICON_ITEM_ICON_SIZE_HEIGHT_RATIO);
            float side = Math.max(1f, Math.min(availableWidth, availableHeight));
            float left = (viewWidth - side) / 2f;
            float top = (viewHeight - side) / 2f;
            return new RectF(left, top, left + side, top + side);
        }

        private void markGlyphLayoutDirty() {
            glyphLayoutDirty = true;
        }

        private boolean ensureGlyphLayout() {
            if (!hasSymbol()) {
                return false;
            }
            int viewWidth = getWidth();
            int viewHeight = getHeight();
            if (viewWidth <= 0 || viewHeight <= 0) {
                return false;
            }
            if (!glyphLayoutDirty
                    && cachedLayoutWidth == viewWidth
                    && cachedLayoutHeight == viewHeight) {
                return true;
            }
            glyphBounds.set(buildCenteredSquareGlyphBounds(viewWidth, viewHeight));
            float availableWidth = glyphBounds.width();
            float availableHeight = glyphBounds.height();

            paint.setTextScaleX(CUSTOM_EMOTICON_ITEM_GLYPH_TEXT_SCALE_X);
            paint.setTextSize(Math.max(1f, availableHeight));
            paint.getTextBounds(symbol, 0, symbol.length(), bounds);
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            float textWidth = Math.max(1f, Math.max(bounds.width(), paint.measureText(symbol)));
            float textHeight = Math.max(1f, Math.max(bounds.height(),
                    fontMetrics.descent - fontMetrics.ascent));
            float scale = Math.min(availableWidth / textWidth, availableHeight / textHeight);
            cachedTextSizePx = Math.max(1f, paint.getTextSize() * Math.min(scale, 1f));
            paint.setTextSize(cachedTextSizePx);

            paint.getTextBounds(symbol, 0, symbol.length(), bounds);
            cachedDrawX = (glyphBounds.centerX() - (bounds.width() / 2f)) - bounds.left;
            cachedDrawY = (glyphBounds.centerY() - (bounds.height() / 2f)) - bounds.top;
            cachedLayoutWidth = viewWidth;
            cachedLayoutHeight = viewHeight;
            glyphLayoutDirty = false;
            return true;
        }

        private void applyResolvedGlyphColor() {
            paint.setColor(resolveCurrentGlyphColor());
        }

        private int resolveCurrentGlyphColor() {
            if (textColors == null) {
                return Color.WHITE;
            }
            return textColors.getColorForState(getDrawableState(), textColors.getDefaultColor());
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            applyResolvedGlyphColor();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            restoreAncestorClipping(this);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w != oldw || h != oldh) {
                markGlyphLayoutDirty();
            }
        }
    }

    private static void logInfo(String message) {
        Log.i(TAG, message);
    }

    private static void logWarn(String message, Throwable throwable) {
        AtomicInteger count = WARNING_LOG_COUNTS.computeIfAbsent(
                message, ignored -> new AtomicInteger(0));
        if (count.getAndIncrement() < 3) {
            Log.w(TAG, message, throwable);
        }
    }

    private static Field findFieldInHierarchy(Class<?> owner, String name)
            throws NoSuchFieldException {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }

    private static final class Handles {
        final Class<?> keyboardProviderReceiverInterfaceClass;
        final Class<?> emoticonKeyboardClass;
        final Class<?> emoticonRecyclerViewClass;
        final Class<?> emoticonRecyclerAdapterClass;
        final Class<?> supportRecyclerViewClass;
        final Class<?> recyclerViewViewHolderClass;
        final Class<?> gridLayoutManagerClass;
        final Constructor<?> immutableMapBuilderConstructor;
        final Method immutableMapBuilderPutMethod;
        final Method immutableMapBuilderBuildMethod;
        final Method keyboardTypeFromStringMethod;
        final Field keyboardTypeNameField;
        final Field extensionManagerKeyboardProviderMapField;
        final Class<?> expressionCorpusItemClass;
        final Field richSymbolCorpusProviderItemField;
        final Field expressionCorpusItemExtensionClassField;
        final Field expressionCorpusItemKeyboardTypeField;
        final Constructor<?> expressionCorpusItemBuilderCopyConstructor;
        final Method expressionCorpusItemBuilderDefaultKeyboardTypeMethod;
        final Method expressionCorpusItemBuilderBuildMethod;
        final Field expressionCorpusItemBuilderEligibleKeyboardTypesField;
        final Field expressionCorpusItemBuilderExtensionClassField;
        final Method immutableListCopyMethod;
        final Constructor<?> integerStringMapConstructor;
        final Field emoticonDescriptionProviderField;
        final Field emoticonBodyRecyclerField;
        final Field emoticonEmptyStateField;
        final Field emoticonKeyboardContextField;
        final Field emoticonHistoryField;
        final Class<?> consumerClass;
        final Method consumerAcceptMethod;
        final Method emoticonRecyclerAttachAdapterMethod;
        final Method emoticonRecyclerSetItemsMethod;
        final Method emoticonSetEmoticonsMethod;
        final Constructor<?> emoticonItemClickConsumerConstructor;
        final Class<?> emoticonItemClickConsumerClass;
        final Field emoticonItemClickConsumerReceiverField;
        final Field emoticonItemClickConsumerCaseField;
        final Field emoticonRecyclerAdapterItemHeightField;
        final Field emoticonRecyclerAdapterItemSpacingField;
        final Field emoticonRecyclerAdapterClickConsumerField;
        final Field emoticonRecyclerAdapterItemsField;
        final Field recyclerViewAdapterField;
        final Constructor<?> recyclerViewViewHolderConstructor;
        final Field emoticonRecyclerSpanCountField;
        final Field recyclerViewLayoutManagerField;
        final Field recyclerViewViewHolderItemViewField;
        final Field emoticonCategoryNameMapField;
        final Field emoticonHeaderControllerField;
        final Field emoticonHeaderScrollControllerField;
        final Method headerControllerSetModelMethod;
        final Method headerModelBuilderFactoryMethod;
        final Method headerModelBuilderAddMiddleItemMethod;
        final Method headerModelBuilderBuildMethod;
        final Field headerModelBuilderSelectedPositionField;
        final Field headerModelBuilderStartItemField;
        final Method headerItemBuilderFactoryMethod;
        final Field headerItemBuilderContentTypeField;
        final Method headerItemBuilderBuildMethod;
        final Field headerItemBuilderImageField;
        final Field headerItemBuilderContentField;
        final Field headerItemBuilderCallbackField;
        final Method headerImageBuilderFactoryMethod;
        final Method headerImageBuilderSetIconResMethod;
        final Method headerImageBuilderSetContentDescriptionResMethod;
        final Field headerImageBuilderSizeField;
        final Method headerImageBuilderBuildMethod;
        final Method headerTextBuilderFactoryMethod;
        final Method headerTextBuilderSetTextMethod;
        final Method headerTextBuilderSetContentDescriptionMethod;
        final Method headerTextBuilderBuildMethod;
        final Constructor<?> headerCallbackInfoConstructor;
        final Constructor<?> headerSelectedPositionConstructor;
        final Object headerSelectedPositionMiddleEnum;
        final Object headerItemContentTypeMiddleEnum;
        final Object headerItemContentTypeImageResourceEnum;
        final Object headerImageSizeSmallEnum;
        final Field headerScrollBehaviorField;
        final Field headerScrollSelectedIndexField;
        final Method gridLayoutManagerSetSpanCountMethod;

        Handles(ClassLoader classLoader) throws Throwable {
            Class<?> keyboardTypeClass = Class.forName("qch", false, classLoader);
            keyboardProviderReceiverInterfaceClass = Class.forName("pla", false, classLoader);
            supportRecyclerViewClass = Class.forName(
                    "android.support.v7.widget.RecyclerView", false, classLoader);
            recyclerViewViewHolderClass = Class.forName("kr", false, classLoader);
            gridLayoutManagerClass = Class.forName(
                    "android.support.v7.widget.GridLayoutManager", false, classLoader);
            Class<?> immutableMapBuilderClass = Class.forName("wnv", false, classLoader);
            Class<?> immutableListClass = Class.forName("wns", false, classLoader);
            Class<?> integerStringMapClass = Class.forName("wsw", false, classLoader);
            emoticonKeyboardClass = Class.forName(
                    "com.google.android.apps.inputmethod.libs.search.emoticon.EmoticonKeyboardM2",
                    false, classLoader);
            emoticonRecyclerViewClass = Class.forName(
                    "com.google.android.apps.inputmethod.libs.search.emoticon.EmoticonRecyclerView",
                    false, classLoader);
            emoticonRecyclerAdapterClass = Class.forName("jiu", false, classLoader);
            Class<?> emoticonDescriptionProviderClass = Class.forName("jjn", false, classLoader);
            emoticonItemClickConsumerClass = Class.forName("jhc", false, classLoader);
            consumerClass = Class.forName("java.util.function.Consumer", false, classLoader);
            Class<?> emoticonHeaderControllerClass = Class.forName("gpk", false, classLoader);
            Class<?> emoticonHeaderScrollControllerClass = Class.forName("gpz", false, classLoader);
            Class<?> emoticonHeaderModelClass = Class.forName("gpm", false, classLoader);
            Class<?> emoticonHeaderModelBuilderClass = Class.forName("gpl", false, classLoader);
            Class<?> emoticonHeaderItemClass = Class.forName("gph", false, classLoader);
            Class<?> emoticonHeaderItemBuilderClass = Class.forName("aaaa", false, classLoader);
            Class<?> emoticonHeaderTextClass = Class.forName("gpf", false, classLoader);
            Class<?> emoticonHeaderTextBuilderClass = Class.forName("zmt", false, classLoader);
            Class<?> emoticonHeaderImageClass = Class.forName("gpd", false, classLoader);
            Class<?> emoticonHeaderImageBuilderClass = Class.forName("ntv", false, classLoader);
            Class<?> emoticonHeaderSelectionClass = Class.forName("gpo", false, classLoader);
            Class<?> emoticonHeaderSelectionTypeClass = Class.forName("gpn", false, classLoader);
            Class<?> emoticonHeaderContentTypeClass = Class.forName("gpb", false, classLoader);
            Class<?> emoticonHeaderCallbackInfoClass = Class.forName("gpa", false, classLoader);
            Class<?> emoticonHeaderImageSizeClass = Class.forName("gpe", false, classLoader);
            Class<?> startElementScrollBehaviorClass = Class.forName(
                    "com.google.android.apps.inputmethod.libs.expression.header.StartElementScrollBehavior",
                    false, classLoader);
            Class<?> extensionManagerClass = Class.forName("ohx", false, classLoader);
            expressionCorpusItemClass = Class.forName("ohn", false, classLoader);
            Class<?> expressionCorpusItemBuilderClass = Class.forName("ohl", false, classLoader);
            Class<?> richSymbolCorpusProviderClass = Class.forName("jpv", false, classLoader);

            immutableMapBuilderConstructor = immutableMapBuilderClass.getDeclaredConstructor(int.class);
            immutableMapBuilderConstructor.setAccessible(true);
            immutableMapBuilderPutMethod = immutableMapBuilderClass.getDeclaredMethod("l", Map.class);
            immutableMapBuilderPutMethod.setAccessible(true);
            immutableMapBuilderBuildMethod = immutableMapBuilderClass.getDeclaredMethod("m");
            immutableMapBuilderBuildMethod.setAccessible(true);

            keyboardTypeFromStringMethod = Class.forName("qaz", false, classLoader).getDeclaredMethod("ag", Object.class);
            keyboardTypeFromStringMethod.setAccessible(true);
            keyboardTypeNameField = keyboardTypeClass.getDeclaredField("m");
            keyboardTypeNameField.setAccessible(true);

            extensionManagerKeyboardProviderMapField = extensionManagerClass.getDeclaredField("c");
            extensionManagerKeyboardProviderMapField.setAccessible(true);

            expressionCorpusItemKeyboardTypeField = expressionCorpusItemClass.getDeclaredField("c");
            expressionCorpusItemKeyboardTypeField.setAccessible(true);
            expressionCorpusItemExtensionClassField = expressionCorpusItemClass.getDeclaredField("g");
            expressionCorpusItemExtensionClassField.setAccessible(true);
            richSymbolCorpusProviderItemField =
                    richSymbolCorpusProviderClass.getDeclaredField("a");
            richSymbolCorpusProviderItemField.setAccessible(true);

            expressionCorpusItemBuilderCopyConstructor =
                    expressionCorpusItemBuilderClass.getDeclaredConstructor(expressionCorpusItemClass);
            expressionCorpusItemBuilderCopyConstructor.setAccessible(true);
            expressionCorpusItemBuilderDefaultKeyboardTypeMethod =
                    expressionCorpusItemBuilderClass.getDeclaredMethod("b", keyboardTypeClass);
            expressionCorpusItemBuilderDefaultKeyboardTypeMethod.setAccessible(true);
            expressionCorpusItemBuilderBuildMethod =
                    expressionCorpusItemBuilderClass.getDeclaredMethod("a");
            expressionCorpusItemBuilderBuildMethod.setAccessible(true);
            expressionCorpusItemBuilderEligibleKeyboardTypesField =
                    expressionCorpusItemBuilderClass.getDeclaredField("a");
            expressionCorpusItemBuilderEligibleKeyboardTypesField.setAccessible(true);
            expressionCorpusItemBuilderExtensionClassField =
                    expressionCorpusItemBuilderClass.getDeclaredField("c");
            expressionCorpusItemBuilderExtensionClassField.setAccessible(true);

            immutableListCopyMethod = immutableListClass.getDeclaredMethod("o", Collection.class);
            immutableListCopyMethod.setAccessible(true);

            integerStringMapConstructor = integerStringMapClass.getDeclaredConstructor(
                    Object[].class, int.class);
            integerStringMapConstructor.setAccessible(true);

            emoticonDescriptionProviderField = emoticonKeyboardClass.getDeclaredField("c");
            emoticonDescriptionProviderField.setAccessible(true);
            emoticonBodyRecyclerField = emoticonKeyboardClass.getDeclaredField("e");
            emoticonBodyRecyclerField.setAccessible(true);
            emoticonEmptyStateField = emoticonKeyboardClass.getDeclaredField("q");
            emoticonEmptyStateField.setAccessible(true);
            emoticonKeyboardContextField = findFieldInHierarchy(emoticonKeyboardClass, "z");
            emoticonKeyboardContextField.setAccessible(true);
            emoticonHistoryField = emoticonKeyboardClass.getDeclaredField("b");
            emoticonHistoryField.setAccessible(true);

            consumerAcceptMethod = consumerClass.getDeclaredMethod("accept", Object.class);
            consumerAcceptMethod.setAccessible(true);
            emoticonRecyclerAttachAdapterMethod = emoticonRecyclerViewClass.getDeclaredMethod(
                    "aL", emoticonDescriptionProviderClass, consumerClass);
            emoticonRecyclerAttachAdapterMethod.setAccessible(true);
            emoticonRecyclerSetItemsMethod = emoticonRecyclerViewClass.getDeclaredMethod(
                    "a", List.class);
            emoticonRecyclerSetItemsMethod.setAccessible(true);
            emoticonSetEmoticonsMethod = emoticonKeyboardClass.getDeclaredMethod(
                    "D", emoticonRecyclerViewClass, String.class);
            emoticonSetEmoticonsMethod.setAccessible(true);

            emoticonItemClickConsumerConstructor =
                    emoticonItemClickConsumerClass.getDeclaredConstructor(Object.class, int.class);
            emoticonItemClickConsumerConstructor.setAccessible(true);
            emoticonItemClickConsumerReceiverField =
                    emoticonItemClickConsumerClass.getDeclaredField("a");
            emoticonItemClickConsumerReceiverField.setAccessible(true);
            emoticonItemClickConsumerCaseField =
                    emoticonItemClickConsumerClass.getDeclaredField("$cid");
            emoticonItemClickConsumerCaseField.setAccessible(true);

            emoticonRecyclerAdapterItemHeightField =
                    emoticonRecyclerAdapterClass.getDeclaredField("f");
            emoticonRecyclerAdapterItemHeightField.setAccessible(true);
            emoticonRecyclerAdapterItemSpacingField =
                    emoticonRecyclerAdapterClass.getDeclaredField("i");
            emoticonRecyclerAdapterItemSpacingField.setAccessible(true);
            emoticonRecyclerAdapterClickConsumerField =
                    emoticonRecyclerAdapterClass.getDeclaredField("e");
            emoticonRecyclerAdapterClickConsumerField.setAccessible(true);
            emoticonRecyclerAdapterItemsField =
                    emoticonRecyclerAdapterClass.getDeclaredField("d");
            emoticonRecyclerAdapterItemsField.setAccessible(true);

            recyclerViewAdapterField = supportRecyclerViewClass.getDeclaredField("k");
            recyclerViewAdapterField.setAccessible(true);
            recyclerViewViewHolderConstructor =
                    recyclerViewViewHolderClass.getDeclaredConstructor(View.class);
            recyclerViewViewHolderConstructor.setAccessible(true);
            emoticonRecyclerSpanCountField = emoticonRecyclerViewClass.getDeclaredField("ac");
            emoticonRecyclerSpanCountField.setAccessible(true);
            recyclerViewLayoutManagerField = supportRecyclerViewClass.getDeclaredField("l");
            recyclerViewLayoutManagerField.setAccessible(true);
            recyclerViewViewHolderItemViewField =
                    recyclerViewViewHolderClass.getDeclaredField("a");
            recyclerViewViewHolderItemViewField.setAccessible(true);

            emoticonCategoryNameMapField = emoticonKeyboardClass.getDeclaredField("m");
            emoticonCategoryNameMapField.setAccessible(true);
            emoticonHeaderControllerField = emoticonKeyboardClass.getDeclaredField("f");
            emoticonHeaderControllerField.setAccessible(true);
            emoticonHeaderScrollControllerField = emoticonKeyboardClass.getDeclaredField("g");
            emoticonHeaderScrollControllerField.setAccessible(true);

            headerControllerSetModelMethod = emoticonHeaderControllerClass.getDeclaredMethod(
                    "j", emoticonHeaderModelClass);
            headerControllerSetModelMethod.setAccessible(true);
            headerModelBuilderFactoryMethod = emoticonHeaderModelClass.getDeclaredMethod("a");
            headerModelBuilderFactoryMethod.setAccessible(true);
            headerModelBuilderAddMiddleItemMethod =
                    emoticonHeaderModelBuilderClass.getDeclaredMethod("b", emoticonHeaderItemClass);
            headerModelBuilderAddMiddleItemMethod.setAccessible(true);
            headerModelBuilderBuildMethod =
                    emoticonHeaderModelBuilderClass.getDeclaredMethod("a");
            headerModelBuilderBuildMethod.setAccessible(true);
            headerModelBuilderSelectedPositionField =
                    emoticonHeaderModelBuilderClass.getDeclaredField("b");
            headerModelBuilderSelectedPositionField.setAccessible(true);
            headerModelBuilderStartItemField =
                    emoticonHeaderModelBuilderClass.getDeclaredField("a");
            headerModelBuilderStartItemField.setAccessible(true);

            headerItemBuilderFactoryMethod = emoticonHeaderItemClass.getDeclaredMethod("a");
            headerItemBuilderFactoryMethod.setAccessible(true);
            headerItemBuilderContentTypeField = emoticonHeaderItemBuilderClass.getDeclaredField("a");
            headerItemBuilderContentTypeField.setAccessible(true);
            headerItemBuilderBuildMethod =
                    emoticonHeaderItemBuilderClass.getDeclaredMethod("o");
            headerItemBuilderBuildMethod.setAccessible(true);
            headerItemBuilderImageField = emoticonHeaderItemBuilderClass.getDeclaredField("b");
            headerItemBuilderImageField.setAccessible(true);
            headerItemBuilderContentField = emoticonHeaderItemBuilderClass.getDeclaredField("d");
            headerItemBuilderContentField.setAccessible(true);
            headerItemBuilderCallbackField = emoticonHeaderItemBuilderClass.getDeclaredField("c");
            headerItemBuilderCallbackField.setAccessible(true);

            headerImageBuilderFactoryMethod = emoticonHeaderImageClass.getDeclaredMethod("a");
            headerImageBuilderFactoryMethod.setAccessible(true);
            headerImageBuilderSetIconResMethod =
                    emoticonHeaderImageBuilderClass.getDeclaredMethod("o", int.class);
            headerImageBuilderSetIconResMethod.setAccessible(true);
            headerImageBuilderSetContentDescriptionResMethod =
                    emoticonHeaderImageBuilderClass.getDeclaredMethod("n", int.class);
            headerImageBuilderSetContentDescriptionResMethod.setAccessible(true);
            headerImageBuilderSizeField = emoticonHeaderImageBuilderClass.getDeclaredField("b");
            headerImageBuilderSizeField.setAccessible(true);
            headerImageBuilderBuildMethod =
                    emoticonHeaderImageBuilderClass.getDeclaredMethod("l");
            headerImageBuilderBuildMethod.setAccessible(true);

            headerTextBuilderFactoryMethod = emoticonHeaderTextClass.getDeclaredMethod("a");
            headerTextBuilderFactoryMethod.setAccessible(true);
            headerTextBuilderSetTextMethod =
                    emoticonHeaderTextBuilderClass.getDeclaredMethod("i", String.class);
            headerTextBuilderSetTextMethod.setAccessible(true);
            headerTextBuilderSetContentDescriptionMethod =
                    emoticonHeaderTextBuilderClass.getDeclaredMethod("f", String.class);
            headerTextBuilderSetContentDescriptionMethod.setAccessible(true);
            headerTextBuilderBuildMethod =
                    emoticonHeaderTextBuilderClass.getDeclaredMethod("e");
            headerTextBuilderBuildMethod.setAccessible(true);

            headerCallbackInfoConstructor =
                    emoticonHeaderCallbackInfoClass.getDeclaredConstructor(int.class, String.class);
            headerCallbackInfoConstructor.setAccessible(true);
            headerSelectedPositionConstructor =
                    emoticonHeaderSelectionClass.getDeclaredConstructor(
                            emoticonHeaderSelectionTypeClass, int.class);
            headerSelectedPositionConstructor.setAccessible(true);

            Class<? extends Enum> headerSelectionEnumClass =
                    (Class<? extends Enum>) emoticonHeaderSelectionTypeClass.asSubclass(Enum.class);
            headerSelectedPositionMiddleEnum =
                    Enum.valueOf(headerSelectionEnumClass, "MIDDLE");

            Class<? extends Enum> headerContentTypeEnumClass =
                    (Class<? extends Enum>) emoticonHeaderContentTypeClass.asSubclass(Enum.class);
            headerItemContentTypeMiddleEnum =
                    Enum.valueOf(headerContentTypeEnumClass, "TEXT");
            headerItemContentTypeImageResourceEnum =
                    Enum.valueOf(headerContentTypeEnumClass, "IMAGE_RESOURCE");

            Class<? extends Enum> headerImageSizeEnumClass =
                    (Class<? extends Enum>) emoticonHeaderImageSizeClass.asSubclass(Enum.class);
            headerImageSizeSmallEnum = Enum.valueOf(headerImageSizeEnumClass, "SMALL");

            headerScrollBehaviorField = emoticonHeaderScrollControllerClass.getDeclaredField("b");
            headerScrollBehaviorField.setAccessible(true);
            headerScrollSelectedIndexField =
                    startElementScrollBehaviorClass.getDeclaredField("h");
            headerScrollSelectedIndexField.setAccessible(true);

            gridLayoutManagerSetSpanCountMethod =
                    gridLayoutManagerClass.getDeclaredMethod("z", int.class);
            gridLayoutManagerSetSpanCountMethod.setAccessible(true);
        }

        Object buildKeyboardType(String keyboardType) throws Throwable {
            return keyboardTypeFromStringMethod.invoke(null, keyboardType);
        }
    }

    private static final class KeyboardProviderReceiverBridge implements InvocationHandler {
        private final Handles handles;
        private final Object originalKeyboardType;
        private final Object rewrittenKeyboardType;
        private final Object delegate;

        KeyboardProviderReceiverBridge(
                Handles handles,
                Object originalKeyboardType,
                Object rewrittenKeyboardType,
                Object delegate) {
            this.handles = handles;
            this.originalKeyboardType = originalKeyboardType;
            this.rewrittenKeyboardType = rewrittenKeyboardType;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            Object[] bridgedArgs = bridgeKeyboardTypeArgs(args);
            try {
                return method.invoke(delegate, bridgedArgs);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return "KeyboardProviderReceiverBridge(" + delegate + ")";
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                Object other = args != null && args.length > 0 ? args[0] : null;
                return Boolean.valueOf(proxy == other);
            }
            return null;
        }

        private Object[] bridgeKeyboardTypeArgs(Object[] args) {
            if (args == null || args.length == 0 || args[0] == null) {
                return args;
            }
            String keyboardTypeName = extractKeyboardTypeName(handles, args[0]);
            String rewrittenTypeName = extractKeyboardTypeName(handles, rewrittenKeyboardType);
            if (!java.util.Objects.equals(rewrittenTypeName, keyboardTypeName)) {
                return args;
            }
            Object[] bridged = args.clone();
            bridged[0] = originalKeyboardType;
            return bridged;
        }
    }
}
