import android.content.Context;
import android.view.inputmethod.EditorInfo;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class gyo {
    public final Context c;
    gyo(Context context) { c=context; }
    public wns a(EditorInfo editorInfo, boolean incognito) { return new wns(Collections.emptyList()); }
}
final class ohn {
    public final qch c;
    ohn(qch keyboardType) { c=keyboardType; }
}
final class qch {
    public final String m;
    qch(String name) { m=name; }
}
final class wns extends AbstractList<Object> {
    static int copyCalls;
    private final List<Object> values;
    wns(Collection<?> input) { values=Collections.unmodifiableList(new ArrayList<Object>(input)); }
    public Object get(int index) { return values.get(index); }
    public int size() { return values.size(); }
    public static wns o(Collection<?> values) { copyCalls++; return new wns(values); }
}
