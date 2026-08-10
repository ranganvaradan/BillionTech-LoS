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
import com.los.core.model.dto.response.BorrowerLoanAccountResponse;
import com.los.core.model.dto.response.BorrowerNotificationItemResponse;
import com.los.core.model.dto.response.BorrowerRepaymentScheduleItemResponse;
import com.los.core.model.dto.response.BorrowerServicingDataResponse;
import com.los.core.model.dto.response.BorrowerStatementLineResponse;
import com.los.core.model.dto.response.BorrowerTransactionItemResponse;
import com.los.lms.dto.LoanAccountSummary;
import com.los.lms.dto.RepaymentCallbackRequest;
import com.los.lms.dto.RepaymentScheduleEntry;
import com.los.lms.dto.RepaymentScheduleResponse;
import com.los.lms.service.LmsService;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.model.entity.LosUser;
import com.los.core.model.entity.schema.los2.EsignRequest;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.repository.schema.los2.EsignRequestRepository;
import com.los.core.service.demo.DemoApplicationPurgeService;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.esign.EsignRequestStatuses;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.loan.InvoiceDiscountingLosLoanGuard;
import com.los.core.service.repayment.LoanProductRepaymentDefaultService;
import com.los.core.payment.service.LosLoanPayuPaymentService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final DocumentRepository documentRepository;
    private final EsignRequestRepository esignRequestRepository;
    private final KfsService kfsService;
    private final DemoApplicationPurgeService demoApplicationPurgeService;
    private final LmsService lmsService;
    private final BorrowerProgramsService borrowerProgramsService;
    private final BorrowerApplicationOwnershipService ownershipService;
    private final InvoiceDiscountingLosLoanGuard invoiceDiscountingLosLoanGuard;
    private final SanctionRecordRepository sanctionRecordRepository;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final LoanProductRepaymentDefaultService loanProductRepaymentDefaultService;
    private final LosLoanPayuPaymentService losLoanPayuPaymentService;

    public void requireBorrower(String role) {
        if (role == null || !ROLE.equalsIgnoreCase(role.trim())) {
            throw new ForbiddenException("This area is only for borrowers. Sign in with a borrower account.");
        }
    }

    public BorrowerDashboardResponse dashboard(UUID borrowerUserId) {
        ownershipService.reconcileCustomerId(borrowerUserId);
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
            if (a.getStatus() == ApplicationStatus.DISBURSED
                    && !InvoiceDiscountingApplicationRules.isBorrowerFlow(a)) {
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
        BorrowerProgramsService.InvoiceDiscountingFlowFlags flowFlags =
                borrowerProgramsService.resolveInvoiceDiscountingFlowFlags(borrowerUserId);
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
                .invoiceDiscountingLinked(flowFlags.anyLinked())
                .purchaseBillDiscountingLinked(flowFlags.purchaseBill())
                .salesBillDiscountingLinked(flowFlags.salesBill())
                .purchaseOrderDiscountingLinked(flowFlags.purchaseOrder())
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
        if (!ownershipService.ownsApplication(borrowerUserId, app)) {
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
        boolean invoiceDiscountingBorrower = invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app);
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
        BigDecimal sanctionedAmount = app.getSanctionedAmount();
        BigDecimal interestRate = app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        Integer tenureMonths = app.getTenureMonths();
        boolean termsDocumentAvailable = false;
        if (invoiceDiscountingBorrower) {
            Optional<SanctionRecord> sanction = sanctionRecordRepository
                    .findTopByApplicationIdOrderByCreatedAtDesc(applicationId);
            if (sanction.isPresent()) {
                SanctionRecord rec = sanction.get();
                if (rec.getApprovedAmount() != null) {
                    sanctionedAmount = rec.getApprovedAmount();
                }
                if (rec.getInterestRate() != null) {
                    interestRate = rec.getInterestRate();
                }
                if (rec.getApprovedTenure() != null) {
                    tenureMonths = rec.getApprovedTenure();
                }
                termsDocumentAvailable = true;
            } else if (sanctionedAmount != null && app.getStatus() != ApplicationStatus.DRAFT) {
                termsDocumentAvailable = app.getStatus() == ApplicationStatus.SANCTIONED
                        || app.getStatus() == ApplicationStatus.KFS_GENERATED
                        || app.getStatus() == ApplicationStatus.ESIGN_PENDING
                        || app.getStatus() == ApplicationStatus.ESIGN_COMPLETED;
            }
        }
        return BorrowerApplicationDetailResponse.builder()
                .applicationId(app.getId())
                .applicationNumber(app.getApplicationNumber())
                .customerName(base.getCustomerName())
                .product(app.getLoanProduct())
                .status(app.getStatus())
                .friendlyStatusHeadline(BorrowerFriendlyLabels.headline(app.getStatus(), invoiceDiscountingBorrower))
                .currentStageMessage(stageMessage(app.getStatus(), invoiceDiscountingBorrower))
                .estimatedProcessingHint(invoiceDiscountingBorrower
                        ? "Invoice discounting onboarding — use Programs and Invoice discounting after eSign is complete."
                        : "Estimated time for this stage: 1–2 business days where manual review applies.")
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
                .invoiceDiscountingBorrower(invoiceDiscountingBorrower)
                .sanctionedAmount(sanctionedAmount)
                .interestRate(interestRate)
                .tenureMonths(tenureMonths)
                .termsDocumentAvailable(termsDocumentAvailable)
                .build();
    }

    public byte[] invoiceDiscountingTermsPdf(UUID borrowerUserId, UUID applicationId) {
        LoanApplication app = loadOwned(borrowerUserId, applicationId);
        if (!invoiceDiscountingLosLoanGuard.skipsLosTermLoanCreation(app)) {
            throw new ForbiddenException("Sanction terms download is only for invoice discounting borrower onboarding.");
        }
        return kfsPdfGenerationService.generateInvoiceDiscountingTermsPdfForApplication(applicationId);
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

    private String stageMessage(ApplicationStatus status, boolean invoiceDiscountingBorrower) {
        return "Your application is currently in: "
                + BorrowerFriendlyLabels.headline(status, invoiceDiscountingBorrower) + ".";
    }

    public List<BorrowerDocumentItemResponse> listDocumentsSafe(UUID borrowerUserId, UUID applicationId) {
        loadOwned(borrowerUserId, applicationId);
        List<BorrowerDocumentItemResponse> out = new ArrayList<>();
        for (DocumentResponse d : documentService.getDocuments(applicationId)) {
            if (!BorrowerDocumentVisibility.isVisibleUploadType(d.getDocumentType())) {
                continue;
            }
            out.add(BorrowerDocumentItemResponse.builder()
                    .id(d.getId())
                    .source("UPLOAD")
                    .category(BorrowerDocumentVisibility.categoryForUploadType(d.getDocumentType()))
                    .documentType(d.getDocumentType() != null ? d.getDocumentType() : "DOCUMENT")
                    .fileName(d.getFileName())
                    .contentType(d.getContentType())
                    .fileSize(d.getFileSize())
                    .createdAt(d.getCreatedAt())
                    .build());
        }
        for (EsignRequest e : esignRequestRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
            if (!EsignRequestStatuses.SIGNED.equalsIgnoreCase(e.getStatus())) {
                continue;
            }
            if (e.getSignedDocumentUrl() == null || e.getSignedDocumentUrl().isBlank()) {
                continue;
            }
            Instant when = e.getSignedAt() != null ? e.getSignedAt() : e.getCreatedAt();
            out.add(BorrowerDocumentItemResponse.builder()
                    .id(e.getId())
                    .source("ESIGN")
                    .category("SIGNED")
                    .documentType(e.getDocumentType() != null ? e.getDocumentType() : "SIGNED_DOCUMENT")
                    .fileName(esignFileName(e))
                    .contentType("application/pdf")
                    .fileSize(signedPdfSize(e.getSignedDocumentUrl()))
                    .createdAt(when)
                    .build());
        }
        out.sort(Comparator.comparing(BorrowerDocumentItemResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public BorrowerDocumentContent previewUploadDocument(UUID borrowerUserId, UUID applicationId, UUID documentId) {
        loadOwned(borrowerUserId, applicationId);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        if (!applicationId.equals(doc.getApplicationId())) {
            throw new ForbiddenException("Document does not belong to this application.");
        }
        if (!BorrowerDocumentVisibility.isVisibleUploadType(doc.getDocumentType())) {
            throw new ForbiddenException("This document is not available on the borrower portal.");
        }
        byte[] data = documentService.downloadDocument(documentId);
        return new BorrowerDocumentContent(data, resolveContentType(doc.getContentType(), doc.getFileName()), doc.getFileName());
    }

    public BorrowerDocumentContent previewEsignDocument(UUID borrowerUserId, UUID applicationId, UUID esignRequestId) {
        loadOwned(borrowerUserId, applicationId);
        EsignRequest req = esignRequestRepository.findById(esignRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Signed document not found: " + esignRequestId));
        if (!applicationId.equals(req.getApplicationId())) {
            throw new ForbiddenException("Signed document does not belong to this application.");
        }
        if (!EsignRequestStatuses.SIGNED.equalsIgnoreCase(req.getStatus())) {
            throw new ResourceNotFoundException("Signed document is not available yet.");
        }
        byte[] data = readSignedPdfBytes(req.getSignedDocumentUrl());
        return new BorrowerDocumentContent(data, "application/pdf", esignFileName(req));
    }

    private static String esignFileName(EsignRequest e) {
        String dt = e.getDocumentType();
        if (dt == null || dt.isBlank()) {
            return "signed-document.pdf";
        }
        return "signed-" + dt.toLowerCase(Locale.ROOT).replace('_', '-') + ".pdf";
    }

    private static long signedPdfSize(String signedDocumentUrl) {
        try {
            Path p = Path.of(signedDocumentUrl.trim());
            if (Files.isRegularFile(p)) {
                return Files.size(p);
            }
        } catch (Exception ignored) {
            // size unknown
        }
        return 0L;
    }

    private static byte[] readSignedPdfBytes(String signedDocumentUrl) {
        if (signedDocumentUrl == null || signedDocumentUrl.isBlank()) {
            throw new ResourceNotFoundException("Signed document file is not available.");
        }
        try {
            Path p = Path.of(signedDocumentUrl.trim());
            if (!Files.isRegularFile(p)) {
                throw new ResourceNotFoundException("Signed document file is not available.");
            }
            return Files.readAllBytes(p);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResourceNotFoundException("Could not read signed document.");
        }
    }

    private static String resolveContentType(String stored, String fileName) {
        if (stored != null && !stored.isBlank() && !"application/octet-stream".equalsIgnoreCase(stored.trim())) {
            return stored.trim();
        }
        if (fileName != null) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf")) {
                return "application/pdf";
            }
            if (lower.endsWith(".png")) {
                return "image/png";
            }
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return "image/jpeg";
            }
        }
        return "application/octet-stream";
    }

    public record BorrowerDocumentContent(byte[] bytes, String contentType, String fileName) {}

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

    // ===================== Post-disbursement loan servicing (LMS-backed) =====================
    // These read from the LMS (Encore via LmsService) when available and fall back to the safe
    // synthetic demo data so the borrower portal keeps working in environments without Encore.

    /** Loan account summary for a disbursed loan (LMS account summary with local fallback). */
    public BorrowerLoanAccountResponse loanAccount(UUID borrowerUserId, UUID loanId) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Loan account is available after disbursement.");
        }
        LoanAccountSummary s = lmsService.getAccountSummary(app.getApplicationNumber());
        String acct = s.getLmsReferenceId() != null ? s.getLmsReferenceId()
                : (app.getLmsReferenceId() != null ? app.getLmsReferenceId() : app.getApplicationNumber());
        String loanProduct = app.getLoanProduct();
        String repaymentMechanism = loanProductRepaymentDefaultService.resolveMechanism(loanProduct);
        boolean payuCheckoutAvailable = LosLoanPayuPaymentService.PAYMENT_METHOD_PAYU.equals(repaymentMechanism)
                && losLoanPayuPaymentService.isPayuConfigured()
                && !InvoiceDiscountingApplicationRules.isInvoiceDiscounting(app);
        return BorrowerLoanAccountResponse.builder()
                .loanAccountNumber(acct)
                .loanStatus(s.getLoanStatus() != null ? s.getLoanStatus() : "ACTIVE")
                .sanctionedAmount(s.getSanctionedAmount() != null ? s.getSanctionedAmount() : app.getSanctionedAmount())
                .disbursedAmount(s.getDisbursedAmount() != null ? s.getDisbursedAmount() : app.getDisbursedAmount())
                .outstandingPrincipal(s.getOutstandingPrincipal())
                .totalPaid(s.getTotalPaid())
                .overdueAmount(s.getOverdueAmount())
                .totalEmis(s.getTotalEmis())
                .paidEmis(s.getPaidEmis())
                .overdueEmis(s.getOverdueEmis())
                .nextEmiDate(s.getNextEmiDate())
                .nextEmiAmount(s.getNextEmiAmount())
                .lastPaymentDate(s.getLastPaymentDate())
                .dpd(s.getDpd())
                .servicingActive(true)
                .loanProduct(loanProduct)
                .repaymentMechanism(repaymentMechanism)
                .payuCheckoutAvailable(payuCheckoutAvailable)
                .build();
    }

    /** Repayment schedule for a disbursed loan (LMS schedule; indicative amortization fallback). */
    public BorrowerServicingDataResponse<BorrowerRepaymentScheduleItemResponse> repaymentSchedule(
            UUID borrowerUserId, UUID loanId) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Repayment schedule is available after disbursement.");
        }
        try {
            RepaymentScheduleResponse resp = lmsService.getRepaymentSchedule(app.getApplicationNumber());
            if (resp != null && resp.getSchedule() != null && !resp.getSchedule().isEmpty()) {
                List<BorrowerRepaymentScheduleItemResponse> rows = new ArrayList<>();
                for (RepaymentScheduleEntry e : resp.getSchedule()) {
                    rows.add(BorrowerRepaymentScheduleItemResponse.builder()
                            .installmentNo(e.getInstallmentNumber())
                            .dueDate(e.getDueDate())
                            .emi(e.getEmiAmount())
                            .principal(e.getPrincipalComponent())
                            .interest(e.getInterestComponent())
                            .outstandingPrincipal(e.getOutstandingPrincipal())
                            .build());
                }
                if (isEncoreSchedule(resp)) {
                    return lmsData(rows);
                }
                return BorrowerServicingDataResponse.<BorrowerRepaymentScheduleItemResponse>builder()
                        .source("INDICATIVE")
                        .rows(rows)
                        .build();
            }
        } catch (RuntimeException ex) {
            // fall through to indicative demo
        }
        return localData(repaymentScheduleDemo(borrowerUserId, loanId));
    }

    /**
     * Account statement for a disbursed loan. Prefers the Encore LMS ledger; otherwise builds an honest
     * statement from the actual disbursal credit plus recorded repayment debits (no fabricated EMI rows).
     */
    public BorrowerServicingDataResponse<BorrowerStatementLineResponse> statement(UUID borrowerUserId, UUID loanId) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Statement is available after disbursement.");
        }
        try {
            List<Map<String, Object>> entries =
                    lmsService.getEncoreAccountStatement(app.getApplicationNumber(), null, null);
            if (entries != null && !entries.isEmpty()) {
                List<BorrowerStatementLineResponse> lines = mapEncoreStatementEntries(entries);
                if (!lines.isEmpty()) {
                    return lmsData(lines);
                }
            }
        } catch (RuntimeException ex) {
            // fall through to a real, locally-derived statement
        }
        return localData(statementFromActuals(app));
    }

    /** Transactions for a disbursed loan (real disbursal + recorded LMS repayments). */
    public BorrowerServicingDataResponse<BorrowerTransactionItemResponse> transactions(
            UUID borrowerUserId, UUID loanId) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Transactions are available after disbursement.");
        }
        List<BorrowerTransactionItemResponse> out = new ArrayList<>();
        out.add(BorrowerTransactionItemResponse.builder()
                .postedAt(app.getDisbursedAt() != null ? app.getDisbursedAt() : app.getUpdatedAt())
                .description("Loan disbursal")
                .reference("DISB-" + app.getApplicationNumber())
                .amount(app.getDisbursedAmount() != null ? app.getDisbursedAmount() : app.getRequestedAmount())
                .type("CREDIT")
                .build());
        boolean fromLms = false;
        try {
            List<RepaymentCallbackRequest> history = lmsService.getPaymentHistory(app.getApplicationNumber());
            if (history != null && !history.isEmpty()) {
                fromLms = true;
                for (RepaymentCallbackRequest r : history) {
                    out.add(BorrowerTransactionItemResponse.builder()
                            .postedAt(r.getPaymentDate() != null
                                    ? r.getPaymentDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                                    : app.getUpdatedAt())
                            .description("Repayment" + (r.getInstallmentNumber() > 0 ? " (installment " + r.getInstallmentNumber() + ")" : ""))
                            .reference(r.getUtrNumber() != null ? r.getUtrNumber() : "REPAY")
                            .amount(r.getPaidAmount())
                            .type("DEBIT")
                            .build());
                }
            }
        } catch (RuntimeException ex) {
            // disbursal-only is still real data
        }
        return BorrowerServicingDataResponse.<BorrowerTransactionItemResponse>builder()
                .source(fromLms ? "LMS" : "LOCAL")
                .rows(out)
                .build();
    }

    /** True when the schedule entries originated from Encore (tagged FROM_ENCORE by {@code LmsService}). */
    private static boolean isEncoreSchedule(RepaymentScheduleResponse resp) {
        return resp.getSchedule().stream()
                .anyMatch(e -> "FROM_ENCORE".equalsIgnoreCase(e.getStatus()));
    }

    /** Real statement built from the actual disbursal credit and recorded repayment debits. */
    private List<BorrowerStatementLineResponse> statementFromActuals(LoanApplication app) {
        BigDecimal disbursed = app.getDisbursedAmount() != null ? app.getDisbursedAmount() : principalFor(app);
        List<BorrowerStatementLineResponse> lines = new ArrayList<>();
        BigDecimal balance = disbursed;
        lines.add(BorrowerStatementLineResponse.builder()
                .valueDate(app.getDisbursedAt() != null
                        ? LocalDate.ofInstant(app.getDisbursedAt(), ZoneId.systemDefault())
                        : LocalDate.now())
                .description("Loan disbursal")
                .credit(disbursed)
                .debit(null)
                .balance(balance)
                .build());
        try {
            List<RepaymentCallbackRequest> history = lmsService.getPaymentHistory(app.getApplicationNumber());
            if (history != null) {
                List<RepaymentCallbackRequest> ordered = new ArrayList<>(history);
                ordered.sort((a, b) -> {
                    LocalDate da = a.getPaymentDate();
                    LocalDate db = b.getPaymentDate();
                    if (da == null && db == null) return 0;
                    if (da == null) return -1;
                    if (db == null) return 1;
                    return da.compareTo(db);
                });
                for (RepaymentCallbackRequest r : ordered) {
                    BigDecimal paid = r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO;
                    balance = balance.subtract(paid).max(BigDecimal.ZERO);
                    lines.add(BorrowerStatementLineResponse.builder()
                            .valueDate(r.getPaymentDate() != null ? r.getPaymentDate() : LocalDate.now())
                            .description("Repayment received")
                            .credit(null)
                            .debit(paid)
                            .balance(balance)
                            .build());
                }
            }
        } catch (RuntimeException ignored) {
            // disbursal line alone is still accurate
        }
        return lines;
    }

    private static <T> BorrowerServicingDataResponse<T> lmsData(List<T> rows) {
        return BorrowerServicingDataResponse.<T>builder().source("LMS").rows(rows).build();
    }

    private static <T> BorrowerServicingDataResponse<T> localData(List<T> rows) {
        return BorrowerServicingDataResponse.<T>builder().source("LOCAL").rows(rows).build();
    }

    /** Borrower-initiated repayment; posts to the LMS and returns the refreshed loan account. */
    public BorrowerLoanAccountResponse makeRepayment(UUID borrowerUserId, UUID loanId, BigDecimal amount) {
        LoanApplication app = loadOwned(borrowerUserId, loanId);
        if (app.getStatus() != ApplicationStatus.DISBURSED) {
            throw new ForbiddenException("Repayments are available after disbursement.");
        }
        String mechanism = loanProductRepaymentDefaultService.resolveMechanism(app.getLoanProduct());
        if (LosLoanPayuPaymentService.PAYMENT_METHOD_PAYU.equals(mechanism)
                && !InvoiceDiscountingApplicationRules.isInvoiceDiscounting(app)) {
            throw new BusinessRuleException("This loan uses PayU — use Pay via PayU on the loan account page.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Enter a valid repayment amount.");
        }
        lmsService.recordBorrowerRepayment(app.getApplicationNumber(), amount);
        return loanAccount(borrowerUserId, loanId);
    }

    private static BigDecimal parseBig(Object o) {
        if (o == null) {
            return null;
        }
        try {
            String s = String.valueOf(o).trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Maps Encore {@code compositeStatement} rows (or legacy flat statement entries) to borrower SOA lines. */
    @SuppressWarnings("unchecked")
    private static List<BorrowerStatementLineResponse> mapEncoreStatementEntries(List<Map<String, Object>> entries) {
        List<BorrowerStatementLineResponse> lines = new ArrayList<>();
        for (Map<String, Object> row : entries) {
            Map<String, Object> dto = row;
            if (row.get("accountEntryDto") instanceof Map<?, ?> nested) {
                dto = (Map<String, Object>) nested;
            }
            BigDecimal amount = extractEncoreMoney(dto.get("amount"));
            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            String type = String.valueOf(dto.getOrDefault("accountEntryType", dto.getOrDefault("type", "")));
            boolean credit = type.toUpperCase(Locale.ROOT).contains("CREDIT");
            String description = textOrNull(dto.get("description"));
            if (description == null || description.isBlank()) {
                description = textOrNull(dto.get("transactionName"));
            }
            if (description == null || description.isBlank()) {
                description = "Transaction";
            }
            Object balanceRaw = row.containsKey("balance") ? row.get("balance") : dto.get("balance");
            lines.add(BorrowerStatementLineResponse.builder()
                    .valueDate(parseDate(dto.get("valueDate") != null ? dto.get("valueDate") : dto.get("valueDateStr")))
                    .description(description)
                    .credit(credit ? amount : null)
                    .debit(credit ? null : amount)
                    .balance(extractEncoreMoney(balanceRaw))
                    .build());
        }
        return lines;
    }

    private static BigDecimal extractEncoreMoney(Object raw) {
        if (raw instanceof Map<?, ?> money) {
            BigDecimal mag = parseBig(money.get("magnitude"));
            if (mag != null) {
                return mag;
            }
            return parseBig(money.get("displayValue"));
        }
        return parseBig(raw);
    }

    private static String textOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return "null".equalsIgnoreCase(s) ? null : s;
    }

    private static LocalDate parseDate(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            if (s.length() >= 10 && s.charAt(4) == '-') {
                return LocalDate.parse(s.substring(0, 10));
            }
        } catch (RuntimeException ignored) {
            // ignore
        }
        return null;
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
        if (!ownershipService.ownsApplication(borrowerUserId, app)) {
            throw new ForbiddenException("You do not have access to this application.");
        }
        return app;
    }
}
