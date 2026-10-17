package dev.jason.gboardpatches.extension.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Small, deterministic arithmetic parser used by the keyboard calculator. */
public final class GboardCalculatorEngine {
    static final int MAX_EXPRESSION_LENGTH = 128;
    static final int MAX_BINARY_OPERATIONS = 63;
    private static final int MAX_PARENTHESES_DEPTH = 16;
    private static final int MAX_DECIMAL_EXPONENT = 999;
    private static final MathContext MATH_CONTEXT =
            new MathContext(16, RoundingMode.HALF_EVEN);

    private GboardCalculatorEngine() {
    }

    public static Evaluation evaluate(String rawExpression) {
        if (rawExpression != null && rawExpression.length() > MAX_EXPRESSION_LENGTH) {
            return Evaluation.error("Expression is too long");
        }
        String expression = normalize(rawExpression);
        if (expression.isEmpty()) {
            return Evaluation.empty();
        }
        try {
            Parser parser = new Parser(expression);
            BigDecimal value = parser.parseExpression();
            parser.skipSpaces();
            if (!parser.isAtEnd()) {
                return Evaluation.error("Unexpected character");
            }
            checkMagnitude(value);
            return Evaluation.valid(format(value));
        } catch (ParseFailure failure) {
            return failure.incomplete
                    ? Evaluation.incomplete()
                    : Evaluation.error(failure.getMessage());
        } catch (ArithmeticException failure) {
            return Evaluation.error("Cannot divide by zero");
        }
    }

    private static String normalize(String rawExpression) {
        if (rawExpression == null) {
            return "";
        }
        return rawExpression.trim()
                .replace('\u2212', '-')
                .replace('\u00d7', '*')
                .replace('\u00f7', '/')
                .replace('\uff08', '(')
                .replace('\uff09', ')')
                .replace('\uff05', '%')
                .replace("**", "^");
    }

    private static void checkMagnitude(BigDecimal value) throws ParseFailure {
        if (value.signum() == 0) {
            return;
        }
        long decimalExponent = (long) value.precision() - value.scale() - 1;
        if (Math.abs(decimalExponent) > MAX_DECIMAL_EXPONENT) {
            throw ParseFailure.invalid("Result is too large");
        }
    }

    private static String format(BigDecimal value) {
        BigDecimal normalized = value.round(MATH_CONTEXT).stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return normalized.toPlainString();
    }

    public enum Status {
        EMPTY,
        INCOMPLETE,
        VALID,
        ERROR
    }

    public static final class Evaluation {
        private final Status status;
        private final String result;
        private final String message;

        private Evaluation(Status status, String result, String message) {
            this.status = status;
            this.result = result;
            this.message = message;
        }

        public Status getStatus() {
            return status;
        }

        public String getResult() {
            return result;
        }

        public String getMessage() {
            return message;
        }

        public boolean isValid() {
            return status == Status.VALID;
        }

        private static Evaluation empty() {
            return new Evaluation(Status.EMPTY, "", "");
        }

        private static Evaluation incomplete() {
            return new Evaluation(Status.INCOMPLETE, "", "");
        }

        private static Evaluation valid(String result) {
            return new Evaluation(Status.VALID, result, "");
        }

        private static Evaluation error(String message) {
            return new Evaluation(Status.ERROR, "",
                    message == null ? "Invalid expression" : message);
        }
    }

    private static final class Parser {
        private final String expression;
        private int index;
        private int depth;
        private int binaryOperations;
        // Set when the most recent factor or term was a bare percentage such as "5%",
        // so "+" and "-" can take it as a percentage of the term before them.
        private boolean lastFactorWasPercent;
        private boolean lastTermWasPercent;

        private Parser(String expression) {
            this.expression = expression;
        }

        /**
         * After "+" or "-", a bare percentage is taken of the term just before it, sign included,
         * matching Google: 10+5-10% = 10+5-0.5 = 14.5 and 20-5+10% = 20-5-0.5 = 14.5. When that
         * term was itself a percentage, percentages stack on the running total instead, the way
         * discounts do: 100-10%-10% = 81.
         */
        private BigDecimal parseExpression() throws ParseFailure {
            BigDecimal value = parseTerm();
            BigDecimal previousTerm = value;
            boolean previousWasPercent = false;
            while (true) {
                skipSpaces();
                boolean add = consume('+');
                if (!add && !consume('-')) {
                    return value;
                }
                recordBinaryOperation();
                BigDecimal term = parseTerm();
                boolean percent = lastTermWasPercent;
                if (percent) {
                    BigDecimal base = previousWasPercent ? value : previousTerm;
                    term = base.multiply(term, MATH_CONTEXT);
                }
                BigDecimal signedTerm = add ? term : term.negate();
                value = value.add(signedTerm, MATH_CONTEXT);
                previousTerm = signedTerm;
                previousWasPercent = percent;
            }
        }

