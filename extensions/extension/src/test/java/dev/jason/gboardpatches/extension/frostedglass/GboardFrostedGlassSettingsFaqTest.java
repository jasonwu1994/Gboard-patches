package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;

import android.content.Context;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardFrostedGlassSettingsFaqTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final Host host = new Host(context);

    private GboardPatchesSettingsContract.Section lastSection() {
        List<GboardPatchesSettingsContract.Section> sections =
                new GboardFrostedGlassSettingsFeature(null).buildScreen(host).getSections();
        assertEquals(2, sections.size());
        return sections.get(1);
    }

    @Test public void faqIsTheLastSectionAndEachRowOpensItsIssue() {
        GboardPatchesSettingsContract.Section faq = lastSection();
        assertEquals("FAQ", faq.getTitle());
        assertEquals(2, faq.getItems().size());
        for (GboardPatchesSettingsContract.Row row : faq.getItems()) {
            ((GboardPatchesSettingsContract.ActionRow) row).getAction().run();
        }
        assertEquals(Arrays.asList(
                "https://github.com/jasonwu1994/Gboard-patches/issues/68",
                "https://github.com/jasonwu1994/Gboard-patches/issues/58"), host.opened);
    }

    @Test public void faqStaysAvailableWhileFrostedGlassIsOff() {
        assertTrue(GboardFrostedGlassSettings.writeEnabled(context, false));
        for (GboardPatchesSettingsContract.Row row : lastSection().getItems()) {
            assertTrue(row.isEnabled());
        }
        assertFalse(GboardFrostedGlassSettings.readEnabled(GboardPatchesSettings.preferences(context)));
    }

    @Test public void aFailingBrowserHandoffCannotEscapeTheFaqRow() {
        host.failure = new IllegalStateException("Injected navigation failure");
        for (GboardPatchesSettingsContract.Row row : lastSection().getItems()) {
            ((GboardPatchesSettingsContract.ActionRow) row).getAction().run();
        }
        assertTrue(host.opened.isEmpty());
    }

    private static final class Host extends GboardPatchesSettingsTestHost {
        RuntimeException failure;
        final List<String> opened = new ArrayList<>();
        Host(Context context) { super(context); }
        @Override public void openExternalUrl(String url) {
            if (failure != null) throw failure;
            opened.add(url);
        }
    }
}
