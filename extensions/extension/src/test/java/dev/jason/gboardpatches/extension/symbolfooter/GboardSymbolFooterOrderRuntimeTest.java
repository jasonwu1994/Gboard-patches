package dev.jason.gboardpatches.extension.symbolfooter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsProvider;

@RunWith(RobolectricTestRunner.class)
public final class GboardSymbolFooterOrderRuntimeTest {
    private Context context;
    private OrderProvider provider;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        provider = new OrderProvider();
        provider.onCreate();
        ShadowContentResolver.registerProviderInternal(
                context.getPackageName() + GboardPatchesSettingsProvider.AUTHORITY_SUFFIX,
                provider);
        GboardSymbolFooterOrderRuntime.invalidateCachedSettings();
        copyCallsField().setInt(null, 0);
    }

    @Test
    public void exact1803MappingsReorderThroughVvwCopyAndPreserveItemIdentity() throws Exception {
        provider.order = new ArrayList<String>(Arrays.asList(
                "jasondev_symbol", "emoticon", "smiley",
                "gif_search_result", "sticker_search_result"));
        Object receiver = construct("gyo", new Class<?>[] { Context.class }, context);
        Object futureOne = item("future_one");
        Object emoji = item("smiley");
        Object custom = item("jasondev_symbol");
        Object futureTwo = item("future_two");
        Object emoticon = item("emoticon");
        Object source = list(futureOne, emoji, custom, futureTwo, emoticon);

        Object reordered = GboardSymbolFooterOrderRuntime.reorderExpressionCorpusList(
                receiver, source);

        Assert.assertNotSame(source, reordered);
        Assert.assertEquals("wns", reordered.getClass().getName());
        Assert.assertEquals(1, copyCallsField().getInt(null));
        Assert.assertEquals(Arrays.asList(custom, emoticon, emoji, futureOne, futureTwo),
                new ArrayList<Object>((Collection<?>) reordered));
        Assert.assertEquals(1, provider.callCount);
    }

    @Test
    public void unchangedTargetOrderRetainsOriginalVvwIdentityWithoutRebuild() throws Exception {
        provider.order = new ArrayList<String>(
                GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER);
        Object source = list(
                item("smiley"),
                item("gif_search_result"),
                item("sticker_search_result"),
                item("emoticon"),
                item("jasondev_symbol"),
                item("future_one"));

        Object result = GboardSymbolFooterOrderRuntime.reorderExpressionCorpusList(
                construct("gyo", new Class<?>[] { Context.class }, context), source);

        Assert.assertSame(source, result);
        Assert.assertEquals(0, copyCallsField().getInt(null));
    }

    @Test
    public void missingKnownAndHiddenCustomStayAbsentWhileUnknownTailIsStable() throws Exception {
        provider.order = new ArrayList<String>(Arrays.asList(
                "emoticon", "jasondev_symbol", "smiley"));
        Object futureOne = item("future_one");
        Object sticker = item("sticker_search_result");
        Object emoji = item("smiley");
        Object futureTwo = item("future_two");
        Object emoticon = item("emoticon");
        Object gif = item("gif_search_result");
        Object source = list(futureOne, sticker, emoji, futureTwo, emoticon, gif);

        Object result = GboardSymbolFooterOrderRuntime.reorderExpressionCorpusList(
                construct("gyo", new Class<?>[] { Context.class }, context), source);

        Assert.assertEquals(Arrays.asList(
                        emoticon, emoji, gif, sticker, futureOne, futureTwo),
                new ArrayList<Object>((Collection<?>) result));
        Assert.assertEquals(6, ((Collection<?>) result).size());
    }

    @Test
    public void malformedAndThrowingProviderFailClosedToExactPublicDefault() throws Exception {
        Object emoticon = item("emoticon");
        Object emoji = item("smiley");
        Object gif = item("gif_search_result");
        Object source = list(emoticon, emoji, gif);
        Object receiver = construct("gyo", new Class<?>[] { Context.class }, context);

        provider.order = new ArrayList<String>(Arrays.asList("future_one", "emoticon"));
        Object malformed = GboardSymbolFooterOrderRuntime.reorderExpressionCorpusList(
                receiver, source);
        Assert.assertEquals(Arrays.asList(emoji, gif, emoticon),
                new ArrayList<Object>((Collection<?>) malformed));

        GboardSymbolFooterOrderRuntime.invalidateCachedSettings();
        provider.throwOnCall = true;
        Object throwing = GboardSymbolFooterOrderRuntime.reorderExpressionCorpusList(
                receiver, source);
        Assert.assertEquals(Arrays.asList(emoji, gif, emoticon),
                new ArrayList<Object>((Collection<?>) throwing));
    }

    @Test
    public void targetClassLoaderBindingsAreIsolatedAndMissingTargetsFailBoundedly() throws Exception {
        TargetClassLoader loader = new TargetClassLoader(getClass().getClassLoader());
        Class<?> isolatedGri = loader.loadClass("gyo");
        Class<?> isolatedPpa = loader.loadClass("qch");
        Class<?> isolatedNva = loader.loadClass("ohn");
        Class<?> isolatedVvw = loader.loadClass("wns");
        Object receiver = declaredConstructor(isolatedGri, Context.class).newInstance(context);
        Object emojiType = declaredConstructor(isolatedPpa, String.class).newInstance("smiley");
        Object emoticonType = declaredConstructor(isolatedPpa, String.class).newInstance("emoticon");
        Object emoji = declaredConstructor(isolatedNva, isolatedPpa).newInstance(emojiType);
        Object emoticon = declaredConstructor(isolatedNva, isolatedPpa).newInstance(emoticonType);
        Object source = declaredConstructor(isolatedVvw, Collection.class)
                .newInstance(Arrays.asList(emoticon, emoji));
        provider.order = new ArrayList<String>(
                GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER);

        Object result = GboardSymbolFooterOrderRuntime.reorderExpressionCorpusList(
                receiver, source);

        Assert.assertSame(loader, result.getClass().getClassLoader());
        Assert.assertEquals(Arrays.asList(emoji, emoticon),
                new ArrayList<Object>((Collection<?>) result));

        Object ordinary = new Object();
        Assert.assertSame(source,
                GboardSymbolFooterOrderRuntime.reorderExpressionCorpusList(ordinary, source));
    }

    @Test
    public void settingsCacheFreshnessIsBoundedToOneSecond() throws Exception {
        Method method = GboardSymbolFooterOrderRuntime.class.getDeclaredMethod(
                "isSettingsCacheFresh", long.class, long.class);
        method.setAccessible(true);

        Assert.assertEquals(Boolean.TRUE, method.invoke(null, 1_000L, 2_000L));
        Assert.assertEquals(Boolean.FALSE, method.invoke(null, 1_000L, 2_001L));
    }

    private Object item(String typeName) throws Exception {
        Object keyboardType = construct("qch", new Class<?>[] { String.class }, typeName);
        return construct("ohn", new Class<?>[] { Class.forName("qch") }, keyboardType);
    }

    private Object list(Object... items) throws Exception {
        return construct("wns", new Class<?>[] { Collection.class }, Arrays.asList(items));
    }

    private Object construct(String name, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        return declaredConstructor(Class.forName(name), parameterTypes).newInstance(arguments);
    }

    private Constructor<?> declaredConstructor(Class<?> owner, Class<?>... parameterTypes)
            throws Exception {
        Constructor<?> constructor = owner.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor;
    }

    private Field copyCallsField() throws Exception {
        Field field = Class.forName("wns").getDeclaredField("copyCalls");
        field.setAccessible(true);
        return field;
    }

    private static final class TargetClassLoader extends ClassLoader {
        private static final Set<String> TARGETS = new HashSet<String>(
                Arrays.asList("gyo", "ohn", "qch", "wns"));

        TargetClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (this) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && TARGETS.contains(name)) {
                    loaded = defineTarget(name);
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private Class<?> defineTarget(String name) throws ClassNotFoundException {
            try (InputStream input = getParent().getResourceAsStream(name + ".class")) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (ClassNotFoundException missing) {
                throw missing;
            } catch (Exception failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }
    }

    private static final class OrderProvider extends ContentProvider {
        ArrayList<String> order;
        boolean throwOnCall;
        int callCount;

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            callCount++;
            if (throwOnCall) {
                throw new IllegalStateException("provider failure");
            }
            Bundle result = new Bundle();
            if (order != null) {
                result.putStringArrayList(
                        GboardPatchesSettingsProvider.BUNDLE_KEY_SYMBOL_FOOTER_ORDER,
                        new ArrayList<String>(order));
            }
            return result;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                String[] selectionArgs) { return 0; }
    }
}
