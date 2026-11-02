import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class fpn {
    public final fpo a;
    fpn(fpo owner) { a = owner; }
    public Object call() { return null; }
}

class fpo {
    public final Context b;
    fpo(Context context) { b = context; }
}

class fqx {
    static long primaryTimestamp;
    static long cleanupFailureId = Long.MIN_VALUE;
    static final List<String> events = new ArrayList<>();
    public final Context c;
    public final boolean f;
    public final AtomicInteger g;

    fqx(Context context, boolean disabled, int count) {
        c = context;
        f = disabled;
        g = new AtomicInteger(count);
    }

    public static long a(Context context) { return primaryTimestamp; }

    private Cursor s(Uri uri, int itemType) {
        return c.getContentResolver().query(uri, null, "item_type = ?",
                new String[] { Integer.toString(itemType) }, "timestamp DESC");
    }

    private void t(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        if (id == cleanupFailureId) {
            events.add("cleanup-failed:" + id);
            throw new IllegalStateException("cleanup failed for " + id);
        }
        events.add("cleanup:" + id);
    }

    public void g() {}
}

class jt {
    public final List<Integer> removed = new ArrayList<>();
    public void n(int position) { removed.add(Integer.valueOf(position)); }
}

class fqn extends jt {
    public final Context e;
    public final List<Object> n;
    public int o;
    public int z;
    public int refreshCount;

    fqn(Context context, List<Object> items, int recentCount, int pinnedVisibleCount) {
        e = context;
        n = items;
        o = recentCount;
        z = pinnedVisibleCount;
    }

    public void F() {}
    public void p(kr holder, int position) {}
    private void R() { refreshCount++; }
}

class kr {}

class fql extends kr {
    public final TextView t;
    fql(TextView textView) { t = textView; }
}

class fpm {
    public String a;
    public final int c;
    public final boolean h;

    fpm(int itemType) {
        this(itemType, false);
    }

    fpm(int itemType, boolean sensitive) {
        a = "";
        c = itemType;
        h = sensitive;
    }
}

class fpk {
    public static final fpk a = new fpk(-1L, new j$.time.Instant(0L), 0, false, false);
    public static final fpk b = new fpk(-2L, new j$.time.Instant(0L), 0, false, false);
    public static final fpk c = new fpk(-3L, new j$.time.Instant(0L), 2, false, true);

    public final long d;
    public final j$.time.Instant e;
    public final fpm g;
    private final boolean pinned;
    private final boolean special;

    fpk(long id, j$.time.Instant timestamp, int itemType, boolean isPinned,
            boolean isSpecial) {
        this(id, timestamp, itemType, isPinned, isSpecial, false);
    }

    fpk(long id, j$.time.Instant timestamp, int itemType, boolean isPinned,
            boolean isSpecial, boolean sensitive) {
        d = id;
        e = timestamp;
        g = new fpm(itemType, sensitive);
        pinned = isPinned;
        special = isSpecial;
    }

    public boolean m() { return g.h; }
    public boolean n() { return pinned; }
    public boolean o() { return special; }
}

class fqr {
    static final List<Object> items = new ArrayList<>();
    static final List<String> events = fqx.events;
    static final Uri URI = Uri.parse("content://clipboard1803/items");

    public static Uri d(Context context, int type, long id) { return URI; }

    public static List<Object> j(Context context, String selection, String[] selectionArgs,
            String sortOrder) {
        long timestamp = selectionArgs == null || selectionArgs.length == 0
                ? Long.MIN_VALUE : Long.parseLong(selectionArgs[0]);
        List<Object> result = new ArrayList<>();
        for (Object item : items) {
            fpk clip = (fpk) item;
            long value = clip.e.toEpochMilli();
            if ("timestamp = ?".equals(selection)) {
                if (value == timestamp) result.add(item);
            } else if (clip.n() || clip.o() || value >= timestamp) {
                result.add(item);
            }
        }
        result.sort(Comparator.comparingLong((Object value) ->
                ((fpk) value).e.toEpochMilli()).reversed());
        return result;
    }

    public static void h(Context context, List<Long> ids) {
        for (Long id : ids) {
            events.add("delete:" + id);
            context.getContentResolver().delete(URI, "_id = ?",
                    new String[] { Long.toString(id.longValue()) });
        }
    }
}

class cht {
    static long lastVisibleTimestamp;
    public long m(int key, long defaultValue) { return lastVisibleTimestamp; }
    public void s(int key, long value) { lastVisibleTimestamp = value; }
}

class qvt extends cht {
    public static qvt I(Context context) { return new qvt(); }
}

class AbstractKeyboard1803Stub extends com.google.android.libraries.inputmethod.keyboard.impl.AbstractKeyboard {
    public final Object y;
    AbstractKeyboard1803Stub(Context context, Object shadow) {
        super(context);
        y = shadow;
    }
}

class ClipboardKeyboard1803Stub extends AbstractKeyboard1803Stub {
    ClipboardKeyboard1803Stub(Context context, Object shadow) {
        super(context, shadow);
    }
}
