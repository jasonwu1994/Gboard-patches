package dev.jason.gboardpatches.extension.manualincognito;

final class GboardManualIncognitoSessionPolicy {
    final boolean manualApplied;
    final boolean allowClipboardApplied;
    final boolean allowVoiceApplied;
    final boolean stockRequestedAtStart;
    final long generation;

    GboardManualIncognitoSessionPolicy(boolean manualApplied,
            boolean allowClipboardApplied,
            boolean allowVoiceApplied,
            boolean stockRequestedAtStart,
            long generation) {
        this.manualApplied = manualApplied;
        this.allowClipboardApplied = allowClipboardApplied;
        this.allowVoiceApplied = allowVoiceApplied;
        this.stockRequestedAtStart = stockRequestedAtStart;
        this.generation = generation;
    }

    static GboardManualIncognitoSessionPolicy defaults() {
        return new GboardManualIncognitoSessionPolicy(false, false, false, false, 0L);
    }
}
