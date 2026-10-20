package dev.jason.gboardpatches.extension.zhuyintoggle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;

/** Bindings verified against Gboard 18.0.3's Chinese HMM decoder. */
public final class GboardZhuyinCandidateConversionRuntime {
    private static final String TAG = "ZhuyinConversion";
    private static final String TAIWAN_DECODER =
            "com.google.android.apps.inputmethod.latin.hmm.LatinTaiwanZhuyinDecodeProcessor";
    private static final String CANTONESE_DECODER_PREFIX =
            "com.google.android.apps.inputmethod.latin.hmm.LatinCantonese";
    private static final String CHINESE_DECODER =
            "com.google.android.apps.inputmethod.libs.chinese.ime.hmm.AbstractHmmChineseDecodeProcessor";
    private static volatile Session active;

    private GboardZhuyinCandidateConversionRuntime() {}

    // Called on the decoder's own thread, at session start and before candidate publication.
    public static void onDecoderReady(Object decoder) {
        if (decoder == null) return;
        String name = decoder.getClass().getName();
        if (!TAIWAN_DECODER.equals(name) && !isCantoneseDecoder(name)) return;
        try {
            Context context = (Context) decoder.getClass().getField("u").get(decoder);
            if (context == null) return;
            Session session = active;
            if (session == null || session.decoder.get() != decoder) {
                Looper looper = Looper.myLooper();
                if (looper == null) {
                    Log.e(TAG, "Decoder thread has no Looper; live conversion unavailable");
                    return;
                }
                session = new Session(decoder, new Handler(looper));
                active = session;
                Log.i(TAG, "Decoder attached: " + name + " on " + Thread.currentThread().getName());
                GboardZhuyinTraditionalSimplifiedToggleRuntime.onCandidateButtonSettingChanged();
            }
            if (GboardZhuyinSimplifiedCandidateButtonSettings.readEnabled(context)) {
                session.updateConverter(decoder, context);
            }
        } catch (Throwable error) {
            Log.e(TAG, "Cannot attach/update Zhuyin decoder", error);
        }
    }

    private static boolean isCantoneseDecoder(String name) {
        return (CANTONESE_DECODER_PREFIX + "DecodeProcessor").equals(name)
                || (CANTONESE_DECODER_PREFIX + "CangjieDecodeProcessor").equals(name)
                || (CANTONESE_DECODER_PREFIX + "StrokeDecodeProcessor").equals(name);
    }

    static boolean isCantoneseActive() {
        Session session = active;
        Object decoder = session == null ? null : session.decoder.get();
        return decoder != null && isCantoneseDecoder(decoder.getClass().getName());
    }

    public static void onDecoderFinished(Object decoder) {
        Session session = active;
        if (session != null && session.decoder.get() == decoder) active = null;
    }

    static void refreshCandidates() {
        final Session session = active;
        if (session == null) {
            Log.e(TAG, "Toggle received without an attached Zhuyin decoder");
            return;
        }
        if (!session.handler.post(new Runnable() {
            @Override public void run() {
                Object decoder = session.decoder.get();
                if (active != session || decoder == null) return;
                try {
                    Context context = (Context) decoder.getClass().getField("u").get(decoder);
                    if (!GboardZhuyinSimplifiedCandidateButtonSettings.readEnabled(context)) return;
                    session.updateConverter(decoder, context);
                    // Use the same iterator publication as native ak(J), without setting
                    // SINGLE_CHARACTER_CANDIDATE or restarting the composing session.
                    session.singleCharacter.setBoolean(decoder, false);
                    if ((Boolean) session.isComposing.invoke(decoder)) {
                        Iterator<?> candidates = (Iterator<?>) session.candidates.invoke(decoder);
                        if (candidates != null && candidates.hasNext()) {
                            session.publish.invoke(decoder, candidates);
                        }
                    } else {
                        session.publishPredictions.invoke(decoder);
                    }
                    Log.i(TAG, "Converter updated and candidates republished in place");
                } catch (Throwable error) {
                    Log.e(TAG, "Live candidate conversion failed", error);
                }
            }
        })) Log.e(TAG, "Decoder queue rejected conversion update");
    }

    private static final class Session {
        final WeakReference<Object> decoder;
        final Handler handler;
        final Field converter;
        final Field singleCharacter;
        final Method configure;
        final Method candidates;
        final Method publish;
        final Method publishPredictions;
        final Method isComposing;
        final Field displayLocale;
        final Method dictionaryLocale;
        final Method outputLocale;

        Session(Object receiver, Handler handler) throws ReflectiveOperationException {
            this.decoder = new WeakReference<Object>(receiver);
            this.handler = handler;
            ClassLoader loader = receiver.getClass().getClassLoader();
            Class<?> chinese = Class.forName(CHINESE_DECODER, false, loader);
            Class<?> hmm = chinese.getSuperclass();
            converter = chinese.getDeclaredField("j");
            singleCharacter = chinese.getDeclaredField("R");
            Class<?> converterClass = converter.getType();
            configure = converterClass.getDeclaredMethod("d", Context.class, int.class, int.class);
            displayLocale = converterClass.getDeclaredField("b");
            candidates = chinese.getDeclaredMethod("B");
            publish = hmm.getDeclaredMethod("ax", Iterator.class);
            publishPredictions = chinese.getDeclaredMethod("E");
            isComposing = receiver.getClass().getMethod("ay");
            dictionaryLocale = chinese.getDeclaredMethod("w");
            outputLocale = chinese.getDeclaredMethod("v");
            for (java.lang.reflect.AccessibleObject member : new java.lang.reflect.AccessibleObject[] {
                    converter, singleCharacter, configure, displayLocale, candidates, publish,
                    publishPredictions, isComposing, dictionaryLocale, outputLocale}) member.setAccessible(true);
        }

        void updateConverter(Object receiver, Context context) throws ReflectiveOperationException {
            Object instance = converter.get(receiver);
            if (instance == null) {
                throw new IllegalStateException("Chinese candidate converter unavailable");
            }
            // Virtual dispatch preserves each decoder's native preference and script:
            // Zhuyin/stroke use w=2, v=1/2; Cantonese phonetic/Cangjie use w=3, v=1/3.
            int dictionary = (Integer) dictionaryLocale.invoke(receiver);
            int target = (Integer) outputLocale.invoke(receiver);
            if (displayLocale.getInt(instance) != target) {
                // fhk.a and decoder.z share this converter for candidates and commit.
                configure.invoke(instance, context, dictionary, target);
                Log.i(TAG, "Display/output locale=" + target);
            }
        }
    }
}
