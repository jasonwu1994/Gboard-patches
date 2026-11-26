package dev.jason.gboardpatches.extension.editingaccesspoints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GboardEditingAccessPointsPolicyTest {
    @Test public void appendPreservesOrderAndDeduplicates() {
        List<String> stock = Arrays.asList("emoji", "web_search", "text_edit_copy", "emoji");
        List<String> actual = GboardEditingAccessPointsContribution.extend(stock, action -> true);
        List<String> expected = new ArrayList<>(Arrays.asList("emoji", "web_search", "text_edit_copy"));
        for (EditorAction action : EditorAction.values()) {
            if (!expected.contains(action.token)) expected.add(action.token);
        }
        assertEquals(expected, actual);
        assertEquals(actual, GboardEditingAccessPointsContribution.extend(actual, action -> true));
    }

    @Test public void individualAdmissionDoesNotEnableOtherActions() {
        assertEquals(Collections.singletonList("text_edit_paste"),
                GboardEditingAccessPointsContribution.extend(Collections.emptyList(),
                        action -> action == EditorAction.PASTE));
        assertTrue(GboardEditingAccessPointsContribution.extend(Collections.emptyList(),
                action -> false).isEmpty());
    }

    @Test public void settingsDefaultOnButUnavailableAndMalformedFailClosed() {
        String key = GboardEditingAccessPointsSettings.ENABLED;
        assertTrue(GboardEditingAccessPointsSettings.read(Collections.emptyMap(), key));
        assertFalse(GboardEditingAccessPointsSettings.read((Map<String, ?>) null, key));
        for (Object value : Arrays.asList(false, "false", 1, "TRUE", "")) {
            assertFalse(GboardEditingAccessPointsSettings.read(Collections.singletonMap(key, value), key));
        }
        assertTrue(GboardEditingAccessPointsSettings.read(Collections.singletonMap(key, "true"), key));
        assertTrue(GboardEditingAccessPointsSettings.read(Collections.singletonMap(key, true), key));
        assertEquals("pref_editing_access_points_text_edit_cut",
                GboardEditingAccessPointsSettings.key(EditorAction.CUT));
    }

    @Test public void coreButtonsDefaultOnAndExtendedButtonsDefaultOff() {
        for (EditorAction action : EditorAction.values()) {
            String key = GboardEditingAccessPointsSettings.key(action);
            assertEquals(action.name(), action.defaultEnabled,
                    GboardEditingAccessPointsSettings.read(Collections.emptyMap(), key));
            assertTrue(GboardEditingAccessPointsSettings.read(Collections.singletonMap(key, "true"), key));
            assertFalse(GboardEditingAccessPointsSettings.read(Collections.singletonMap(key, false), key));
        }
        List<EditorAction> core = Arrays.asList(EditorAction.SELECT_ALL, EditorAction.COPY,
                EditorAction.CUT, EditorAction.PASTE);
        for (EditorAction action : EditorAction.values()) {
            assertEquals(action.name(), core.contains(action), action.defaultEnabled);
        }
        assertFalse(GboardEditingAccessPointsSettings.read(Collections.emptyMap(),
                GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED));
        assertFalse(GboardEditingAccessPointsSettings.read(Collections.emptyMap(), (String) null));
    }

    @Test public void keyActionsCarryKeyCodesAndMenuActionsCarryMenuIds() {
        for (EditorAction action : EditorAction.values()) {
            if (action.dispatchType == EditorAction.DispatchType.KEY_EVENT) {
                assertTrue(action.name(), action.keyCode > 0);
                assertEquals(action.name(), 0, action.contextMenuId);
            } else {
                assertNotEquals(action.name(), 0, action.contextMenuId);
                assertEquals(action.name(), 0, action.keyCode);
            }
        }
    }

    @Test public void englishFallbackLabels() {
        assertEquals("Select All", GboardEditingAccessPointsContribution.label(EditorAction.SELECT_ALL));
        assertEquals("Copy", GboardEditingAccessPointsContribution.label(EditorAction.COPY));
        assertEquals("Cut", GboardEditingAccessPointsContribution.label(EditorAction.CUT));
        assertEquals("Paste", GboardEditingAccessPointsContribution.label(EditorAction.PASTE));
    }
}
