package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generic residual-content guard for flat DESCRIBE authoring.
 * After a flat parse claims complete, every substantive phrase in the source
 * must be accounted for (bound parameter, operator, value) or classified as
 * non-substantive filler. Unconsumed clause-bearing content fail-closes.
 */
public final class PlainEnglishConsumptionGuard {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9.%\\-]+");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    /** Longest-first operator / comparison phrases consumed by a flat leaf. */
    private static final String[] CONSUMABLE_PHRASES = {
            "less than or equal to", "greater than or equal to", "more than or equal to",
            "fewer than or equal to", "less than or equal", "greater than or equal",
            "more than or equal", "fewer than or equal",
            "is equal to", "equal to", "equals to", "equals",
            "not exceed", "no more than", "at least", "at most",
            "or above", "or below", "or more", "or higher", "or greater",
            "and above", "and below", "& above", "& below",
            "greater than", "less than", "more than", "fewer than",
            "below", "above", "under", "over",
            "must not", "must be", "should not", "should be", "shall be", "shall not",
            "is not", "are not",
            "reject if", "reject when", "allow if", "allow when",
            "bureau score", "credit score", "thin file", "status ntc",
            "obligation ratio", "interest rate"
    };

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "to", "of", "for", "in", "on", "by", "with", "from",
            "be", "is", "are", "was", "were", "been", "being",
            "will", "would", "can", "could", "may", "might", "shall", "should", "must",
            "only", "also", "just", "still", "than", "then", "that", "this", "these", "those",
            "it", "its", "as", "at", "or", "and", "if", "when", "while", "into",
            "rule", "policy", "condition", "criteria", "requirement",
            "reject", "rejected", "allow", "allowed", "approve", "approved", "pass", "fail",
            "refer", "manual", "review", "info", "warning",
            "percent", "percentage", "pct", "score", "value", "limit", "threshold",
            "applicant", "borrower", "customer", "case"
    );

    /** Clause-bearing leftovers that must never be silently dropped. */
    private static final Set<String> CLAUSE_MARKERS = Set.of(
            "unless", "except", "besides", "provided", "apart", "either", "otherwise",
            "whenever", "whereas", "albeit", "however", "although", "though",
            "foir", "ltv", "dti", "adb", "edi", "ntc", "gst", "pan", "cibil",
            "bureau", "banking", "turnover", "vintage", "bounce", "cheque", "writeoff",
            "write", "off", "exceeds", "exceed", "between", "both", "neither", "nor"
    );

    private PlainEnglishConsumptionGuard() {}

    /**
     * True when flat-complete draft left substantive source content unmapped.
     * Comparison phrases such as "less than or equal" are treated as consumed operator text.
     */
    public static boolean hasUnconsumedSubstantiveContent(
            String sourceText,
            String parameterId,
            String operator,
            Object value,
            String businessName) {
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        String residual = normalize(sourceText);

        // Consume comparison / operator phrases before any OR/AND residual check
        for (String phrase : CONSUMABLE_PHRASES) {
            residual = residual.replace(phrase, " ");
        }
        residual = stripOperatorSymbols(residual, operator);

        if (parameterId != null && !parameterId.isBlank()) {
            residual = stripParameterTokens(residual, parameterId);
        }
        if (businessName != null && !businessName.isBlank()) {
            residual = stripPhrase(residual, normalize(businessName));
        }
        residual = stripValue(residual, value);
        residual = MULTI_SPACE.matcher(residual).replaceAll(" ").trim();

        // Clause-level AND/OR remaining after comparison-phrase consumption
        if (CompoundPlainEnglishParser.hasClauseLevelOr(residual)
                || CompoundPlainEnglishParser.hasClauseLevelAnd(residual)) {
            return true;
        }

        List<String> tokens = tokens(residual);
        if (tokens.isEmpty()) {
            return false;
        }
        Set<String> otherParamHints = otherParameterHints(parameterId);
        for (String tok : tokens) {
            if (tok.isBlank() || STOPWORDS.contains(tok)) {
                continue;
            }
            if (isNumericToken(tok)) {
                return true; // leftover number not equal to bound value
            }
            if (CLAUSE_MARKERS.contains(tok) || otherParamHints.contains(tok)) {
                return true;
            }
            // Any other non-stopword leftover is unconsumed substantive content
            if (tok.length() >= 3) {
                return true;
            }
        }
        return false;
    }

    /** Backward-compatible OR/AND-only check (pre-generalization). */
    public static boolean hasResidualLogicalConnective(String text) {
        return CompoundPlainEnglishParser.hasResidualLogicalConnective(text);
    }

    private static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        lower = lower.replace('%', ' ');
        // Treat hyphenated compounds as space-separated so strip phrases match
        // (write-offs ≡ write offs, co-borrower ≡ co borrower, pay-off ≡ pay off).
        lower = lower.replace('-', ' ');
        lower = NON_ALNUM.matcher(lower).replaceAll(" ");
        return MULTI_SPACE.matcher(lower).replaceAll(" ").trim();
    }

    private static String stripPhrase(String residual, String phrase) {
        if (phrase == null || phrase.isBlank()) return residual;
        return residual.replace(phrase, " ");
    }

    private static String stripOperatorSymbols(String residual, String operator) {
        String r = residual;
        if (operator != null) {
            String op = operator.trim().toLowerCase(Locale.ROOT);
            if (!op.isBlank()) {
                r = r.replace(op, " ");
            }
        }
        r = r.replace(">=", " ").replace("<=", " ").replace("!=", " ")
                .replace(">", " ").replace("<", " ").replace("=", " ");
        return r;
    }

    private static String stripParameterTokens(String residual, String parameterId) {
        String r = residual;
        String id = parameterId.toLowerCase(Locale.ROOT);
        for (String part : id.split("[._]")) {
            if (part.length() >= 3 && !STOPWORDS.contains(part)) {
                r = r.replace(part, " ");
            }
        }
        var opt = CanonicalParameterRegistry.shared().findById(parameterId);
        if (opt.isPresent()) {
            var def = opt.get();
            if (def.businessName() != null) {
                r = stripPhrase(r, normalize(def.businessName()));
            }
            if (def.aliases() != null) {
                for (String a : def.aliases()) {
                    if (a != null && !a.isBlank()) {
                        r = stripPhrase(r, normalize(a));
                    }
                }
            }
        }
        if (id.contains("score")) {
            r = stripPhrase(r, "bureau score");
            r = stripPhrase(r, "cibil");
        }
        if (id.contains("obligation") || id.contains("foir")) {
            r = stripPhrase(r, "foir");
            r = stripPhrase(r, "obligation ratio");
            r = stripPhrase(r, "dti");
        }
        if (id.contains("ntc") || id.contains("thin_file")) {
            r = stripPhrase(r, "ntc");
            r = stripPhrase(r, "thin file");
            r = stripPhrase(r, "new to credit");
        }
        if (id.contains("writeoff") || id.contains("write_off") || id.contains("written_off")) {
            // Longest phrases first so short tokens do not leave residual "loan".
            r = stripPhrase(r, "no loan write offs are allowed except for credit cards");
            r = stripPhrase(r, "no loan write offs are allowed");
            r = stripPhrase(r, "loan write offs are allowed");
            r = stripPhrase(r, "loan write offs");
            r = stripPhrase(r, "loan write off");
            r = stripPhrase(r, "write offs");
            r = stripPhrase(r, "write off");
            r = stripPhrase(r, "writeoffs");
            r = stripPhrase(r, "written off");
            if (id.contains("non_cc") || id.contains("writeoff_non_cc")) {
                r = stripPhrase(r, "except for credit cards");
                r = stripPhrase(r, "except credit cards");
                r = stripPhrase(r, "excluding credit cards");
                r = stripPhrase(r, "credit cards");
                r = stripPhrase(r, "credit card");
                // Residual "loan" from "No Loan Write-Offs…" is non-substantive once write-off bound.
                r = stripPhrase(r, "loan");
                r = stripPhrase(r, "loans");
                r = stripPhrase(r, "allowed");
                r = stripPhrase(r, "are");
            }
        }
        return r;
    }

    private static String stripValue(String residual, Object value) {
        if (value == null) return residual;
        String r = residual;
        String raw = String.valueOf(value).toLowerCase(Locale.ROOT).trim();
        if (!raw.isBlank()) {
            r = r.replace(raw, " ");
            // numeric without trailing .0
            if (value instanceof Number n) {
                long asLong = n.longValue();
                if (Math.abs(n.doubleValue() - asLong) < 1e-9) {
                    r = r.replace(Long.toString(asLong), " ");
                }
            }
        }
        if (value instanceof Boolean) {
            r = r.replace("true", " ").replace("false", " ")
                    .replace("yes", " ").replace("no", " ");
        }
        return r;
    }

    private static List<String> tokens(String residual) {
        List<String> out = new ArrayList<>();
        if (residual == null || residual.isBlank()) return out;
        for (String t : residual.split("\\s+")) {
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    private static boolean isNumericToken(String tok) {
        return tok.matches("-?\\d+(\\.\\d+)?%?");
    }

    private static Set<String> otherParameterHints(String boundParameterId) {
        Set<String> hints = new LinkedHashSet<>();
        for (CanonicalParameterDefinition def : CanonicalParameterRegistry.shared().all()) {
            if (boundParameterId != null && boundParameterId.equals(def.id())) {
                continue;
            }
            if (def.aliases() != null) {
                for (String a : def.aliases()) {
                    if (a != null) {
                        for (String part : normalize(a).split("\\s+")) {
                            if (part.length() >= 3) hints.add(part);
                        }
                    }
                }
            }
            if (def.businessName() != null) {
                for (String part : normalize(def.businessName()).split("\\s+")) {
                    if (part.length() >= 4 && !STOPWORDS.contains(part)) {
                        hints.add(part);
                    }
                }
            }
        }
        return hints;
    }
}