        private BigDecimal parseTerm() throws ParseFailure {
            BigDecimal value = parseUnary();
            boolean percent = lastFactorWasPercent;
            while (true) {
                if (peek('(')) {
                    // Implicit multiplication, only when directly adjacent: 2(3+4), (1+2)(3+4).
                    recordBinaryOperation();
                    value = value.multiply(parseUnary(), MATH_CONTEXT);
                    percent = false;
                    continue;
                }
                skipSpaces();
                if (consume('*') || consume('x') || consume('X')) {
                    recordBinaryOperation();
                    value = value.multiply(parseUnary(), MATH_CONTEXT);
                    percent = false;
                } else if (consume('/')) {
                    recordBinaryOperation();
                    BigDecimal divisor = parseUnary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("division by zero");
                    }
                    value = value.divide(divisor, MATH_CONTEXT);
                    percent = false;
                } else {
                    lastTermWasPercent = percent;
                    return value;
                }
            }
        }

        private BigDecimal parseUnary() throws ParseFailure {
            skipSpaces();
            boolean negative = false;
            while (true) {
                if (consume('+')) {
                    skipSpaces();
                } else if (consume('-')) {
                    negative = !negative;
                    skipSpaces();
                } else {
                    break;
                }
            }
            BigDecimal value = parsePower();
            return negative ? value.negate(MATH_CONTEXT) : value;
        }

        /** "^" binds tighter than a leading minus (-2^2 = -4) and groups right to left (2^3^2 = 512). */
        private BigDecimal parsePower() throws ParseFailure {
            BigDecimal base = parsePercent();
            boolean percent = lastFactorWasPercent;
            int beforeSpaces = index;
            skipSpaces();
            if (consume('^')) {
                recordBinaryOperation();
                base = power(base, parseUnary());
                percent = false;
            } else {
                index = beforeSpaces;
            }
            lastFactorWasPercent = percent;
            return base;
        }

        private BigDecimal parsePercent() throws ParseFailure {
            BigDecimal value = parsePrimary();
            boolean percent = consume('%');
            lastFactorWasPercent = percent;
            return percent ? value.movePointLeft(2) : value;
        }

        private BigDecimal power(BigDecimal base, BigDecimal exponent) throws ParseFailure {
            BigDecimal whole = exponent.stripTrailingZeros();
            if (whole.scale() > 0) {
                throw ParseFailure.invalid("Exponent must be a whole number");
            }
            BigDecimal result = base.pow(whole.intValueExact(), MATH_CONTEXT);
            checkMagnitude(result);
            return result;
        }

        private BigDecimal parsePrimary() throws ParseFailure {
            skipSpaces();
            if (isAtEnd()) {
                throw ParseFailure.incomplete();
            }
            if (consume('(')) {
                depth++;
                if (depth > MAX_PARENTHESES_DEPTH) {
                    throw ParseFailure.invalid("Too many parentheses");
                }
                BigDecimal value = parseExpression();
                skipSpaces();
                if (!consume(')')) {
                    throw isAtEnd()
                            ? ParseFailure.incomplete()
                            : ParseFailure.invalid("Missing closing parenthesis");
                }
                depth--;
                return value;
            }
            return parseNumber();
        }

        private BigDecimal parseNumber() throws ParseFailure {
            int start = index;
            boolean decimalSeen = false;
            boolean digitSeen = false;
            while (!isAtEnd()) {
                char value = expression.charAt(index);
                if (Character.isDigit(value)) {
                    digitSeen = true;
                    index++;
                } else if (value == '.' && !decimalSeen) {
                    decimalSeen = true;
                    index++;
                } else {
                    break;
                }
            }
            if (!digitSeen) {
                throw ParseFailure.invalid("Number expected");
            }
            try {
                return new BigDecimal(expression.substring(start, index), MATH_CONTEXT);
            } catch (NumberFormatException failure) {
                throw ParseFailure.invalid("Invalid number");
            }
        }

        private void recordBinaryOperation() throws ParseFailure {
            binaryOperations++;
            if (binaryOperations > MAX_BINARY_OPERATIONS) {
                throw ParseFailure.invalid("Too many operations");
            }
        }

        private boolean peek(char expected) {
            return !isAtEnd() && expression.charAt(index) == expected;
        }

        private boolean consume(char expected) {
            if (!isAtEnd() && expression.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (!isAtEnd() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }

        private boolean isAtEnd() {
            return index >= expression.length();
        }
    }

    private static final class ParseFailure extends Exception {
        private final boolean incomplete;

        private ParseFailure(String message, boolean incomplete) {
            super(message);
            this.incomplete = incomplete;
        }

        private static ParseFailure incomplete() {
            return new ParseFailure("", true);
        }

        private static ParseFailure invalid(String message) {
            return new ParseFailure(message, false);
        }
    }
}
