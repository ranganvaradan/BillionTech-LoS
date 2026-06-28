package com.los.core.service.borrower;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves which {@link LoanApplication} rows belong to a logged-in borrower, including staff-created
 * invoice-discounting apps where {@code customer_id} was a placeholder until submit/provisioning.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowerApplicationOwnershipService {

    private final LoanApplicationRepository applicationRepository;
    private final LosUserRepository losUserRepository;

    /**
     * Best invoice-discounting application for PLP menus (synced PLP borrower identity).
     */
    public Optional<LoanApplication> findInvoiceDiscountingPlpApplication(UUID borrowerUserId) {
        return findInvoiceDiscountingApplications(borrowerUserId).stream()
                .filter(this::hasPlpBorrowerIdentity)
                .max(Comparator.comparing(LoanApplication::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public boolean isInvoiceDiscountingLinked(UUID borrowerUserId) {
        return findInvoiceDiscountingPlpApplication(borrowerUserId).isPresent();
    }

    public Optional<UUID> resolvePlpBorrowerId(UUID borrowerUserId) {
        return findInvoiceDiscountingPlpApplication(borrowerUserId).map(LoanApplication::getPlpBorrowerId);
    }

    /**
     * When an app matches the borrower's email / mobile / {@code borrowerUserId} but {@code customer_id}
     * is stale, link it so portal lists and detail APIs work.
     */
    @Transactional
    public void reconcileCustomerId(UUID borrowerUserId) {
        LosUser user = losUserRepository.findById(borrowerUserId).orElse(null);
        if (user == null) {
            return;
        }
        for (LoanApplication app : findInvoiceDiscountingApplications(borrowerUserId)) {
            if (borrowerUserId.equals(app.getCustomerId())) {
                continue;
            }
            app.setCustomerId(borrowerUserId);
            Map<String, Object> pi = app.getPersonalInfo() != null
                    ? new LinkedHashMap<>(app.getPersonalInfo()) : new LinkedHashMap<>();
            pi.put("borrowerUserId", borrowerUserId.toString());
            app.setPersonalInfo(pi);
            applicationRepository.save(app);
            log.info("Linked application {} to borrower user {} (customer_id reconciled)",
                    app.getApplicationNumber(), borrowerUserId);
        }
    }

    public boolean ownsApplication(UUID borrowerUserId, LoanApplication app) {
        if (app == null) {
            return false;
        }
        if (borrowerUserId.equals(app.getCustomerId())) {
            return true;
        }
        return findInvoiceDiscountingApplications(borrowerUserId).stream()
                .anyMatch(candidate -> candidate.getId().equals(app.getId()));
    }

    private List<LoanApplication> findInvoiceDiscountingApplications(UUID borrowerUserId) {
        LinkedHashMap<UUID, LoanApplication> byId = new LinkedHashMap<>();

        applicationRepository
                .findFirstByCustomerIdAndLoanProductOrderByUpdatedAtDesc(
                        borrowerUserId, StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .ifPresent(app -> byId.put(app.getId(), app));

        applicationRepository
                .findFirstByCustomerIdAndLoanProductAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(
                        borrowerUserId, StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .ifPresent(app -> byId.putIfAbsent(app.getId(), app));

        applicationRepository
                .findFirstByPersonalInfoBorrowerUserIdAndLoanProductOrderByUpdatedAtDesc(
                        borrowerUserId.toString(), StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .ifPresent(app -> byId.putIfAbsent(app.getId(), app));

        LosUser user = losUserRepository.findById(borrowerUserId).orElse(null);
        String email = normalizedEmail(user);
        String mobileDigits = mobileDigits(user);

        if (!email.isBlank()) {
            applicationRepository
                    .findFirstByBorrowerEmailAndLoanProductOrderByUpdatedAtDesc(
                            email, StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                    .ifPresent(app -> byId.putIfAbsent(app.getId(), app));
        }

        if (mobileDigits.length() >= 10) {
            applicationRepository
                    .findFirstByBorrowerMobileAndLoanProductOrderByUpdatedAtDesc(
                            mobileDigits, StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                    .ifPresent(app -> byId.putIfAbsent(app.getId(), app));
        }

        for (LoanApplication app : applicationRepository.findPlpSyncedApplicationsForBorrowerContact(
                borrowerUserId, email, mobileDigits)) {
            byId.putIfAbsent(app.getId(), app);
        }

        return new ArrayList<>(byId.values());
    }

    private boolean hasPlpBorrowerIdentity(LoanApplication app) {
        if (app == null) {
            return false;
        }
        boolean plpPipelineReady = app.getSubProgramId() != null
                && app.getPlpBorrowerSyncStatus() == PlpSyncStatus.SYNC_SUCCESS
                && (app.getPlpLinkSyncStatus() == PlpSyncStatus.SYNC_SUCCESS
                || app.getPlpSubProgramBorrowerId() != null);
        if (plpPipelineReady) {
            return app.getPlpBorrowerId() != null
                    || app.getPlpSubProgramBorrowerId() != null
                    || app.getPlpBorrowerProgramMappingId() != null;
        }
        if (!InvoiceDiscountingApplicationRules.isInvoiceDiscounting(app)) {
            return false;
        }
        if (app.getPlpBorrowerId() != null) {
            return true;
        }
        return app.getPlpBorrowerSyncStatus() == PlpSyncStatus.SYNC_SUCCESS
                || app.getPlpSubProgramBorrowerId() != null
                || app.getPlpBorrowerProgramMappingId() != null;
    }

    private static String normalizedEmail(LosUser user) {
        if (user == null || user.getEmail() == null) {
            return "";
        }
        return user.getEmail().trim().toLowerCase();
    }

    private static String mobileDigits(LosUser user) {
        if (user == null || user.getMobile() == null) {
            return "";
        }
        return user.getMobile().replaceAll("\\D", "");
    }

    /** Email on file for an application (used when reconciling by contact). */
    public static String applicationContactEmail(LoanApplication app) {
        return ApplicationPartyResolver.resolveEmail(app);
    }
}
