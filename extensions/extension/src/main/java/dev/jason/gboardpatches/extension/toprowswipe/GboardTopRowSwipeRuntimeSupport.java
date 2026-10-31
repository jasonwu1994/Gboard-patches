package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class GboardTopRowSwipeRuntimeSupport {
    static final int PRIMARY_LABEL_VIEW_ID = 0x7f0b05f5;
    static final int PLAIN_TEXT_KEYCODE = -0x2719;
    static final String[] TOP_ROW_SLOT_VIEW_NAMES = {
            "E01", "E02", "E03", "E04", "E05",
            "E06", "E07", "E08", "E09", "E10"
    };
    static final String[] ENGLISH_QWERTY_TOP_ROW_LABELS = {
            "q", "w", "e", "r", "t",
            "y", "u", "i", "o", "p"
    };
    static final String[] ZHUYIN_TOP_ROW_LABELS = {
            "ㄅ", "ㄉ", "ˇ", "ˋ", "ㄓ",
            "ˊ", "˙", "ㄚ", "ㄞ", "ㄢ"
    };

    private static final String SOFT_KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String ACTION_TYPE_CLASS = "qae";
    private static final String ACTION_SET_CLASS =
            "com.google.android.libraries.inputmethod.metadata.SoftKeyDef";
    private static final String ACTION_DEF_CLASS =
            "com.google.android.libraries.inputmethod.metadata.ActionDef";
    private static final String ACTION_DATA_CLASS = "qbc";
    private static final String ACTION_DEF_BUILDER_CLASS = "qaf";
    private static final String ACTION_SET_BUILDER_CLASS = "qcv";
    private static final String INTENTION_CLASS = "qbb";
    private static final String POINTER_TRACKER_CLASS = "qin";
    private static final String GESTURE_DISPATCHER_CLASS = "qil";

    private static final String ACTION_NAME_PRESS = "PRESS";
    private static final String ACTION_NAME_LONG_PRESS = "LONG_PRESS";
    private static final String ACTION_NAME_SLIDE_UP = "SLIDE_UP";
    private static final String ACTION_NAME_SLIDE_DOWN = "SLIDE_DOWN";
    private static final String INTENTION_NAME_COMMIT = "COMMIT";

    private static final Map<String, String> LEGACY_ENGLISH_QWERTY_SLIDE_DOWN =
            createLegacyEnglishQwertySlideDownMap();
    private static final Map<ClassLoader, ReflectionHandles> REFLECTION_BY_LOADER =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, ReflectionHandles>());

    private GboardTopRowSwipeRuntimeSupport() {
    }

    enum LegacyKeyboardKind {
        NONE,
        ZHUYIN,
        ENGLISH_QWERTY
    }

    static final class KeyBehavior {
        final String primaryLabel;
        final String pressText;
        final String[] tokens;
        final boolean hasSlideUpAction;
        final boolean hasSlideDownAction;
        final LegacyKeyboardKind legacyKeyboardKind;

        KeyBehavior(String primaryLabel, String pressText, String[] tokens,
                boolean hasSlideUpAction, boolean hasSlideDownAction,
                LegacyKeyboardKind legacyKeyboardKind) {
            this.primaryLabel = primaryLabel;
            this.pressText = pressText;
            this.tokens = tokens;
            this.hasSlideUpAction = hasSlideUpAction;
            this.hasSlideDownAction = hasSlideDownAction;
            this.legacyKeyboardKind = legacyKeyboardKind;
        }
    }

    static final class LabelSet {
        final int[] ids;
        final CharSequence[] texts;

        LabelSet(int[] ids, CharSequence[] texts) {
            this.ids = ids;
            this.texts = texts;
        }
    }

    static ReflectionHandles reflectionHandles(ClassLoader classLoader) throws Throwable {
        synchronized (REFLECTION_BY_LOADER) {
            ReflectionHandles cached = REFLECTION_BY_LOADER.get(classLoader);
            if (cached != null) {
                return cached;
            }
            ReflectionHandles created = new ReflectionHandles(classLoader);
            REFLECTION_BY_LOADER.put(classLoader, created);
            return created;
        }
    }

    static KeyBehavior inspectKeyBehavior(ReflectionHandles handles, Object keyMetadata)
            throws Throwable {
        String primaryLabel = handles.extractPrimaryLabel(keyMetadata);
        Object pressAction = handles.findExactAction(keyMetadata, handles.pressActionType);
        String pressText = handles.extractSinglePayloadToken(pressAction);
        Object longPressAction = handles.findExactAction(keyMetadata, handles.longPressActionType);
        String[] tokens = handles.extractPayloadTokens(longPressAction);
        boolean hasSlideUpAction =
                handles.findExactAction(keyMetadata, handles.slideUpActionType) != null;
        boolean hasSlideDownAction =
                handles.findExactAction(keyMetadata, handles.slideDownActionType) != null;

        if (resolveEnglishQwertyKeyboardKind(primaryLabel, pressText)) {
            return new KeyBehavior(primaryLabel, pressText, tokens, hasSlideUpAction,
                    hasSlideDownAction, LegacyKeyboardKind.ENGLISH_QWERTY);
        }
        boolean legacyZhuyinCandidate = isLegacyZhuyinSlideCandidate(primaryLabel, tokens);
        return new KeyBehavior(primaryLabel, pressText, tokens, hasSlideUpAction,
                hasSlideDownAction,
                legacyZhuyinCandidate ? LegacyKeyboardKind.ZHUYIN : LegacyKeyboardKind.NONE);
    }

    static LabelSet appendOrReplaceTextLabelIds(int[] existingIds,
            CharSequence[] existingTexts, int labelId, CharSequence labelText) {
        if (labelText == null || labelText.toString().isBlank()) {
            return new LabelSet(existingIds, existingTexts);
        }
        int safeLength = Math.min(existingIds.length, existingTexts.length);
        int existingIndex = -1;
        for (int index = 0; index < safeLength; index++) {
            if (existingIds[index] == labelId) {
                existingIndex = index;
                break;
            }
        }
        if (existingIndex >= 0) {
            int[] updatedIds = existingIds.clone();
            CharSequence[] updatedTexts = existingTexts.clone();
            updatedTexts[existingIndex] = labelText;
            return new LabelSet(updatedIds, updatedTexts);
        }
        int[] updatedIds = new int[safeLength + 1];
        CharSequence[] updatedTexts = new CharSequence[safeLength + 1];
        System.arraycopy(existingIds, 0, updatedIds, 0, safeLength);
        System.arraycopy(existingTexts, 0, updatedTexts, 0, safeLength);
        updatedIds[safeLength] = labelId;
        updatedTexts[safeLength] = labelText;
        return new LabelSet(updatedIds, updatedTexts);
    }

    static String safeViewName(View view) {
        if (view == null) {
            return null;
        }
        if (view.getId() == View.NO_ID) {
            return null;
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static int topRowSlotIndexForView(View view) {
        String viewName = safeViewName(view);
        if (viewName == null) {
            return -1;
        }
        for (int index = 0; index < TOP_ROW_SLOT_VIEW_NAMES.length; index++) {
            if (TOP_ROW_SLOT_VIEW_NAMES[index].equals(viewName)) {
                return index;
            }
        }
        return -1;
    }

    static int englishQwertyTopRowIndexForLabel(String primaryLabel) {
        String normalizedLabel = asciiLowercase(primaryLabel);
        for (int index = 0; index < ENGLISH_QWERTY_TOP_ROW_LABELS.length; index++) {
            if (ENGLISH_QWERTY_TOP_ROW_LABELS[index].equals(normalizedLabel)) {
                return index;
            }
        }
        return -1;
    }

    static boolean isEnglishQwertyTopRowLabels(List<String> labels) {
        if (labels == null || labels.size() != ENGLISH_QWERTY_TOP_ROW_LABELS.length) {
            return false;
        }
        for (int index = 0; index < ENGLISH_QWERTY_TOP_ROW_LABELS.length; index++) {
            if (!ENGLISH_QWERTY_TOP_ROW_LABELS[index]
                    .equals(asciiLowercase(labels.get(index)))) {
                return false;
            }
        }
        return true;
    }

    static boolean matchesZhuyinTopRowLabel(int rowIndex, String label) {
        if (rowIndex < 0 || rowIndex >= ZHUYIN_TOP_ROW_LABELS.length) {
            return false;
        }
        String expected = ZHUYIN_TOP_ROW_LABELS[rowIndex];
        return expected.equals(label)
                || ("˙".equals(expected) && "·".equals(label));
    }

    static boolean isLegacyZhuyinSlideCandidate(String primaryLabel, String[] tokens) {
        if (primaryLabel != null && isZhuyinSymbolToken(primaryLabel)) {
            return resolveLegacySlideText(tokens, false) != null
                    || resolveLegacySlideText(tokens, true) != null;
        }
        if (tokens == null || tokens.length < 2) {
            return false;
        }
        String primaryToken = firstNonBlankToken(tokens);
        if (primaryToken == null || !isZhuyinSymbolToken(primaryToken)) {
            return false;
        }
        return resolveLegacySlideText(tokens, false) != null
                || resolveLegacySlideText(tokens, true) != null;
    }

    private static boolean resolveEnglishQwertyKeyboardKind(String primaryLabel,
            String pressText) {
        String baseLabel = primaryLabel;
        if ((baseLabel == null || baseLabel.isBlank())
                && pressText != null && !pressText.isBlank()) {
            baseLabel = pressText;
        }
        if (!isAsciiLetterToken(baseLabel)) {
            return false;
        }
        if (pressText != null && !pressText.isBlank() && !isAsciiLetterToken(pressText)) {
            return false;
        }
        return LEGACY_ENGLISH_QWERTY_SLIDE_DOWN.containsKey(asciiLowercase(baseLabel));
    }

    private static Map<String, String> createLegacyEnglishQwertySlideDownMap() {
        Map<String, String> overrides = new HashMap<String, String>();
        overrides.put("q", "1");
        overrides.put("w", "2");
        overrides.put("e", "3");
        overrides.put("r", "4");
        overrides.put("t", "5");
        overrides.put("y", "6");
        overrides.put("u", "7");
        overrides.put("i", "8");
        overrides.put("o", "9");
        overrides.put("p", "0");
        overrides.put("a", "@");
        overrides.put("s", "*");
        overrides.put("d", "+");
        overrides.put("f", "-");
        overrides.put("g", "=");
        overrides.put("h", "/");
        overrides.put("j", "#");
        overrides.put("k", "(");
        overrides.put("l", ")");
        overrides.put("z", "'");
        overrides.put("x", ":");
        overrides.put("c", "\"");
        overrides.put("v", "?");
        overrides.put("b", "!");
        overrides.put("n", "~");
        overrides.put("m", "…");
        return Collections.unmodifiableMap(overrides);
    }

    static String asciiLowercase(String token) {
        if (!isAsciiLetterToken(token)) {
            return token;
        }
        char value = token.charAt(0);
        if (value >= 'A' && value <= 'Z') {
            value = (char) (value - 'A' + 'a');
        }
        return String.valueOf(value);
    }

    static boolean isAsciiLetterToken(String token) {
        if (token == null || token.length() != 1) {
            return false;
        }
        char value = token.charAt(0);
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    static String firstNonBlankToken(String[] tokens) {
        if (tokens == null) {
            return null;
        }
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        return null;
    }

    static boolean isZhuyinSymbolToken(String token) {
        if (token == null || token.length() != 1) {
            return false;
        }
        char value = token.charAt(0);
        return (value >= '\u3105' && value <= '\u3129')
                || value == '\u00B7'
                || value == '\u02CA'
                || value == '\u02C7'
                || value == '\u02CB'
                || value == '\u02D9';
    }

    static String resolveLegacySlideText(String[] tokens, boolean slideUp) {
        if (tokens == null || tokens.length < 2) {
            return null;
        }
        List<String> nonBlankTokens = new ArrayList<String>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                nonBlankTokens.add(token);
            }
        }
        if (nonBlankTokens.size() < 2) {
            return null;
        }
        if (slideUp) {
            return nonBlankTokens.size() >= 3 ? nonBlankTokens.get(2) : null;
        }
        return nonBlankTokens.get(1);
    }

    private static Class<?> resolveClass(ClassLoader classLoader, String className)
            throws ClassNotFoundException {
        return Class.forName(className, false, classLoader);
    }

    static final class ReflectionHandles {
        final Field softKeyMetadataField;
        final Field softKeyBindTokenField;
        final Field actionDefsField;
        final Field keyLabelTextsField;
        final Field keyLabelIdsField;
        final Method bindSoftKeyMethod;
        final Method exactActionLookupMethod;
        final Field actionDefTypeField;
        final Field actionEntriesField;
        final Field actionPayloadField;
        final Constructor<?> actionBuilderConstructor;
        final Field actionTypeField;
        final Method setPopupLabelsMethod;
        final Method setSingleActionMethod;
        final Method buildActionMethod;
        final Constructor<?> keyMetadataBuilderConstructor;
        final Method copyKeyMetadataMethod;
        final Method buildKeyMetadataMethod;
        final Method putActionMethod;
        final Method setKeyLabelTextsMethod;
        final Field gestureDispatcherContextField;
        final Object commitIntention;
        final Object pressActionType;
        final Object longPressActionType;
        final Object slideUpActionType;
        final Object slideDownActionType;

        ReflectionHandles(ClassLoader classLoader) throws Throwable {
            Class<?> softKeyViewClass = resolveClass(classLoader, SOFT_KEY_VIEW_CLASS);
            Class<?> actionTypeClass = resolveClass(classLoader, ACTION_TYPE_CLASS);
            Class<?> actionSetClass = resolveClass(classLoader, ACTION_SET_CLASS);
            Class<?> actionDefClass = resolveClass(classLoader, ACTION_DEF_CLASS);
            Class<?> actionDefBuilderClass = resolveClass(classLoader, ACTION_DEF_BUILDER_CLASS);
            Class<?> actionSetBuilderClass = resolveClass(classLoader, ACTION_SET_BUILDER_CLASS);
            Class<?> intentionClass = resolveClass(classLoader, INTENTION_CLASS);
            Class<?> gestureDispatcherClass = resolveClass(classLoader, GESTURE_DISPATCHER_CLASS);
            resolveClass(classLoader, ACTION_DATA_CLASS);
            resolveClass(classLoader, POINTER_TRACKER_CLASS);

            softKeyMetadataField = softKeyViewClass.getDeclaredField("e");
            softKeyMetadataField.setAccessible(true);

            softKeyBindTokenField = softKeyViewClass.getDeclaredField("f");
            softKeyBindTokenField.setAccessible(true);

            bindSoftKeyMethod = softKeyViewClass.getDeclaredMethod("s", actionSetClass,
                    long.class);
            bindSoftKeyMethod.setAccessible(true);

            actionDefsField = actionSetClass.getDeclaredField("h");
            actionDefsField.setAccessible(true);

            keyLabelTextsField = actionSetClass.getDeclaredField("i");
            keyLabelTextsField.setAccessible(true);

            keyLabelIdsField = actionSetClass.getDeclaredField("j");
            keyLabelIdsField.setAccessible(true);

            exactActionLookupMethod = actionSetClass.getDeclaredMethod("g", actionTypeClass);
            exactActionLookupMethod.setAccessible(true);

            actionDefTypeField = actionDefClass.getDeclaredField("e");
            actionDefTypeField.setAccessible(true);

            actionEntriesField = actionDefClass.getDeclaredField("f");
            actionEntriesField.setAccessible(true);

            Class<?> actionDataClass = resolveClass(classLoader, ACTION_DATA_CLASS);
            actionPayloadField = actionDataClass.getDeclaredField("e");
            actionPayloadField.setAccessible(true);

            actionBuilderConstructor = actionDefBuilderClass.getDeclaredConstructor();
            actionBuilderConstructor.setAccessible(true);

            actionTypeField = actionDefBuilderClass.getDeclaredField("a");
            actionTypeField.setAccessible(true);

            setPopupLabelsMethod = actionDefBuilderClass.getDeclaredMethod("t", String[].class);
            setPopupLabelsMethod.setAccessible(true);

            setSingleActionMethod = actionDefBuilderClass.getDeclaredMethod("s", int.class,
                    intentionClass, Object.class);
            setSingleActionMethod.setAccessible(true);

            buildActionMethod = actionDefBuilderClass.getDeclaredMethod("c");
            buildActionMethod.setAccessible(true);

            keyMetadataBuilderConstructor = actionSetBuilderClass.getDeclaredConstructor();
            keyMetadataBuilderConstructor.setAccessible(true);

            copyKeyMetadataMethod = actionSetBuilderClass.getDeclaredMethod("k", actionSetClass);
            copyKeyMetadataMethod.setAccessible(true);

            buildKeyMetadataMethod = actionSetBuilderClass.getDeclaredMethod("b");
            buildKeyMetadataMethod.setAccessible(true);

            putActionMethod = actionSetBuilderClass.getDeclaredMethod("t", actionDefClass);
            putActionMethod.setAccessible(true);

            setKeyLabelTextsMethod = actionSetBuilderClass.getDeclaredMethod("w", int[].class,
                    List.class);
            setKeyLabelTextsMethod.setAccessible(true);

            gestureDispatcherContextField = gestureDispatcherClass.getDeclaredField("b");
            gestureDispatcherContextField.setAccessible(true);

            commitIntention = Enum.valueOf(intentionClass.asSubclass(Enum.class),
                    INTENTION_NAME_COMMIT);
            pressActionType = Enum.valueOf(actionTypeClass.asSubclass(Enum.class),
                    ACTION_NAME_PRESS);
            longPressActionType = Enum.valueOf(actionTypeClass.asSubclass(Enum.class),
                    ACTION_NAME_LONG_PRESS);
            slideUpActionType = Enum.valueOf(actionTypeClass.asSubclass(Enum.class),
                    ACTION_NAME_SLIDE_UP);
            slideDownActionType = Enum.valueOf(actionTypeClass.asSubclass(Enum.class),
                    ACTION_NAME_SLIDE_DOWN);
        }

        void rebindSoftKeyView(Object softKeyView, Object keyMetadata) throws Throwable {
            if (softKeyView == null || keyMetadata == null) {
                return;
            }
            Object token = softKeyBindTokenField.get(softKeyView);
            long bindToken = token instanceof Long value ? value.longValue() : 0L;
            bindSoftKeyMethod.invoke(softKeyView, keyMetadata, bindToken);
        }

        Object findExactAction(Object keyMetadata, Object actionType) throws Throwable {
            return exactActionLookupMethod.invoke(keyMetadata, actionType);
        }

        String[] extractPayloadTokens(Object actionDef) throws Throwable {
            if (actionDef == null) {
                return new String[0];
            }
            Object actionsObject = actionEntriesField.get(actionDef);
            if (!(actionsObject instanceof List<?> actionEntries) || actionEntries.isEmpty()) {
                return new String[0];
            }
            List<String> tokens = new ArrayList<String>();
            for (Object actionEntry : actionEntries) {
                if (actionEntry == null) {
                    continue;
                }
                Object payload = actionPayloadField.get(actionEntry);
                if (payload instanceof CharSequence charSequence) {
                    String token = charSequence.toString();
                    if (!token.isBlank()) {
                        tokens.add(token);
                    }
                }
            }
            return tokens.toArray(new String[0]);
        }

        String extractSinglePayloadToken(Object actionDef) throws Throwable {
            String[] tokens = extractPayloadTokens(actionDef);
            return tokens.length == 0 ? null : tokens[0];
        }

        String extractActionDataPayload(Object actionData) throws Throwable {
            if (actionData == null) {
                return null;
            }
            Object payload = actionPayloadField.get(actionData);
            return payload instanceof CharSequence charSequence
                    ? charSequence.toString() : null;
        }

        String extractPrimaryLabel(Object keyMetadata) throws Throwable {
            if (keyMetadata == null) {
                return null;
            }
            Object labelsObject = keyLabelTextsField.get(keyMetadata);
            if (!(labelsObject instanceof List<?> labels)) {
                return null;
            }
            for (Object label : labels) {
                if (label == null) {
                    continue;
                }
                String value = label.toString();
                if (!value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        int[] extractKeyLabelIds(Object keyMetadata) throws Throwable {
            if (keyMetadata == null) {
                return new int[0];
            }
            Object idsObject = keyLabelIdsField.get(keyMetadata);
            return idsObject instanceof int[] ids ? ids : new int[0];
        }

        CharSequence[] extractKeyLabelTexts(Object keyMetadata) throws Throwable {
            if (keyMetadata == null) {
                return new CharSequence[0];
            }
            Object textsObject = keyLabelTextsField.get(keyMetadata);
            return textsObject instanceof List<?> texts
                    ? texts.toArray(new CharSequence[0]) : new CharSequence[0];
        }

        Context extractGestureDispatcherContext(Object gestureDispatcher) throws Throwable {
            if (gestureDispatcher == null) {
                return null;
            }
            Object contextObject = gestureDispatcherContextField.get(gestureDispatcher);
            return contextObject instanceof Context context ? context : null;
        }

        Object buildPlainTextAction(Object actionType, String text, String popupLabel)
                throws Throwable {
            Object builder = actionBuilderConstructor.newInstance();
            actionTypeField.set(builder, actionType);
            setPopupLabelsMethod.invoke(builder, (Object) new String[] { popupLabel });
            setSingleActionMethod.invoke(builder, PLAIN_TEXT_KEYCODE, commitIntention, text);
            Object built = buildActionMethod.invoke(builder);
            if (built == null) {
                return null;
            }
            return built;
        }

        Object buildPlainTextAction(Object actionType, String text) throws Throwable {
            return buildPlainTextAction(actionType, text, text);
        }

        void replaceActionOnKeyMetadataBuilder(Object builder, Object actionType, Object actionDef)
                throws Throwable {
            if (builder == null || actionType == null || actionDef == null) {
                return;
            }
            putActionMethod.invoke(builder, actionDef);
        }
    }
}
