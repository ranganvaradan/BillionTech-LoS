package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyAuthoringSupport;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts headings, bullets, and numbered nested clauses from policy TXT.
 * Distinguishes Banking "Fields Required" vs "Banking BRE Rules"; nests Bureau overdue 1–4.
 */
@Component
public class PolicyClauseExtractor {

    private static final Pattern BULLET = Pattern.compile("^\\s*[•\\-\\*]\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*(\\d+)\\.\\s+(.+)$");

    public enum FixtureKind {
        BANKING_BRE,
        BUREAU_BRE,
        KYC_BRE,
        GENERIC
    }

    public FixtureKind detectKind(String text) {
        String t = text == null ? "" : text;
        // Preserve Banking/Bureau demos — detect them before KYC markers
        if (t.contains("Banking BRE Rules") || t.contains("Fields Required in Excel Report")) {
            return FixtureKind.BANKING_BRE;
        }
        if (t.contains("Bureau BRE") || t.contains("Bureau Score")) {
            return FixtureKind.BUREAU_BRE;
        }
        if (KycPolicyAuthoringSupport.looksLikeKycPolicyDocument(t)) {
            return FixtureKind.KYC_BRE;
        }
        return FixtureKind.GENERIC;
    }

    public List<CiPolicyClause> extract(UUID documentId, String sourceText) {
        FixtureKind kind = detectKind(sourceText);
        return switch (kind) {
            case BANKING_BRE -> extractBanking(documentId, sourceText);
            case BUREAU_BRE -> extractBureau(documentId, sourceText);
            case KYC_BRE -> extractKyc(documentId, sourceText);
            default -> {
                List<CiPolicyClause> generic = extractGeneric(documentId, sourceText);
                // Mixed credit+KYC uploads: stamp KYC metadata where clause text is KYC-related
                KycPolicyAuthoringSupport.enrichClauses(generic);
                yield generic;
            }
        };
    }

