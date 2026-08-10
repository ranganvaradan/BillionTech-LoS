package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.BindingCertificationStatus;
import com.los.core.creditintelligence.cutover.service.BindingCertificationService;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import com.los.core.creditintelligence.validation.domain.CiPolicyBinding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Requires 100% CERTIFIED for knockout/hard/critical scorecard bindings (§8).
 */
@Service
public class PilotCriticalBindingCertificationService {

    public record PilotCriticalBindingCertificationReport(
            UUID tenantId,
            UUID cohortId,
            String productCode,
            int criticalCount,
            int certifiedCount,
            BigDecimal coveragePct,
            boolean allCertified,
            List<Map<String, Object>> bindings,
            List<String> blockers
    ) {
    }

    private final BindingCertificationService bindingCertification;

    public PilotCriticalBindingCertificationService(
            CutoverStore store, BindingCertificationService bindingCertification) {
        // store retained for Spring wiring / future cohort-scoped binding queries
        this.bindingCertification = bindingCertification;
    }

    public PilotCriticalBindingCertificationReport certify(UUID tenantId, UUID cohortId, String productCode) {
        List<CiPolicyBinding> all = bindingCertification.ensureBindings(tenantId);
        List<CiPolicyBinding> critical = all.stream()
                .filter(b -> isCriticalForPilot(b))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        int certified = 0;
        for (CiPolicyBinding b : critical) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("legacyParameter", b.getLegacyParameter());
            row.put("canonicalPath", b.getCanonicalPath());
            row.put("certificationStatus", b.getCertificationStatus());
            row.put("critical", b.isCritical());
            row.put("missingDataPolicy", b.getMissingDataPolicy());
            boolean ok = BindingCertificationStatus.CERTIFIED.name().equals(b.getCertificationStatus());
            row.put("certified", ok);
            if (ok) {
                certified++;
            } else {
                blockers.add(b.getLegacyParameter() + " status=" + b.getCertificationStatus()
                        + " (must be CERTIFIED)");
            }
            rows.add(row);
        }

        BigDecimal coverage = critical.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(certified * 100.0 / critical.size()).setScale(2, RoundingMode.HALF_UP);
        boolean allOk = !critical.isEmpty() && certified == critical.size();
        if (critical.isEmpty()) {
            blockers.add("No critical bindings found for tenant — cannot certify");
        }

        return new PilotCriticalBindingCertificationReport(
                tenantId, cohortId, productCode,
                critical.size(), certified, coverage, allOk, rows, blockers);
    }

    static boolean isCriticalForPilot(CiPolicyBinding b) {
        if (b.isCritical()) return true;
        List<String> rules = b.getProductionRules() == null ? List.of() : b.getProductionRules();
        for (String rule : rules) {
            String u = rule == null ? "" : rule.toUpperCase(Locale.ROOT);
            if (u.contains("KNOCKOUT") || u.contains("HARD") || u.contains("SCORECARD")
                    || u.contains("ELIGIBILITY")) {
                return true;
            }
        }
        Map<String, Object> meta = b.getMetadata();
        if (meta != null) {
            Object cat = meta.get("ruleCategory");
            if (cat != null) {
                String u = String.valueOf(cat).toUpperCase(Locale.ROOT);
                return u.contains("KNOCKOUT") || u.contains("HARD") || u.contains("SCORECARD")
                        || u.contains("ELIGIBILITY");
            }
        }
        return false;
    }
}
