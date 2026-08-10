package com.los.core.service.underwriting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Safe arithmetic expression evaluator for computed scorecard parameters.
 * Supports +, -, *, /, parentheses, numeric literals, and named variable substitution.
 * All math uses BigDecimal with HALF_UP rounding at scale 6.
 */
public final class FormulaEvaluator {

    private static final int SCALE = 6;

    private FormulaEvaluator() {}

    /**
     * Evaluate an infix expression like "(A + B) / C * 100" with variable values from the map.
     * Variable names are case-insensitive and matched against the map keys.
     * Returns null if any referenced variable is missing from the map or if the expression is malformed.
     */
    public static BigDecimal evaluate(String expression, Map<String, BigDecimal> variables) {
        if (expression == null || expression.isBlank()) return null;
        try {
            Parser parser = new Parser(expression.trim(), variables);
            BigDecimal result = parser.parseExpression();
            if (parser.pos < parser.tokens.length()) {
                return null;
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static class Parser {
        final String tokens;
        final Map<String, BigDecimal> vars;
        int pos;

        Parser(String tokens, Map<String, BigDecimal> vars) {
            this.tokens = tokens;
            this.vars = vars;
            this.pos = 0;
        }

        void skipWhitespace() {
            while (pos < tokens.length() && Character.isWhitespace(tokens.charAt(pos))) pos++;
        }

        BigDecimal parseExpression() {
            BigDecimal left = parseTerm();
            if (left == null) return null;
            while (true) {
                skipWhitespace();
                if (pos >= tokens.length()) break;
                char op = tokens.charAt(pos);
                if (op != '+' && op != '-') break;
                pos++;
                BigDecimal right = parseTerm();
                if (right == null) return null;
                left = op == '+' ? left.add(right) : left.subtract(right);
            }
            return left;
        }

        BigDecimal parseTerm() {
            BigDecimal left = parseFactor();
            if (left == null) return null;
            while (true) {
                skipWhitespace();
                if (pos >= tokens.length()) break;
                char op = tokens.charAt(pos);
                if (op != '*' && op != '/') break;
                pos++;
                BigDecimal right = parseFactor();
                if (right == null) return null;
                if (op == '*') {
                    left = left.multiply(right);
                } else {
                    if (right.compareTo(BigDecimal.ZERO) == 0) return null;
                    left = left.divide(right, SCALE, RoundingMode.HALF_UP);
                }
            }
            return left;
        }

        BigDecimal parseFactor() {
            skipWhitespace();
            if (pos >= tokens.length()) return null;

            if (tokens.charAt(pos) == '-') {
                pos++;
                BigDecimal f = parseFactor();
                return f != null ? f.negate() : null;
            }

            if (tokens.charAt(pos) == '(') {
                pos++;
                BigDecimal inner = parseExpression();
                if (inner == null) return null;
                skipWhitespace();
                if (pos >= tokens.length() || tokens.charAt(pos) != ')') return null;
                pos++;
                return inner;
            }

            if (Character.isDigit(tokens.charAt(pos)) || tokens.charAt(pos) == '.') {
                int start = pos;
                while (pos < tokens.length() && (Character.isDigit(tokens.charAt(pos)) || tokens.charAt(pos) == '.')) pos++;
                try {
                    return new BigDecimal(tokens.substring(start, pos));
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            if (Character.isLetter(tokens.charAt(pos)) || tokens.charAt(pos) == '_') {
                int start = pos;
                while (pos < tokens.length() && (Character.isLetterOrDigit(tokens.charAt(pos)) || tokens.charAt(pos) == '_')) pos++;
                String name = tokens.substring(start, pos);
                for (Map.Entry<String, BigDecimal> e : vars.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name)) {
                        return e.getValue();
                    }
                }
                return null;
            }

            return null;
        }
    }
}
