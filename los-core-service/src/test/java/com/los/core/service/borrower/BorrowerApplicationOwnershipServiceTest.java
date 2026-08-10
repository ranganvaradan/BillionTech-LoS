package com.los.core.service.borrower;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.IntakeSegment;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.LosUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowerApplicationOwnershipServiceTest {

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private LosUserRepository losUserRepository;

    @InjectMocks
    private BorrowerApplicationOwnershipService service;

    @Test
    void isInvoiceDiscountingLinked_matchesByBorrowerEmailWhenCustomerIdDiffers() {
        UUID borrowerUserId = UUID.randomUUID();
        UUID plpBorrowerId = UUID.randomUUID();
        LosUser user = LosUser.builder().id(borrowerUserId).email("borrower@test.com").build();
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.BORROWER)
                .plpBorrowerId(plpBorrowerId)
                .plpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .personalInfo(Map.of("email", "borrower@test.com"))
                .build();

        when(applicationRepository.findFirstByCustomerIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByCustomerIdAndLoanProductAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByPersonalInfoBorrowerUserIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId.toString()), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(losUserRepository.findById(borrowerUserId)).thenReturn(Optional.of(user));
        when(applicationRepository.findFirstByBorrowerEmailAndLoanProductOrderByUpdatedAtDesc(
                eq("borrower@test.com"), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.of(app));
        when(applicationRepository.findPlpSyncedApplicationsForBorrowerContact(
                eq(borrowerUserId), eq("borrower@test.com"), eq("")))
                .thenReturn(List.of());

        assertThat(service.isInvoiceDiscountingLinked(borrowerUserId)).isTrue();
        assertThat(service.resolvePlpBorrowerId(borrowerUserId)).contains(plpBorrowerId);
    }

    @Test
    void isInvoiceDiscountingLinked_matchesByMobileWhenCustomerIdDiffers() {
        UUID borrowerUserId = UUID.randomUUID();
        UUID plpBorrowerId = UUID.randomUUID();
        LosUser user = LosUser.builder().id(borrowerUserId).email("other@test.com").mobile("9876518067").build();
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.BORROWER)
                .plpBorrowerId(plpBorrowerId)
                .plpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .personalInfo(Map.of("mobile", "9876518067"))
                .build();

        when(applicationRepository.findFirstByCustomerIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByCustomerIdAndLoanProductAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByPersonalInfoBorrowerUserIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId.toString()), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(losUserRepository.findById(borrowerUserId)).thenReturn(Optional.of(user));
        when(applicationRepository.findFirstByBorrowerEmailAndLoanProductOrderByUpdatedAtDesc(
                eq("other@test.com"), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByBorrowerMobileAndLoanProductOrderByUpdatedAtDesc(
                eq("9876518067"), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.of(app));
        when(applicationRepository.findPlpSyncedApplicationsForBorrowerContact(
                eq(borrowerUserId), eq("other@test.com"), eq("9876518067")))
                .thenReturn(List.of());

        assertThat(service.isInvoiceDiscountingLinked(borrowerUserId)).isTrue();
    }

    @Test
    void isInvoiceDiscountingLinked_matchesPlpSyncedApplicationByContact() {
        UUID borrowerUserId = UUID.randomUUID();
        UUID plpBorrowerId = UUID.randomUUID();
        UUID subProgramId = UUID.randomUUID();
        LosUser user = LosUser.builder().id(borrowerUserId).email("linked@test.com").build();
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.BORROWER)
                .subProgramId(subProgramId)
                .plpBorrowerId(plpBorrowerId)
                .plpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .plpLinkSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .plpMappingSyncStatus(PlpSyncStatus.SYNC_SUCCESS)
                .personalInfo(Map.of("email", "linked@test.com"))
                .build();

        stubNoDirectCustomerMatch(borrowerUserId, user);
        when(applicationRepository.findPlpSyncedApplicationsForBorrowerContact(
                eq(borrowerUserId), eq("linked@test.com"), eq("")))
                .thenReturn(List.of(app));

        assertThat(service.isInvoiceDiscountingLinked(borrowerUserId)).isTrue();
    }

    private void stubNoDirectCustomerMatch(UUID borrowerUserId, LosUser user) {
        when(applicationRepository.findFirstByCustomerIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByCustomerIdAndLoanProductAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByPersonalInfoBorrowerUserIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId.toString()), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(losUserRepository.findById(borrowerUserId)).thenReturn(Optional.of(user));
        when(applicationRepository.findFirstByBorrowerEmailAndLoanProductOrderByUpdatedAtDesc(
                eq(user.getEmail()), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByBorrowerMobileAndLoanProductOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.anyString(), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
    }

    @Test
    void reconcileCustomerId_updatesStaleCustomerIdFromEmailMatch() {
        UUID borrowerUserId = UUID.randomUUID();
        LosUser user = LosUser.builder().id(borrowerUserId).email("borrower@test.com").build();
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("LOS-IND-1")
                .customerId(UUID.randomUUID())
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.BORROWER)
                .plpBorrowerId(UUID.randomUUID())
                .personalInfo(Map.of("email", "borrower@test.com"))
                .build();

        when(losUserRepository.findById(borrowerUserId)).thenReturn(Optional.of(user));
        when(applicationRepository.findFirstByCustomerIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByCustomerIdAndLoanProductAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(
                eq(borrowerUserId), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByPersonalInfoBorrowerUserIdAndLoanProductOrderByUpdatedAtDesc(
                eq(borrowerUserId.toString()), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.empty());
        when(applicationRepository.findFirstByBorrowerEmailAndLoanProductOrderByUpdatedAtDesc(
                eq("borrower@test.com"), eq(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)))
                .thenReturn(Optional.of(app));
        when(applicationRepository.findPlpSyncedApplicationsForBorrowerContact(
                eq(borrowerUserId), eq("borrower@test.com"), eq("")))
                .thenReturn(List.of());

        service.reconcileCustomerId(borrowerUserId);

        assertThat(app.getCustomerId()).isEqualTo(borrowerUserId);
        verify(applicationRepository).save(app);
    }
}
