package com.los.core.creditintelligence.policystudio.catalogue;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * POLICY-UX-2C — add/edit catalogue capabilities into Policy Studio draft sessions.
 * Persists via existing clause/rule metadata + expression JSON (no new tables).
 */
@Service
public class CatalogueCapabilityDraftService {

    private final CreditCapabilityCatalogueService catalogueService;

    public CatalogueCapabilityDraftService(CreditCapabilityCatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    public record DraftMutation(
            CiPolicyClause clause,
            CiPolicyRuleCandidate rule,
            boolean created
    ) {}

    @SuppressWarnings("unchecked")
    public DraftMutation addOrUpdate(PolicyStudioSession session, Map<String, Object> body) {
        if (session == null || session.getDocument() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document not found");
        }
        String capabilityId = str(body, "businessCapabilityId");
        if (capabilityId == null || capabilityId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "businessCapabilityId is required");
        }
        BusinessCapability cap = catalogueService.findById(capabilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown capability: " + capabilityId));

        Map<String, Object> parameters = body.get("parameters") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : defaultParameters(cap);
        mergeDefaults(cap, parameters);

        List<String> validation = CatalogueCapabilityExpressionBuilder.validate(cap, parameters);
        if (!validation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", validation));
        }

        String requestedTreatment = str(body, "failureTreatment");
        if (requestedTreatment == null) {
            requestedTreatment = str(body, "treatment");
        }
        final String treatment = (requestedTreatment == null || requestedTreatment.isBlank())
                ? (cap.supportedTreatments().isEmpty() ? "REJECT" : cap.supportedTreatments().get(0))
                : requestedTreatment;
        if (!cap.supportedTreatments().isEmpty()
                && cap.supportedTreatments().stream().noneMatch(t -> t.equalsIgnoreCase(treatment))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Treatment '" + treatment + "' is not supported for this capability");
        }

        String dataRequirement = str(body, "dataRequirement");
        if (dataRequirement == null || dataRequirement.isBlank()) {
            dataRequirement = cap.dataAvailability() == null ? "AUTOMATIC" : cap.dataAvailability();
        }
        boolean useManualInput = Boolean.TRUE.equals(body.get("useManualInput"))
                || "MANUAL_INPUT".equalsIgnoreCase(dataRequirement)
                || "MANUAL_ONLY".equalsIgnoreCase(dataRequirement);

        CatalogueCapabilityExpressionBuilder.BuiltRule built =
                CatalogueCapabilityExpressionBuilder.build(cap, parameters, treatment);

        UUID existingRuleId = parseUuid(body.get("ruleId"));
        if (existingRuleId != null) {
            CiPolicyRuleCandidate existing = session.getRuleCandidates().stream()
                    .filter(r -> existingRuleId.equals(r.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
            applyToExisting(session, existing, cap, parameters, treatment, dataRequirement, useManualInput, built, body);
            CiPolicyClause clause = session.getClauses().stream()
                    .filter(c -> existing.getClauseId() != null && existing.getClauseId().equals(c.getId()))
                    .findFirst()
                    .orElse(null);
            return new DraftMutation(clause, existing, false);
        }

        return createNew(session, cap, parameters, treatment, dataRequirement, useManualInput, built, body);
    }

    private DraftMutation createNew(
            PolicyStudioSession session,
            BusinessCapability cap,
            Map<String, Object> parameters,
            String treatment,
            String dataRequirement,
            boolean useManualInput,
            CatalogueCapabilityExpressionBuilder.BuiltRule built,
            Map<String, Object> body) {

        CiPolicyDocument doc = session.getDocument();
        UUID clauseId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        Map<String, Object> clauseMeta = new LinkedHashMap<>();
        clauseMeta.put("catalogueAdded", true);
        clauseMeta.put("businessCapabilityId", cap.businessCapabilityId());
        clauseMeta.put("businessGroup", cap.domain().displayName());

        CiPolicyClause clause = CiPolicyClause.builder()
                .id(clauseId)
                .policyDocumentId(doc.getId())
                .section(cap.domain().displayName())
                .sourceText(built.businessSummary())
                .normalizedText(built.businessSummary())
                .clauseType(ClauseType.HARD_RULE.name())
                .extractionConfidence(new BigDecimal("1.0000"))
                .sortOrder(session.getClauses().size())
                .sourceLocation("catalogue:" + cap.businessCapabilityId())
                .status("EXTRACTED")
                .metadata(clauseMeta)
                .effectiveScope(Map.of())
                .build();

        Map<String, Object> lineage = new LinkedHashMap<>();
        lineage.put("documentId", doc.getId().toString());
        lineage.put("documentName", doc.getName());
        lineage.put("clauseId", clauseId.toString());
        lineage.put("section", cap.domain().displayName());
        lineage.put("sourceText", built.businessSummary());
        lineage.put("sourceLocation", "catalogue:" + cap.businessCapabilityId());
        lineage.put("provenance", "MANUAL_CATALOGUE_ADD");

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, domainFor(cap).name());

        Map<String, Object> meta = catalogueMetadata(cap, parameters, treatment, dataRequirement,
                useManualInput, built, body, true);

        CiPolicyRuleCandidate rule = CiPolicyRuleCandidate.builder()
                .id(ruleId)
                .clauseId(clauseId)
                .systemRuleId(built.systemRuleId())
                .ruleVersion("DRAFT")
                .ruleType("HARD")
                .scope(scope)
                .expression(built.expression())
                .onTrue(built.onTrue())
                .onFalse(built.onFalse())
                .onMissing(built.onMissing())
                .confidence(new BigDecimal("1.0000"))
                .reviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name())
                .lineage(lineage)
                .metadata(meta)
                .build();

        session.getClauses().add(clause);
        session.getRuleCandidates().add(rule);
        return new DraftMutation(clause, rule, true);
    }

    private void applyToExisting(
            PolicyStudioSession session,
            CiPolicyRuleCandidate rule,
            BusinessCapability cap,
            Map<String, Object> parameters,
            String treatment,
            String dataRequirement,
            boolean useManualInput,
            CatalogueCapabilityExpressionBuilder.BuiltRule built,
            Map<String, Object> body) {

        Object existingCap = rule.getMetadata() == null ? null : rule.getMetadata().get("businessCapabilityId");
        if (existingCap != null && !cap.businessCapabilityId().equals(String.valueOf(existingCap))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot change capability ID when editing an existing rule");
        }

        rule.setExpression(built.expression());
        rule.setOnTrue(built.onTrue());
        rule.setOnFalse(built.onFalse());
        rule.setOnMissing(built.onMissing());
        rule.setSystemRuleId(built.systemRuleId());
        rule.setReviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name());

        Map<String, Object> meta = catalogueMetadata(cap, parameters, treatment, dataRequirement,
                useManualInput, built, body, false);
        // Preserve ignore/delete if somehow editing — prefer ACCEPTED/EDITED for param edits
        meta.put("disposition", "EDITED");
        meta.put("excludedFromActivation", false);
        meta.put("deleted", false);
        rule.setMetadata(meta);

        session.getClauses().stream()
                .filter(c -> rule.getClauseId() != null && rule.getClauseId().equals(c.getId()))
                .findFirst()
                .ifPresent(c -> {
                    c.setSourceText(built.businessSummary());
                    c.setNormalizedText(built.businessSummary());
                    Map<String, Object> cm = c.getMetadata() == null
                            ? new LinkedHashMap<>()
                            : new LinkedHashMap<>(c.getMetadata());
                    cm.put("businessCapabilityId", cap.businessCapabilityId());
                    c.setMetadata(cm);
                });
    }

