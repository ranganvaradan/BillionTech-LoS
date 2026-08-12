package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.metrics.AdbBulkDepositAdjustmentCalculator;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * POLICY-RESOLUTION-PERSISTENCE-P0 — stable identity for resolutions so reparse/reload
 * cannot invent duplicate unresolved work for the same requirement.
 */
public final class PolicyResolutionIdentity {

    public static final String ADB_BULK = "adjustment:banking.adb_bulk_deposit_adjustment";
    public static final String EDI_PARAMETER = "parameter:application.proposed_edi";
    public static final String INWARD_100_BOUNDARY = "boundary:inward_return:100";
    public static final String EDI_AMBIGUITY = "ambiguity:phrase:EDI";

    private PolicyResolutionIdentity() {}

    public static String forAdjustment(String dataItemId) {
        if (dataItemId == null) return null;
        String id = dataItemId.trim().toLowerCase(Locale.ROOT);
        if (id.contains("adb_bulk") || id.equals("banking.adb_bulk_deposit_adjustment")) {
            return ADB_BULK;
        }
        return "adjustment:" + id;
    }

    public static String forParameter(String parameterIdOrOperandKey) {
        if (parameterIdOrOperandKey == null) return null;
        String k = ParameterResolutionSupport.normalizeOperandKey(parameterIdOrOperandKey);
        if ("proposed_edi".equals(k) || "application.proposed_edi".equalsIgnoreCase(parameterIdOrOperandKey)) {
            return EDI_PARAMETER;
        }
        return "parameter:" + (k == null ? parameterIdOrOperandKey.trim().toLowerCase(Locale.ROOT) : k);
    }

    public static String forAmbiguity(CiPolicyAmbiguity a) {
        if (a == null) return null;
        String phrase = a.getPhrase() == null ? "" : a.getPhrase().trim().toUpperCase(Locale.ROOT);
        String type = a.getAmbiguityType() == null ? "" : a.getAmbiguityType().trim().toUpperCase(Locale.ROOT);
        String desc = a.getDescription() == null ? "" : a.getDescription().toUpperCase(Locale.ROOT);
        if ((phrase.contains("EDI") || desc.contains("EDI")) && !phrase.contains("CREDIT CARD")) {
            return EDI_AMBIGUITY;
        }
        if (type.contains("BOUNDARY") || phrase.contains("100") || desc.contains("100")
                || phrase.contains("TRANSACTION") || desc.contains("EXACTLY")) {
            if (phrase.contains("INWARD") || phrase.contains("RETURN") || phrase.contains("CHEQUE")
                    || phrase.contains("ECS") || phrase.contains("ENACH") || phrase.contains("100")
                    || desc.contains("INWARD") || desc.contains("100")) {
                return INWARD_100_BOUNDARY;
            }
            return "boundary:" + phrase.replaceAll("\\s+", "_");
        }
        if (!phrase.isBlank()) {
            return "ambiguity:phrase:" + phrase.replaceAll("\\s+", "_");
        }
        return a.getId() == null ? null : "ambiguity:id:" + a.getId();
    }

    public static String forRule(CiPolicyRuleCandidate r) {
        if (r == null) return null;
        if (r.getSystemRuleId() != null && !r.getSystemRuleId().isBlank()) {
            return "rule:system:" + r.getSystemRuleId().trim().toUpperCase(Locale.ROOT);
        }
        String name = "";
        if (r.getMetadata() != null && r.getMetadata().get("businessTitle") != null) {
            name = String.valueOf(r.getMetadata().get("businessTitle")).trim().toUpperCase(Locale.ROOT);
        }
        if (name.isBlank() && r.getLineage() != null && r.getLineage().get("sourceText") != null) {
            name = String.valueOf(r.getLineage().get("sourceText")).trim().toUpperCase(Locale.ROOT);
        }
        if (name.contains("INWARD") || name.contains("RETURN")) {
            return "rule:system:BANK_INWARD_RETURN_BRANCHED_100";
        }
        if (name.contains("BULK") || (name.contains("10") && name.contains("DEPOSIT"))) {
            return "rule:adb_bulk_clause";
        }
        if (r.getId() != null) {
            return "rule:id:" + r.getId();
        }
        return null;
    }

    /** Stamp stable identity onto resolution / ambiguity metadata. */
    public static void stampIdentity(Map<String, Object> target, String identityKey) {
        if (target == null || identityKey == null) return;
        target.put("resolutionIdentity", identityKey);
        target.put("persistence", "POLICY_VERSION_DURABLE");
    }

