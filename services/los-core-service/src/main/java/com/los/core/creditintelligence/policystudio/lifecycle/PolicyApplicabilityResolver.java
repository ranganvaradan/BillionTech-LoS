package com.los.core.creditintelligence.policystudio.lifecycle;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic single-policy resolution for a single-NBFC deployment.
 * Never silently chooses between two equally applicable in-force policies.
 * Does NOT enable production underwriting authority.
 */
@Service
public class PolicyApplicabilityResolver {

    public static final String EXACTLY_ONE = "EXACTLY_ONE";
    public static final String NO_APPLICABLE_POLICY = "NO_APPLICABLE_POLICY";
    public static final String AMBIGUOUS_POLICY_CONFIGURATION = "AMBIGUOUS_POLICY_CONFIGURATION";

    public Map<String, Object> resolve(ApplicationPolicyQuery query, List<PolicyApplicabilityRecord> catalogue) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("productionAuthority", "DISABLED");
        out.put("normalProcessingComparesMultiplePolicies", false);
        out.put("evaluationDate", query == null || query.evaluationDate() == null
                ? null : query.evaluationDate().toString());
        out.put("productCode", query == null ? null : query.productCode());
        out.put("applicationCode", query == null ? null : query.applicationCode());

        if (query == null || query.evaluationDate() == null) {
            out.put("outcome", NO_APPLICABLE_POLICY);
            out.put("reason", "Evaluation / business date is required (do not use wall-clock now()).");
            out.put("selectedPolicy", null);
            return out;
        }

        List<PolicyApplicabilityRecord> candidates = new ArrayList<>();
        List<Map<String, Object>> considered = new ArrayList<>();
        if (catalogue != null) {
            for (PolicyApplicabilityRecord r : catalogue) {
                if (r == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>(r.toBusinessView());
                boolean statusOk = isResolvableStatus(r.businessStatus(), query.evaluationDate(), r);
                boolean dateOk = r.isInForceOn(query.evaluationDate());
                boolean scopeOk = r.matchesScope(query);
                row.put("statusEligible", statusOk);
                row.put("dateEligible", dateOk);
                row.put("scopeEligible", scopeOk);
                considered.add(row);
                if (statusOk && dateOk && scopeOk) {
                    candidates.add(r);
                }
            }
        }
        out.put("considered", considered);

        if (candidates.isEmpty()) {
            out.put("outcome", NO_APPLICABLE_POLICY);
            out.put("reason", "No approved/scheduled/active policy matches product scope and evaluation date.");
            out.put("selectedPolicy", null);
            out.put("onePolicySelected", false);
            return out;
        }
        if (candidates.size() > 1) {
            out.put("outcome", AMBIGUOUS_POLICY_CONFIGURATION);
            out.put("reason", "Two or more policy versions would apply to the same applications on "
                    + query.evaluationDate() + " for product "
                    + (query.productCode() == null ? "?" : query.productCode())
                    + ". Change effective dates or applicability scope — do not silently prefer latest.");
            out.put("conflictingPolicies", candidates.stream().map(PolicyApplicabilityRecord::toBusinessView).toList());
            out.put("selectedPolicy", null);
            out.put("onePolicySelected", false);
            out.put("configurationError", true);
            return out;
        }

        PolicyApplicabilityRecord chosen = candidates.get(0);
        out.put("outcome", EXACTLY_ONE);
        out.put("onePolicySelected", true);
        out.put("selectedPolicy", chosen.toBusinessView());
        out.put("reason", "Product match + effective date"
                + (chosen.products().isEmpty() ? "" : " (" + String.join(", ", chosen.products()) + ")")
                + " — ONE POLICY SELECTED.");
        return out;
    }

