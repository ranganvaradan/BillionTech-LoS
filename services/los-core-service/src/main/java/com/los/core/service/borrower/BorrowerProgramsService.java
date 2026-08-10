package com.los.core.service.borrower;

import com.los.core.exception.ForbiddenException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.BorrowerProgramEnrollmentResponse;
import com.los.core.model.dto.response.BorrowerProgramsResponse;
import com.los.core.repository.LosUserRepository;
import com.los.plp.client.PlpBorrowerClient;
import com.los.plp.client.PlpIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Borrower program memberships via PLP (same data as PLP borrower portal Programs page).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowerProgramsService {

    private static final String ROLE = "BORROWER";

    private final LosUserRepository losUserRepository;
    private final PlpBorrowerClient plpBorrowerClient;
    private final BorrowerApplicationOwnershipService ownershipService;

    public void requireBorrower(String role) {
        if (role == null || !ROLE.equalsIgnoreCase(role.trim())) {
            throw new ForbiddenException("This area is only for borrowers. Sign in with a borrower account.");
        }
    }

    public boolean isInvoiceDiscountingLinked(UUID borrowerUserId) {
        return ownershipService.isInvoiceDiscountingLinked(borrowerUserId);
    }

    public InvoiceDiscountingFlowFlags resolveInvoiceDiscountingFlowFlags(UUID borrowerUserId) {
        if (!isInvoiceDiscountingLinked(borrowerUserId)) {
            return InvoiceDiscountingFlowFlags.none();
        }
        Optional<UUID> plpBorrowerId = ownershipService.resolvePlpBorrowerId(borrowerUserId);
        if (plpBorrowerId.isEmpty() || !plpBorrowerClient.isEnabled()) {
            return InvoiceDiscountingFlowFlags.linkedOnly();
        }
        try {
            List<Map<String, Object>> subPrograms = plpBorrowerClient.listSubPrograms();
            boolean pbf = false;
            boolean sbd = false;
            boolean po = false;
            for (Map<String, Object> sp : subPrograms) {
                UUID spId = asUuid(sp.get("id"));
                if (spId == null) {
                    continue;
                }
                try {
                    if (plpBorrowerClient.getBorrowerLimitSummary(spId, plpBorrowerId.get()).isEmpty()) {
                        continue;
                    }
                } catch (PlpIntegrationException e) {
                    continue;
                }
                String flow = asString(sp.get("flowType"));
                if (flow == null || flow.isBlank() || "PURCHASE_BILL_DISCOUNTING".equalsIgnoreCase(flow)) {
                    pbf = true;
                } else if ("SALES_BILL_DISCOUNTING".equalsIgnoreCase(flow)) {
                    sbd = true;
                } else if ("PURCHASE_ORDER_DISCOUNTING".equalsIgnoreCase(flow)) {
                    po = true;
                }
            }
            return new InvoiceDiscountingFlowFlags(true, pbf, sbd, po);
        } catch (PlpIntegrationException e) {
            log.warn("Could not resolve invoice flow enrollments for {}: {}", borrowerUserId, e.getMessage());
            return InvoiceDiscountingFlowFlags.linkedOnly();
        }
    }

    public record InvoiceDiscountingFlowFlags(
            boolean anyLinked, boolean purchaseBill, boolean salesBill, boolean purchaseOrder) {
        static InvoiceDiscountingFlowFlags none() {
            return new InvoiceDiscountingFlowFlags(false, false, false, false);
        }

        static InvoiceDiscountingFlowFlags linkedOnly() {
            return new InvoiceDiscountingFlowFlags(true, true, false, false);
        }
    }

    public BorrowerProgramsResponse listPrograms(UUID borrowerUserId) {
        losUserRepository.findById(borrowerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!isInvoiceDiscountingLinked(borrowerUserId)) {
            return BorrowerProgramsResponse.builder()
                    .linked(false)
                    .message("Programs are available once your invoice-discounting application is linked to an anchor program.")
                    .enrollments(List.of())
                    .build();
        }
        if (!plpBorrowerClient.isEnabled()) {
            return BorrowerProgramsResponse.builder()
                    .linked(false)
                    .message("Programs are not enabled in this environment.")
                    .enrollments(List.of())
                    .build();
        }

        Optional<UUID> plpBorrowerId = ownershipService.resolvePlpBorrowerId(borrowerUserId);
        if (plpBorrowerId.isEmpty()) {
            return BorrowerProgramsResponse.builder()
                    .linked(false)
                    .message("Your PLP borrower profile is not set up yet.")
                    .enrollments(List.of())
                    .build();
        }

        try {
            List<Map<String, Object>> subPrograms = plpBorrowerClient.listSubPrograms();
            Map<String, Map<String, Object>> programCache = new HashMap<>();
            List<BorrowerProgramEnrollmentResponse> enrollments = new ArrayList<>();

            for (Map<String, Object> sp : subPrograms) {
                UUID spId = asUuid(sp.get("id"));
                if (spId == null) {
                    continue;
                }
                Optional<Map<String, Object>> membershipOpt;
                try {
                    membershipOpt = plpBorrowerClient.getBorrowerLimitSummary(spId, plpBorrowerId.get());
                } catch (PlpIntegrationException e) {
                    continue;
                }
                if (membershipOpt.isEmpty()) {
                    continue;
                }
                Map<String, Object> membership = membershipOpt.get();
                UUID programId = asUuid(sp.get("programId"));
                Map<String, Object> program = null;
                if (programId != null) {
                    program = programCache.computeIfAbsent(programId.toString(), key -> {
                        try {
                            return plpBorrowerClient.getProgram(programId);
                        } catch (PlpIntegrationException e) {
                            log.warn("Could not load PLP program {} for borrower {}: {}", programId, borrowerUserId,
                                    e.getMessage());
                            return Map.<String, Object>of();
                        }
                    });
                }
                Map<String, Object> borrowerTerms = findBorrowerTerms(spId, plpBorrowerId.get());
                enrollments.add(toEnrollment(sp, program, membership, borrowerTerms));
            }

            return BorrowerProgramsResponse.builder()
                    .linked(true)
                    .message(null)
                    .enrollments(enrollments)
                    .build();
        } catch (PlpIntegrationException e) {
            log.warn("Borrower programs unavailable for {}: {}", borrowerUserId, e.getMessage());
            return BorrowerProgramsResponse.builder()
                    .linked(true)
                    .message("Could not load program details. Please try again shortly.")
                    .enrollments(List.of())
                    .build();
        }
    }

    private BorrowerProgramEnrollmentResponse toEnrollment(
            Map<String, Object> sp,
            Map<String, Object> program,
            Map<String, Object> membership,
            Map<String, Object> borrowerTerms) {
        boolean hasProgram = program != null && !program.isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = hasProgram && program.get("parameters") instanceof Map<?, ?> p
                ? (Map<String, Object>) p
                : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> config = hasProgram && program.get("config") instanceof Map<?, ?> c
                ? (Map<String, Object>) c
                : null;
        return BorrowerProgramEnrollmentResponse.builder()
                .subProgramId(asString(sp.get("id")))
                .subProgramName(asString(sp.get("name")))
                .subProgramCode(asString(sp.get("code")))
                .subProgramStatus(asString(sp.get("status")))
                .flowType(asString(sp.get("flowType")))
                .subProgramInterestRate(asBig(sp.get("interestRate")))
                .subProgramMarginPercent(asBig(sp.get("marginPercent")))
                .subProgramMaxTenureDays(asInt(sp.get("maxTenureDays")))
                .programId(hasProgram ? asString(program.get("id")) : null)
                .programName(hasProgram ? asString(program.get("programName")) : null)
                .programCode(hasProgram ? asString(program.get("programCode")) : null)
                .programStatus(hasProgram ? asString(program.get("status")) : null)
                .productType(hasProgram ? asString(program.get("productType")) : null)
                .programLimit(hasProgram ? asBig(program.get("programLimit")) : null)
                .programUtilizedLimit(hasProgram ? asBig(program.get("utilizedLimit")) : null)
                .programAvailableLimit(hasProgram ? asBig(program.get("availableLimit")) : null)
                .defaultInterestRate(hasProgram ? asBig(program.get("defaultInterestRate")) : null)
                .programMarginPercent(hasProgram ? asBig(program.get("marginPercent")) : null)
                .programMaxTenureDays(hasProgram ? asInt(program.get("maxTenureDays")) : null)
                .programParameters(parameters)
                .programConfig(config)
                .lmsEntryIn(hasProgram ? asString(program.get("lmsEntryIn")) : null)
                .encoreProductCode(hasProgram ? asString(program.get("encoreProductCode")) : null)
                .borrowerLimit(asBig(membership.get("borrowerLimit")))
                .borrowerUtilizedLimit(asBig(membership.get("utilizedLimit")))
                .borrowerAvailableLimit(asBig(membership.get("availableLimit")))
                .membershipStatus(asString(membership.get("status"), "ACTIVE"))
                .borrowerInterestRate(borrowerTerms != null ? asBig(borrowerTerms.get("interestRate")) : null)
                .borrowerDiscountMarginPercent(borrowerTerms != null ? asBig(borrowerTerms.get("discountMarginPercent")) : null)
                .borrowerCreditPeriodDays(borrowerTerms != null ? asInt(borrowerTerms.get("creditPeriodDays")) : null)
                .borrowerDiscountHold(borrowerTerms != null ? asString(borrowerTerms.get("discountHold")) : null)
                .borrowerPaymentMethod(borrowerTerms != null ? asString(borrowerTerms.get("paymentMethod")) : null)
                .borrowerOverdueInterestRate(borrowerTerms != null ? asBig(borrowerTerms.get("overdueInterestRate")) : null)
                .build();
    }

    private Map<String, Object> findBorrowerTerms(UUID subProgramId, UUID plpBorrowerId) {
        try {
            for (Map<String, Object> row : plpBorrowerClient.listSubProgramBorrowers(subProgramId)) {
                UUID bid = asUuid(row.get("borrowerId"));
                if (plpBorrowerId.equals(bid)) {
                    return row;
                }
            }
        } catch (PlpIntegrationException e) {
            log.debug("Could not load borrower terms for subProgram={} borrower={}: {}", subProgramId, plpBorrowerId,
                    e.getMessage());
        }
        return Map.of();
    }

    private static UUID asUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return asString(value, null);
    }

    private static String asString(Object value, String defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? defaultVal : s;
    }

    private static BigDecimal asBig(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
