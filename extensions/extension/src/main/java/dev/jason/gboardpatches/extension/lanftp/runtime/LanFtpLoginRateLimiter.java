package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sliding-window limiter shared by all control connections in one server generation. */
public final class LanFtpLoginRateLimiter {
    private final int maxPeerFailures;
    private final int maxGlobalFailures;
    private final long windowMillis;
    private final ArrayDeque<Long> globalFailures = new ArrayDeque<>();
    private final Map<String, ArrayDeque<Long>> peerFailures =
            new LinkedHashMap<>(16, 0.75f, true);

    public LanFtpLoginRateLimiter(int maxPeerFailures, int maxGlobalFailures,
            long windowMillis) {
        if (maxPeerFailures < 1 || maxGlobalFailures < maxPeerFailures
                || windowMillis < 1L) {
            throw new IllegalArgumentException("Invalid FTP login rate-limit policy");
        }
        this.maxPeerFailures = maxPeerFailures;
        this.maxGlobalFailures = maxGlobalFailures;
        this.windowMillis = windowMillis;
    }

    public synchronized boolean isAllowed(String peer, long nowMillis) {
        pruneAll(nowMillis);
        ArrayDeque<Long> failures = peerFailures.get(peerKey(peer));
        return globalFailures.size() < maxGlobalFailures
                && (failures == null || failures.size() < maxPeerFailures);
    }

    public synchronized void recordFailure(String peer, long nowMillis) {
        pruneAll(nowMillis);
        addBounded(globalFailures, nowMillis, maxGlobalFailures);
        String key = peerKey(peer);
        ArrayDeque<Long> failures = peerFailures.get(key);
        if (failures == null) {
            while (peerFailures.size() >= maxGlobalFailures) {
                Iterator<String> eldest = peerFailures.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
            failures = new ArrayDeque<>();
            peerFailures.put(key, failures);
        }
        addBounded(failures, nowMillis, maxPeerFailures);
    }

    public synchronized void recordSuccess(String peer) {
        peerFailures.remove(peerKey(peer));
    }

    private void prune(ArrayDeque<Long> failures, long nowMillis) {
        long cutoff = nowMillis - windowMillis;
        while (!failures.isEmpty() && failures.peekFirst() <= cutoff) {
            failures.removeFirst();
        }
    }

    private void pruneAll(long nowMillis) {
        prune(globalFailures, nowMillis);
        Iterator<Map.Entry<String, ArrayDeque<Long>>> peers =
                peerFailures.entrySet().iterator();
        while (peers.hasNext()) {
            ArrayDeque<Long> failures = peers.next().getValue();
            prune(failures, nowMillis);
            if (failures.isEmpty()) {
                peers.remove();
            }
        }
    }

    private static void addBounded(ArrayDeque<Long> failures, long nowMillis, int maximum) {
        while (failures.size() >= maximum) {
            failures.removeFirst();
        }
        failures.addLast(nowMillis);
    }

    private static String peerKey(String peer) {
        return peer == null || peer.isBlank() ? "unknown" : peer;
    }
}