    /**
     * Detect overlapping in-force windows for the same applicable scope before schedule/approve.
     */
    public Map<String, Object> detectOverlap(PolicyApplicabilityRecord candidate, List<PolicyApplicabilityRecord> catalogue) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        List<Map<String, Object>> conflicts = new ArrayList<>();
        if (candidate == null || candidate.effectiveFrom() == null) {
            out.put("blocked", false);
            out.put("conflicts", conflicts);
            return out;
        }
        LocalDate from = candidate.effectiveFrom();
        LocalDate until = candidate.effectiveUntil();
        if (catalogue != null) {
            for (PolicyApplicabilityRecord other : catalogue) {
                if (other == null || other.policyVersionId().equals(candidate.policyVersionId())) {
                    continue;
                }
                if (!isLifecycleConflictStatus(other.businessStatus())) {
                    continue;
                }
                if (!scopesOverlap(candidate, other)) {
                    continue;
                }
                if (!dateWindowsOverlap(from, until, other.effectiveFrom(), other.effectiveUntil())) {
                    continue;
                }
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("existing", other.toBusinessView());
                c.put("message", "Two policy versions would apply to the same "
                        + String.join("/", candidate.products().isEmpty() ? List.of("ALL") : candidate.products())
                        + " applications from "
                        + overlapStart(from, other.effectiveFrom())
                        + (overlapEnd(until, other.effectiveUntil()) == null ? " onward"
                        : " to " + overlapEnd(until, other.effectiveUntil()) + ".")
                        + " Change effective dates or applicability scope.");
                conflicts.add(c);
            }
        }
        out.put("blocked", !conflicts.isEmpty());
        out.put("conflicts", conflicts);
        if (!conflicts.isEmpty()) {
            out.put("actionRequired", List.of("Change effective dates", "Change applicability scope"));
        }
        return out;
    }

    private boolean isResolvableStatus(String status, LocalDate asOf, PolicyApplicabilityRecord r) {
        String s = PolicyBusinessLifecycleStatus.fromStored(status);
        if (PolicyBusinessLifecycleStatus.ACTIVE.equals(s)) {
            return true;
        }
        if (PolicyBusinessLifecycleStatus.SCHEDULED.equals(s)) {
            // Scheduled becomes selectable once evaluation date reaches effectiveFrom
            return r.isInForceOn(asOf);
        }
        // APPROVED without schedule is not yet resolvable for applications
        return false;
    }

    private boolean isLifecycleConflictStatus(String status) {
        String s = PolicyBusinessLifecycleStatus.fromStored(status);
        return PolicyBusinessLifecycleStatus.ACTIVE.equals(s)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(s)
                || PolicyBusinessLifecycleStatus.APPROVED.equals(s);
    }

    private boolean scopesOverlap(PolicyApplicabilityRecord a, PolicyApplicabilityRecord b) {
        if (a.products().isEmpty() || b.products().isEmpty()) {
            return true;
        }
        for (String p : a.products()) {
            String pu = p == null ? "" : p.toUpperCase(Locale.ROOT);
            for (String q : b.products()) {
                String qu = q == null ? "" : q.toUpperCase(Locale.ROOT);
                if (pu.equals(qu) || pu.equals("ALL") || qu.equals("ALL") || pu.equals("*") || qu.equals("*")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dateWindowsOverlap(LocalDate aFrom, LocalDate aUntil, LocalDate bFrom, LocalDate bUntil) {
        if (aFrom == null || bFrom == null) {
            return false;
        }
        LocalDate aEnd = aUntil == null ? LocalDate.MAX : aUntil;
        LocalDate bEnd = bUntil == null ? LocalDate.MAX : bUntil;
        return !aFrom.isAfter(bEnd) && !bFrom.isAfter(aEnd);
    }

    private String overlapStart(LocalDate a, LocalDate b) {
        if (a == null) {
            return String.valueOf(b);
        }
        if (b == null) {
            return String.valueOf(a);
        }
        return a.isAfter(b) ? a.toString() : b.toString();
    }

    private String overlapEnd(LocalDate a, LocalDate b) {
        if (a == null) {
            return b == null ? null : b.toString();
        }
        if (b == null) {
            return a.toString();
        }
        return a.isBefore(b) ? a.toString() : b.toString();
    }
}
