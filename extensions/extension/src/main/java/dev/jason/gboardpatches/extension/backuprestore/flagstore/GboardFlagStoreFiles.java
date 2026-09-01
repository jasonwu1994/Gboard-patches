package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import android.content.Context;

import java.io.File;
import java.util.List;

/** Version-neutral catalog and target paths for Gboard's persisted flag stores. */
public final class GboardFlagStoreFiles {
    public static final String PROTO_FILE = "flags_jetpack_data_store.pb";
    public static final String LEGACY_OVERRIDE_FILE = "flag_override.xml";
    public static final String LEGACY_VALUE_FILE = "flag_value.xml";

    private static final String PROTO_RELATIVE_PATH = "datastore/" + PROTO_FILE;
    private static final String STAGING_DIRECTORY = "gboard_patches_flag_store";
    private static final List<String> CANONICAL_NAMES = List.of(
            PROTO_FILE, LEGACY_OVERRIDE_FILE, LEGACY_VALUE_FILE);

    private GboardFlagStoreFiles() {
    }

    public static List<String> canonicalNames() {
        return CANONICAL_NAMES;
    }

    public static boolean isProto(String name) {
        return PROTO_FILE.equals(name);
    }

    public static String requireCanonicalName(String name) {
        if (!CANONICAL_NAMES.contains(name)) {
            throw new IllegalArgumentException("Unsupported flag-store file name: " + name);
        }
        return name;
    }

    static File targetFile(Context context, String name) {
        Context appContext = applicationContext(context);
        requireCanonicalName(name);
        if (isProto(name)) {
            return new File(appContext.getFilesDir(), PROTO_RELATIVE_PATH);
        }
        return new File(new File(appContext.getApplicationInfo().dataDir, "shared_prefs"), name);
    }

    static File stagingDirectory(Context context) {
        return new File(applicationContext(context).getFilesDir(), STAGING_DIRECTORY);
    }

    static Context applicationContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Target context is required");
        }
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
    }
}
