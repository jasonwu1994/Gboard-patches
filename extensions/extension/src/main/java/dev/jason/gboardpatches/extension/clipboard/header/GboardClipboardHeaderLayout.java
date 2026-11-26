package dev.jason.gboardpatches.extension.clipboard.header;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Public-API view lookups for the clipboard header; callers supply the reviewed names/types. */
public final class GboardClipboardHeaderLayout {
    private GboardClipboardHeaderLayout() { }

    /**
     * Resolves an id by resource entry name inside the package that owns {@code root}'s own id.
     * Repackaged Gboard builds keep their resource table package, so no package name is assumed.
     */
    public static int idByName(View root, String name) {
        int ownId = root.getId();
        if (ownId == View.NO_ID || (ownId >>> 24) == 0) return 0;
        Resources resources = root.getResources();
        try {
            String owner = resources.getResourcePackageName(ownId);
            return resources.getIdentifier(name, "id", owner);
        } catch (Resources.NotFoundException missing) {
            return 0;
        }
    }

    /**
     * First TextView in layout order outside {@code excluded} and outside any view of
     * {@code opaque} type (native soft keys own their own labels). Null when absent.
     */
    public static TextView findTitle(View root, View excluded, Class<?> opaque) {
        if (root == excluded || (opaque != null && opaque.isInstance(root))) return null;
        if (root instanceof TextView text) return text;
        if (!(root instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findTitle(group.getChildAt(i), excluded, opaque);
            if (found != null) return found;
        }
        return null;
    }
}
