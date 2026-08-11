package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.RuleOperandPresenter;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * LOS-LIVE-READINESS-1 — derive required parameters from Studio rules or Live UW configs.
 */
@Service
public class PolicyRequiredParameterExtractor {

    private CanonicalParameterRegistry registry() {
        return PolicyStudioConvergencePresenter.registry();
    }

    public Map<String, Object> fromStudioSession(PolicyStudioSession session) {
        Set<String> ids = new LinkedHashSet<>();
        List<Map<String, Object>> details = new ArrayList<>();
        if (session == null) {
            return result(ids, details, "STUDIO_SESSION");
        }
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            if (r == null) continue;
            Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
            if (Boolean.TRUE.equals(meta.get("classificationOnly"))
                    || Boolean.TRUE.equals(meta.get("dataRequirementOnly"))
                    || Boolean.TRUE.equals(meta.get("metricAdjustment"))
                    || Boolean.TRUE.equals(meta.get("deleted"))
                    || "IGNORED".equalsIgnoreCase(String.valueOf(meta.get("disposition")))) {
                continue;
            }
            if (PolicyStudioConvergencePresenter.isCompoundChild(r.getSystemRuleId())) {
                continue;
            }
            collectFromRule(r, ids, details);
        }
        return result(ids, details, "STUDIO_SESSION");
    }

    public Map<String, Object> fromLiveRuleSet(UnderwritingRuleSet ruleSet) {
        Set<String> ids = new LinkedHashSet<>();
        List<Map<String, Object>> details = new ArrayList<>();
        if (ruleSet == null || ruleSet.getRulesJson() == null) {
            return result(ids, details, "LIVE_RULE_SET");
        }
        Map<String, Object> rulesJson = ruleSet.getRulesJson();
        Object rules = rulesJson.get("rules");
        if (!(rules instanceof List<?> list)) {
            // Some payloads store a single rule field at root
            if (rulesJson.get("parameter") != null) {
                resolveLiveParam(String.valueOf(rulesJson.get("parameter")), ids, details, "LIVE_RULE");
            }
            return result(ids, details, "LIVE_RULE_SET");
        }
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> rm)) continue;
            Object param = rm.get("parameter");
            if (param == null) param = rm.get("param");
            if (param != null) {
                resolveLiveParam(String.valueOf(param), ids, details, "LIVE_RULE");
            }
        }
        return result(ids, details, "LIVE_RULE_SET");
    }

    public Map<String, Object> fromLiveScorecard(UnderwritingScorecard scorecard) {
        Set<String> ids = new LinkedHashSet<>();
        List<Map<String, Object>> details = new ArrayList<>();
        if (scorecard == null) {
            return result(ids, details, "LIVE_SCORECARD");
        }
        collectJsonParams(scorecard.getScorecardJson(), ids, details, "LIVE_SCORECARD");
        collectJsonParams(scorecard.getHardRulesJson(), ids, details, "LIVE_SCORECARD_HARD");
        return result(ids, details, "LIVE_SCORECARD");
    }

    @SuppressWarnings("unchecked")
    private void collectJsonParams(Map<String, Object> json, Set<String> ids,
                                   List<Map<String, Object>> details, String origin) {
        if (json == null) return;
        Object params = json.get("parameters");
        if (params instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> rm) {
                    Object p = rm.get("parameter");
                    if (p == null) p = rm.get("param");
                    if (p == null) p = rm.get("code");
                    if (p != null) resolveLiveParam(String.valueOf(p), ids, details, origin);
                }
            }
        }
        Object rules = json.get("rules");
        if (rules instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> rm) {
                    Object p = rm.get("parameter");
                    if (p != null) resolveLiveParam(String.valueOf(p), ids, details, origin);
                }
            }
        }
        Object hard = json.get("hardRules");
        if (hard instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> rm) {
                    Object p = rm.get("parameter");
                    if (p != null) resolveLiveParam(String.valueOf(p), ids, details, origin);
                }
            }
        }
    }

    private void collectFromRule(CiPolicyRuleCandidate r, Set<String> ids, List<Map<String, Object>> details) {
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        List<String> dataUsed = new ArrayList<>();
        Object rawDu = meta.get("dataUsed");
        if (rawDu instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) dataUsed.add(String.valueOf(o));
            }
        }
        Map<String, Object> visual = meta.get("visualLogic") instanceof Map<?, ?> vm
                ? castMap(vm) : Map.of();
        List<Map<String, Object>> operands = RuleOperandPresenter.buildOperands(
                r.getSystemRuleId(), dataUsed, meta, visual);
        for (Map<String, Object> op : operands) {
            String pid = firstNonBlank(
                    str(op, "canonicalParameterId"),
                    str(op, "parameterId"),
                    str(op, "resolvedParameterId"));
            String phrase = firstNonBlank(str(op, "label"), str(op, "phrase"), str(op, "name"));
            if (pid != null) {
                addDetail(ids, details, pid, phrase, "STUDIO_OPERAND");
            } else if (phrase != null) {
                Optional<CanonicalParameterDefinition> hit = registry().resolve(phrase);
                hit.ifPresent(def -> addDetail(ids, details, def.id(), phrase, "STUDIO_RESOLVED"));
                if (hit.isEmpty()) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("parameterId", null);
                    d.put("phrase", phrase);
                    d.put("classification", "UNRESOLVED");
                    d.put("origin", "STUDIO_OPERAND");
                    details.add(d);
                }
            }
        }
        Object parameterId = meta.get("parameterId");
        if (parameterId != null) {
            addDetail(ids, details, String.valueOf(parameterId), null, "STUDIO_META");
        }
    }

    private void resolveLiveParam(String liveKey, Set<String> ids,
                                  List<Map<String, Object>> details, String origin) {
        if (liveKey == null || liveKey.isBlank()) return;
        String key = liveKey.trim();
        for (CanonicalParameterDefinition d : registry().all()) {
            if (key.equalsIgnoreCase(d.liveRuleParameter())
                    || key.equalsIgnoreCase(d.liveScorecardParameter())
                    || key.equalsIgnoreCase(d.id())) {
                addDetail(ids, details, d.id(), key, origin);
                return;
            }
            if (d.aliases() != null) {
                for (String a : d.aliases()) {
                    if (key.equalsIgnoreCase(a)) {
                        addDetail(ids, details, d.id(), key, origin);
                        return;
                    }
                }
            }
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("parameterId", null);
        d.put("phrase", key);
        d.put("liveKey", key);
        d.put("classification", "UNRESOLVED");
        d.put("origin", origin);
        details.add(d);
    }

    private void addDetail(Set<String> ids, List<Map<String, Object>> details,
                           String parameterId, String phrase, String origin) {
        if (parameterId == null || parameterId.isBlank()) return;
        boolean first = ids.add(parameterId);
        if (!first) return;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("parameterId", parameterId);
        d.put("phrase", phrase);
        d.put("origin", origin);
        registry().findById(parameterId).ifPresent(def -> {
            d.put("businessName", def.businessName());
            d.put("source", def.evaluatedFrom());
            d.put("type", def.type());
            d.put("availability", def.availability());
        });
        details.add(d);
    }

    private Map<String, Object> result(Set<String> ids, List<Map<String, Object>> details, String sourceKind) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceKind", sourceKind);
        out.put("requiredParameterIds", new ArrayList<>(ids));
        out.put("requiredCount", ids.size());
        out.put("details", details);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