    private List<CiPolicyClause> extractBanking(UUID documentId, String text) {
        List<CiPolicyClause> out = new ArrayList<>();
        String section = null;
        int order = 0;
        String[] lines = text.split("\\R");
        StringBuilder continuation = null;
        Integer continuationIdx = null;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.stripTrailing();
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equalsIgnoreCase("Fields Required in Excel Report")) {
                section = "Fields Required in Excel Report";
                continuation = null;
                continue;
            }
            if (trimmed.equalsIgnoreCase("Banking BRE Rules")) {
                section = "Banking BRE Rules";
                continuation = null;
                continue;
            }
            Matcher bullet = BULLET.matcher(trimmed);
            if (bullet.matches()) {
                String body = bullet.group(1).trim();
                CiPolicyClause c = base(documentId, order++, section, body);
                if ("Fields Required in Excel Report".equals(section)) {
                    c.setClauseType(ClauseType.INFORMATION_REQUIREMENT.name());
                } else {
                    classifyBankingRule(c, body);
                }
                out.add(c);
                continuation = new StringBuilder(body);
                continuationIdx = out.size() - 1;
                continue;
            }
            // continuation of previous bullet (wrapped lines without bullet)
            if (continuation != null && continuationIdx != null && !trimmed.startsWith("-")
                    && section != null) {
                continuation.append(' ').append(trimmed);
                CiPolicyClause prev = out.get(continuationIdx);
                prev.setSourceText(continuation.toString());
                prev.setNormalizedText(normalize(continuation.toString()));
                if ("Banking BRE Rules".equals(section)) {
                    classifyBankingRule(prev, continuation.toString());
                }
            }
        }
        return out;
    }

    private void classifyBankingRule(CiPolicyClause c, String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("removed from average daily balance")
                || lower.contains("to be removed from average daily balance")) {
            c.setClauseType(ClauseType.METRIC_ADJUSTMENT.name());
            return;
        }
        String product = detectProduct(lower);
        if (product != null) {
            c.setProductScope(product);
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("products", List.of(product));
            if ((lower.contains("above") && (lower.contains("60,000") || lower.contains("rs.60,000")))
                    || lower.contains("60000")) {
                scope.put("loanAmount", Map.of("op", "GT", "value", 60000));
            }
            c.setEffectiveScope(scope);
        }
        c.setClauseType(ClauseType.HARD_RULE.name());
    }

    private String detectProduct(String lower) {
        if (lower.contains("starter")) {
            return "STARTER";
        }
        if (lower.contains("digileap") || lower.contains("digi leap")) {
            return "DIGILEAP";
        }
        if (lower.contains("smart switch")) {
            return "SMART_SWITCH";
        }
        if (lower.contains("reboost") || lower.contains("re boost")) {
            return "REBOOST";
        }
        if (lower.contains("all the bank statement based products")) {
            return "ALL_BANK_STATEMENT";
        }
        return null;
    }

    private List<CiPolicyClause> extractBureau(UUID documentId, String text) {
        List<CiPolicyClause> out = new ArrayList<>();
        int order = 0;
        String section = "Bureau BRE";
        String[] lines = text.split("\\R");
        StringBuilder pending = null;
        Integer pendingIdx = null;
        UUID overdueParentId = null;
        boolean inOverdueChildren = false;

        for (String raw : lines) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("Bureau BRE")) {
                continue;
            }
            Matcher numbered = NUMBERED.matcher(trimmed);
            if (numbered.matches() && overdueParentId != null) {
                inOverdueChildren = true;
                String num = numbered.group(1);
                String body = numbered.group(2).trim();
                CiPolicyClause child = base(documentId, order++, section, body);
                child.setClauseNumber(num);
                child.setParentClauseId(overdueParentId);
                child.setClauseType(ClauseType.EXCEPTION.name());
                child.setProductScope("ALL");
                child.setEffectiveScope(Map.of("products", List.of("ALL"), "parent", "OVERDUE_EXCEPTION"));
                out.add(child);
                pending = new StringBuilder(body);
                pendingIdx = out.size() - 1;
                continue;
            }
            Matcher bullet = BULLET.matcher(trimmed);
            if (bullet.matches()) {
                inOverdueChildren = false;
                String body = bullet.group(1).trim();
                CiPolicyClause c = base(documentId, order++, section, body);
                c.setProductScope("ALL");
                c.setEffectiveScope(Map.of("products", List.of("ALL")));
                if (body.toLowerCase(Locale.ROOT).contains("overdue rule")) {
                    c.setClauseType(ClauseType.EXCEPTION.name());
                    c.setClauseNumber("OVERDUE_PARENT");
                    out.add(c);
                    overdueParentId = c.getId();
                } else {
                    c.setClauseType(ClauseType.HARD_RULE.name());
                    out.add(c);
                }
                pending = new StringBuilder(body);
                pendingIdx = out.size() - 1;
                continue;
            }
            if (pending != null && pendingIdx != null && !inOverdueChildren) {
                // wrap continuation for bullets
                if (!NUMBERED.matcher(trimmed).matches() && !BULLET.matcher(trimmed).matches()) {
                    pending.append(' ').append(trimmed);
                    CiPolicyClause prev = out.get(pendingIdx);
                    prev.setSourceText(pending.toString());
                    prev.setNormalizedText(normalize(pending.toString()));
                }
            } else if (pending != null && pendingIdx != null && inOverdueChildren
                    && !NUMBERED.matcher(trimmed).matches() && !BULLET.matcher(trimmed).matches()) {
                pending.append(' ').append(trimmed);
                CiPolicyClause prev = out.get(pendingIdx);
                prev.setSourceText(pending.toString());
                prev.setNormalizedText(normalize(pending.toString()));
            }
        }
        return out;
    }

    private List<CiPolicyClause> extractKyc(UUID documentId, String text) {
        List<CiPolicyClause> out = new ArrayList<>();
        int order = 0;
        String section = "KYC & Eligibility";
        for (String raw : text.split("\\R")) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equalsIgnoreCase("KYC & Eligibility")
                    || trimmed.equalsIgnoreCase("KYC and Eligibility")
                    || trimmed.equalsIgnoreCase("Identity Verification")
                    || trimmed.toUpperCase(Locale.ROOT).startsWith("DEMO POLICY")
                    || trimmed.toUpperCase(Locale.ROOT).startsWith("VALIDATION SAMPLE")) {
                section = "KYC & Eligibility";
                continue;
            }
            if (trimmed.equalsIgnoreCase("Credit Underwriting")
                    || trimmed.equalsIgnoreCase("Credit Rules")) {
                section = "Credit Underwriting";
                continue;
            }
            Matcher bullet = BULLET.matcher(trimmed);
            Matcher numbered = NUMBERED.matcher(trimmed);
            String body;
            if (bullet.matches()) {
                body = bullet.group(1);
            } else if (numbered.matches()) {
                body = numbered.group(2);
            } else if (trimmed.length() > 20 && !trimmed.endsWith(":")) {
                body = trimmed;
            } else {
                continue;
            }
            CiPolicyClause c = base(documentId, order++, section, body);
            c.setClauseType(ClauseType.HARD_RULE.name());
            out.add(c);
        }
        KycPolicyAuthoringSupport.enrichClauses(out);
        return out;
    }

    private List<CiPolicyClause> extractGeneric(UUID documentId, String text) {
        List<CiPolicyClause> out = new ArrayList<>();
        int order = 0;
        for (String raw : text.split("\\R")) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher bullet = BULLET.matcher(trimmed);
            Matcher numbered = NUMBERED.matcher(trimmed);
            String body;
            if (bullet.matches()) {
                body = bullet.group(1);
            } else if (numbered.matches()) {
                body = numbered.group(2);
            } else if (trimmed.length() > 20) {
                body = trimmed;
            } else {
                continue;
            }
            // POLICY-UX-2D — split multi-sentence lines so each rule can bind independently
            List<String> parts = splitPolicySentences(body);
            for (String part : parts) {
                CiPolicyClause c = base(documentId, order++, "GENERIC", part);
                c.setClauseType(ClauseType.UNKNOWN.name());
                out.add(c);
            }
        }
        return out;
    }

    /** Split on sentence terminators when multiple policy statements share a line. */
    static List<String> splitPolicySentences(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String[] bits = body.split("(?<=[.!?])\\s+(?=[A-Z0-9\"'])");
        List<String> out = new ArrayList<>();
        for (String bit : bits) {
            String t = bit == null ? "" : bit.trim();
            if (t.length() >= 12) {
                out.add(t);
            }
        }
        return out.isEmpty() ? List.of(body.trim()) : out;
    }

    private CiPolicyClause base(UUID documentId, int order, String section, String body) {
        return CiPolicyClause.builder()
                .id(UUID.randomUUID())
                .policyDocumentId(documentId)
                .section(section)
                .sourceText(body)
                .normalizedText(normalize(body))
                .extractionConfidence(new BigDecimal("0.9200"))
                .sortOrder(order)
                .sourceLocation("line:" + order)
                .status("EXTRACTED")
                .metadata(Map.of())
                .effectiveScope(Map.of())
                .build();
    }

    private String normalize(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
