package dev.jason.gboardpatches.extension.clipboard;

import android.widget.TextView;

final class GboardClipboardCountdownFeature {
    private final GboardClipboardRuntimeSupport support;
    private final GboardClipboardRetentionFeature retentionFeature;

    GboardClipboardCountdownFeature(GboardClipboardRuntimeSupport support,
            GboardClipboardRetentionFeature retentionFeature) {
        this.support = support;
        this.retentionFeature = retentionFeature;
    }

    boolean shouldContinueUpdates(GboardClipboardRuntimeSupport.CountdownBinding binding,
            GboardClipboardRuntimeSupport.RuntimeSettings settings) {
        return support.runSafely("check clipboard countdown continuation", () -> {
            if (binding == null || settings == null) {
                return Boolean.FALSE;
            }
            TextView view = binding.textView();
            if (!settings.showExpiryCountdown || binding.pinned || binding.special
                    || binding.clipboardTtlMs < 0L || !binding.isVisible(view)) {
                return Boolean.FALSE;
            }
            long remainingMs = retentionFeature.remainingMs(binding.clipTimestamp,
                    binding.clipboardTtlMs);
            if (remainingMs <= 0L) {
                return Boolean.FALSE;
            }
            return Boolean.valueOf(
                    support.activeCountdownByTextView.get(view) == binding);
        }, Boolean.FALSE).booleanValue();
    }

    String formatToken(GboardClipboardRuntimeSupport.ReflectionHandles handles,
            GboardClipboardRuntimeSupport.CountdownBinding binding) {
        return support.runSafely("format clipboard countdown token", () -> {
            if (binding == null) {
                return null;
            }
            if (binding.pinned) {
                return "pinned";
            }
            if (binding.special) {
                return "special";
            }
            if (binding.clipboardTtlMs < 0L) {
                return "inf";
            }

            long remainingMs = retentionFeature.remainingMs(binding.clipTimestamp,
                    binding.clipboardTtlMs);
            TextView view = binding.textView();
            long primaryTimestamp = view == null ? 0L : support.readPrimaryTimestamp(handles,
                    view.getContext());
            boolean isPrimary = primaryTimestamp == binding.clipTimestamp
                    && primaryTimestamp > 0L;
            long remainingSeconds = Math.max(0L, (remainingMs + 999L) / 1000L);
            String countdownText = formatRemainingDuration(remainingSeconds);
            return isPrimary ? countdownText + " primary" : countdownText;
        }, null);
    }

    boolean isToken(String token) {
        return token != null
                && ("pinned".equals(token)
                || "special".equals(token)
                || "inf".equals(token)
                || token.matches("(?:\\d+h\\d+m)?(?:\\d+m)?\\d+s(?: primary)?"));
    }

    private String formatRemainingDuration(long remainingSeconds) {
        long hours = remainingSeconds / 3600L;
        long minutes = (remainingSeconds % 3600L) / 60L;
        long seconds = remainingSeconds % 60L;

        StringBuilder builder = new StringBuilder();
        if (hours > 0L) {
            builder.append(hours).append('h');
            builder.append(minutes).append('m');
        } else if (minutes > 0L) {
            builder.append(minutes).append('m');
        }
        builder.append(seconds).append('s');
        return builder.toString();
    }
}
