import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class wpa extends AbstractSet<String> {
    public static wpa n(Collection<?> source) {
        if (source instanceof wpa catalog) return catalog;
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object value : source) values.add((String) value);
        return new Catalog(values);
    }

    private static final class Catalog extends wpa {
        private final Set<String> values;
        Catalog(Set<String> values) { this.values = Collections.unmodifiableSet(values); }
        @Override public Iterator<String> iterator() { return values.iterator(); }
        @Override public int size() { return values.size(); }
    }
}
