package com.los.core.creditintelligence.policystudio.truth;

import com.los.core.creditintelligence.policystudio.certification.CertifiableArtifactType;
import com.los.core.creditintelligence.policystudio.certification.CertificationScopeType;
import com.los.core.creditintelligence.policystudio.certification.CertificationStatus;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationAuthority;
import com.los.core.creditintelligence.policystudio.certification.ProductionCertificationService;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityProjection;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticProjection;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticRegistry;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wave-9 single canonical truth projection for all lender/admin surfaces.
 * Consumes GACAT semantics + CPES ExecutionResult + ProductionCertification — never invents capability.
 */
public final class CanonicalParameterTruthProjection {

    public static final String AUTHORITY = "CanonicalParameterTruthProjection";

    private CanonicalParameterTruthProjection() {}

    public static Map<String, Object> project(String canonicalId) {
        return project(canonicalId, EvaluationMode.POLICY_TEST, null, null);
    }

    public static Map<String, Object> project(
            String canonicalId,
            EvaluationMode mode,
            EvaluationContext context,
            LocalDate evaluationAsOf) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectionAuthority", AUTHORITY);
        out.put("canonicalId", canonicalId);

        Optional<CanonicalParameterDefinition> opt = canonicalId == null || canonicalId.isBlank()
                ? Optional.empty()
                : PolicyStudioConvergencePresenter.registry().findById(canonicalId.trim());
        if (opt.isEmpty()) {
            out.put("found", false);
            out.put("primaryStatus", "NOT_IN_CATALOGUE");
            out.put("primaryStatusLabel", "Not in catalogue");
            return out;
        }
        CanonicalParameterDefinition def = opt.get();
        out.put("found", true);

        // --- semantic (Wave-4) ---
        Map<String, Object> semantic = new LinkedHashMap<>(GacatSemanticProjection.project(def));
        semantic.put("businessName", def.businessName());
        semantic.put("description", def.calculationSummary());
        semantic.put("valueType", def.type());
        semantic.put("unit", def.unit());
        semantic.put("sourceDomain", def.evaluatedFrom());
        out.put("semantic", semantic);

        boolean manual = isManual(def);
        boolean authored = isAuthored(def, semantic);
        boolean raw = "RAW".equalsIgnoreCase(String.valueOf(semantic.get("calculationMode")))
                || (def.capability() != null && !def.capability().derivationDefined()
                && def.capability().implemented() && !manual);

        // --- execution (CPES) ---
        Map<String, Object> execution = new LinkedHashMap<>();
        EvaluationMode em = mode == null ? EvaluationMode.POLICY_TEST : mode;
        boolean capability = ExecutionCapabilityAuthority.hasExecutionCapability(def.id(), em);
        execution.put("capability", capability);
        execution.put("policyTestCapable",
                ExecutionCapabilityAuthority.hasExecutionCapability(def.id(), EvaluationMode.POLICY_TEST));
        execution.put("w6Capable",
                ExecutionCapabilityAuthority.hasExecutionCapability(def.id(), EvaluationMode.W6_ACQUISITION));
        execution.put("underwritingCapable",
                ExecutionCapabilityAuthority.hasExecutionCapability(def.id(), EvaluationMode.UNDERWRITING));

        ExecutionResult er = null;
        try {
            var spine = ExecutionCapabilityAuthority.orNull();
            if (spine != null) {
                EvaluationContext ctx = context != null ? context : EvaluationContext.builder()
                        .mode(em)
                        .evaluationAsOf(evaluationAsOf != null ? evaluationAsOf : LocalDate.of(2026, 8, 1))
                        .build();
                er = spine.resolveAndExecute(def.id(), ctx);
            }
        } catch (Exception ignored) {
            // projection must not fail surfaces
        }
        if (er != null) {
            execution.put("status", er.status() == null ? null : er.status().name());
            execution.put("valueAvailable", er.valueAvailable());
            execution.put("value", er.value());
            execution.put("producerType", er.producerType() == null ? null : er.producerType().name());
            execution.put("producerId", er.producerId());
            execution.put("dependencies", er.dependencies());
            execution.put("missingReason", er.missingReason() != null ? er.missingReason() : er.reason());
            execution.put("evaluationAsOf", er.evaluationAsOf() == null ? null : er.evaluationAsOf().toString());
            execution.put("simulatedValue", er.simulatedValue());
            execution.put("executionContract", er.toCanonicalContractMap());
        } else {
            execution.put("status", capability ? ExecutionStatus.DATA_NOT_AVAILABLE.name()
                    : (authored && !capability ? ExecutionStatus.CALCULATION_NOT_DEFINED.name()
                    : ExecutionStatus.NOT_EXECUTABLE.name()));
            execution.put("valueAvailable", false);
            execution.put("simulatedValue", false);
        }
        out.put("execution", execution);

