package dev.jason.gboardpatches.extension.clipboard;

import android.view.View;
public class GboardClipboardGridAccess implements GboardClipboardColumnLayouts.Access {
    public View recycler(Object keyboard){return null;}
    public Object manager(View recycler){return null;}
    public int count(Object manager){return 0;}
    public void setCount(Object manager,int count){}
    public boolean isManager(Object manager){return false;}
}
