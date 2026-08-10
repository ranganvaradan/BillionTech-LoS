package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Day 6.1 — Business Measure Designer.
 * Business-language definition → executability check against existing metric/DSL framework.
 * Does NOT create a new calculation engine. AI may propose; human confirms; governance required.
 */
@Service
public class BusinessMeasureDesignerService {

    public static final String READY_TO_CONFIGURE = "READY_TO_CONFIGURE";
    public static final String CONFIGURED = "CONFIGURED";
    public static final String SOURCE_DATA_REQUIRED = "SOURCE_DATA_REQUIRED";
    public static final String BUSINESS_DEFINITION_REQUIRED = "BUSINESS_DEFINITION_REQUIRED";
    public static final String ENGINEERING_REQUIRED = "ENGINEERING_REQUIRED";
    public static final String MANUAL_VERIFICATION = "MANUAL_VERIFICATION";
    public static final String NOT_IMPLEMENTABLE = "NOT_IMPLEMENTABLE";
    public static final String APPLICATION_INPUT_REQUIRED = "APPLICATION_INPUT_REQUIRED";

    public static final String GOV_PROPOSED = "PROPOSED";
    public static final String GOV_REVIEWED = "REVIEWED";
    public static final String GOV_APPROVED = "APPROVED";

    /** Calculation types genuinely supported by POLICY_DSL_V1 / existing metric candidates. */
    public static final Set<String> SUPPORTED_CALC_TYPES = Set.of(
            "DIRECT_FIELD",
            "SUM",
            "AVERAGE",
            "COUNT",
            "MIN",
            "MAX",
            "RATIO",
            "PERCENTAGE",
            "PERIOD_AGGREGATION",
            "FILTERED_TRANSACTION_AGGREGATION",
            "DERIVED_FROM_MEASURES",
            "CROSS_SOURCE_VARIANCE"
    );

    private final PolicyAuthoringRegistry registry;
    private final PolicyStudioPersistenceService persistenceService;
    private final PolicyReviewService reviewService;

    public BusinessMeasureDesignerService(
            PolicyAuthoringRegistry registry,
            PolicyStudioPersistenceService persistenceService,
            PolicyReviewService reviewService) {
        this.registry = registry == null ? new PolicyAuthoringRegistry() : registry;
        this.persistenceService = persistenceService == null
                ? new PolicyStudioPersistenceService() : persistenceService;
        this.reviewService = reviewService;
    }

    public BusinessMeasureDesignerService() {
        this(new PolicyAuthoringRegistry(), new PolicyStudioPersistenceService(), null);
    }

