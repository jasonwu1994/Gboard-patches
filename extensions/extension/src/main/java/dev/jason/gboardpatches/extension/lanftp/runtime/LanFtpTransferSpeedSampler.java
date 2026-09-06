package dev.jason.gboardpatches.extension.lanftp.runtime;

/** Computes transfer throughput only after a full window, excluding socket-buffer bursts. */
final class LanFtpTransferSpeedSampler {
    static final long NO_SAMPLE = -1L;
    private static final long MIN_SAMPLE_NANOS = 1_000_000_000L;

    private long sampleBytes;
    private long sampleNanos;

    LanFtpTransferSpeedSampler(long startedNanos) {
        sampleNanos = startedNanos;
    }

    long sample(long totalBytes, long nowNanos) {
        long safeBytes = Math.max(sampleBytes, totalBytes);
        long elapsedNanos = nowNanos - sampleNanos;
        if (elapsedNanos < MIN_SAMPLE_NANOS) {
            return NO_SAMPLE;
        }
        if (elapsedNanos <= 0L) {
            sampleBytes = safeBytes;
            sampleNanos = nowNanos;
            return NO_SAMPLE;
        }
        long transferredBytes = safeBytes - sampleBytes;
        double rate = (transferredBytes / (double) elapsedNanos) * 1_000_000_000.0d;
        long bytesPerSecond = rate >= Long.MAX_VALUE
                ? Long.MAX_VALUE
                : Math.max(0L, (long) rate);
        sampleBytes = safeBytes;
        sampleNanos = nowNanos;
        return bytesPerSecond;
    }
}
