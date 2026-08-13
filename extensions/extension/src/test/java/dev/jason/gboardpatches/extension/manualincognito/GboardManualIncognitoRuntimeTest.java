package dev.jason.gboardpatches.extension.manualincognito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public final class GboardManualIncognitoRuntimeTest {
    @After
    public void resetSession() {
        GboardManualIncognitoRuntime.setSessionForTests(null);
    }

    @Test
    public void manualSessionPromotesIncognitoWithoutCancellingStockPrivacy() {
        GboardManualIncognitoRuntime.setSessionForTests(session(
                true, false, false, false));

        Assert.assertTrue(GboardManualIncognitoRuntime.applyIncognitoPredicate(false));
        Assert.assertTrue(GboardManualIncognitoRuntime.applyIncognitoPredicate(true));
        Assert.assertTrue(GboardManualIncognitoRuntime.applyClipboardIncognitoPolicy(false));
        Assert.assertTrue(GboardManualIncognitoRuntime.applyVoiceIncognitoPolicy(false));
    }

    @Test
    public void clipboardAndVoiceAllowancesOnlyOpenTheirOwnIncognitoGates() {
        GboardManualIncognitoRuntime.setSessionForTests(session(
                true, true, true, false));

        Assert.assertFalse(GboardManualIncognitoRuntime.applyClipboardIncognitoPolicy(false));
        Assert.assertFalse(GboardManualIncognitoRuntime.applyVoiceIncognitoPolicy(false));
        Assert.assertFalse(GboardManualIncognitoRuntime.applyDictationIncognitoFlag(true));
    }

    @Test
    public void dictationFlagIsUnchangedOutsideAllowedIncognitoSession() {
        GboardManualIncognitoRuntime.setSessionForTests(session(
                false, false, true, false));
        Assert.assertTrue(GboardManualIncognitoRuntime.applyDictationIncognitoFlag(true));

        GboardManualIncognitoRuntime.setSessionForTests(session(
                false, false, true, true));
        Assert.assertFalse(GboardManualIncognitoRuntime.applyDictationIncognitoFlag(true));
    }

    @Test
    public void accessPointTokenHelpersAreIdempotentAndPreserveOrder() {
        Assert.assertEquals(
                Arrays.asList("translate", "manual_incognito", "clipboard"),
                GboardManualIncognitoAccessPoint1777Support.appendToken(
                        Arrays.asList(
                                "translate",
                                "manual_incognito",
                                "clipboard",
                                "translate")));
        Assert.assertEquals(
                Arrays.asList("translate", "clipboard"),
                GboardManualIncognitoAccessPoint1777Support.removeToken(
                        Arrays.asList("translate", "manual_incognito", "clipboard")));
    }

    private static GboardManualIncognitoSessionPolicy session(
            boolean manual, boolean clipboard, boolean voice, boolean stock) {
        return new GboardManualIncognitoSessionPolicy(
                manual, clipboard, voice, stock, 1L);
    }
}
