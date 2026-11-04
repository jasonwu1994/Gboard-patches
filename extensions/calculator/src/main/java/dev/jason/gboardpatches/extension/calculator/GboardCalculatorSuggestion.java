package dev.jason.gboardpatches.extension.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a complete arithmetic expression immediately before the editor cursor. */
public final class GboardCalculatorSuggestion {
    private static final int MAX_FRACTION_DIGITS = 3;
    private static final int SCIENTIFIC_EXPONENT_THRESHOLD = 12;
    private static final Pattern ISO_DATE =
            Pattern.compile("[0-9]{4}-([0-9]{1,2})-([0-9]{1,2})");
    private static final Pattern SLASH_DATE =
            Pattern.compile("([0-9]{1,2})/([0-9]{1,2})/(?:[0-9]{2}|[0-9]{4})");
    private static final Pattern DASH_DATE =
            Pattern.compile("([0-9]{1,2})-([0-9]{1,2})-[0-9]{4}");

    private GboardCalculatorSuggestion() {
    }

    public static Candidate fromTextBeforeCursor(CharSequence textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
            return null;
        }
        int originalEnd = textBeforeCursor.length();
        int end = originalEnd;
        int trailingSpaceCount = 0;
        while (end > 0 && Character.isWhitespace(textBeforeCursor.charAt(end - 1))) {
            if (textBeforeCursor.charAt(end - 1) != ' ') {
                return null;
            }
            trailingSpaceCount++;
            end--;
        }
        if (trailingSpaceCount > 1) {
            return null;
        }
        if (end > 0 && isEquals(textBeforeCursor.charAt(end - 1))) {
            end--;
        }
        int start = end;
        int lowerBound = Math.max(
                0, end - GboardCalculatorEngine.MAX_EXPRESSION_LENGTH);
        while (start > lowerBound && isExpressionCharacterAt(textBeforeCursor, start - 1)) {
            start--;
        }
        if (start == lowerBound && lowerBound > 0
                && isExpressionCharacterAt(textBeforeCursor, lowerBound - 1)) {
            return null;
        }
        // Skip leading spaces, and a leading "x" that belongs to a word, e.g. "box2+3" -> "2+3".
        while (start < end && (Character.isWhitespace(textBeforeCursor.charAt(start))
                || isLetterX(textBeforeCursor.charAt(start)))) {
            start++;
        }
        if (start >= end
                || originalEnd - start > GboardCalculatorEngine.MAX_EXPRESSION_LENGTH
                || hasInvalidWhitespace(textBeforeCursor, start, originalEnd)) {
            return null;
        }
        // The text before a sum can look like part of it ("stop. 3+4", "Room 12 3+4"), so if the
        // longest stretch does not work out, try again from each following word.
        for (int from = start; from < end; from = nextWordStart(textBeforeCursor, from, end)) {
            Candidate candidate = evaluate(textBeforeCursor, from, end, originalEnd);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The next place a sum could start after a space, or {@code end} if there is none. A space
     * straight after an operator is part of the sum ("2 + 3"), so it is not a new start.
     */
    private static int nextWordStart(CharSequence text, int from, int end) {
        for (int index = from + 1; index < end; index++) {
            if (text.charAt(index - 1) == ' '
                    && index - 2 >= from
                    && !isOperator(text.charAt(index - 2))
                    && canStartSum(text.charAt(index))) {
                return index;
            }
        }
        return end;
    }

    private static boolean isOperator(char value) {
        return value == '+' || value == '-' || value == '*' || value == '/' || value == '^'
                || value == '\u2212' || value == '\u00d7' || value == '\u00f7'
                || value == '(' || value == '\uff08' || isLetterX(value);
    }

    private static boolean canStartSum(char value) {
        return Character.isDigit(value)
                || value == '(' || value == '\uff08'
                || value == GboardCalculatorEngine.SQUARE_ROOT
                || value == GboardCalculatorEngine.PI_SYMBOL;
    }

    private static Candidate evaluate(CharSequence textBeforeCursor, int start, int end,
            int originalEnd) {
        String expression = textBeforeCursor.subSequence(start, end).toString();
        if (!hasAtLeastTwoNumbers(expression)
                && expression.indexOf(GboardCalculatorEngine.SQUARE_ROOT) < 0) {
            return null;
        }
        if (isDate(expression)) {
            return null;
        }
        GboardCalculatorEngine.Evaluation evaluation =
                GboardCalculatorEngine.evaluate(expression);
        if (!evaluation.isValid()) {
            return null;
        }
        String currency = currencyOf(expression);
        if (currency == null) {
            return null;
        }
        // The engine only accepts commas as thousands separators, so any comma means the
        // sum was written with them and the result should match.
        boolean grouped = expression.indexOf(',') >= 0;
        String result = formatSuggestionResult(
                evaluation.getResult(), grouped, currency, expression.indexOf('.') >= 0);
        return result.isEmpty()
                ? null
                : new Candidate(expression, result, originalEnd - start);
    }

    /**
     * Full dates would otherwise read as sums (2026-10-17 = 1999). Dashed dates need a four-digit
     * year so short sums like 10-5-20 still work; slashed dates also allow a two-digit year.
     */
    private static boolean isDate(String expression) {
        Matcher iso = ISO_DATE.matcher(expression);
        if (iso.matches()) {
            return isMonthAndDay(iso.group(1), iso.group(2));
        }
        return isDayMonthOrMonthDay(SLASH_DATE.matcher(expression))
                || isDayMonthOrMonthDay(DASH_DATE.matcher(expression));
    }

    /** Accepts both UK (17/10/2026) and US (10/17/2026) order. */
    private static boolean isDayMonthOrMonthDay(Matcher date) {
        return date.matches()
                && (isMonthAndDay(date.group(1), date.group(2))
                        || isMonthAndDay(date.group(2), date.group(1)));
    }

    private static boolean isMonthAndDay(String month, String day) {
        int monthValue = Integer.parseInt(month);
        int dayValue = Integer.parseInt(day);
        return monthValue >= 1 && monthValue <= 12 && dayValue >= 1 && dayValue <= 31;
    }

    private static boolean hasInvalidWhitespace(CharSequence value, int start, int end) {
        boolean previousWasSpace = false;
        for (int index = start; index < end; index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current) && current != ' ') {
                return true;
            }
            boolean currentIsSpace = current == ' ';
            if (currentIsSpace && previousWasSpace) {
                return true;
            }
            previousWasSpace = currentIsSpace;
        }
        return false;
    }

    private static boolean isEquals(char value) {
        return value == '=' || value == '\uff1d';
    }

    private static boolean isLineBreak(char value) {
        return value == '\n'
                || value == '\r'
                || value == '\u0085'
                || value == '\u2028'
                || value == '\u2029';
    }

    private static boolean isExpressionCharacterAt(CharSequence text, int index) {
        char value = text.charAt(index);
        return value == ','
                ? GboardCalculatorEngine.isThousandsSeparator(text, index)
                : isExpressionCharacter(value);
    }

    private static boolean isLetterX(char value) {
        return value == 'x' || value == 'X';
    }

    private static boolean isExpressionCharacter(char value) {
        return Character.isDigit(value)
                || isLetterX(value)
                || value == '%'
                || GboardCalculatorEngine.CURRENCIES.indexOf(value) >= 0
                || value == '\uff05'
                || value == '^'
                || value == GboardCalculatorEngine.SQUARE_ROOT
                || value == GboardCalculatorEngine.PI_SYMBOL
                || (Character.isWhitespace(value) && !isLineBreak(value))
                || value == '.'
                || value == '+'
                || value == '-'
                || value == '*'
                || value == '/'
                || value == '\u2212'
                || value == '\u00d7'
                || value == '\u00f7'
                || value == '('
                || value == ')'
                || value == '\uff08'
                || value == '\uff09';
    }

    private static boolean hasAtLeastTwoNumbers(String expression) {
        int numberCount = 0;
        boolean inNumber = false;
        for (int index = 0; index < expression.length(); index++) {
            char value = expression.charAt(index);
            if (value == GboardCalculatorEngine.PI_SYMBOL) {
                numberCount++;
                if (numberCount >= 2) {
                    return true;
                }
                inNumber = false;
            } else if (Character.isDigit(value)) {
                if (!inNumber) {
                    numberCount++;
                    if (numberCount >= 2) {
                        return true;
                    }
                }
                inNumber = true;
            } else if (value != '.' && value != ',') {
                inNumber = false;
            }
        }
        return false;
    }

    /** The currency sign the sum uses, "" if none, or null if it mixes currencies. */
    private static String currencyOf(String expression) {
        String currency = "";
        for (int index = 0; index < expression.length(); index++) {
            char value = expression.charAt(index);
            if (GboardCalculatorEngine.CURRENCIES.indexOf(value) < 0) {
                continue;
            }
            if (!currency.isEmpty() && currency.charAt(0) != value) {
                return null; // Mixed currencies would need exchange rates.
            }
            currency = String.valueOf(value);
        }
        return currency;
    }

    /**
     * Rounds to 3 decimal places, or to cents for money. Money is shown as a whole amount when
     * the sum had no decimals and the result is whole: $100-15% = $85, £74.99-5% = £71.24.
     */
    private static String formatSuggestionResult(String rawResult, boolean groupThousands,
            String currency, boolean decimalsTyped) {
        try {
            BigDecimal value = new BigDecimal(rawResult);
            BigDecimal normalized = value.stripTrailingZeros();
            int exponent = normalized.precision() - normalized.scale() - 1;
            if (exponent >= SCIENTIFIC_EXPONENT_THRESHOLD) {
                return formatScientific(normalized, exponent);
            }
            boolean money = !currency.isEmpty();
            BigDecimal rounded =
                    value.setScale(money ? 2 : MAX_FRACTION_DIGITS, RoundingMode.HALF_UP);
            boolean cents = money && (decimalsTyped || rounded.stripTrailingZeros().scale() > 0);
            DecimalFormat format = new DecimalFormat(
                    currency + "#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
            format.setGroupingUsed(groupThousands);
            format.setMinimumFractionDigits(cents ? 2 : 0);
            format.setMaximumFractionDigits(rounded.scale());
            return format.format(rounded);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String formatScientific(BigDecimal value, int exponent) {
        BigDecimal coefficient = value.movePointLeft(exponent);
        if (coefficient.scale() > MAX_FRACTION_DIGITS) {
            coefficient = coefficient.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP);
        }
        coefficient = coefficient.stripTrailingZeros();
        if (coefficient.abs().compareTo(BigDecimal.TEN) >= 0) {
            coefficient = coefficient.movePointLeft(1).stripTrailingZeros();
            exponent++;
        }
        return coefficient.toPlainString() + "\u00d710" + superscript(exponent);
    }

    private static String superscript(int value) {
        String plain = Integer.toString(value);
        StringBuilder result = new StringBuilder(plain.length());
        for (int index = 0; index < plain.length(); index++) {
            switch (plain.charAt(index)) {
                case '-': result.append('\u207b'); break;
                case '0': result.append('\u2070'); break;
                case '1': result.append('\u00b9'); break;
                case '2': result.append('\u00b2'); break;
                case '3': result.append('\u00b3'); break;
                case '4': result.append('\u2074'); break;
                case '5': result.append('\u2075'); break;
                case '6': result.append('\u2076'); break;
                case '7': result.append('\u2077'); break;
                case '8': result.append('\u2078'); break;
                case '9': result.append('\u2079'); break;
                default: throw new IllegalArgumentException("Unsupported exponent");
            }
        }
        return result.toString();
    }

    public static final class Candidate {
        private final String expression;
        private final String result;
        private final int deleteBeforeCursorCount;

        private Candidate(String expression, String result, int deleteBeforeCursorCount) {
            this.expression = expression;
            this.result = result;
            this.deleteBeforeCursorCount = deleteBeforeCursorCount;
        }

        public String getExpression() {
            return expression;
        }

        public String getEquation() {
            return expression + "=" + result;
        }

        public String getResult() {
            return result;
        }

        public int getDeleteBeforeCursorCount() {
            return deleteBeforeCursorCount;
        }
    }
}
