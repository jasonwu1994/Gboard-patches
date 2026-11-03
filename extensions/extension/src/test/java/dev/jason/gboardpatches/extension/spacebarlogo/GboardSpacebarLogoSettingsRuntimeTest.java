package dev.jason.gboardpatches.extension.spacebarlogo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class GboardSpacebarLogoSettingsRuntimeTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        preferences = GboardPatchesSettings.preferences(context);
        preferences.edit().clear().commit();
    }

    @Test public void imageReplacementPreservesSizeAndResetsOnlyTheColorMode() {
        assertEquals(24, GboardSpacebarLogoSettings.readSizeDp(preferences));
        assertTrue(GboardSpacebarLogoSettings.writeImage(context, GboardSpacebarLogoImageData.base64()));
        assertTrue(GboardSpacebarLogoSettings.writeEnabled(context, true));
        assertTrue(GboardSpacebarLogoSettings.writeSizeDp(context, 30));
        assertTrue(GboardSpacebarLogoSettings.writeColorHex(context, "AUTO"));
        assertTrue(GboardSpacebarLogoSettings.writeImageTint(context, true));
        var snapshot = GboardSpacebarLogoSettings.snapshot(context);
        assertTrue(snapshot.enabled);
        assertTrue(snapshot.shouldTint());
        assertEquals(30, snapshot.sizeDp);
        assertEquals(GboardSpacebarLogoImageData.base64(), snapshot.customImage);
        assertTrue(GboardSpacebarLogoSettings.writeImage(context, snapshot.customImage));
        snapshot = GboardSpacebarLogoSettings.snapshot(context);
        assertFalse(snapshot.shouldTint());
        assertEquals(30, snapshot.sizeDp);
        assertFalse(GboardSpacebarLogoSettings.writeSizeDp(context, 41));
        assertTrue(GboardSpacebarLogoSettings.removeImage(context));
        assertEquals("", GboardSpacebarLogoSettings.readImage(preferences));
        assertEquals(GboardSpacebarLogoContract.DEFAULT_ICON, GboardSpacebarLogoSettings.readIcon(preferences));
    }

    @Test public void lockedSettingsReturnStockBehaviorAndDoNotOverwriteTheImage() {
        GboardSpacebarLogoSettings.writeImage(context, GboardSpacebarLogoImageData.base64());
        GboardSpacebarLogoSettings.writeEnabled(context, true);
        UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
        Shadows.shadowOf(users).setUserUnlocked(false);
        try {
            assertFalse(GboardSpacebarLogoSettings.snapshot(context).enabled);
            assertFalse(GboardSpacebarLogoSettings.writeImage(context, "replacement"));
        } finally { Shadows.shadowOf(users).setUserUnlocked(true); }
        assertEquals(GboardSpacebarLogoImageData.base64(), GboardSpacebarLogoSettings.readImage(preferences));
    }

    @Test public void screenUsesFiveSizeOptionsAndPreservesOriginalColorControls() {
        Object[][] shown = {null};
        GboardPatchesSettingsContract.Host host = (GboardPatchesSettingsContract.Host) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{GboardPatchesSettingsContract.Host.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getContext")) return context;
                    if (method.getName().equals("submit")) {
                        ((GboardPatchesSettingsContract.Intent) args[0]).apply((GboardPatchesSettingsContract.Host) proxy);
                    } else if (method.getName().equals("showChoiceDialog")) shown[0] = args;
                    return null;
                });
        var feature = new GboardSpacebarLogoSettingsFeature(context);
        GboardSpacebarLogoSettings.writeIcon(context, GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR);
        var screen = feature.buildScreen(host);
        assertFalse(screen.getSections().get(0).getItems().get(2).isEnabled());
        var size = (GboardPatchesSettingsContract.SelectorRow) screen.getSections().get(0).getItems().get(4);
        assertEquals("24 dp", size.getCurrentValue());
        size.getAction().run();
        assertArrayEquals(new String[]{"12", "24", "30", "40", "CUSTOM"}, (String[]) shown[0][2]);
        ((GboardPatchesSettingsContract.StringValueConsumer) shown[0][6]).accept("30");
        assertEquals(30, GboardSpacebarLogoSettings.readSizeDp(preferences));
        GboardSpacebarLogoSettings.writeImage(context, GboardSpacebarLogoImageData.base64());
        screen = feature.buildScreen(host);
        assertEquals(7, screen.getSections().get(0).getItems().size());
        assertFalse(screen.getSections().get(0).getItems().get(2).isEnabled());
        ((GboardPatchesSettingsContract.ToggleRow) screen.getSections().get(0).getItems().get(4))
                .getToggleAction().accept(true);
        assertTrue(feature.buildScreen(host).getSections().get(0).getItems().get(2).isEnabled());
    }
}