    public static Map<String, Object> extractBundle(PolicyStudioSession session) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        if (session == null || session.getDocument() == null) return bundle;
        UUID docId = session.getDocument().getId();
        bundle.put("documentId", docId == null ? null : docId.toString());
        bundle.put("contentHash", session.getDocument().getContentHash());
        Map<String, Object> docMeta = session.getDocument().getMetadata();
        if (docMeta == null) docMeta = Map.of();
        bundle.put("demoKind", docMeta.get("kind"));
        bundle.put("lineageRootId", docMeta.getOrDefault("lineageRootId",
                docMeta.getOrDefault("clonedFromDocumentId", docId == null ? null : docId.toString())));
        Object pdr = docMeta.get(PolicyDataResolutionSupport.DOC_META_KEY);
        if (pdr instanceof Map<?, ?> m) {
            bundle.put("policyDataResolutions", new LinkedHashMap<>(m));
        }
        Object ppm = docMeta.get(ParameterResolutionSupport.DOC_META_KEY);
        if (ppm instanceof Map<?, ?> m) {
            bundle.put("policyParameterMappings", new LinkedHashMap<>(m));
        }
        Map<String, Object> amb = new LinkedHashMap<>();
        if (session.getAmbiguities() != null) {
            for (CiPolicyAmbiguity a : session.getAmbiguities()) {
                String key = forAmbiguity(a);
                if (key == null) continue;
                String status = a.getResolutionStatus() == null ? "OPEN" : a.getResolutionStatus();
                if (!"RESOLVED".equalsIgnoreCase(status) && !"SUPERSEDED".equalsIgnoreCase(status)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("resolutionStatus", status);
                row.put("resolvedOption", a.getResolvedOption());
                row.put("ambiguityType", a.getAmbiguityType());
                row.put("sourcePhrase", a.getPhrase());
                amb.put(key, row);
            }
        }
        bundle.put("ambiguityResolutions", amb);

        Map<String, Object> rules = new LinkedHashMap<>();
        if (session.getRuleCandidates() != null) {
            for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
                String key = forRule(r);
                if (key == null) continue;
                Map<String, Object> meta = r.getMetadata();
                if (meta == null || meta.isEmpty()) continue;
                boolean interesting = meta.containsKey(ParameterResolutionSupport.META_KEY)
                        || meta.containsKey("boundaryResolved")
                        || meta.containsKey(PolicyDataResolutionSupport.RULE_META_KEY)
                        || meta.containsKey("dataCalcResolution");
                if (!interesting) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("systemRuleId", r.getSystemRuleId());
                row.put("name", r.getMetadata() == null ? null : r.getMetadata().get("businessTitle"));
                row.put("expression", r.getExpression());
                row.put("metadata", new LinkedHashMap<>(meta));
                rules.put(key, row);
            }
        }
        bundle.put("ruleResolutions", rules);
        return bundle;
    }

    /**
     * Re-apply durable resolutions onto a session after extract/clone/reload.
     * Does not invent READY — only restores previously confirmed resolutions.
     */
    @SuppressWarnings("unchecked")
    public static void rebind(PolicyStudioSession session, Map<String, Object> bundle) {
        if (session == null || bundle == null || bundle.isEmpty()) return;
        if (session.getDocument() == null) return;

        Map<String, Object> docMeta = session.getDocument().getMetadata();
        if (docMeta == null) {
            docMeta = new LinkedHashMap<>();
            session.getDocument().setMetadata(docMeta);
        } else if (!(docMeta instanceof LinkedHashMap)) {
            docMeta = new LinkedHashMap<>(docMeta);
            session.getDocument().setMetadata(docMeta);
        }

        Object pdr = bundle.get("policyDataResolutions");
        if (pdr instanceof Map<?, ?> m && !m.isEmpty()) {
            Map<String, Object> existing = PolicyDataResolutionSupport.fromDocument(session);
            Map<String, Object> merged = new LinkedHashMap<>(existing);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() instanceof Map<?, ?> res) {
                    merged.put(String.valueOf(e.getKey()), new LinkedHashMap<>((Map<String, Object>) res));
                }
            }
            docMeta.put(PolicyDataResolutionSupport.DOC_META_KEY, merged);
        }
        Object ppm = bundle.get("policyParameterMappings");
        if (ppm instanceof Map<?, ?> m && !m.isEmpty()) {
            Object cur = docMeta.get(ParameterResolutionSupport.DOC_META_KEY);
            Map<String, Object> merged = cur instanceof Map<?, ?> c
                    ? new LinkedHashMap<>((Map<String, Object>) c) : new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) merged.put(String.valueOf(e.getKey()), e.getValue());
            }
            docMeta.put(ParameterResolutionSupport.DOC_META_KEY, merged);
        }

        Object ambObj = bundle.get("ambiguityResolutions");
        if (ambObj instanceof Map<?, ?> ambRaw && session.getAmbiguities() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ambMap = (Map<String, Object>) ambRaw;
            for (CiPolicyAmbiguity a : session.getAmbiguities()) {
                String key = forAmbiguity(a);
                if (key == null || !ambMap.containsKey(key)) continue;
                Object rowObj = ambMap.get(key);
                if (!(rowObj instanceof Map<?, ?> row)) continue;
                Object statusObj = row.get("resolutionStatus");
                String status = statusObj == null ? "RESOLVED" : String.valueOf(statusObj);
                a.setResolutionStatus(status);
                if (row.get("resolvedOption") != null) {
                    a.setResolvedOption(String.valueOf(row.get("resolvedOption")));
                }
            }
        }

        Object ruleObj = bundle.get("ruleResolutions");
        if (ruleObj instanceof Map<?, ?> ruleRaw && session.getRuleCandidates() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ruleMap = (Map<String, Object>) ruleRaw;
            for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
                String key = forRule(r);
                if (key == null || !ruleMap.containsKey(key)) continue;
                Object rowObj = ruleMap.get(key);
                if (!(rowObj instanceof Map<?, ?> row)) continue;
                Map<String, Object> meta = r.getMetadata();
                if (meta == null) {
                    meta = new LinkedHashMap<>();
                    r.setMetadata(meta);
                } else if (!(meta instanceof LinkedHashMap)) {
                    meta = new LinkedHashMap<>(meta);
                    r.setMetadata(meta);
                }
                Object savedMeta = row.get("metadata");
                if (savedMeta instanceof Map<?, ?> sm) {
                    for (Map.Entry<?, ?> e : sm.entrySet()) {
                        if (e.getKey() == null) continue;
                        String mk = String.valueOf(e.getKey());
                        if (ParameterResolutionSupport.META_KEY.equals(mk)
                                || "boundaryResolved".equals(mk)
                                || "boundaryOption".equals(mk)
                                || PolicyDataResolutionSupport.RULE_META_KEY.equals(mk)
                                || "dataCalcResolution".equals(mk)) {
                            meta.put(mk, e.getValue());
                        }
                    }
                }
                if (row.get("expression") instanceof Map<?, ?> expr) {
                    // Only restore expression when boundary was resolved (inward 100)
                    if (Boolean.TRUE.equals(meta.get("boundaryResolved"))) {
                        r.setExpression(new LinkedHashMap<>((Map<String, Object>) expr));
                    }
                }
            }
        }
    }

    /** True when ADB bulk wording changed enough to stale the adjustment. */
    public static boolean adbWordingChanged(String previousText, String newText) {
        if (newText == null) return false;
        if (previousText == null) return true;
        String a = normalizeWording(previousText);
        String b = normalizeWording(newText);
        if (a.equals(b)) return false;
        boolean aAdb = a.contains("bulk") || a.contains("10") && a.contains("deposit");
        boolean bAdb = b.contains("bulk") || b.contains("10") && b.contains("deposit");
        return aAdb || bAdb;
    }

    public static void invalidateAdbBulk(PolicyStudioSession session) {
        if (session == null || session.getDocument() == null) return;
        Map<String, Object> all = PolicyDataResolutionSupport.fromDocument(session);
        all.remove(AdbBulkDepositAdjustmentCalculator.ADJUSTMENT_ID);
        all.remove("banking.adb_bulk_deposit_adjustment");
        Map<String, Object> docMeta = session.getDocument().getMetadata();
        if (docMeta == null) {
            docMeta = new LinkedHashMap<>();
            session.getDocument().setMetadata(docMeta);
        }
        docMeta.put(PolicyDataResolutionSupport.DOC_META_KEY, all);
    }

    private static String normalizeWording(String t) {
        return t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
