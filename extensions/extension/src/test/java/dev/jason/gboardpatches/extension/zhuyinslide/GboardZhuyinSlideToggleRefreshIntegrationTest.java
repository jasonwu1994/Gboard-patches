package dev.jason.gboardpatches.extension.zhuyinslide;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import dev.jason.gboardpatches.extension.zhuyintoggle
        .GboardZhuyinTraditionalSimplifiedToggleRuntime;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardZhuyinSlideToggleRefreshIntegrationTest {
    private static final int TOGGLE_KEY_ID = 0x7f0b28c5;

    private Context context;

    @Before
    public void resetState() throws Exception {
        context = RuntimeEnvironment.getApplication();
        GboardZhuyinSlideTargetFixture.resetPreferences();
        Field visibleKeys = GboardZhuyinTraditionalSimplifiedToggleRuntime.class
                .getDeclaredField("VISIBLE_TOGGLE_KEYS");
        visibleKeys.setAccessible(true);
        ((Map<?, ?>) visibleKeys.get(null)).clear();
    }

    @Test
    public void toggleRefreshRebindKeepsSlideIdentityForPointerOwnership() throws Exception {
        Object view = GboardZhuyinSlideTargetFixture.newSoftKeyView(context);
        Object base = GboardZhuyinSlideTargetFixture.metadata(
                TOGGLE_KEY_ID, "ㄥ", "ㄥ", new String[] {"ㄥ", "？"}, null, null);

        GboardZhuyinSlideTargetFixture.bind(view, base, 42L);
        Object initiallyBound = GboardZhuyinSlideTargetFixture.boundMetadata(view);
        assertTrue(GboardZhuyinSlideRuntime.isPatchedMetadata(initiallyBound));
        GboardZhuyinTraditionalSimplifiedToggleRuntime.afterSoftKeyBound(view);

        assertTrue(GboardZhuyinTraditionalSimplifiedToggleRuntime.maybeToggle(
                GboardZhuyinSlideTargetFixture.newDispatcher(context),
                GboardZhuyinSlideTargetFixture.action("SLIDE_UP"),
                initiallyBound));

        Object rebound = GboardZhuyinSlideTargetFixture.boundMetadata(view);
        assertNotSame(initiallyBound, rebound);
        assertTrue(GboardZhuyinSlideRuntime.isPatchedMetadata(rebound));

        Object tracker = GboardZhuyinSlideTargetFixture.newPointerTracker();
        GboardZhuyinSlideTargetFixture.setActions(tracker, "PRESS", "PRESS");
        assertFalse(GboardZhuyinSlideRuntime.maybeCaptureAndShouldSuppressRetarget(
                tracker, view, 1.0f, 1.0f));
        GboardZhuyinSlideTargetFixture.setCurrentOwner(tracker, view);

        Object crossedView = GboardZhuyinSlideTargetFixture.newSoftKeyView(context);
        Object crossedBase = GboardZhuyinSlideTargetFixture.metadata(
                0x7f0b2801, "ㄝ", "ㄝ", new String[] {"ㄝ", "……"}, null, null);
        GboardZhuyinSlideTargetFixture.bind(crossedView, crossedBase, 43L);
        GboardZhuyinSlideTargetFixture.setActions(tracker, "PRESS", "SLIDE_DOWN");
        assertTrue(GboardZhuyinSlideRuntime.maybeCaptureAndShouldSuppressRetarget(
                tracker, crossedView, 2.0f, 3.0f));

        GboardZhuyinSlideRuntime.clearPointerState(tracker);
        assertFalse(GboardZhuyinSlideRuntime.maybeCaptureAndShouldSuppressRetarget(
                tracker, crossedView, 2.0f, 3.0f));
    }
}
