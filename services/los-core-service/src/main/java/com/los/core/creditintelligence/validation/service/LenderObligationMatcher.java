package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.validation.domain.CiLenderAlias;
import com.los.core.creditintelligence.validation.domain.CiLenderIdentity;
import com.los.core.creditintelligence.validation.domain.CiObligationMatch;
import com.los.core.creditintelligence.validation.domain.ObligationMatchStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes lender names via aliases and produces obligation match records.
 * Works in-memory (seed aliases) without requiring DB for unit tests.
 */
@Service
public class LenderObligationMatcher {

    private final Map<UUID, List<CiLenderIdentity>> identitiesByTenant = new ConcurrentHashMap<>();
    private final Map<UUID, List<CiLenderAlias>> aliasesByIdentity = new ConcurrentHashMap<>();

    public void seedCommonAliases(UUID tenantId) {
        identitiesByTenant.computeIfAbsent(tenantId, t -> new ArrayList<>());
        if (!identitiesByTenant.get(tenantId).isEmpty()) {
            return;
        }
        seed(tenantId, "HDFC BANK", "HDFC", List.of(
                "HDFC BANK", "HDFC BANK LTD", "HDFC BANK LIMITED", "HDFC EMI", "HDFC NACH"));
        seed(tenantId, "ICICI BANK", "ICICI", List.of(
                "ICICI BANK", "ICICI BANK LTD", "ICICI EMI", "ICICI NACH"));
        seed(tenantId, "STATE BANK OF INDIA", "SBI", List.of(
                "SBI", "STATE BANK OF INDIA", "SBI EMI", "SBI NACH"));
        seed(tenantId, "AXIS BANK", "AXIS", List.of(
                "AXIS BANK", "AXIS BANK LTD", "AXIS EMI"));
        seed(tenantId, "BAJAJ FINANCE", "BAJAJ", List.of(
                "BAJAJ", "BAJAJ FINANCE", "BAJAJ FINSERV", "BAJAJ FINANCE LTD"));
        seed(tenantId, "KOTAK MAHINDRA BANK", "KOTAK", List.of(
                "KOTAK", "KOTAK MAHINDRA", "KOTAK BANK"));
        seed(tenantId, "INDUSIND BANK", "INDUSIND", List.of("INDUSIND", "INDUSIND BANK"));
    }

    private void seed(UUID tenantId, String canonical, String code, List<String> aliases) {
        UUID id = UUID.randomUUID();
        CiLenderIdentity identity = CiLenderIdentity.builder()
                .id(id)
                .tenantId(tenantId)
                .canonicalName(canonical)
                .lenderCode(code)
                .build();
        identitiesByTenant.computeIfAbsent(tenantId, t -> new ArrayList<>()).add(identity);
        List<CiLenderAlias> aliasList = new ArrayList<>();
        for (String a : aliases) {
            aliasList.add(CiLenderAlias.builder()
                    .id(UUID.randomUUID())
                    .lenderIdentityId(id)
                    .aliasText(a)
                    .aliasSource("SEED")
                    .build());
        }
        aliasesByIdentity.put(id, aliasList);
    }

