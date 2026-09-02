package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.BuildConfig;
import dev.jason.gboardpatches.extension.clipboard.GboardClipboardSettings;
import dev.jason.gboardpatches.extension.clipboardcontentlimit.GboardClipboardContentLimitSettings;
import dev.jason.gboardpatches.extension.lanftp.config.LanFtpPreferences;
import dev.jason.gboardpatches.extension.keyboard.GboardEnglishUppercaseToggleSettings;
import dev.jason.gboardpatches.extension.keyboard.GboardLatinGlobeKeyIgnoreIntervalSettings;
import dev.jason.gboardpatches.extension.manualincognito.GboardManualIncognitoSettings;
import dev.jason.gboardpatches.extension.ocr.GboardOcrSettings;
import dev.jason.gboardpatches.extension.settingshomepage.GboardSettingsHomepageSettings;
import dev.jason.gboardpatches.extension.symbolfooter.GboardSymbolFooterOrderSettings;
import dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeSettings;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardPreferences;
import dev.jason.gboardpatches.extension.writingtools.GboardAiWritingToolsSettings;
import dev.jason.gboardpatches.extension.zhuyinbottomrow.GboardZhuyinBottomRowWeightSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** Lossless typed JSON backup for Gboard Patches-owned settings stores. */
public final class GboardPatchesBackupManager {
    static final String FORMAT = "gboard.patches.backup";
    static final int SCHEMA_VERSION = 2;

    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_STRING_SET = "string_set";
    private static final ModuleValueAdapter IDENTITY_VALUE_ADAPTER =
            new IdentityValueAdapter();
    private static final ModuleValueAdapter ZHUYIN_VALUE_ADAPTER =
            new ZhuyinBottomRowValueAdapter();
    private static final ModuleValueAdapter LATIN_GLOBE_VALUE_ADAPTER =
            new LatinGlobeValueAdapter();

    /** The only registration point for dedicated Patches settings stores. */
    private static final List<BackupModuleDescriptor> MODULE_CATALOG = validatedCatalog(List.of(
            new BackupModuleDescriptor("gboard-patches.core", "Core Patches settings",
                    GboardPatchesSettings.PREF_FILE,
                    null),
            new BackupModuleDescriptor("gboard-patches.custom-top-row", "Custom Top Row",
                    GboardTopRowSwipeSettings.PREF_FILE,
                    null),
            new BackupModuleDescriptor("gboard-patches.manual-incognito", "Manual Incognito",
                    GboardManualIncognitoSettings.PREF_FILE,
                    null),
            new BackupModuleDescriptor("gboard-patches.settings-homepage", "Settings homepage",
                    GboardSettingsHomepageSettings.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.zhuyin-bottom-row", "Zhuyin bottom row",
                    GboardZhuyinBottomRowWeightSettings.PREF_FILE,
                    ZHUYIN_VALUE_ADAPTER, null),
            new BackupModuleDescriptor("gboard-patches.latin-globe", "Latin globe key",
                    GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_FILE,
                    LATIN_GLOBE_VALUE_ADAPTER, null),
            new BackupModuleDescriptor("gboard-patches.lan-ftp", "LAN FTP",
                    LanFtpPreferences.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.clipboard", "Clipboard",
                    GboardClipboardSettings.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.web-clipboard", "Web Clipboard",
                    WebClipboardPreferences.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.ocr", "OCR",
                    GboardOcrSettings.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.symbol-footer", "Symbol footer order",
                    GboardSymbolFooterOrderSettings.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.english-uppercase", "English uppercase",
                    GboardEnglishUppercaseToggleSettings.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.ai-writing-tools", "AI Writing Tools",
                    GboardAiWritingToolsSettings.PREF_FILE, null),
            new BackupModuleDescriptor("gboard-patches.clipboard-content-limit",
                    "Clipboard content limit", GboardClipboardContentLimitSettings.PREF_FILE,
                    null),
            new BackupModuleDescriptor("gboard-patches.custom-symbol-recents",
                    "Custom symbol recents", "gboardpatches_gboard_symbol_recents", null)));

    private GboardPatchesBackupManager() {
    }

    public static String exportBackup(Context context) {
        return exportBackup(openStores(context));
    }

