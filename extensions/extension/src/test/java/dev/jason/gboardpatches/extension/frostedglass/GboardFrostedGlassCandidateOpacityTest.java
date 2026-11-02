package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GboardFrostedGlassCandidateOpacityTest {
    @Test public void releasedAndReboundCandidateDoesNotLeaveAGrayFill() throws Exception {
        for (int alpha : new int[]{128, 0, 255, -1}) {
            try (var f = new GboardFrostedGlassKeyOpacityTest.Fixture()) {
                TextView candidate = new TextView(f.activity.get());
                candidate.setTag(".keytop.for-candidate-key");
                candidate.setText("Ahhh");
                // iosgray: normal is transparent white; pressed is opaque gray.
                ColorDrawable fill = new ColorDrawable(0x00ffffff);
                candidate.setBackground(fill);
                f.root.addView(candidate);
                f.keys(alpha);
                f.apply();
                assertEquals(0, fill.getAlpha());

                for (int press = 0; press < 3; press++) {
                    candidate.setPressed(true);
                    fill.setColor(0xffc7c7c7);
                    f.apply();
                    candidate.setPressed(false);
                    fill.setColor(0x00ffffff);
                    f.apply();
                    assertEquals("released candidate must stay transparent, alpha=" + alpha,
                            0, fill.getAlpha());
                    candidate.setText(press % 2 == 0 ? "and" : "lght");
                    f.apply();
                    assertEquals("reusing the candidate must not reveal its white RGB",
                            0xff222222, pixelOverDarkBackground(fill));
                }
                f.keys(-1);
                f.apply();
                f.restore();
                assertEquals(0x00ffffff, fill.getColor());
            }
        }
    }

    @Test public void candidateKeepsThemeColorsAndPressedHighlightWhileOrdinaryKeysFade()
            throws Exception {
        try (var f = new GboardFrostedGlassKeyOpacityTest.Fixture()) {
            TextView candidate = new TextView(f.activity.get());
            candidate.setTag(".keytop.for-candidate-key.with-popup");
            candidate.setText("word");
            candidate.setTextColor(Color.WHITE);
            StateListDrawable fill = new StateListDrawable();
            fill.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0xffc7c7c7));
            fill.addState(new int[]{}, new ColorDrawable(0x40123456));
            candidate.setBackground(new InsetDrawable(fill, 3));
            f.root.addView(candidate);
            var ordinary = f.image(".keytop.with-popup", Color.WHITE);
            ordinary.setBackgroundColor(Color.BLUE);
            f.keys(128);
            f.apply();
            assertEquals(0x40123456, ((ColorDrawable) fill.getCurrent()).getColor());
            candidate.setPressed(true);
            f.apply();
            assertEquals(0xffc7c7c7, ((ColorDrawable) fill.getCurrent()).getColor());
            candidate.setPressed(false);
            f.apply();
            assertEquals(0x40123456, ((ColorDrawable) fill.getCurrent()).getColor());
            assertEquals(128, ordinary.getBackground().getAlpha());
            assertEquals(255, ordinary.getDrawable().getAlpha());
            assertEquals(Color.WHITE, candidate.getCurrentTextColor());
            assertEquals(1f, candidate.getAlpha(), 0f);
            f.keys(-1);
            f.apply();
            assertEquals(255, ordinary.getBackground().getAlpha());
            assertEquals(0x40123456, ((ColorDrawable) fill.getCurrent()).getColor());
        }
    }

    @Test public void candidateModifierUsesCompleteSelectorBoundaries() {
        for (String tag : new String[]{".keytop.for-candidate-key",
                ".keytop.for-candidate-key.with-popup", ".keytop.with-popup.for-candidate-key"}) {
            assertEquals(tag, 0, GboardFrostedGlassRuntime.targetKinds(tag));
        }
        for (String tag : new String[]{".keytop", ".keytop.with-popup", ".space_bar",
                ".background-icon.for-action-key", ".keytop.for-candidate-keyboard"}) {
            assertNotEquals(tag, 0, GboardFrostedGlassRuntime.targetKinds(tag));
        }
    }

    private static int pixelOverDarkBackground(ColorDrawable fill) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(0xff222222);
            fill.setBounds(0, 0, 1, 1);
            fill.draw(new Canvas(bitmap));
            return bitmap.getPixel(0, 0);
        } finally {
            bitmap.recycle();
        }
    }
}
