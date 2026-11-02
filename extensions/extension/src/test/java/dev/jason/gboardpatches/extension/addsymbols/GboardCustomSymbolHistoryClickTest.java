package dev.jason.gboardpatches.extension.addsymbols;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public final class GboardCustomSymbolHistoryClickTest {
    private Context context;
    @Before public void prepare() throws Exception {
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("gboardpatches_gboard_symbol_recents",0).edit().clear().commit();
        renderModes().clear();
    }
    @After public void cleanup() throws Exception { renderModes().clear(); }

    @Test public void customCellCommitsAndPersistsOnlyOwnHistoryAndRecentIsClickable() throws Exception {
        Keyboard keyboard=new Keyboard(context);renderModes().put(keyboard,true);
        Click consumer=new Click(keyboard,7);
        cell(consumer,"♞").performClick();
        assertEquals("♞",keyboard.committed.toString());
        assertEquals("♞",recents());assertEquals(0,keyboard.b.writes);
        cell(consumer,recents()).performClick();
        assertEquals("♞♞",keyboard.committed.toString());
        assertEquals("♞",recents());assertEquals(0,keyboard.b.writes);
    }
    @Test public void officialCellDoesNotEnterCustomBucket() throws Exception {
        Keyboard keyboard=new Keyboard(context);
        cell(new Click(keyboard,7),"stock").performClick();
        assertEquals("stock",keyboard.committed.toString());
        assertEquals(1,keyboard.b.writes);assertEquals("",recents());
    }
    @Test public void wrongSinkRemainsOfficialEvenInsideCustomCallback() throws Exception {
        Keyboard keyboard=new Keyboard(context);renderModes().put(keyboard,true);
        History other=new History();
        assertFalse(intercept(new Click(keyboard,7),other,"wrong"));
        assertEquals("",recents());assertEquals(0,keyboard.b.writes);
    }
    @Test public void nestedStockAndFooterCannotEraseOrInheritOuterOwnership() throws Exception {
        Keyboard custom=new Keyboard(context);Keyboard stock=new Keyboard(context);
        renderModes().put(custom,true);
        Click outer=new Click(custom,7);
        outer.beforeWrite=()->{
            new Click(stock,7).accept("official");
            new Click(stock,8).accept(new Event());
        };
        cell(outer,"custom").performClick();
        assertEquals("custom",recents());assertEquals(0,custom.b.writes);
        assertEquals(1,stock.b.writes);assertEquals(1,stock.footerEvents);
    }
    @Test public void footerCaseEightRejectsStringCellContract() throws Exception {
        Keyboard custom=new Keyboard(context);renderModes().put(custom,true);
        assertThrows(ClassCastException.class,()->new Click(custom,8).accept("glyph"));
        assertEquals("",recents());assertEquals(0,custom.b.writes);
    }
    @Test public void throwingNestedCallbackCannotAffectLaterCustomClick() throws Exception {
        Keyboard custom=new Keyboard(context);renderModes().put(custom,true);
        Click broken=new Click(custom,7);broken.beforeWrite=()->{throw new IllegalStateException("fixture");};
        assertThrows(IllegalStateException.class,()->broken.accept("first"));
        cell(new Click(custom,7),"second").performClick();
        assertEquals("second",recents());assertEquals(0,custom.b.writes);
    }
    @Test public void persistedHistoryReopensWithNewKeyboardAndDoesNotFallBackToOfficial() throws Exception {
        Keyboard first=new Keyboard(context);renderModes().put(first,true);
        cell(new Click(first,7),"one").performClick();
        cell(new Click(first,7),"two").performClick();
        assertEquals("two\u0001one",recents());
        renderModes().clear();Keyboard reopened=new Keyboard(context);renderModes().put(reopened,true);
        cell(new Click(reopened,7),recents().split("\u0001")[1]).performClick();
        assertEquals("one\u0001two",recents());assertEquals(0,reopened.b.writes);
    }
    private View cell(Consumer<Object> consumer,String glyph){View cell=new View(context);cell.setOnClickListener(v->consumer.accept(glyph));return cell;}
    private String recents(){return context.getSharedPreferences("gboardpatches_gboard_symbol_recents",0).getString("jasondev_symbol_recents","");}
    @SuppressWarnings("unchecked") private static Map<Object,Boolean> renderModes() throws Exception {
        Field field=GboardAddSymbolsRuntime.class.getDeclaredField("CURRENT_CUSTOM_EMOTICON_RENDER_MODE");field.setAccessible(true);return (Map<Object,Boolean>)field.get(null);
    }
    private static boolean intercept(Click consumer,History sink,String glyph) throws Exception {
        return GboardAddSymbolsRuntime.interceptHistoryWriteForConsumer(consumer,sink,glyph,Click.class,
                field(Click.class,"$cid"),field(Click.class,"a"),field(Keyboard.class,"z"),field(Keyboard.class,"b"));
    }
    private static Field field(Class<?> owner,String name) throws Exception {Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
    private static final class History { int writes;void b(String text){writes++;} }
    private static final class Event { }
    private static final class Keyboard {final Context z;final History b=new History();final StringBuilder committed=new StringBuilder();int footerEvents;Keyboard(Context context){z=context;} }
    private static final class Click implements Consumer<Object> {
        final Object a;final int $cid;Runnable beforeWrite;
        Click(Keyboard keyboard,int cid){a=keyboard;$cid=cid;}
        public void accept(Object value){
            Keyboard keyboard=(Keyboard)a;
            if($cid==8){Event event=(Event)value;keyboard.footerEvents++;return;}
            if($cid!=7)return;
            String text=(String)value;
            if(beforeWrite!=null)beforeWrite.run();
            try {if(!intercept(this,keyboard.b,text))keyboard.b.b(text);}catch(RuntimeException e){throw e;}catch(Exception e){throw new AssertionError(e);}
            keyboard.committed.append(text);
        }
    }
}
