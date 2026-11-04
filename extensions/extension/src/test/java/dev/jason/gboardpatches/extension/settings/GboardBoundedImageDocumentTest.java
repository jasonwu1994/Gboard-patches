package dev.jason.gboardpatches.extension.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.io.InputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract.BinaryDocumentConsumer;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GboardBoundedImageDocumentTest {
    @Test public void slowDocumentReadDoesNotDelayExistingSettingsWrites() throws Exception {
        var activity = Robolectric.buildActivity(GboardPatchesSettingsActivity.class).get();
        Uri uri = Uri.parse("content://spacebar-test/slow.png");
        CountDownLatch reading = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch settingsWritten = new CountDownLatch(1), imported = new CountDownLatch(1);
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                reading.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Read gate timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); return -1; }
                return super.read(bytes, offset, length);
            }
        };
        Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri, stream);
        try {
            ReflectionHelpers.setField(activity, "pendingBinaryDocumentReader",
                    (BinaryDocumentConsumer) document -> imported.countDown());
            ReflectionHelpers.setField(activity, "pendingBinaryDocumentLimit", 10);
            Method result = GboardPatchesSettingsActivity.class.getDeclaredMethod(
                    "handleOpenBinaryDocumentResult", int.class, Intent.class);
            result.setAccessible(true);
            result.invoke(activity, Activity.RESULT_OK, new Intent().setData(uri));
            assertTrue(reading.await(3, TimeUnit.SECONDS));
            ((ExecutorService) ReflectionHelpers.getField(activity, "backgroundStateExecutor"))
                    .execute(settingsWritten::countDown);
            assertTrue("An image provider must not block unrelated settings writes",
                    settingsWritten.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            imported.await(3, TimeUnit.SECONDS);
            stopWorkers(activity);
        }
    }

    @Test public void rejectedDocumentWorkDoesNotEscapeTheActivityCallback() throws Exception {
        var activity = Robolectric.buildActivity(GboardPatchesSettingsActivity.class).get();
        stopWorkers(activity);
        ReflectionHelpers.setField(activity, "pendingBinaryDocumentReader", (BinaryDocumentConsumer) document -> fail());
        ReflectionHelpers.setField(activity, "pendingBinaryDocumentLimit", 10);
        Method result = GboardPatchesSettingsActivity.class.getDeclaredMethod(
                "handleOpenBinaryDocumentResult", int.class, Intent.class);
        result.setAccessible(true);
        result.invoke(activity, Activity.RESULT_OK, new Intent().setData(Uri.parse("content://spacebar-test/late.png")));
    }

    private static void stopWorkers(GboardPatchesSettingsActivity activity) throws Exception {
        for (java.lang.reflect.Field field : GboardPatchesSettingsActivity.class.getDeclaredFields()) {
            if (ExecutorService.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                ((ExecutorService) field.get(activity)).shutdownNow();
            }
        }
    }

    @Test public void boundedReaderStopsAtLimitBeforeHandingOverTheImage() throws Exception {
        var activity = Robolectric.buildActivity(GboardPatchesSettingsActivity.class).get();
        Uri uri = Uri.parse("content://spacebar-test/too-large.png");
        Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,
                new ByteArrayInputStream(new byte[11]));
        Method read = GboardPatchesSettingsActivity.class.getDeclaredMethod("readBinaryDocument", Uri.class, int.class);
        read.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> read.invoke(activity, uri, 10));
        assertTrue(failure.getCause() instanceof java.io.IOException);
        stopWorkers(activity);
    }

    @Test public void boundedImportRunsOffTheUiThreadAndCancelDoesNotCallConsumer() throws Exception {
        var activity = Robolectric.buildActivity(GboardPatchesSettingsActivity.class).get();
        Uri uri = Uri.parse("content://spacebar-test/icon.png");
        Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
        CountDownLatch imported = new CountDownLatch(1);
        AtomicBoolean uiThread = new AtomicBoolean(true);
        BinaryDocumentConsumer consumer = document -> {
            uiThread.set(Looper.myLooper() == Looper.getMainLooper());
            assertEquals(3, document.getData().length);
            imported.countDown();
        };
        Method result = GboardPatchesSettingsActivity.class.getDeclaredMethod(
                "handleOpenBinaryDocumentResult", int.class, Intent.class);
        result.setAccessible(true);
        try {
            ReflectionHelpers.setField(activity, "pendingBinaryDocumentReader", consumer);
            ReflectionHelpers.setField(activity, "pendingBinaryDocumentLimit", 10);
            result.invoke(activity, Activity.RESULT_CANCELED, null);
            assertEquals(1, imported.getCount());
            ReflectionHelpers.setField(activity, "pendingBinaryDocumentReader", consumer);
            ReflectionHelpers.setField(activity, "pendingBinaryDocumentLimit", 10);
            result.invoke(activity, Activity.RESULT_OK, new Intent().setData(uri));
            assertTrue(imported.await(5, TimeUnit.SECONDS));
            assertFalse(uiThread.get());
        } finally { stopWorkers(activity); }
    }
}
