package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PinnedArtifactSelection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable canonical configuration identity package.
 * Holds identities, not configuration content.
 */
public record CanonicalApplicationConfiguration(
        UUID applicationId,
        UUID customerCategoryId,
        Integer customerCategoryVersion,
        String customerCategoryCode,
        UUID workflowVersionId,
        Integer workflowVersionNumber,
        UUID policyApplicabilityId,
        UUID policyDocumentId,
        Integer policyDocumentVersion,
        String policyVersionLabel,
        UUID scorecardId,
        Integer scorecardVersion,
        boolean scorecardRequired,
        boolean scorecardExplicitlyAbsent,
        UUID bureauReportId,
        String bureauProviderCode,
        String bureauParserVersion,
        String bureauNormalizerVersion,
        LocalDate evaluationAsOf,
        List<CanonicalCalculationPin> calculationDefinitionPins,
        Instant resolutionTimestamp,
        Map<String, String> resolutionProvenance
) {
    public CanonicalApplicationConfiguration {
        calculationDefinitionPins = calculationDefinitionPins == null
                ? List.of() : List.copyOf(calculationDefinitionPins);
        resolutionProvenance = resolutionProvenance == null
                ? Map.of() : Map.copyOf(resolutionProvenance);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", uuid(applicationId));
        m.put("customerCategoryId", uuid(customerCategoryId));
        m.put("customerCategoryVersion", customerCategoryVersion);
        m.put("customerCategoryCode", customerCategoryCode);
        m.put("workflowVersionId", uuid(workflowVersionId));
        m.put("workflowVersionNumber", workflowVersionNumber);
        m.put("policyApplicabilityId", uuid(policyApplicabilityId));
        m.put("policyDocumentId", uuid(policyDocumentId));
        m.put("policyDocumentVersion", policyDocumentVersion);
        m.put("policyVersionLabel", policyVersionLabel);
        m.put("scorecardId", uuid(scorecardId));
        m.put("scorecardVersion", scorecardVersion);
        m.put("scorecardRequired", scorecardRequired);
        m.put("scorecardExplicitlyAbsent", scorecardExplicitlyAbsent);
        m.put("bureauReportId", uuid(bureauReportId));
        m.put("bureauProviderCode", bureauProviderCode);
        m.put("bureauParserVersion", bureauParserVersion);
        m.put("bureauNormalizerVersion", bureauNormalizerVersion);
        m.put("evaluationAsOf", evaluationAsOf == null ? null : evaluationAsOf.toString());
        List<Map<String, Object>> pins = new ArrayList<>();
        for (CanonicalCalculationPin pin : calculationDefinitionPins) {
            pins.add(pin.toMap());
        }
        m.put("calculationDefinitionPins", pins);
        m.put("resolutionTimestamp", resolutionTimestamp == null ? null : resolutionTimestamp.toString());
        m.put("resolutionProvenance", new LinkedHashMap<>(resolutionProvenance));
        return m;
    }

    @SuppressWarnings("unchecked")
    public static CanonicalApplicationConfiguration fromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        List<CanonicalCalculationPin> pins = new ArrayList<>();
        Object rawPins = m.get("calculationDefinitionPins");
        if (rawPins instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> pm) {
                    pins.add(CanonicalCalculationPin.fromMap((Map<String, Object>) pm));
                }
            }
        }
        Map<String, String> prov = new LinkedHashMap<>();
        Object rawProv = m.get("resolutionProvenance");
        if (rawProv instanceof Map<?, ?> pm) {
            pm.forEach((k, v) -> {
                if (k != null) {
                    prov.put(String.valueOf(k), v == null ? null : String.valueOf(v));
                }
            });
        }
        LocalDate asOf = null;
        Object rawAsOf = m.get("evaluationAsOf");
        if (rawAsOf != null && !String.valueOf(rawAsOf).isBlank()) {
            asOf = LocalDate.parse(String.valueOf(rawAsOf));
        }
        Instant ts = null;
        Object rawTs = m.get("resolutionTimestamp");
        if (rawTs != null && !String.valueOf(rawTs).isBlank()) {
            ts = Instant.parse(String.valueOf(rawTs));
        }
        return new CanonicalApplicationConfiguration(
                uuidVal(m.get("applicationId")),
                uuidVal(m.get("customerCategoryId")),
                intVal(m.get("customerCategoryVersion")),
                str(m.get("customerCategoryCode")),
                uuidVal(m.get("workflowVersionId")),
                intVal(m.get("workflowVersionNumber")),
                uuidVal(m.get("policyApplicabilityId")),
                uuidVal(m.get("policyDocumentId")),
                intVal(m.get("policyDocumentVersion")),
                str(m.get("policyVersionLabel")),
                uuidVal(m.get("scorecardId")),
                intVal(m.get("scorecardVersion")),
                bool(m.get("scorecardRequired")),
                bool(m.get("scorecardExplicitlyAbsent")),
                uuidVal(m.get("bureauReportId")),
                str(m.get("bureauProviderCode")),
                str(m.get("bureauParserVersion")),
                str(m.get("bureauNormalizerVersion")),
                asOf,
                pins,
                ts,
                prov);
    }

    /**
     * Identity hash excludes {@code resolutionTimestamp} and provenance.
     * Same pins → same hash across freeze round-trip.
     */
    public String identityHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(uuid(applicationId)).append('|')
                .append(uuid(customerCategoryId)).append('|')
                .append(customerCategoryVersion).append('|')
                .append(customerCategoryCode).append('|')
                .append(uuid(workflowVersionId)).append('|')
                .append(workflowVersionNumber).append('|')
                .append(uuid(policyApplicabilityId)).append('|')
                .append(uuid(policyDocumentId)).append('|')
                .append(policyDocumentVersion).append('|')
                .append(policyVersionLabel).append('|')
                .append(uuid(scorecardId)).append('|')
                .append(scorecardVersion).append('|')
                .append(scorecardRequired).append('|')
                .append(scorecardExplicitlyAbsent).append('|')
                .append(uuid(bureauReportId)).append('|')
                .append(bureauProviderCode).append('|')
                .append(bureauParserVersion).append('|')
                .append(bureauNormalizerVersion).append('|')
                .append(evaluationAsOf).append('|');
        for (CanonicalCalculationPin pin : calculationDefinitionPins) {
            sb.append(pin.parameterId()).append(':')
                    .append(pin.calculationType()).append(':')
                    .append(uuid(pin.calculationDefinitionId())).append(':')
                    .append(pin.calculationDefinitionVersion()).append(';');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("identity hash unavailable", e);
        }
    }

    /**
     * Wave-10 pin map for later W11.3 shadow. Does not invoke CanonicalPolicyRuntime.
     */
    public PinnedArtifactSelection toPinnedArtifactSelection() {
        Map<String, String> calcVers = new LinkedHashMap<>();
        Map<String, String> producers = new LinkedHashMap<>();
        for (CanonicalCalculationPin pin : calculationDefinitionPins) {
            if (pin.parameterId() == null) {
                continue;
            }
            if (pin.calculationDefinitionVersion() != null) {
                calcVers.put(pin.parameterId(), String.valueOf(pin.calculationDefinitionVersion()));
            }
            if (pin.authority() != null) {
                producers.put(pin.parameterId(), pin.authority());
            }
        }
        return PinnedArtifactSelection.builder()
                .policyId(uuid(policyDocumentId))
                .policyVersion(policyDocumentVersion == null ? policyVersionLabel : String.valueOf(policyDocumentVersion))
                .scorecardId(uuid(scorecardId))
                .scorecardVersion(scorecardVersion == null ? null : String.valueOf(scorecardVersion))
                .gacatSemanticVersion(GacatSemanticTaxonomy.SEMANTIC_VERSION)
                .calculationDefinitionVersions(calcVers)
                .producerVersions(producers)
                .evaluationAsOf(evaluationAsOf)
                .build();
    }

    private static String uuid(UUID id) {
        return id == null ? null : id.toString();
    }

    private static UUID uuidVal(Object o) {
        if (o == null || String.valueOf(o).isBlank() || "null".equalsIgnoreCase(String.valueOf(o))) {
            return null;
        }
        return UUID.fromString(String.valueOf(o));
    }

    private static Integer intVal(Object o) {
        if (o == null || String.valueOf(o).isBlank() || "null".equalsIgnoreCase(String.valueOf(o))) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(o));
    }

    private static String str(Object o) {
        return o == null || "null".equalsIgnoreCase(String.valueOf(o)) ? null : String.valueOf(o);
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o != null && Boolean.parseBoolean(String.valueOf(o));
    }
}
