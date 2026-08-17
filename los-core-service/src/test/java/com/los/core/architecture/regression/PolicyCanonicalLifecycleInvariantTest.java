package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.lifecycle.PolicyBusinessLifecycleStatus;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyCanonicalLifecycleAuthority;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-ACTIVE-LIFECYCLE-AND-CUSTOMER-CATEGORY-LINKAGE-INVARIANT-1
 * Static guards: one lifecycle authority, no Save Draft on ACTIVE, category eligibility
 * from that authority, no invented ACTIVE, no draft masquerading as active.
 */
class PolicyCanonicalLifecycleInvariantTest {

    @Test
    void sameVersionCannotProjectDifferentLifecycleOnListVsVersions() {
        String list = PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", "APPROVED");
        String versions = PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", "APPROVED");
        assertThat(list).isEqualTo(versions);
        assertThat(list).isEqualTo(PolicyBusinessLifecycleStatus.APPROVED);
    }

    @Test
    void activeVersionCannotExposeSaveDraft() {
        var projection = PolicyCanonicalLifecycleAuthority.project(
                "ACTIVE", "APPROVED", UUID.randomUUID(), true);
        assertThat(projection.contentEditable()).isFalse();
        assertThat(PolicyCanonicalLifecycleAuthority.contentEditable(projection.businessStatus())).isFalse();
    }

    @Test
    void customerCategoryEligibilityUsesCanonicalAuthority() {
        var eligible = PolicyCanonicalLifecycleAuthority.project("DRAFT", "APPROVED", UUID.randomUUID(), true);
        assertThat(eligible.lifecycleAuthority()).isEqualTo(PolicyCanonicalLifecycleAuthority.NAME);
        assertThat(eligible.ownerType()).isEqualTo(PolicyCanonicalLifecycleAuthority.OWNER_TYPE);
        assertThat(eligible.eligibleForCustomerCategoryLinkage()).isTrue();

        var draft = PolicyCanonicalLifecycleAuthority.project("DRAFT", "DRAFT", UUID.randomUUID(), false);
        assertThat(draft.eligibleForCustomerCategoryLinkage()).isFalse();
        assertThat(draft.ineligibleReason()).isEqualTo("DRAFT_NOT_ELIGIBLE");
    }

    @Test
    void draftCannotMasqueradeAsActive() {
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", "DRAFT"))
                .isNotEqualTo(PolicyBusinessLifecycleStatus.ACTIVE);
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile("DRAFT", "APPROVED"))
                .isNotEqualTo(PolicyBusinessLifecycleStatus.ACTIVE);
    }

    @Test
    void activeCannotBorrowDraftMetadata() {
        assertThat(PolicyCanonicalLifecycleAuthority.reconcile("ACTIVE", "DRAFT"))
                .isEqualTo(PolicyBusinessLifecycleStatus.ACTIVE);
        assertThat(PolicyCanonicalLifecycleAuthority.contentEditable(
                PolicyCanonicalLifecycleAuthority.reconcile("ACTIVE", "DRAFT"))).isFalse();
    }
}
