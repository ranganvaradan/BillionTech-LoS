package com.los.core.creditintelligence.policystudio.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCanonicalLifecycleAuthorityTest {

    @Test
    void sessionDraftCannotMaskCatalogueApproved() {
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", "APPROVED"))
                .isEqualTo(PolicyBusinessLifecycleStatus.APPROVED);
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile(null, "APPROVED"))
                .isEqualTo(PolicyBusinessLifecycleStatus.APPROVED);
    }

    @Test
    void sessionActiveIsNotOverwrittenByCatalogueApproved() {
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile("ACTIVE", "APPROVED"))
                .isEqualTo(PolicyBusinessLifecycleStatus.ACTIVE);
    }

    @Test
    void doesNotInventActiveFromBlankSession() {
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile(null, "APPROVED"))
                .isNotEqualTo(PolicyBusinessLifecycleStatus.ACTIVE);
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", null))
                .isEqualTo(PolicyBusinessLifecycleStatus.DRAFT);
    }

    @Test
    void sameVersionCannotProjectDifferentLifecycle() {
        String canonical = PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", "APPROVED");
        assertThat(canonical).isEqualTo(PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", "APPROVED"));
        assertThat(PolicyCanonicalLifecycleAuthority.project("DRAFT", "APPROVED", UUID.randomUUID(), true)
                .businessStatus()).isEqualTo(canonical);
    }

    @Test
    void activeAndApprovedAreNotEditable() {
        assertThat(PolicyCanonicalLifecycleAuthority.contentEditable("ACTIVE")).isFalse();
        assertThat(PolicyCanonicalLifecycleAuthority.contentEditable("APPROVED")).isFalse();
        assertThat(PolicyCanonicalLifecycleAuthority.contentEditable("DRAFT")).isTrue();
        assertThat(PolicyCanonicalLifecycleAuthority.project("ACTIVE", "APPROVED", null, true)
                .contentEditable()).isFalse();
    }

    @Test
    void categoryEligibilityUsesCanonicalStatus() {
        assertThat(PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage("APPROVED")).isTrue();
        assertThat(PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage("ACTIVE")).isTrue();
        assertThat(PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage("DRAFT")).isFalse();
        assertThat(PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage("IN REVIEW")).isFalse();
        assertThat(PolicyCanonicalLifecycleAuthority.ineligibleReason("DRAFT")).isEqualTo("DRAFT_NOT_ELIGIBLE");
    }

    @Test
    void retiredWinsOverSessionActive() {
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile("ACTIVE", "RETIRED"))
                .isEqualTo(PolicyBusinessLifecycleStatus.RETIRED);
    }
}
