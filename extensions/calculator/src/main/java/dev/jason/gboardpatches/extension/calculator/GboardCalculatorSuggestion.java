package dev.jason.gboardpatches.extension.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

/** Finds a complete arithmetic expression immediately before the editor cursor. */
public final class GboardCalculatorSuggestion {
    private static final int MAX_FRACTION_DIGITS = 3;
    private static final int SCIENTIFIC_EXPONENT_THRESHOLD = 12;

    private GboardCalculatorSuggestion() {
    }

    /**
     * Like {@link #fromTextBeforeCursor}, but when the cursor sits inside a sum the whole sum is
     * used, the way Gboard suggests for a whole word rather than the part before the cursor.
     * The text after the cursor is only read when the text before it could end in a sum.
     */
    public static Candidate fromTextAroundCursor(CharSequence textBeforeCursor,
            Supplier<CharSequence> textAfterCursor) {
        if (!couldEndInSum(textBeforeCursor)) {
            return null;
        }
        CharSequence after = textAfterCursor.get();
        int afterLength = continuationLength(after);
        if (afterLength == 0) {
            return fromTextBeforeCursor(textBeforeCursor);
        }
        Candidate whole = fromTextBeforeCursor(
                new StringBuilder(textBeforeCursor).append(after, 0, afterLength));
        if (whole == null || whole.deleteBeforeCursorCount <= afterLength) {
            // Either the whole sum is not valid, or the cursor is in front of it, not inside it.
            return null;
        }
        return new Candidate(whole.expression, whole.result,
                whole.deleteBeforeCursorCount - afterLength, afterLength);
    }

    /** False when the text before the cursor cannot end in a sum, as in most typing. */
    private static boolean couldEndInSum(CharSequence textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
            return false;
        }
        int end = textBeforeCursor.length();
        if (end > 1 && textBeforeCursor.charAt(end - 1) == ' ') {
            end--; // "3+4 " can still end in a sum, but "hello " cannot.
        }
        char last = textBeforeCursor.charAt(end - 1);
        return !Character.isWhitespace(last)
                && (isExpressionCharacter(last) || last == ',' || isEquals(last));
    }

    /** How many characters straight after the cursor carry on the sum before it. */
    private static int continuationLength(CharSequence textAfterCursor) {
        if (textAfterCursor == null) {
            return 0;
        }
        int limit = Math.min(textAfterCursor.length(), GboardCalculatorEngine.MAX_EXPRESSION_LENGTH);
        for (int length = 0; length < limit; length++) {
            char value = textAfterCursor.charAt(length);
            if (isEquals(value)) {
                return length + 1; // A trailing equals sign belongs to the sum.
            }
            char next = length + 1 < textAfterCursor.length()
                    ? textAfterCursor.charAt(length + 1) : ' ';
            if (!continuesSum(value, next)) {
                return length;
            }
        }
        return limit;
    }

    private static boolean continuesSum(char value, char next) {
        if (value == ',') {
            return Character.isDigit(next); // 1,000 but not "3+4, then"
        }
        if (value == 'x' || value == 'X') {
            return !Character.isLetter(next); // 2x3 but not "2+3xyz"
        }
        return !Character.isWhitespace(value) && isExpressionCharacter(value);
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
        while (start > lowerBound && isExpressionCharacter(textBeforeCursor.charAt(start - 1))) {
            start--;
        }
        if (start == lowerBound && lowerBound > 0
                && isExpressionCharacter(textBeforeCursor.charAt(lowerBound - 1))) {
            return null;
        }
        while (start < end && Character.isWhitespace(textBeforeCursor.charAt(start))) {
            start++;
        }
        if (start >= end
                || originalEnd - start > GboardCalculatorEngine.MAX_EXPRESSION_LENGTH
                || hasInvalidWhitespace(textBeforeCursor, start, originalEnd)) {
            return null;
        }
        String expression = textBeforeCursor.subSequence(start, end).toString();
        if (!hasAtLeastTwoNumbers(expression)) {
            return null;
        }
        GboardCalculatorEngine.Evaluation evaluation =
                GboardCalculatorEngine.evaluate(expression);
        if (!evaluation.isValid()) {
            return null;
        }
        String result = formatSuggestionResult(evaluation.getResult());
        return result.isEmpty()
                ? null
                : new Candidate(expression, result, originalEnd - start);
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

    private static boolean isExpressionCharacter(char value) {
        return Character.isDigit(value)
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
            if (Character.isDigit(value)) {
                if (!inNumber) {
                    numberCount++;
                    if (numberCount >= 2) {
                        return true;
                    }
                }
                inNumber = true;
            } else if (value != '.') {
                inNumber = false;
            }
        }
        return false;
    }

    private static String formatSuggestionResult(String rawResult) {
        try {
            BigDecimal value = new BigDecimal(rawResult);
            if (value.compareTo(BigDecimal.ZERO) == 0) {
                return "0";
            }
            BigDecimal normalized = value.stripTrailingZeros();
            int exponent = normalized.precision() - normalized.scale() - 1;
            if (exponent >= SCIENTIFIC_EXPONENT_THRESHOLD) {
                return formatScientific(normalized, exponent);
            }
            BigDecimal rounded = value.scale() > MAX_FRACTION_DIGITS
                    ? value.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
                    : value;
            return rounded.stripTrailingZeros().toPlainString();
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
        private final int deleteAfterCursorCount;

        private Candidate(String expression, String result, int deleteBeforeCursorCount) {
            this(expression, result, deleteBeforeCursorCount, 0);
        }

        private Candidate(String expression, String result, int deleteBeforeCursorCount,
                int deleteAfterCursorCount) {
            this.expression = expression;
            this.result = result;
            this.deleteBeforeCursorCount = deleteBeforeCursorCount;
            this.deleteAfterCursorCount = deleteAfterCursorCount;
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

        public int getDeleteAfterCursorCount() {
            return deleteAfterCursorCount;
        }
    }
}
