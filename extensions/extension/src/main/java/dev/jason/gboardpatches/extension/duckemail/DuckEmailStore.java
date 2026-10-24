package dev.jason.gboardpatches.extension.duckemail;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Separate from exportable patch preferences. The bearer token is Keystore encrypted. */
final class DuckEmailStore {
    static final String FILE = "gboard_duck_email_private";
    private static final String KEY_ALIAS = "gboard_patches_duck_email_v1";
    private static final Object LOCK = new Object();
    interface TokenCipher {
        String encrypt(String token) throws Exception;
        String decrypt(String token) throws Exception;
    }
    private final SharedPreferences preferences;
    private final TokenCipher cipher;

    DuckEmailStore(Context context) {
        this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), new KeystoreCipher());
    }
    DuckEmailStore(SharedPreferences preferences, TokenCipher cipher) {
        this.preferences = preferences;
        this.cipher = cipher;
    }

    boolean enabled() { return preferences.getBoolean("enabled", true); }
    void enabled(boolean enabled) { preferences.edit().putBoolean("enabled", enabled).apply(); }
    boolean copyGeneratedEmail() { return preferences.getBoolean("copy_generated_email", false); }
    void copyGeneratedEmail(boolean enabled) {
        preferences.edit().putBoolean("copy_generated_email", enabled).apply();
    }
    String username() { return preferences.getString("username", ""); }
    String pending() { return preferences.getString("pending", ""); }
    String lastAddress() { return preferences.getString("last_address", ""); }
    String revision() { return preferences.getString("revision", ""); }
    boolean signedIn() { return !preferences.getString("token", "").isEmpty(); }
    String token() throws Exception {
        String encrypted = preferences.getString("token", "");
        if (encrypted.isEmpty()) throw new IllegalStateException("Sign in first");
        return cipher.decrypt(encrypted);
    }

    boolean pending(String user, String expectedRevision) {
        synchronized (LOCK) {
            if (!revision().equals(expectedRevision)) return false;
            return preferences.edit().putString("pending", user).commit();
        }
    }

    boolean save(DuckEmailApi.Account account, String expectedRevision) throws Exception {
        String encrypted = cipher.encrypt(account.token());
        synchronized (LOCK) {
            if (!revision().equals(expectedRevision)) return false;
            if (!preferences.edit().putString("username", account.username())
                    .putString("token", encrypted).remove("pending").remove("last_address")
                    .putString("revision", UUID.randomUUID().toString()).commit()) {
                throw new IllegalStateException("Unable to save Duck account");
            }
            return true;
        }
    }

    boolean remember(String address, String expectedRevision) {
        synchronized (LOCK) {
            if (!revision().equals(expectedRevision) || !signedIn()) return false;
            return preferences.edit().putString("last_address", address).commit();
        }
    }

    void signOut() {
        synchronized (LOCK) {
            if (!preferences.edit().remove("token").remove("username").remove("pending")
                    .remove("last_address").putString("revision", UUID.randomUUID().toString())
                    .commit()) throw new IllegalStateException("Unable to sign out");
        }
    }

    private static final class KeystoreCipher implements TokenCipher {
        private SecretKey key() throws Exception {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            SecretKey existing = (SecretKey) store.getKey(KEY_ALIAS, null);
            if (existing != null) return existing;
            KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
        public String encrypt(String token) throws Exception {
            synchronized (LOCK) {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key());
                return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                        + Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),
                        Base64.NO_WRAP);
            }
        }
        public String decrypt(String token) throws Exception {
            synchronized (LOCK) {
                String[] parts = token.split(":", 2);
                if (parts.length != 2) throw new IllegalStateException("Invalid stored credential");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(),
                        new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
                return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                        StandardCharsets.UTF_8);
            }
        }
    }
}
