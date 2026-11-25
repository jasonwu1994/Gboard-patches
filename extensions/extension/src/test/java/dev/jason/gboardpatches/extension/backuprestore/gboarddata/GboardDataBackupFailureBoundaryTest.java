package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GboardDataBackupFailureBoundaryTest {
    @Test
    public void flagCallbackForcesOnlyTheBackupProviderBooleans() {
        assertEquals(Boolean.TRUE, GboardDataBackupFlagRuntime.applyFlagValue(
                GboardDataBackupFlagPolicy.HMM_BACKUP_FLAG, Boolean.FALSE));
        assertEquals(Boolean.TRUE, GboardDataBackupFlagRuntime.applyFlagValue(
                GboardDataBackupFlagPolicy.IMAGE_FILE_BACKUP_FLAG, Boolean.FALSE));
        assertEquals(Boolean.FALSE, GboardDataBackupFlagRuntime.applyFlagValue(
                "enable_unrelated_flag", Boolean.FALSE));
    }

    @Test
    public void flagCallbackKeepsTheComposedResultForUnexpectedInput() {
        Object stock = new Object();
        assertSame(stock, GboardDataBackupFlagRuntime.applyFlagValue(
                GboardDataBackupFlagPolicy.HMM_BACKUP_FLAG, stock));
        assertSame(stock, GboardDataBackupFlagRuntime.applyFlagValue(null, stock));
        assertNull(GboardDataBackupFlagRuntime.applyFlagValue(
                GboardDataBackupFlagPolicy.HMM_BACKUP_FLAG, null));
    }

    @Test
    public void unmatchedGboardBuildResolvesToNoBindingInsteadOfThrowing() {
        // The host test class path has none of Gboard's backup classes.
        assertNull(GboardDataBackupTargetBinding.resolveOrNull());
    }

    @Test
    public void gatewayReportsAnUnavailableFrameworkThroughItsCallback() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        GboardDataBackupGateway gateway = GboardDataBackupTargetBinding.gateway();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<Object> value = new AtomicReference<>();
        gateway.inspect(context, (result, failure) -> {
            value.set(result);
            error.set(failure);
            done.countDown();
        });
        long deadline = System.currentTimeMillis() + 5000;
        while (done.getCount() > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            done.await(20, TimeUnit.MILLISECONDS);
        }
        assertTrue("callback was not delivered", done.getCount() == 0);
        assertNull(value.get());
        assertNotNull(error.get());
    }
}