    private Map<String, Object> catalogueMetadata(
            BusinessCapability cap,
            Map<String, Object> parameters,
            String treatment,
            String dataRequirement,
            boolean useManualInput,
            CatalogueCapabilityExpressionBuilder.BuiltRule built,
            Map<String, Object> body,
            boolean created) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("businessCapabilityId", cap.businessCapabilityId());
        meta.put("businessTitle", cap.businessName());
        meta.put("businessSummary", built.businessSummary());
        if (built.metricPath() != null && built.metricPath().contains(".")) {
            meta.put("parameterId", built.metricPath());
        }
        meta.put("parameters", new LinkedHashMap<>(parameters));
        meta.put("failureTreatment", treatment.toUpperCase(Locale.ROOT));
        meta.put("dataRequirement", dataRequirement);
        meta.put("dataSource", cap.dataSource());
        meta.put("source", "MANUAL_CATALOGUE_ADD");
        meta.put("catalogueBacked", true);
        meta.put("existingCapability", true);
        meta.put("capabilityBadge", created
                ? "Existing capability · added manually"
                : "Existing capability · edited");
        meta.put("activationIncluded", true);
        meta.put("excludedFromActivation", false);
        meta.put("deleted", false);
        meta.put("disposition", created ? "ACCEPTED" : "EDITED");
        meta.put("authoringOnly", true);
        meta.put("allowCanonicalAuthority", false);
        meta.put("policySelectsProvider", false);
        meta.put("policyEnqueuesWorkflowStep", false);
        meta.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, domainFor(cap).name());
        meta.put("lastUiAction", created ? "CATALOGUE_ADD" : "CATALOGUE_EDIT");
        meta.put("lastUiActionAt", Instant.now().toString());

        List<Map<String, Object>> bindings = new ArrayList<>();
        for (ImplementationBinding b : cap.implementationBindings()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceKind", b.sourceKind());
            row.put("productionPath", b.productionPath());
            row.put("label", b.toBusinessView(false).get("label"));
            bindings.add(row);
        }
        meta.put("implementationBindings", bindings);

        if (useManualInput && cap.manualInputPossible()) {
            meta.put("useManualInput", true);
            meta.put("verificationMode", "MANUAL");
            meta.put("dataGapDisposition", "MANUAL_VERIFICATION");
            meta.put("manualInputLabel", body.get("manualInputLabel") != null
                    ? String.valueOf(body.get("manualInputLabel"))
                    : cap.businessName());
            meta.put("manualInputType", body.get("manualInputType") != null
                    ? String.valueOf(body.get("manualInputType"))
                    : preferredManualType(cap));
            meta.put("requiredActor", body.get("requiredActor") != null
                    ? String.valueOf(body.get("requiredActor"))
                    : "Credit Officer");
            // Manual input is a data path — keep accepted for activation inclusion unless ignored
            meta.put("disposition", "MANUAL_INPUT");
        }

        return meta;
    }

    private static DecisionPolicyDomain domainFor(BusinessCapability cap) {
        return switch (cap.domain()) {
            case KYC -> DecisionPolicyDomain.KYC;
            case ELIGIBILITY -> DecisionPolicyDomain.ELIGIBILITY;
            default -> DecisionPolicyDomain.CREDIT;
        };
    }

    private static Map<String, Object> defaultParameters(BusinessCapability cap) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ParameterDefinition p : cap.parameterDefinitions()) {
            if (p.defaultValue() != null) {
                m.put(p.name(), p.defaultValue());
            }
        }
        return m;
    }

    private static void mergeDefaults(BusinessCapability cap, Map<String, Object> parameters) {
        for (ParameterDefinition p : cap.parameterDefinitions()) {
            if (!parameters.containsKey(p.name()) && p.defaultValue() != null) {
                parameters.put(p.name(), p.defaultValue());
            }
        }
    }

    private static String preferredManualType(BusinessCapability cap) {
        if (cap.parameterDefinitions().isEmpty()) {
            return "YES_NO";
        }
        String type = cap.parameterDefinitions().get(0).type();
        if (type == null) return "NUMBER";
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "ENUM" -> "DROPDOWN";
            case "MONEY_INR", "PERCENT", "DECIMAL", "INTEGER", "NUMBER", "SCORE" -> "NUMBER";
            default -> "TEXT";
        };
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static UUID parseUuid(Object v) {
        if (v == null) return null;
        try {
            return UUID.fromString(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
