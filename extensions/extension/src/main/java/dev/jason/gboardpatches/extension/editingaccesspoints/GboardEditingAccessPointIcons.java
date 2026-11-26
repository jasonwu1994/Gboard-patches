package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Icon;
import android.util.DisplayMetrics;
import dev.jason.gboardpatches.extension.settings.GboardPatchesIcons;

/**
 * Self-contained vector icon renderer for toolbar editing access points.
 * Renders white glyphs onto transparent bitmaps so Gboard's toolbar theme tint applies naturally,
 * without referencing version-specific target APK drawable IDs.
 */
public final class GboardEditingAccessPointIcons {
    private static final float ICON_SIZE_DP = 24f;
    private static final float VIEWPORT_960 = 960f;
    private static final float VIEWPORT_24 = 24f;

    // Text box with solid corner handles and a rounded "A", so it reads differently
    // from Gboard's dotted resize frame. Every sub-path winds clockwise.
    private static final String PATH_SELECT_ALL =
            "M156,120H234A36,36 0 0 1 270,156V234A36,36 0 0 1 234,270H156A36,36 0 0 1 120,234"
                    + "V156A36,36 0 0 1 156,120Z"
                    + "M726,120H804A36,36 0 0 1 840,156V234A36,36 0 0 1 804,270H726A36,36 0 0 1 690,234"
                    + "V156A36,36 0 0 1 726,120Z"
                    + "M726,690H804A36,36 0 0 1 840,726V804A36,36 0 0 1 804,840H726A36,36 0 0 1 690,804"
                    + "V726A36,36 0 0 1 726,690Z"
                    + "M156,690H234A36,36 0 0 1 270,726V804A36,36 0 0 1 234,840H156A36,36 0 0 1 120,804"
                    + "V726A36,36 0 0 1 156,690Z"
                    + "M270,159H690V231H270Z"
                    + "M729,270H801V690H729Z"
                    + "M270,729H690V801H270Z"
                    + "M159,270H231V690H159Z"
                    + "M517.4,349.1L402.4,654.1A40,40 0 0 1 327.6,625.9L442.6,320.9"
                    + "A40,40 0 0 1 517.4,349.1Z"
                    + "M517.4,320.9L632.4,625.9A40,40 0 0 1 557.6,654.1L442.6,349.1"
                    + "A40,40 0 0 1 517.4,320.9Z"
                    + "M404.6,503L555.4,503A32,32 0 0 1 555.4,567L404.6,567A32,32 0 0 1 404.6,503Z";

    private static final String PATH_COPY =
            "M360,720q-33,0-56.5-23.5T280,640V160q0-33 23.5-56.5T360,80H720"
                    + "q33,0 56.5,23.5T800,160V640q0,33-23.5,56.5T720,720H360Z"
                    + "m0-80H720V160H360V640ZM200,880q-33,0-56.5-23.5T120,800V240h80"
                    + "V800H640v80H200ZM360,640V160V640Z";

    private static final String PATH_CUT =
            "M760,840L480,560l-94,94q8,15 11,32t3,34q0,66-47,113T240,880T127,833T80,720"
                    + "t47-113t113-47q17,0 34,3t32,11l94-94l-94-94q-15,8-32,11t-34,3"
                    + "q-66,0-113-47T80,240t47-113T240,80t113,47t47,113q0,17-3,34t-11,32"
                    + "L880,800v40H760ZM600,440l-80-80L760,120H880v40L600,440Z"
                    + "M296.5,296.5Q320,273 320,240t-23.5-56.5T240,160t-56.5,23.5T160,240"
                    + "t23.5,56.5T240,320t56.5-23.5ZM494,494q6-6 6-14t-6-14t-14-6t-14,6"
                    + "t-6,14t6,14t14,6t14-6ZM296.5,776.5Q320,753 320,720t-23.5-56.5"
                    + "T240,640t-56.5,23.5T160,720t23.5,56.5T240,800t56.5-23.5Z";

