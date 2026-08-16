package com.los.core.creditintelligence.policystudio.truth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin surface facades over {@link CanonicalParameterTruthProjection}.
 * Surfaces may add presentation context; they must not invent execution/certification.
 */
public final class SurfaceCanonicalTruthFacade {

    public static final String DATA_PARAMETERS = "DATA_PARAMETERS";
    public static final String POLICY_STUDIO = "POLICY_STUDIO";
    public static final String POLICY_INVENTORY = "POLICY_INVENTORY";
    public static final String SCORECARD_PICKER = "SCORECARD_PICKER";
    public static final String POLICY_TEST = "POLICY_TEST";
    public static final String WORKFLOW_W6 = "WORKFLOW_W6";
    public static final String UNDERWRITING = "UNDERWRITING";

    private SurfaceCanonicalTruthFacade() {}

    public static Map<String, Object> forSurface(String surface, String canonicalId) {
        Map<String, Object> truth = CanonicalParameterTruthProjection.project(canonicalId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("surface", surface);
        out.put("truthAuthority", CanonicalParameterTruthProjection.AUTHORITY);
        out.put("canonicalId", truth.get("canonicalId"));
        out.put("found", truth.get("found"));
        out.put("executionCapability", capability(truth));
        out.put("executionStatus", status(truth));
        out.put("certificationStatus", certStatus(truth));
        out.put("primaryStatus", truth.get("primaryStatus"));
        out.put("primaryStatusLabel", truth.get("primaryStatusLabel"));
        out.put("nextAction", truth.get("nextAction"));
        out.put("calculationExplanation", truth.get("calculationExplanation"));
        out.put("parameterClassLabel", truth.get("parameterClassLabel"));
        out.put("liveUseDisplay", truth.get("liveUseDisplay"));
        out.put("executionLabel", truth.get("executionLabel"));
        out.put("certificationLabel", truth.get("certificationLabel"));
        out.put("semantic", truth.get("semantic"));
        out.put("execution", truth.get("execution"));
        out.put("calculation", truth.get("calculation"));
        out.put("certification", truth.get("certification"));
        out.put("acquisition", truth.get("acquisition"));
        out.put("policy", truth.get("policy"));
        out.put("advanced", advanced(truth));
        // Surface-specific presentation hints (not alternate truth)
        if (POLICY_STUDIO.equals(surface) || POLICY_INVENTORY.equals(surface)) {
            out.put("outstandingAction", truth.get("nextAction"));
        }
        if (SCORECARD_PICKER.equals(surface)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> semantic = (Map<String, Object>) truth.get("semantic");
            boolean selectable = semantic != null
                    && Boolean.TRUE.equals(semantic.get("policySelectableDefault"));
            out.put("designSelectable", selectable);
            out.put("setupIncomplete", !Boolean.TRUE.equals(capability(truth)));
            out.put("designabilityDoesNotImplyExecutability", true);
        }
        if (POLICY_TEST.equals(surface)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> exec = (Map<String, Object>) truth.get("execution");
            boolean simulated = exec != null && Boolean.TRUE.equals(exec.get("simulatedValue"));
            out.put("simulatedValue", simulated);
            out.put("liveExecutable", Boolean.TRUE.equals(capability(truth)) && !simulated);
            if (simulated && !Boolean.TRUE.equals(capability(truth))) {
                out.put("simulationBanner", "SIMULATED — NOT LIVE-EXECUTABLE");
            }
            out.put("testSuccessDoesNotImplyCertification", true);
        }
        if (WORKFLOW_W6.equals(surface)) {
            out.put("sourceStatusAxis", "ACQUISITION");
            out.put("factStatusAxis", "FACT");
            out.put("parameterExecutionAxis", "EXECUTION");
            out.put("axesAreDistinct", true);
            out.put("providerCompleteDoesNotImplyParameterReady", true);
        }
        if (UNDERWRITING.equals(surface)) {
            String cert = certStatus(truth);
            out.put("liveBlockedNotCertified",
                    !"CERTIFIED".equals(cert) && Boolean.TRUE.equals(capability(truth)));
            out.put("liveBlockIsOperationalNotCreditReject", true);
            out.put("operationalBlockLabel",
                    !"CERTIFIED".equals(cert) ? "LIVE BLOCKED — NOT CERTIFIED" : null);
        }
        return out;
    }

    public static Boolean capability(Map<String, Object> truth) {
        if (truth == null) return false;
        Object exec = truth.get("execution");
        if (exec instanceof Map<?, ?> m) {
            return Boolean.TRUE.equals(m.get("capability"));
        }
        return false;
    }

    public static String status(Map<String, Object> truth) {
        if (truth == null) return null;
        Object exec = truth.get("execution");
        if (exec instanceof Map<?, ?> m && m.get("status") != null) {
            return String.valueOf(m.get("status"));
        }
        return null;
    }

    public static String certStatus(Map<String, Object> truth) {
        if (truth == null) return "UNCERTIFIED";
        Object cert = truth.get("certification");
        if (cert instanceof Map<?, ?> m) {
            Object s = m.get("status");
            if (s == null) s = m.get("certificationStatus");
            if (s != null) return String.valueOf(s);
        }
        return "UNCERTIFIED";
    }

    private static Map<String, Object> advanced(Map<String, Object> truth) {
        Map<String, Object> adv = new LinkedHashMap<>();
        adv.put("canonicalId", truth.get("canonicalId"));
        adv.put("semantic", truth.get("semantic"));
        adv.put("execution", truth.get("execution"));
        adv.put("calculation", truth.get("calculation"));
        adv.put("certification", truth.get("certification"));
        adv.put("acquisition", truth.get("acquisition"));
        adv.put("spineCapability", truth.get("spineCapability"));
        adv.put("note", "Single Advanced block — technical axes only");
        return adv;
    }
}
