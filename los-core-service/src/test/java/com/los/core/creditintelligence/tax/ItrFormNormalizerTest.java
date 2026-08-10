package com.los.core.creditintelligence.tax;

import com.los.core.creditintelligence.tax.domain.ItrForm;
import com.los.core.creditintelligence.tax.util.ItrFormNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItrFormNormalizerTest {

    @Test
    void mapsCommonProviderVariants() {
        assertThat(ItrFormNormalizer.normalize("ITR-6")).isEqualTo(ItrForm.ITR_6);
        assertThat(ItrFormNormalizer.normalize("ITR6")).isEqualTo(ItrForm.ITR_6);
        assertThat(ItrFormNormalizer.normalize("itr 4")).isEqualTo(ItrForm.ITR_4);
        assertThat(ItrFormNormalizer.normalize(null)).isEqualTo(ItrForm.UNKNOWN);
        assertThat(ItrFormNormalizer.isPresumptiveForm(ItrForm.ITR_4)).isTrue();
        assertThat(ItrFormNormalizer.looksPresumptive("Section 44AD")).isTrue();
    }
}