    private static final String PATH_PASTE =
            "M200,840q-33,0-56.5-23.5T120,760V200q0-33 23.5-56.5T200,120H367"
                    + "q11-35 43-57.5T480,40q40,0 71.5,22.5T594,120H760q33,0 56.5,23.5"
                    + "T840,200V760q0,33-23.5,56.5T760,840H200Zm0-80H760V200H680V320H280"
                    + "V200H200V760ZM508.5,188.5Q520,177 520,160t-11.5-28.5T480,120"
                    + "t-28.5,11.5T440,160t11.5,28.5T480,200t28.5-11.5Z";

    // Mirrored horizontally at draw time to form a right-pointing delete glyph matching Gboard's style.
    private static final String PATH_FORWARD_DELETE =
            "M557.23,526.31l83.26,83.21q9.31,9.31 23.05,9.38t23.2-9.38t9.46-23.29"
                    + "t-9.46-23.29L603.54,480l82.92-82.92q9.31-9.31 9.38-23.08t-9.38-23.23"
                    + "t-23.29-9.46t-23.29,9.46l-82.64,82.92l-83.21-83.21q-9.37-9.31-23.11-9.38"
                    + "t-23.2,9.38t-9.46,23.29t9.46,23.29L510.92,480l-83.21,82.92"
                    + "q-9.31,9.31-9.38,23.08t9.38,23.23t23.29,9.46t23.29-9.46l82.92-82.92Z"
                    + "M358.41,792q-16.85,0-31.95-7.46t-24.82-20.8L120.08,522.72"
                    + "q-14.31-18.77-14.31-42.66t14.31-42.77L301.36,196.26"
                    + "q9.72-13.34 24.82-20.8T358.13,168H801.31q29.32,0 50.14,20.85"
                    + "t20.83,50.13V721.02q0,29.28-20.83,50.12T801.31,792H358.41Z"
                    + "m0-66.05h442.9q2.15,0 3.54-1.38t1.38-3.54V238.98q0-2.15-1.38-3.54"
                    + "t-3.54-1.38H358.41q-1.23,0-2.23,0.46t-1.77,1.38L172.9,476.92"
                    + "q-1.08,1.38-1.08,3.08t1.08,3.08L354.41,724.1q0.77,0.92 1.77,1.38"
                    + "t2.23,0.46Z";

    // Clipboard outline with a centered rounded "T" glyph for plain-text paste.
    private static final String PATH_PASTE_PLAIN_TEXT =
            PATH_PASTE
                    + "M480,692q-16,0-27-11t-11-27V476H368q-16,0-27-11t-11-27t11-27t27-11"
                    + "H592q16,0 27,11t11,27t-11,27t-27,11H518V654q0,16-11,27t-27,11Z";

    private static final String PATH_HOME =
            "M251.5,708.5Q240,697 240,680V280q0-17 11.5-28.5T280,240t28.5,11.5"
                    + "T320,280V680q0,17-11.5,28.5T280,720t-28.5-11.5ZM552,480L708,636"
                    + "q11,11 11,28t-11,28t-28,11t-28-11L468,508q-6-6-8.5-13T457,480"
                    + "t2.5-15t8.5-13L652,268q11-11 28-11t28,11t11,28t-11,28L552,480Z";

    private static final String PATH_END =
            "M408,480L252,324q-11-11-11-28t11-28t28-11t28,11L492,452q6,6 8.5,13"
                    + "t2.5,15t-2.5,15T492,508L308,692q-11,11-28,11t-28-11t-11-28t11-28"
                    + "L408,480ZM708.5,251.5Q720,263 720,280V680q0,17-11.5,28.5T680,720"
                    + "t-28.5-11.5T640,680V280q0-17 11.5-28.5T680,240t28.5,11.5Z";

    private static final String PATH_TAB =
            "M11.59,7.41L15.17,11H1v2h14.17l-3.59,3.59L13,18l6,-6 -6,-6 -1.41,1.41z"
                    + "M20,6v12h2V6h-2z";

    private static final String PATH_ARROW_LEFT =
            "M432,480L588,636q11,11 11,28t-11,28t-28,11t-28-11L348,508q-6-6-8.5-13"
                    + "T337,480t2.5-15t8.5-13L532,268q11-11 28-11t28,11t11,28t-11,28L432,480Z";

