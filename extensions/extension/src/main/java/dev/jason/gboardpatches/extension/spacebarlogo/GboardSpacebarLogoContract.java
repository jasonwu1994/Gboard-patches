package dev.jason.gboardpatches.extension.spacebarlogo;

public final class GboardSpacebarLogoContract {
    public static final String ICON_GOOGLE_G = "GOOGLE_G";
    public static final String ICON_HEART = "HEART";
    public static final String ICON_EMOJI = "EMOJI";
    public static final String ICON_GOOGLE_G_COLOR = "GOOGLE_G_COLOR";
    public static final String ICON_CUSTOM_IMAGE = "CUSTOM_IMAGE";
    /** Larger than any phone space key in pixels, so 100% never upsamples a stored image. */
    public static final int MAX_IMAGE_WIDTH = 1024;
    public static final int MAX_IMAGE_HEIGHT = 256;
    /** Keeps the stored Base64 value small enough for settings and backups. */
    public static final int MAX_IMAGE_BYTES = 128 * 1024;
    public static final int MAX_BASE64_LENGTH = ((MAX_IMAGE_BYTES + 2) / 3) * 4;
    public static final String DEFAULT_ICON = ICON_GOOGLE_G;
    /** Percent of the space key's inner box; 100% touches its height or width. */
    public static final int DEFAULT_SIZE_PERCENT = 60;
    public static final int MIN_SIZE_PERCENT = 10;
    public static final int MAX_SIZE_PERCENT = 100;
    /** Earlier dp sizes convert with 40 dp as 100%. */
    public static final int LEGACY_FULL_SIZE_DP = 40;

    private GboardSpacebarLogoContract() {
    }

    public static String normalizeIcon(String icon) {
        String normalized = icon == null ? "" : icon.trim();
        if (ICON_GOOGLE_G_COLOR.equalsIgnoreCase(normalized)) {
            return ICON_GOOGLE_G_COLOR;
        }
        if (ICON_CUSTOM_IMAGE.equalsIgnoreCase(normalized)) {
            return ICON_CUSTOM_IMAGE;
        }
        if (ICON_HEART.equalsIgnoreCase(normalized)) {
            return ICON_HEART;
        }
        if (ICON_EMOJI.equalsIgnoreCase(normalized)) {
            return ICON_EMOJI;
        }
        return ICON_GOOGLE_G;
    }

    public static String boundedImageValue(String value) {
        return value != null && value.length() <= MAX_BASE64_LENGTH ? value : "";
    }

    public static boolean isValidSizePercent(int sizePercent) {
        return sizePercent >= MIN_SIZE_PERCENT && sizePercent <= MAX_SIZE_PERCENT;
    }

    /** Out-of-range numbers clamp, so a large stored value means "as large as the key". */
    public static int normalizeSizePercent(int sizePercent) {
        return Math.max(MIN_SIZE_PERCENT, Math.min(MAX_SIZE_PERCENT, sizePercent));
    }

    public static int sizePercentFromLegacyDp(int sizeDp) {
        return normalizeSizePercent(Math.round(sizeDp * 100f / LEGACY_FULL_SIZE_DP));
    }
}
