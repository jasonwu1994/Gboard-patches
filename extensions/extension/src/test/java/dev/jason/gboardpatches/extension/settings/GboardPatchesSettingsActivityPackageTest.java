package dev.jason.gboardpatches.extension.settings;

import org.junit.Assert;
import org.junit.Test;

public final class GboardPatchesSettingsActivityPackageTest {
    @Test
    public void ownConfiguredPackageIsAlwaysSupported() {
        Assert.assertTrue(GboardPatchesSettingsActivity.isSupportedTargetPackage(
                "com.example.keyboard", "com.example.keyboard"));
        Assert.assertTrue(GboardPatchesSettingsActivity.isSupportedTargetPackage(
                "com.touchtype.swiftkey", "com.touchtype.swiftkey"));
    }

    @Test
    public void officialPackageRemainsSupportedAndUnknownPackagesAreNot() {
        Assert.assertTrue(GboardPatchesSettingsActivity.isSupportedTargetPackage(
                "com.google.android.inputmethod.latin", "com.example.keyboard"));
        Assert.assertFalse(GboardPatchesSettingsActivity.isSupportedTargetPackage(
                "com.other.app", "com.example.keyboard"));
        Assert.assertFalse(GboardPatchesSettingsActivity.isSupportedTargetPackage(
                null, "com.example.keyboard"));
    }
}
