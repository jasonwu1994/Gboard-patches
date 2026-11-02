package dev.jason.gboardpatches.extension.websearch;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class GboardFloatingWebSearchAccessPointTest {
    private static final int GOOGLE_ICON = 0x7f080467;
    private static final GboardFloatingWebSearchAccessPointContribution CONTRIBUTION =
            GboardFloatingWebSearchAccessPointContribution.INSTANCE;
    private TrackingContext context;
    private SharedPreferences preferences;

    @Before public void setup() throws Exception {
        context = new TrackingContext(RuntimeEnvironment.getApplication());
        preferences = GboardFloatingWebSearchSettingsStore.preferences(context);
        preferences.edit().clear().commit();
        Class.forName("mtr").getField("testFactories").setInt(null, 0);
    }

    @Test public void enabledCatalogUses1853ImmutableSetAndPreservesStockOrder() throws Exception {
        Object stock = catalog("clipboard", "translate");
        Object actual = CONTRIBUTION.extendOrderCatalog(context, stock);
        assertTrue(Class.forName("wpa").isInstance(actual));
        assertEquals(Arrays.asList("clipboard", "translate", "web_search"), list(actual));
        assertEquals(Arrays.asList("clipboard", "translate"), list(stock));
        try {
            ((Collection<?>) actual).clear();
            fail("Native master catalog must remain immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void existingTokenIsNotDuplicatedOrMovedAndCatalogIsIdempotent() throws Exception {
        Object stock = catalog("translate", "web_search", "clipboard");
        Object actual = CONTRIBUTION.extendOrderCatalog(context, stock);
        assertSame(stock, actual);
        assertSame(actual, CONTRIBUTION.extendOrderCatalog(context, actual));
        assertEquals(Arrays.asList("translate", "web_search", "clipboard"), list(actual));
    }

    @Test public void disabledMalformedOrMissingContextPreservesTheExactStockCarrier() throws Exception {
        Object stock = catalog("clipboard");
        preferences.edit().putBoolean(GboardFloatingWebSearchSettings.PREF_KEY_ENABLED, false).commit();
        assertSame(stock, CONTRIBUTION.extendOrderCatalog(context, stock));
        preferences.edit().putInt(GboardFloatingWebSearchSettings.PREF_KEY_ENABLED, 1).commit();
        assertSame(stock, CONTRIBUTION.extendOrderCatalog(context, stock));
        assertSame(stock, CONTRIBUTION.extendOrderCatalog(null, stock));
    }

    @Test public void wrongCatalogCarriersFailClosedWithoutReinterpretingListsOrMutableSets() {
        Object list = new ArrayList<>(Arrays.asList("clipboard", "translate"));
        Object set = new LinkedHashSet<>(Arrays.asList("clipboard", "translate"));
        assertSame(list, CONTRIBUTION.extendOrderCatalog(context, list));
        assertSame(set, CONTRIBUTION.extendOrderCatalog(context, set));
        assertNull(CONTRIBUTION.extendOrderCatalog(context, null));
    }

    @Test public void registrationBuildsThe1853DescriptorWithoutWritingStringIntoTheActionMap() throws Exception {
        Object builder = Class.forName("mtm").getConstructor().newInstance();
        assertEquals(String.class, Class.forName("mtm").getField("d").getType());
        assertEquals(Class.forName("wnz"), Class.forName("mtm").getField("e").getType());
        assertNull(Class.forName("mtm").getField("e").get(builder));
        Object controller = controller();
        CONTRIBUTION.register(controller, context);
        Object descriptor = descriptor(controller);
        assertNotNull("Exact1853 AP must actually be registered", descriptor);
        assertEquals("web_search", field(descriptor, "b"));
        assertEquals(GOOGLE_ICON, ((Number) field(descriptor, "c")).intValue());
        assertEquals("Web Search", field(descriptor, "f"));
        assertTrue(Class.forName("wnz").isInstance(field(descriptor, "j")));
        assertNotNull(callback(descriptor));
        assertFalse((Boolean) field(controller, "testLastBoolean"));
        assertEquals(1, ((Number) field(controller, "testRegistrations")).intValue());
    }

    @Test public void registrationPreservesSavedOrderingAndReplacesTheSameTokenOnRepeat() throws Exception {
        Object controller = controller();
        @SuppressWarnings("unchecked") List<String> saved = (List<String>) field(controller, "testSavedOrder");
        saved.addAll(Arrays.asList("translate", "web_search", "clipboard"));
        CONTRIBUTION.register(controller, context);
        CONTRIBUTION.register(controller, context);
        assertNotNull(descriptor(controller));
        assertEquals(1, ((Map<?, ?>) field(controller, "testDescriptors")).size());
        assertEquals(Arrays.asList("translate", "web_search", "clipboard"), saved);
    }

    @Test public void disabledMalformedAndWrongControllerProduceNoSyntheticDescriptor() throws Exception {
        Object controller = controller();
        preferences.edit().putBoolean(GboardFloatingWebSearchSettings.PREF_KEY_ENABLED, false).commit();
        CONTRIBUTION.register(controller, context);
        preferences.edit().putInt(GboardFloatingWebSearchSettings.PREF_KEY_ENABLED, 1).commit();
        CONTRIBUTION.register(controller, context);
        preferences.edit().putBoolean(GboardFloatingWebSearchSettings.PREF_KEY_ENABLED, true).commit();
        CONTRIBUTION.register(new Object(), context);
        CONTRIBUTION.register(controller, null);
        CONTRIBUTION.register(null, context);
        assertNull(descriptor(controller));
        assertEquals(0, Class.forName("mtr").getField("testFactories").getInt(null));
    }

    @Test public void missingWrongTypeOrWrongIdentityIconFailsBeforeDescriptorConstruction() throws Exception {
        for (String mode : Arrays.asList("missing", "wrong-type", "wrong-identity")) {
            Object controller = controller();
            context.resources.mode = mode;
            CONTRIBUTION.register(controller, context);
            assertNull(mode, descriptor(controller));
        }
        assertEquals(0, Class.forName("mtr").getField("testFactories").getInt(null));
    }

    @Test public void nativeRegistrationFailureDoesNotEscapeIntoTheKeyboard() throws Exception {
        Object controller = controller();
        controller.getClass().getField("testFailRegistration").setBoolean(controller, true);
        CONTRIBUTION.register(controller, context);
        assertNull(descriptor(controller));
    }

    @Test public void capturedRunnableUsesTheSafeContextAndCurrentConfiguredUrl() throws Exception {
        Object controller = controller();
        CONTRIBUTION.register(controller, context);
        Runnable callback = callback(descriptor(controller));
        String url = "https://example.com/gboard-sandbox";
        preferences.edit().putString(GboardFloatingWebSearchSettings.PREF_KEY_START_PAGE_MODE, "custom")
                .putString(GboardFloatingWebSearchSettings.PREF_KEY_CUSTOM_START_PAGE_URL, url).commit();
        allowBrowser(url);
        callback.run();
        assertEquals(1, context.started.size());
        assertEquals(Intent.ACTION_VIEW, context.started.get(0).getAction());
        assertEquals(url, context.started.get(0).getDataString());
        assertTrue(context.started.get(0).hasCategory(Intent.CATEGORY_BROWSABLE));
    }

    @Test public void previouslyRegisteredRunnableDoesNotLaunchAfterDisableOrWithoutABrowser() throws Exception {
        Object controller = controller();
        CONTRIBUTION.register(controller, context);
        Runnable callback = callback(descriptor(controller));
        callback.run();
        assertTrue(context.started.isEmpty());
        allowBrowser(GboardFloatingWebSearchSettings.GOOGLE_URL);
        preferences.edit().putBoolean(GboardFloatingWebSearchSettings.PREF_KEY_ENABLED, false).commit();
        callback.run();
        assertTrue(context.started.isEmpty());
    }

    private void allowBrowser(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.name = "SandboxBrowser";
        info.activityInfo.packageName = "test.sandbox.browser";
        Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(intent, info);
    }

    private static Object catalog(String... values) throws Exception {
        return Class.forName("wpa").getDeclaredMethod("n", Collection.class)
                .invoke(null, Arrays.asList(values));
    }
    private static Object controller() throws Exception { return Class.forName("mwv").getConstructor().newInstance(); }
    private static List<?> list(Object value) { return new ArrayList<>((Collection<?>) value); }
    private static Object field(Object owner, String name) throws Exception { return owner.getClass().getField(name).get(owner); }
    private static Object descriptor(Object controller) throws Exception { return ((Map<?, ?>) field(controller, "testDescriptors")).get("web_search"); }
    private static Runnable callback(Object descriptor) throws Exception {
        assertNotNull("Register the descriptor before exercising its real runnable", descriptor);
        Object action = ((Map<?, ?>) field(descriptor, "j")).get("PRESS");
        assertEquals(-0x9c47, ((Number) field(action, "code")).intValue());
        return (Runnable) field(action, "payload");
    }

    private static final class TrackingContext extends ContextWrapper {
        final TargetResources resources;
        final List<Intent> started = new ArrayList<>();
        TrackingContext(Context base) { super(base); resources = new TargetResources(base.getResources()); }
        @Override public Context getApplicationContext() { return this; }
        @Override public Resources getResources() { return resources; }
        @Override public void startActivity(Intent intent) { started.add(new Intent(intent)); }
        @Override public boolean bindService(Intent intent, ServiceConnection connection, int flags) { return false; }
    }

    private static final class TargetResources extends Resources {
        String mode = "valid";
        TargetResources(Resources base) { super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration()); }
        @Override public String getResourceTypeName(int id) {
            requireGoogle(id);
            return "wrong-type".equals(mode) ? "string" : "drawable";
        }
        @Override public String getResourceEntryName(int id) {
            requireGoogle(id);
            return "wrong-identity".equals(mode) ? "unrelated_icon" : "drawable_0x7f080467";
        }
        private void requireGoogle(int id) {
            if (id != GOOGLE_ICON || "missing".equals(mode)) throw new NotFoundException("Not the1853 Google drawable");
        }
    }
}
