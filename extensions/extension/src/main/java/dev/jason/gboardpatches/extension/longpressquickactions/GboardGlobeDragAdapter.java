package dev.jason.gboardpatches.extension.longpressquickactions;

import android.view.View;

final class GboardGlobeDragAdapter implements GboardGlobeDragPort {
    private static final String GLOBE_VIEW_NAME = "key_pos_qwerty_globe";
    private static final String NEXT_LANGUAGE_VIEW_NAME = "key_pos_switch_to_next_language";
    private static final int MARKER_DOWN = -0x2730;
    private static final int MARKER_UP = -0x2731;
    private static final int SWITCH_TO_NEXT_LANGUAGE = -0x271b;
    private static final int CHORD_EVENT_SUBTYPE = 3;
    private static final int TYPING_PULSE_KEY_CODE = -10043;
    private static final int PLAIN_TEXT_KEY_CODE = -10009;

    private final GboardLongPressQuickActionsReflectionHandles handles;
    private static final GboardGlobeDragBoundSlotRegistry BOUND_SLOTS =
            GboardGlobeDragBoundSlotRegistry.shared();

    GboardGlobeDragAdapter(
            GboardLongPressQuickActionsReflectionHandles handles) {
        if (handles == null) {
            throw new IllegalArgumentException("18.5.4 globe adapter requires handles");
        }
        this.handles = handles;
    }

