package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import android.os.Process;
import android.util.Printer;
import java.io.Closeable;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog.Family;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelSettings.Snapshot;

/** Typed resource policy, one process snapshot, and optional native-boundary observations. */
public final class GboardLanguageModelRuntime {
    private static volatile Snapshot snapshot;
    private static final Map<String, Object> effective = new LinkedHashMap<>();
    private static final Map<String, JSONObject> loaded = new LinkedHashMap<>();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static volatile ScheduledExecutorService worker;
    private GboardLanguageModelRuntime() { }

    static Context application() throws Exception {
        return (Context) Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
    }

    static Snapshot settings() {
        Snapshot state = snapshot;
        if (state != null) return state;
        synchronized (GboardLanguageModelRuntime.class) {
            if (snapshot != null) return snapshot;
            try {
                Context context = application();
                if (!GboardPatchesFeatureAvailability.hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_LANGUAGE_MODELS)) return null;
                state = GboardPatchesSettings.readRuntimeSetting(context, GboardLanguageModelSettings::read, null);
                if (state != null) snapshot = state;
            } catch (Exception unavailable) { return null; }
        }
        return state;
    }

    static boolean isObservationEnabled() {
        try { return GboardLanguageModelObservationControl.state().enabled(); }
        catch (Throwable unavailable) { return false; }
    }

    static synchronized void resetObservation() {
        try {
            loaded.clear(); effective.clear(); PENDING.set(false);
            if (worker != null) { worker.shutdownNow(); worker = null; }
        } catch (Throwable ignored) { /* Optional diagnostics cannot affect model policy. */ }
    }

    public static Object applyOverriddenFlagValue(Object receiver, Object stock) {
        try {
            String name = (String) GboardLanguageModelBindings.read(receiver, "flag_name_field");
            Family family = GboardLanguageModelCatalog.forFlag(name);
            if (family == null) return stock;
            Snapshot state = settings();
            Object baked = state != null && GboardLanguageModelCatalog.APK.equals(state.effectiveChoice(family))
                    ? GboardLanguageModelBindings.call(receiver, "language_models_baked_getter") : null;
            Object result = GboardLanguageModelPolicy.apply(name, stock, baked, state);
            try { observe(name, result); }
            catch (Throwable unavailable) { /* Diagnostics must never undo the selected resource policy. */ }
            return result;
        } catch (Throwable unavailable) { return stock; }
    }

    /** Reject only a mismatched NWP open result; the original manager owns synchronization. */
    public static Object restrictNwpCachedPack(Object manager, Object pack) {
        if (pack == null) return null;
        try {
            if (!manager.getClass().getName().equals(GboardLanguageModelBindings.owner("language_models_nwp_manager_factory"))) return pack;
            Snapshot state = settings();
            if (state == null || GboardLanguageModelCatalog.PIXEL.equals(state.effectiveChoice(Family.NWP))) return pack;
            long expected = Long.parseLong(GboardLanguageModelCatalog.version(Family.NWP, state.effectiveChoice(Family.NWP)));
            Object metadata = GboardLanguageModelBindings.call(pack, "language_models_pack_metadata");
            Object parent = GboardLanguageModelBindings.call(metadata, "language_models_pack_parent");
            long actual = ((Number) GboardLanguageModelBindings.call(parent, "language_models_parent_version")).longValue();
            if (actual == expected) return pack;
            try { ((Closeable) pack).close(); } catch (Throwable ignored) { /* Never return a rejected, possibly closed pack. */ }
            return null;
        } catch (Throwable unavailable) { return pack; }
    }

    public static void afterHmmEnroll(Object provider, String scheme, boolean succeeded) {
        if (!succeeded || "en_data_scheme".equals(scheme)) return;
        try {
            if (!isObservationEnabled()) return;
            File pack = (File) GboardLanguageModelBindings.read(provider, "language_models_hmm_pack");
            Object info = GboardLanguageModelBindings.read(provider, "language_models_hmm_provider");
            long version = ((Number) GboardLanguageModelBindings.read(info, "language_models_hmm_version")).longValue();
            String path = pack.getAbsolutePath();
            String locale = path.contains("zh_TW") ? "zh-TW" : path.contains("zh_CN") ? "zh-CN"
                    : path.contains("zh_HK") ? "zh-HK" : path.contains("ko_") ? "ko" : "unknown";
            record("hmm", locale, "enrolled", version, pack.getName() + "/" + scheme, 0, null);
        } catch (Throwable unavailable) { /* Read-only observation must not change enrollment. */ }
    }

    public static void afterLanguageModelLoad(Object decoder, Object wrapper, boolean succeeded) {
        if (!succeeded) return;
        try {
            if (!isObservationEnabled()) return;
            Object model = GboardLanguageModelBindings.read(wrapper, "language_models_descriptor");
            int type = ((Number) GboardLanguageModelBindings.read(model, "language_models_model_type")).intValue();
            if (type != 1 && type != 29) return;
            String locale = locale(model);
            long version = ((Number) GboardLanguageModelBindings.read(model, "language_models_model_version")).longValue();
            File file = new File((String) GboardLanguageModelBindings.read(model, "language_models_model_file"));
            String hash = type == 29 ? GboardKeyCorrectionIdentity.hash(file) : null;
            if (type == 29) version = GboardKeyCorrectionIdentity.versionForHash(hash);
            record(type == 1 ? "delight" : "key_correction", locale, "loaded", version, file.getName(), System.identityHashCode(decoder), hash);
        } catch (Throwable unavailable) { /* Observation is independent from the native result. */ }
    }

    public static void afterLanguageModelUnload(Object decoder, Object wrapper, boolean succeeded) {
        if (!succeeded) return;
        try {
            if (!isObservationEnabled()) return;
            Object model = GboardLanguageModelBindings.read(wrapper, "language_models_descriptor");
            int type = ((Number) GboardLanguageModelBindings.read(model, "language_models_model_type")).intValue();
            if (type != 1 && type != 29) return;
            File file = new File((String) GboardLanguageModelBindings.read(model, "language_models_model_file"));
            remove(type == 1 ? "delight" : "key_correction", locale(model), file.getName(), System.identityHashCode(decoder));
        } catch (Throwable unavailable) { /* Preserve the original unload result. */ }
    }

    public static void afterEngineAdded(Object decoder, Object engine) {
        try {
            if (!isObservationEnabled()) return;
            if (((Number) GboardLanguageModelBindings.read(engine, "language_models_engine_type")).intValue() != 7) return;
            for (Object model : (List<?>) GboardLanguageModelBindings.read(engine, "language_models_engine_models")) {
                File file = new File((String) GboardLanguageModelBindings.read(model, "language_models_engine_model_file"));
                if (!file.getName().endsWith(".tflite")) continue;
                long version = ((Number) GboardLanguageModelBindings.read(model, "language_models_engine_model_version")).longValue();
                String stage = "submitted";
                try {
                    NwpReadiness probe = new NwpReadiness();
                    GboardLanguageModelBindings.method(decoder, "language_models_decoder_dump", Printer.class, boolean.class)
                            .invoke(decoder, (Printer) probe::accept, false);
                    if (probe.isReady()) stage = "ready";
                } catch (Throwable unavailable) { /* The weaker submitted evidence remains valid. */ }
                record("nwp", "en-US", stage, version, file.getName(), System.identityHashCode(decoder), null);
                break;
            }
        } catch (Throwable unavailable) { /* Optional diagnostics cannot fail engine creation. */ }
    }

    public static void afterEngineRemoved(Object decoder, Object engine) {
        try {
            if (!isObservationEnabled()) return;
            if (((Number) GboardLanguageModelBindings.read(engine, "language_models_engine_type")).intValue() == 7)
                remove("nwp", "en-US", null, System.identityHashCode(decoder));
        } catch (Throwable unavailable) { /* Preserve native engine cleanup. */ }
    }

    public static synchronized void beforeDecoderReset(Object decoder) {
        try {
            if (!isObservationEnabled()) return;
            int id = System.identityHashCode(decoder);
            if (loaded.entrySet().removeIf(entry -> entry.getValue().optInt("decoder") == id)) schedule();
        } catch (Throwable unavailable) { /* Do not interfere with resetting the decoder. */ }
    }

    private static String locale(Object model) throws Exception {
        String language = (String) GboardLanguageModelBindings.read(model, "language_models_model_language");
        String country = (String) GboardLanguageModelBindings.read(model, "language_models_model_country");
        return language + (country.isEmpty() ? "" : "-" + country);
    }

    private static synchronized void observe(String name, Object value) {
        if (!isObservationEnabled()) return;
        Object safe = value instanceof byte[] bytes ? "MDD descriptor (" + bytes.length + " bytes)" : value;
        if (!Objects.equals(effective.get(name), safe)) { effective.put(name, safe); schedule(); }
    }

    private static synchronized void record(String family, String locale, String stage, long version, String pack, int decoder, String hash) throws Exception {
        if (!isObservationEnabled()) return;
        String key = family + "/" + locale;
        JSONObject row = new JSONObject().put("family", family).put("locale", locale).put("stage", stage)
                .put("version", version).put("pack", pack).put("decoder", decoder);
        if (hash != null && !hash.isEmpty()) row.put("content_sha256", hash);
        if (!row.toString().equals(String.valueOf(loaded.get(key)))) { loaded.put(key, row); schedule(); }
    }

    private static synchronized void remove(String family, String locale, String pack, int decoder) {
        if (!isObservationEnabled()) return;
        String key = family + "/" + locale;
        JSONObject previous = loaded.get(key);
        if (previous != null && previous.optInt("decoder") == decoder && (pack == null || pack.equals(previous.optString("pack")))) {
            loaded.remove(key); schedule();
        }
    }

    private static void schedule() {
        if (!isObservationEnabled() || !PENDING.compareAndSet(false, true)) return;
        try { publisher().schedule(GboardLanguageModelRuntime::publish, 250, TimeUnit.MILLISECONDS); }
        catch (Throwable unavailable) { PENDING.set(false); }
    }

    private static synchronized ScheduledExecutorService publisher() {
        if (worker == null) worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "gboard-language-model-status"); thread.setDaemon(true); return thread;
        });
        return worker;
    }

    private static void publish() {
        try {
            PENDING.set(false);
            var observation = GboardLanguageModelObservationControl.state();
            if (!observation.enabled()) return;
            Context app = application();
            if (app == null) { schedule(); return; }
            if (android.os.Build.VERSION.SDK_INT >= 28 && !app.getPackageName().equals(android.app.Application.getProcessName())) return;
            JSONObject status;
            synchronized (GboardLanguageModelRuntime.class) {
                Snapshot state = settings();
                status = new JSONObject().put("pid", Process.myPid()).put("package", app.getPackageName())
                        .put("observed_at", System.currentTimeMillis()).put("signature", state == null ? "unavailable" : state.signature())
                        .put("observation_session", observation.session())
                        .put("effective", new JSONObject(effective)).put("models", new JSONObject(loaded));
            }
            GboardLanguageModelStatusStore.write(app, status);
        } catch (Throwable unavailable) { /* Retry after the next resource event. */ }
    }

    static final class NwpReadiness {
        private boolean inNwp, ready;
        void accept(String text) {
            if (text == null) return;
            for (String line : text.split("\\r?\\n")) {
                String value = line.trim();
                if (value.startsWith("[")) inNwp = value.startsWith("[TfLiteNwpEngine]");
                if (inNwp && value.matches("Ready\\?\\s*1(?:\\D.*)?")) ready = true;
            }
        }
        boolean isReady() { return ready; }
    }
}
