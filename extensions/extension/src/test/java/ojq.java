/** Mirrors the target's candidate-builder reflection contract. */
public final class ojq {
    public CharSequence a;
    public CharSequence b;
    public ojs e;
    public Object m;
    public Candidate a() { return new Candidate(a, e, m); }

    public static final class Candidate {
        public final CharSequence a;
        public final ojs e;
        public final Object m;
        Candidate(CharSequence text, ojs type, Object data) { a = text; e = type; m = data; }
    }
}
