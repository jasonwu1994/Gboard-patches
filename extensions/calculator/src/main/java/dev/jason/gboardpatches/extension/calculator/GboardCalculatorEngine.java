package dev.jason.gboardpatches.extension.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Small, deterministic arithmetic parser used by the keyboard calculator. */
public final class GboardCalculatorEngine {
    static final int MAX_EXPRESSION_LENGTH = 128;
    static final int MAX_BINARY_OPERATIONS = 63;
    private static final int MAX_PARENTHESES_DEPTH = 16;
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
                .replace('\uff09', ')');
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

        private Parser(String expression) {
            this.expression = expression;
        }

        private BigDecimal parseExpression() throws ParseFailure {
            BigDecimal value = parseTerm();
            while (true) {
                skipSpaces();
                if (consume('+')) {
                    recordBinaryOperation();
                    value = value.add(parseTerm(), MATH_CONTEXT);
                } else if (consume('-')) {
                    recordBinaryOperation();
                    value = value.subtract(parseTerm(), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal parseTerm() throws ParseFailure {
            BigDecimal value = parseUnary();
            while (true) {
                skipSpaces();
                if (consume('*')) {
                    recordBinaryOperation();
                    value = value.multiply(parseUnary(), MATH_CONTEXT);
                } else if (consume('/')) {
                    recordBinaryOperation();
                    BigDecimal divisor = parseUnary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("division by zero");
                    }
                    value = value.divide(divisor, MATH_CONTEXT);
                } else {
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
            BigDecimal value = parsePrimary();
            return negative ? value.negate(MATH_CONTEXT) : value;
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
