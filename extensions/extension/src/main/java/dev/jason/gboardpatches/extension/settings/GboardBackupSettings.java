package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GboardBackupSettings {
    private static final String TAG = "GboardPatches";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GboardBackupSettings() {
    }

    public static String exportSettings(Context context) {
        Map<String, Map<String, Map<String, Object>>> allFiles = new HashMap<>();

        // CE storage
        scanPrefs(context, allFiles, "ce");

        // DE storage (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            scanPrefs(context.createDeviceProtectedStorageContext(), allFiles, "de");
        }

        return GSON.toJson(allFiles);
    }

    private static void scanPrefs(Context context,
            Map<String, Map<String, Map<String, Object>>> allFiles, String suffix) {
        File dataDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? context.getDataDir()
                : new File(context.getApplicationInfo().dataDir);
        File prefsDir = new File(dataDir, "shared_prefs");

        if (prefsDir.exists() && prefsDir.isDirectory()) {
            File[] files = prefsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".xml")) {
                        String prefName = file.getName().substring(0, file.getName().length() - 4);
                        SharedPreferences prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                        Map<String, Map<String, Object>> fileSettings = new HashMap<>();
                        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                            Object value = entry.getValue();
                            if (value == null) continue;

                            Map<String, Object> typeValue = new HashMap<>();
                            String type = "string";
                            if (value instanceof Boolean) type = "bool";
                            else if (value instanceof Integer) type = "int";
                            else if (value instanceof Long) type = "long";
                            else if (value instanceof Float) type = "float";
                            else if (value instanceof Set) type = "set";

                            typeValue.put("t", type);
                            typeValue.put("v", value);
                            fileSettings.put(entry.getKey(), typeValue);
                        }
                        // Use a key that includes the storage type to avoid collisions
                        allFiles.put(prefName + ":" + suffix, fileSettings);
                    }
                }
            }
        }
    }

    public static boolean importSettings(Context context, String json) {
        try {
            Type type = new TypeToken<Map<String, Map<String, Map<String, Object>>>>() {}.getType();
            Map<String, Map<String, Map<String, Object>>> allFiles = GSON.fromJson(json, type);
            if (allFiles == null) {
                return false;
            }

            for (Map.Entry<String, Map<String, Map<String, Object>>> fileEntry : allFiles.entrySet()) {
                String keyWithSuffix = fileEntry.getKey();
                String prefName;
                Context targetContext;

                if (keyWithSuffix.endsWith(":de") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    prefName = keyWithSuffix.substring(0, keyWithSuffix.length() - 3);
                    targetContext = context.createDeviceProtectedStorageContext();
                } else if (keyWithSuffix.endsWith(":ce")) {
                    prefName = keyWithSuffix.substring(0, keyWithSuffix.length() - 3);
                    targetContext = context;
                } else {
                    // Fallback for old backup files without suffix
                    prefName = keyWithSuffix;
                    targetContext = context;
                }

                SharedPreferences prefs = targetContext.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();

                Map<String, Map<String, Object>> fileSettings = fileEntry.getValue();
                for (Map.Entry<String, Map<String, Object>> settingEntry : fileSettings.entrySet()) {
                    String key = settingEntry.getKey();
                    Map<String, Object> typeValue = settingEntry.getValue();
                    String t = (String) typeValue.get("t");
                    Object v = typeValue.get("v");

                    if (t == null || v == null) continue;

                    switch (t) {
                        case "bool":
                            editor.putBoolean(key, (Boolean) v);
                            break;
                        case "int":
                            editor.putInt(key, ((Number) v).intValue());
                            break;
                        case "long":
                            editor.putLong(key, ((Number) v).longValue());
                            break;
                        case "float":
                            editor.putFloat(key, ((Number) v).floatValue());
                            break;
                        case "string":
                            editor.putString(key, (String) v);
                            break;
                        case "set":
                            if (v instanceof Iterable) {
                                HashSet<String> set = new HashSet<>();
                                for (Object item : (Iterable<?>) v) {
                                    if (item instanceof String) {
                                        set.add((String) item);
                                    }
                                }
                                editor.putStringSet(key, set);
                            }
                            break;
                    }
                }
                editor.apply();
            }
            return true;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to import settings", throwable);
            return false;
        }
    }
}