    public static boolean isExecutableExpression(Map<String, Object> expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }
        Object op = expression.get("op");
        if (op != null) {
            String ops = String.valueOf(op);
            if ("NEW_METRIC_CANDIDATE".equalsIgnoreCase(ops)
                    || "UNKNOWN".equalsIgnoreCase(ops)
                    || "REQUIRE_METRIC_AVAILABILITY".equalsIgnoreCase(ops)) {
                return false;
            }
            return true;
        }
        // Structured adjusted-ADB style: base + exclusions
        return expression.get("base") != null;
    }

    public static boolean isApprovedExecutableMeasure(CiPolicyMetricCandidate m) {
        if (m == null) {
            return false;
        }
        Map<String, Object> meta = m.getMetadata() == null ? Map.of() : m.getMetadata();
        if (!isExecutableExpression(m.getExpression())) {
            return false;
        }
        if (Boolean.TRUE.equals(meta.get("NEW_METRIC_CANDIDATE"))
                && !Boolean.TRUE.equals(meta.get("executable"))) {
            return false;
        }
        String gov = String.valueOf(meta.getOrDefault("governanceStatus", ""));
        if (GOV_APPROVED.equalsIgnoreCase(gov)) {
            return true;
        }
        // Registry-native AVAILABLE metrics without custom stub
        return "AVAILABLE".equalsIgnoreCase(String.valueOf(meta.get("availability")))
                && Boolean.TRUE.equals(meta.get("executable"));
    }

    /** Classify a gap for the designer — do not force every ambiguity into BUSINESS_MEASURE. */
    public String classifyGap(String path, String status, CiPolicyRuleCandidate rule, CiPolicyAmbiguity amb) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        String phrase = amb == null || amb.getPhrase() == null ? "" : amb.getPhrase().toLowerCase(Locale.ROOT);
        String sys = rule == null || rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().toUpperCase(Locale.ROOT);

        if (p.startsWith("application.") || p.contains("proposed_edi")
                || phrase.equals("edi") || phrase.contains("proposed edi")
                || phrase.matches(".*\\bedi\\b.*")) {
            if (!phrase.contains("settlement") && !p.contains("settlement")) {
                return "APPLICATION_INPUT";
            }
        }
        if (phrase.contains("exactly 100") || phrase.contains("100 transaction") || sys.contains("INWARD")) {
            if (phrase.contains("100") || sys.contains("100")) {
                return "BOUNDARY_CONDITION";
            }
        }
        if (phrase.contains("clean") || p.contains("clean_history")) {
            return "CLASSIFICATION";
        }
        if (phrase.contains("ntc") || p.contains("ntc")) {
            return "BUSINESS_TERM";
        }
        if (p.contains("turnover") && (p.startsWith("gst") || p.startsWith("bank"))) {
            return "RECONCILIATION";
        }
        if ("MAPPING_REQUIRED".equals(status) || "__UNMAPPED__".equals(path)) {
            return "SOURCE_MAPPING";
        }
        if (p.contains("settlement") || p.contains("overdue") || p.contains("credit_after")
                || phrase.contains("settlement") || phrase.contains("qr")
                || phrase.contains("average deposit") || phrase.contains("deposition")) {
            return "BUSINESS_MEASURE";
        }
        if ("DEFINITION_REQUIRED".equals(status) || "METRIC_REQUIRED".equals(status)) {
            return "BUSINESS_MEASURE";
        }
        return "OTHER";
    }

    public Map<String, Object> openDesigner(PolicyStudioSession session, String dataElementCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("dataElementCode", dataElementCode);

        Map<String, Object> catalog = BusinessDataSourceCatalog.describe(dataElementCode);
        out.put("businessName", catalog.get("businessName"));
        out.put("category", catalog.get("category"));
        out.put("primarySource", catalog.get("primarySource"));
        out.put("fallbackSources", catalog.get("fallbackSources"));

        List<Map<String, Object>> affected = affectedRules(session, dataElementCode);
        out.put("affectedRules", affected);
        out.put("affectedRuleCount", affected.size());

        String sourceAvailability = sourceDataAvailability(dataElementCode);
        out.put("sourceDataAvailability", sourceAvailability);
        out.put("sourceDataAvailable", "AVAILABLE".equals(sourceAvailability)
                || "PARTIAL".equals(sourceAvailability));

        CiPolicyMetricCandidate existing = findMeasure(session, dataElementCode);
        out.put("existingMeasure", existing == null ? null : summarizeMeasure(existing));

        String classification = classifyFromCode(dataElementCode, session);
        out.put("classification", classification);
        out.put("supportedCalculationTypes", new ArrayList<>(SUPPORTED_CALC_TYPES));

        Map<String, Object> aiProposal = proposeDefinition(session, dataElementCode, classification);
        out.put("aiProposedDefinition", aiProposal);
        out.put("aiLabel", "AI PROPOSED DEFINITION");
        out.put("aiDisclaimer",
                "AI may propose using the policy clause and available data. Credit Head must confirm. "
                        + "AI does not make the measure executable automatically.");

        Map<String, Object> validation = validateDefinition(
                dataElementCode, classification, aiProposal, sourceAvailability, false);
        out.put("executabilityPreview", validation);

        out.put("stages", Map.of(
                "sourceData", sourceAvailability,
                "businessDefinition", existing != null && existing.getMetadata() != null
                        && existing.getMetadata().get("businessDefinition") != null ? "PRESENT" : "NEEDED",
                "businessMeasure", existing != null && isApprovedExecutableMeasure(existing)
                        ? "CONFIGURED" : "NOT_CONFIGURED",
                "ruleExecutability", "PENDING"
        ));
        return out;
    }

    public Map<String, Object> confirmDefinition(
            PolicyStudioSession session,
            Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("dataElementCode", ""));
        if (code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataElementCode required");
        }
        String classification = String.valueOf(body.getOrDefault("classification",
                classifyFromCode(code, session)));
        String calcType = String.valueOf(body.getOrDefault("calculationType", ""));
        String governance = String.valueOf(body.getOrDefault("governanceStatus", GOV_PROPOSED));
        boolean humanConfirmed = Boolean.TRUE.equals(body.get("humanConfirmed"));
        if (!humanConfirmed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Credit Head must confirm the business definition (humanConfirmed=true)");
        }

        if ("MANUAL_VERIFICATION".equals(String.valueOf(body.get("resolutionMode")))) {
            return confirmManualVerification(session, code, body);
        }

        // Application input path — not a metric
        if ("APPLICATION_INPUT".equals(classification)) {
            return confirmApplicationInput(session, code, body);
        }
        if ("BOUNDARY_CONDITION".equals(classification)
                || "BUSINESS_TERM".equals(classification)
                || "CLASSIFICATION".equals(classification)) {
            return confirmTermOrBoundary(session, code, classification, body);
        }

        String sourceAv = sourceDataAvailability(code);
        Map<String, Object> definition = businessDefinitionFromBody(body);
        Map<String, Object> validation = validateDefinition(code, classification, definition, sourceAv, true);
        String execStatus = String.valueOf(validation.get("status"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("dataElementCode", code);
        out.put("classification", classification);
        out.put("executability", validation);
        out.put("affectedRules", affectedRules(session, code));
        out.put("metricCreated", false);

        if (APPLICATION_INPUT_REQUIRED.equals(execStatus)
                || SOURCE_DATA_REQUIRED.equals(execStatus)
                || BUSINESS_DEFINITION_REQUIRED.equals(execStatus)
                || ENGINEERING_REQUIRED.equals(execStatus)
                || MANUAL_VERIFICATION.equals(execStatus)
                || NOT_IMPLEMENTABLE.equals(execStatus)) {
            out.put("configured", false);
            out.put("message", validation.get("message"));
            // Persist proposed definition as non-executable candidate for audit — not AVAILABLE
            if (ENGINEERING_REQUIRED.equals(execStatus) || BUSINESS_DEFINITION_REQUIRED.equals(execStatus)) {
                persistNonExecutableProposal(session, code, definition, calcType, execStatus, body);
            }
            persistenceService.saveSessionSnapshot(session);
            return out;
        }

        if (!SUPPORTED_CALC_TYPES.contains(calcType.toUpperCase(Locale.ROOT))
                && !READY_TO_CONFIGURE.equals(execStatus)
                && !CONFIGURED.equals(execStatus)) {
            validation.put("status", ENGINEERING_REQUIRED);
            out.put("executability", validation);
            out.put("configured", false);
            persistenceService.saveSessionSnapshot(session);
            return out;
        }

        Map<String, Object> expression = buildExpression(code, calcType, definition, body);
        if (!isExecutableExpression(expression)) {
            out.put("configured", false);
            out.put("executability", Map.of(
                    "status", ENGINEERING_REQUIRED,
                    "message", "Could not build a safe executable expression from the confirmed definition."));
            return out;
        }

        // Only APPROVED governance can mark AVAILABLE / metricCreated
        boolean approve = GOV_APPROVED.equalsIgnoreCase(governance);
        if (!approve && GOV_REVIEWED.equalsIgnoreCase(governance)) {
            // reviewed but not approved — still candidate
        } else if (!approve) {
            governance = GOV_PROPOSED;
        }

        CiPolicyMetricCandidate measure = upsertExecutableMeasure(
                session, code, expression, definition, calcType, governance, body);
        out.put("configured", true);
        out.put("governanceStatus", governance);
        out.put("measureId", measure.getId().toString());
        out.put("metricCreated", approve && isApprovedExecutableMeasure(measure));
        out.put("message", approve
                ? "Business measure approved and executable — implementability will recalculate."
                : "Business measure saved as " + governance
                + " — critical rules stay blocked until APPROVED.");

        // Close related open ambiguities that match this measure once approved
        if (approve) {
            closeRelatedAmbiguities(session, code, body);
        }
        persistenceService.saveSessionSnapshot(session);
        return out;
    }

    public Map<String, Object> approveMeasure(PolicyStudioSession session, String measureId, String actor) {
        UUID id = UUID.fromString(measureId);
        CiPolicyMetricCandidate m = session.getMetricCandidates().stream()
                .filter(c -> id.equals(c.getId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Measure not found"));
        if (!isExecutableExpression(m.getExpression())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot approve — no executable calculation on this measure");
        }
        Map<String, Object> meta = new LinkedHashMap<>(m.getMetadata() == null ? Map.of() : m.getMetadata());
        meta.put("governanceStatus", GOV_APPROVED);
        meta.put("availability", "AVAILABLE");
        meta.put("executable", true);
        meta.put("approvedBy", actor);
        meta.put("approvedAt", Instant.now().toString());
        meta.remove("NEW_METRIC_CANDIDATE");
        m.setMetadata(meta);
        m.setReviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name());
        if (reviewService != null) {
            reviewService.invalidateCheckerApproval(session, actor);
        }
        closeRelatedAmbiguities(session, m.getCandidateCanonicalCode(), Map.of("resolvedBy", actor));
        persistenceService.saveSessionSnapshot(session);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("measureId", measureId);
        out.put("governanceStatus", GOV_APPROVED);
        out.put("metricCreated", true);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    // ─── internals ───────────────────────────────────────────────

    private Map<String, Object> confirmApplicationInput(
            PolicyStudioSession session, String code, Map<String, Object> body) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldName", body.getOrDefault("fieldName",
                BusinessDataSourceCatalog.businessName(code)));
        field.put("description", body.getOrDefault("businessMeaning",
                body.getOrDefault("description", "")));
        field.put("dataType", body.getOrDefault("dataType", "DECIMAL"));
        field.put("required", body.getOrDefault("required", true));
        field.put("productApplicability", body.getOrDefault("productApplicability", "ALL"));
        field.put("validation", body.getOrDefault("validation", "POSITIVE_NUMBER"));
        field.put("captureMode", body.getOrDefault("captureMode", "MANUAL_ENTRY"));
        field.put("governanceStatus", body.getOrDefault("governanceStatus", GOV_PROPOSED));

        Map<String, Object> sessionMeta = sessionMeta(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputs = sessionMeta.get("applicationInputs") instanceof List<?> l
                ? (List<Map<String, Object>>) l : new ArrayList<>();
        inputs.removeIf(i -> code.equals(String.valueOf(i.get("dataElementCode"))));
        Map<String, Object> row = new LinkedHashMap<>(field);
        row.put("dataElementCode", code);
        row.put("status", APPLICATION_INPUT_REQUIRED);
        inputs.add(row);
        sessionMeta.put("applicationInputs", inputs);
        // Store on document metadata via a synthetic measure with CANDIDATE (manual capture)
        Map<String, Object> expression = Map.of(
                "op", "DIRECT_FIELD",
                "applicationField", code,
                "source", "APPLICATION_FORM");
        Map<String, Object> def = businessDefinitionFromBody(body);
        def.put("applicationInput", field);
        String gov = String.valueOf(body.getOrDefault("governanceStatus", GOV_PROPOSED));
        upsertExecutableMeasure(session, code, expression, def, "DIRECT_FIELD", gov, body);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resolution", APPLICATION_INPUT_REQUIRED);
        out.put("applicationInput", field);
        out.put("configured", GOV_APPROVED.equalsIgnoreCase(gov));
        out.put("metricCreated", GOV_APPROVED.equalsIgnoreCase(gov));
        out.put("message", "Application input recorded — add field to application form. Not a provider integration.");
        out.put("affectedRules", affectedRules(session, code));
        out.put("allowCanonicalAuthority", false);
        persistenceService.saveSessionSnapshot(session);
        return out;
    }

    private Map<String, Object> confirmTermOrBoundary(
            PolicyStudioSession session, String code, String classification, Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("classification", classification);
        out.put("configured", false);
        out.put("metricCreated", false);
        out.put("message", classification + " should be resolved in Ambiguous Terms / vocabulary — "
                + "not forced through Business Measure Designer.");
        out.put("recommendedTab", "ambiguities");
        out.put("allowCanonicalAuthority", false);
        if (Boolean.TRUE.equals(body.get("markManualVerification"))) {
            return confirmManualVerification(session, code, body);
        }
        return out;
    }

    private Map<String, Object> confirmManualVerification(
            PolicyStudioSession session, String code, Map<String, Object> body) {
        Map<String, Object> manual = new LinkedHashMap<>();
        manual.put("dataElementCode", code);
        manual.put("whatMustBeVerified", body.getOrDefault("whatMustBeVerified",
                BusinessDataSourceCatalog.businessName(code)));
        manual.put("evidenceRequired", body.getOrDefault("evidenceRequired",
                "Credit appraisal note / supporting document"));
        manual.put("responsibleRole", body.getOrDefault("responsibleRole", "Credit Manager"));
        manual.put("outcomeBehaviour", body.getOrDefault("outcomeBehaviour", "PASS / FAIL / REFER"));
        manual.put("governanceStatus", body.getOrDefault("governanceStatus", GOV_PROPOSED));

        Map<String, Object> expression = Map.of(
                "op", "MANUAL_VERIFICATION",
                "actor", manual.get("responsibleRole"),
                "evidence", manual.get("evidenceRequired"),
                "outcomes", "PASS,FAIL,REFER");
        Map<String, Object> def = businessDefinitionFromBody(body);
        def.put("manualVerification", manual);
        String gov = String.valueOf(body.getOrDefault("governanceStatus", GOV_PROPOSED));
        CiPolicyMetricCandidate m = upsertExecutableMeasure(
                session, code, expression, def, "MANUAL_VERIFICATION", gov, body);
        Map<String, Object> meta = new LinkedHashMap<>(m.getMetadata());
        meta.put("verificationMode", "MANUAL");
        meta.put("availability", GOV_APPROVED.equalsIgnoreCase(gov) ? "CANDIDATE" : "CANDIDATE");
        // Manual approved → still MANUAL not READY auto — implementability treats as MANUAL_VERIFICATION
        m.setMetadata(meta);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resolution", MANUAL_VERIFICATION);
        out.put("manualVerification", manual);
        out.put("configured", true);
        out.put("metricCreated", false); // manual is not an automated measure
        out.put("message", "Manual verification designated — remains visible in the policy package.");
        out.put("affectedRules", affectedRules(session, code));
        out.put("allowCanonicalAuthority", false);
        persistenceService.saveSessionSnapshot(session);
        return out;
    }

    private void persistNonExecutableProposal(
            PolicyStudioSession session, String code, Map<String, Object> definition,
            String calcType, String execStatus, Map<String, Object> body) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("custom", true);
        meta.put("businessMeasure", true);
        meta.put("NEW_METRIC_CANDIDATE", true);
        meta.put("executable", false);
        meta.put("availability", "UNAVAILABLE");
        meta.put("governanceStatus", GOV_PROPOSED);
        meta.put("executabilityStatus", execStatus);
        meta.put("businessDefinition", definition);
        meta.put("metricCreated", false);
        CiPolicyMetricCandidate m = CiPolicyMetricCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(session.getClauses().isEmpty() ? null : session.getClauses().get(0).getId())
                .metricName(String.valueOf(body.getOrDefault("metricName",
                        BusinessDataSourceCatalog.businessName(code))))
                .candidateCanonicalCode(code)
                .systemMetricId(code.replace('.', '_').toUpperCase(Locale.ROOT))
                .expression(Map.of("op", "NEW_METRIC_CANDIDATE", "reason", execStatus))
                .period(String.valueOf(definition.getOrDefault("period", "TRAILING_3M")))
                .aggregation(calcType)
                .missingDataPolicy("DATA_INSUFFICIENT")
                .reviewStatus(ReviewState.AI_DRAFTED.name())
                .metadata(meta)
                .build();
        session.getMetricCandidates().removeIf(c -> code.equals(c.getCandidateCanonicalCode()));
        session.getMetricCandidates().add(m);
    }

    private CiPolicyMetricCandidate upsertExecutableMeasure(
            PolicyStudioSession session,
            String code,
            Map<String, Object> expression,
            Map<String, Object> definition,
            String calcType,
            String governance,
            Map<String, Object> body) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("custom", true);
        meta.put("businessMeasure", true);
        meta.put("executable", true);
        meta.put("governanceStatus", governance);
        meta.put("businessDefinition", definition);
        meta.put("calculationType", calcType);
        meta.put("availability", GOV_APPROVED.equalsIgnoreCase(governance) ? "AVAILABLE" : "CANDIDATE");
        meta.put("metricCreated", GOV_APPROVED.equalsIgnoreCase(governance));
        meta.put("definedBy", body.getOrDefault("resolvedBy", "credit_manager"));
        meta.put("definedAt", Instant.now().toString());
        if (body.get("missingDataBehaviour") != null) {
            meta.put("missingDataBehaviour", String.valueOf(body.get("missingDataBehaviour")));
        }

        List<Object> inclusions = body.get("include") instanceof List<?> l ? new ArrayList<>(l) : List.of();
        List<Object> exclusions = body.get("exclude") instanceof List<?> l ? new ArrayList<>(l) : List.of();

        CiPolicyMetricCandidate m = CiPolicyMetricCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(session.getClauses().isEmpty() ? null : session.getClauses().get(0).getId())
                .metricName(String.valueOf(body.getOrDefault("metricName",
                        BusinessDataSourceCatalog.businessName(code))))
                .candidateCanonicalCode(code)
                .systemMetricId(String.valueOf(body.getOrDefault("systemMetricId",
                        code.replace('.', '_').toUpperCase(Locale.ROOT))))
                .expression(expression)
                .inclusions(inclusions)
                .exclusions(exclusions)
                .period(String.valueOf(definition.getOrDefault("period",
                        body.getOrDefault("period", "TRAILING_3M"))))
                .aggregation(calcType)
                .baseMetric(body.get("baseMetric") == null ? null : String.valueOf(body.get("baseMetric")))
                .dependencies(body.get("dependencies") instanceof List<?> l ? new ArrayList<>(l)
                        : List.of("bank.transaction"))
                .missingDataPolicy(String.valueOf(body.getOrDefault("missingDataPolicy",
                        body.getOrDefault("missingDataBehaviour", "DATA_INSUFFICIENT"))))
                .reviewStatus(GOV_APPROVED.equalsIgnoreCase(governance)
                        ? ReviewState.CREDIT_MANAGER_APPROVED.name()
                        : ReviewState.AI_DRAFTED.name())
                .metadata(meta)
                .build();
        session.getMetricCandidates().removeIf(c -> code.equals(c.getCandidateCanonicalCode()));
        session.getMetricCandidates().add(m);
        return m;
    }

    private Map<String, Object> buildExpression(
            String code, String calcType, Map<String, Object> definition, Map<String, Object> body) {
        String type = calcType == null ? "" : calcType.toUpperCase(Locale.ROOT);
        Map<String, Object> expr = new LinkedHashMap<>();
        switch (type) {
            case "DIRECT_FIELD" -> {
                expr.put("op", "DIRECT_FIELD");
                expr.put("path", code);
            }
            case "SUM", "AVERAGE", "COUNT", "MIN", "MAX" -> {
                expr.put("op", type);
                expr.put("source", definition.getOrDefault("sourceData", "bank.transaction"));
                expr.put("period", definition.getOrDefault("period", "TRAILING_3M"));
                if (body.get("include") != null) {
                    expr.put("include", body.get("include"));
                }
                if (body.get("exclude") != null) {
                    expr.put("exclude", body.get("exclude"));
                }
            }
            case "PERIOD_AGGREGATION", "FILTERED_TRANSACTION_AGGREGATION" -> {
                expr.put("op", "AVERAGE");
                expr.put("source", "bank.transaction");
                expr.put("period", definition.getOrDefault("period", "TRAILING_3M"));
                expr.put("aggregation", "AVERAGE_DAILY");
                expr.put("include", body.getOrDefault("include", List.of()));
                expr.put("exclude", body.getOrDefault("exclude", List.of()));
                expr.put("formula", definition.getOrDefault("calculation",
                        "SUM eligible credits / number of applicable days"));
            }
            case "RATIO", "PERCENTAGE" -> {
                expr.put("op", "DIVIDE");
                expr.put("numerator", body.getOrDefault("numerator", code));
                expr.put("denominator", body.getOrDefault("denominator", "application.proposed_edi"));
            }
            case "DERIVED_FROM_MEASURES" -> {
                expr.put("op", "DERIVED");
                expr.put("base", body.getOrDefault("baseMetric", code));
                expr.put("exclusions", body.getOrDefault("exclude", List.of()));
            }
            case "CROSS_SOURCE_VARIANCE" -> {
                expr.put("op", "RECONCILE");
                expr.put("left", body.getOrDefault("left", "gst.turnover.trailing_12m"));
                expr.put("right", body.getOrDefault("right", "bank.turnover.trailing_12m"));
                expr.put("method", "PERCENT_VARIANCE");
            }
            case "MANUAL_VERIFICATION" -> {
                expr.put("op", "MANUAL_VERIFICATION");
            }
            default -> {
                // Unsupported — leave empty (caller treats as non-executable)
            }
        }
        return expr;
    }

    private Map<String, Object> validateDefinition(
            String code,
            String classification,
            Map<String, Object> definition,
            String sourceAvailability,
            boolean requireCalcType) {
        Map<String, Object> v = new LinkedHashMap<>();
        if ("APPLICATION_INPUT".equals(classification)) {
            v.put("status", APPLICATION_INPUT_REQUIRED);
            v.put("message", "Capture on application form — not a provider metric.");
            return v;
        }
        if ("BOUNDARY_CONDITION".equals(classification)
                || "BUSINESS_TERM".equals(classification)
                || "CLASSIFICATION".equals(classification)) {
            v.put("status", BUSINESS_DEFINITION_REQUIRED);
            v.put("message", "Resolve as " + classification + " in Ambiguous Terms — not a calculated measure.");
            return v;
        }
        if ("UNAVAILABLE".equals(sourceAvailability) || "UNKNOWN".equals(sourceAvailability)) {
            // Underlying bank transactions may still exist for settlement measures
            if (code != null && code.contains("settlement")) {
                v.put("status", READY_TO_CONFIGURE);
                v.put("message", "Bank transactions available; settlement classification measure can be configured "
                        + "via filtered aggregation if Credit Head defines include/exclude.");
                v.put("supported", true);
                return v;
            }
            if (code != null && (code.contains("overdue.age") || code.contains("credit_after")
                    || code.contains("clean_history"))) {
                v.put("status", ENGINEERING_REQUIRED);
                v.put("message", "Bureau event sequencing for this measure is not safely representable "
                        + "in the current metric framework without engineering.");
                return v;
            }
            v.put("status", SOURCE_DATA_REQUIRED);
            v.put("message", "Underlying source data is not available in the configured registry.");
            return v;
        }
        String meaning = definition == null ? "" : String.valueOf(definition.getOrDefault("businessMeaning", ""));
        if (meaning.isBlank() && requireCalcType) {
            v.put("status", BUSINESS_DEFINITION_REQUIRED);
            v.put("message", "Business meaning is required.");
            return v;
        }
        String calc = definition == null ? "" : String.valueOf(definition.getOrDefault("calculationType",
                definition.getOrDefault("calculation", "")));
        if (requireCalcType && !calc.isBlank()) {
            String upper = calc.toUpperCase(Locale.ROOT);
            if (!SUPPORTED_CALC_TYPES.contains(upper)
                    && !upper.contains("SUM") && !upper.contains("AVERAGE")
                    && !upper.contains("FILTER")) {
                // free-text calculation description is OK if we map to FILTERED_TRANSACTION_AGGREGATION
                if (code != null && code.contains("settlement")) {
                    v.put("status", READY_TO_CONFIGURE);
                    v.put("suggestedCalculationType", "FILTERED_TRANSACTION_AGGREGATION");
                    v.put("message", "Definition can be configured as filtered transaction aggregation.");
                    return v;
                }
            }
        }
        v.put("status", READY_TO_CONFIGURE);
        v.put("message", "Existing metric framework can represent this measure after Credit Head confirmation.");
        v.put("supported", true);
        return v;
    }

    private Map<String, Object> proposeDefinition(
            PolicyStudioSession session, String code, String classification) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("label", "AI PROPOSED DEFINITION");
        p.put("businessMeasureName", BusinessDataSourceCatalog.businessName(code));
        p.put("classification", classification);

        String clauseText = relatedClauseText(session, code);
        p.put("sourceClause", clauseText);

        if ("APPLICATION_INPUT".equals(classification) || (code != null && code.contains("proposed_edi"))) {
            p.put("businessMeaning", "Equated Daily Instalment proposed for the loan application.");
            p.put("sourceData", "Application form");
            p.put("calculationType", "DIRECT_FIELD");
            p.put("period", "N/A");
            p.put("include", List.of());
            p.put("exclude", List.of());
            p.put("calculation", "Captured from application — not derived from banking.");
            p.put("missingDataBehaviour", "DATA_INSUFFICIENT");
            p.put("assumptions", List.of(
                    "EDI is application-supplied, not inferred from bureau EMI.",
                    "Product team must place the field on the application journey."));
            return p;
        }

        if (code != null && code.contains("settlement")) {
            p.put("businessMeaning",
                    "Average daily eligible QR/digital settlement credits received in the previous three months.");
            p.put("sourceData", "Bank Transactions — Account Aggregator");
            p.put("calculationType", "FILTERED_TRANSACTION_AGGREGATION");
            p.put("period", "Previous 3 months");
            p.put("include", List.of(
                    "QR merchant settlements",
                    "UPI merchant settlements",
                    "Approved payment gateway settlements"));
            p.put("exclude", List.of(
                    "Loan credits",
                    "Internal transfers",
                    "Cash deposits",
                    "Reversals",
                    "Refunds"));
            p.put("calculation", "SUM eligible credits / number of applicable days");
            p.put("missingDataBehaviour", "Manual Credit Review");
            p.put("assumptions", List.of(
                    "Eligible settlement taxonomy must be confirmed by Credit Head.",
                    "Account Aggregator transaction feed is available.",
                    "Merchant category tagging quality may vary by bank."));
            p.put("questions", List.of(
                    "Which payment gateway settlement narrations are in-scope?",
                    "Are UPI P2P credits excluded?"));
            return p;
        }

        if (code != null && code.contains("avg_daily_balance")) {
            p.put("businessMeaning", "Average daily closing balance over the previous three months, "
                    + "after policy exclusions if defined.");
            p.put("sourceData", "Bank Transactions — Account Aggregator");
            p.put("calculationType", "PERIOD_AGGREGATION");
            p.put("period", "Previous 3 months");
            p.put("include", List.of("EOD balances"));
            p.put("exclude", List.of());
            p.put("calculation", "Average of daily end-of-day balances");
            p.put("missingDataBehaviour", "DATA_INSUFFICIENT");
            return p;
        }

        p.put("businessMeaning", "Business meaning for " + BusinessDataSourceCatalog.businessName(code)
                + " derived from policy language — confirm before use.");
        p.put("sourceData", BusinessDataSourceCatalog.describe(code).get("primarySource"));
        p.put("calculationType", "PERIOD_AGGREGATION");
        p.put("period", "As stated in policy");
        p.put("include", List.of());
        p.put("exclude", List.of());
        p.put("calculation", "Confirm calculation with Credit Head");
        p.put("missingDataBehaviour", "DATA_INSUFFICIENT");
        p.put("assumptions", List.of("Proposal is grounded in clause text only — not auto-executable."));
        return p;
    }

    private String relatedClauseText(PolicyStudioSession session, String code) {
        for (CiPolicyAmbiguity a : session.getAmbiguities()) {
            String phrase = a.getPhrase() == null ? "" : a.getPhrase().toLowerCase(Locale.ROOT);
            if ((code != null && code.contains("settlement") && (phrase.contains("settlement") || phrase.contains("qr")))
                    || (code != null && code.contains("edi") && phrase.contains("edi"))
                    || (code != null && code.contains("overdue") && phrase.contains("overdue"))
                    || (code != null && code.contains("clean") && phrase.contains("clean"))
                    || (code != null && code.contains("ntc") && phrase.contains("ntc"))) {
                CiPolicyClause c = session.getClauses().stream()
                        .filter(cl -> cl.getId() != null && cl.getId().equals(a.getClauseId()))
                        .findFirst().orElse(null);
                if (c != null) {
                    return c.getSourceText();
                }
            }
        }
        return "";
    }

    private List<Map<String, Object>> affectedRules(PolicyStudioSession session, String code) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
            Set<String> paths = new LinkedHashSet<>();
            collectPaths(rule.getExpression(), paths);
            if (paths.contains(code) || paths.stream().anyMatch(p -> p.equals(code))) {
                rows.add(Map.of(
                        "ruleId", rule.getId() == null ? "" : rule.getId().toString(),
                        "systemRuleId", rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId(),
                        "ruleName", rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().replace('_', ' ')));
            }
        }
        // Also match via open ambiguities linked to rules
        if (rows.isEmpty() && code != null) {
            for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
                String sys = rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().toUpperCase(Locale.ROOT);
                if ((code.contains("settlement") && sys.contains("SETTLEMENT"))
                        || (code.contains("edi") && sys.contains("EDI"))
                        || (code.contains("adb") && sys.contains("ADB"))
                        || (code.contains("overdue") && sys.contains("OVERDUE"))
                        || (code.contains("ntc") && sys.contains("NTC"))
                        || (code.contains("clean") && sys.contains("CLEAN"))) {
                    rows.add(Map.of(
                            "ruleId", rule.getId() == null ? "" : rule.getId().toString(),
                            "systemRuleId", rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId(),
                            "ruleName", rule.getSystemRuleId() == null ? "" : rule.getSystemRuleId().replace('_', ' ')));
                }
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private void collectPaths(Object node, Set<String> out) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            for (String key : List.of("metric", "fact", "applicationField", "path", "reconciliation")) {
                if (m.get(key) != null) {
                    out.add(String.valueOf(m.get(key)));
                }
            }
            for (Object v : m.values()) {
                collectPaths(v, out);
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                collectPaths(v, out);
            }
        }
    }

    private String sourceDataAvailability(String code) {
        Map<String, Object> reg = registry.registry();
        for (String key : List.of("metrics", "facts")) {
            Object list = reg.get(key);
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m && code.equals(String.valueOf(m.get("code")))) {
                        return String.valueOf(m.get("availability"));
                    }
                }
            }
        }
        // Banking settlement derives from AA transactions even if settlement metric UNAVAILABLE
        if (code != null && (code.startsWith("banking.") || code.startsWith("bank."))) {
            return "PARTIAL";
        }
        if (code != null && code.startsWith("application.")) {
            return "APPLICATION";
        }
        return "UNKNOWN";
    }

    private String classifyFromCode(String code, PolicyStudioSession session) {
        CiPolicyAmbiguity amb = session.getAmbiguities().stream()
                .filter(a -> {
                    String ph = a.getPhrase() == null ? "" : a.getPhrase().toLowerCase(Locale.ROOT);
                    return (code.contains("edi") && ph.contains("edi"))
                            || (code.contains("settlement") && (ph.contains("settlement") || ph.contains("qr")))
                            || (code.contains("ntc") && ph.contains("ntc"))
                            || (code.contains("clean") && ph.contains("clean"))
                            || (code.contains("overdue") && ph.contains("overdue"));
                })
                .findFirst().orElse(null);
        return classifyGap(code, "DEFINITION_REQUIRED", null, amb);
    }

    private CiPolicyMetricCandidate findMeasure(PolicyStudioSession session, String code) {
        return session.getMetricCandidates().stream()
                .filter(m -> code.equals(m.getCandidateCanonicalCode()) || code.equals(m.getSystemMetricId()))
                .findFirst().orElse(null);
    }

    private Map<String, Object> summarizeMeasure(CiPolicyMetricCandidate m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", m.getId() == null ? null : m.getId().toString());
        s.put("metricName", m.getMetricName());
        s.put("code", m.getCandidateCanonicalCode());
        s.put("executable", isExecutableExpression(m.getExpression()));
        s.put("approved", isApprovedExecutableMeasure(m));
        s.put("governanceStatus", m.getMetadata() == null ? null : m.getMetadata().get("governanceStatus"));
        s.put("availability", m.getMetadata() == null ? null : m.getMetadata().get("availability"));
        s.put("metricCreated", m.getMetadata() != null && Boolean.TRUE.equals(m.getMetadata().get("metricCreated")));
        return s;
    }

    private Map<String, Object> businessDefinitionFromBody(Map<String, Object> body) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("businessMeasureName", body.getOrDefault("metricName", body.get("businessMeasureName")));
        d.put("businessMeaning", body.getOrDefault("businessMeaning", ""));
        d.put("sourceData", body.getOrDefault("sourceData", ""));
        d.put("period", body.getOrDefault("period", "TRAILING_3M"));
        d.put("calculationType", body.getOrDefault("calculationType", ""));
        d.put("calculation", body.getOrDefault("calculation", ""));
        d.put("include", body.getOrDefault("include", List.of()));
        d.put("exclude", body.getOrDefault("exclude", List.of()));
        d.put("missingDataBehaviour", body.getOrDefault("missingDataBehaviour", "DATA_INSUFFICIENT"));
        d.put("humanConfirmed", body.get("humanConfirmed"));
        d.put("aiProposed", body.getOrDefault("aiProposed", false));
        return d;
    }

    private void closeRelatedAmbiguities(PolicyStudioSession session, String code, Map<String, Object> body) {
        String resolvedBy = String.valueOf(body.getOrDefault("resolvedBy", "credit_manager"));
        for (CiPolicyAmbiguity a : session.getAmbiguities()) {
            if (!"OPEN".equals(a.getResolutionStatus())
                    && !"CLARIFICATION_REQUESTED".equals(a.getResolutionStatus())) {
                continue;
            }
            String ph = a.getPhrase() == null ? "" : a.getPhrase().toLowerCase(Locale.ROOT);
            boolean match = (code != null && code.contains("settlement")
                    && (ph.contains("settlement") || ph.contains("qr")))
                    || (code != null && code.contains("edi") && ph.contains("edi") && !ph.contains("settlement"))
                    || (code != null && code.contains("overdue") && ph.contains("overdue"))
                    || (code != null && code.contains("clean") && ph.contains("clean"))
                    || (code != null && code.contains("ntc") && ph.contains("ntc"));
            if (!match) {
                continue;
            }
            a.setResolutionStatus("RESOLVED");
            a.setResolvedOption("BUSINESS_MEASURE_APPROVED:" + code);
            a.setResolvedBy(resolvedBy);
            a.setResolvedAt(Instant.now());
            a.setResolutionNotes("Resolved via Business Measure Designer (governed)");
            Map<String, Object> meta = new LinkedHashMap<>(a.getMetadata() == null ? Map.of() : a.getMetadata());
            meta.put("metricCreated", true);
            meta.put("resolvedVia", "BusinessMeasureDesigner");
            a.setMetadata(meta);
        }
    }

    private Map<String, Object> sessionMeta(PolicyStudioSession session) {
        return new LinkedHashMap<>();
    }

    /** Enrich implementability gaps with classification + designer affordances. */
    public void enrichImplementability(Map<String, Object> implementability, PolicyStudioSession session) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = implementability.get("gaps") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = implementability.get("rules") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();

        Map<String, Long> classCounts = new LinkedHashMap<>();
        for (Map<String, Object> gap : gaps) {
            String code = String.valueOf(gap.get("dataElementCode"));
            String status = String.valueOf(gap.get("status"));
            String classification = classifyFromCode(code, session);
            if ("__UNMAPPED__".equals(code) || "null".equals(code)) {
                classification = "SOURCE_MAPPING";
            }
            gap.put("classification", classification);
            gap.put("canDefineBusinessMeasure",
                    "BUSINESS_MEASURE".equals(classification)
                            && (PolicyImplementabilityService.DEFINITION_REQUIRED.equals(status)
                            || PolicyImplementabilityService.METRIC_REQUIRED.equals(status)
                            || "UNAVAILABLE".equals(String.valueOf(gap.get("configuredSource")))));
            gap.put("canDefineApplicationInput", "APPLICATION_INPUT".equals(classification));
            gap.put("designerAction", gapAction(classification));
            classCounts.merge(classification, 1L, Long::sum);
        }
        implementability.put("gapClassifications", classCounts);

        for (Map<String, Object> rule : rules) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reqs = rule.get("requirements") instanceof List<?> l
                    ? (List<Map<String, Object>>) l : List.of();
            for (Map<String, Object> req : reqs) {
                String code = String.valueOf(req.get("dataElementCode"));
                enrichRequirementStages(req, code, session);
            }
            rule.put("readinessStages", rollupStages(reqs));
        }
    }

    private String gapAction(String classification) {
        return switch (classification) {
            case "BUSINESS_MEASURE" -> "DEFINE_BUSINESS_MEASURE";
            case "APPLICATION_INPUT" -> "APPLICATION_INPUT_REQUIRED";
            case "BOUNDARY_CONDITION", "BUSINESS_TERM", "CLASSIFICATION" -> "RESOLVE_AMBIGUOUS_TERM";
            case "RECONCILIATION" -> "DEFINE_RECONCILIATION";
            case "SOURCE_MAPPING" -> "COMPLETE_MAPPING";
            default -> "REVIEW";
        };
    }

    private void enrichRequirementStages(Map<String, Object> req, String code, PolicyStudioSession session) {
        String sourceAv = sourceDataAvailability(code);
        CiPolicyMetricCandidate measure = findMeasure(session, code);
        boolean defPresent = measure != null && measure.getMetadata() != null
                && measure.getMetadata().get("businessDefinition") != null;
        boolean measureOk = isApprovedExecutableMeasure(measure);
        boolean manual = measure != null && measure.getMetadata() != null
                && "MANUAL".equalsIgnoreCase(String.valueOf(measure.getMetadata().get("verificationMode")));

        Map<String, Object> stages = new LinkedHashMap<>();
        stages.put("sourceData", Map.of(
                "ok", !"UNAVAILABLE".equals(sourceAv) && !"UNKNOWN".equals(sourceAv),
                "label", sourceAv,
                "detail", BusinessDataSourceCatalog.describe(code).get("primarySource")));
        stages.put("businessDefinition", Map.of(
                "ok", defPresent || measureOk,
                "label", defPresent || measureOk ? "Confirmed" : "Needs confirmation"));
        stages.put("businessMeasure", Map.of(
                "ok", measureOk || manual,
                "label", measureOk ? "Configured" : (manual ? "Manual verification" : "Not yet configured")));
        String status = String.valueOf(req.get("status"));
        boolean ruleOk = PolicyImplementabilityService.READY.equals(status)
                || PolicyImplementabilityService.READY_WITH_FALLBACK.equals(status)
                || PolicyImplementabilityService.MANUAL_VERIFICATION.equals(status)
                || PolicyImplementabilityService.MANUAL_INPUT_REQUIRED.equals(status);
        stages.put("ruleExecutability", Map.of(
                "ok", ruleOk && (measureOk || manual
                        || PolicyImplementabilityService.READY.equals(status)
                        || PolicyImplementabilityService.MANUAL_INPUT_REQUIRED.equals(status)),
                "label", ruleOk ? String.valueOf(req.get("status")) : "Not executable"));
        req.put("stages", stages);
        req.put("classification", classifyFromCode(code, session));
        req.put("canDefineBusinessMeasure",
                "BUSINESS_MEASURE".equals(req.get("classification"))
                        && !measureOk
                        && !manual);
    }

    private Map<String, Object> rollupStages(List<Map<String, Object>> reqs) {
        boolean source = true, def = true, measure = true, rule = true;
        for (Map<String, Object> req : reqs) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stages = req.get("stages") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            source &= stageOk(stages.get("sourceData"));
            def &= stageOk(stages.get("businessDefinition"));
            measure &= stageOk(stages.get("businessMeasure"));
            rule &= stageOk(stages.get("ruleExecutability"));
        }
        return Map.of(
                "sourceData", source,
                "businessDefinition", def,
                "businessMeasure", measure,
                "ruleExecutability", rule);
    }

    private boolean stageOk(Object stage) {
        if (stage instanceof Map<?, ?> m) {
            return Boolean.TRUE.equals(m.get("ok"));
        }
        return false;
    }
}
