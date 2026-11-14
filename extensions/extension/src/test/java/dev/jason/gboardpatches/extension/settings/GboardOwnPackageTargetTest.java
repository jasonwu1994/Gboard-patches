package dev.jason.gboardpatches.extension.settings;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

/** The patched app only serves and targets its own package, whatever that package is named. */
public final class GboardOwnPackageTargetTest {
    private static final String OFFICIAL = "com.google.android.inputmethod.latin";
    private static final String RENAMED = "dev.jason.com.google.android.inputmethod.latin";
    private static final String CUSTOM = "com.example.keyboard";

    @Test
    public void settingsTargetsAreOnlyTheOwnPackage() {
        Assert.assertEquals(Arrays.asList(CUSTOM),
                GboardPatchesSettingsActivity.ownTargetSettingsPackages(CUSTOM));
        Assert.assertEquals(Arrays.asList(OFFICIAL),
                GboardPatchesSettingsActivity.ownTargetSettingsPackages(OFFICIAL));
        Assert.assertEquals(Collections.emptyList(),
                GboardPatchesSettingsActivity.ownTargetSettingsPackages(null));
        Assert.assertEquals(Collections.emptyList(),
                GboardPatchesSettingsActivity.ownTargetSettingsPackages(""));
    }

    @Test
    public void runtimeSettingsAreOnlyServedToTheOwnPackage() {
        Assert.assertTrue(GboardPatchesSettingsProvider.isTopRowSwipeSettingsCallerAllowed(
                CUSTOM, CUSTOM));
        Assert.assertTrue(GboardPatchesSettingsProvider.isTopRowSwipeSettingsCallerAllowed(
                OFFICIAL, OFFICIAL));
        // Another installed copy of Gboard is a different app and is not served.
        Assert.assertFalse(GboardPatchesSettingsProvider.isTopRowSwipeSettingsCallerAllowed(
                CUSTOM, OFFICIAL));
        Assert.assertFalse(GboardPatchesSettingsProvider.isTopRowSwipeSettingsCallerAllowed(
                OFFICIAL, RENAMED));
        Assert.assertFalse(GboardPatchesSettingsProvider.isTopRowSwipeSettingsCallerAllowed(
                CUSTOM, "com.example.spy"));
        Assert.assertFalse(GboardPatchesSettingsProvider.isTopRowSwipeSettingsCallerAllowed(
                null, CUSTOM));
    }
}
