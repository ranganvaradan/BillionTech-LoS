package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;
import com.los.core.creditintelligence.aiunderwriter.domain.SuggestionStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects invented metrics/numbers/rule IDs / mismatched recommendation amounts.
 */
@Component
public class AiGroundingValidator {

    private static final Pattern RULE_ID = Pattern.compile(
            "\\b(XSRC_[A-Z0-9_]+|[A-Z]{1,5}\\d{1,3}|RULE_[A-Z0-9_]+|[A-Z]+_[A-Z]+_[A-Z]+)\\b");
    private static final Pattern METRIC_PATH = Pattern.compile(
            "\\b([a-z]+(?:\\.[a-z0-9_]+)+)\\b");
    private static final Pattern LARGE_NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9_])(\\d{5,}|\\d{1,3}(?:,\\d{3})+)(?![A-Za-z0-9_])");

    public GroundingResult validate(AiUnderwritingProvider.RawSuggestion suggestion, AiUnderwritingContext context) {
        List<String> failures = new ArrayList<>();
        if (suggestion == null || context == null) {
            return GroundingResult.failed(List.of("missing suggestion or context"), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        String content = suggestion.content() == null ? "" : suggestion.content();
        Set<String> knownMetricKeys = new HashSet<>(context.knownMetrics().keySet());
        Set<String> knownRuleKeys = new HashSet<>(context.knownRuleIds().keySet());
        Set<String> knownTokens = new HashSet<>(knownRuleKeys);
        knownTokens.addAll(extractTokens(context.policyOutcome()));
        knownTokens.addAll(extractTokens(context.shadowRecommendation()));
        Set<BigDecimal> allowedNumbers = collectAllowedNumbers(context);

        // Invented metric paths mentioned with bracket refs
        Matcher metricMatcher = METRIC_PATH.matcher(content);
        int metricMentions = 0;
        int metricHits = 0;
        while (metricMatcher.find()) {
            String path = metricMatcher.group(1);
            if (path.startsWith("http") || path.contains("www")) {
                continue;
            }
            if (!looksLikeMetric(path)) {
                continue;
            }
            metricMentions++;
            if (knownMetricKeys.contains(path) || context.allowedCanonicalPaths().contains(path)) {
                metricHits++;
            } else if (path.contains("turnover") || path.contains("emi") || path.contains("abb")
                    || path.startsWith("recommendation.") || path.startsWith("gst.")
                    || path.startsWith("bank.") || path.startsWith("bureau.") || path.startsWith("itr.")) {
                failures.add("Invented or unknown metric path: " + path);
            }
        }

        // Rule IDs
        Matcher ruleMatcher = RULE_ID.matcher(content);
        while (ruleMatcher.find()) {
            String rule = ruleMatcher.group(1);
            if (isBenignToken(rule)) {
                continue;
            }
            if (!knownTokens.contains(rule) && !knownMetricKeys.contains(rule)) {
                // Only fail clearly rule-like tokens
                if (rule.startsWith("XSRC_") || rule.startsWith("RULE_") || rule.matches("[A-Z]+_\\d+")
                        || (rule.contains("_") && rule.equals(rule.toUpperCase()) && rule.length() > 6)) {
                    failures.add("Invented or unknown rule ID: " + rule);
                }
            }
        }

        // Structured payload invented rule/metric
        if (suggestion.structuredPayload() != null) {
            Object inventedMetric = suggestion.structuredPayload().get("inventedMetric");
            Object inventedRule = suggestion.structuredPayload().get("inventedRuleId");
            Object claimedAmount = suggestion.structuredPayload().get("claimedRecommendationAmount");
            if (inventedMetric != null && !knownMetricKeys.contains(String.valueOf(inventedMetric))) {
                failures.add("Invented metric in payload: " + inventedMetric);
            }
            if (inventedRule != null && !knownRuleKeys.contains(String.valueOf(inventedRule))) {
                failures.add("Invented rule ID in payload: " + inventedRule);
            }
            if (claimedAmount != null) {
                BigDecimal claimed = toBd(claimedAmount);
                BigDecimal canonical = canonicalRecommendationAmount(context);
                if (claimed != null && canonical != null && claimed.compareTo(canonical) != 0) {
                    failures.add("Mismatched recommendation amount: claimed=" + claimed + " canonical=" + canonical);
                }
            }
        }

        // Large numbers must appear in allowed set (skip UUID / hex id contexts)
        Matcher numMatcher = LARGE_NUMBER.matcher(content);
        while (numMatcher.find()) {
            int start = numMatcher.start(1);
            int end = numMatcher.end(1);
            if (insideUuidOrHexId(content, start, end)) {
                continue;
            }
            String raw = numMatcher.group(1).replace(",", "");
            BigDecimal n = toBd(raw);
            if (n == null) {
                continue;
            }
            if (!numberAllowed(n, allowedNumbers)) {
                failures.add("Invented numeric value not in evidence: " + raw);
            }
        }

        int claims = Math.max(1, metricMentions + knownRuleKeys.size());
        BigDecimal groundingCoverage = BigDecimal.valueOf(metricHits)
                .divide(BigDecimal.valueOf(claims), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        BigDecimal evidenceCompleteness = knownMetricKeys.isEmpty()
                ? BigDecimal.valueOf(0.3)
                : BigDecimal.valueOf(Math.min(1.0, knownMetricKeys.size() / 5.0))
                .setScale(4, RoundingMode.HALF_UP);

        if (!failures.isEmpty()) {
            return GroundingResult.failed(failures, groundingCoverage, evidenceCompleteness);
        }
        return GroundingResult.ok(groundingCoverage, evidenceCompleteness);
    }

    private boolean insideUuidOrHexId(String content, int start, int end) {
        // UUID segments and similar hex ids should not be treated as invented amounts
        int left = Math.max(0, start - 8);
        int right = Math.min(content.length(), end + 8);
        String window = content.substring(left, right);
        return window.matches("(?s).*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}.*")
                || window.contains("-") && window.matches("(?s).*[0-9a-fA-F-]{12,}.*");
    }

    private boolean looksLikeMetric(String path) {
        return path.contains(".") && path.length() > 5;
    }

    private boolean isBenignToken(String rule) {
        return Set.of("AI_SUGGESTION", "CAM_DRAFT", "CREDIT_NOTE_DRAFT", "HUMAN_REVIEW",
                "NON_AUTHORITATIVE", "POLICY_STUDIO", "DATA_INSUFFICIENT",
                "APPROVE_WITH_CONDITIONS", "COUNTER_OFFER", "ALTERNATE_STRUCTURE_SUGGESTION",
                "POLICY_CLARIFICATION_SUGGESTION", "PENDING_REVIEW", "ACCEPTED_AS_NOTE",
                "REJECTED_GROUNDING_FAILURE").contains(rule)
                || rule.endsWith("_DRAFT")
                || rule.endsWith("_SUGGESTION")
                || rule.startsWith("APPROVE")
                || rule.equals("DECLINE")
                || rule.equals("REFER")
                || rule.equals("PASS")
                || rule.equals("FAIL");
    }

    private Set<String> extractTokens(Map<String, Object> map) {
        Set<String> out = new HashSet<>();
        if (map == null) {
            return out;
        }
        for (Object v : map.values()) {
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v);
            if (s.equals(s.toUpperCase(Locale.ROOT)) && s.contains("_")) {
                out.add(s);
            }
        }
        return out;
    }

    private Set<BigDecimal> collectAllowedNumbers(AiUnderwritingContext context) {
        Set<BigDecimal> nums = new HashSet<>();
        collectNumbers(context.knownMetrics(), nums);
        collectNumbers(context.shadowRecommendation(), nums);
        collectNumbers(context.creditEvidenceView(), nums);
        collectNumbers(context.creditDecisionView(), nums);
        collectNumbers(context.materialReconciliations(), nums);
        // small counts allowed (question counts etc.)
        nums.add(BigDecimal.valueOf(context.investigationQuestions().size()));
        return nums;
    }

    @SuppressWarnings("unchecked")
    private void collectNumbers(Object node, Set<BigDecimal> nums) {
        if (node instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                collectNumbers(v, nums);
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                collectNumbers(v, nums);
            }
        } else {
            BigDecimal bd = toBd(node);
            if (bd != null) {
                nums.add(bd.stripTrailingZeros());
            }
        }
    }

    private boolean numberAllowed(BigDecimal n, Set<BigDecimal> allowed) {
        BigDecimal normalized = n.stripTrailingZeros();
        for (BigDecimal a : allowed) {
            if (a.compareTo(normalized) == 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal canonicalRecommendationAmount(AiUnderwritingContext context) {
        Object entry = context.knownMetrics().get("recommendation.amount");
        if (entry instanceof Map<?, ?> m) {
            return toBd(m.get("value"));
        }
        Object amt = context.shadowRecommendation().get("amount");
        if (amt == null) {
            amt = context.shadowRecommendation().get("recommendedAmount");
        }
        return toBd(amt);
    }

    private BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).stripTrailingZeros();
        }
        try {
            return new BigDecimal(String.valueOf(o).replace(",", "")).stripTrailingZeros();
        } catch (Exception e) {
            return null;
        }
    }

    public record GroundingResult(
            boolean grounded,
            List<String> failures,
            BigDecimal groundingCoverage,
            BigDecimal evidenceCompleteness,
            String status
    ) {
        public static GroundingResult ok(BigDecimal coverage, BigDecimal completeness) {
            return new GroundingResult(true, List.of(), coverage, completeness, "GROUNDED");
        }

        public static GroundingResult failed(List<String> failures, BigDecimal coverage, BigDecimal completeness) {
            return new GroundingResult(false, List.copyOf(failures), coverage, completeness,
                    SuggestionStatus.REJECTED_GROUNDING_FAILURE.name());
        }
    }
}
