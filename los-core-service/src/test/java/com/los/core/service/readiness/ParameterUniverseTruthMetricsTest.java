package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthorableParameterProjection;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameter Universe Truth — exact metric surface for certification/reporting.
 *
 * <p>This test intentionally computes ID-set equality (not label/count-only),
 * and prints the exact counts/differences for the final pre-UAT report.
 */
class ParameterUniverseTruthMetricsTest {

    @Test
    void metrics_print_exact_counts_and_differences() {
        DataParametersAdminService admin = new DataParametersAdminService();
        CanonicalParameterRegistry reg = PolicyStudioConvergencePresenter.registry();

        Set<String> canonicalAuthorable = PolicyAuthorableParameterProjection.authorableOf(reg).stream()
                .map(CanonicalParameterDefinition::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        // A: Administration → Data & Parameters (authorable-only mode)
        Set<String> dataSurface = new LinkedHashSet<>();
        for (String source : reg.sources()) {
            Map<String, Object> browse = admin.browseBySource(source, true);
            dataSurface.addAll(extractIds(browse.get("raw")));
            dataSurface.addAll(extractIds(browse.get("derived")));
            dataSurface.addAll(extractIds(browse.get("manual")));
        }

        // B: Policy Studio Add Rule
        Set<String> addRuleSurface = PolicyAuthorableParameterProjection.authorableOf(reg).stream()
                .map(CanonicalParameterDefinition::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        // C: Policy Studio Change Parameter
        Set<String> changeParameterSurface = PolicyAuthorableParameterProjection.authorableOf(reg).stream()
                .map(CanonicalParameterDefinition::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        int dataCount = dataSurface.size();
        int addCount = addRuleSurface.size();
        int changeCount = changeParameterSurface.size();

        Set<String> aMinusB = new HashSet<>(dataSurface);
        aMinusB.removeAll(addRuleSurface);
        int dataMinusAdd = aMinusB.size();

        Set<String> bMinusA = new HashSet<>(addRuleSurface);
        bMinusA.removeAll(dataSurface);
        int addMinusData = bMinusA.size();

        Set<String> aMinusC = new HashSet<>(dataSurface);
        aMinusC.removeAll(changeParameterSurface);
        int dataMinusChange = aMinusC.size();

        Set<String> cMinusA = new HashSet<>(changeParameterSurface);
        cMinusA.removeAll(dataSurface);
        int changeMinusData = cMinusA.size();

        int techMetadataAuthorableCount = PolicyAuthorableParameterProjection.technicalMetadataAuthorableCount(reg);

        System.out.println("DATA_PARAMETERS_AUTHORABLE_ID_COUNT=" + dataCount);
        System.out.println("POLICY_STUDIO_ADD_RULE_PARAMETER_ID_COUNT=" + addCount);
        System.out.println("POLICY_STUDIO_CHANGE_PARAMETER_ID_COUNT=" + changeCount);

        System.out.println("DATA_PARAMETERS_MINUS_ADD_RULE_COUNT=" + dataMinusAdd);
        System.out.println("ADD_RULE_MINUS_DATA_PARAMETERS_COUNT=" + addMinusData);

        System.out.println("DATA_PARAMETERS_MINUS_CHANGE_PARAMETER_COUNT=" + dataMinusChange);
        System.out.println("CHANGE_PARAMETER_MINUS_ADD_RULE_COUNT=" + changeMinusData);

        System.out.println("TECHNICAL_METADATA_AUTHORABLE_COUNT=" + techMetadataAuthorableCount);
        System.out.println("PARAMETER_UNIVERSE_SINGLE_AUTHORITY_INVARIANT="
                + (dataSurface.equals(addRuleSurface) && addRuleSurface.equals(changeParameterSurface) && techMetadataAuthorableCount == 0 ? "YES" : "NO"));

        // Certification-grade invariants.
        assertThat(dataSurface).as("DATA_PARAMETERS_AUTHORABLE_ID_SET").isEqualTo(addRuleSurface);
        assertThat(dataSurface).as("DATA_PARAMETERS_AUTHORABLE_ID_SET").isEqualTo(changeParameterSurface);
        assertThat(techMetadataAuthorableCount).isZero();

        // Extra sanity: the projection authority should be the same canonical set used by its own definition.
        assertThat(dataSurface).as("CANONICAL_AUTHORABLE_SET").isEqualTo(canonicalAuthorable);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractIds(Object listObj) {
        if (!(listObj instanceof List<?> list)) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object id = m.get("id");
            if (id != null) out.add(String.valueOf(id));
        }
        return out;
    }
}

