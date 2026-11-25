package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Target-context export and restore of Gboard's personal data through Gboard's own backup
 * providers. Every call blocks and must run off the main thread.
 */
public final class GboardDataBackupManager {
    private static final String WORK_DIRECTORY = "gboard-data-backup-work";
    private static final long UNLIMITED_COLLECTOR_QUOTA = Long.MAX_VALUE / 4L;
    private static final int MAX_SETTINGS_BYTES = 8 * 1024 * 1024;

    private GboardDataBackupManager() {
    }

    public static GboardDataBackupResults.Inspection inspect(Context context,
            GboardDataBackupBinding binding) throws Exception {
        Context appContext = applicationContext(context);
        List<GboardDataBackupResults.Provider> providers = new ArrayList<>();
        for (Object provider : providers(appContext, binding)) {
            providers.add(new GboardDataBackupResults.Provider(
                    id(binding, provider), category(binding, provider)));
        }
        return new GboardDataBackupResults.Inspection(versionName(appContext),
                versionCode(appContext), providers);
    }

    /**
     * Writes a complete archive to {@code output}. Providers are selected by id; {@code null}
     * selects every provider and the Gboard settings.
     */
    public static GboardDataBackupResults.Export export(Context context,
            GboardDataBackupBinding binding, OutputStream output, Set<String> selectedIds)
            throws Exception {
        Context appContext = applicationContext(context);
        File work = createWorkDirectory(appContext);
        List<GboardDataBackupArchive.ProviderEntry> entries = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int settingsCount = -1;
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            int index = 0;
            for (Object provider : providers(appContext, binding)) {
                String id = id(binding, provider);
                if (!GboardDataBackupArchive.isSafeName(id)
                        || GboardDataBackupArchive.SETTINGS_ID.equals(id)
                        || (selectedIds != null && !selectedIds.contains(id))) {
                    continue;
                }
                String category = category(binding, provider);
                File collected = new File(work, "provider-" + index++ + ".zip");
                try {
                    collect(binding, provider, collected);
                    entries.add(new GboardDataBackupArchive.ProviderEntry(id, category,
                            copyProviderEntries(id, collected, zip)));
                } catch (Throwable failure) {
                    failures.add(id + ": " + describe(failure));
                } finally {
                    deleteRecursively(collected);
                }
            }
            if (selectedIds == null || selectedIds.contains(GboardDataBackupArchive.SETTINGS_ID)) {
                try {
                    Map<String, Map<String, ?>> stores = new LinkedHashMap<>();
                    for (Map.Entry<String, SharedPreferences> store
                            : settingsStores(appContext).entrySet()) {
                        Map<String, ?> values = store.getValue().getAll();
                        if (!values.isEmpty()) {
                            stores.put(store.getKey(), values);
                        }
                    }
                    byte[] settings = GboardDataBackupArchive.Preferences.toJson(stores);
                    zip.putNextEntry(new ZipEntry(GboardDataBackupArchive.SETTINGS_ENTRY));
                    zip.write(settings);
                    zip.closeEntry();
                    settingsCount = GboardDataBackupArchive.Preferences.count(settings);
                } catch (Throwable failure) {
                    failures.add(GboardDataBackupArchive.SETTINGS_ID + ": " + describe(failure));
                }
            }
            GboardDataBackupArchive.Manifest manifest = new GboardDataBackupArchive.Manifest(
                    versionName(appContext), versionCode(appContext),
                    System.currentTimeMillis(), entries, settingsCount);
            zip.putNextEntry(new ZipEntry(GboardDataBackupArchive.MANIFEST_ENTRY));
            zip.write(manifest.toJson());
            zip.closeEntry();
            zip.finish();
            return new GboardDataBackupResults.Export(manifest, failures);
        } finally {
            deleteRecursively(work);
        }
    }

    /** Restores the selected entries of an archive; {@code null} selects all of them. */
    public static GboardDataBackupResults.Restore restore(Context context,
            GboardDataBackupBinding binding, InputStream input, Set<String> selectedIds)
            throws Exception {
        Context appContext = applicationContext(context);
        File work = createWorkDirectory(appContext);
        try {
            File archiveFile = new File(work, "archive.zip");
            copy(input, archiveFile);
            try (ZipFile archive = new ZipFile(archiveFile)) {
                GboardDataBackupArchive.Manifest manifest =
                        GboardDataBackupArchive.readManifest(archive);
                long currentVersion = versionCode(appContext);
                if (currentVersion > 0L && manifest.getGboardVersionCode() > currentVersion) {
                    throw new IllegalStateException(
                            GboardDataBackupResults.ERROR_NEWER_GBOARD);
                }
                return restoreProviders(appContext, binding, archive, manifest, work,
                        selectedIds);
            }
        } finally {
            deleteRecursively(work);
        }
    }

    private static GboardDataBackupResults.Restore restoreProviders(Context context,
            GboardDataBackupBinding binding, ZipFile archive,
            GboardDataBackupArchive.Manifest manifest, File work, Set<String> selectedIds)
            throws Exception {
        Map<String, Object> registered = new LinkedHashMap<>();
        for (Object provider : providers(context, binding)) {
            registered.put(id(binding, provider), provider);
        }
        List<GboardDataBackupResults.Outcome> outcomes = new ArrayList<>();
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Map<String, File>> requestedFiles = new LinkedHashMap<>();
        Map<String, List<String>> missingKeys = new LinkedHashMap<>();
        int fileIndex = 0;
        for (GboardDataBackupArchive.ProviderEntry entry : manifest.getProviders()) {
            String id = entry.getId();
            if (selectedIds != null && !selectedIds.contains(id)) {
                continue;
            }
            if (!registered.containsKey(id)) {
                outcomes.add(GboardDataBackupResults.Outcome.unsupported(id,
                        entry.getCategory(), entry.getKeys()));
                continue;
            }
            Map<String, File> files = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (String key : entry.getKeys()) {
                ZipEntry zipEntry = archive.getEntry(
                        GboardDataBackupArchive.providerEntryName(id, key));
                if (zipEntry == null) {
                    missing.add(key);
                    continue;
                }
                File file = new File(work, "restore-" + fileIndex++);
                try (InputStream input = archive.getInputStream(zipEntry)) {
                    copy(input, file);
                }
                files.put(key, file);
            }
            missingKeys.put(id, missing);
            if (files.isEmpty()) {
                outcomes.add(GboardDataBackupResults.Outcome.of(id, entry.getCategory(),
                        List.of(), missing, null));
                continue;
            }
            requestedFiles.put(id, files);
            request.put(id, binding.immutableMapCopy.invoke(null, files));
        }

        String dispatchError = null;
        if (!request.isEmpty()) {
            try {
                binding.dispatcher.invoke(null, context, request);
            } catch (Throwable failure) {
                dispatchError = describe(failure);
            }
        }
        for (Map.Entry<String, Map<String, File>> provider : requestedFiles.entrySet()) {
            List<String> restored = new ArrayList<>();
            List<String> failed = new ArrayList<>(missingKeys.get(provider.getKey()));
            for (Map.Entry<String, File> file : provider.getValue().entrySet()) {
                // The dispatcher deletes or moves exactly the files a provider consumed.
                (file.getValue().exists() ? failed : restored).add(file.getKey());
            }
            outcomes.add(GboardDataBackupResults.Outcome.of(provider.getKey(),
                    category(binding, registered.get(provider.getKey())), restored, failed,
                    dispatchError));
        }

        if (manifest.hasSettings() && (selectedIds == null
                || selectedIds.contains(GboardDataBackupArchive.SETTINGS_ID))) {
            outcomes.add(restoreSettings(context, archive));
        }
        outcomes.sort(Comparator.comparing(GboardDataBackupResults.Outcome::getId));
        return new GboardDataBackupResults.Restore(manifest.getGboardVersionName(), outcomes);
    }

    private static GboardDataBackupResults.Outcome restoreSettings(Context context,
            ZipFile archive) {
        List<String> keys = List.of(GboardDataBackupArchive.SETTINGS_ENTRY);
        ZipEntry entry = archive.getEntry(GboardDataBackupArchive.SETTINGS_ENTRY);
        if (entry == null) {
            return GboardDataBackupResults.Outcome.of(GboardDataBackupArchive.SETTINGS_ID,
                    GboardDataBackupArchive.SETTINGS_CATEGORY, List.of(), keys, null);
        }
        try (InputStream input = archive.getInputStream(entry)) {
            byte[] json = GboardDataBackupArchive.readBounded(input, MAX_SETTINGS_BYTES);
            Map<String, SharedPreferences> stores = settingsStores(context);
            for (SharedPreferences.Editor editor : GboardDataBackupArchive.Preferences.apply(
                    json, storage -> stores.containsKey(storage)
                            ? stores.get(storage).edit() : null)) {
                if (!editor.commit()) {
                    throw new IOException("SharedPreferences commit failed");
                }
            }
            return GboardDataBackupResults.Outcome.of(GboardDataBackupArchive.SETTINGS_ID,
                    GboardDataBackupArchive.SETTINGS_CATEGORY, keys, List.of(), null);
        } catch (Throwable failure) {
            return GboardDataBackupResults.Outcome.of(GboardDataBackupArchive.SETTINGS_ID,
                    GboardDataBackupArchive.SETTINGS_CATEGORY, List.of(), keys,
                    describe(failure));
        }
    }

    private static void collect(GboardDataBackupBinding binding, Object provider, File target)
            throws Exception {
        Object collector = binding.collectorConstructor.newInstance(
                new FileOutputStream(target));
        try {
            if (binding.collectorQuota != null) {
                binding.collectorQuota.setLong(collector, UNLIMITED_COLLECTOR_QUOTA);
            }
            binding.backup.invoke(provider, collector);
        } finally {
            binding.collectorClose.invoke(collector);
        }
    }

    /** The collector names entries {@code <providerId>_<key>}. */
    private static List<String> copyProviderEntries(String id, File collected,
            ZipOutputStream zip) throws IOException {
        List<String> keys = new ArrayList<>();
        if (!collected.isFile() || collected.length() == 0L) {
            return keys;
        }
        String prefix = id + "_";
        try (ZipFile source = new ZipFile(collected)) {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix)) {
                    throw new IOException("Unexpected provider entry " + name);
                }
                String key = name.substring(prefix.length());
                zip.putNextEntry(new ZipEntry(
                        GboardDataBackupArchive.providerEntryName(id, key)));
                try (InputStream input = source.getInputStream(entry)) {
                    pump(input, zip);
                }
                zip.closeEntry();
                keys.add(key);
            }
        }
        return keys;
    }

    private static List<Object> providers(Context context, GboardDataBackupBinding binding)
            throws Exception {
        Object result = invoke(binding.registry, null, context, binding.providerType);
        if (!(result instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Provider registry returned no map");
        }
        List<Object> providers = new ArrayList<>();
        for (Object provider : ((Collection<?>) map.values())) {
            if (binding.providerType.isInstance(provider)) {
                providers.add(provider);
            }
        }
        providers.sort(Comparator.comparing(provider -> {
            try {
                return id(binding, provider);
            } catch (Exception failure) {
                return "";
            }
        }));
        return providers;
    }

    private static String id(GboardDataBackupBinding binding, Object provider) throws Exception {
        return String.valueOf(invoke(binding.id, provider));
    }

    private static String category(GboardDataBackupBinding binding, Object provider)
            throws Exception {
        return String.valueOf(invoke(binding.category, provider));
    }

    private static Object invoke(java.lang.reflect.Method method, Object receiver,
            Object... arguments) throws Exception {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw failure;
        }
    }

    /**
     * Gboard's default preferences by storage. Gboard reads them from device-protected storage.
     * SharedPreferences instances are cached per file, so these are the instances Gboard uses.
     */
    static Map<String, SharedPreferences> settingsStores(Context context) {
        String name = context.getPackageName() + "_preferences";
        Map<String, SharedPreferences> stores = new LinkedHashMap<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Context deviceContext = context.createDeviceProtectedStorageContext();
                if (deviceContext != null && !deviceContext.getDataDir().getAbsoluteFile()
                        .equals(context.getDataDir().getAbsoluteFile())) {
                    stores.put(GboardDataBackupArchive.Preferences.DEVICE_PROTECTED,
                            deviceContext.getSharedPreferences(name, Context.MODE_PRIVATE));
                }
            } catch (Throwable unavailable) {
                // Credential-protected storage alone is still exported.
            }
        }
        stores.put(GboardDataBackupArchive.Preferences.CREDENTIAL,
                context.getSharedPreferences(name, Context.MODE_PRIVATE));
        return stores;
    }

    private static String versionName(Context context) {
        PackageInfo info = packageInfo(context);
        return info == null || info.versionName == null ? "" : info.versionName;
    }

    private static long versionCode(Context context) {
        PackageInfo info = packageInfo(context);
        if (info == null) {
            return 0L;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
    }

    private static PackageInfo packageInfo(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (Throwable unavailable) {
            return null;
        }
    }

    private static File createWorkDirectory(Context context) throws IOException {
        // Providers move restored files into place. Files created under the cache directory
        // keep the cache group and would be counted as cache, so work in no-backup storage.
        File root = new File(context.getNoBackupFilesDir(), WORK_DIRECTORY);
        File[] stale = root.listFiles();
        if (stale != null) {
            long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
            for (File file : stale) {
                if (file.lastModified() < cutoff) {
                    deleteRecursively(file);
                }
            }
        }
        File work = new File(root, UUID.randomUUID().toString());
        if (!work.mkdirs()) {
            throw new IOException("Unable to create backup work directory");
        }
        return work;
    }

    private static void copy(InputStream input, File target) throws IOException {
        try (InputStream buffered = new BufferedInputStream(input);
                OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            pump(buffered, output);
        }
    }

    private static void pump(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        for (int read; (read = input.read(buffer)) > 0;) {
            output.write(buffer, 0, read);
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    static String describe(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static Context applicationContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
    }
}
