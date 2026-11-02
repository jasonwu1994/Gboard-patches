package dev.jason.gboardpatches.extension.zhuyinslide;

import android.content.Context;
import java.util.*;

public final class GboardInputModel {
    public enum ActionType { PRESS, LONG_PRESS, SLIDE_UP, SLIDE_DOWN, DOWN, UP }
    public enum Intention { DECODE, COMMIT }
    public static final class Wns<E> extends AbstractList<E> {
        private final List<E> values;
        public Wns(Collection<? extends E> values) { this.values = Collections.unmodifiableList(new ArrayList<>(values)); }
        @SuppressWarnings("unchecked") public static <T> Wns<T> o(Collection<? extends T> values) { return values instanceof Wns<?> ? (Wns<T>)values : new Wns<>(values); }
        public E get(int index) { return values.get(index); }
        public int size() { return values.size(); }
    }
    public static final class Entry {
        public int c; public Intention d; public Object e; public int f;
        public Entry(int code, Intention intention, Object payload, int hash) { c=code;d=intention;e=payload;f=hash; }
        @Override public boolean equals(Object value) { if(!(value instanceof Entry))return false;Entry that=(Entry)value;return c==that.c&&d==that.d&&Objects.equals(e,that.e); }
        @Override public int hashCode() { return Objects.hash(c,d,e); }
    }
    public static final class Action {
        public ActionType e; public Wns<Entry> f; public int i; public Wns<String> p; public int[] q;
        public Entry b() { return f.isEmpty()?null:f.get(0); }
        @Override public boolean equals(Object value) { if(!(value instanceof Action))return false;Action that=(Action)value;return e==that.e&&i==that.i&&Objects.equals(f,that.f)&&Objects.equals(p,that.p)&&Arrays.equals(q,that.q); }
        @Override public int hashCode() { return 31*Objects.hash(e,f,i,p)+Arrays.hashCode(q); }
    }
    public static final class ActionBuilder {
        public ActionType a; public Wns<Entry> b=Wns.o(Collections.emptyList());
        public Wns<String> c=Wns.o(Collections.emptyList()); public int[] d=new int[0]; public int g;
        public void n(Entry[] values) { b=Wns.o(Arrays.asList(values)); }
        public void t(String[] values) { c=Wns.o(Arrays.asList(values)); }
        public void s(int code, Intention intention, Object value) { n(new Entry[]{new Entry(code,intention,value,Integer.MAX_VALUE)}); }
        public void i(Action original) { a=original.e;b=Wns.o(original.f);c=Wns.o(original.p);d=original.q.clone();g=original.i; }
        public Action c() { Action action=new Action();action.e=a;action.f=Wns.o(b);action.p=Wns.o(c);action.q=d.clone();action.i=g;return action; }
    }
    public static final class Metadata {
        public int f; public Wns<Action> h=Wns.o(Collections.emptyList());
        public Wns<CharSequence> i=Wns.o(Collections.emptyList()); public int[] j=new int[0];
        public Metadata() {}
        public Metadata(int id) { f=id; }
        @Override public boolean equals(Object value) { if(!(value instanceof Metadata))return false;Metadata that=(Metadata)value;return f==that.f&&Objects.equals(h,that.h)&&Objects.equals(i,that.i)&&Arrays.equals(j,that.j); }
        @Override public int hashCode() { return 31*Objects.hash(f,h,i)+Arrays.hashCode(j); }
        public static Metadata toggle(int id,String label) { MetadataBuilder builder=new MetadataBuilder();builder.a=id;add(builder,ActionType.SLIDE_UP,"",new String[]{label});return builder.b(); }
        public Action g(ActionType type) { for(Action action:h) if(action.e==type)return action;return null; }
        public Action h(ActionType type) { Action exact=g(type);return exact!=null?exact:g(ActionType.PRESS); }
        public static Metadata actionWithKeyCode(int id,int code) { MetadataBuilder builder=new MetadataBuilder();builder.a=id;ActionBuilder action=new ActionBuilder();action.a=ActionType.PRESS;action.s(code,Intention.COMMIT,null);builder.t(action.c());return builder.b(); }
        public static Metadata key(int id,String label,String press,String[] tokens,String up,String down) {
            MetadataBuilder builder=new MetadataBuilder();builder.a=id;builder.w(new int[]{0x7f0b05f5},Arrays.asList(label));
            add(builder,ActionType.PRESS,press,new String[]{press});
            if(tokens!=null && tokens.length>0){ActionBuilder a=new ActionBuilder();a.a=ActionType.LONG_PRESS;a.g=42;Entry[] entries=new Entry[tokens.length];for(int n=0;n<tokens.length;n++)entries[n]=new Entry(-0x2719,Intention.COMMIT,tokens[n],Integer.MAX_VALUE);a.n(entries);a.t(tokens);builder.t(a.c());}
            if(up!=null)add(builder,ActionType.SLIDE_UP,up,new String[]{up});
            if(down!=null)add(builder,ActionType.SLIDE_DOWN,down,new String[]{down});
            return builder.b();
        }
        private static void add(MetadataBuilder builder,ActionType type,String text,String[] labels){ActionBuilder a=new ActionBuilder();a.a=type;a.s(-0x2719,Intention.COMMIT,text);a.t(labels);builder.t(a.c());}
    }
    public static final class MetadataBuilder {
        public int a; public final EnumMap<ActionType,Action> actions=new EnumMap<>(ActionType.class);
        public int[] ids=new int[0];public List<CharSequence> labels=new ArrayList<>();
        public void k(Metadata metadata){a=metadata.f;actions.clear();for(Action action:metadata.h)actions.put(action.e,action);ids=metadata.j.clone();labels=new ArrayList<>(metadata.i);}
        public void t(Action action){actions.put(action.e,action);}
        public void w(int[] values,List<CharSequence> texts){ids=values.clone();labels=new ArrayList<>(texts);}
        public Metadata b(){Metadata result=new Metadata();result.f=a;result.h=Wns.o(actions.values());result.i=Wns.o(labels);result.j=ids.clone();return result;}
    }
    public static class BasePreferences { public boolean enabled; public boolean x(int id,boolean fallback){return enabled;} }
    public static final class Preferences extends BasePreferences {
        private static final Preferences VALUE=new Preferences();
        public static Preferences I(Context context){return VALUE;}
        public static Preferences instance(){return VALUE;}
        public boolean throwOnWrite;public int writeAttempts;
        public static void reset(){VALUE.enabled=false;VALUE.throwOnWrite=false;VALUE.writeAttempts=0;}
        public void setSimplifiedEnabled(boolean enabled){this.enabled=enabled;}
        public boolean isSimplifiedEnabled(){return enabled;}
        public void setThrowOnWrite(boolean value){throwOnWrite=value;}
        public int getWriteAttempts(){return writeAttempts;}
        public void f(String key,boolean enabled){writeAttempts++;if(throwOnWrite)throw new IllegalStateException("fixture");this.enabled=enabled;}
    }
    public static final class Dispatcher {
        public Context b;public Preferences c;public Dispatcher(Context context,Preferences preferences){b=context;c=preferences;}
    }
}
