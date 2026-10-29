package dev.jason.gboardpatches.extension.languagemodels;

import static org.junit.Assert.*;
import java.io.Closeable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class GboardLanguageModelRuntimeTest {
    @After public void releasePublisher() throws Exception {
        Field publisher = publisherField();
        if (!Modifier.isFinal(publisher.getModifiers())) {
            ScheduledExecutorService previous = (ScheduledExecutorService) publisher.get(null);
            if (previous != null) previous.shutdownNow();
            publisher.set(null, null);
        }
        pending().set(false);
    }
    @Before public void prepare() throws Exception {
        Field publisher = publisherField();
        if (!Modifier.isFinal(publisher.getModifiers())) {
            ScheduledExecutorService previous = (ScheduledExecutorService) publisher.get(null);
            if (previous != null) previous.shutdownNow();
            publisher.set(null, null);
        }
        pending().set(false);
        Field observations = GboardLanguageModelRuntime.class.getDeclaredField("effective");
        observations.setAccessible(true); ((Map<?, ?>) observations.get(null)).clear();
        set(GboardLanguageModelRuntime.class, "snapshot", GboardLanguageModelSettings.fromValues(Map.of()));
        JSONObject bindings = new JSONObject()
                .put("language_models_nwp_manager_factory", member(Manager.class, "create()V"))
                .put("language_models_pack_metadata", member(Pack.class, "metadata()Ljava/lang/Object;"))
                .put("language_models_pack_parent", member(Metadata.class, "parent()Ljava/lang/Object;"))
                .put("language_models_parent_version", member(Parent.class, "version()I"))
                .put("flag_name_field", member(Flag.class, "key:Ljava/lang/String;"))
                .put("language_models_baked_getter", member(Flag.class, "baked()Ljava/lang/Object;"))
                .put("language_models_descriptor", member(Wrapper.class, "model:Ljava/lang/Object;"))
                .put("language_models_model_type", member(Model.class, "type:I"))
                .put("language_models_model_language", member(Model.class, "language:Ljava/lang/String;"))
                .put("language_models_model_country", member(Model.class, "country:Ljava/lang/String;"))
                .put("language_models_model_version", member(Model.class, "version:J"))
                .put("language_models_model_file", member(Model.class, "file:Ljava/lang/String;"))
                .put("language_models_engine_type", member(Engine.class, "type:I"))
                .put("language_models_engine_models", member(Engine.class, "models:Ljava/util/List;"))
                .put("language_models_engine_model_file", member(Model.class, "file:Ljava/lang/String;"))
                .put("language_models_engine_model_version", member(Model.class, "version:J"))
                .put("language_models_decoder_dump", member(Decoder.class, "dump(Landroid/util/Printer;Z)V"));
        set(GboardLanguageModelBindings.class, "bindings", bindings);
        for (String key : new String[]{"FIELDS", "METHODS"}) {
            Field field = GboardLanguageModelBindings.class.getDeclaredField(key);
            field.setAccessible(true); ((Map<?, ?>) field.get(null)).clear();
        }
        loaded().clear();
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        dev.jason.gboardpatches.extension.settings.GboardPatchesSettings.preferences(context).edit().clear().commit();
        assertTrue(GboardLanguageModelSettings.writeObservation(context, true));
        GboardLanguageModelObservationControl.initialize(context);
        assertTrue(GboardLanguageModelRuntime.isObservationEnabled());
    }

    @Test public void disabledFlagPolicyUsesBakedAndUnknownFlagsPreserveStock() {
        assertEquals(2025110701L, GboardLanguageModelRuntime.applyOverriddenFlagValue(
                new Flag("hmm_superpacks_manifest_version"), 2026101403L));
        assertEquals(99L, GboardLanguageModelRuntime.applyOverriddenFlagValue(new Flag("other"), 99L));
        assertEquals(99L, GboardLanguageModelRuntime.applyOverriddenFlagValue(new Object(), 99L));
    }

    @Test public void newerNwpCacheIsClosedAndRejectedWhenReturningToApkDefaults() {
        Pack mismatch = new Pack(20240611);
        assertNull(GboardLanguageModelRuntime.restrictNwpCachedPack(new Manager(), mismatch));
        assertTrue(mismatch.closed);
        Pack matching = new Pack(20210507);
        assertSame(matching, GboardLanguageModelRuntime.restrictNwpCachedPack(new Manager(), matching));
        assertFalse(matching.closed);
    }

    @Test public void selectedNwpVersionIsAcceptedAndRejectionSurvivesCloseFailure() throws Exception {
        set(GboardLanguageModelRuntime.class, "snapshot", GboardLanguageModelSettings.fromValues(Map.of(
                GboardLanguageModelSettings.ENABLED, true,
                GboardLanguageModelCatalog.Family.NWP.preferenceKey(), GboardLanguageModelCatalog.NWP_2023)));
        Pack matching = new Pack(20240611);
        assertSame(matching, GboardLanguageModelRuntime.restrictNwpCachedPack(new Manager(), matching));
        Pack mismatch = new Pack(20210507); mismatch.failClose = true;
        assertNull(GboardLanguageModelRuntime.restrictNwpCachedPack(new Manager(), mismatch));
        assertTrue(mismatch.closed);
    }

    @Test public void unrelatedManagersAndUnavailableMetadataRemainStock() {
        Pack pack = new Pack(123);
        assertSame(pack, GboardLanguageModelRuntime.restrictNwpCachedPack(new Object(), pack));
        Object unavailable = new Object();
        assertSame(unavailable, GboardLanguageModelRuntime.restrictNwpCachedPack(new Manager(), unavailable));
        assertFalse(pack.closed);
    }

    @Test public void rejectedPackNeverReturnsAfterCloseReleasesResourcesAndThrowsError() {
        Pack pack = new Pack(20240611); pack.failCloseError = true;
        assertNull(GboardLanguageModelRuntime.restrictNwpCachedPack(new Manager(), pack));
        assertTrue(pack.closed);
    }

    @Test public void diagnosticFailureCannotUndoTheSelectedFlagValue() throws Exception {
        stopPublisher();
        assertEquals(2026101403L, GboardLanguageModelRuntime.applyOverriddenFlagValue(
                new Flag("hmm_superpacks_manifest_version"), 2025110701L));
    }

    @Test public void rejectedPublicationDoesNotLeavePendingLatchSet() throws Exception {
        stopPublisher();
        GboardLanguageModelRuntime.applyOverriddenFlagValue(new Flag("hmm_superpacks_manifest_version"), 2025110701L);
        assertFalse("A failed publication must allow later events to retry", pending().get());
    }

    @Test public void publisherRetriesAfterThreadStartFailureOnTheNextResourceEvent() throws Exception {
        int[] attempts = {0};
        ScheduledThreadPoolExecutor publisher = new ScheduledThreadPoolExecutor(1) {
            @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
                if (attempts[0]++ == 0) throw new LinkageError("Thread creation unavailable");
                return super.schedule(task, delay, unit);
            }
        };
        publisherField().set(null, publisher);
        GboardLanguageModelRuntime.applyOverriddenFlagValue(new Flag("hmm_superpacks_manifest_version"), 1L);
        assertFalse(pending().get());
        GboardLanguageModelRuntime.applyOverriddenFlagValue(new Flag("delight_latest_metadata_version"), 1L);
        assertEquals(2, attempts[0]);
        assertTrue(pending().get());
    }

    @Test public void unexpectedObservationErrorDoesNotRevertTheComputedPolicy() throws Exception {
        set(GboardLanguageModelRuntime.class, "snapshot", GboardLanguageModelSettings.fromValues(Map.of(
                GboardLanguageModelSettings.ENABLED, true,
                GboardLanguageModelCatalog.Family.HMM.preferenceKey(), GboardLanguageModelCatalog.PIXEL)));
        Field field = GboardLanguageModelRuntime.class.getDeclaredField("effective"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, Object> observations = (Map<String, Object>) field.get(null);
        observations.put("hmm_superpacks_manifest_version", new Object() {
            @Override public boolean equals(Object value) { throw new LinkageError("Observation unavailable"); }
        });
        assertEquals(2026101403L, GboardLanguageModelRuntime.applyOverriddenFlagValue(
                new Flag("hmm_superpacks_manifest_version"), 2025110701L));
    }

    private static void stopPublisher() throws Exception {
        set(GboardLanguageModelRuntime.class, "snapshot", GboardLanguageModelSettings.fromValues(Map.of(
                GboardLanguageModelSettings.ENABLED, true,
                GboardLanguageModelCatalog.Family.HMM.preferenceKey(), GboardLanguageModelCatalog.PIXEL)));
        GboardLanguageModelRuntime.applyOverriddenFlagValue(new Flag("hmm_superpacks_manifest_version"), 2025110701L);
        ((ScheduledExecutorService) publisherField().get(null)).shutdownNow();
        pending().set(false);
        Field observations = GboardLanguageModelRuntime.class.getDeclaredField("effective");
        observations.setAccessible(true); ((Map<?, ?>) observations.get(null)).clear();
    }

    private static Field publisherField() {
        for (Field field : GboardLanguageModelRuntime.class.getDeclaredFields()) {
            if (ScheduledExecutorService.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true); return field;
            }
        }
        throw new AssertionError("Status publisher missing");
    }

    private static AtomicBoolean pending() throws Exception {
        Field field = GboardLanguageModelRuntime.class.getDeclaredField("PENDING");
        field.setAccessible(true); return (AtomicBoolean) field.get(null);
    }

    @Test public void statusStorageSkipsLockedUsersAndToleratesUnavailableStorage() {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        int[] accesses = {0};
        android.content.Context locked = new android.content.ContextWrapper(app) {
            @Override public Object getSystemService(String name) {
                throw new IllegalStateException("User state unavailable");
            }
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                accesses[0]++;
                throw new IllegalStateException("Storage unavailable");
            }
        };
        assertEquals(0, GboardLanguageModelStatusStore.read(locked).length());
        GboardLanguageModelStatusStore.write(locked, new JSONObject());
        assertEquals(0, accesses[0]);
        android.content.Context unavailable = new android.content.ContextWrapper(app) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                throw new IllegalStateException("Storage unavailable");
            }
        };
        assertEquals(0, GboardLanguageModelStatusStore.read(unavailable).length());
        GboardLanguageModelStatusStore.write(unavailable, new JSONObject());
    }

    @Test public void onlySuccessfulMainLoadsPublishAndUnloadRemovesTheirOwnObservation() throws Exception {
        Decoder decoder = new Decoder(); Wrapper wrapper = new Wrapper();
        GboardLanguageModelRuntime.afterLanguageModelLoad(decoder, wrapper, false);
        assertTrue(loaded().isEmpty());
        GboardLanguageModelRuntime.afterLanguageModelLoad(decoder, wrapper, true);
        JSONObject row = (JSONObject) loaded().get("delight/en-US");
        assertEquals(2026092216L, row.getLong("version"));
        assertEquals("loaded", row.getString("stage"));
        GboardLanguageModelRuntime.afterLanguageModelUnload(new Decoder(), wrapper, true);
        assertEquals(1, loaded().size());
        GboardLanguageModelRuntime.afterLanguageModelUnload(decoder, wrapper, false);
        assertEquals(1, loaded().size());
        GboardLanguageModelRuntime.afterLanguageModelUnload(decoder, wrapper, true);
        assertTrue(loaded().isEmpty());
        wrapper.model.type = 2;
        GboardLanguageModelRuntime.afterLanguageModelLoad(decoder, wrapper, true);
        assertTrue(loaded().isEmpty());
    }

    @Test public void nwpReadinessAndLifecycleBelongToTheObservedDecoder() throws Exception {
        Decoder decoder = new Decoder(); Engine engine = new Engine();
        GboardLanguageModelRuntime.afterEngineAdded(decoder, engine);
        assertEquals("ready", ((JSONObject) loaded().get("nwp/en-US")).getString("stage"));
        GboardLanguageModelRuntime.afterEngineRemoved(new Decoder(), engine);
        assertEquals(1, loaded().size());
        GboardLanguageModelRuntime.beforeDecoderReset(new Decoder());
        assertEquals(1, loaded().size());
        GboardLanguageModelRuntime.beforeDecoderReset(decoder);
        assertTrue(loaded().isEmpty());
        decoder.ready = false;
        GboardLanguageModelRuntime.afterEngineAdded(decoder, engine);
        assertEquals("submitted", ((JSONObject) loaded().get("nwp/en-US")).getString("stage"));
        GboardLanguageModelRuntime.afterEngineRemoved(decoder, engine);
        assertTrue(loaded().isEmpty());
    }

    @Test public void readinessErrorStillRecordsTheSuccessfulEngineSubmission() throws Exception {
        Decoder decoder = new Decoder(); decoder.failDump = true;
        GboardLanguageModelRuntime.afterEngineAdded(decoder, new Engine());
        assertEquals("submitted", ((JSONObject) loaded().get("nwp/en-US")).getString("stage"));
    }

    private static Map<?, ?> loaded() throws Exception {
        Field field = GboardLanguageModelRuntime.class.getDeclaredField("loaded");
        field.setAccessible(true); return (Map<?, ?>) field.get(null);
    }

    private static String member(Class<?> type, String suffix) {
        return "L" + type.getName().replace('.', '/') + ";->" + suffix;
    }
    private static void set(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    public static class Manager { }
    public static class Flag {
        public String key;
        Flag(String key) { this.key = key; }
        public Object baked() { return 2025110701L; }
    }
    public static class Pack implements Closeable {
        boolean closed, failClose, failCloseError;
        final Metadata metadata;
        Pack(int version) { metadata = new Metadata(version); }
        public Metadata metadata() { return metadata; }
        @Override public void close() throws java.io.IOException {
            closed = true;
            if (failCloseError) throw new AssertionError("close failed after release");
            if (failClose) throw new java.io.IOException("close failed");
        }
    }
    public static class Metadata {
        final Parent parent;
        Metadata(int version) { parent = new Parent(version); }
        public Parent parent() { return parent; }
    }
    public static class Parent {
        final int value;
        Parent(int version) { value = version; }
        public int version() { return value; }
    }
    public static class Wrapper { public Model model = new Model(); }
    public static class Model {
        public int type = 1;
        public String language = "en", country = "US", file = "/model/model.tflite";
        public long version = 2026092216L;
    }
    public static class Engine {
        public int type = 7;
        public java.util.List<Model> models = java.util.List.of(new Model());
    }
    public static class Decoder {
        boolean ready = true, failDump;
        public void dump(android.util.Printer printer, boolean all) {
            if (failDump) throw new LinkageError("Native readiness probe unavailable");
            printer.println("[TfLiteNwpEngine]\nReady? " + (ready ? "1" : "0") + "\n[Other]\nReady? 1");
        }
    }
}