    private static final String PATH_ARROW_RIGHT =
            "M504,480L348,324q-11-11-11-28t11-28t28-11t28,11L588,452q6,6 8.5,13"
                    + "t2.5,15t-2.5,15T588,508L404,692q-11,11-28,11t-28-11t-11-28t11-28L504,480Z";

    private static final String PATH_ARROW_UP =
            "M480,432L324,588q-11,11-28,11t-28-11t-11-28t11-28L452,348q12-12 28-12"
                    + "t28,12L692,532q11,11 11,28t-11,28t-28,11t-28-11L480,432Z";

    private static final String PATH_ARROW_DOWN =
            "M465,596.5q-7-2.5-13-8.5L268,404q-11-11-11-28t11-28t28-11t28,11L480,504"
                    + "L636,348q11-11 28-11t28,11t11,28t-11,28L508,588q-6,6-13,8.5"
                    + "T480,599t-15-2.5Z";

    private GboardEditingAccessPointIcons() {
    }

    public static boolean hasIcon(String token) {
        return spec(token) != null;
    }

    public static Icon createIcon(Context context, String token) {
        if (context == null || token == null) {
            return null;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int sizePx = Math.max(1, Math.round(ICON_SIZE_DP * metrics.density));
        Bitmap bitmap = renderBitmap(token, sizePx, metrics.densityDpi);
        return bitmap == null ? null : Icon.createWithBitmap(bitmap);
    }

    public static Bitmap renderBitmap(String token, int sizePx, int densityDpi) {
        IconSpec spec = spec(token);
        if (spec == null || sizePx <= 0) {
            return null;
        }
        try {
            Path source = GboardPatchesIcons.parsePathData(spec.pathData);
            Path scaled = new Path();
            Matrix matrix = new Matrix();
            float scale = sizePx / spec.viewport;
            if (spec.flipHorizontal) {
                matrix.setScale(-scale, scale);
                matrix.postTranslate(sizePx, 0f);
            } else {
                matrix.setScale(scale, scale);
            }
            source.transform(matrix, scaled);

            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            if (densityDpi > 0) {
                bitmap.setDensity(densityDpi);
            }
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawPath(scaled, paint);
            return bitmap;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static String pathData(String token) {
        IconSpec spec = spec(token);
        return spec == null ? null : spec.pathData;
    }

    private static IconSpec spec(String token) {
        if (token == null) {
            return null;
        }
        switch (token) {
            case "text_edit_select_all":
                return new IconSpec(PATH_SELECT_ALL, VIEWPORT_960, false);
            case "text_edit_copy":
                return new IconSpec(PATH_COPY, VIEWPORT_960, false);
            case "text_edit_cut":
                return new IconSpec(PATH_CUT, VIEWPORT_960, false);
            case "text_edit_paste":
                return new IconSpec(PATH_PASTE, VIEWPORT_960, false);
            case "text_edit_forward_delete":
                return new IconSpec(PATH_FORWARD_DELETE, VIEWPORT_960, true);
            case "text_edit_paste_plain_text":
                return new IconSpec(PATH_PASTE_PLAIN_TEXT, VIEWPORT_960, false);
            case "text_edit_home":
                return new IconSpec(PATH_HOME, VIEWPORT_960, false);
            case "text_edit_end":
                return new IconSpec(PATH_END, VIEWPORT_960, false);
            case "text_edit_tab":
                return new IconSpec(PATH_TAB, VIEWPORT_24, false);
            case "text_edit_arrow_left":
                return new IconSpec(PATH_ARROW_LEFT, VIEWPORT_960, false);
            case "text_edit_arrow_right":
                return new IconSpec(PATH_ARROW_RIGHT, VIEWPORT_960, false);
            case "text_edit_arrow_up":
                return new IconSpec(PATH_ARROW_UP, VIEWPORT_960, false);
            case "text_edit_arrow_down":
                return new IconSpec(PATH_ARROW_DOWN, VIEWPORT_960, false);
            default:
                return null;
        }
    }

    private static final class IconSpec {
        final String pathData;
        final float viewport;
        final boolean flipHorizontal;

        IconSpec(String pathData, float viewport, boolean flipHorizontal) {
            this.pathData = pathData;
            this.viewport = viewport;
            this.flipHorizontal = flipHorizontal;
        }
    }
}
