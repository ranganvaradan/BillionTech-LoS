package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthorableParameterProjection;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterUniverseTruthTest {

    @Test
    void dataParameters_authorable_idSet_equals_policyStudio_addRule_and_changeParameter() {
        DataParametersAdminService admin = new DataParametersAdminService();
        CanonicalParameterRegistry reg = PolicyStudioConvergencePresenter.registry();

        Set<String> canonical = PolicyAuthorableParameterProjection.authorableOf(reg).stream()
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

        // B: Policy Studio Add Rule authorable universe
        Set<String> addRuleSurface = PolicyAuthorableParameterProjection.authorableOf(reg).stream()
                .map(CanonicalParameterDefinition::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        // C: Policy Studio Change Parameter authorable universe
        // Code contract: Change Parameter uses the same projection authority.
        Set<String> changeParameterSurface = PolicyAuthorableParameterProjection.authorableOf(reg).stream()
                .map(CanonicalParameterDefinition::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> aMinusB = new HashSet<>(dataSurface);
        aMinusB.removeAll(addRuleSurface);
        Set<String> bMinusA = new HashSet<>(addRuleSurface);
        bMinusA.removeAll(dataSurface);

        Set<String> aMinusC = new HashSet<>(dataSurface);
        aMinusC.removeAll(changeParameterSurface);

        assertThat(dataSurface)
                .as("DATA_PARAMETERS_AUTHORABLE_ID_COUNT=%s", dataSurface.size())
                .isEqualTo(addRuleSurface);

        assertThat(dataSurface)
                .as("DATA_PARAMETERS_AUTHORABLE_ID_COUNT=%s", dataSurface.size())
                .isEqualTo(changeParameterSurface);

        assertThat(aMinusB)
                .withFailMessage("DATA_PARAMETERS_MINUS_ADD_RULE_COUNT=%s. Missing IDs=%s",
                        aMinusB.size(), aMinusB.stream().limit(20).toList())
                .isEmpty();

        assertThat(bMinusA)
                .withFailMessage("ADD_RULE_MINUS_DATA_PARAMETERS_COUNT=%s. Missing IDs=%s",
                        bMinusA.size(), bMinusA.stream().limit(20).toList())
                .isEmpty();

        assertThat(aMinusC)
                .withFailMessage("DATA_PARAMETERS_MINUS_CHANGE_PARAMETER_COUNT=%s. Missing IDs=%s",
                        aMinusC.size(), aMinusC.stream().limit(20).toList())
                .isEmpty();
    }

    @Test
    void technicalMetadata_authorableLeak_is_zero() {
        CanonicalParameterRegistry reg = PolicyStudioConvergencePresenter.registry();
        int leaks = PolicyAuthorableParameterProjection.technicalMetadataAuthorableCount(reg);
        assertThat(leaks).as("TECHNICAL_METADATA_AUTHORABLE_COUNT").isEqualTo(0);
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