    public static BackupPlan inspectBackup(String jsonText) {
        ParsedBackup parsed = parseBackup(jsonText);
        List<ModulePlan> modules = new ArrayList<>();
        for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
            Map<String, PreferenceValue> values = parsed.modules.get(descriptor.logicalId);
            if (values != null) {
                modules.add(new ModulePlan(descriptor.logicalId, descriptor.displayName,
                        new ArrayList<>(values.keySet())));
            }
        }
        return new BackupPlan(modules);
    }

    public static RestorePreview inspectRestore(Context context, String jsonText,
            Set<String> selectedModuleIds) {
        return inspectRestore(openStores(context), jsonText, selectedModuleIds);
    }

    public static RestoreResult restoreBackup(Context context, String jsonText) {
        BackupPlan plan = inspectBackup(jsonText);
        return restoreBackup(context, jsonText, plan.getModuleIds());
    }

    public static RestoreResult restoreBackup(Context context, String jsonText,
            Set<String> selectedModuleIds) {
        RestoreResult result = restoreBackup(openStores(context), jsonText, selectedModuleIds);
        return result.withMirrorsSynchronized(
                synchronizeRemoteMirrors(context, selectedModuleIds));
    }

    static String exportBackup(Map<String, SharedPreferences> stores) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("producer", new JSONObject()
                    .put("name", "Gboard Patches")
                    .put("version", BuildConfig.PATCH_VERSION));
            JSONArray modules = new JSONArray();
            for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
                modules.put(new JSONObject()
                        .put("moduleId", descriptor.logicalId)
                        .put("moduleName", descriptor.displayName)
                        .put("captureMode", "complete_module")
                        .put("entries", encodeEntries(captureModuleValues(
                                descriptor, requiredStore(stores, descriptor.physicalName)))));
            }
            root.put("sections", new JSONObject().put("gboardPatches", new JSONObject()
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("modules", modules)));
            return root.toString(2);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to encode Gboard Patches backup", exception);
        }
    }

    static RestoreResult restoreBackup(Map<String, SharedPreferences> stores, String jsonText) {
        return restoreBackup(stores, jsonText, inspectBackup(jsonText).getModuleIds());
    }

    static RestorePreview inspectRestore(Map<String, SharedPreferences> stores, String jsonText,
            Set<String> selectedModuleIds) {
        ParsedBackup parsed = parseBackup(jsonText);
        List<BackupModuleDescriptor> selected = validateSelection(parsed, selectedModuleIds);
        List<ModuleChange> modules = new ArrayList<>();
        int added = 0;
        int changed = 0;
        int removed = 0;
        int unchanged = 0;
        for (BackupModuleDescriptor descriptor : selected) {
            Map<String, PreferenceValue> current = captureModuleValues(
                    descriptor, requiredStore(stores, descriptor.physicalName));
            Map<String, PreferenceValue> backup = parsed.modules.get(descriptor.logicalId);
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            keys.addAll(current.keySet());
            keys.addAll(backup.keySet());
            List<String> sortedKeys = new ArrayList<>(keys);
            sortedKeys.sort(Comparator.naturalOrder());
            List<KeyChange> keyChanges = new ArrayList<>();
            for (String key : sortedKeys) {
                PreferenceValue currentValue = current.get(key);
                PreferenceValue backupValue = backup.get(key);
                ChangeStatus status;
                if (currentValue == null) {
                    status = ChangeStatus.ADDED;
                    added++;
                } else if (backupValue == null) {
                    status = ChangeStatus.REMOVED;
                    removed++;
                } else if (backupValue.sameValue(currentValue)) {
                    status = ChangeStatus.UNCHANGED;
                    unchanged++;
                } else {
                    status = ChangeStatus.CHANGED;
                    changed++;
                }
                keyChanges.add(new KeyChange(key, status,
                        currentValue == null ? null : currentValue.displayValue(),
                        backupValue == null ? null : backupValue.displayValue()));
            }
            modules.add(new ModuleChange(descriptor.logicalId, descriptor.displayName,
                    keyChanges));
        }
        return new RestorePreview(modules, added, changed, removed, unchanged);
    }

    static RestoreResult restoreBackup(Map<String, SharedPreferences> stores, String jsonText,
            Set<String> selectedModuleIds) {
        ParsedBackup parsed = parseBackup(jsonText);
        List<BackupModuleDescriptor> selected = validateSelection(parsed, selectedModuleIds);
        List<ModuleResult> results = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int restoredModules = 0;
        int restoredEntries = 0;
        int failedModules = 0;
        for (BackupModuleDescriptor descriptor : selected) {
            Map<String, PreferenceValue> values = parsed.modules.get(descriptor.logicalId);
            SharedPreferences preferences = requiredStore(stores, descriptor.physicalName);
            Map<String, PreferenceValue> previousStore = capturePhysicalStore(preferences);
            Set<String> previousKeys = new LinkedHashSet<>(preferences.getAll().keySet());
            try {
                if (!replaceModule(descriptor, preferences, values)) {
                    throw new RestoreOperationException(descriptor.logicalId, null,
                            "Settings commit returned false");
                }
                results.add(verifyModule(descriptor, preferences, values, previousKeys));
                restoredModules++;
                restoredEntries += values.size();
            } catch (Throwable failure) {
                failedModules++;
                String failureMessage = failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage();
                try {
                    if (!replaceStore(preferences, previousStore)
                            || !matchesStore(preferences, previousStore)) {
                        throw new IllegalStateException("Previous values did not match read-back");
                    }
                    results.add(previousValuesRestoredResult(
                            descriptor, values, previousKeys, failure));
                    failures.add(descriptor.displayName + ": " + failureMessage);
                } catch (Throwable protectiveRestoreFailure) {
                    ModuleResult uncertain = inspectFailedModule(
                            descriptor, preferences, values, previousKeys,
                            failureMessage + "; previous values could not be restored: "
                                    + String.valueOf(protectiveRestoreFailure.getMessage()));
                    results.add(uncertain);
                    restoredEntries += countAppliedKeys(uncertain);
                    failures.add(descriptor.displayName + ": " + uncertain.getMessage());
                }
            }
        }
        boolean success = failedModules == 0;
        boolean partialSuccess = !success && (restoredModules > 0 || restoredEntries > 0);
        return new RestoreResult(success, partialSuccess, restoredModules, failedModules,
                restoredEntries, false,
                failures.isEmpty() ? null : String.join("; ", failures), results);
    }

    private static List<BackupModuleDescriptor> validateSelection(ParsedBackup parsed,
            Set<String> selectedModuleIds) {
        if (selectedModuleIds == null || selectedModuleIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one module to restore");
        }
        LinkedHashSet<String> remaining = new LinkedHashSet<>(selectedModuleIds);
        List<BackupModuleDescriptor> selected = new ArrayList<>();
        for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
            if (remaining.remove(descriptor.logicalId)) {
                if (!parsed.modules.containsKey(descriptor.logicalId)) {
                    throw new IllegalArgumentException(
                            "Selected module is missing from backup: " + descriptor.logicalId);
                }
                selected.add(descriptor);
            }
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown selected module: " + remaining.iterator().next());
        }
        return selected;
    }

    private static Map<String, SharedPreferences> openStores(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        Context appContext = context.getApplicationContext();
        Context storageContext = appContext != null ? appContext : context;
        Map<String, SharedPreferences> stores = new LinkedHashMap<>();
        for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
            stores.put(descriptor.physicalName, storageContext.getSharedPreferences(
                    descriptor.physicalName, Context.MODE_PRIVATE));
        }
        return stores;
    }

    private static JSONObject encodeEntries(Map<String, PreferenceValue> values)
            throws JSONException {
        JSONObject entries = new JSONObject();
        List<String> keys = new ArrayList<>(values.keySet());
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("Preference key cannot be null");
            }
            entries.put(key, values.get(key).toJson());
        }
        return entries;
    }

    private static ParsedBackup parseBackup(String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw new IllegalArgumentException("Backup JSON is empty");
        }
        try {
            JSONObject root = new JSONObject(jsonText);
            if (!FORMAT.equals(root.optString("format", null))) {
                throw new IllegalArgumentException("Unsupported backup format");
            }
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported backup schema version");
            }
            JSONObject patches = requiredObject(requiredObject(root, "sections"),
                    "gboardPatches");
            if (patches.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported Gboard Patches section version");
            }
            JSONArray moduleArray = patches.optJSONArray("modules");
            if (moduleArray == null) {
                throw new IllegalArgumentException("Gboard Patches modules are missing");
            }
            Map<String, Map<String, PreferenceValue>> modules = new LinkedHashMap<>();
            for (int index = 0; index < moduleArray.length(); index++) {
                JSONObject module = moduleArray.optJSONObject(index);
                if (module == null) {
                    throw new IllegalArgumentException("Gboard Patches module must be an object");
                }
                String moduleId = module.optString("moduleId", "");
                if (!isKnownModule(moduleId)) {
                    continue;
                }
                if (modules.containsKey(moduleId)) {
                    throw new IllegalArgumentException("Duplicate module: " + moduleId);
                }
                BackupModuleDescriptor descriptor = moduleDescriptor(moduleId);
                modules.put(moduleId, normalizeModuleValues(descriptor,
                        parseEntries(requiredObject(module, "entries"))));
            }
            if (modules.isEmpty()) {
                throw new IllegalArgumentException("Backup contains no supported modules");
            }
            return new ParsedBackup(modules);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid backup JSON", exception);
        }
    }

    private static Map<String, PreferenceValue> parseEntries(JSONObject entries) {
        List<String> keys = new ArrayList<>();
        entries.keys().forEachRemaining(keys::add);
        keys.sort(Comparator.naturalOrder());
        Map<String, PreferenceValue> parsed = new LinkedHashMap<>();
        for (String key : keys) {
            JSONObject encodedValue = entries.optJSONObject(key);
            if (encodedValue == null) {
                throw new IllegalArgumentException("Preference value must be an object: " + key);
            }
            parsed.put(key, PreferenceValue.fromJson(encodedValue));
        }
        return parsed;
    }

    private static JSONObject requiredObject(JSONObject parent, String key) {
        JSONObject value = parent.optJSONObject(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing object: " + key);
        }
        return value;
    }

    private static boolean isKnownModule(String logicalId) {
        for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
            if (descriptor.logicalId.equals(logicalId)) {
                return true;
            }
        }
        return false;
    }

    private static BackupModuleDescriptor moduleDescriptor(String logicalId) {
        for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
            if (descriptor.logicalId.equals(logicalId)) {
                return descriptor;
            }
        }
        throw new IllegalArgumentException("Unknown backup module: " + logicalId);
    }

    private static Map<String, PreferenceValue> capturePhysicalStore(
            SharedPreferences preferences) {
        Map<String, PreferenceValue> values = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            values.put(entry.getKey(), PreferenceValue.fromStoredValue(entry.getValue()));
        }
        return values;
    }

    private static Map<String, PreferenceValue> captureModuleValues(
            BackupModuleDescriptor descriptor, SharedPreferences preferences) {
        Map<String, PreferenceValue> captured = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            captured.put(entry.getKey(), descriptor.valueAdapter.toPortable(
                    entry.getKey(), PreferenceValue.fromStoredValue(entry.getValue())));
        }
        return captured;
    }

    private static Map<String, PreferenceValue> normalizeModuleValues(
            BackupModuleDescriptor descriptor, Map<String, PreferenceValue> values) {
        Map<String, PreferenceValue> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, PreferenceValue> entry : values.entrySet()) {
            normalized.put(entry.getKey(), descriptor.valueAdapter.toPortable(
                    entry.getKey(), entry.getValue()));
        }
        return normalized;
    }

    private static boolean replaceModule(BackupModuleDescriptor descriptor,
            SharedPreferences preferences, Map<String, PreferenceValue> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, PreferenceValue> entry : values.entrySet()) {
            descriptor.valueAdapter.putPhysical(editor, entry.getKey(), entry.getValue());
        }
        return editor.commit();
    }

    private static boolean replaceStore(SharedPreferences preferences,
            Map<String, PreferenceValue> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, PreferenceValue> entry : values.entrySet()) {
            entry.getValue().put(editor, entry.getKey());
        }
        return editor.commit();
    }

    private static boolean matchesStore(SharedPreferences preferences,
            Map<String, PreferenceValue> expected) {
        Map<String, ?> actual = preferences.getAll();
        if (!actual.keySet().equals(expected.keySet())) {
            return false;
        }
        for (Map.Entry<String, PreferenceValue> entry : expected.entrySet()) {
            if (!entry.getValue().matches(actual.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static ModuleResult verifyModule(BackupModuleDescriptor descriptor,
            SharedPreferences preferences, Map<String, PreferenceValue> expected,
            Set<String> previousKeys) {
        Map<String, PreferenceValue> actual = captureModuleValues(descriptor, preferences);
        List<KeyResult> keyResults = new ArrayList<>();
        for (Map.Entry<String, PreferenceValue> entry : expected.entrySet()) {
            boolean matched = actual.containsKey(entry.getKey())
                    && entry.getValue().sameValue(actual.get(entry.getKey()));
            keyResults.add(new KeyResult(entry.getKey(),
                    matched ? KeyStatus.RESTORED : KeyStatus.FAILED));
            if (!matched) {
                throw new RestoreOperationException(descriptor.logicalId, entry.getKey(),
                        "Read-back value did not match");
            }
        }
        for (String previousKey : previousKeys) {
            if (!expected.containsKey(previousKey)) {
                if (actual.containsKey(previousKey)) {
                    throw new RestoreOperationException(descriptor.logicalId, previousKey,
                            "Key expected to be removed is still present");
                }
                keyResults.add(new KeyResult(previousKey, KeyStatus.REMOVED));
            }
        }
        return new ModuleResult(descriptor.logicalId, descriptor.displayName,
                ModuleStatus.SUCCESS, null, keyResults);
    }

    private static ModuleResult previousValuesRestoredResult(
            BackupModuleDescriptor descriptor, Map<String, PreferenceValue> expected,
            Set<String> previousKeys, Throwable failure) {
        String failedModule = failure instanceof RestoreOperationException operation
                ? operation.moduleId : null;
        String failedKey = failure instanceof RestoreOperationException operation
                ? operation.key : null;
        LinkedHashSet<String> allKeys = new LinkedHashSet<>(expected.keySet());
        allKeys.addAll(previousKeys);
        List<KeyResult> keys = new ArrayList<>();
        for (String key : allKeys) {
            keys.add(new KeyResult(key,
                    descriptor.logicalId.equals(failedModule) && key.equals(failedKey)
                            ? KeyStatus.FAILED : KeyStatus.PREVIOUS_VALUE_RESTORED));
        }
        return new ModuleResult(descriptor.logicalId, descriptor.displayName,
                ModuleStatus.PREVIOUS_VALUES_RESTORED, failure.getMessage(), keys);
    }

    private static ModuleResult inspectFailedModule(BackupModuleDescriptor descriptor,
            SharedPreferences preferences, Map<String, PreferenceValue> expected,
            Set<String> previousKeys, String message) {
        Map<String, PreferenceValue> actual = captureModuleValues(descriptor, preferences);
        List<KeyResult> keys = new ArrayList<>();
        int applied = 0;
        for (Map.Entry<String, PreferenceValue> entry : expected.entrySet()) {
            boolean matched = actual.containsKey(entry.getKey())
                    && entry.getValue().sameValue(actual.get(entry.getKey()));
            keys.add(new KeyResult(entry.getKey(),
                    matched ? KeyStatus.RESTORED : KeyStatus.FAILED));
            if (matched) {
                applied++;
            }
        }
        for (String previousKey : previousKeys) {
            if (!expected.containsKey(previousKey)) {
                boolean removed = !actual.containsKey(previousKey);
                keys.add(new KeyResult(previousKey,
                        removed ? KeyStatus.REMOVED : KeyStatus.FAILED));
                if (removed) {
                    applied++;
                }
            }
        }
        return new ModuleResult(descriptor.logicalId, descriptor.displayName,
                applied > 0 ? ModuleStatus.PARTIAL : ModuleStatus.FAILED,
                message, keys);
    }

    private static int countAppliedKeys(ModuleResult result) {
        int count = 0;
        for (KeyResult key : result.keys) {
            if (key.status == KeyStatus.RESTORED) {
                count++;
            }
        }
        return count;
    }

    private static SharedPreferences requiredStore(Map<String, SharedPreferences> stores,
            String physicalName) {
        SharedPreferences preferences = stores == null ? null : stores.get(physicalName);
        if (preferences == null) {
            throw new IllegalArgumentException("Missing preference store: " + physicalName);
        }
        return preferences;
    }

    private static boolean synchronizeRemoteMirrors(Context context,
            Set<String> selectedModuleIds) {
        boolean synchronizedAll = true;
        for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
            if (selectedModuleIds.contains(descriptor.logicalId)
                    && descriptor.mirrorSynchronizer != null) {
                synchronizedAll &= descriptor.mirrorSynchronizer.synchronize(context);
            }
        }
        return synchronizedAll;
    }

    private static List<BackupModuleDescriptor> validatedCatalog(
            List<BackupModuleDescriptor> modules) {
        LinkedHashSet<String> moduleIds = new LinkedHashSet<>();
        LinkedHashSet<String> physicalNames = new LinkedHashSet<>();
        for (BackupModuleDescriptor descriptor : modules) {
            if (descriptor.logicalId.isBlank() || descriptor.displayName.isBlank()
                    || descriptor.physicalName.isBlank()) {
                throw new IllegalStateException("Backup module catalog contains a blank value");
            }
            if (!moduleIds.add(descriptor.logicalId)) {
                throw new IllegalStateException(
                        "Duplicate backup module ID: " + descriptor.logicalId);
            }
            if (!physicalNames.add(descriptor.physicalName)) {
                throw new IllegalStateException(
                        "Duplicate backup preference store: " + descriptor.physicalName);
            }
        }
        return List.copyOf(modules);
    }

    static List<String> physicalStoreNames() {
        List<String> names = new ArrayList<>(MODULE_CATALOG.size());
        for (BackupModuleDescriptor descriptor : MODULE_CATALOG) {
            names.add(descriptor.physicalName);
        }
        return Collections.unmodifiableList(names);
    }

    public static final class BackupPlan {
        private final List<ModulePlan> modules;

        BackupPlan(List<ModulePlan> modules) {
            this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
        }

        public List<ModulePlan> getModules() { return modules; }

        public Set<String> getModuleIds() {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (ModulePlan module : modules) {
                ids.add(module.moduleId);
            }
            return Collections.unmodifiableSet(ids);
        }
    }

    public static final class ModulePlan {
        private final String moduleId;
        private final String moduleName;
        private final List<String> keys;

        ModulePlan(String moduleId, String moduleName, List<String> keys) {
            this.moduleId = moduleId;
            this.moduleName = moduleName;
            this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        }

        public String getModuleId() { return moduleId; }
        public String getModuleName() { return moduleName; }
        public List<String> getKeys() { return keys; }
    }

    public static final class RestorePreview {
        private final List<ModuleChange> modules;
        private final int addedCount;
        private final int changedCount;
        private final int removedCount;
        private final int unchangedCount;

        RestorePreview(List<ModuleChange> modules, int addedCount, int changedCount,
                int removedCount, int unchangedCount) {
            this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
            this.addedCount = addedCount;
            this.changedCount = changedCount;
            this.removedCount = removedCount;
            this.unchangedCount = unchangedCount;
        }

        public List<ModuleChange> getModules() { return modules; }
        public int getAddedCount() { return addedCount; }
        public int getChangedCount() { return changedCount; }
        public int getRemovedCount() { return removedCount; }
        public int getUnchangedCount() { return unchangedCount; }
        public int getDifferenceCount() { return addedCount + changedCount + removedCount; }
    }

    public static final class ModuleChange {
        private final String moduleId;
        private final String moduleName;
        private final List<KeyChange> keys;

        ModuleChange(String moduleId, String moduleName, List<KeyChange> keys) {
            this.moduleId = moduleId;
            this.moduleName = moduleName;
            this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        }

        public String getModuleId() { return moduleId; }
        public String getModuleName() { return moduleName; }
        public List<KeyChange> getKeys() { return keys; }
    }

    public enum ChangeStatus { ADDED, CHANGED, REMOVED, UNCHANGED }

    public static final class KeyChange {
        private final String key;
        private final ChangeStatus status;
        private final String currentValue;
        private final String backupValue;

        KeyChange(String key, ChangeStatus status, String currentValue, String backupValue) {
            this.key = key;
            this.status = status;
            this.currentValue = currentValue;
            this.backupValue = backupValue;
        }

        public String getKey() { return key; }
        public ChangeStatus getStatus() { return status; }
        public String getCurrentValue() { return currentValue; }
        public String getBackupValue() { return backupValue; }
    }

    public static final class RestoreResult {
        private final boolean success;
        private final boolean partialSuccess;
        private final int restoredStoreCount;
        private final int failedModuleCount;
        private final int restoredEntryCount;
        private final boolean mirrorsSynchronized;
        private final String errorMessage;
        private final List<ModuleResult> modules;

        RestoreResult(boolean success, boolean partialSuccess, int restoredStoreCount,
                int failedModuleCount, int restoredEntryCount, boolean mirrorsSynchronized,
                String errorMessage, List<ModuleResult> modules) {
            this.success = success;
            this.partialSuccess = partialSuccess;
            this.restoredStoreCount = restoredStoreCount;
            this.failedModuleCount = failedModuleCount;
            this.restoredEntryCount = restoredEntryCount;
            this.mirrorsSynchronized = mirrorsSynchronized;
            this.errorMessage = errorMessage;
            this.modules = Collections.unmodifiableList(new ArrayList<>(modules));
        }

        RestoreResult withMirrorsSynchronized(boolean value) {
            return new RestoreResult(success, partialSuccess, restoredStoreCount,
                    failedModuleCount, restoredEntryCount, value, errorMessage, modules);
        }

        public boolean isSuccess() { return success; }
        public boolean isPartialSuccess() { return partialSuccess; }
        public int getRestoredStoreCount() { return restoredStoreCount; }
        public int getFailedModuleCount() { return failedModuleCount; }
        public int getRestoredEntryCount() { return restoredEntryCount; }
        public boolean areMirrorsSynchronized() { return mirrorsSynchronized; }
        public String getErrorMessage() { return errorMessage; }
        public List<ModuleResult> getModules() { return modules; }
    }

    public enum ModuleStatus { SUCCESS, PARTIAL, FAILED, PREVIOUS_VALUES_RESTORED }
    public enum KeyStatus { RESTORED, REMOVED, FAILED, PREVIOUS_VALUE_RESTORED }

    public static final class ModuleResult {
        private final String moduleId;
        private final String moduleName;
        private final ModuleStatus status;
        private final String message;
        private final List<KeyResult> keys;

        ModuleResult(String moduleId, String moduleName, ModuleStatus status,
                String message, List<KeyResult> keys) {
            this.moduleId = moduleId;
            this.moduleName = moduleName;
            this.status = status;
            this.message = message;
            this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        }

        public String getModuleId() { return moduleId; }
        public String getModuleName() { return moduleName; }
        public ModuleStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public List<KeyResult> getKeys() { return keys; }
    }

    public static final class KeyResult {
        private final String key;
        private final KeyStatus status;

        KeyResult(String key, KeyStatus status) {
            this.key = key;
            this.status = status;
        }

        public String getKey() { return key; }
        public KeyStatus getStatus() { return status; }
    }

    private static final class ParsedBackup {
        final Map<String, Map<String, PreferenceValue>> modules;

        ParsedBackup(Map<String, Map<String, PreferenceValue>> modules) {
            this.modules = modules;
        }
    }

    private static final class BackupModuleDescriptor {
        final String logicalId;
        final String displayName;
        final String physicalName;
        final ModuleValueAdapter valueAdapter;
        final MirrorSynchronizer mirrorSynchronizer;

        BackupModuleDescriptor(String logicalId, String displayName, String physicalName,
                MirrorSynchronizer mirrorSynchronizer) {
            this(logicalId, displayName, physicalName,
                    IDENTITY_VALUE_ADAPTER, mirrorSynchronizer);
        }

        BackupModuleDescriptor(String logicalId, String displayName, String physicalName,
                ModuleValueAdapter valueAdapter, MirrorSynchronizer mirrorSynchronizer) {
            this.logicalId = logicalId;
            this.displayName = displayName;
            this.physicalName = physicalName;
            this.valueAdapter = valueAdapter;
            this.mirrorSynchronizer = mirrorSynchronizer;
        }
    }

    private interface ModuleValueAdapter {
        PreferenceValue toPortable(String key, PreferenceValue value);

        void putPhysical(SharedPreferences.Editor editor, String key, PreferenceValue value);
    }

    private static final class IdentityValueAdapter implements ModuleValueAdapter {
        @Override
        public PreferenceValue toPortable(String key, PreferenceValue value) {
            return value;
        }

        @Override
        public void putPhysical(SharedPreferences.Editor editor, String key,
                PreferenceValue value) {
            value.put(editor, key);
        }
    }

    private static final class ZhuyinBottomRowValueAdapter implements ModuleValueAdapter {
        private static final Set<String> WEIGHT_KEYS = Set.of(
                GboardZhuyinBottomRowWeightSettings.PREF_KEY_WEIGHT_SWITCH_TO_SYMBOL,
                GboardZhuyinBottomRowWeightSettings.PREF_KEY_WEIGHT_A02,
                GboardZhuyinBottomRowWeightSettings.PREF_KEY_WEIGHT_SWITCH_TO_NEXT_LANGUAGE,
                GboardZhuyinBottomRowWeightSettings.PREF_KEY_WEIGHT_SPACE,
                GboardZhuyinBottomRowWeightSettings.PREF_KEY_WEIGHT_A06,
                GboardZhuyinBottomRowWeightSettings.PREF_KEY_WEIGHT_A08,
                GboardZhuyinBottomRowWeightSettings.PREF_KEY_WEIGHT_IME_ACTION);

        @Override
        public PreferenceValue toPortable(String key, PreferenceValue value) {
            if (GboardZhuyinBottomRowWeightSettings.PREF_KEY_ENABLED.equals(key)) {
                if (TYPE_BOOLEAN.equals(value.type)) {
                    return value;
                }
                if (TYPE_STRING.equals(value.type)) {
                    if (Boolean.TRUE.toString().equals(value.value)) {
                        return new PreferenceValue(TYPE_BOOLEAN, Boolean.TRUE);
                    }
                    if (Boolean.FALSE.toString().equals(value.value)) {
                        return new PreferenceValue(TYPE_BOOLEAN, Boolean.FALSE);
                    }
                }
                throw new IllegalArgumentException(
                        "Invalid Zhuyin bottom row enabled value");
            }
            if (!WEIGHT_KEYS.contains(key)) {
                return value;
            }
            int weight;
            if (TYPE_INT.equals(value.type)) {
                weight = ((Integer) value.value).intValue();
            } else if (TYPE_STRING.equals(value.type)) {
                try {
                    weight = Integer.parseInt((String) value.value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "Invalid Zhuyin bottom row weight: " + key, exception);
                }
            } else {
                throw new IllegalArgumentException(
                        "Invalid Zhuyin bottom row weight type: " + key);
            }
            return new PreferenceValue(TYPE_INT,
                    Integer.valueOf(GboardZhuyinBottomRowWeightSettings.sanitizeWeight(weight)));
        }

        @Override
        public void putPhysical(SharedPreferences.Editor editor, String key,
                PreferenceValue value) {
            toPortable(key, value).put(editor, key);
        }
    }

    private static final class LatinGlobeValueAdapter implements ModuleValueAdapter {
        @Override
        public PreferenceValue toPortable(String key, PreferenceValue value) {
            if (GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_ENABLED.equals(key)) {
                if (TYPE_BOOLEAN.equals(value.type)) {
                    return value;
                }
                if (TYPE_STRING.equals(value.type)) {
                    if (Boolean.TRUE.toString().equals(value.value)) {
                        return new PreferenceValue(TYPE_BOOLEAN, Boolean.TRUE);
                    }
                    if (Boolean.FALSE.toString().equals(value.value)) {
                        return new PreferenceValue(TYPE_BOOLEAN, Boolean.FALSE);
                    }
                }
                throw new IllegalArgumentException("Invalid Latin globe enabled value");
            }
            if (!GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_INTERVAL_MS.equals(key)) {
                return value;
            }
            int intervalMs;
            if (TYPE_INT.equals(value.type)) {
                intervalMs = ((Integer) value.value).intValue();
            } else if (TYPE_STRING.equals(value.type)) {
                try {
                    intervalMs = Integer.parseInt((String) value.value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "Invalid Latin globe interval", exception);
                }
            } else {
                throw new IllegalArgumentException("Invalid Latin globe interval type");
            }
            return new PreferenceValue(TYPE_INT, Integer.valueOf(
                    GboardLatinGlobeKeyIgnoreIntervalSettings.sanitizeIntervalMs(intervalMs)));
        }

        @Override
        public void putPhysical(SharedPreferences.Editor editor, String key,
                PreferenceValue value) {
            toPortable(key, value).put(editor, key);
        }
    }

    @FunctionalInterface
    private interface MirrorSynchronizer {
        boolean synchronize(Context context);
    }

    private static final class RestoreOperationException extends IllegalStateException {
        final String moduleId;
        final String key;

        RestoreOperationException(String moduleId, String key, String message) {
            super(message);
            this.moduleId = moduleId;
            this.key = key;
        }
    }

    private static final class PreferenceValue {
        final String type;
        final Object value;

        PreferenceValue(String type, Object value) {
            this.type = type;
            this.value = value;
        }

        static PreferenceValue fromStoredValue(Object value) {
            if (value instanceof Boolean booleanValue) {
                return new PreferenceValue(TYPE_BOOLEAN, booleanValue);
            }
            if (value instanceof Integer intValue) {
                return new PreferenceValue(TYPE_INT, intValue);
            }
            if (value instanceof Long longValue) {
                return new PreferenceValue(TYPE_LONG, longValue);
            }
            if (value instanceof Float floatValue) {
                return new PreferenceValue(TYPE_FLOAT,
                        Integer.valueOf(Float.floatToRawIntBits(floatValue.floatValue())));
            }
            if (value instanceof String stringValue) {
                return new PreferenceValue(TYPE_STRING, stringValue);
            }
            if (value instanceof Set<?> setValue) {
                LinkedHashSet<String> strings = new LinkedHashSet<>();
                for (Object item : setValue) {
                    if (!(item instanceof String stringItem)) {
                        throw new IllegalArgumentException(
                                "String Set contains a non-string value");
                    }
                    strings.add(stringItem);
                }
                return new PreferenceValue(TYPE_STRING_SET, strings);
            }
            throw new IllegalArgumentException("Unsupported preference value type: "
                    + (value == null ? "null" : value.getClass().getName()));
        }

        static PreferenceValue fromJson(JSONObject encoded) {
            String type = encoded.optString("type", "");
            return switch (type) {
                case TYPE_BOOLEAN -> new PreferenceValue(TYPE_BOOLEAN,
                        requiredBoolean(encoded, "value"));
                case TYPE_INT -> new PreferenceValue(TYPE_INT,
                        Integer.valueOf(parseIntString(encoded, "value")));
                case TYPE_LONG -> new PreferenceValue(TYPE_LONG,
                        Long.valueOf(parseLongString(encoded, "value")));
                case TYPE_FLOAT -> new PreferenceValue(TYPE_FLOAT,
                        Integer.valueOf(parseFloatBits(encoded)));
                case TYPE_STRING -> new PreferenceValue(TYPE_STRING,
                        requiredString(encoded, "value"));
                case TYPE_STRING_SET -> new PreferenceValue(TYPE_STRING_SET,
                        parseStringSet(encoded));
                default -> throw new IllegalArgumentException(
                        "Unsupported preference value type: " + type);
            };
        }

        JSONObject toJson() throws JSONException {
            JSONObject encoded = new JSONObject().put("type", type);
            switch (type) {
                case TYPE_BOOLEAN -> encoded.put("value", value);
                case TYPE_INT, TYPE_LONG -> encoded.put("value", value.toString());
                case TYPE_FLOAT -> {
                    int rawBits = ((Integer) value).intValue();
                    encoded.put("rawBitsHex", String.format(Locale.ROOT, "%08x", rawBits));
                    encoded.put("display", Float.toString(Float.intBitsToFloat(rawBits)));
                }
                case TYPE_STRING -> encoded.put("value", value);
                case TYPE_STRING_SET -> {
                    List<String> sorted = new ArrayList<>(castStringSet(value));
                    sorted.sort(Comparator.naturalOrder());
                    encoded.put("value", new JSONArray(sorted));
                }
                default -> throw new IllegalStateException("Unknown type " + type);
            }
            return encoded;
        }

        void put(SharedPreferences.Editor editor, String key) {
            switch (type) {
                case TYPE_BOOLEAN -> editor.putBoolean(key, ((Boolean) value).booleanValue());
                case TYPE_INT -> editor.putInt(key, ((Integer) value).intValue());
                case TYPE_LONG -> editor.putLong(key, ((Long) value).longValue());
                case TYPE_FLOAT -> editor.putFloat(key,
                        Float.intBitsToFloat(((Integer) value).intValue()));
                case TYPE_STRING -> editor.putString(key, (String) value);
                case TYPE_STRING_SET -> editor.putStringSet(key,
                        new LinkedHashSet<>(castStringSet(value)));
                default -> throw new IllegalStateException("Unknown type " + type);
            }
        }

        boolean matches(Object storedValue) {
            if (TYPE_FLOAT.equals(type)) {
                return storedValue instanceof Float floatValue
                        && Float.floatToRawIntBits(floatValue.floatValue())
                        == ((Integer) value).intValue();
            }
            if (TYPE_STRING_SET.equals(type)) {
                return storedValue instanceof Set<?> setValue
                        && castStringSet(value).equals(setValue);
            }
            return value.equals(storedValue);
        }

        boolean sameValue(PreferenceValue other) {
            if (other == null || !type.equals(other.type)) {
                return false;
            }
            if (TYPE_STRING_SET.equals(type)) {
                return castStringSet(value).equals(castStringSet(other.value));
            }
            return value.equals(other.value);
        }

        String displayValue() {
            String displayed = switch (type) {
                case TYPE_FLOAT -> {
                    int rawBits = ((Integer) value).intValue();
                    yield Float.toString(Float.intBitsToFloat(rawBits))
                            + " (0x" + String.format(Locale.ROOT, "%08x", rawBits) + ")";
                }
                case TYPE_STRING_SET -> {
                    List<String> sorted = new ArrayList<>(castStringSet(value));
                    sorted.sort(Comparator.naturalOrder());
                    yield "[" + String.join(", ", sorted) + "]";
                }
                default -> String.valueOf(value);
            };
            return type + " · " + displayed;
        }

        private static Boolean requiredBoolean(JSONObject object, String key) {
            Object value = object.opt(key);
            if (!(value instanceof Boolean booleanValue)) {
                throw new IllegalArgumentException(key + " must be a Boolean");
            }
            return booleanValue;
        }

        private static String requiredString(JSONObject object, String key) {
            Object value = object.opt(key);
            if (!(value instanceof String stringValue)) {
                throw new IllegalArgumentException(key + " must be a String");
            }
            return stringValue;
        }

        private static int parseIntString(JSONObject object, String key) {
            String value = requiredString(object, key);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid int: " + value, exception);
            }
        }

        private static long parseLongString(JSONObject object, String key) {
            String value = requiredString(object, key);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid long: " + value, exception);
            }
        }

        private static int parseFloatBits(JSONObject object) {
            String rawBitsHex = requiredString(object, "rawBitsHex");
            if (!rawBitsHex.matches("[0-9a-fA-F]{8}")) {
                throw new IllegalArgumentException("Float rawBitsHex must contain 8 hex digits");
            }
            return (int) Long.parseLong(rawBitsHex, 16);
        }

        private static Set<String> parseStringSet(JSONObject object) {
            JSONArray array = object.optJSONArray("value");
            if (array == null) {
                throw new IllegalArgumentException("String Set value must be an array");
            }
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (int index = 0; index < array.length(); index++) {
                Object item = array.opt(index);
                if (!(item instanceof String stringItem)) {
                    throw new IllegalArgumentException(
                            "String Set array contains a non-string value");
                }
                values.add(stringItem);
            }
            return values;
        }

        @SuppressWarnings("unchecked")
        private static Set<String> castStringSet(Object value) {
            return (Set<String>) value;
        }
    }
}
