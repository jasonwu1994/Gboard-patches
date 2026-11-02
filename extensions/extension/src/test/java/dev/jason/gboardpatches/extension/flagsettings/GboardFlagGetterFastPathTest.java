package dev.jason.gboardpatches.extension.flagsettings;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import dev.jason.gboardpatches.extension.accesspointcount.GboardAccessPointCountRuntime;
import dev.jason.gboardpatches.extension.accesspointcount.GboardAccessPointCountSettings;
import dev.jason.gboardpatches.extension.closeproactivesuggestions.GboardCloseProactiveSuggestionsRuntime;
import dev.jason.gboardpatches.extension.closeproactivesuggestions.GboardCloseProactiveSuggestionsSettings;
import dev.jason.gboardpatches.extension.quickinsert.GboardQuickInsertRuntime;
import dev.jason.gboardpatches.extension.quickinsert.GboardQuickInsertSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.writingtools.GboardAiWritingToolsRuntime;
import dev.jason.gboardpatches.extension.writingtools.GboardAiWritingToolsSettings;

@RunWith(RobolectricTestRunner.class)
public final class GboardFlagGetterFastPathTest {
    private CountingContext context;
    private Object oldFlagContext;
    private Object oldWritingContext;

    @Before public void setUp() throws Exception {
        context = new CountingContext();
        oldFlagContext = contextField(GboardFlagRuntimeContext.class).get(null);
        oldWritingContext = contextField(GboardAiWritingToolsRuntime.class).get(null);
        contextField(GboardFlagRuntimeContext.class).set(null, context);
        contextField(GboardAiWritingToolsRuntime.class).set(null, context);
        preferences().edit().clear()
                .putBoolean(GboardQuickInsertSettings.PREF_KEY_ENABLED, true)
                .putBoolean(GboardCloseProactiveSuggestionsSettings.PREF_KEY_ENABLED, true)
                .putBoolean(GboardAccessPointCountSettings.PREF_KEY_ENABLED, true)
                .putInt(GboardAccessPointCountSettings.PREF_KEY_COUNT, 7).commit();
        context.getSharedPreferences(GboardAiWritingToolsSettings.PREF_FILE, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.lookups = 0;
    }

    @After public void tearDown() throws Exception {
        contextField(GboardFlagRuntimeContext.class).set(null, oldFlagContext);
        contextField(GboardAiWritingToolsRuntime.class).set(null, oldWritingContext);
    }

    @Test public void thousandUnrelatedFlagsDoNotReadSettingsInThreeDelegates() {
        for (int i = 0; i < 1000; i++) {
            assertSame(Boolean.FALSE, GboardQuickInsertRuntime.applyFlagValue("unrelated", Boolean.FALSE));
            assertSame(Boolean.FALSE, GboardCloseProactiveSuggestionsRuntime.applyFlagValue("unrelated", Boolean.FALSE));
            assertSame(Boolean.FALSE, GboardAccessPointCountRuntime.applyFlagValue("unrelated", Boolean.FALSE));
        }
        assertEquals(0, context.lookups);
    }

    @Test public void writingToolsDoesNotSnapshotThousandUnrelatedFlags() {
        for (int i = 0; i < 1000; i++) {
            assertSame(Boolean.FALSE, GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                    new FlagCarrier("unrelated"), Boolean.FALSE));
        }
        assertEquals(0, context.lookups);
    }

