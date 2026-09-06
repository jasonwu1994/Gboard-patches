package dev.jason.gboardpatches.extension.calculator;

import org.junit.Assert;
import org.junit.Test;

public final class GboardCalculatorSuggestionTest {
    @Test
    public void exposesEquationAndAnswerWithOrWithoutTrailingEquals() {
        GboardCalculatorSuggestion.Candidate plain = candidate("1+1");
        GboardCalculatorSuggestion.Candidate equals = candidate("1+1=");
        GboardCalculatorSuggestion.Candidate fullWidthEquals = candidate("1+1\uff1d");

        Assert.assertEquals("1+1=2", plain.getEquation());
        Assert.assertEquals("2", plain.getResult());
        Assert.assertEquals("1+1=2", equals.getEquation());
        Assert.assertEquals(4, equals.getDeleteBeforeCursorCount());
        Assert.assertEquals("1+1=2", fullWidthEquals.getEquation());
    }

    @Test
    public void roundsDisplayAndUsesPortableScientificNotation() {
        Assert.assertEquals("8987+865.73883=9852.739",
                candidate("8987+865.73883").getEquation());
        Assert.assertEquals("1.54\u00d710\u00b2\u00b9",
                candidate("1540000000000*1000000000").getResult());
        Assert.assertEquals("999999999999",
                candidate("999999999999+0").getResult());
    }

    @Test
    public void acceptsSingleSpacesAndFullWidthParenthesesButRejectsInvalidWhitespace() {
        Assert.assertEquals("1 + 1=2", candidate("1 + 1 ").getEquation());
        Assert.assertEquals("\uff082+3\uff09/5=1",
                candidate("\uff082+3\uff09/5").getEquation());
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("1+1  "));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("1  +1"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("1+\t1"));
    }

    @Test
    public void lineBreaksAreHardBoundariesAndEarlierExpressionsDoNotInterfere() {
        GboardCalculatorSuggestion.Candidate candidate = candidate(
                "8.44-67755877785543678=\n\n\n"
                        + "\uff081+2\uff09\u00f71+-*\n\n\n\n1+11");
        Assert.assertEquals("1+11=12", candidate.getEquation());
        Assert.assertEquals(4, candidate.getDeleteBeforeCursorCount());
        Assert.assertEquals("2+3=5", candidate("1+\n2+3").getEquation());
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("1+11\n"));
    }

    @Test
    public void rejectsIncompleteInvalidAndTruncatedExpressions() {
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("42"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("2+"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("2+a"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor(
                "1+".repeat(70) + "1"));
    }

    private static GboardCalculatorSuggestion.Candidate candidate(String value) {
        GboardCalculatorSuggestion.Candidate candidate =
                GboardCalculatorSuggestion.fromTextBeforeCursor(value);
        Assert.assertNotNull(candidate);
        return candidate;
    }
}
