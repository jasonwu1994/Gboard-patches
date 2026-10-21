import java.util.EnumMap;

public final class ovv {
    private int keyId;
    public int o;
    private EnumMap<oth, otk> actions = new EnumMap<oth, otk>(oth.class);
    private CharSequence[] labels = new CharSequence[0];
    private int[] labelIds = new int[0];

    public ovv() {
    }

    public void j(owd metadata) {
        keyId = metadata.d;
        o = metadata.e;
        actions = metadata.copyActions();
        labels = metadata.g.clone();
        labelIds = metadata.h.clone();
    }

    public void q(otk actionDef) {
        actions.put(actionDef.c, actionDef);
    }

    public void t(otk actionDef) {
        actions.put(actionDef.c, actionDef);
    }

    public void f(int id, CharSequence text) {
        labelIds = new int[] {id};
        labels = new CharSequence[] {text};
    }

    public void t(int[] ids, CharSequence[] texts) {
        labelIds = ids.clone();
        labels = texts.clone();
    }

    public void v(int[] ids, CharSequence[] texts) {
        labelIds = ids.clone();
        labels = texts.clone();
    }

    public Object d() {
        owd metadata = new owd(keyId, actions, labels, labelIds);
        metadata.e = o;
        return metadata;
    }
}
