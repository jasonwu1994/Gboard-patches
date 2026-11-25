package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Value types returned by {@link GboardDataBackupManager}, with a JSON form for transports. */
public final class GboardDataBackupResults {
    public static final String ERROR_NEWER_GBOARD = "backup_from_newer_gboard";

    private GboardDataBackupResults() {
    }

    public static final class Provider {
        private final String id;
        private final String category;

        public Provider(String id, String category) {
            this.id = id;
            this.category = category == null ? "" : category;
        }

        public String getId() { return id; }
        public String getCategory() { return category; }
    }

    public static final class Inspection {
        private final String gboardVersionName;
        private final long gboardVersionCode;
        private final List<Provider> providers;

        public Inspection(String gboardVersionName, long gboardVersionCode,
                List<Provider> providers) {
            this.gboardVersionName = gboardVersionName == null ? "" : gboardVersionName;
            this.gboardVersionCode = gboardVersionCode;
            this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
        }

        public String getGboardVersionName() { return gboardVersionName; }
        public long getGboardVersionCode() { return gboardVersionCode; }
        public List<Provider> getProviders() { return providers; }

        public String toJson() throws JSONException {
            JSONArray array = new JSONArray();
            for (Provider provider : providers) {
                array.put(new JSONObject().put("id", provider.getId())
                        .put("category", provider.getCategory()));
            }
            return new JSONObject().put("versionName", gboardVersionName)
                    .put("versionCode", Long.toString(gboardVersionCode))
                    .put("providers", array).toString();
        }

        public static Inspection fromJson(String json) throws JSONException {
            JSONObject root = new JSONObject(json);
            JSONArray array = root.getJSONArray("providers");
            List<Provider> providers = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                providers.add(new Provider(item.getString("id"), item.optString("category")));
            }
            return new Inspection(root.optString("versionName"),
                    Long.parseLong(root.optString("versionCode", "0")), providers);
        }
    }

    public static final class Export {
        private final GboardDataBackupArchive.Manifest manifest;
        private final List<String> failures;

        public Export(GboardDataBackupArchive.Manifest manifest, List<String> failures) {
            this.manifest = manifest;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public GboardDataBackupArchive.Manifest getManifest() { return manifest; }
        public List<String> getFailures() { return failures; }

        public int getEntryCount() {
            int count = manifest.hasSettings() ? 1 : 0;
            for (GboardDataBackupArchive.ProviderEntry provider : manifest.getProviders()) {
                count += provider.getKeys().size();
            }
            return count;
        }

        public String toJson() throws JSONException {
            JSONArray array = new JSONArray();
            for (String failure : failures) {
                array.put(failure);
            }
            return new JSONObject().put("entries", getEntryCount())
                    .put("failures", array).toString();
        }
    }

    public enum Status {
        RESTORED,
        PARTIAL,
        FAILED,
        UNSUPPORTED
    }

    public static final class Outcome {
        private final String id;
        private final String category;
        private final Status status;
        private final List<String> restoredKeys;
        private final List<String> failedKeys;
        private final String error;

        Outcome(String id, String category, Status status, List<String> restoredKeys,
                List<String> failedKeys, String error) {
            this.id = id;
            this.category = category == null ? "" : category;
            this.status = status;
            this.restoredKeys = Collections.unmodifiableList(new ArrayList<>(restoredKeys));
            this.failedKeys = Collections.unmodifiableList(new ArrayList<>(failedKeys));
            this.error = error;
        }

        static Outcome of(String id, String category, List<String> restored,
                List<String> failed, String error) {
            Status status = failed.isEmpty() && !restored.isEmpty() ? Status.RESTORED
                    : restored.isEmpty() ? Status.FAILED : Status.PARTIAL;
            return new Outcome(id, category, status, restored, failed, error);
        }

        static Outcome unsupported(String id, String category, List<String> keys) {
            return new Outcome(id, category, Status.UNSUPPORTED, List.of(), keys, null);
        }

        public String getId() { return id; }
        public String getCategory() { return category; }
        public Status getStatus() { return status; }
        public List<String> getRestoredKeys() { return restoredKeys; }
        public List<String> getFailedKeys() { return failedKeys; }
        public String getError() { return error; }
    }

    public static final class Restore {
        private final String backupVersionName;
        private final List<Outcome> outcomes;

        public Restore(String backupVersionName, List<Outcome> outcomes) {
            this.backupVersionName = backupVersionName == null ? "" : backupVersionName;
            this.outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
        }

        public String getBackupVersionName() { return backupVersionName; }
        public List<Outcome> getOutcomes() { return outcomes; }

        public boolean isCompleteSuccess() {
            for (Outcome outcome : outcomes) {
                if (outcome.getStatus() != Status.RESTORED) {
                    return false;
                }
            }
            return !outcomes.isEmpty();
        }

        public String toJson() throws JSONException {
            JSONArray array = new JSONArray();
            for (Outcome outcome : outcomes) {
                array.put(new JSONObject().put("id", outcome.getId())
                        .put("category", outcome.getCategory())
                        .put("status", outcome.getStatus().name())
                        .put("restored", new JSONArray(outcome.getRestoredKeys()))
                        .put("failed", new JSONArray(outcome.getFailedKeys()))
                        .put("error", outcome.getError() == null
                                ? JSONObject.NULL : outcome.getError()));
            }
            return new JSONObject().put("backupVersionName", backupVersionName)
                    .put("outcomes", array).toString();
        }

        public static Restore fromJson(String json) throws JSONException {
            JSONObject root = new JSONObject(json);
            JSONArray array = root.getJSONArray("outcomes");
            List<Outcome> outcomes = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                outcomes.add(new Outcome(item.getString("id"), item.optString("category"),
                        Status.valueOf(item.getString("status")),
                        strings(item.optJSONArray("restored")),
                        strings(item.optJSONArray("failed")),
                        item.isNull("error") ? null : item.optString("error")));
            }
            return new Restore(root.optString("backupVersionName"), outcomes);
        }

        private static List<String> strings(JSONArray array) throws JSONException {
            List<String> values = new ArrayList<>();
            for (int index = 0; array != null && index < array.length(); index++) {
                values.add(array.getString(index));
            }
            return values;
        }
    }
}