    @Override
    public boolean isGlobeView(View view) {
        if (view == null) {
            return false;
        }
        try {
            String name = view.getResources().getResourceEntryName(view.getId());
            return GLOBE_VIEW_NAME.equals(name) || NEXT_LANGUAGE_VIEW_NAME.equals(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public Object patchGlobeMetadata(Object metadata, Object marker) throws Throwable {
        if (!handles.isStockGlobeMetadata(metadata)) {
            return null;
        }
        return handles.appendShiftChordActions(metadata, MARKER_DOWN, marker, MARKER_UP);
    }

    @Override
    public Object extractSoftKeyMetadata(Object softKeyView) throws Throwable {
        return handles.extractSoftKeyMetadata(softKeyView);
    }

    @Override
    public void observeBoundKey(Object metadata, View view) throws Throwable {
        if (metadata == null || view == null) {
            return;
        }
        int keyId = handles.extractKeyId(metadata);
        if (GboardLongPressQuickActionsPolicy.isZhuyinKeyId(keyId)) {
            return;
        }
        String viewEntryName;
        try {
            viewEntryName = view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return;
        }
        BOUND_SLOTS.observe(
                metadata,
                shortcutSlot(viewEntryName),
                keyId,
                handles.extractPressText(metadata),
                handles.extractPressCarrierCode(metadata),
                TYPING_PULSE_KEY_CODE);
    }

    @Override
    public InputSignal inspectInputEvent(Object event, Object marker) throws Throwable {
        Object metadata = handles.extractEventMetadata(event);
        String actionType = handles.extractEventActionTypeName(event);
        int code = handles.extractSelectedEventCode(event);
        Object payload = handles.extractSelectedEventPayload(event);
        boolean markerDown = "DOWN".equals(actionType)
                && code == MARKER_DOWN && marker.equals(payload);
        boolean markerUp = "UP".equals(actionType) && code == MARKER_UP;
        int subtype = requiresEventSubtype(markerDown, markerUp)
                ? handles.extractEventSubtype(event) : 0;
        return new InputSignal(
                actionType,
                code,
                metadata,
                markerDown,
                markerUp,
                "PRESS".equals(actionType) && code == SWITCH_TO_NEXT_LANGUAGE,
                subtype == CHORD_EVENT_SUBTYPE);
    }

    static boolean requiresEventSubtype(boolean markerDown, boolean markerUp) {
        return !markerDown && !markerUp;
    }

    @Override
    public GestureSignal inspectGesture(Object actionType, Object entry, Object metadata)
            throws Throwable {
        String actionTypeName = handles.extractActionTypeName(actionType);
        int code = handles.extractEntryCode(entry);
        return new GestureSignal(
                actionTypeName,
                code,
                metadata,
                "PRESS".equals(actionTypeName) && code == SWITCH_TO_NEXT_LANGUAGE,
                inspectTerminalTarget(metadata, code, handles.extractEntryPayload(entry)));
    }

    @Override
    public TargetSignal inspectPointerTarget(Object softKeyView, Object metadata) throws Throwable {
        if (GboardLongPressQuickActionsPolicy.isZhuyinKeyId(
                handles.extractKeyId(metadata))) {
            return inspectPointerTarget(null, metadata);
        }
        String viewEntryName = null;
        if (softKeyView instanceof View view) {
            viewEntryName = view.getResources().getResourceEntryName(view.getId());
        }
        return inspectPointerTarget(viewEntryName, metadata);
    }

    TargetSignal inspectPointerTarget(String viewEntryName, Object metadata) throws Throwable {
        int keyId = handles.extractKeyId(metadata);
        String pressText = handles.extractPressText(metadata);
        int pressCarrier = handles.extractPressCarrierCode(metadata);
        GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot physicalSlot = null;
        GboardEditingShortcutPolicy.Shortcut shortcut;
        if (GboardLongPressQuickActionsPolicy.isZhuyinKeyId(keyId)) {
            shortcut = GboardLongPressQuickActionsPolicy.shortcutForPointerTarget(
                    keyId, pressText, pressCarrier, TYPING_PULSE_KEY_CODE);
        } else {
            physicalSlot = shortcutSlot(viewEntryName);
            shortcut = GboardGlobeDragPhysicalSlotPolicy.resolve(
                    physicalSlot,
                    pressText,
                    pressCarrier,
                    TYPING_PULSE_KEY_CODE);
        }
        return new TargetSignal(
                metadata,
                physicalSlot,
                keyId,
                pressText,
                Integer.valueOf(pressCarrier),
                shortcut,
                pressText != null);
    }

    @Override
    public TargetSignal inspectTerminalTarget(Object metadata, int selectedCode)
            throws Throwable {
        return inspectTerminalTarget(metadata, selectedCode, null);
    }

    private TargetSignal inspectTerminalTarget(Object metadata, int selectedCode,
            Object entryPayload) throws Throwable {
        int keyId = handles.extractKeyId(metadata);
        String pressText = handles.extractPressText(metadata);
        int pressCarrier = handles.extractPressCarrierCode(metadata);
        boolean zhuyinKey = GboardLongPressQuickActionsPolicy.isZhuyinKeyId(keyId);
        GboardEditingShortcutPolicy.Shortcut zhuyinShortcut = zhuyinKey
                ? GboardLongPressQuickActionsPolicy.shortcutForChord(
                    keyId,
                    pressText,
                    selectedCode,
                    TYPING_PULSE_KEY_CODE,
                    PLAIN_TEXT_KEY_CODE)
                : null;
        GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot boundSlot = zhuyinKey
                ? null
                : BOUND_SLOTS.resolveSlot(
                    metadata,
                    keyId,
                    pressText,
                    pressCarrier,
                    TYPING_PULSE_KEY_CODE);
        GboardEditingShortcutPolicy.Shortcut boundSlotShortcut =
                GboardGlobeDragPhysicalSlotPolicy.resolve(
                    boundSlot,
                    pressText,
                    pressCarrier,
                    TYPING_PULSE_KEY_CODE);
        GboardEditingShortcutPolicy.Shortcut shortcut = resolveTerminalShortcut(
                zhuyinKey, zhuyinShortcut, boundSlotShortcut);
        return new TargetSignal(
                metadata, boundSlot,
                keyId, pressText, entryPayload, shortcut, pressText != null);
    }

    @Override
    public boolean cancelScheduledLongPress(Object pointerTracker) throws Throwable {
        return handles.cancelScheduledLongPress(pointerTracker);
    }

    static GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot shortcutSlot(
            String viewEntryName) {
        if (viewEntryName == null) {
            return null;
        }
        return switch (viewEntryName) {
            case "C01" -> GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.SELECT_ALL;
            case "B01" -> GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.UNDO;
            case "B02" -> GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.CUT;
            case "B03" -> GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.COPY;
            case "B04" -> GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.PASTE;
            case "D06" -> GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.REDO;
            default -> null;
        };
    }

    static GboardEditingShortcutPolicy.Shortcut resolveTerminalShortcut(
            boolean zhuyinKey,
            GboardEditingShortcutPolicy.Shortcut zhuyinShortcut,
            GboardEditingShortcutPolicy.Shortcut boundSlotShortcut) {
        return zhuyinKey ? zhuyinShortcut : boundSlotShortcut;
    }
}
