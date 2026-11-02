package dev.jason.gboardpatches.extension.settings;

import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GboardPatchesIconsTest {
    @Test
    public void everyShellIconParsesIntoTheMaterialViewport() {
        for (int icon = GboardPatchesIcons.BACKUP; icon <= GboardPatchesIcons.KEYBOARD; icon++) {
            String data = GboardPatchesIcons.pathData(icon);
            Assert.assertNotNull("icon " + icon, data);
            Path path = GboardPatchesIcons.PathDataParser.parse(data);
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            Assert.assertFalse("icon " + icon + " is empty", bounds.isEmpty());
            Assert.assertTrue("icon " + icon + " exceeds 24x24: " + bounds,
                    bounds.left >= -0.5f && bounds.top >= -0.5f
                            && bounds.right <= 24.5f && bounds.bottom <= 24.5f);
        }
    }

    @Test
    public void unknownIconsHaveNoDrawable() {
        Assert.assertNull(GboardPatchesIcons.create(GboardPatchesIcons.NONE, Color.BLACK, 1f));
        Assert.assertNull(GboardPatchesIcons.create(999, Color.BLACK, 1f));
    }

    @Test
    public void iconDrawableReportsDensityScaledSize() {
        Drawable drawable = GboardPatchesIcons.create(GboardPatchesIcons.CLOSE, Color.BLACK, 2f);
        Assert.assertNotNull(drawable);
        Assert.assertEquals(48, drawable.getIntrinsicWidth());
        Assert.assertEquals(48, drawable.getIntrinsicHeight());
    }

    @Test
    public void parserHandlesCompactNumbersAndArcs() {
        RectF bounds = new RectF();
        GboardPatchesIcons.PathDataParser.parse("M0 0h10v10H0z").computeBounds(bounds, true);
        Assert.assertEquals(new RectF(0f, 0f, 10f, 10f), bounds);

        Path arc = GboardPatchesIcons.PathDataParser.parse("M2 12a10 10 0 0 0 20 0");
        arc.computeBounds(bounds, true);
        Assert.assertEquals(2f, bounds.left, 0.01f);
        Assert.assertEquals(22f, bounds.right, 0.01f);

        GboardPatchesIcons.PathDataParser.parse("M.5.5 1 1").computeBounds(bounds, true);
        Assert.assertEquals(0.5f, bounds.left, 0.001f);
        Assert.assertEquals(1f, bounds.right, 0.001f);
    }
}
