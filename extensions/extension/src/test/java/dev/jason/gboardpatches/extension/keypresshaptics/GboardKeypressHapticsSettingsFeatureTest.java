package dev.jason.gboardpatches.extension.keypresshaptics;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;
import dev.jason.gboardpatches.extension.keypresshaptics.GboardKeypressHapticsSettings;
import dev.jason.gboardpatches.extension.keypresshaptics.GboardKeypressHapticsSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardFeatureEntryState;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardRestartPendingChanges;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = GboardKeypressHapticsSettingsFeatureTest.Store.class)
public final class GboardKeypressHapticsSettingsFeatureTest {
    private GboardKeypressHapticsSettingsFeature feature;
    private GboardPatchesSettingsContract.FeatureHost host;
    private final AtomicInteger refreshes = new AtomicInteger();

    @Before public void prepare() {
        Context app = RuntimeEnvironment.getApplication();
        Resources real = app.getResources();
        Resources labels = new Resources(real.getAssets(), real.getDisplayMetrics(), real.getConfiguration()) {
            @Override public CharSequence getText(int id) { return "label " + id; }
        };
        Context context = new ContextWrapper(app) {
            @Override public Resources getResources() { return labels; }
        };
        host = (GboardPatchesSettingsContract.FeatureHost) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { GboardPatchesSettingsContract.Host.class },
                (proxy, method, args) -> {
                    if ("getContext".equals(method.getName())) return context;
                    if ("refresh".equals(method.getName())) refreshes.incrementAndGet();
                    if ("submit".equals(method.getName())) ((GboardPatchesSettingsContract.Intent) args[0])
                            .apply((GboardPatchesSettingsContract.Host) proxy);
                    return null;
                });
        feature = new GboardKeypressHapticsSettingsFeature(context);
        Store.enabled = false;
        Store.failRead = false;
        Store.failWrite = false;
        GboardRestartPendingChanges.onTargetRestarted();
    }

    @After public void cleanup() { GboardRestartPendingChanges.onTargetRestarted(); }

    private GboardPatchesSettingsContract.ToggleRow observe() {
        var row = GboardFeatureEntryState.masterToggle(feature.buildScreen(host));
        assertNotNull(row);
        assertTrue(row.isRestartRequired());
        assertEquals(GboardKeypressHapticsSettings.PREF_KEY_ENABLED, row.getRestartKey());
        GboardRestartPendingChanges.observe(row.getRestartKey(), Boolean.toString(row.isChecked()), "zh-Hant");
        return row;
    }

    @Test public void categoryAndDetailShareOnePendingRestartKeyAndRevertingCancels() {
        var row = observe();
        assertFalse(row.isChecked());
        assertEquals(0, GboardRestartPendingChanges.pendingCount());
        row.getToggleAction().accept(true);
        observe();
        assertEquals(1, GboardRestartPendingChanges.pendingCount());
        var category = GboardFeatureEntryState.inspect(feature, host).getMasterToggle();
        assertEquals(row.getRestartKey(), category.getRestartKey());
        GboardRestartPendingChanges.observe(category.getRestartKey(), "true", "zh-Hant");
        assertEquals(1, GboardRestartPendingChanges.pendingCount());
        category.getToggleAction().accept(false);
        observe();
        assertEquals(0, GboardRestartPendingChanges.pendingCount());
    }

    @Test public void disablingAfterSuccessfulRestartAlsoMarksOneChange() {
        observe().getToggleAction().accept(true);
        observe();
        GboardRestartPendingChanges.onTargetRestarted();
        observe().getToggleAction().accept(false);
        observe();
        assertEquals(1, GboardRestartPendingChanges.pendingCount());
    }

    @Test public void renderFailureProducesUnavailableScreenAndFailedWritePreservesSetting() {
        Store.failRead = true;
        var unavailable = feature.buildScreen(host);
        assertTrue(unavailable.getSections().isEmpty());
        assertEquals(1, unavailable.getStatusBlocks().size());
        Store.failRead = false;
        Store.failWrite = true;
        observe().getToggleAction().accept(true);
        assertFalse(observe().isChecked());
        assertEquals(1, refreshes.get());
        assertEquals(0, GboardRestartPendingChanges.pendingCount());
        assertTrue(feature.buildScreen(null).getSections().isEmpty());
    }

    @Implements(value = GboardPatchesSettings.class, isInAndroidSdk = false)
    public static class Store {
        static boolean enabled, failRead, failWrite;
        @Implementation public static Boolean readKeypressHapticsEnabled(Context context) {
            if (failRead) throw new IllegalStateException("settings unavailable");
            return enabled;
        }
        @Implementation public static boolean writeKeypressHapticsEnabled(Context context, boolean value) {
            if (failWrite) return false;
            enabled = value;
            return true;
        }
    }
}
