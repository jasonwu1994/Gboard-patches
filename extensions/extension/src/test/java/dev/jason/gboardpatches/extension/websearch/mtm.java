import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class mtm {
    public String d;
    public wnz e;
    private String q;
    private int r;
    private final Map<String, wnz.Action> testActions = new LinkedHashMap<>();

    public mtm() {}
    public final void l(String token) { q = Objects.requireNonNull(token); }
    public final void i(int icon) { r = icon; }
    public final void q(Runnable callback) { testActions.put("PRESS", new wnz.Action(callback)); }
    public final mtr a() {
        e = wnz.testSnapshot(testActions);
        return new mtr(Objects.requireNonNull(q), r, d, e);
    }
}
