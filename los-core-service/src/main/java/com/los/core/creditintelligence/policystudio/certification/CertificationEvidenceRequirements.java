package com.los.core.creditintelligence.policystudio.certification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimum evidence expectations by artifact type (Wave-8). Not auto-enforced bureaucracy —
 * certify() still requires explicit actor + evidence summary.
 */
public final class CertificationEvidenceRequirements {

    private CertificationEvidenceRequirements() {}

    public static Map<String, Object> forType(CertifiableArtifactType type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("artifactType", type.name());
        m.put("required", switch (type) {
            case CANONICAL_PARAMETER_PRODUCER -> List.of(
                    "exactCanonicalId", "producerIdVersion", "deterministicGolden",
                    "missingDataBehavior", "noFixtureLiveFallback", "exactIdMaterialization",
                    "provenance", "explicitAsOfIfTemporal");
            case AUTHORED_CALCULATION_DEFINITION -> List.of(
                    "exactDefinitionIdVersion", "semanticTargetValidation", "dependencyValidation",
                    "typeUnitValidation", "multipleTestCases", "boundaryCases", "missingDataCase",
                    "explicitAsOfIfTemporal", "approvedAuthorReviewer", "engineVersion");
            case POLICY_VERSION -> List.of(
                    "exactPolicyVersionRuleHashes", "automaticOperandsCertified",
                    "manualInputsClassified", "sameCanonicalPolicyRuntime", "goldenPolicyTest",
                    "missingDataSemanticsReviewed", "noUnresolvedMandatoryOperand",
                    "wave7DecisionOwnershipCompliant");
            case SCORECARD_VERSION -> List.of(
                    "exactScorecardVersion", "factorsLiveCertifiedOrManualPermitted",
                    "factorResolutionViaCpesOnly", "scoreGoldens", "noDuplicatePolicyKnockout",
                    "versionPinned");
            case SOURCE_INTEGRATION -> List.of(
                    "integrationIdVersion", "determinismEvidence", "failureModesDocumented");
        });
        return m;
    }
}
