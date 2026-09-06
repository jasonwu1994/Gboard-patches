package dev.jason.gboardpatches.extension.calculator;

import org.junit.Assert;
import org.junit.Test;

public final class GboardCalculatorEngineTest {
    @Test
    public void appliesOperatorPrecedenceParenthesesAndUnicodeOperators() {
        assertResult("2+3*4", "14");
        assertResult("(2+3)*4", "20");
        assertResult("\uff082+3\uff09/5", "1");
        assertResult("12.5\u00d72\u22125\u00f72", "22.5");
    }

    @Test
    public void supportsUnarySignsAndSixteenDigitArithmeticPrecision() {
        assertResult("-2*-3", "6");
        assertResult("-(2+3)", "-5");
        assertResult("12345678901234567+0", "12345678901234570");
    }

    @Test
    public void distinguishesIncompleteInvalidAndOversizedExpressions() {
        Assert.assertEquals(GboardCalculatorEngine.Status.INCOMPLETE,
                GboardCalculatorEngine.evaluate("2+").getStatus());
        Assert.assertEquals(GboardCalculatorEngine.Status.INCOMPLETE,
                GboardCalculatorEngine.evaluate("(2+3").getStatus());
        Assert.assertEquals(GboardCalculatorEngine.Status.ERROR,
                GboardCalculatorEngine.evaluate("2+a").getStatus());
        Assert.assertEquals(GboardCalculatorEngine.Status.ERROR,
                GboardCalculatorEngine.evaluate("1/0").getStatus());
        Assert.assertEquals(GboardCalculatorEngine.Status.ERROR,
                GboardCalculatorEngine.evaluate(" ".repeat(128) + "1+1").getStatus());
    }

    @Test
    public void enforcesOperatorAndParenthesesBudgets() {
        assertResult("1+".repeat(63) + "1", "64");
        Assert.assertEquals(GboardCalculatorEngine.Status.ERROR,
                GboardCalculatorEngine.evaluate("1+".repeat(64) + "1").getStatus());
        assertResult("(".repeat(16) + "1+1" + ")".repeat(16), "2");
        Assert.assertEquals(GboardCalculatorEngine.Status.ERROR,
                GboardCalculatorEngine.evaluate(
                        "(".repeat(17) + "1+1" + ")".repeat(17)).getStatus());
    }

    @Test
    public void handlesMaximumLengthNumbersAndUnaryPrefixesWithinFixedBudgets() {
        String left = "1" + "0".repeat(62);
        String right = "1" + "0".repeat(63);
        assertResult(left + "*" + right, "1" + "0".repeat(125));
        assertResult("-".repeat(120) + "1+1", "2");
    }

    private static void assertResult(String expression, String expected) {
        GboardCalculatorEngine.Evaluation evaluation =
                GboardCalculatorEngine.evaluate(expression);
        Assert.assertEquals(GboardCalculatorEngine.Status.VALID, evaluation.getStatus());
        Assert.assertEquals(expected, evaluation.getResult());
    }
}
