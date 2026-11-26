package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Portable archive for Gboard's personal data.
 *
 * <pre>
 * manifest.json                      format, Gboard version and the entries below
 * providers/&lt;providerId&gt;/&lt;key&gt;    one file exactly as the Gboard provider produced it
 * settings/default_preferences.json  typed copy of Gboard's default SharedPreferences
 * </pre>
 */
public final class GboardDataBackupArchive {
    public static final String FORMAT = "gboard.data.backup";
    public static final int SCHEMA = 1;
    public static final String CANONICAL_FILE_NAME = "gboard-data-backup.zip";
    public static final String MIME_TYPE = "application/zip";
    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String SETTINGS_ID = "gboard.settings";
    public static final String SETTINGS_CATEGORY = "settings";
    static final String PROVIDER_PREFIX = "providers/";
    static final String SETTINGS_ENTRY = "settings/default_preferences.json";
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 200;

    private GboardDataBackupArchive() {
    }

    static String providerEntryName(String providerId, String key) {
        return PROVIDER_PREFIX + requireSafeName(providerId, "provider id") + "/"
                + requireSafeName(key, "provider key");
    }

    static String requireSafeName(String value, String label) {
        if (!isSafeName(value)) {
            throw new IllegalArgumentException("Unsafe " + label + ": " + value);
        }
        return value;
    }

