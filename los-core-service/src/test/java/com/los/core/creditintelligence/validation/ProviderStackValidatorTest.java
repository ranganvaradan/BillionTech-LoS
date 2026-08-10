package com.los.core.creditintelligence.validation;

import com.los.core.creditintelligence.validation.domain.DataOrigin;
import com.los.core.creditintelligence.validation.model.ProviderStackResult;
import com.los.core.creditintelligence.validation.service.ProviderStackValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderStackValidatorTest {

    private final ProviderStackValidator validator = new ProviderStackValidator();

    @Test
    void allMajorFixtures_extractThroughAdapters() {
        List<ProviderStackResult> results = validator.validateAllMajorFixtures();
        assertThat(results).hasSizeGreaterThanOrEqualTo(9);
        for (ProviderStackResult r : results) {
            assertThat(r.origin()).isEqualTo(DataOrigin.USER_SUPPLIED_SAMPLE);
            assertThat(r.errors()).as(r.provider() + " " + r.metadata()).isEmpty();
            assertThat(r.entityCount() + r.factCount()).as(r.provider()).isGreaterThan(0);
            assertThat(r.parserVersion()).as(r.provider()).isNotBlank();
        }
    }
}
