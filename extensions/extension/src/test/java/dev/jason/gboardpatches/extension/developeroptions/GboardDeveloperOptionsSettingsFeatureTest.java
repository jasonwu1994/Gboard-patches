package dev.jason.gboardpatches.extension.developeroptions;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardFeatureGroup;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;

public final class GboardDeveloperOptionsSettingsFeatureTest {
    @Test
    public void groupedEntryRequestsOfficialHeaderWithoutOpeningIntermediateScreen() {
        GboardDeveloperOptionsSettingsFeature developer =
                new GboardDeveloperOptionsSettingsFeature(
                        "Developer options", "Open Developer options and the Flag Editor.");
        GboardPatchesSettingsContract.Feature visibleDeveloper =
                (GboardPatchesSettingsContract.Feature) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] { GboardPatchesSettingsContract.Feature.class },
                        (proxy, method, args) -> "isAvailable".equals(method.getName())
                                ? true : method.invoke(developer, args));
        AtomicInteger openedHeader = new AtomicInteger();
        GboardPatchesSettingsTestHost host = new GboardPatchesSettingsTestHost() {
            @Override
            public void openTargetSettingsHeader(int headerKeyResourceId) {
                openedHeader.set(headerKeyResourceId);
            }
        };
        GboardFeatureGroup group = new GboardFeatureGroup(
                "More settings", "", "Gboard", "", "", "", List.of(visibleDeveloper));
        GboardPatchesSettingsContract.NavigationRow row =
                (GboardPatchesSettingsContract.NavigationRow)
                        group.buildScreen(host).getRows().get(0);

        row.getAction().run();

        Assert.assertEquals(0x7f140a6d, openedHeader.get());
        Assert.assertNull(host.getOpenedFeature());
    }

    @Test
    public void rootEntryIsDirectAndRequestsOnlyThe1853HeaderId() throws Exception {
        Path sourcePath = Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/developeroptions/"
                        + "GboardDeveloperOptionsSettingsFeature.java");
        Assert.assertTrue("Developer options settings feature missing",
                Files.isRegularFile(sourcePath));

        Class<?> featureClass = Class.forName(
                "dev.jason.gboardpatches.extension.developeroptions."
                        + "GboardDeveloperOptionsSettingsFeature");
        Constructor<?> constructor = featureClass.getDeclaredConstructor(
                String.class, String.class);
        constructor.setAccessible(true);
        Object feature = constructor.newInstance(
                "Developer options", "Open Developer options and the Flag Editor.");

        AtomicInteger openedHeader = new AtomicInteger();
        AtomicBoolean openedIntermediateFeature = new AtomicBoolean(false);
        GboardPatchesSettingsTestHost host = new GboardPatchesSettingsTestHost() {
            @Override
            public void openTargetSettingsHeader(int headerKeyResourceId) {
                openedHeader.set(headerKeyResourceId);
            }

            @Override
            public void openFeature(GboardPatchesSettingsContract.Feature openedFeature) {
                openedIntermediateFeature.set(true);
            }
        };

        Method openRootEntry = featureClass.getMethod(
                "openRootEntry", GboardPatchesSettingsContract.FeatureHost.class);
        openRootEntry.invoke(feature, host);

        Assert.assertEquals(0x7f140a6d, openedHeader.get());
        Assert.assertFalse(openedIntermediateFeature.get());
    }

    @Test
    public void sourceContainsNoLegacyFragmentOrVersionFallback() throws Exception {
        String featureSource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/developeroptions/"
                        + "GboardDeveloperOptionsSettingsFeature.java");
        String activitySource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");

        Assert.assertFalse(featureSource.contains("DeveloperSettingsFragment"));
        Assert.assertFalse(featureSource.contains(":android:show_fragment"));
        Assert.assertFalse(activitySource.contains("DeveloperSettingsFragment"));
        Assert.assertFalse(activitySource.contains(":android:show_fragment"));
        Assert.assertFalse(activitySource.contains("17.0.10"));
        Assert.assertFalse(activitySource.contains("versionName.startsWith("));
        Assert.assertTrue(activitySource.contains(
                "18.5.4.993608717-release-arm64-v8a"));
        Assert.assertTrue(activitySource.contains(
                "showUnsupportedDeveloperOptionsPackageDialog(currentPackage"));
        Assert.assertTrue(activitySource.contains(
                "tryLaunchTargetSettingsHeader(packageName, headerKeyResourceId, true)"));
    }

    @Test
    public void versionSupportRequiresExactFull1853ReleaseName() throws Exception {
        String activityClassName =
                "dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity";
        Method versionCheck = Class.forName(activityClassName).getDeclaredMethod(
                "isSupportedDeveloperOptionsTargetVersion", String.class);
        versionCheck.setAccessible(true);

        Assert.assertEquals(Boolean.TRUE, versionCheck.invoke(
                null, "18.5.4.993608717-release-arm64-v8a"));
        Assert.assertEquals(Boolean.FALSE, versionCheck.invoke(null, "18.5.4"));
        Assert.assertEquals(Boolean.FALSE, versionCheck.invoke(null, "18.0.3.954559732-release-arm64-v8a"));
        Assert.assertEquals(Boolean.FALSE, versionCheck.invoke(
                null, "18.5.4.993608717-release-arm64-v8a-debug"));
        Assert.assertEquals(Boolean.FALSE, versionCheck.invoke(
                null, "18.0.3.932364120-release-arm64-v8a"));
        Assert.assertEquals(Boolean.FALSE, versionCheck.invoke(null, new Object[] { null }));
    }

    private static String readSource(String path) throws Exception {
        Path sourcePath = Path.of(path);
        Assert.assertTrue("Missing source: " + path, Files.isRegularFile(sourcePath));
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }

}
