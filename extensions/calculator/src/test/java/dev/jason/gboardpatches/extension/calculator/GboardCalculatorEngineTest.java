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

    @Test
    public void appliesPercentagesToTheTermBeforeAdditionOrSubtraction() {
        assertResult("382-2%", "374.36");
        assertResult("74.99-5%", "71.2405");
        assertResult("50+20%", "60");
        assertResult("10+5-10%", "14.5");
        assertResult("20-5+10%", "14.5");
        assertResult("1+2*5+10%", "12");
        assertResult("100-10%-10%", "81");
        assertResult("50+10%+10%", "60.5");
        assertResult("200-(10+5)%", "170");
        assertResult("100 - -5%", "105");
        assertResult("50+20\uff05", "60");
    }

    @Test
    public void treatsOtherPercentagesAsHundredths() {
        assertResult("5%", "0.05");
        assertResult("80*15%", "12");
        assertResult("100/50%", "200");
        assertResult("5%+10", "10.05");
        assertError("5%3");
    }

    @Test
    public void evaluatesWholeNumberPowersRightToLeft() {
        assertResult("2^10", "1024");
        assertResult("2 ^ 3", "8");
        assertResult("2**3", "8");
        assertResult("2 ** 3 ** 2", "512");
        assertResult("2^3^2", "512");
        assertResult("-2^2", "-4");
        assertResult("(-2)^2", "4");
        assertResult("2^-1", "0.5");
        assertResult("0^0", "1");
        assertResult("10^999", "1" + "0".repeat(999));
        assertError("2^0.5");
        assertError("10^1000");
        assertError("2^9999999999");
        assertError("0^-1");
        assertError("(10^999)^2");
        assertError("10^999*10");
    }

    @Test
    public void acceptsLetterXAsMultiplicationBetweenNumbers() {
        assertResult("2x3", "6");
        assertResult("2 X 3", "6");
        assertResult("1920x1080", "2073600");
        assertError("x2");
    }

    @Test
    public void multipliesImplicitlyOnlyWhenParenthesisDirectlyFollows() {
        assertResult("2(3+4)", "14");
        assertResult("(1+2)(3+4)", "21");
        assertResult("2(3)^2", "18");
        assertError("2 (3)");
        assertError("(020)7946");
        assertError("(020) 7946 0958");
    }

    @Test
    public void readsCommaThousandsSeparators() {
        assertResult("1,299.99-5%", "1234.9905");
        assertResult("1,000,000/4", "250000");
        assertError("1,23");
        assertError("1,2345");
        assertError("1234,567");
        assertError("1.5,000");
    }

    private static void assertError(String expression) {
        Assert.assertEquals(expression, GboardCalculatorEngine.Status.ERROR,
                GboardCalculatorEngine.evaluate(expression).getStatus());
    }

    private static void assertResult(String expression, String expected) {
        GboardCalculatorEngine.Evaluation evaluation =
                GboardCalculatorEngine.evaluate(expression);
        Assert.assertEquals(GboardCalculatorEngine.Status.VALID, evaluation.getStatus());
        Assert.assertEquals(expected, evaluation.getResult());
    }
}
