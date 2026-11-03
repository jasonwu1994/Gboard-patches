package dev.jason.gboardpatches.extension.spacebarlogo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class GboardSpacebarFailureBoundaryTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void aLateImportRefreshCannotRevertANewerSize() throws Exception {
        SharedPreferences preferences = GboardPatchesSettings.preferences(context);
        preferences.edit().clear().putBoolean(GboardSpacebarLogoSettings.PREF_KEY_ENABLED, true)
                .putString(GboardSpacebarLogoSettings.PREF_KEY_IMAGE, GboardSpacebarLogoImageData.base64())
                .putString(GboardSpacebarLogoSettings.PREF_KEY_ICON, GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE)
                .putInt(GboardSpacebarLogoSettings.PREF_KEY_SIZE_DP, 12).commit();
        List<Runnable> lateWorkerPosts = new ArrayList<>();
        Handler main = new Handler(Looper.getMainLooper());
        FrameLayout root = new FrameLayout(context) {
            @Override public boolean post(Runnable action) {
                if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
                    lateWorkerPosts.add(action);
                    return true;
                }
                return main.post(action);
            }
        };
        int spaceId = View.generateViewId(), labelId = View.generateViewId();
        root.setId(spaceId);
        TextView label = new TextView(context); label.setId(labelId); root.addView(label);
        var bindings = new GboardSpacebarLogoRuntime.ViewBindings(spaceId, labelId, 0, 0, 0);
        GboardSpacebarLogoRuntime.afterSoftKeyBound(root, true, GboardSpacebarLogoSettings.snapshot(context), bindings);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread importer = new Thread(() -> {
            try { assertTrue(GboardSpacebarLogoSettings.writeImage(context, GboardSpacebarLogoImageData.base64())); }
            catch (Throwable error) { failure.set(error); }
        }, "spacebar-import-test");
        importer.start(); importer.join(5000);
        assertFalse(importer.isAlive());
        assertNull(failure.get());
        assertTrue(GboardSpacebarLogoSettings.writeSizeDp(context, 40));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        for (Runnable action : lateWorkerPosts) main.post(action);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(40, GboardSpacebarLogoSettings.readSizeDp(preferences));
        assertEquals("The final view must match the newest persisted size",
                Math.round(40 * context.getResources().getDisplayMetrics().density),
                root.getChildAt(1).getLayoutParams().width);
    }

    @Test public void runtimeReadsDoNotWaitForImageDiskPersistence() throws Exception {
        SharedPreferences actual = context.getSharedPreferences("spacebar-slow-save", Context.MODE_PRIVATE);
        actual.edit().clear().putBoolean(GboardSpacebarLogoSettings.PREF_KEY_ENABLED, false).commit();
        CountDownLatch committing = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch snapshotRead = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SharedPreferences slow = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(actual, args);
                    SharedPreferences.Editor editor = actual.edit();
                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (editorProxy, editMethod, editArgs) -> {
                                if (editMethod.getName().equals("commit")) {
                                    committing.countDown();
                                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Commit gate timeout");
                                }
                                Object result = editMethod.invoke(editor, editArgs);
                                return editMethod.getReturnType() == SharedPreferences.Editor.class ? editorProxy : result;
                            });
                });
        Thread writer = new Thread(() -> {
            try { assertTrue(GboardSpacebarLogoSettings.writeImage(slow, "image")); }
            catch (Throwable error) { failure.set(error); }
        });
        Thread reader = new Thread(() -> {
            try { assertFalse(GboardSpacebarLogoSettings.snapshotFromPreferences(slow).enabled); }
            catch (Throwable error) { failure.set(error); }
            finally { snapshotRead.countDown(); }
        });
        try {
            writer.start();
            assertTrue(committing.await(3, TimeUnit.SECONDS));
            reader.start();
            assertTrue("Runtime binds must not wait for a background disk transaction",
                    snapshotRead.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            writer.join(5000); reader.join(5000);
        }
        assertNull(failure.get());
    }

    @Test public void aFailedCommitRestoresOnlyThePreviousImageFields() {
        assertRejectedReplacementPreservesState(false, false);
    }

    @Test public void aThrowingCommitRestoresOnlyThePreviousImageFields() {
        assertRejectedReplacementPreservesState(true, false);
    }

    @Test public void aFailedFirstImportPreservesAbsentKeys() {
        assertRejectedReplacementPreservesState(false, true);
    }

    @Test public void aFailedCommitPreservesUnexpectedStoredTypes() {
        assertRejectedReplacementPreservesState(false, false, true, false);
    }

    @Test public void aThrowingRollbackDoesNotEscape() {
        assertRejectedReplacementPreservesState(false, false, false, true);
    }

    private void assertRejectedReplacementPreservesState(boolean throwsAfterApply, boolean absentImage) {
        assertRejectedReplacementPreservesState(throwsAfterApply, absentImage, false, false);
    }

    private void assertRejectedReplacementPreservesState(boolean throwsAfterApply, boolean absentImage,
            boolean unexpectedTypes, boolean throwingRollback) {
        SharedPreferences actual = context.getSharedPreferences("spacebar-failed-save", Context.MODE_PRIVATE);
        actual.edit().clear().putInt(GboardSpacebarLogoSettings.PREF_KEY_SIZE_DP, 30).commit();
        if (!absentImage) actual.edit().putString(GboardSpacebarLogoSettings.PREF_KEY_ICON, "HEART")
                .putString(GboardSpacebarLogoSettings.PREF_KEY_IMAGE, "old-image")
                .putBoolean(GboardSpacebarLogoSettings.PREF_KEY_IMAGE_TINT, true).commit();
        if (unexpectedTypes) actual.edit().putStringSet(GboardSpacebarLogoSettings.PREF_KEY_IMAGE,
                java.util.Set.of("preserved", "values"))
                .putLong(GboardSpacebarLogoSettings.PREF_KEY_IMAGE_TINT, 7L)
                .putFloat(GboardSpacebarLogoSettings.PREF_KEY_ICON, 2.5f).commit();
        Map<String, ?> before = new HashMap<>(actual.getAll());
        AtomicBoolean failOnce = new AtomicBoolean(true);
        SharedPreferences faulty = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(actual, args);
                    SharedPreferences.Editor editor = actual.edit();
                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (editorProxy, editMethod, editArgs) -> {
                                if (throwingRollback && !failOnce.get() && editMethod.getName().equals("commit")) {
                                    throw new IllegalStateException("Rollback storage unavailable");
                                }
                                Object result;
                                try { result = editMethod.invoke(editor, editArgs); }
                                catch (InvocationTargetException error) { throw error.getCause(); }
                                if (editMethod.getName().equals("commit") && failOnce.getAndSet(false)) {
                                    actual.edit().putBoolean("unrelated_feature", true).commit();
                                    if (throwsAfterApply) throw new IllegalStateException("Disk write failed after memory apply");
                                    return false;
                                }
                                return editMethod.getReturnType() == SharedPreferences.Editor.class ? editorProxy : result;
                            });
                });
        assertFalse(GboardSpacebarLogoSettings.writeImage(faulty, "replacement-image"));
        Map<String, Object> expected = new HashMap<>(before);
        expected.put("unrelated_feature", true);
        if (throwingRollback) {
            assertTrue(actual.getBoolean("unrelated_feature", false));
        } else assertEquals(expected, actual.getAll());
    }
}