        // --- calculation ---
        Map<String, Object> calculation = new LinkedHashMap<>();
        boolean derivationDefined = def.capability() != null && def.capability().derivationDefined();
        boolean authoredHowPresent = AuthoredDerivedCalculationSupport.latestExecutableHow(def.id()).isPresent();
        boolean calcRequired = authored && !capability;
        calculation.put("required", calcRequired);
        calculation.put("definitionPresent", capability || authoredHowPresent);
        calculation.put("definitionId", (capability || authoredHowPresent) ? def.id() : null);
        calculation.put("definitionVersion", (capability || authoredHowPresent) ? "1" : null);
        calculation.put("tested", false); // TESTED ≠ CERTIFIED; not inferred here
        calculation.put("explanation", LenderTruthDisplayMapper.calculationExplanation(
                def, semantic, capability, calcRequired, manual, raw));
        calculation.put("catalogueDerivationDefinedLegacy", derivationDefined);
        out.put("calculation", calculation);

        // --- certification (Wave-8) ---
        Map<String, Object> certification = new LinkedHashMap<>();
        ProductionCertificationService certSvc = ProductionCertificationAuthority.get();
        if (certSvc != null) {
            Map<String, Object> proj = certSvc.projectionFor(
                    CertifiableArtifactType.CANONICAL_PARAMETER_PRODUCER,
                    def.id(), "1", CertificationScopeType.PLATFORM, null);
            if (!CertificationStatus.CERTIFIED.name().equals(String.valueOf(proj.get("certificationStatus")))) {
                Map<String, Object> authoredCert = certSvc.projectionFor(
                        CertifiableArtifactType.AUTHORED_CALCULATION_DEFINITION,
                        def.id(), "1", CertificationScopeType.PLATFORM, null);
                if (CertificationStatus.CERTIFIED.name()
                        .equals(String.valueOf(authoredCert.get("certificationStatus")))) {
                    proj = authoredCert;
                }
            }
            certification.putAll(proj);
        } else {
            certification.put("status", CertificationStatus.UNCERTIFIED.name());
            certification.put("certificationStatus", CertificationStatus.UNCERTIFIED.name());
            certification.put("certificationId", null);
        }
        if (!certification.containsKey("status") && certification.get("certificationStatus") != null) {
            certification.put("status", certification.get("certificationStatus"));
        }
        out.put("certification", certification);

        // --- acquisition (placeholder — context-specific) ---
        Map<String, Object> acquisition = new LinkedHashMap<>();
        acquisition.put("sourceRequired", !manual);
        acquisition.put("sourceStatus", "CONTEXT_DEPENDENT");
        acquisition.put("note", "W6 acquisition status is orthogonal; set by workflow context");
        out.put("acquisition", acquisition);

        // --- policy (generic; rule-specific overlays may add) ---
        Map<String, Object> policy = new LinkedHashMap<>();
        boolean selectable = Boolean.TRUE.equals(semantic.get("policySelectableDefault"));
        policy.put("policySelectableDefault", selectable);
        policy.put("policyTestReady", execution.get("policyTestCapable"));
        policy.put("referencedByRule", null);
        policy.put("ruleAccepted", null);
        policy.put("outstandingAction", LenderTruthDisplayMapper.nextAction(
                capability, calcRequired, er, certification));
        out.put("policy", policy);

        // spine capability projection for facades (no independent invent)
        out.put("spineCapability", CanonicalParameterCapabilityProjection.project(def));

        Map<String, Object> display = LenderTruthDisplayMapper.primary(def, semantic, execution, calculation, certification);
        out.putAll(display);
        out.put("catalogueImplementedIsNotReadiness", true);
        out.put("catalogueProductionReadyIsNotLiveStatus", true);
        out.put("definitionTestedIsNotCertification", true);
        return out;
    }

    public static List<Map<String, Object>> projectAll() {
        return PolicyStudioConvergencePresenter.registry().all().stream()
                .map(d -> project(d.id()))
                .toList();
    }

    private static boolean isManual(CanonicalParameterDefinition def) {
        if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type())
                || "INPUT".equalsIgnoreCase(def.type())
                || "APPLICATION_INPUT".equalsIgnoreCase(def.type())) {
            return true;
        }
        return GacatSemanticRegistry.shared().find(def.id())
                .map(e -> "MANUAL_INPUT".equals(e.parameterClass().name())
                        || "MANUAL".equals(e.calculationMode().name()))
                .orElse(false);
    }

    private static boolean isAuthored(CanonicalParameterDefinition def, Map<String, Object> semantic) {
        Object mode = semantic.get("calculationMode");
        if (mode != null && "AUTHORED".equalsIgnoreCase(String.valueOf(mode))) return true;
        Object pc = semantic.get("parameterClass");
        return pc != null && "BUSINESS_PARAMETER".equals(String.valueOf(pc))
                && mode != null && !"RAW".equalsIgnoreCase(String.valueOf(mode))
                && !"BUILT_IN".equalsIgnoreCase(String.valueOf(mode))
                && !"MANUAL".equalsIgnoreCase(String.valueOf(mode));
    }
}
