package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single surface projection of the three authorities:
 * <ul>
 *   <li>GACAT — identity / designability</li>
 *   <li>{@link CanonicalParameterExecutionService} — execution capability by mode</li>
 *   <li>Production Certification — NOT ESTABLISHED until a durable cert authority exists</li>
 * </ul>
 *
 * <p>Does not invent certification from catalogue {@code production_ready}.
 */
public final class CanonicalParameterCapabilityProjection {

    public static final String PROD_CERT_NOT_ESTABLISHED = "NOT_ESTABLISHED";
    public static final String PROD_CERT_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String EXEC_EXECUTABLE = "EXECUTABLE";
    public static final String EXEC_NOT_EXECUTABLE = "NOT_EXECUTABLE";
    public static final String EXEC_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String DESIGN_AVAILABLE = "AVAILABLE";
    public static final String DESIGN_NOT_AVAILABLE = "NOT_AVAILABLE";

    private CanonicalParameterCapabilityProjection() {}

    public static Map<String, Object> project(String canonicalParameterId) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return empty(canonicalParameterId, "Empty parameter id");
        }
        Optional<CanonicalParameterDefinition> opt =
                PolicyStudioConvergencePresenter.registry().findById(canonicalParameterId.trim());
        if (opt.isEmpty()) {
            return empty(canonicalParameterId, "Not in GACAT");
        }
        return project(opt.get());
    }

    public static Map<String, Object> project(CanonicalParameterDefinition def) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authorityModel", ParameterTruthAuthorities.INVARIANT);
        out.put("canonicalParameterId", def.id());
        out.put("businessName", def.businessName());
        out.put("type", def.type());
        out.put("sourceFamily", def.evaluatedFrom());
        out.put("unit", def.unit());

        boolean manual = CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type())
                || "INPUT".equalsIgnoreCase(def.type())
                || "APPLICATION_INPUT".equalsIgnoreCase(def.type());

        // DESIGNABLE — GACAT identity exists; do not require spine producers
        out.put("designAvailability", DESIGN_AVAILABLE);
        out.put("designable", true);
        out.put("policyDesign", Map.of(
                "status", DESIGN_AVAILABLE,
                "available", true,
                "label", "Available for policy design",
                "reason", "Present in GACAT — may be selected in Policy / Scorecard design"));

        boolean pt = ExecutionCapabilityAuthority.hasExecutionCapability(def.id(), EvaluationMode.POLICY_TEST);
        boolean w6 = ExecutionCapabilityAuthority.hasExecutionCapability(def.id(), EvaluationMode.W6_ACQUISITION);
        boolean uw = ExecutionCapabilityAuthority.hasExecutionCapability(def.id(), EvaluationMode.UNDERWRITING);

        out.put("policyTest", execBlock(pt, "Policy Test"));
        out.put("workflow", execBlock(w6, "Workflow / W6"));
        out.put("underwriting", execBlock(uw, "Underwriting"));

        out.put("policyTestExecutable", pt);
        out.put("workflowExecutable", w6);
        out.put("underwritingExecutable", uw);
        // Gate3 / inventory / lifecycle alias — spine POLICY_TEST only
        out.put("policyTestReady", pt);
        out.put("runtimeReady", w6 || uw);
        out.put("executable", pt || w6 || uw);

        String executionState;
        if (manual && pt) {
            executionState = ParameterExecutabilitySupport.MANUAL_AUTHORISED;
        } else if (pt && (w6 || uw)) {
            // Spine-capable beyond Policy Test, but production certification not established
            executionState = ParameterExecutabilitySupport.RUNTIME_READY_NONPROD;
        } else if (pt) {
            executionState = ParameterExecutabilitySupport.POLICY_TEST_READY;
        } else if (w6 || uw) {
            executionState = ParameterExecutabilitySupport.RUNTIME_READY_NONPROD;
        } else {
            executionState = ParameterExecutabilitySupport.DATA_SOURCE_UNAVAILABLE;
        }
        out.put("executionState", executionState);

        // Production Certification — Wave-8 ledger when installed; never catalogue production_ready
        boolean catalogueClaim = def.capability() != null && def.capability().productionReady();
        Map<String, Object> prod = new LinkedHashMap<>();
        var certSvc = com.los.core.creditintelligence.policystudio.certification
                .ProductionCertificationAuthority.get();
        String certStatus = PROD_CERT_NOT_ESTABLISHED;
        boolean certified = false;
        String certId = null;
        if (manual) {
            prod.put("status", PROD_CERT_NOT_APPLICABLE);
            prod.put("certified", false);
            prod.put("label", "Not applicable");
            prod.put("reason", "Manual / application input — not a provider production certification");
            certStatus = PROD_CERT_NOT_APPLICABLE;
        } else if (certSvc != null) {
            Map<String, Object> proj = certSvc.projectionFor(
                    com.los.core.creditintelligence.policystudio.certification.CertifiableArtifactType
                            .CANONICAL_PARAMETER_PRODUCER,
                    def.id(), "1",
                    com.los.core.creditintelligence.policystudio.certification.CertificationScopeType.PLATFORM,
                    null);
            if (!"CERTIFIED".equals(String.valueOf(proj.get("certificationStatus")))) {
                Map<String, Object> authored = certSvc.projectionFor(
                        com.los.core.creditintelligence.policystudio.certification.CertifiableArtifactType
                                .AUTHORED_CALCULATION_DEFINITION,
                        def.id(), "1",
                        com.los.core.creditintelligence.policystudio.certification.CertificationScopeType.PLATFORM,
                        null);
                if ("CERTIFIED".equals(String.valueOf(authored.get("certificationStatus")))) {
                    proj = authored;
                }
            }
            certStatus = String.valueOf(proj.getOrDefault("certificationStatus",
                    com.los.core.creditintelligence.policystudio.certification.CertificationStatus.UNCERTIFIED.name()));
            certified = "CERTIFIED".equals(certStatus);
            certId = proj.get("certificationId") == null ? null : String.valueOf(proj.get("certificationId"));
            prod.put("status", certified ? "CERTIFIED" : certStatus);
            prod.put("certificationStatus", certStatus);
            prod.put("certificationId", certId);
            prod.put("certified", certified);
            prod.put("label", certified ? "Approved for live use" : "Not approved for live use");
            prod.put("reason", certified
                    ? "Exact artifact certified in production certification ledger"
                    : "Certification ledger has no CERTIFIED grant — catalogue flag is not proof");
        } else {
            prod.put("status", PROD_CERT_NOT_ESTABLISHED);
            prod.put("certificationStatus",
                    com.los.core.creditintelligence.policystudio.certification.CertificationStatus.UNCERTIFIED.name());
            prod.put("certified", false);
            prod.put("label", "Not certified");
            prod.put("reason", "Production certification authority not installed — catalogue flag is not proof");
        }
        prod.put("legacyCatalogueProductionReadyClaim", catalogueClaim);
        out.put("productionCertification", prod);
        out.put("productionReady", false);
        out.put("productionCertified", certified);
        out.put("certificationStatus", certStatus);

        // Advanced / internal legacy claims (descriptive only)
        Map<String, Object> legacy = new LinkedHashMap<>();
        if (def.capability() != null) {
            legacy.put("catalogueImplemented", def.capability().implemented());
            legacy.put("catalogueProductionReady", def.capability().productionReady());
            legacy.put("catalogueDerivationDefined", def.capability().derivationDefined());
            legacy.put("catalogueSourceAvailable", def.capability().sourceAvailable());
        }
        legacy.put("note", "Legacy catalogue claims — not execution or certification truth");
        out.put("legacyCatalogueClaims", legacy);
        out.put("runtimeFactAliases", ParameterExecutabilitySupport.runtimeFactAliases(def.id()));

        List<String> blockers = new ArrayList<>();
        if (!pt) {
            blockers.add("No CanonicalParameterExecutionService producer capable in POLICY_TEST");
        }
        out.put("blockers", blockers);
        out.put("executionAuthority", ParameterTruthAuthorities.EXECUTION_SPINE);
        out.put("identityAuthority", ParameterTruthAuthorities.GACAT_IDENTITY);
        out.put("productionCertificationAuthority", ParameterTruthAuthorities.PRODUCTION_CERTIFICATION);
        out.put("catalogueFlagsAreNotExecutionAuthority", true);
        // Wave-1: expose empty-context contract sample for POLICY_TEST (capability vs value)
        try {
            CanonicalParameterExecutionService spine = ExecutionCapabilityAuthority.orNull();
            if (spine != null) {
                EvaluationContext probeCtx = EvaluationContext.builder()
                        .mode(EvaluationMode.POLICY_TEST)
                        .build();
                ExecutionResult probe = spine.resolveAndExecute(def.id(), probeCtx);
                out.put("executionContractSample", CanonicalExecutionContractProjection.project(probe));
            }
        } catch (Exception ignored) {
            // projection must not fail design surfaces
        }
        return out;
    }

    /** True iff every non-blank id is spine-capable in POLICY_TEST. Empty list → false. */
    public static boolean allPolicyTestCapable(Iterable<String> canonicalParameterIds) {
        boolean any = false;
        for (String id : canonicalParameterIds) {
            if (id == null || id.isBlank()) {
                return false;
            }
            any = true;
            if (!ExecutionCapabilityAuthority.hasExecutionCapability(id.trim(), EvaluationMode.POLICY_TEST)) {
                return false;
            }
        }
        return any;
    }

    private static Map<String, Object> execBlock(boolean capable, String surface) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", capable ? EXEC_EXECUTABLE : EXEC_NOT_EXECUTABLE);
        m.put("executable", capable);
        m.put("label", capable ? "Executable" : "Not yet executable");
        m.put("reason", capable
                ? "CanonicalParameterExecutionService has a capable producer for " + surface
                : "No capable producer registered for " + surface);
        return m;
    }

    private static Map<String, Object> empty(String id, String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("canonicalParameterId", id);
        out.put("designable", false);
        out.put("designAvailability", DESIGN_NOT_AVAILABLE);
        out.put("policyTestExecutable", false);
        out.put("workflowExecutable", false);
        out.put("underwritingExecutable", false);
        out.put("policyTestReady", false);
        out.put("runtimeReady", false);
        out.put("executable", false);
        out.put("productionReady", false);
        out.put("productionCertified", false);
        out.put("executionState", ParameterExecutabilitySupport.DATA_SOURCE_UNAVAILABLE);
        out.put("blockers", List.of(reason));
        out.put("productionCertification", Map.of(
                "status", PROD_CERT_NOT_ESTABLISHED,
                "certified", false,
                "label", "Not certified",
                "reason", reason));
        return out;
    }
}
