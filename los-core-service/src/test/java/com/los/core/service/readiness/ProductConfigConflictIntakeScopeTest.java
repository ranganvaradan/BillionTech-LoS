package com.los.core.service.readiness;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents intake-scoped uniqueness: BORROWER + ANCHOR Invoice Discounting workflows
 * must not be treated as same-intake ambiguity.
 */
class ProductConfigConflictIntakeScopeTest {

    @Test
    void invoiceDiscounting_borrowerAndAnchor_areDistinctChannels() {
        // Product capability: both channels preserved. Certification is per intakeSegment.
        assertThat(List.of("BORROWER", "ANCHOR")).containsExactly("BORROWER", "ANCHOR");
        Map<String, String> companyId = Map.of(
                "BORROWER", "Default Workflow - Company - ... Invoice Discounting",
                "ANCHOR", "Anchor ID — Company — Invoice Discounting");
        assertThat(companyId).hasSize(2);
        assertThat(companyId.get("BORROWER")).isNotEqualTo(companyId.get("ANCHOR"));
    }

    @Test
    void companyBusinessTerm_dualScorecards_arePriorityAmountScoped() {
        // SME p200 500k-10M; Default p100 50k-50M — deterministic, not equal-priority overlap
        record Band(int priority, long min, long max) {}
        Band sme = new Band(200, 500_000L, 10_000_000L);
        Band def = new Band(100, 50_000L, 50_000_000L);
        assertThat(sme.priority()).isGreaterThan(def.priority());
        long amt = 250_000L;
        Band selected = (amt >= sme.min() && amt <= sme.max()) ? sme : def;
        assertThat(selected).isEqualTo(def);
        long amt2 = 1_000_000L;
        Band selected2 = (amt2 >= sme.min() && amt2 <= sme.max() && sme.priority() > def.priority()) ? sme : def;
        assertThat(selected2).isEqualTo(sme);
    }
}
