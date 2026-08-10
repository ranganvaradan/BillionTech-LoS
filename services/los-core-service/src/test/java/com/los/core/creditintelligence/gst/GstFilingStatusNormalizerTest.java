package com.los.core.creditintelligence.gst;

import com.los.core.creditintelligence.gst.domain.GstFilingStatus;
import com.los.core.creditintelligence.gst.util.GstFilingStatusNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GstFilingStatusNormalizerTest {

    @Test
    void mapsFiledLatePendingUnknown() {
        assertThat(GstFilingStatusNormalizer.normalize("Filed").status()).isEqualTo(GstFilingStatus.FILED);
        assertThat(GstFilingStatusNormalizer.normalize("Filed", 5).status()).isEqualTo(GstFilingStatus.LATE_FILED);
        assertThat(GstFilingStatusNormalizer.normalize("Filed", 5).delayDays()).isEqualTo(5);
        assertThat(GstFilingStatusNormalizer.normalize("Late Filed").status()).isEqualTo(GstFilingStatus.LATE_FILED);
        assertThat(GstFilingStatusNormalizer.normalize("Pending").status()).isEqualTo(GstFilingStatus.PENDING);
        assertThat(GstFilingStatusNormalizer.normalize("Not Filed").status()).isEqualTo(GstFilingStatus.NOT_FILED);
        assertThat(GstFilingStatusNormalizer.normalize("weird-status").status()).isEqualTo(GstFilingStatus.UNKNOWN);
        assertThat(GstFilingStatusNormalizer.normalize(null).status()).isEqualTo(GstFilingStatus.UNKNOWN);
    }

    @Test
    void unknownIsNotFiledLike() {
        assertThat(GstFilingStatusNormalizer.isFiledLike(GstFilingStatus.UNKNOWN)).isFalse();
        assertThat(GstFilingStatusNormalizer.isFiledLike(GstFilingStatus.FILED)).isTrue();
        assertThat(GstFilingStatusNormalizer.isFiledLike(GstFilingStatus.LATE_FILED)).isTrue();
    }
}