    public CiObligationMatch match(
            UUID tenantId,
            UUID applicationId,
            String bureauLender,
            String bankDetectedLender,
            BigDecimal bureauEmi,
            BigDecimal bankObservedEmi) {
        seedCommonAliases(tenantId);
        List<String> signals = new ArrayList<>();
        UUID bureauIdentity = resolveIdentity(tenantId, bureauLender, signals, "bureau");
        UUID bankIdentity = resolveIdentity(tenantId, bankDetectedLender, signals, "bank");

        ObligationMatchStatus status;
        BigDecimal confidence;
        UUID lenderId = null;
        if (bureauIdentity != null && bureauIdentity.equals(bankIdentity)) {
            status = ObligationMatchStatus.MATCH;
            confidence = BigDecimal.valueOf(0.95);
            lenderId = bureauIdentity;
            signals.add("ALIAS_CANONICAL_EQUAL");
        } else if (bureauIdentity != null && bankIdentity != null) {
            status = ObligationMatchStatus.AMBIGUOUS;
            confidence = BigDecimal.valueOf(0.40);
            signals.add("DISTINCT_CANONICAL_IDENTITIES");
        } else if (normalize(bureauLender).equals(normalize(bankDetectedLender))
                && !normalize(bureauLender).isBlank()) {
            status = ObligationMatchStatus.PROBABLE_MATCH;
            confidence = BigDecimal.valueOf(0.70);
            signals.add("RAW_TEXT_EQUAL_AFTER_NORMALIZE");
        } else if (containsTokenOverlap(bureauLender, bankDetectedLender)) {
            status = ObligationMatchStatus.PROBABLE_MATCH;
            confidence = BigDecimal.valueOf(0.60);
            signals.add("TOKEN_OVERLAP");
            lenderId = bureauIdentity != null ? bureauIdentity : bankIdentity;
        } else if (bureauLender == null || bankDetectedLender == null
                || bureauLender.isBlank() || bankDetectedLender.isBlank()) {
            status = ObligationMatchStatus.NO_MATCH;
            confidence = BigDecimal.ZERO;
            signals.add("MISSING_LENDER_NAME");
        } else {
            status = ObligationMatchStatus.NO_MATCH;
            confidence = BigDecimal.valueOf(0.10);
            signals.add("NO_ALIAS_HIT");
        }

        BigDecimal abs = null;
        BigDecimal pct = null;
        if (bureauEmi != null && bankObservedEmi != null) {
            abs = bureauEmi.subtract(bankObservedEmi).abs();
            BigDecimal base = bureauEmi.max(bankObservedEmi).max(BigDecimal.ONE);
            pct = abs.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
            if (pct.compareTo(BigDecimal.valueOf(20)) > 0) {
                signals.add("EMI_MATERIAL_VARIANCE");
            }
        }

        return CiObligationMatch.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .matchStatus(status.name())
                .bureauLender(bureauLender)
                .bankDetectedLender(bankDetectedLender)
                .lenderIdentityId(lenderId)
                .bureauEmi(bureauEmi)
                .bankObservedEmi(bankObservedEmi)
                .absoluteVariance(abs)
                .percentageVariance(pct)
                .confidence(confidence)
                .signals(signals)
                .evidenceRefs(List.of("bureau.tradeline.emi", "bank.recurring.emi"))
                .build();
    }

    private UUID resolveIdentity(UUID tenantId, String raw, List<String> signals, String side) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = normalize(raw);
        for (CiLenderIdentity id : identitiesByTenant.getOrDefault(tenantId, List.of())) {
            if (normalize(id.getCanonicalName()).equals(norm)) {
                signals.add(side + ":CANONICAL_HIT:" + id.getLenderCode());
                return id.getId();
            }
            for (CiLenderAlias alias : aliasesByIdentity.getOrDefault(id.getId(), List.of())) {
                String a = normalize(alias.getAliasText());
                if (norm.equals(a) || norm.contains(a) || a.contains(norm)) {
                    signals.add(side + ":ALIAS_HIT:" + alias.getAliasText());
                    return id.getId();
                }
            }
        }
        return null;
    }

    private static boolean containsTokenOverlap(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String[] ta = normalize(a).split("\\s+");
        String nb = normalize(b);
        int hits = 0;
        for (String t : ta) {
            if (t.length() >= 3 && nb.contains(t)) {
                hits++;
            }
        }
        return hits >= 1;
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9\\s]", " ")
                .replaceAll("\\b(LTD|LIMITED|PVT|PRIVATE|BANK|FINANCE|EMI|NACH|ECS)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public Map<String, Object> toMap(CiObligationMatch m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matchStatus", m.getMatchStatus());
        out.put("bureauLender", m.getBureauLender());
        out.put("bankDetectedLender", m.getBankDetectedLender());
        out.put("bureauEmi", m.getBureauEmi());
        out.put("bankObservedEmi", m.getBankObservedEmi());
        out.put("absoluteVariance", m.getAbsoluteVariance());
        out.put("percentageVariance", m.getPercentageVariance());
        out.put("confidence", m.getConfidence());
        out.put("signals", m.getSignals());
        return out;
    }
}