    @Test public void targetNamesWithWrongTypesDoNotReadSettings() {
        assertSame("wrong", GboardQuickInsertRuntime.applyFlagValue("super_insert", "wrong"));
        assertSame("wrong", GboardCloseProactiveSuggestionsRuntime.applyFlagValue(
                "enable_close_proactive_suggestions_access_point", "wrong"));
        assertSame(Boolean.FALSE, GboardAccessPointCountRuntime.applyFlagValue("config_max_access_points", Boolean.FALSE));
        assertSame("wrong", GboardAiWritingToolsRuntime.applyOverriddenFlagValue(new FlagCarrier("writing_tools"), "wrong"));
        assertSame(Boolean.FALSE, GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                new FlagCarrier("writing_tools_v2_backend_type"), Boolean.FALSE));
        assertSame(Boolean.FALSE, GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                new FlagCarrier("writing_helper_model_version"), Boolean.FALSE));
        assertEquals(0, context.lookups);
    }

    @Test public void targetFlagsKeepOverridesAndObserveSettingChanges() {
        assertSame(Boolean.TRUE, GboardQuickInsertRuntime.applyFlagValue("super_insert", Boolean.FALSE));
        assertSame(Boolean.TRUE, GboardCloseProactiveSuggestionsRuntime.applyFlagValue(
                "enable_close_proactive_suggestions_access_point", Boolean.FALSE));
        assertEquals(Long.valueOf(7), GboardAccessPointCountRuntime.applyFlagValue("config_max_access_points", 5L));
        assertTrue(context.lookups > 0);
        preferences().edit().putBoolean(GboardQuickInsertSettings.PREF_KEY_ENABLED, false).commit();
        assertSame(Boolean.FALSE, GboardQuickInsertRuntime.applyFlagValue("super_insert", Boolean.FALSE));
    }

    @Test public void writingToolsKeepsBooleanStringAndLongOverridesAndCanBeDisabled() {
        assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.applyOverriddenFlagValue(new FlagCarrier("writing_tools"), Boolean.FALSE));
        assertEquals("*", GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                new FlagCarrier("writing_helper_supported_language_tags"), "en"));
        assertEquals(Long.valueOf(1), GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                new FlagCarrier("writing_tools_v2_backend_type"), 0L));
        assertEquals("202504090000_writing_tools_config", GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                new FlagCarrier("writing_helper_model_version"), "stock"));
        context.getSharedPreferences(GboardAiWritingToolsSettings.PREF_FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(GboardAiWritingToolsSettings.PREF_KEY_ENABLED, false).commit();
        assertSame(Boolean.FALSE, GboardAiWritingToolsRuntime.applyOverriddenFlagValue(new FlagCarrier("writing_tools"), Boolean.FALSE));
    }

    @Test public void allWritingToolsOverridesRemainReachableThroughFastPath() {
        for (String name : new String[] {"config_proofread", "writing_tools",
                "enable_writing_tools_cooperative_mode", "writing_helper_on_selected_text",
                "writing_helper_enable_text_stylization_internal", "writing_helper",
                "enable_writing_tools_for_minors", "enable_nga_lab_modeless_smartedit",
                "enable_writing_tools_voice_commands"}) {
            assertEquals(name, Boolean.TRUE, GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                    new FlagCarrier(name), Boolean.FALSE));
        }
        for (String name : new String[] {"writing_tools_enable_hybrid", "enable_on_device_proofread"}) {
            assertEquals(name, Boolean.FALSE, GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                    new FlagCarrier(name), Boolean.TRUE));
        }
        for (String name : new String[] {"writing_helper_model_version",
                "writing_helper_voice_input_model_version", "writing_helper_text_stylization_model_version"}) {
            assertEquals(name, "202504090000_writing_tools_config",
                    GboardAiWritingToolsRuntime.applyOverriddenFlagValue(new FlagCarrier(name), "stock"));
        }
        for (String name : new String[] {"writing_helper_supported_language_tags", "llm_pc_supported_language_tags"}) {
            assertEquals(name, "*", GboardAiWritingToolsRuntime.applyOverriddenFlagValue(new FlagCarrier(name), "en"));
        }
        assertEquals("v3", GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                new FlagCarrier("nga_lab_modeless_smartedit_regex_version"), ""));
        assertEquals(Long.valueOf(1), GboardAiWritingToolsRuntime.applyOverriddenFlagValue(
                new FlagCarrier("writing_tools_v2_backend_type"), 0L));
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(GboardPatchesSettings.PREF_FILE, Context.MODE_PRIVATE);
    }

    private static Field contextField(Class<?> owner) throws Exception {
        Field field = owner.getDeclaredField("applicationContext");
        field.setAccessible(true);
        return field;
    }

    public static final class FlagCarrier {
        public final String a;
        FlagCarrier(String name) { a = name; }
    }
    private static final class CountingContext extends ContextWrapper {
        int lookups;
        CountingContext() { super(RuntimeEnvironment.getApplication()); }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            lookups++;
            return super.getSharedPreferences(name, mode);
        }
    }
}
