package com.los.core.service.loan.intake;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.entity.LosUser;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.LosUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationCustomerIdResolverTest {

    @Mock
    private LosUserRepository losUserRepository;

    private ApplicationCustomerIdResolver resolver;

    private static final UUID BORROWER = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("a1000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        resolver = new ApplicationCustomerIdResolver(losUserRepository);
    }

    @Test
    void borrowerRoleUsesActingUserId() {
        CreateApplicationRequest req = new CreateApplicationRequest();
        assertThat(resolver.resolveCustomerId(req, BORROWER, "BORROWER")).isEqualTo(BORROWER);
    }

    @Test
    void borrowerRoleWithoutIdThrows() {
        CreateApplicationRequest req = new CreateApplicationRequest();
        assertThatThrownBy(() -> resolver.resolveCustomerId(req, null, "BORROWER"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void staffMatchesEmailToRegisteredUser() {
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setBorrowerType(BorrowerType.INDIVIDUAL);
        req.setLoanProduct("P");
        req.setRequestedAmount(new BigDecimal("1"));
        req.setPersonalInfo(Map.of("email", "sahil@gmail.com"));

        LosUser u = LosUser.builder().id(BORROWER).name("Sahil").email("sahil@gmail.com").active(true).build();
        when(losUserRepository.findByEmailIgnoreCase("sahil@gmail.com")).thenReturn(Optional.of(u));

        assertThat(resolver.resolveCustomerId(req, STAFF, "SALES_OFFICER")).isEqualTo(BORROWER);
    }

    @Test
    void staffDoesNotDefaultToStaffIdWhenNoMatch() {
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setBorrowerType(BorrowerType.INDIVIDUAL);
        req.setLoanProduct("P");
        req.setRequestedAmount(new BigDecimal("1"));
        req.setPersonalInfo(Map.of("email", "notfound@x.com", "phone", "9999999999"));
        when(losUserRepository.findByEmailIgnoreCase("notfound@x.com")).thenReturn(Optional.empty());
        when(losUserRepository.findByMobileIsNotNull()).thenReturn(List.of());

        assertThat(resolver.resolveCustomerId(req, STAFF, "ADMINISTRATOR"))
                .isNotEqualTo(STAFF);
    }
}
