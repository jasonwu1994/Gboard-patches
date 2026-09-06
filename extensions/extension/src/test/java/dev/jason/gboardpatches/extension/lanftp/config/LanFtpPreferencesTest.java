package dev.jason.gboardpatches.extension.lanftp.config;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class LanFtpPreferencesTest {
    @Test
    public void firstContextReadAtomicallyMigratesAllLegacyFtpValues() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences();
        InMemorySharedPreferences dedicated = new InMemorySharedPreferences();
        legacy.edit()
                .putBoolean(LanFtpPreferences.PREF_KEY_ENABLED, true)
                .putInt(LanFtpPreferences.PREF_KEY_CONTROL_PORT, 2222)
                .putInt(LanFtpPreferences.PREF_KEY_PASSIVE_PORT_START, 51000)
                .putInt(LanFtpPreferences.PREF_KEY_PASSIVE_PORT_END, 51009)
                .putString(LanFtpPreferences.PREF_KEY_ROOT_TREE_URI,
                        "content://storage/tree/primary%3ADownload")
                .putBoolean(LanFtpPreferences.PREF_KEY_READ_ONLY, true)
                .putBoolean(LanFtpPreferences.PREF_KEY_ALLOW_ANONYMOUS, false)
                .putString(LanFtpPreferences.PREF_KEY_PASSWORD, "legacy")
                .putString("pref_lan_ftp_credential_generation", "1".repeat(32))
                .putString("pref_lan_ftp_metadata_auth_key", "2".repeat(64))
                .putString("unrelated_setting", "preserved")
                .commit();

        SharedPreferences selected = LanFtpPreferences.preferences(
                namedPreferencesContext(legacy, dedicated));

        Assert.assertSame(dedicated, selected);
        LanFtpPreferences.Snapshot snapshot = LanFtpPreferences.read(selected);
        Assert.assertTrue(snapshot.enabled);
        Assert.assertEquals(2222, snapshot.controlPort);
        Assert.assertEquals(51000, snapshot.passivePortStart);
        Assert.assertEquals(51009, snapshot.passivePortEnd);
        Assert.assertEquals("legacy", snapshot.password);
        Assert.assertEquals("preserved", legacy.getString("unrelated_setting", null));
        for (String key : LanFtpPreferences.ftpPreferenceKeys()) {
            Assert.assertFalse("legacy key must be removed only after target commit: " + key,
                    legacy.contains(key));
            Assert.assertTrue("dedicated preference must contain migrated key: " + key,
                    dedicated.contains(key));
        }
        Assert.assertFalse(legacy.contains("pref_lan_ftp_credential_generation"));
        Assert.assertFalse(legacy.contains("pref_lan_ftp_metadata_auth_key"));
        Assert.assertFalse(dedicated.contains("pref_lan_ftp_credential_generation"));
        Assert.assertFalse(dedicated.contains("pref_lan_ftp_metadata_auth_key"));
    }

    @Test
    public void failedDedicatedCommitNeverMakesLegacyCredentialsWritableAgain() {
        InMemorySharedPreferences legacy = new InMemorySharedPreferences();
        InMemorySharedPreferences dedicated = new InMemorySharedPreferences();
        dedicated.failCommits = true;
        legacy.edit()
                .putString(LanFtpPreferences.PREF_KEY_PASSWORD, "legacy")
                .putInt(LanFtpPreferences.PREF_KEY_CONTROL_PORT, 2323)
                .commit();

        SharedPreferences selected = LanFtpPreferences.preferences(
                namedPreferencesContext(legacy, dedicated));

        Assert.assertSame(dedicated, selected);
        Assert.assertEquals("legacy", legacy.getString(
                LanFtpPreferences.PREF_KEY_PASSWORD, null));
        Assert.assertEquals(2323, legacy.getInt(
                LanFtpPreferences.PREF_KEY_CONTROL_PORT, 0));
        Assert.assertFalse(dedicated.contains(LanFtpPreferences.PREF_KEY_PASSWORD));
        Assert.assertFalse(LanFtpPreferences.read(selected).enabled);
    }

    @Test
    public void defaultsAreDisabledWritableAnonymousAndBounded() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        LanFtpPreferences.ensureDefaults(preferences);
        LanFtpPreferences.Snapshot snapshot = LanFtpPreferences.read(preferences);

        Assert.assertFalse(snapshot.enabled);
        Assert.assertEquals(2121, snapshot.controlPort);
        Assert.assertEquals("file:///sdcard", snapshot.rootTreeUri);
        Assert.assertFalse(snapshot.readOnly);
        Assert.assertTrue(snapshot.allowAnonymous);
        Assert.assertEquals("gboard", snapshot.username);
        Assert.assertTrue(snapshot.password.matches("[a-z]{6}"));
        Assert.assertEquals(50000, snapshot.passivePortStart);
        Assert.assertEquals(50009, snapshot.passivePortEnd);
        Assert.assertEquals(4, snapshot.maxSessions);
        Assert.assertEquals(15 * 60_000L, snapshot.idleTimeoutMs);
    }

    @Test
    public void invalidValuesFailClosedToSafeDefaults() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit()
                .putBoolean(LanFtpPreferences.PREF_KEY_ENABLED, true)
                .putInt(LanFtpPreferences.PREF_KEY_CONTROL_PORT, 70000)
                .putString(LanFtpPreferences.PREF_KEY_ROOT_TREE_URI, "not-a-content-uri")
                .putBoolean(LanFtpPreferences.PREF_KEY_READ_ONLY, false)
                .putString(LanFtpPreferences.PREF_KEY_PASSWORD, "short")
                .commit();

        LanFtpPreferences.Snapshot snapshot = LanFtpPreferences.read(preferences);

        Assert.assertFalse(snapshot.enabled);
        Assert.assertEquals(2121, snapshot.controlPort);
        Assert.assertEquals("file:///sdcard", snapshot.rootTreeUri);
        Assert.assertTrue(snapshot.readOnly);
        Assert.assertTrue(snapshot.allowAnonymous);
        Assert.assertEquals("gboard", snapshot.username);
        Assert.assertTrue(snapshot.password.matches("[a-z]{6}"));
    }

    @Test
    public void obsoleteRetainedUploadSecretsArePurgedWithoutDisablingServer() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        LanFtpPreferences.ensureDefaults(preferences);
        preferences.edit()
                .putBoolean(LanFtpPreferences.PREF_KEY_ENABLED, true)
                .putString("pref_lan_ftp_credential_generation", "obsolete")
                .putString("pref_lan_ftp_metadata_auth_key", "obsolete")
                .commit();

        LanFtpPreferences.Snapshot snapshot = LanFtpPreferences.read(preferences);

        Assert.assertTrue(snapshot.enabled);
        Assert.assertFalse(preferences.contains("pref_lan_ftp_credential_generation"));
        Assert.assertFalse(preferences.contains("pref_lan_ftp_metadata_auth_key"));
    }

    @Test
    public void anonymousLoginIsOptionalAndPersisted() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        LanFtpPreferences.ensureDefaults(preferences);

        Assert.assertTrue(LanFtpPreferences.setAllowAnonymous(preferences, true));
        Assert.assertTrue(LanFtpPreferences.read(preferences).allowAnonymous);
        Assert.assertTrue(LanFtpPreferences.setAllowAnonymous(preferences, false));
        Assert.assertFalse(LanFtpPreferences.read(preferences).allowAnonymous);
    }

    @Test
    public void existingSafRootIsPreservedWhenDefaultsAreAdded() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit().putString(
                LanFtpPreferences.PREF_KEY_ROOT_TREE_URI,
                "content://storage/tree/primary%3ADownload").commit();

        LanFtpPreferences.ensureDefaults(preferences);

        Assert.assertEquals("content://storage/tree/primary%3ADownload",
                LanFtpPreferences.read(preferences).rootTreeUri);
    }

    @Test
    public void legacyEmptyRootMigratesToSharedStorageDefault() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit().putString(LanFtpPreferences.PREF_KEY_ROOT_TREE_URI, "").commit();

        LanFtpPreferences.ensureDefaults(preferences);

        Assert.assertEquals(LanFtpPreferences.SHARED_STORAGE_ROOT_URI,
                LanFtpPreferences.read(preferences).rootTreeUri);
    }

    @Test
    public void regeneratePasswordStoresANewSixLetterCredential() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        LanFtpPreferences.ensureDefaults(preferences);
        String previous = LanFtpPreferences.read(preferences).password;
        long previousRevision = LanFtpPreferences.read(preferences).passwordRevision;

        String replacement = LanFtpPreferences.regeneratePassword(preferences);

        Assert.assertNotEquals(previous, replacement);
        Assert.assertEquals(replacement, LanFtpPreferences.read(preferences).password);
        Assert.assertTrue(replacement.matches("[a-z]{6}"));
        Assert.assertEquals(previousRevision + 1L,
                LanFtpPreferences.read(preferences).passwordRevision);
    }

    @Test
    public void customPasswordMustContainExactlySixLowercaseLetters() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        LanFtpPreferences.ensureDefaults(preferences);

        Assert.assertTrue(LanFtpPreferences.setPassword(preferences, " custom "));
        Assert.assertEquals("custom", LanFtpPreferences.read(preferences).password);
        long changedRevision = LanFtpPreferences.read(preferences).passwordRevision;
        Assert.assertTrue(LanFtpPreferences.setPassword(preferences, "custom"));
        Assert.assertEquals(changedRevision,
                LanFtpPreferences.read(preferences).passwordRevision);
        Assert.assertFalse(LanFtpPreferences.setPassword(preferences, "short"));
        Assert.assertFalse(LanFtpPreferences.setPassword(preferences, "ABCDEF"));
        Assert.assertFalse(LanFtpPreferences.setPassword(preferences, "abc123"));
        Assert.assertEquals("custom", LanFtpPreferences.read(preferences).password);
    }

    @Test
    public void passiveRangeIsPersistedValidatedAndCannotOverlapControlPort() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        LanFtpPreferences.ensureDefaults(preferences);

        Assert.assertTrue(LanFtpPreferences.setPassivePortRange(
                preferences, 60000, 60009));
        Assert.assertFalse(LanFtpPreferences.setPassivePortRange(
                preferences, 60000, 60040));
        Assert.assertFalse(LanFtpPreferences.setControlPort(preferences, 60005));

        LanFtpPreferences.Snapshot snapshot = LanFtpPreferences.read(preferences);
        Assert.assertEquals(60000, snapshot.passivePortStart);
        Assert.assertEquals(60009, snapshot.passivePortEnd);
        Assert.assertEquals("gboard", snapshot.username);
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private boolean failCommits;

        @Override public Map<String, ?> getAll() { return Collections.unmodifiableMap(values); }
        @Override public String getString(String key, String value) {
            Object stored = values.get(key); return stored instanceof String ? (String) stored : value;
        }
        @Override public Set<String> getStringSet(String key, Set<String> value) { return value; }
        @Override public int getInt(String key, int value) {
            Object stored = values.get(key); return stored instanceof Number ? ((Number) stored).intValue() : value;
        }
        @Override public long getLong(String key, long value) {
            Object stored = values.get(key); return stored instanceof Number ? ((Number) stored).longValue() : value;
        }
        @Override public float getFloat(String key, float value) { return value; }
        @Override public boolean getBoolean(String key, boolean value) {
            Object stored = values.get(key); return stored instanceof Boolean ? (Boolean) stored : value;
        }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> values) { pending.put(key, values); return this; }
            @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            @Override public Editor remove(String key) { pending.put(key, null); return this; }
            @Override public Editor clear() { values.clear(); pending.clear(); return this; }
            @Override public boolean commit() {
                if (failCommits) return false;
                apply();
                return true;
            }
            @Override public void apply() {
                for (Map.Entry<String, Object> entry : pending.entrySet()) {
                    if (entry.getValue() == null) values.remove(entry.getKey());
                    else values.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private static Context namedPreferencesContext(
            SharedPreferences legacy, SharedPreferences dedicated) {
        return new ContextWrapper(null) {
            @Override public Context getApplicationContext() { return this; }

            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return LanFtpPreferences.PREF_FILE.equals(name) ? dedicated : legacy;
            }
        };
    }
}
