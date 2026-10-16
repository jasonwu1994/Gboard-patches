package dev.jason.gboardpatches.extension.duckemail;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class DuckEmailStoreTest {
    private SharedPreferences preferences;
    private DuckEmailStore store;
    @Before public void setUp() {
        preferences = RuntimeEnvironment.getApplication()
                .getSharedPreferences(DuckEmailStore.FILE, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        store = new DuckEmailStore(preferences, new DuckEmailStore.TokenCipher() {
            public String encrypt(String value) { return "encrypted-value"; }
            public String decrypt(String value) {
                assertEquals("encrypted-value", value);
                return "sensitive-token";
            }
        });
    }
    @Test public void persistsOnlyCipherOutputAndRemovesPendingPassphraseUser() throws Exception {
        store.pending("example", store.revision());
        assertTrue(store.save(new DuckEmailApi.Account("example", "sensitive-token"), store.revision()));
        assertTrue(store.signedIn());
        assertEquals("example", store.username());
        assertEquals("", store.pending());
        assertEquals("sensitive-token", store.token());
        assertFalse(preferences.getAll().containsValue("sensitive-token"));
    }
    @Test public void signOutInvalidatesInFlightLoginAndGenerationResults() throws Exception {
        String beforeLogin = store.revision();
        store.signOut();
        assertFalse(store.save(new DuckEmailApi.Account("example", "token"), beforeLogin));
        assertTrue(store.save(new DuckEmailApi.Account("example", "token"), store.revision()));
        String generation = store.revision();
        assertTrue(store.remember("alias@duck.com", generation));
        store.signOut();
        assertFalse(store.remember("late@duck.com", generation));
        assertFalse(store.pending("late", generation));
        assertFalse(store.signedIn());
        assertEquals("", store.lastAddress());
        assertEquals("", store.username());
        assertThrows(IllegalStateException.class, store::token);
    }
    @Test public void encryptionFailureNeverStoresPlaintextOrCreatesASignedInAccount() {
        DuckEmailStore broken = new DuckEmailStore(preferences, new DuckEmailStore.TokenCipher() {
            public String encrypt(String value) throws Exception { throw new Exception("Keystore unavailable"); }
            public String decrypt(String value) { return ""; }
        });
        assertThrows(Exception.class, () -> broken.save(
                new DuckEmailApi.Account("example", "sensitive-token"), broken.revision()));
        assertFalse(broken.signedIn());
        assertFalse(preferences.getAll().containsValue("sensitive-token"));
    }
}
