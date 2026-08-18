package com.los.core.creditintelligence.policystudio.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyGraphParticipationTest {

    @Test
    void acceptedRuleParticipates() {
        assertThat(PolicyGraphParticipation.participates(Map.of("disposition", "ACCEPTED"))).isTrue();
    }

    @Test
    void deletedRuleDoesNotParticipate() {
        assertThat(PolicyGraphParticipation.participates(Map.of(
                "deleted", true,
                "disposition", "DELETED",
                "excludedFromActivation", true))).isFalse();
    }

    @Test
    void deferredSourceNotProvenDoesNotParticipate() {
        assertThat(PolicyGraphParticipation.participates(Map.of(
                "disposition", "DEFERRED_SOURCE_NOT_PROVEN",
                "excludedFromActivation", true,
                "dispositionReason", "SOURCE_NOT_PROVEN"))).isFalse();
    }

    @Test
    void independentBankingFixtureRuleParticipatesWithoutCustomerBranch() {
        // obligation.ratio is a non-bureau independent policy fixture — still participates.
        assertThat(PolicyGraphParticipation.participates(Map.of(
                "disposition", "ACCEPTED",
                "canonicalParameterId", "obligation.ratio"))).isTrue();
    }
}
