package dev.jason.gboardpatches.extension.settings;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public final class GboardPatchesIcons {
    public static final int NONE = 0;
    public static final int BACKUP = 1;
    public static final int BOLT = 2;
    public static final int CHEVRON_LEFT = 3;
    public static final int CHEVRON_RIGHT = 4;
    public static final int CLIPBOARD = 5;
    public static final int CLOSE = 6;
    public static final int CODE = 7;
    public static final int IMAGE = 8;
    public static final int KEYBOARD_FILLED = 9;
    public static final int LAN = 10;
    public static final int PALETTE = 11;
    public static final int PERSON = 12;
    public static final int PLAY = 13;
    public static final int SPARKLE = 14;
    public static final int TAG = 15;
    public static final int TRANSLATE = 16;

    public static final int SEARCH = 17;
    public static final int MORE = 18;
    public static final int KEYBOARD = 19;

    private static final float VIEWPORT = 24f;

    private GboardPatchesIcons() {
    }

    static String pathData(int icon) {
        switch (icon) {
            case BACKUP:
                return "M14 12c0-1.1-.9-2-2-2s-2 .9-2 2 .9 2 2 2 2-.9 2-2zm-2-9c-4.97 0-9 4.03-9 9H0"
                        + "l4 4 4-4H5c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.51 0-2.91-.49-4.06"
                        + "-1.3l-1.42 1.44C8.04 20.3 9.94 21 12 21c4.97 0 9-4.03 9-9s-4.03-9-9-9z";
            case BOLT:
                return "M11 21h-1l1-7H7.5c-.88 0-.33-.75-.31-.78C8.48 10.94 10.42 7.54 13.01 3h1l-1 7"
                        + "h3.51c.4 0 .62.19.4.66C12.97 17.55 11 21 11 21z";
            case CHEVRON_LEFT:
                return "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z";
            case CHEVRON_RIGHT:
                return "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z";
            case CLIPBOARD:
                return "M19 2h-4.18C14.4.84 13.3 0 12 0S9.6.84 9.18 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2"
                        + " 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1"
                        + "-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z";
            case CLOSE:
                return "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41"
                        + " 17.59 19 19 17.59 13.41 12z";
            case CODE:
                return "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6"
                        + "-6 6-1.4-1.4z";
            case IMAGE:
                return "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2z"
                        + "M8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z";
            case KEYBOARD_FILLED:
                return "M20 5H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2z"
                        + "m-9 3h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2z"
                        + "m0-3H5V8h2v2zm9 7H8v-2h8v2zm0-4h-2v-2h2v2zm0-3h-2V8h2v2zm3 3h-2v-2h2v2z"
                        + "m0-3h-2V8h2v2z";
            case LAN:
                return "M13 22h8v-7h-3v-4h-5V9h3V2H8v7h3v2H6v4H3v7h8v-7H8v-2h8v2h-3z";
            case PALETTE:
                return "M12 3a9 9 0 0 0 0 18c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26"
                        + "-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8"
                        + "-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12"
                        + " 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5"
                        + " 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5"
                        + " 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5"
                        + " 1.5z";
            case PERSON:
                return "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34"
                        + "-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
            case PLAY:
                return "M10 8.64v6.72L15.27 12 10 8.64zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48"
                        + " 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z";
            case SPARKLE:
                return "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5"
                        + "L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75"
                        + "L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z";
            case TAG:
                return "M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05"
                        + ".59 1.42l9 9c.36.36.86.58 1.41.58s1.05-.22 1.41-.59l7-7c.37-.36.59-.86"
                        + ".59-1.41s-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7"
                        + " 4.67 7 5.5 6.33 7 5.5 7z";
            case TRANSLATE:
                return "M12.87 15.07l-2.54-2.51.03-.03A17.52 17.52 0 0 0 14.07 6H17V4h-7V2H8v2H1v2"
                        + "h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63"
                        + " 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12"
                        + " 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z";
            case SEARCH:
                return "M9.5,3a6.5,6.5 0,1 0,4.03 11.6L19,20.07 20.07,19l-5.47,-5.47A6.5,6.5 0,0 0,9.5 3zM5,9.5a4.5,4.5 0,1 1,9 0,4.5 4.5,0 0,1 -9,0z";
            case MORE:
                return "M6,10a2,2 0,1 0,0 4a2,2 0,1 0,0 -4M12,10a2,2 0,1 0,0 4a2,2 0,1 0,0 -4M18,10a2,2 0,1 0,0 4a2,2 0,1 0,0 -4";
            case KEYBOARD:
                return "M4,19q-0.825,0-1.4125-0.5875T2,17V7q0-0.825 0.5875-1.4125T4,5H20q0.825,0 1.4125,0.5875T22,7V17q0,0.825-0.5875,1.4125T20,19H4Zm0-2H20V7H4V17Zm4-1H16V14H8v2ZM5,13h2V11H5v2Zm3,0h2V11H8v2Zm3,0h2V11H11v2Zm3,0h2V11H14v2Zm3,0h2V11H17v2ZM5,10h2V8H5v2Zm3,0h2V8H8v2Zm3,0h2V8H11v2Zm3,0h2V8H14v2Zm3,0h2V8H17v2ZM4,17V7V17Z";
            default:
                return null;
        }
    }

    public static Drawable create(int icon, int color, float density) {
        String data = pathData(icon);
        if (data == null) {
            return null;
        }
        try {
            return new IconDrawable(PathDataParser.parse(data), color, density);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static final class IconDrawable extends Drawable {
        private final Path sourcePath;
        private final Path scaledPath = new Path();
        private final Matrix matrix = new Matrix();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int intrinsicSize;
        private ColorStateList tint;

        IconDrawable(Path sourcePath, int color, float density) {
            this.sourcePath = sourcePath;
            this.intrinsicSize = Math.round(VIEWPORT * density);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            float scale = Math.min(bounds.width(), bounds.height()) / VIEWPORT;
            float left = bounds.left + ((bounds.width() - (VIEWPORT * scale)) / 2f);
            float top = bounds.top + ((bounds.height() - (VIEWPORT * scale)) / 2f);
            matrix.setScale(scale, scale);
            matrix.postTranslate(left, top);
            sourcePath.transform(matrix, scaledPath);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(scaledPath, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicSize;
        }

        @Override
        public void setTint(int tintColor) {
            setTintList(ColorStateList.valueOf(tintColor));
        }

        @Override
        public void setTintList(ColorStateList tintList) {
            tint = tintList;
            applyTint(getState());
        }

        @Override
        public boolean isStateful() {
            return tint != null && tint.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return applyTint(state);
        }

        private boolean applyTint(int[] state) {
            if (tint == null) {
                return false;
            }
            int next = tint.getColorForState(state, tint.getDefaultColor());
            if (next == paint.getColor()) {
                return false;
            }
            paint.setColor(next);
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    static final class PathDataParser {
        private final String data;
        private int index;

        private PathDataParser(String data) {
            this.data = data;
        }

        static Path parse(String data) {
            return new PathDataParser(data).run();
        }

        private Path run() {
            Path path = new Path();
            float currentX = 0f;
            float currentY = 0f;
            float startX = 0f;
            float startY = 0f;
            float controlX = 0f;
            float controlY = 0f;
            char previous = ' ';
            char command = ' ';
            while (true) {
                skipSeparators();
                if (index >= data.length()) {
                    break;
                }
                char next = data.charAt(index);
                if (Character.isLetter(next)) {
                    command = next;
                    index++;
                } else if (command == ' ') {
                    throw new IllegalArgumentException("Path data must start with a command");
                } else if (command == 'M') {
                    command = 'L';
                } else if (command == 'm') {
                    command = 'l';
                }
                boolean relative = Character.isLowerCase(command);
                float baseX = relative ? currentX : 0f;
                float baseY = relative ? currentY : 0f;
                switch (Character.toUpperCase(command)) {
                    case 'M':
                        currentX = baseX + number();
                        currentY = baseY + number();
                        path.moveTo(currentX, currentY);
                        startX = currentX;
                        startY = currentY;
                        break;
                    case 'L':
                        currentX = baseX + number();
                        currentY = baseY + number();
                        path.lineTo(currentX, currentY);
                        break;
                    case 'H':
                        currentX = baseX + number();
                        path.lineTo(currentX, currentY);
                        break;
                    case 'V':
                        currentY = baseY + number();
                        path.lineTo(currentX, currentY);
                        break;
                    case 'C': {
                        float x1 = baseX + number();
                        float y1 = baseY + number();
                        controlX = baseX + number();
                        controlY = baseY + number();
                        currentX = baseX + number();
                        currentY = baseY + number();
                        path.cubicTo(x1, y1, controlX, controlY, currentX, currentY);
                        break;
                    }
                    case 'S': {
                        boolean smooth = "CcSs".indexOf(previous) >= 0;
                        float x1 = smooth ? (2f * currentX) - controlX : currentX;
                        float y1 = smooth ? (2f * currentY) - controlY : currentY;
                        controlX = baseX + number();
                        controlY = baseY + number();
                        currentX = baseX + number();
                        currentY = baseY + number();
                        path.cubicTo(x1, y1, controlX, controlY, currentX, currentY);
                        break;
                    }
                    case 'Q':
                        controlX = baseX + number();
                        controlY = baseY + number();
                        currentX = baseX + number();
                        currentY = baseY + number();
                        path.quadTo(controlX, controlY, currentX, currentY);
                        break;
                    case 'T': {
                        boolean smooth = "QqTt".indexOf(previous) >= 0;
                        controlX = smooth ? (2f * currentX) - controlX : currentX;
                        controlY = smooth ? (2f * currentY) - controlY : currentY;
                        currentX = baseX + number();
                        currentY = baseY + number();
                        path.quadTo(controlX, controlY, currentX, currentY);
                        break;
                    }
                    case 'A': {
                        float radiusX = number();
                        float radiusY = number();
                        float rotation = number();
                        boolean largeArc = flag();
                        boolean sweep = flag();
                        float endX = baseX + number();
                        float endY = baseY + number();
                        arcTo(path, currentX, currentY, endX, endY, radiusX, radiusY, rotation,
                                largeArc, sweep);
                        currentX = endX;
                        currentY = endY;
                        break;
                    }
                    case 'Z':
                        path.close();
                        currentX = startX;
                        currentY = startY;
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported path command: " + command);
                }
                previous = command;
                if (Character.toUpperCase(command) == 'Z') {
                    command = ' ';
                }
            }
            return path;
        }

        private void skipSeparators() {
            while (index < data.length()) {
                char value = data.charAt(index);
                if (value == ',' || Character.isWhitespace(value)) {
                    index++;
                } else {
                    break;
                }
            }
        }

        private boolean flag() {
            skipSeparators();
            if (index >= data.length()) {
                throw new IllegalArgumentException("Missing arc flag");
            }
            char value = data.charAt(index++);
            if (value != '0' && value != '1') {
                throw new IllegalArgumentException("Invalid arc flag: " + value);
            }
            return value == '1';
        }

        private float number() {
            skipSeparators();
            int start = index;
            if (index < data.length() && (data.charAt(index) == '-' || data.charAt(index) == '+')) {
                index++;
            }
            boolean seenDot = false;
            boolean seenDigit = false;
            while (index < data.length()) {
                char value = data.charAt(index);
                if (Character.isDigit(value)) {
                    seenDigit = true;
                    index++;
                } else if (value == '.' && !seenDot) {
                    seenDot = true;
                    index++;
                } else if ((value == 'e' || value == 'E') && seenDigit) {
                    index++;
                    if (index < data.length()
                            && (data.charAt(index) == '-' || data.charAt(index) == '+')) {
                        index++;
                    }
                } else {
                    break;
                }
            }
            if (!seenDigit) {
                throw new IllegalArgumentException("Expected number at " + start);
            }
            return Float.parseFloat(data.substring(start, index));
        }

        private static void arcTo(Path path, float x0, float y0, float x1, float y1,
                float radiusX, float radiusY, float rotationDegrees, boolean largeArc,
                boolean sweep) {
            if (x0 == x1 && y0 == y1) {
                return;
            }
            double rx = Math.abs(radiusX);
            double ry = Math.abs(radiusY);
            if (rx == 0 || ry == 0) {
                path.lineTo(x1, y1);
                return;
            }
            double phi = Math.toRadians(rotationDegrees);
            double cosPhi = Math.cos(phi);
            double sinPhi = Math.sin(phi);
            double dx = (x0 - x1) / 2.0;
            double dy = (y0 - y1) / 2.0;
            double x1p = (cosPhi * dx) + (sinPhi * dy);
            double y1p = (-sinPhi * dx) + (cosPhi * dy);
            double lambda = ((x1p * x1p) / (rx * rx)) + ((y1p * y1p) / (ry * ry));
            if (lambda > 1) {
                double scale = Math.sqrt(lambda);
                rx *= scale;
                ry *= scale;
            }
            double numerator = (rx * rx * ry * ry) - (rx * rx * y1p * y1p) - (ry * ry * x1p * x1p);
            double denominator = (rx * rx * y1p * y1p) + (ry * ry * x1p * x1p);
            double coefficient = denominator == 0
                    ? 0
                    : Math.sqrt(Math.max(0, numerator / denominator));
            if (largeArc == sweep) {
                coefficient = -coefficient;
            }
            double cxp = coefficient * ((rx * y1p) / ry);
            double cyp = coefficient * (-(ry * x1p) / rx);
            double cx = (cosPhi * cxp) - (sinPhi * cyp) + ((x0 + x1) / 2.0);
            double cy = (sinPhi * cxp) + (cosPhi * cyp) + ((y0 + y1) / 2.0);
            double theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
            double delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry,
                    (-x1p - cxp) / rx, (-y1p - cyp) / ry);
            if (!sweep && delta > 0) {
                delta -= 2 * Math.PI;
            } else if (sweep && delta < 0) {
                delta += 2 * Math.PI;
            }
            int segments = (int) Math.ceil(Math.abs(delta) / (Math.PI / 2) - 1e-7);
            segments = Math.max(1, segments);
            double step = delta / segments;
            double handle = (4.0 / 3.0) * Math.tan(step / 4.0);
            double angle = theta1;
            for (int segment = 0; segment < segments; segment++) {
                double cos1 = Math.cos(angle);
                double sin1 = Math.sin(angle);
                double cos2 = Math.cos(angle + step);
                double sin2 = Math.sin(angle + step);
                double ex1 = cos1 - (handle * sin1);
                double ey1 = sin1 + (handle * cos1);
                double ex2 = cos2 + (handle * sin2);
                double ey2 = sin2 - (handle * cos2);
                path.cubicTo(
                        (float) mapX(cx, rx, ry, cosPhi, sinPhi, ex1, ey1),
                        (float) mapY(cy, rx, ry, cosPhi, sinPhi, ex1, ey1),
                        (float) mapX(cx, rx, ry, cosPhi, sinPhi, ex2, ey2),
                        (float) mapY(cy, rx, ry, cosPhi, sinPhi, ex2, ey2),
                        (float) mapX(cx, rx, ry, cosPhi, sinPhi, cos2, sin2),
                        (float) mapY(cy, rx, ry, cosPhi, sinPhi, cos2, sin2));
                angle += step;
            }
        }

        private static double mapX(double cx, double rx, double ry, double cosPhi, double sinPhi,
                double unitX, double unitY) {
            return cx + (cosPhi * rx * unitX) - (sinPhi * ry * unitY);
        }

        private static double mapY(double cy, double rx, double ry, double cosPhi, double sinPhi,
                double unitX, double unitY) {
            return cy + (sinPhi * rx * unitX) + (cosPhi * ry * unitY);
        }

        private static double angle(double ux, double uy, double vx, double vy) {
            double dot = (ux * vx) + (uy * vy);
            double length = Math.sqrt((ux * ux) + (uy * uy)) * Math.sqrt((vx * vx) + (vy * vy));
            double value = Math.acos(Math.max(-1, Math.min(1, dot / length)));
            return ((ux * vy) - (uy * vx)) < 0 ? -value : value;
        }
    }
}
