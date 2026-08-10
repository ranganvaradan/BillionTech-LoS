package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ConflictType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PolicyConflictDetector {

    public List<Map<String, Object>> detect(List<CiPolicyRuleCandidate> rules) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                CiPolicyRuleCandidate a = rules.get(i);
                CiPolicyRuleCandidate b = rules.get(j);
                if (Objects.equals(a.getSystemRuleId(), b.getSystemRuleId())) {
                    conflicts.add(conflict(ConflictType.DUPLICATE, a, b, "Same systemRuleId"));
                    conflicts.add(conflict(ConflictType.DUPLICATE_RULE, a, b, "Same systemRuleId"));
                }
                if (sameMetricFamily(a, b) && scopesOverlap(a, b) && contradictoryThresholds(a, b)) {
                    conflicts.add(conflict(ConflictType.DIRECT_CONFLICT, a, b,
                            "Contradictory thresholds on overlapping scope"));
                }
                if (sameMetricFamily(a, b) && scopesOverlap(a, b) && overlappingRanges(a, b)) {
                    conflicts.add(conflict(ConflictType.OVERLAPPING_SCOPE, a, b,
                            "Overlapping score/threshold ranges e.g. 650-699"));
                }
                if (sameMetricFamily(a, b) && scopesOverlap(a, b) && redundant(a, b)) {
                    conflicts.add(conflict(ConflictType.REDUNDANT_RULE, a, b, "Redundant rule"));
                }
                if (conflictingException(a, b)) {
                    conflicts.add(conflict(ConflictType.CONFLICTING_EXCEPTION, a, b,
                            "Conflicting exception behaviour"));
                }
                if (conflictingMissingData(a, b)) {
                    conflicts.add(conflict(ConflictType.CONFLICTING_MISSING_DATA_POLICY, a, b,
                            "Conflicting onMissing policies for same metric/scope"));
                }
                if (metricDefinitionConflict(a, b)) {
                    conflicts.add(conflict(ConflictType.METRIC_DEFINITION_CONFLICT, a, b,
                            "Same metric referenced with incompatible definitions"));
                }
            }
        }
        return conflicts;
    }

    private boolean sameMetricFamily(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        String ma = metricHint(a);
        String mb = metricHint(b);
        return ma != null && ma.equals(mb);
    }

    private String metricHint(CiPolicyRuleCandidate r) {
        if (r.getExpression() == null) {
            return null;
        }
        String s = r.getExpression().toString();
        if (s.contains("bureau.score")) return "bureau.score";
        if (s.contains("bureau.max_dpd")) return "bureau.max_dpd";
        if (s.contains("avg_daily_balance") || s.contains("ADJUSTED_ADB")) return "adb";
        if (s.contains("transaction_count")) return "txn_count";
        if (r.getSystemRuleId() != null && r.getSystemRuleId().contains("SCORE")) return "bureau.score";
        return r.getSystemRuleId();
    }

    private boolean scopesOverlap(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        Object pa = a.getScope() == null ? null : a.getScope().get("products");
        Object pb = b.getScope() == null ? null : b.getScope().get("products");
        if (pa == null || pb == null) {
            return true;
        }
        if (pa instanceof List<?> la && pb instanceof List<?> lb) {
            for (Object x : la) {
                if (lb.contains(x) || "ALL".equals(String.valueOf(x)) || lb.contains("ALL")) {
                    return true;
                }
            }
            return false;
        }
        return Objects.equals(pa, pb);
    }

    private boolean contradictoryThresholds(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        Integer ta = threshold(a);
        Integer tb = threshold(b);
        if (ta == null || tb == null) {
            return false;
        }
        String oa = opHint(a);
        String ob = opHint(b);
        // e.g. score >= 700 AND score < 650 same scope
        if ("GTE".equals(oa) && "LT".equals(ob) && ta >= tb) {
            return true;
        }
        if ("GTE".equals(ob) && "LT".equals(oa) && tb >= ta) {
            return true;
        }
        return false;
    }

    private boolean overlappingRanges(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        // Detect score-band style overlap like 650-699 vs 680-720 from metadata/expression
        Integer loA = rangeBound(a, "lo");
        Integer hiA = rangeBound(a, "hi");
        Integer loB = rangeBound(b, "lo");
        Integer hiB = rangeBound(b, "hi");
        if (loA == null || hiA == null || loB == null || hiB == null) {
            return false;
        }
        return loA <= hiB && loB <= hiA;
    }

    private Integer rangeBound(CiPolicyRuleCandidate r, String which) {
        if (r.getMetadata() != null && r.getMetadata().get("range_" + which) != null) {
            return ((Number) r.getMetadata().get("range_" + which)).intValue();
        }
        // Heuristic for bureau score bands encoded in rule id / lineage
        String id = r.getSystemRuleId() == null ? "" : r.getSystemRuleId();
        if (id.contains("650") && id.contains("699")) {
            return "lo".equals(which) ? 650 : 699;
        }
        if (id.contains("700") && id.contains("749")) {
            return "lo".equals(which) ? 700 : 749;
        }
        return null;
    }

    private boolean redundant(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        return Objects.equals(a.getExpression(), b.getExpression())
                && scopesOverlap(a, b)
                && !Objects.equals(a.getSystemRuleId(), b.getSystemRuleId());
    }

    private boolean conflictingException(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        boolean aEx = a.getSystemRuleId() != null && a.getSystemRuleId().contains("EXCEPTION");
        boolean bEx = b.getSystemRuleId() != null && b.getSystemRuleId().contains("EXCEPTION");
        if (!(aEx || bEx)) {
            return false;
        }
        return sameMetricFamily(a, b) && scopesOverlap(a, b)
                && !Objects.equals(a.getOnTrue(), b.getOnTrue());
    }

    private boolean conflictingMissingData(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        return sameMetricFamily(a, b) && scopesOverlap(a, b)
                && a.getOnMissing() != null && b.getOnMissing() != null
                && !a.getOnMissing().equals(b.getOnMissing());
    }

    private boolean metricDefinitionConflict(CiPolicyRuleCandidate a, CiPolicyRuleCandidate b) {
        if (!sameMetricFamily(a, b) || !scopesOverlap(a, b)) {
            return false;
        }
        String ua = a.getUnitLeft();
        String ub = b.getUnitLeft();
        return ua != null && ub != null && !ua.equalsIgnoreCase(ub);
    }

    private Integer threshold(CiPolicyRuleCandidate r) {
        if (r.getMetadata() != null && r.getMetadata().get("threshold") instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private String opHint(CiPolicyRuleCandidate r) {
        if (r.getExpression() == null) {
            return null;
        }
        Object op = r.getExpression().get("op");
        return op == null ? null : String.valueOf(op);
    }

    private Map<String, Object> conflict(ConflictType type, CiPolicyRuleCandidate a, CiPolicyRuleCandidate b, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.name());
        m.put("ruleA", a.getSystemRuleId());
        m.put("ruleB", b.getSystemRuleId());
        m.put("message", msg);
        m.put("blocking", type == ConflictType.DIRECT_CONFLICT
                || type == ConflictType.CONFLICTING_EXCEPTION
                || type == ConflictType.METRIC_DEFINITION_CONFLICT
                || type == ConflictType.CONFLICTING_MISSING_DATA_POLICY);
        m.put("autoResolved", false);
        return m;
    }
}
