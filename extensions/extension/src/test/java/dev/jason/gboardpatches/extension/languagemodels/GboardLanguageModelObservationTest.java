package dev.jason.gboardpatches.extension.languagemodels;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class GboardLanguageModelObservationTest {
    private Context context;
    private SharedPreferences preferences;
    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        preferences = GboardPatchesSettings.preferences(context);
        preferences.edit().clear().commit();
        GboardLanguageModelObservationControl.initialize(context);
        GboardLanguageModelRuntime.resetObservation();
    }
    @After public void stopPublisher() { GboardLanguageModelRuntime.resetObservation(); }

    @Test public void defaultOffDoesNotCollectOrStartAWorker() throws Exception {
        assertFalse(GboardLanguageModelRuntime.isObservationEnabled());
        GboardLanguageModelRuntime.afterLanguageModelLoad(new Object(), new Object(), true);
        GboardLanguageModelRuntime.afterEngineAdded(new Object(), new Object());
        GboardLanguageModelRuntime.afterHmmEnroll(new Object(), "data_scheme", true);
        assertTrue(((Map<?, ?>) field("loaded")).isEmpty());
        assertTrue(((Map<?, ?>) field("effective")).isEmpty());
        assertNull(field("worker"));
    }

    @Test public void toggleKeepsResourceChoicesAndRotatesSessions() {
        preferences.edit().putBoolean(GboardLanguageModelSettings.ENABLED, true)
                .putString(GboardLanguageModelCatalog.Family.HMM.preferenceKey(), GboardLanguageModelCatalog.PIXEL).commit();
        String policy = GboardLanguageModelSettings.read(preferences).signature();
        assertTrue(GboardLanguageModelSettings.writeObservation(context, true));
        String first = GboardLanguageModelSettings.observation(preferences).session();
        assertFalse(first.isEmpty());
        assertTrue(GboardLanguageModelSettings.writeObservation(context, false));
        assertTrue(GboardLanguageModelSettings.writeObservation(context, true));
        assertNotEquals(first, GboardLanguageModelSettings.observation(preferences).session());
        assertEquals(policy, GboardLanguageModelSettings.read(preferences).signature());
    }

    @Test public void enablingRestartsOnceAndTheControlStaysAboveResults() {
        class Host extends GboardPatchesSettingsTestHost {
            int restarts;
            @Override public void requestTargetRestart() {
                assertTrue(GboardLanguageModelSettings.observation(preferences).enabled());
                restarts++;
            }
        }
        Host host = new Host();
        var page = details(host);
        var screen = page.buildScreen(host);
        assertTrue(screen.isMasterToggleFirst());
        assertTrue(page.updatesDuringKeyboardPreview());
        assertEquals(0, screen.getRefreshIntervalMs());
        var toggle = (GboardPatchesSettingsContract.ToggleRow) screen.getSections().get(0).getItems().get(0);
        assertFalse(toggle.isChecked()); assertFalse(toggle.isRestartRequired());
        toggle.getToggleAction().accept(true);
        assertEquals(1, host.restarts);
        toggle.getToggleAction().accept(true);
        toggle.getToggleAction().accept(false);
        assertEquals(1, host.restarts);
        toggle.getToggleAction().accept(true);
        assertEquals(2, host.restarts);
        var parent = new GboardLanguageModelSettingsFeature(context);
        assertTrue(parent.getNavigationChildren().stream().anyMatch(child -> child.getClass() == page.getClass()));
    }

    @Test public void publicationUpdatesTheVisiblePageAndHiddenPagesReleaseListeners() throws Exception {
        GboardLanguageModelSettings.writeObservation(context, true);
        var host = new GboardPatchesSettingsTestHost(context);
        var page = details(host); page.onVisible(host);
        GboardLanguageModelStatusStore.write(context, report(101));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(host.getRefreshCount() > 0);
        assertTrue(page.buildScreen(host).getStatusBlocks().stream().anyMatch(b -> b.getTitle().contains("2026101403")));
        assertFalse(page.buildScreen(host).getStatusBlocks().stream().anyMatch(b -> b.getTitle().equals("Observed loaded resources")));
        page.onHidden(host);
        int count = host.getRefreshCount();
        GboardLanguageModelStatusStore.write(context, report(102));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(count, host.getRefreshCount());
    }

    @Test public void oldOrDisabledSessionCannotOverwriteStatusOrAppearInThePage() throws Exception {
        GboardLanguageModelSettings.writeObservation(context, true);
        JSONObject first = report(101);
        GboardLanguageModelStatusStore.write(context, first);
        GboardLanguageModelSettings.writeObservation(context, false);
        JSONObject late = new JSONObject(first.toString()).put("pid", 999);
        GboardLanguageModelStatusStore.write(context, late);
        assertEquals(101, GboardLanguageModelStatusStore.read(context).getInt("pid"));
        GboardLanguageModelSettings.writeObservation(context, true);
        GboardLanguageModelStatusStore.write(context, late);
        assertEquals(101, GboardLanguageModelStatusStore.read(context).getInt("pid"));
        var host = new GboardPatchesSettingsTestHost(context);
        assertFalse(details(host).buildScreen(host).getStatusBlocks().stream().anyMatch(b -> b.getTitle().contains("2026101403")));
        GboardLanguageModelStatusStore.write(context, report(102));
        assertEquals(102, GboardLanguageModelStatusStore.read(context).getInt("pid"));
    }

    @Test public void disablingClearsNativeObservationsAndStopsThePublisher() throws Exception {
        GboardLanguageModelSettings.writeObservation(context, true);
        assertTrue(GboardLanguageModelRuntime.isObservationEnabled());
        ((Map<String, Object>) field("loaded")).put("hmm/zh-TW", new JSONObject());
        var worker = GboardLanguageModelRuntime.class.getDeclaredField("worker"); worker.setAccessible(true);
        var executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        worker.set(null, executor);
        GboardLanguageModelSettings.writeObservation(context, false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(GboardLanguageModelRuntime.isObservationEnabled());
        assertTrue(((Map<?, ?>) field("loaded")).isEmpty());
        assertNull(field("worker")); assertTrue(executor.isShutdown());
    }

    private GboardPatchesSettingsContract.Feature details(GboardPatchesSettingsTestHost host) {
        var screen = new GboardLanguageModelSettingsFeature(context).buildScreen(host);
        ((GboardPatchesSettingsContract.NavigationRow) screen.getSections().get(1).getItems().get(0)).getAction().run();
        return host.getOpenedFeature();
    }
    private JSONObject report(int pid) throws Exception {
        return new JSONObject().put("pid", pid).put("package", context.getPackageName())
                .put("signature", GboardLanguageModelSettings.read(preferences).signature())
                .put("observation_session", GboardLanguageModelSettings.observation(preferences).session())
                .put("models", new JSONObject().put("hmm/zh-TW", new JSONObject().put("family", "hmm")
                        .put("locale", "zh-TW").put("stage", "enrolled").put("version", 2026101403L)));
    }
    private Object field(String name) throws Exception {
        Field field = GboardLanguageModelRuntime.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
}
