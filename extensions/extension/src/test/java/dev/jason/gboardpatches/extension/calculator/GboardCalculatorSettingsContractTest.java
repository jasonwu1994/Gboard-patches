package dev.jason.gboardpatches.extension.calculator;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class GboardCalculatorSettingsContractTest {
    @Test
    public void keyboardToolsGroupEndsWithCalculator() throws Exception {
        String group = read("src/main/java/dev/jason/gboardpatches/extension/keyboard/"
                + "GboardKeyboardToolsSettingsGroupFeature.java");

        int webSearch = group.indexOf("new GboardFloatingWebSearchSettingsFeature(context)");
        int calculator = group.indexOf("new GboardCalculatorSettingsFeature(context)");
        Assert.assertTrue(webSearch >= 0);
        Assert.assertTrue(calculator > webSearch);
        Assert.assertEquals(calculator,
                group.lastIndexOf("new GboardCalculatorSettingsFeature(context)"));
    }

    @Test
    public void usageSectionContainsTheTwoRequestedRowsAndAllLimits() throws Exception {
        String feature = read("src/main/java/dev/jason/gboardpatches/extension/calculator/"
                + "GboardCalculatorSettingsFeature.java");
        String text = read("src/main/settings-text/gboard_settings_text.xml");

        Assert.assertTrue(feature.indexOf("usageTitle,") < feature.indexOf("limitsTitle,"));
        Assert.assertTrue(text.contains("Inline result suggestion"));
        Assert.assertTrue(text.contains("Supported range and limits"));
        Assert.assertTrue(text.contains("Expression length: Up to 128 characters"));
        Assert.assertTrue(text.contains("Binary operations: Up to 63"));
        Assert.assertTrue(text.contains("Parentheses depth: Up to 16 levels"));
        Assert.assertTrue(text.contains("16 significant digits, HALF_EVEN"));
        Assert.assertTrue(text.contains("Up to 3 decimal places, HALF_UP"));
        Assert.assertTrue(text.contains("Scientific notation from 10¹²"));
        Assert.assertTrue(text.contains("Operators: + - * / − × ÷"));
        Assert.assertTrue(text.contains("full-width （）"));
    }

    @Test
    public void portableRuntimeDoesNotReadTheSettingsBackend() throws Exception {
        String runtime = read("../calculator/src/main/java/dev/jason/gboardpatches/extension/"
                + "calculator/GboardCalculatorRuntime.java");
        String lifecycle = read("../calculator/src/main/java/dev/jason/gboardpatches/extension/"
                + "calculator/GboardCalculatorLifecycleRuntime.java");

        Assert.assertFalse(runtime.contains("GboardCalculatorSettings"));
        Assert.assertFalse(runtime.contains("GboardCalculatorPreferenceContract"));
        Assert.assertTrue(runtime.contains("onInputViewStarted(Object receiver, boolean enabled)"));
        Assert.assertFalse(lifecycle.contains("GboardCalculatorSettings"));
        Assert.assertTrue(lifecycle.contains("pref_simple_calculator_enabled"));
        Assert.assertTrue(lifecycle.contains("booleanValue("));
        Assert.assertTrue(lifecycle.contains("receiver, enabled"));
        Assert.assertTrue(lifecycle.contains("GboardCalculatorRuntime.onInputViewStarted("));
    }

    @Test
    public void calculatorToggleDefaultsToDisabledInSettingsAndLifecycle() throws Exception {
        String lifecycle = read("../calculator/src/main/java/dev/jason/gboardpatches/extension/"
                + "calculator/GboardCalculatorLifecycleRuntime.java");

        Assert.assertFalse(GboardCalculatorSettings.DEFAULT_ENABLED);
        Assert.assertTrue(lifecycle.contains("DEFAULT_ENABLED = false"));
    }

    @Test
    public void editorOrchestrationAndSuggestionRenderingAreSeparateModules() throws Exception {
        String runtime = read("../calculator/src/main/java/dev/jason/gboardpatches/extension/"
                + "calculator/GboardCalculatorRuntime.java");
        String view = read("../calculator/src/main/java/dev/jason/gboardpatches/extension/"
                + "calculator/GboardCalculatorSuggestionView.java");
        String snapshot = read("../calculator/src/main/java/dev/jason/gboardpatches/extension/"
                + "calculator/GboardCalculatorDisplaySnapshot.java");

        Assert.assertFalse(runtime.contains("new HorizontalScrollView"));
        Assert.assertFalse(runtime.contains("findKeyboardPanelTop"));
        Assert.assertTrue(view.contains("HorizontalScrollView"));
        Assert.assertTrue(view.contains("findKeyboardPanelTop"));
        Assert.assertTrue(snapshot.contains("matchesCurrent("));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
