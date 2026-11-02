package dev.jason.gboardpatches.extension.settings;

import static org.junit.Assert.*;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class GboardSettingsNavigationIntentTest {
    @Test public void consumedTilePathCannotBeReplayedByLocaleRecreation() throws Exception {
        Intent intent = new Intent().putStringArrayListExtra(
                GboardPatchesSettingsActivity.EXTRA_NAVIGATION_PATH,
                new ArrayList<>(List.of("unavailable-feature")));
        var consume = GboardPatchesSettingsActivity.class.getDeclaredMethod("restoreNavigationPathFromIntent", Intent.class);
        consume.setAccessible(true);
        var first = Robolectric.buildActivity(GboardPatchesSettingsActivity.class).get();
        consume.invoke(first, intent);
        assertFalse(intent.hasExtra(GboardPatchesSettingsActivity.EXTRA_NAVIGATION_PATH));
        var recreated = Robolectric.buildActivity(GboardPatchesSettingsActivity.class).get();
        assertEquals(false, consume.invoke(recreated, intent));
    }
}
