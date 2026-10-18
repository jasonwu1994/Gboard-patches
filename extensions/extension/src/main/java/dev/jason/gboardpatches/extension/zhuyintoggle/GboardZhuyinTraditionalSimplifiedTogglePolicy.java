package dev.jason.gboardpatches.extension.zhuyintoggle;

public final class GboardZhuyinTraditionalSimplifiedTogglePolicy {
    public static final int TOGGLE_KEY_ID = 0x7f0b296a;
    public static final int SINGLE_CHARACTER_CANDIDATE_ON_KEY_ID = 0x7f0b28ea;
    public static final int SINGLE_CHARACTER_CANDIDATE_OFF_KEY_ID = 0x7f0b28e8;
    public static final int SINGLE_CHARACTER_CANDIDATE_LABEL_LOCATION = 0x7f0b0651;
    public static final String ACTION_SLIDE_UP = "SLIDE_UP";
    public static final String ACTION_PRESS = "PRESS";

    private GboardZhuyinTraditionalSimplifiedTogglePolicy() {
    }

    public static boolean shouldHandleKey(int keyId) {
        return keyId == TOGGLE_KEY_ID;
    }

    public static boolean shouldConsume(int keyId, String actionName) {
        return shouldHandleKey(keyId) && ACTION_SLIDE_UP.equals(actionName);
    }

    public static boolean shouldConsumeCandidateButton(int keyId, String actionName) {
        return isSingleCharacterCandidateKey(keyId) && ACTION_PRESS.equals(actionName);
    }

    public static boolean isSingleCharacterCandidateKey(int keyId) {
        return keyId == SINGLE_CHARACTER_CANDIDATE_ON_KEY_ID
                || keyId == SINGLE_CHARACTER_CANDIDATE_OFF_KEY_ID;
    }

    public static String nextLabel(boolean simplifiedEnabled) {
        return simplifiedEnabled ? "繁" : "簡";
    }
}
