package dev.jason.gboardpatches.extension.spacebarlogo;

public final class GboardSpacebarLogoContract {
    public static final String ICON_GOOGLE_G = "GOOGLE_G";
    public static final String ICON_HEART = "HEART";
    public static final String ICON_EMOJI = "EMOJI";
    public static final String ICON_GOOGLE_G_COLOR = "GOOGLE_G_COLOR";
    public static final String ICON_CUSTOM_IMAGE = "CUSTOM_IMAGE";
    public static final int MAX_IMAGE_EDGE = 96;
    public static final int MAX_PNG_BYTES = 48 * 1024;
    public static final int MAX_BASE64_LENGTH = ((MAX_PNG_BYTES + 2) / 3) * 4;
    public static final String DEFAULT_ICON = ICON_GOOGLE_G;
    public static final int DEFAULT_SIZE_DP = 24;
    public static final int MIN_SIZE_DP = 12;
    public static final int MAX_SIZE_DP = 40;

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

    public static boolean isValidSizeDp(int sizeDp) {
        return sizeDp >= MIN_SIZE_DP && sizeDp <= MAX_SIZE_DP;
    }

    public static int normalizeSizeDp(int sizeDp) {
        return isValidSizeDp(sizeDp) ? sizeDp : DEFAULT_SIZE_DP;
    }
}