    static boolean isSafeName(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_NAME_LENGTH
                || value.equals(".") || value.equals("..")) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '/' || character == '\\' || character < 0x20 || character == 0x7f) {
                return false;
            }
        }
        return true;
    }

    /** Reads only the manifest from an archive held in memory. */
    public static Manifest readManifest(byte[] archive) throws IOException {
        if (archive == null || archive.length == 0) {
            throw new IOException("Backup file is empty");
        }
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                if (MANIFEST_ENTRY.equals(entry.getName())) {
                    return Manifest.parse(readBounded(input, MAX_MANIFEST_BYTES));
                }
            }
        }
        throw new IOException("Backup manifest is missing");
    }

    static Manifest readManifest(ZipFile archive) throws IOException {
        ZipEntry entry = archive.getEntry(MANIFEST_ENTRY);
        if (entry == null) {
            throw new IOException("Backup manifest is missing");
        }
        try (InputStream input = archive.getInputStream(entry)) {
            return Manifest.parse(readBounded(input, MAX_MANIFEST_BYTES));
        }
    }

    static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) > 0;) {
            if (read > maxBytes - output.size()) {
                throw new IOException("Archive entry is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /** Archive description; immutable once parsed or built. */
    public static final class Manifest {
        private final String gboardVersionName;
        private final long gboardVersionCode;
        private final long createdAtMillis;
        private final List<ProviderEntry> providers;
        private final int settingsEntryCount;

        Manifest(String gboardVersionName, long gboardVersionCode, long createdAtMillis,
                List<ProviderEntry> providers, int settingsEntryCount) {
            this.gboardVersionName = gboardVersionName == null ? "" : gboardVersionName;
            this.gboardVersionCode = gboardVersionCode;
            this.createdAtMillis = createdAtMillis;
            this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
            this.settingsEntryCount = settingsEntryCount;
        }

        public String getGboardVersionName() { return gboardVersionName; }
        public long getGboardVersionCode() { return gboardVersionCode; }
        public long getCreatedAtMillis() { return createdAtMillis; }
        public List<ProviderEntry> getProviders() { return providers; }
        public boolean hasSettings() { return settingsEntryCount >= 0; }
        public int getSettingsEntryCount() { return Math.max(0, settingsEntryCount); }

        public ProviderEntry provider(String id) {
            for (ProviderEntry provider : providers) {
                if (provider.getId().equals(id)) {
                    return provider;
                }
            }
            return null;
        }

        byte[] toJson() throws JSONException {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("schema", SCHEMA);
            root.put("gboardVersionName", gboardVersionName);
            root.put("gboardVersionCode", Long.toString(gboardVersionCode));
            root.put("createdAtMillis", Long.toString(createdAtMillis));
            JSONArray providerArray = new JSONArray();
            for (ProviderEntry provider : providers) {
                JSONObject item = new JSONObject();
                item.put("id", provider.getId());
                item.put("category", provider.getCategory());
                JSONArray keys = new JSONArray();
                for (String key : provider.getKeys()) {
                    keys.put(key);
                }
                item.put("keys", keys);
                providerArray.put(item);
            }
            root.put("providers", providerArray);
            if (settingsEntryCount >= 0) {
                root.put("settingsEntries", settingsEntryCount);
            }
            return root.toString(2).getBytes(StandardCharsets.UTF_8);
        }

        static Manifest parse(byte[] json) throws IOException {
            try {
                JSONObject root = new JSONObject(new String(json, StandardCharsets.UTF_8));
                if (!FORMAT.equals(root.optString("format"))) {
                    throw new IOException("Not a Gboard data backup");
                }
                int schema = root.optInt("schema", -1);
                if (schema != SCHEMA) {
                    throw new IOException("Unsupported backup schema " + schema);
                }
                List<ProviderEntry> providers = new ArrayList<>();
                Set<String> ids = new LinkedHashSet<>();
                JSONArray providerArray = root.optJSONArray("providers");
                for (int index = 0; providerArray != null && index < providerArray.length();
                        index++) {
                    JSONObject item = providerArray.getJSONObject(index);
                    String id = item.getString("id");
                    String category = item.optString("category");
                    if (!isSafeName(id) || SETTINGS_ID.equals(id) || !ids.add(id)) {
                        throw new IOException("Invalid provider id in backup: " + id);
                    }
                    JSONArray keyArray = item.optJSONArray("keys");
                    Set<String> keys = new LinkedHashSet<>();
                    for (int keyIndex = 0; keyArray != null && keyIndex < keyArray.length();
                            keyIndex++) {
                        String key = keyArray.getString(keyIndex);
                        if (!isSafeName(key) || !keys.add(key)) {
                            throw new IOException("Invalid provider key in backup: " + key);
                        }
                    }
                    providers.add(new ProviderEntry(id, category, new ArrayList<>(keys)));
                }
                return new Manifest(root.optString("gboardVersionName"),
                        parseLong(root.opt("gboardVersionCode")),
                        parseLong(root.opt("createdAtMillis")),
                        providers,
                        root.has("settingsEntries") ? root.getInt("settingsEntries") : -1);
            } catch (JSONException | NumberFormatException malformed) {
                throw new IOException("Malformed backup manifest", malformed);
            }
        }

        private static long parseLong(Object value) {
            if (value == null || value == JSONObject.NULL) {
                return 0L;
            }
            return value instanceof Number number ? number.longValue()
                    : Long.parseLong(value.toString());
        }
    }

    public static final class ProviderEntry {
        private final String id;
        private final String category;
        private final List<String> keys;

        ProviderEntry(String id, String category, List<String> keys) {
            this.id = id;
            this.category = category == null ? "" : category;
            this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        }

        public String getId() { return id; }
        public String getCategory() { return category; }
        public List<String> getKeys() { return keys; }
    }

    /**
     * Typed, lossless JSON image of Gboard's default SharedPreferences. Gboard keeps them in
     * device-protected storage; the credential-protected file of the same name is kept as well.
     */
    static final class Preferences {
        static final String DEVICE_PROTECTED = "device_protected";
        static final String CREDENTIAL = "credential";

        private Preferences() {
        }

        static byte[] toJson(Map<String, Map<String, ?>> stores) throws JSONException {
            JSONArray storeArray = new JSONArray();
            for (Map.Entry<String, Map<String, ?>> store : stores.entrySet()) {
                storeArray.put(new JSONObject().put("storage", store.getKey())
                        .put("entries", entries(store.getValue())));
            }
            return new JSONObject().put("stores", storeArray).toString(2)
                    .getBytes(StandardCharsets.UTF_8);
        }

        private static JSONArray entries(Map<String, ?> values) throws JSONException {
            JSONArray entries = new JSONArray();
            for (Map.Entry<String, ?> entry : new TreeMap<String, Object>(values).entrySet()) {
                Object value = entry.getValue();
                JSONObject item = new JSONObject();
                item.put("key", entry.getKey());
                if (value instanceof Boolean bool) {
                    item.put("type", "boolean").put("value", bool.booleanValue());
                } else if (value instanceof Integer integer) {
                    item.put("type", "int").put("value", integer.toString());
                } else if (value instanceof Long longValue) {
                    item.put("type", "long").put("value", longValue.toString());
                } else if (value instanceof Float floatValue) {
                    item.put("type", "float_bits").put("value",
                            Integer.toString(Float.floatToRawIntBits(floatValue)));
                } else if (value instanceof String string) {
                    item.put("type", "string").put("value", string);
                } else if (value instanceof Set<?> set) {
                    JSONArray strings = new JSONArray();
                    for (Object element : new java.util.TreeSet<Object>(set)) {
                        strings.put(String.valueOf(element));
                    }
                    item.put("type", "string_set").put("value", strings);
                } else {
                    continue;
                }
                entries.put(item);
            }
            return entries;
        }

        static int count(byte[] json) throws JSONException {
            JSONArray stores = new JSONObject(new String(json, StandardCharsets.UTF_8))
                    .getJSONArray("stores");
            int count = 0;
            for (int index = 0; index < stores.length(); index++) {
                count += stores.getJSONObject(index).getJSONArray("entries").length();
            }
            return count;
        }

        interface EditorFactory {
            /** Returns null when this device has no such storage. */
            SharedPreferences.Editor editor(String storage);
        }

        /**
         * Writes every entry into the store it came from; keys absent from the backup are left
         * untouched. Returns the editors to commit.
         */
        static List<SharedPreferences.Editor> apply(byte[] json, EditorFactory factory)
                throws JSONException {
            JSONArray stores = new JSONObject(new String(json, StandardCharsets.UTF_8))
                    .getJSONArray("stores");
            List<SharedPreferences.Editor> editors = new ArrayList<>();
            for (int storeIndex = 0; storeIndex < stores.length(); storeIndex++) {
                JSONObject store = stores.getJSONObject(storeIndex);
                SharedPreferences.Editor editor = factory.editor(store.getString("storage"));
                if (editor == null) {
                    continue;
                }
                JSONArray entries = store.getJSONArray("entries");
                for (int index = 0; index < entries.length(); index++) {
                    put(editor, entries.getJSONObject(index));
                }
                editors.add(editor);
            }
            return editors;
        }

        private static void put(SharedPreferences.Editor editor, JSONObject item)
                throws JSONException {
            String key = item.getString("key");
            switch (item.getString("type")) {
                case "boolean" -> editor.putBoolean(key, item.getBoolean("value"));
                case "int" -> editor.putInt(key, Integer.parseInt(item.getString("value")));
                case "long" -> editor.putLong(key, Long.parseLong(item.getString("value")));
                case "float_bits" -> editor.putFloat(key, Float.intBitsToFloat(
                        Integer.parseInt(item.getString("value"))));
                case "string" -> editor.putString(key, item.getString("value"));
                case "string_set" -> {
                    JSONArray array = item.getJSONArray("value");
                    Set<String> strings = new LinkedHashSet<>();
                    for (int element = 0; element < array.length(); element++) {
                        strings.add(array.getString(element));
                    }
                    editor.putStringSet(key, strings);
                }
                default -> {
                    // Types from a future format are skipped.
                }
            }
        }
    }
}
