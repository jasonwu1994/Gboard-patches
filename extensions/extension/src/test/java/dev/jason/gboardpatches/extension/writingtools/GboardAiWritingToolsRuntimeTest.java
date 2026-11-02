package dev.jason.gboardpatches.extension.writingtools;

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.Properties;

public final class GboardAiWritingToolsRuntimeTest {
    private static final String[][] STOCK_REMOTE_MODELS = new String[][]{
            {"writing_helper_model_version", "202406101250_prod_sd_config"},
            {"writing_helper_voice_input_model_version", "202406101250_prod_sd_config"},
            {"writing_helper_text_stylization_model_version", "composer_stylization_base"}
    };

    @Test
    public void masterOffPreservesEveryStockResultIdentity() {
        Object original = Long.valueOf(3L);

        Assert.assertSame(original, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_BACKEND_TYPE,
                original,
                settings(false, true,
                        GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA),
                official(true, true)));
    }

    @Test
    public void targetBooleanFlagsUseTheFormalStockCapabilityTuple() {
        GboardAiWritingToolsSettings.Snapshot settings = serverSettings(false);
        GboardAiWritingToolsOfficialPreferences.Snapshot proofreadOff = official(false, true);
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_CONFIG_PROOFREAD,
                Boolean.TRUE,
                settings,
                proofreadOff));
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_WRITING_TOOLS,
                Boolean.FALSE,
                settings,
                proofreadOff));

        GboardAiWritingToolsOfficialPreferences.Snapshot writingToolsOff = official(true, false);
        String[] editingFlags = new String[]{
                GboardAiWritingToolsRuntime.FLAG_WRITING_TOOLS,
                GboardAiWritingToolsRuntime.FLAG_ENABLE_WRITING_TOOLS_COOPERATIVE_MODE,
                GboardAiWritingToolsRuntime.FLAG_WRITING_HELPER_ON_SELECTED_TEXT,
                GboardAiWritingToolsRuntime
                        .FLAG_WRITING_HELPER_ENABLE_TEXT_STYLIZATION_INTERNAL
        };
        for (String flag : editingFlags) {
            Assert.assertSame(flag, Boolean.TRUE,
                    GboardAiWritingToolsRuntime.computeOverrideValue(
                            flag,
                            Boolean.TRUE,
                            settings,
                            writingToolsOff));
        }
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_WRITING_HELPER,
                Boolean.FALSE,
                settings,
                writingToolsOff));
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_ENABLE_WRITING_TOOLS_FOR_MINORS,
                Boolean.FALSE,
                settings,
                writingToolsOff));
    }

    @Test
    public void voiceCommandRolloutFlagIsEnabledWithoutChangingWrongTypes() {
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_ENABLE_WRITING_TOOLS_VOICE_COMMANDS,
                Boolean.FALSE,
                serverSettings(false),
                official(false, false)));

        Object wrongType = "false";
        Assert.assertSame(wrongType, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_ENABLE_WRITING_TOOLS_VOICE_COMMANDS,
                wrongType,
                serverSettings(false),
                official(true, true)));
    }

    @Test
    public void unknownOfficialValuesStayEnabledForReachability() {
        GboardAiWritingToolsOfficialPreferences.Snapshot unknown = official(null, null);

        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_CONFIG_PROOFREAD,
                Boolean.FALSE,
                serverSettings(false),
                unknown));
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_WRITING_TOOLS,
                Boolean.FALSE,
                serverSettings(false),
                unknown));
    }

    @Test
    public void allKeyboardsChangesOnlyTwoStringAllowlists() {
        GboardAiWritingToolsSettings.Snapshot enabled = serverSettings(true);
        Assert.assertEquals("*", GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_WRITING_HELPER_SUPPORTED_LANGUAGE_TAGS,
                "en-US",
                enabled,
                official(true, true)));
        Assert.assertEquals("*", GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_LLM_PC_SUPPORTED_LANGUAGE_TAGS,
                "en-US",
                enabled,
                official(true, true)));

        Object wrongType = Integer.valueOf(7);
        Assert.assertSame(wrongType, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_WRITING_HELPER_SUPPORTED_LANGUAGE_TAGS,
                wrongType,
                enabled,
                official(true, true)));
    }

    @Test
    public void serverUsesVerifiedPixel11RequestModelConfigs() throws Exception {
        Properties pixel11 = pixel11ServerModels();
        for (boolean allKeyboards : new boolean[]{false, true}) {
            for (String[] model : STOCK_REMOTE_MODELS) {
                String expected = pixel11.getProperty(model[0]);
                Assert.assertNotNull("Missing independent model pin: " + model[0], expected);
                Assert.assertEquals(model[0], expected,
                        GboardAiWritingToolsRuntime.computeOverrideValue(
                                model[0], model[1], serverSettings(allKeyboards),
                                official(true, true)));
            }
        }
    }

    @Test
    public void masterOffPreservesAllServerModelConfigs() {
        for (String[] model : STOCK_REMOTE_MODELS) {
            Assert.assertSame(model[0], model[1],
                    GboardAiWritingToolsRuntime.computeOverrideValue(
                            model[0], model[1],
                            settings(false, true,
                                    GboardAiWritingToolsSettings.BACKEND_GBOARD_SERVER),
                            official(true, true)));
        }
    }

    @Test
    public void serverModelConfigsPreserveWrongTypeIdentity() {
        Object[] wrongTypes = new Object[]{
                Integer.valueOf(7), Long.valueOf(7L), Boolean.FALSE, new Object(), null
        };
        for (String[] model : STOCK_REMOTE_MODELS) {
            for (Object wrongType : wrongTypes) {
                Assert.assertSame(model[0], wrongType,
                        GboardAiWritingToolsRuntime.computeOverrideValue(
                                model[0], wrongType, serverSettings(true),
                                official(true, true)));
            }
        }
    }

    @Test
    public void privateBackendsPreserveTheirStockModelConfigs() {
        String[] backends = new String[]{
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_AICORE,
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA
        };
        for (String backend : backends) {
            for (String[] model : STOCK_REMOTE_MODELS) {
                Assert.assertSame(backend + ":" + model[0], model[1],
                        GboardAiWritingToolsRuntime.computeOverrideValue(
                                model[0], model[1], settings(true, true, backend),
                                official(true, true)));
            }
        }
    }

    @Test
    public void serverModelPinsLeaveUnrelatedStringsAndEndpointUntouched() {
        String[][] untouched = new String[][]{
                {"unrelated_string", "original-value"},
                {"writing_helper_server_target",
                        "staging-gboard-imagen-pa.sandbox.googleapis.com"}
        };
        for (String[] entry : untouched) {
            Assert.assertSame(entry[0], entry[1],
                    GboardAiWritingToolsRuntime.computeOverrideValue(
                            entry[0], entry[1], serverSettings(true),
                            official(true, true)));
        }
    }

    @Test
    public void unrelatedAndWrongTypesStayUnchanged() {
        Object unrelated = new Object();
        Assert.assertSame(unrelated, GboardAiWritingToolsRuntime.computeOverrideValue(
                "unrelated_flag",
                unrelated,
                serverSettings(false),
                official(true, true)));

        Object wrongType = "false";
        Assert.assertSame(wrongType, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_CONFIG_PROOFREAD,
                wrongType,
                serverSettings(false),
                official(false, false)));

    }

    @Test
    public void distinctFalseOfficialBooleanIsOverridden() throws Exception {
        Boolean distinctFalse = distinctBoolean(false);
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_CONFIG_PROOFREAD,
                distinctFalse,
                serverSettings(false),
                official(true, true)));
    }

    @Test
    public void distinctTrueOfficialBooleanIsOverridden() throws Exception {
        Boolean distinctTrue = distinctBoolean(true);
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_WRITING_TOOLS,
                distinctTrue,
                serverSettings(false),
                official(true, false)));
    }

    @Test
    public void modelessRegexPromotesOnlyAnEmptyString() {
        Assert.assertEquals("v3", GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_MODELESS_SMART_EDIT_REGEX_VERSION,
                "",
                serverSettings(false),
                official(false, false)));
        String stock = "v4";
        Assert.assertSame(stock, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_MODELESS_SMART_EDIT_REGEX_VERSION,
                stock,
                serverSettings(false),
                official(false, false)));
    }

    @Test
    public void backendDecisionReturnsOneAtomicThreeValueTuple() {
        assertBackend(
                GboardAiWritingToolsSettings.BACKEND_GBOARD_SERVER,
                1L,
                false,
                false);
        assertBackend(
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_AICORE,
                2L,
                true,
                true);
        assertBackend(
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA,
                3L,
                true,
                false);
        Assert.assertNull(GboardAiWritingToolsRuntime.backendDecision("INVALID").backendType);
        Long stock = Long.valueOf(4L);
        Assert.assertSame(stock, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_BACKEND_TYPE, stock,
                settings(true, false, "INVALID"), official(true, true)));
    }

    @Test
    public void backendFlagsPreserveWrongTypeIdentity() {
        GboardAiWritingToolsSettings.Snapshot astrea = settings(
                true,
                false,
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA);
        Assert.assertEquals(Long.valueOf(3L), GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_BACKEND_TYPE,
                Long.valueOf(1L),
                astrea,
                official(true, true)));
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_HYBRID,
                Boolean.FALSE,
                astrea,
                official(true, true)));
        Assert.assertSame(Boolean.FALSE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_ON_DEVICE_PROOFREAD,
                Boolean.FALSE,
                astrea,
                official(true, true)));

        Integer wrongLongType = Integer.valueOf(1);
        Assert.assertSame(wrongLongType, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_BACKEND_TYPE,
                wrongLongType,
                astrea,
                official(true, true)));
    }

    @Test
    public void distinctFalseBackendBooleanIsOverridden() throws Exception {
        Boolean distinctFalse = distinctBoolean(false);
        Assert.assertSame(Boolean.TRUE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_HYBRID,
                distinctFalse,
                settings(
                        true,
                        false,
                        GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA),
                official(true, true)));
    }

    @Test
    public void distinctTrueBackendBooleanIsOverridden() throws Exception {
        Boolean distinctTrue = distinctBoolean(true);
        Assert.assertSame(Boolean.FALSE, GboardAiWritingToolsRuntime.computeOverrideValue(
                GboardAiWritingToolsRuntime.FLAG_ON_DEVICE_PROOFREAD,
                distinctTrue,
                settings(
                        true,
                        false,
                        GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA),
                official(true, true)));
    }

    private static Properties pixel11ServerModels() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = GboardAiWritingToolsRuntimeTest.class.getResourceAsStream(
                "/writingtools/pixel11-18.5-server-models.properties")) {
            Assert.assertNotNull("Missing independently sourced Pixel11 model fixture", input);
            properties.load(input);
        }
        return properties;
    }

    private static Boolean distinctBoolean(boolean value) throws Exception {
        return Boolean.class.getDeclaredConstructor(boolean.class).newInstance(value);
    }

    private static void assertBackend(String backend, long type, boolean hybrid,
            boolean onDeviceProofread) {
        GboardAiWritingToolsRuntime.BackendDecision decision =
                GboardAiWritingToolsRuntime.backendDecision(backend);
        Assert.assertEquals(Long.valueOf(type), decision.backendType);
        Assert.assertEquals(Boolean.valueOf(hybrid), decision.hybridEnabled);
        Assert.assertEquals(Boolean.valueOf(onDeviceProofread),
                decision.onDeviceProofreadEnabled);
    }

    private static GboardAiWritingToolsSettings.Snapshot serverSettings(
            boolean allKeyboardsEnabled) {
        return settings(
                true,
                allKeyboardsEnabled,
                GboardAiWritingToolsSettings.BACKEND_GBOARD_SERVER);
    }

    private static GboardAiWritingToolsSettings.Snapshot settings(boolean featureEnabled,
            boolean allKeyboardsEnabled, String backendType) {
        return new GboardAiWritingToolsSettings.Snapshot(
                featureEnabled,
                allKeyboardsEnabled,
                backendType);
    }

    private static GboardAiWritingToolsOfficialPreferences.Snapshot official(
            Boolean proofreadEnabled, Boolean writingToolsEnabled) {
        return new GboardAiWritingToolsOfficialPreferences.Snapshot(
                proofreadEnabled,
                writingToolsEnabled);
    }
}
