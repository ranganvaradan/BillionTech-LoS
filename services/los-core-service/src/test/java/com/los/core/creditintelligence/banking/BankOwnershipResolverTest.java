package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.OwnershipMatchStatus;
import com.los.core.creditintelligence.banking.service.BankOwnershipResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankOwnershipResolverTest {

    @Test
    void ownershipMatchAndMismatch() {
        BankOwnershipResolver resolver = new BankOwnershipResolver();
        assertThat(resolver.resolve("RAMESH KUMAR", "Ramesh Kumar").status())
                .isEqualTo(OwnershipMatchStatus.MATCH);
        assertThat(resolver.resolve("SOMEONE ELSE", "Ramesh Kumar").status())
                .isEqualTo(OwnershipMatchStatus.MISMATCH);
        assertThat(resolver.resolve(null, "Ramesh").status())
                .isEqualTo(OwnershipMatchStatus.UNKNOWN);
    }
}
