package dev.jason.gboardpatches.extension.emojisize;

public final class GboardEmojiSizePolicy {
    public static final String EMOJI_SCALE_SUPPORTED_FLAG = "emoji_scale_supported";

    private GboardEmojiSizePolicy() {
    }

    public static Object maybeForce(String flagName, Object stockResult, boolean enabled) {
        if (!enabled
                || !EMOJI_SCALE_SUPPORTED_FLAG.equals(flagName)
                || !Boolean.FALSE.equals(stockResult)) {
            return stockResult;
        }
        return Boolean.TRUE;
    }
}
