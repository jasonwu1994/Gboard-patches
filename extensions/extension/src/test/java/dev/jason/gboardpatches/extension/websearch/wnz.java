import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public abstract class wnz extends AbstractMap<String, wnz.Action> {
    public static wnz testSnapshot(Map<String, Action> source) { return new Snapshot(source); }

    public static final class Action {
        public final int code;
        public final Runnable payload;
        public Action(Runnable payload) { this.code = -0x9c47; this.payload = payload; }
    }

    private static final class Snapshot extends wnz {
        private final Map<String, Action> values;
        Snapshot(Map<String, Action> source) { values = Collections.unmodifiableMap(new LinkedHashMap<>(source)); }
        @Override public Set<Entry<String, Action>> entrySet() { return values.entrySet(); }
    }
}
