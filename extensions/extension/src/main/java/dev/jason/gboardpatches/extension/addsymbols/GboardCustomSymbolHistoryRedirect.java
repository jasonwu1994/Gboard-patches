package dev.jason.gboardpatches.extension.addsymbols;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class GboardCustomSymbolHistoryRedirect {
    private static final ThreadLocal<Set<Object>> ACTIVE_SINKS = new ThreadLocal<>();

    private GboardCustomSymbolHistoryRedirect() {}

    static boolean write(Object callbackCase, boolean custom, Object expectedSink,
            Object actualSink, Runnable persist) {
        if (!(callbackCase instanceof Integer)
                || ((Integer) callbackCase).intValue() != 7 || !custom
                || expectedSink == null || expectedSink != actualSink) {
            return false;
        }
        Set<Object> active = ACTIVE_SINKS.get();
        if (active == null) {
            active = Collections.newSetFromMap(new IdentityHashMap<>());
            ACTIVE_SINKS.set(active);
        }
        if (!active.add(actualSink)) {
            return true;
        }
        try {
            persist.run();
            return true;
        } finally {
            active.remove(actualSink);
            if (active.isEmpty()) {
                ACTIVE_SINKS.remove();
            }
        }
    }
}
