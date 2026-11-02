package dev.jason.gboardpatches.extension.clipboard;

import android.os.Looper;
import android.view.View;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardClipboardColumnLayoutsTest {
    @Test public void coldGridUpdatesWhenAsyncSettingsArriveWithoutASecondOpen() {
        Grid grid = new Grid();
        GboardClipboardColumnLayouts.watch(new Object(), grid, grid.manager, 2, grid, settings(false,2));
        flush(); Assert.assertEquals(2, grid.manager.count);
        GboardClipboardColumnLayouts.settingsPublished(settings(true,3));
        flush(); Assert.assertEquals(3, grid.manager.count);
        GboardClipboardColumnLayouts.settingsPublished(settings(true,1));
        flush(); Assert.assertEquals(1, grid.manager.count);
        GboardClipboardColumnLayouts.settingsPublished(settings(false,3));
        flush(); Assert.assertEquals(2, grid.manager.count);
    }
    @Test public void replacedAndDetachedLayoutsAreNotMutatedByDelayedSettings() {
        Grid grid = new Grid();
        Manager original = grid.manager;
        GboardClipboardColumnLayouts.watch(new Object(), grid, original, 2, grid, settings(false,2));
        grid.manager = new Manager();
        GboardClipboardColumnLayouts.settingsPublished(settings(true,3));
        flush(); Assert.assertEquals(2, original.count); Assert.assertEquals(2,grid.manager.count);
        GboardClipboardColumnLayouts.watch(new Object(), grid, grid.manager,2,grid,settings(false,2));
        grid.attached=false;
        GboardClipboardColumnLayouts.settingsPublished(settings(true,1));
        flush();Assert.assertEquals(2,grid.manager.count);
    }
    @Test public void externalStockSpanBecomesRestorationBaseline() {
        Grid grid=new Grid();
        GboardClipboardColumnLayouts.watch(new Object(),grid,grid.manager,2,grid,settings(true,3));
        flush();grid.manager.count=4;
        GboardClipboardColumnLayouts.settingsPublished(settings(true,1));flush();
        GboardClipboardColumnLayouts.settingsPublished(settings(false,2));flush();
        Assert.assertEquals(4,grid.manager.count);
    }
    @Test public void offArrivingBeforeFirstPostedApplyRestoresGetterOverride() {
        Grid grid=new Grid();grid.manager.count=3;
        GboardClipboardColumnLayouts.watch(new Object(),grid,grid.manager,2,grid,settings(true,3));
        GboardClipboardColumnLayouts.settingsPublished(settings(false,2));flush();
        Assert.assertEquals(2,grid.manager.count);
    }
    @Test public void realLocalPreferenceListenerUpdatesTheSameGridAndRestoresStock() throws Exception {
        android.content.Context context=RuntimeEnvironment.getApplication();
        android.content.SharedPreferences prefs=GboardClipboardSettings.preferences(context);
        prefs.edit().clear().putBoolean(GboardClipboardSettings.PREF_KEY_CLIPBOARD_ENABLED,true)
                .putString(GboardClipboardSettings.PREF_KEY_CLIPBOARD_COLUMN_COUNT,"2").commit();
        java.lang.reflect.Method observe=GboardClipboardGridRuntime.class.getDeclaredMethod("observe",android.content.Context.class);
        observe.setAccessible(true);observe.invoke(null,context);
        Grid grid=new Grid();Object keyboard=new Object();
        GboardClipboardColumnLayouts.watch(keyboard,grid,grid.manager,2,grid,settings(true,2));flush();
        for(int count:new int[]{1,2,3}) {
            prefs.edit().putString(GboardClipboardSettings.PREF_KEY_CLIPBOARD_COLUMN_COUNT,Integer.toString(count)).commit();
            flush();Assert.assertEquals(count,grid.manager.count);
        }
        prefs.edit().putBoolean(GboardClipboardSettings.PREF_KEY_CLIPBOARD_ENABLED,false).commit();
        flush();Assert.assertEquals(2,grid.manager.count);
        prefs.edit().clear().commit();
    }
    private static void flush(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static GboardClipboardColumnLayouts.Snapshot settings(boolean enabled,int count){
        return new GboardClipboardColumnLayouts.Snapshot(enabled,count);
    }
    static final class Manager {int count=2;}
    static final class Grid extends View implements GboardClipboardColumnLayouts.Access {
        Manager manager=new Manager(); boolean attached=true;
        Grid(){super(RuntimeEnvironment.getApplication());}
        @Override public boolean isAttachedToWindow(){return attached;}
        @Override public boolean post(Runnable callback){new android.os.Handler(Looper.getMainLooper()).post(callback);return true;}
        public Object manager(View view){return manager;}
        public int count(Object value){return ((Manager)value).count;}
        public void setCount(Object value,int count){((Manager)value).count=count;}
    }
}
