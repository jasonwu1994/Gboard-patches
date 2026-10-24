import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class owd {
    public final int d;
    public int e;
    public final otk[] f;
    public final CharSequence[] g;
    public final int[] h;
    private final EnumMap<oth, otk> actions;

    public owd(int keyId) {
        this(keyId, new EnumMap<oth, otk>(oth.class), new CharSequence[0], new int[0]);
    }

    public owd(int keyId, Map<oth, otk> sourceActions) {
        this(keyId, sourceActions, new CharSequence[0], new int[0]);
    }

    public owd(int keyId, Map<oth, otk> sourceActions,
            CharSequence[] labels, int[] labelIds) {
        d = keyId;
        actions = new EnumMap<oth, otk>(oth.class);
        actions.putAll(sourceActions);
        f = actions.values().toArray(new otk[0]);
        g = labels.clone();
        h = labelIds.clone();
    }

    public otk a(oth actionType) {
        return h(actionType);
    }

    public otk h(oth actionType) {
        return actions.get(actionType);
    }

    public otk i(oth actionType) {
        otk exact = h(actionType);
        return exact != null ? exact : h(oth.PRESS);
    }

    public EnumMap<oth, otk> copyActions() {
        return new EnumMap<oth, otk>(actions);
    }

    public static owd toggle(int keyId, String label) {
        oti actionBuilder = new oti();
        actionBuilder.a = oth.SLIDE_UP;
        actionBuilder.c = new String[] {label};
        actionBuilder.q(-0x2719, ouc.COMMIT, "");
        EnumMap<oth, otk> actions = new EnumMap<oth, otk>(oth.class);
        actions.put(oth.SLIDE_UP, actionBuilder.c());
        return new owd(keyId, actions);
    }

    public static owd actionWithKeyCode(int keyId, int keyCode) {
        EnumMap<oth, otk> actions = new EnumMap<oth, otk>(oth.class);
        actions.put(oth.PRESS, new otk(
                oth.PRESS,
                new oud[] {new oud(keyCode, null, null)},
                new String[0]));
        return new owd(keyId, actions);
    }

    public static owd key(int keyId, String primaryLabel, String pressText,
            String[] longPressTokens, String nativeSlideUp, String nativeSlideDown) {
        EnumMap<oth, otk> actions = new EnumMap<oth, otk>(oth.class);
        putAction(actions, oth.PRESS, new String[] {pressText});
        putAction(actions, oth.LONG_PRESS, longPressTokens);
        putAction(actions, oth.SLIDE_UP,
                nativeSlideUp == null ? null : new String[] {nativeSlideUp});
        putAction(actions, oth.SLIDE_DOWN,
                nativeSlideDown == null ? null : new String[] {nativeSlideDown});
        CharSequence[] labels = primaryLabel == null
                ? new CharSequence[0] : new CharSequence[] {primaryLabel};
        int[] labelIds = primaryLabel == null ? new int[0] : new int[] {1};
        return new owd(keyId, actions, labels, labelIds);
    }

    private static void putAction(EnumMap<oth, otk> actions, oth type, String[] payloads) {
        if (payloads == null) {
            return;
        }
        oud[] entries = new oud[payloads.length];
        for (int index = 0; index < payloads.length; index++) {
            entries[index] = new oud(-0x2719, ouc.COMMIT, payloads[index]);
        }
        actions.put(type, new otk(type, entries, payloads.clone()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof owd)) {
            return false;
        }
        owd that = (owd) other;
        return d == that.d
                && Objects.equals(actions, that.actions)
                && java.util.Arrays.equals(g, that.g)
                && java.util.Arrays.equals(h, that.h);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(Integer.valueOf(d), actions);
        result = 31 * result + java.util.Arrays.hashCode(g);
        result = 31 * result + java.util.Arrays.hashCode(h);
        return result;
    }
}
