package com.los.core.service.borrower;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ForbiddenException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.BorrowerApplicationDetailResponse;
import com.los.core.model.dto.response.BorrowerApplicationSummaryResponse;
import com.los.core.model.dto.response.BorrowerLabelValueItem;
import com.los.core.model.dto.response.BorrowerDashboardResponse;
import com.los.core.model.dto.response.BorrowerDocumentItemResponse;
import com.los.core.model.dto.response.BorrowerKfsSummaryResponse;
import com.los.core.model.dto.response.BorrowerNotificationItemResponse;
import com.los.core.model.dto.response.BorrowerRepaymentScheduleItemResponse;
import com.los.core.model.dto.response.BorrowerStatementLineResponse;
import com.los.core.model.dto.response.BorrowerTransactionItemResponse;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.LosUser;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.service.demo.DemoApplicationPurgeService;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.kfs.KfsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BorrowerPortalService {

    private static final String ROLE = "BORROWER";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LoanApplicationRepository applicationRepository;
    private final LosUserRepository losUserRepository;
    private final BorrowerApplicationStatusService statusService;
    private final BorrowerLifecycleTimelineService timelineService;
    private final IDocumentService documentService;
    private final KfsService kfsService;
    private final DemoApplicationPurgeService demoApplicationPurgeService;

    public void requireBorrower(String role) {
        if (role == null || !ROLE.equalsIgnoreCase(role.trim())) {
            throw new ForbiddenException("This area is only for borrowers. Sign in with a borrower account.");
        }
    }

    public BorrowerDashboardResponse dashboard(UUID borrowerUserId) {
        LosUser u = losUserRepository.findById(borrowerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Page<LoanApplication> page = applicationRepository.findByCustomerId(
                borrowerUserId, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt")));
        List<BorrowerApplicationSummaryResponse> recent = new ArrayList<>();
        int disbursed = 0;
        int open = 0;
        UUID firstDisbursed = null;
        for (LoanApplication a : page.getContent()) {
            recent.add(toSummary(a));
            if (a.getStatus() == ApplicationStatus.DISBURSED) {
                disbursed++;
                if (firstDisbursed == null) {
                    firstDisbursed = a.getId();
                }
            }
            if (isOpenApplication(a.getStatus())) {
                open++;
            }
        }
        String warn = disbursed > 0
                ? "You already have an active disbursed loan on file. A second loan may require additional review."
                : null;
        return BorrowerDashboardResponse.builder()
                .fullName(u.getName())
                .email(u.getEmail())
                .mobile(u.getMobile())
                .activeLoanCount(disbursed)
                .draftOrOpenApplicationCount(open)
                .hasIncompleteDraftHint(page.getContent().stream().anyMatch(x -> x.getStatus() == ApplicationStatus.DRAFT))
                .recentApplications(recent)
                .secondLoanWarning(warn)
                .primaryDisbursedApplicationId(firstDisbursed)
                .build();
    }

    private boolean isOpenApplication(ApplicationStatus s) {
        return s != ApplicationStatus.REJECTED
                && s != ApplicationStatus.WITHDRAWN
                && s != ApplicationStatus.DISBURSED;
    }

    private BorrowerApplicationSummaryResponse toSummary(LoanApplication a) {
        return BorrowerApplicationSummaryResponse.builder()
                .applicationId(a.getId())
                .applicationNumber(a.getApplicationNumber())
                .product(a.getLoanProduct())
                .status(a.getStatus())
                .friendlyStatus(BorrowerFriendlyLabels.applicationSummaryStatus(a.getStatus()))
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    public Page<BorrowerApplicationSummaryResponse> listApplications(UUID borrowerUserId, org.springframework.data.domain.Pageable p) {
        return applicationRepository.findByCustomerId(borrowerUserId, p).map(this::toSummary);
    }

    /**
     * Permanently removes a borrower's application that has not been submitted to KYC yet
     * ({@link ApplicationStatus#DRAFT} or {@link ApplicationStatus#CONSENT_PENDING}).
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUnsubmittedApplication(UUID borrowerUserId, UUID applicationId) {
        LoanApplication app = applicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (!borrowerUserId.equals(app.getCustomerId())) {
            throw new ForbiddenException("You can only delete your own applications.");
        }
        ApplicationStatus st = app.getStatus();
        if (st != ApplicationStatus.DRAFT && st != ApplicationStatus.CONSENT_PENDING) {
            throw new BusinessRuleException(
                    "This application can no longer be deleted. It has already been submitted to processing.");
        }
        demoApplicationPurgeService.deleteApplicationDependentsByIds(List.of(applicationId));
    }

    public BorrowerApplicationDetailResponse applicationDetail(
            UUID borrowerUserId, UUID applicationId, boolean includeTimeline) {
        LoanApplication app = loadOwned(borrowerUserId, applicationId);
        int docCount = documentService.getDocuments(applicationId).size();
        boolean checklist = documentService.isDocumentChecklistComplete(applicationId);
        var base = statusService.build(app, checklist, docCount);
        var timeline = includeTimeline ? timelineService.build(app) : List.<com.los.core.model.dto.response.BorrowerTimelineStepResponse>of();

        String rej = null;
        if (app.getStatus() == ApplicationStatus.REJECTED) {
            String r = app.getRemarks();
            rej = (r != null && !r.isBlank()) ? r.trim() : "We could not continue with this application on this occasion.";
        }
        String mask = bankMask(app);
        return BorrowerApplicationDetailResponse.builder()
                .applicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .customerName(base.getCustomerName())
                .product(app.getLoanProduct())
                .status(app.getStatus())
                .friendlyStatusHeadline(BorrowerFriendlyLabels.headline(app.getStatus()))
                .currentStageMessage(stageMessage(base.getCustomerName(), app.getStatus()))
                .estimatedProcessingHint("Estimated time for this stage: 1–2 business days where manual review applies.")
                .requiredActions(base.getRequiredActions())
                .kycStatus(base.getKycStatus())
                .documentStatus(base.getDocumentStatus())
                .sanctionStatus(base.getSanctionStatus())
                .kfsStatus(base.getKfsStatus())
                .eSignStatus(base.getESignStatus())
                .disbursementStatus(base.getDisbursementStatus())
                .rejectionMessage(rej)
                .reapplyVisible(app.getStatus() == ApplicationStatus.REJECTED
                        || app.getStatus() == ApplicationStatus.WITHDRAWN)
                .disbursedAmount(app.getDisbursedAmount())
                .disbursedAt(app.getDisbursedAt())
                .disbursementAccountMask(mask)
                .loanAccountNumber(app.getLmsReferenceId() != null ? app.getLmsReferenceId() : app.getApplicationNumber())
                .timeline(timeline)
                .collateralSummary(collateralSummaryForBorrower(app))
                .build();
    }

    private List<BorrowerLabelValueItem> collateralSummaryForBorrower(LoanApplication app) {
        Map<String, Object> c = app.getCollateralInfo();
        if (c == null) {
            return List.of();
        }
        Object raw = c.get("borrowerIntake");
        if (!(raw instanceof Map)) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) raw;
        List<BorrowerLabelValueItem> lines = new ArrayList<>();
        addBorrowerLine(lines, "Product", m.get("product"));
        addBorrowerLine(lines, "Type of security", friendlyCollateralType(m.get("collateralType")));
        addBorrowerLine(lines, "Estimated value (INR)", m.get("estimatedValue"));
        String dj = m.get("detailsJson") == null ? "" : m.get("detailsJson").toString();
        if (!dj.isBlank()) {
            try {
                Map<String, Object> d = MAPPER.readValue(dj, new TypeReference<Map<String, Object>>() { });
                for (Map.Entry<String, Object> e : d.entrySet()) {
                    if (e.getValue() == null) {
                        continue;
                    }
                    String s = e.getValue().toString();
                    if (s.isBlank()) {
                        continue;
                    }
                    addBorrowerLine(lines, labelForCollateralDetailKey(e.getKey()), s);
                }
            } catch (Exception ignored) {
                // leave summary without parsed details
            }
        }
        return lines;
    }

    private static void addBorrowerLine(List<BorrowerLabelValueItem> lines, String label, Object value) {
        if (value == null) {
            return;
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return;
        }
        lines.add(BorrowerLabelValueItem.builder().label(label).value(s).build());
    }

    private static String friendlyCollateralType(Object o) {
        if (o == null) {
            return "";
        }
        return switch (o.toString().toUpperCase()) {
            case "PROPERTY" -> "Property (e.g. LAP)";
            case "SHARES" -> "Securities (Loan Against Shares)";
            case "GOLD" -> "Gold / ornaments";
            default -> o.toString();
        };
    }

    private static String labelForCollateralDetailKey(String key) {
        return switch (key) {
            case "propertyType" -> "Property type";
            case "propertyAddress" -> "Property address";
            case "ownershipType" -> "Ownership type";
            case "existingMortgageOrEncumbrance" -> "Existing mortgage or encumbrance";
            case "securityType" -> "Security type";
            case "isin" -> "ISIN";
            case "companyOrMutualFundName" -> "Company / fund name";
            case "quantity" -> "Quantity";
            case "dematAccountNumber" -> "Demat account number";
            case "pledgeConsent" -> "Pledge consent";
            case "goldType" -> "Gold type";
            case "approxGrossWeight" -> "Approx. gross weight";
            case "approxNetWeight" -> "Approx. net weight";
            case "purityOrKarat" -> "Purity / karat";
            case "ornamentDescription" -> "Description of item(s)";
            default -> key.replaceAll("([A-Z])", " $1").trim();
        };
    }

    private String bankMask(LoanApplication app) {
        Object v = app.getPersonalInfo() != null ? app.getPersonalInfo().get("bankAccountLast4") : null;
        if (v != null) {
            String s = v.toString().replaceAll("\\D", "");
            if (s.length() >= 4) {
                return "****" + s.substring(s.length() - 4);
            }
        }
        return "****----";
    }

    private String stageMessage(String name, ApplicationStatus s) {
        return "Your application is currently in: " + BorrowerFriendlyLabels.headline(s) + ".";
    }

    public List<BorrowerDocumentItemResponse> listDocumentsSafe(UUID borrowerUserId, UUID applicationId) {
        loadOwned(borrowerUserId, applicationId);
        List<DocumentResponse> docs = documentService.getDocuments(applicationId);
        List<BorrowerDocumentItemResponse> out = new ArrayList<>();
        for (DocumentResponse d : docs) {
            out.add(BorrowerDocumentItemResponse.builder()
                    .id(d.getId())
                    .documentType(d.getDocumentType() != null ? d.getDocumentType() : "DOCUMENT")
                    .fileName(d.getFileName())
                    .contentType(d.getContentType())
                    .fileSize(d.getFileSize())
                    .createdAt(d.getCreatedAt())
                    .build());
        }
        return out;
    }

    public BorrowerKfsSummaryResponse kfsForBorrower(UUID borrowerUserId, UUID applicationId) {
        LoanApplication app = loadOwned(borrowerUserId, applicationId);
        KfsDocument k;
        try {
            k = kfsService.getLatestKfs(applicationId);
        } catch (RuntimeException e) {
            throw new ResourceNotFoundException("Key Fact Statement is not available yet for this application.");
        }
        return BorrowerKfsSummaryResponse.builder()
                .kfsId(k.getId().toString())
                .version(k.getVersion())
                .sanctionedAmount(k.getSanctionedAmount())
                .interestRate(k.getInterestRate())
                .apr(k.getApr())
                .tenureMonths(k.getTenureMonths())
                .emiAmount(k.getEmiAmount())
                .totalRepayment(k.getTotalRepayment())
                .status(k.getStatus())
                .signPending("ESIGN_PENDING".equalsIgnoreCase(k.getStatus()) || app.getStatus() == ApplicationStatus.ESIGN_PENDING)
                .coolingOffComplete(k.isCoolingOffCompleted())
                .build();
    }

    public List<BorrowerNotificationItemResponse> notifications(UUID borrowerUserId) {
        Page<LoanApplication> page = applicationRepository.findByCustomerId(
                borrowerUserId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt")));
        List<BorrowerNotificationItemResponse> out = new ArrayList<>();
        int n = 0;
        for (LoanApplication a : page.getContent()) {
            n++;
            if (a.getStatus() == ApplicationStatus.ESIGN_PENDING) {
                out.add(notif(n, "Signature needed", "Please complete KFS and agreement e-sign for " + a.getApplicationNumber() + ".", "KFS", a.getUpdatedAt(), a.getId()));
            } else if (a.getStatus() == ApplicationStatus.DRAFT) {
                out.add(notif(n, "Application in progress", "Finish and submit application " + a.getApplicationNumber() + ".", "DRAFT", a.getUpdatedAt(), a.getId()));
            } else if (a.getStatus() == ApplicationStatus.READY_FOR_DISBURSEMENT
                    || a.getStatus() == ApplicationStatus.DISBURSEMENT_PENDING) {
                out.add(notif(n, "Disbursement in progress", "We are moving funds for " + a.getApplicationNumber() + ".", "DISB", a.getUpdatedAt(), a.getId()));
            } else if (a.getStatus() == ApplicationStatus.DISBURSED) {
                out.add(notif(n, "Loan disbursed", "Your loan for " + a.getApplicationNumber() + " has been credited.", "DISBURSED", a.getDisbursedAt() != null ? a.getDisbursedAt() : a.getUpdatedAt(), a.getId()));
            } else if (a.getStatus() == ApplicationStatus.REJECTED) {
                out.add(notif(n, "Application update", "We could not proceed with " + a.getApplicationNumber() + ".", "REJECT", a.getUpdatedAt(), a.getId()));
            }
        }
        return out;
    }

    private BorrowerNotificationItemResponse notif(
            int seed, String title, String msg, String kind, Instant t, UUID appId) {
        return BorrowerNotificationItemResponse.builder()
                .id(UUID.nameUUIDFromBytes((title + appId + seed).getBytes()))
                .title(title)
                .message(msg)
                .kind(kind)
                .createdAt(t)
                .applicationId(appId)
                .build();
    }

    /** Demo repayment schedule: safe synthetic rows when the loan is disbursed. */
    public List<BorrowerRepaymentScheduleItemResponse> repaymentScheduleDemo(UUID borrowerUserId, UUID loanId) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Repayment schedule is available after disbursement.");
        }
        BigDecimal principal = app.getDisbursedAmount() != null ? app.getDisbursedAmount() : app.getSanctionedAmount();
        if (principal == null) {
            principal = app.getRequestedAmount() != null ? app.getRequestedAmount() : BigDecimal.valueOf(100_000);
        }
        int months = app.getTenureMonths() != null && app.getTenureMonths() > 0 ? app.getTenureMonths() : 12;
        BigDecimal emi = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal outstanding = principal;
        List<BorrowerRepaymentScheduleItemResponse> rows = new ArrayList<>();
        LocalDate d = LocalDate.ofInstant(Instant.now(), ZoneId.systemDefault()).plusMonths(1);
        for (int i = 1; i <= Math.min(months, 6); i++) {
            BigDecimal interest = emi.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal p = emi.subtract(interest);
            outstanding = outstanding.subtract(p).max(BigDecimal.ZERO);
            rows.add(BorrowerRepaymentScheduleItemResponse.builder()
                    .installmentNo(i)
                    .dueDate(d)
                    .emi(emi)
                    .principal(p.max(BigDecimal.ZERO))
                    .interest(interest)
                    .outstandingPrincipal(outstanding)
                    .build());
            d = d.plusMonths(1);
        }
        return rows;
    }

    public List<BorrowerStatementLineResponse> statementDemo(UUID borrowerUserId, UUID loanId) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Statement is available after disbursement.");
        }
        LocalDate today = LocalDate.now();
        return List.of(
                BorrowerStatementLineResponse.builder()
                        .valueDate(today.minusMonths(1))
                        .description("Opening balance")
                        .credit(null)
                        .debit(null)
                        .balance(principalFor(app))
                        .build(),
                BorrowerStatementLineResponse.builder()
                        .valueDate(today)
                        .description("EMI due (demo)")
                        .credit(null)
                        .debit(new BigDecimal("5000.00"))
                        .balance(principalFor(app).subtract(new BigDecimal("2000.00")))
                        .build()
        );
    }

    public List<BorrowerTransactionItemResponse> transactionsDemo(UUID borrowerUserId, UUID loanId) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Transactions are available after disbursement.");
        }
        return List.of(
                BorrowerTransactionItemResponse.builder()
                        .postedAt(app.getDisbursedAt() != null ? app.getDisbursedAt() : app.getUpdatedAt())
                        .description("Loan disbursal")
                        .reference("DISB-" + app.getApplicationNumber())
                        .amount(app.getDisbursedAmount() != null ? app.getDisbursedAmount() : app.getRequestedAmount())
                        .type("CREDIT")
                        .build()
        );
    }

    private BigDecimal principalFor(LoanApplication app) {
        if (app.getDisbursedAmount() != null) {
            return app.getDisbursedAmount();
        }
        if (app.getSanctionedAmount() != null) {
            return app.getSanctionedAmount();
        }
        return app.getRequestedAmount() != null ? app.getRequestedAmount() : BigDecimal.valueOf(100_000);
    }

    private LoanApplication loadOwned(UUID borrowerUserId, UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (!borrowerUserId.equals(app.getCustomerId())) {
            throw new ForbiddenException("You do not have access to this application.");
        }
        return app;
    }
}
