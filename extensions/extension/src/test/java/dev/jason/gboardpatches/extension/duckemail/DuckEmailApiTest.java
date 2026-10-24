package dev.jason.gboardpatches.extension.duckemail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class DuckEmailApiTest {
    @Test public void loginExchangesPassphraseThenUsesPermanentGenerationToken() throws Exception {
        List<String> requests = new ArrayList<>();
        DuckEmailApi api = new DuckEmailApi((method, url, token) -> {
            requests.add(method + " " + url);
            String path = URI.create(url).getPath();
            if (path.endsWith("loginlink")) {
                assertNull(token);
                assertEquals("user=example", URI.create(url).getQuery());
                return new DuckEmailApi.Response(200, "");
            }
            if (path.endsWith("/login")) {
                assertNull(token);
                assertEquals("otp=one two three four&user=example",
                        URLDecoder.decode(URI.create(url).getRawQuery(), "UTF-8"));
                return new DuckEmailApi.Response(200, "{\"token\":\"temporary\"}");
            }
            if (path.endsWith("dashboard")) {
                assertEquals("temporary", token);
                return new DuckEmailApi.Response(200,
                        "{\"user\":{\"username\":\"example\",\"access_token\":\"permanent\"}}");
            }
            assertEquals("POST", method);
            assertEquals("permanent", token);
            return new DuckEmailApi.Response(201, "{\"address\":\"private123\"}");
        });
        api.sendCode(" Example@duck.com ");
        DuckEmailApi.Account account = api.signIn("Example@duck.com", " one+two  three\nfour ");
        assertEquals("example", account.username());
        assertEquals("private123@duck.com", api.generate(account.token()));
        assertEquals(4, requests.size());
    }

    @Test public void rejectsForwardingEmailAndQueryInjectionBeforeAnyRequest() {
        DuckEmailApi api = new DuckEmailApi((method, url, token) -> {
            fail("Invalid input must not send an email");
            return null;
        });
        for (String input : new String[] {"", "someone@gmail.com", "x&user=other", "a@duck.com.evil"}) {
            assertThrows(IllegalArgumentException.class, () -> api.sendCode(input));
        }
    }

    @Test public void refusesWrongAccountAndMissingCredentials() {
        for (String dashboard : new String[] {
                "{\"user\":{\"username\":\"other\",\"access_token\":\"secret\"}}",
                "{\"user\":{\"username\":\"example\",\"access_token\":\"\"}}"}) {
            DuckEmailApi api = new DuckEmailApi((method, url, token) ->
                    new DuckEmailApi.Response(200, url.contains("dashboard") ? dashboard
                            : "{\"token\":\"temporary\"}"));
            assertThrows(Exception.class, () -> api.signIn("example", "one two three four"));
        }
    }

    @Test public void rejectsServerErrorsWithoutLeakingResponseAndDoesNotRetryGeneration() {
        for (int status : new int[] {302, 400, 401, 403, 429, 500}) {
            int[] count = {0};
            DuckEmailApi api = new DuckEmailApi((method, url, token) -> {
                count[0]++;
                return new DuckEmailApi.Response(status, "sensitive server response");
            });
            DuckEmailApi.ApiException error = assertThrows(DuckEmailApi.ApiException.class,
                    () -> api.generate("permanent"));
            assertEquals(status, error.status);
            assertFalse(error.getMessage().contains("sensitive"));
            assertEquals(1, count[0]);
        }
    }

    @Test public void acceptsFullAddressButNeverFillsUnexpectedServerText() throws Exception {
        DuckEmailApi api = new DuckEmailApi((method, url, token) ->
                new DuckEmailApi.Response(200, "{\"address\":\"private123@duck.com\"}"));
        assertEquals("private123@duck.com", api.generate("token"));
        for (String body : new String[] {"{}", "{\"address\":null}", "{\"address\":\"x@evil.com\"}", "invalid"}) {
            DuckEmailApi invalid = new DuckEmailApi((method, url, token) ->
                    new DuckEmailApi.Response(200, body));
            assertThrows(Exception.class, () -> invalid.generate("token"));
        }
    }
}
