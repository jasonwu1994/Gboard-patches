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
    public void ignoresFullDatesButKeepsSimilarSums() {
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("2026-10-17"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("Due 17/10/2026"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("10/17/26"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("17-10-2026"));
        Assert.assertEquals("-15", candidate("10-5-20").getResult());
        Assert.assertEquals("2010", candidate("2026-16").getResult());
        Assert.assertEquals("1993", candidate("2026-13-20").getResult());
        Assert.assertEquals("0.067", candidate("20/15/20").getResult());
    }

    @Test
    public void rejectsIncompleteInvalidAndTruncatedExpressions() {
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("42"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("2+"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("2+a"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor(
                "1+".repeat(70) + "1"));
    }

    @Test
    public void suggestsPercentagesInsideText() {
        Assert.assertEquals("74.99-5%=71.241", candidate("Cashback: 74.99-5%").getEquation());
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("50%"));
    }

    @Test
    public void suggestsPowers() {
        Assert.assertEquals("2^10=1024", candidate("2^10").getEquation());
    }

    @Test
    public void suggestsLetterXMultiplicationWithoutCatchingWords() {
        Assert.assertEquals("2073600", candidate("1920x1080").getResult());
        Assert.assertEquals("2+3=5", candidate("box2+3").getEquation());
        Assert.assertEquals(3, candidate("box2+3").getDeleteBeforeCursorCount());
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("3x"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("Linux 6.18"));
    }

    @Test
    public void suggestsImplicitMultiplicationButNotPhoneNumbers() {
        Assert.assertEquals("2(3+4)=14", candidate("2(3+4)").getEquation());
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("(020)7946"));
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("(020) 7946 0958"));
    }

    @Test
    public void readsThousandsSeparatorsWithoutSwallowingProseCommas() {
        Assert.assertEquals("1,299.99-5%=1234.991",
                candidate("Paid 1,299.99-5%").getEquation());
        Assert.assertEquals("4+5=9", candidate("I counted 3, 4+5").getEquation());
        Assert.assertEquals("2+3=5", candidate("1,2+3").getEquation());
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("1,299"));
    }

    @Test
    public void suggestsSquareRootsAloneButPiOnlyInsideASum() {
        Assert.assertEquals("1.414", candidate("\u221a2").getResult());
        Assert.assertEquals("6.283", candidate("2\u03c0").getResult());
        Assert.assertEquals("28.274", candidate("\u03c0*3^2").getResult());
        Assert.assertNull(GboardCalculatorSuggestion.fromTextBeforeCursor("\u03c0"));
    }

    private static GboardCalculatorSuggestion.Candidate candidate(String value) {
        GboardCalculatorSuggestion.Candidate candidate =
                GboardCalculatorSuggestion.fromTextBeforeCursor(value);
        Assert.assertNotNull(candidate);
        return candidate;
    }
}
