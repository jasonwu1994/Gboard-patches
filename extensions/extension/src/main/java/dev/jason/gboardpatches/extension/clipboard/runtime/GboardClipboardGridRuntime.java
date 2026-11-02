package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import java.util.Map;
import java.util.WeakHashMap;

final class GboardClipboardGridRuntime {
    private static final Map<Object,Integer> STOCK_COUNTS=new WeakHashMap<>();
    private static final GboardClipboardGridAccess ACCESS=new GboardClipboardGridAccess();
    private static SharedPreferences observed;
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER=(prefs,key)->publish(prefs);
    private GboardClipboardGridRuntime(){}
    static int resolve(Object keyboard,int stock,GboardClipboardRuntimeSupport support) {
        synchronized(STOCK_COUNTS){STOCK_COUNTS.put(keyboard,stock);}
        try {
            Context context=support.runtimeContext(keyboard);
            if(context==null)return stock;
            support.registerApplicationContext(context);
            SharedPreferences prefs=observe(context);
            return GboardClipboardSettings.readClipboardEnabled(prefs)
                    ?GboardClipboardSettings.readClipboardColumnCount(prefs):stock;
        } catch(Throwable unavailable){return stock;}
    }
    static void afterLayout(Object keyboard,GboardClipboardRuntimeSupport support) {
        try {
            Context context=support.runtimeContext(keyboard);
            if(context==null)return;
            SharedPreferences prefs=observe(context);
            View recycler=ACCESS.recycler(keyboard);
            if(recycler==null)return;
            Object manager=ACCESS.manager(recycler);
            if(manager==null || !ACCESS.isManager(manager))return;
            Integer stock;synchronized(STOCK_COUNTS){stock=STOCK_COUNTS.get(keyboard);}
            if(stock==null)return;
            GboardClipboardColumnLayouts.watch(keyboard,recycler,manager,stock,ACCESS,snapshot(prefs));
        } catch(Throwable unavailable) { /* The stock grid remains owned by Gboard. */ }
    }
    private static synchronized SharedPreferences observe(Context context) {
        SharedPreferences prefs=context.getSharedPreferences(GboardClipboardSettings.PREF_FILE,Context.MODE_PRIVATE);
        if(observed!=prefs){
            if(observed!=null)observed.unregisterOnSharedPreferenceChangeListener(LISTENER);
            observed=prefs;prefs.registerOnSharedPreferenceChangeListener(LISTENER);
        }
        return prefs;
    }
    private static void publish(SharedPreferences prefs) {
        try { GboardClipboardColumnLayouts.settingsPublished(snapshot(prefs)); }
        catch(Throwable unavailable) { }
    }
    private static GboardClipboardColumnLayouts.Snapshot snapshot(SharedPreferences prefs) {
        return new GboardClipboardColumnLayouts.Snapshot(
                GboardClipboardSettings.readClipboardEnabled(prefs),
                GboardClipboardSettings.readClipboardColumnCount(prefs));
    }
}
