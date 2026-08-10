package com.los.core.service.kfs;

import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.KfsTemplate;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.KfsTemplateRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.kfs.edi.EdiKfsComputedScheduleBuilder;
import com.los.lms.service.LmsApplicationConfigResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * KFS (Key Fact Statement) Service — RBI Digital Lending Directions compliance.
 * Handles KFS generation, acknowledgement, cooling-off period enforcement, and template management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KfsService {

    private final KfsDocumentRepository kfsDocumentRepository;
    private final KfsTemplateRepository kfsTemplateRepository;
    private final LoanApplicationRepository applicationRepository;
    private final AuditService auditService;
    private final LmsApplicationConfigResolver lmsApplicationConfigResolver;

    private static final int DEFAULT_COOLING_OFF_HOURS = 72;
    private static final String GRIEVANCE_DEFAULT = "Grievance Redressal Officer: complaints@lender.com | Toll-free: 1800-XXX-XXXX | RBI Ombudsman: https://cms.rbi.org.in";
    private static final String LSP_DEFAULT = "This loan is originated by the Regulated Entity directly. No third-party LSP involved.";
    private static final String RBI_CIRCULAR = "RBI/2022-23/111 DOR.FIN.REC.66/03.10.038/2022-23";

    /**
     * Generate KFS for a loan application after sanction.
     */
    @Transactional
    public KfsDocument generateKfs(UUID applicationId, Map<String, Object> charges) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        BigDecimal principal = app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
        BigDecimal annualRate = app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        Integer tenureMag = app.getTenureMonths();

        if (principal == null || annualRate == null || tenureMag == null) {
            throw new RuntimeException("Loan terms (amount, rate, tenure) must be set before KFS generation");
        }

        int tenure = tenureMag;
        String lmsTenureUnit = lmsApplicationConfigResolver.resolveTenureUnit(app);
        boolean edi = "day".equalsIgnoreCase(lmsTenureUnit);

        BigDecimal emi;
        BigDecimal totalRepayment;
        BigDecimal totalInterest;
        if (edi) {
            emi = EdiKfsComputedScheduleBuilder.calculateDailyInstallment(principal, annualRate, tenure);
            totalRepayment = emi.multiply(BigDecimal.valueOf(tenure));
            totalInterest = totalRepayment.subtract(principal);
        } else {
            emi = calculateEmi(principal, annualRate, tenure);
            totalRepayment = emi.multiply(BigDecimal.valueOf(tenure));
            totalInterest = totalRepayment.subtract(principal);
        }

        BigDecimal processingFee = extractCharge(charges, "processingFee", principal.multiply(new BigDecimal("0.02")));
        BigDecimal stampDuty = extractCharge(charges, "stampDuty", BigDecimal.ZERO);
        BigDecimal insurancePremium = extractCharge(charges, "insurancePremium", BigDecimal.ZERO);
        BigDecimal otherCharges = extractCharge(charges, "otherCharges", BigDecimal.ZERO);

        BigDecimal totalCost = totalInterest.add(processingFee).add(stampDuty).add(insurancePremium).add(otherCharges);

        BigDecimal apr = calculateApr(principal, emi, tenure, processingFee.add(stampDuty).add(insurancePremium).add(otherCharges));

        // Prefer Encore LMS pre-open summary when present (sanction flow stores it in charges).
        Optional<EncorePreOpenKfsMapper.Figures> encore = EncorePreOpenKfsMapper.fromCharges(charges, principal);
        if (encore.isPresent()) {
            EncorePreOpenKfsMapper.Figures f = encore.get();
            if (f.installmentAmount() != null && f.installmentAmount().compareTo(BigDecimal.ZERO) > 0) {
                emi = f.installmentAmount();
            }
            if (f.totalRepayment() != null && f.totalRepayment().compareTo(BigDecimal.ZERO) > 0) {
                totalRepayment = f.totalRepayment();
                totalInterest = f.totalInterest() != null ? f.totalInterest() : totalRepayment.subtract(principal);
                totalCost = totalInterest.add(processingFee).add(stampDuty).add(insurancePremium).add(otherCharges);
            }
            if (f.apr() != null && f.apr().compareTo(BigDecimal.ZERO) > 0) {
                apr = f.apr();
            }
            if (f.processingFee() != null && f.processingFee().compareTo(BigDecimal.ZERO) >= 0) {
                processingFee = f.processingFee();
                totalCost = totalInterest.add(processingFee).add(stampDuty).add(insurancePremium).add(otherCharges);
            }
            if (f.installmentCount() > 0) {
                tenure = f.installmentCount();
            }
            if (charges != null) {
                charges.put("kfsSource", "ENCORE_PRE_OPEN");
            }
        }

        if (charges == null) {
            charges = new LinkedHashMap<>();
        }
        charges.put("lmsTenureUnit", lmsTenureUnit);
        charges.put("installmentLabel", installmentLabelFor(lmsTenureUnit));

        if (edi && !hasStoredRepaymentSchedule(charges, encore)) {
            List<Map<String, Object>> computed = EdiKfsComputedScheduleBuilder.buildRawSchedule(
                    principal, annualRate, tenure, emi, LocalDate.now().plusDays(1));
            if (!computed.isEmpty()) {
                charges.put("encoreRepaymentScheduleJson", computed);
                charges.put("kfsScheduleSource", "COMPUTED_DAILY");
                log.info("EDI KFS: stored {}-row computed repayment schedule for application {}",
                        computed.size(), applicationId);
            }
        }

        KfsTemplate template = kfsTemplateRepository
                .findFirstByLoanProductAndActiveTrueOrderByCreatedAtDesc(app.getLoanProduct())
                .orElse(null);

        String grievance = template != null && template.getGrievanceOfficerDetails() != null
                ? template.getGrievanceOfficerDetails() : GRIEVANCE_DEFAULT;
        String lsp = template != null && template.getLspDetails() != null
                ? template.getLspDetails() : LSP_DEFAULT;

        List<KfsDocument> existing = kfsDocumentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        String version = "v" + (existing.size() + 1);

        KfsDocument kfs = KfsDocument.builder()
                .applicationId(applicationId)
                .version(version)
                .sanctionedAmount(principal)
                .interestRate(annualRate)
                .apr(apr)
                .tenureMonths(tenure)
                .emiAmount(emi)
                .totalInterest(totalInterest)
                .totalRepayment(totalRepayment)
                .processingFee(processingFee)
                .stampDuty(stampDuty)
                .insurancePremium(insurancePremium)
                .otherCharges(otherCharges)
                .totalCostOfCredit(totalCost)
                .coolingOffHours(DEFAULT_COOLING_OFF_HOURS)
                .grievanceMechanism(grievance)
                .lspDisclosure(lsp)
                .additionalTerms(charges)
                .status("GENERATED")
                .build();

        kfs = kfsDocumentRepository.save(kfs);

        auditService.logEvent(applicationId, "KFS_GENERATED",
                Map.of("kfsId", kfs.getId().toString(), "version", version, "apr", apr.toString()));

        log.info("KFS generated for application {} — version={}, APR={}%", applicationId, version, apr);
        return kfs;
    }

    /**
     * Overwrites KFS financial figures from Encore {@code findPreOpenSummary} / {@code findSummaries} JSON
     * stored under {@code encorePreOpenSummaryJson} in {@code charges}.
     */
    @Transactional
    public void applyEncorePreOpenSummary(UUID kfsId, Map<String, Object> charges) {
        if (kfsId == null || charges == null || charges.isEmpty()) {
            return;
        }
        KfsDocument kfs = kfsDocumentRepository.findById(kfsId)
                .orElseThrow(() -> new ResourceNotFoundException("KFS not found: " + kfsId));
        BigDecimal principal = kfs.getSanctionedAmount();
        if (principal == null) {
            return;
        }

        Optional<EncorePreOpenKfsMapper.Figures> encore = EncorePreOpenKfsMapper.fromCharges(charges, principal);
        if (encore.isEmpty()) {
            log.warn("Encore pre-open summary present but could not map KFS figures for {}", kfsId);
            return;
        }

        EncorePreOpenKfsMapper.Figures f = encore.get();
        BigDecimal processingFee = kfs.getProcessingFee() != null ? kfs.getProcessingFee() : BigDecimal.ZERO;
        BigDecimal stampDuty = kfs.getStampDuty() != null ? kfs.getStampDuty() : BigDecimal.ZERO;
        BigDecimal insurancePremium = kfs.getInsurancePremium() != null ? kfs.getInsurancePremium() : BigDecimal.ZERO;
        BigDecimal otherCharges = kfs.getOtherCharges() != null ? kfs.getOtherCharges() : BigDecimal.ZERO;

        if (f.installmentAmount() != null && f.installmentAmount().compareTo(BigDecimal.ZERO) > 0) {
            kfs.setEmiAmount(f.installmentAmount());
        }
        if (f.totalRepayment() != null && f.totalRepayment().compareTo(BigDecimal.ZERO) > 0) {
            kfs.setTotalRepayment(f.totalRepayment());
            kfs.setTotalInterest(f.totalInterest() != null
                    ? f.totalInterest()
                    : f.totalRepayment().subtract(principal));
        }
        if (f.apr() != null && f.apr().compareTo(BigDecimal.ZERO) > 0) {
            kfs.setApr(f.apr());
        }
        if (f.processingFee() != null && f.processingFee().compareTo(BigDecimal.ZERO) >= 0) {
            processingFee = f.processingFee();
            kfs.setProcessingFee(processingFee);
        }
        if (f.installmentCount() > 0) {
            kfs.setTenureMonths(f.installmentCount());
        }

        BigDecimal totalInterest = kfs.getTotalInterest() != null ? kfs.getTotalInterest() : BigDecimal.ZERO;
        kfs.setTotalCostOfCredit(totalInterest.add(processingFee).add(stampDuty).add(insurancePremium).add(otherCharges));

        Map<String, Object> terms = kfs.getAdditionalTerms() != null
                ? new LinkedHashMap<>(kfs.getAdditionalTerms())
                : new LinkedHashMap<>();
        terms.putAll(charges);
        if (!terms.containsKey("encoreRepaymentScheduleJson")
                && charges.containsKey("encoreRepaymentScheduleJson")) {
            terms.put("encoreRepaymentScheduleJson", charges.get("encoreRepaymentScheduleJson"));
        }
        terms.put("kfsSource", "ENCORE_LMS");
        kfs.setAdditionalTerms(terms);
        kfsDocumentRepository.save(kfs);

        log.info("KFS {} reconciled from Encore LMS — APR={}%, installment={}, total={}, processingFee={}",
                kfsId, kfs.getApr(), kfs.getEmiAmount(), kfs.getTotalRepayment(), kfs.getProcessingFee());
    }

    public static final String DOCUMENT_KIND_INVOICE_DISCOUNTING_TERMS = "INVOICE_DISCOUNTING_TERMS";
    public static final String DOCUMENT_KIND_ANCHOR_PROGRAM_TERMS = "ANCHOR_PROGRAM_TERMS";

    /**
     * Anchor program terms (2-page PDF) stored as a KFS row for eSign after L2 program approval.
     */
    @Transactional
    public KfsDocument generateAnchorProgramTermsDocument(
            UUID applicationId,
            SanctionRecord sanction,
            Map<String, Object> programDetails) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        BigDecimal principal = sanction.getApprovedAmount() != null
                ? sanction.getApprovedAmount()
                : app.getSanctionedAmount();
        BigDecimal annualRate = sanction.getInterestRate() != null
                ? sanction.getInterestRate()
                : app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        int tenure = sanction.getApprovedTenure() != null
                ? sanction.getApprovedTenure()
                : app.getTenureMonths() != null ? app.getTenureMonths() : 12;

        List<KfsDocument> existing = kfsDocumentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        String version = "v" + (existing.size() + 1);

        Map<String, Object> additional = new LinkedHashMap<>();
        additional.put("documentKind", DOCUMENT_KIND_ANCHOR_PROGRAM_TERMS);
        if (programDetails != null) {
            additional.put("programDetails", programDetails);
        }
        if (sanction.getConditionsText() != null) {
            additional.put("conditionsText", sanction.getConditionsText());
        }

        KfsDocument kfs = KfsDocument.builder()
                .applicationId(applicationId)
                .version(version)
                .sanctionedAmount(principal)
                .interestRate(annualRate)
                .apr(annualRate)
                .tenureMonths(tenure)
                .emiAmount(BigDecimal.ZERO)
                .totalInterest(BigDecimal.ZERO)
                .totalRepayment(principal)
                .processingFee(BigDecimal.ZERO)
                .stampDuty(BigDecimal.ZERO)
                .insurancePremium(BigDecimal.ZERO)
                .otherCharges(BigDecimal.ZERO)
                .totalCostOfCredit(BigDecimal.ZERO)
                .coolingOffHours(DEFAULT_COOLING_OFF_HOURS)
                .grievanceMechanism(GRIEVANCE_DEFAULT)
                .lspDisclosure(LSP_DEFAULT)
                .additionalTerms(additional)
                .status("GENERATED")
                .build();

        kfs = kfsDocumentRepository.save(kfs);
        auditService.logEvent(applicationId, "ANCHOR_PROGRAM_TERMS_GENERATED",
                Map.of("kfsId", kfs.getId().toString(), "version", version,
                        "documentKind", DOCUMENT_KIND_ANCHOR_PROGRAM_TERMS));
        return kfs;
    }

    /**
     * Invoice discounting borrower: sanction terms document stored as a KFS row for eSign (2-page PDF, no LMS loan).
     */
    @Transactional
    public KfsDocument generateInvoiceDiscountingBorrowerTermsDocument(
            UUID applicationId,
            SanctionRecord sanction,
            Map<String, Object> charges) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        BigDecimal principal = sanction.getApprovedAmount() != null
                ? sanction.getApprovedAmount()
                : app.getSanctionedAmount();
        BigDecimal annualRate = sanction.getInterestRate() != null
                ? sanction.getInterestRate()
                : app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate();
        int tenure = sanction.getApprovedTenure() != null
                ? sanction.getApprovedTenure()
                : app.getTenureMonths() != null ? app.getTenureMonths() : 12;

        if (principal == null || annualRate == null) {
            throw new RuntimeException("Sanction limit and rate are required for invoice discounting terms document");
        }

        BigDecimal processingFee = extractCharge(charges, "processingFee", sanction.getProcessingFee());
        if (processingFee == null) {
            processingFee = BigDecimal.ZERO;
        }

        KfsTemplate template = kfsTemplateRepository
                .findFirstByLoanProductAndActiveTrueOrderByCreatedAtDesc(app.getLoanProduct())
                .orElse(null);
        String grievance = template != null && template.getGrievanceOfficerDetails() != null
                ? template.getGrievanceOfficerDetails() : GRIEVANCE_DEFAULT;
        String lsp = template != null && template.getLspDetails() != null
                ? template.getLspDetails() : LSP_DEFAULT;

        List<KfsDocument> existing = kfsDocumentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        String version = "v" + (existing.size() + 1);

        Map<String, Object> additional = new LinkedHashMap<>();
        additional.put("documentKind", DOCUMENT_KIND_INVOICE_DISCOUNTING_TERMS);
        if (sanction.getConditionsText() != null && !sanction.getConditionsText().isBlank()) {
            additional.put("conditionsText", sanction.getConditionsText());
        }
        if (sanction.getRemarks() != null && !sanction.getRemarks().isBlank()) {
            additional.put("remarks", sanction.getRemarks());
        }
        if (sanction.getApprovedBy() != null && !sanction.getApprovedBy().isBlank()) {
            additional.put("approvedBy", sanction.getApprovedBy());
        }
        if (charges != null) {
            additional.put("sanctionCharges", charges);
        }

        KfsDocument kfs = KfsDocument.builder()
                .applicationId(applicationId)
                .version(version)
                .sanctionedAmount(principal)
                .interestRate(annualRate)
                .apr(annualRate)
                .tenureMonths(tenure)
                .emiAmount(BigDecimal.ZERO)
                .totalInterest(BigDecimal.ZERO)
                .totalRepayment(principal)
                .processingFee(processingFee)
                .stampDuty(BigDecimal.ZERO)
                .insurancePremium(BigDecimal.ZERO)
                .otherCharges(BigDecimal.ZERO)
                .totalCostOfCredit(processingFee != null ? processingFee : BigDecimal.ZERO)
                .coolingOffHours(DEFAULT_COOLING_OFF_HOURS)
                .grievanceMechanism(grievance)
                .lspDisclosure(lsp)
                .additionalTerms(additional)
                .status("GENERATED")
                .build();

        kfs = kfsDocumentRepository.save(kfs);
        auditService.logEvent(applicationId, "KFS_GENERATED",
                Map.of("kfsId", kfs.getId().toString(), "version", version,
                        "documentKind", DOCUMENT_KIND_INVOICE_DISCOUNTING_TERMS));
        log.info("Invoice discounting terms document for application {} — version={}", applicationId, version);
        return kfs;
    }

    /**
     * Record KFS acknowledgement by customer. Starts the cooling-off period.
     */
    @Transactional
    public KfsDocument acknowledgeKfs(UUID kfsId, UUID acknowledgedBy) {
        KfsDocument kfs = kfsDocumentRepository.findById(kfsId)
                .orElseThrow(() -> new RuntimeException("KFS not found: " + kfsId));

        if (!"GENERATED".equals(kfs.getStatus())) {
            throw new RuntimeException("KFS is not in GENERATED status. Current: " + kfs.getStatus());
        }

        kfs.setStatus("ACKNOWLEDGED");
        kfs.setAcknowledgedAt(Instant.now());
        kfs.setAcknowledgedBy(acknowledgedBy);
        kfs.setCoolingOffExpiresAt(Instant.now().plus(kfs.getCoolingOffHours(), ChronoUnit.HOURS));

        kfs = kfsDocumentRepository.save(kfs);

        auditService.logEvent(kfs.getApplicationId(), "KFS_ACKNOWLEDGED",
                Map.of("kfsId", kfsId.toString(), "coolingOffExpiresAt", kfs.getCoolingOffExpiresAt().toString()));

        log.info("KFS acknowledged for application {} — cooling-off expires at {}", kfs.getApplicationId(), kfs.getCoolingOffExpiresAt());
        return kfs;
    }

    /**
     * Check if cooling-off period has completed for a KFS.
     */
    public boolean isCoolingOffComplete(UUID applicationId) {
        return kfsDocumentRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(applicationId, "ACKNOWLEDGED")
                .map(kfs -> {
                    if (kfs.getCoolingOffExpiresAt() != null && Instant.now().isAfter(kfs.getCoolingOffExpiresAt())) {
                        if (!kfs.isCoolingOffCompleted()) {
                            kfs.setCoolingOffCompleted(true);
                            kfsDocumentRepository.save(kfs);
                        }
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    /**
     * Record eSign on KFS document.
     */
    @Transactional
    public KfsDocument esignKfs(UUID kfsId, String esignTransactionId) {
        KfsDocument kfs = kfsDocumentRepository.findById(kfsId)
                .orElseThrow(() -> new RuntimeException("KFS not found: " + kfsId));

        if (!"ACKNOWLEDGED".equals(kfs.getStatus())) {
            throw new RuntimeException("KFS must be acknowledged before eSign. Current: " + kfs.getStatus());
        }

        if (!isCoolingOffComplete(kfs.getApplicationId())) {
            throw new RuntimeException("Cooling-off period has not expired yet. Expires at: " + kfs.getCoolingOffExpiresAt());
        }

        kfs.setStatus("ESIGNED");
        kfs.setEsignedAt(Instant.now());
        kfs.setEsignTransactionId(esignTransactionId);
        kfs = kfsDocumentRepository.save(kfs);

        auditService.logEvent(kfs.getApplicationId(), "KFS_ESIGNED",
                Map.of("kfsId", kfsId.toString(), "esignTransactionId", esignTransactionId));

        log.info("KFS eSigned for application {} — txnId={}", kfs.getApplicationId(), esignTransactionId);
        return kfs;
    }

    /**
     * Get latest KFS for an application.
     * Throws {@link ResourceNotFoundException} when none exists so the global handler maps it
     * to a clean HTTP 404 instead of a noisy 500 with a full stack trace.
     */
    public KfsDocument getLatestKfs(UUID applicationId) {
        return kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No KFS found for application: " + applicationId));
    }

    /**
     * Get all KFS versions for an application.
     */
    public List<KfsDocument> getKfsHistory(UUID applicationId) {
        return kfsDocumentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    // ---- KFS Template Management ----

    @Transactional
    public KfsTemplate createTemplate(KfsTemplate template) {
        return kfsTemplateRepository.save(template);
    }

    @Transactional
    public KfsTemplate updateTemplate(UUID templateId, KfsTemplate updates) {
        KfsTemplate existing = kfsTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

        existing.setName(updates.getName());
        existing.setLoanProduct(updates.getLoanProduct());
        existing.setTemplateContent(updates.getTemplateContent());
        existing.setDefaultCharges(updates.getDefaultCharges());
        existing.setGrievanceOfficerDetails(updates.getGrievanceOfficerDetails());
        existing.setLspDetails(updates.getLspDetails());
        existing.setVersion(updates.getVersion());

        return kfsTemplateRepository.save(existing);
    }

    public List<KfsTemplate> listTemplates(boolean activeOnly) {
        return activeOnly
                ? kfsTemplateRepository.findByActiveTrueOrderByLoanProductAscCreatedAtDesc()
                : kfsTemplateRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void deactivateTemplate(UUID templateId) {
        KfsTemplate template = kfsTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));
        template.setActive(false);
        kfsTemplateRepository.save(template);
    }

    // ---- Private helpers ----

    private static boolean hasStoredRepaymentSchedule(
            Map<String, Object> charges, Optional<EncorePreOpenKfsMapper.Figures> encore) {
        if (encore.isPresent() && encore.get().installmentCount() > 0) {
            return true;
        }
        if (charges == null) {
            return false;
        }
        Object preOpen = charges.get("encorePreOpenSummaryJson");
        if (preOpen != null && !String.valueOf(preOpen).isBlank()) {
            return true;
        }
        Object schedule = charges.get("encoreRepaymentScheduleJson");
        return schedule != null && !String.valueOf(schedule).isBlank();
    }

    private BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        MathContext mc = new MathContext(10);
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), mc);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths, mc);
        return principal.multiply(monthlyRate).multiply(onePlusRPowN)
                .divide(onePlusRPowN.subtract(BigDecimal.ONE), mc)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateApr(BigDecimal principal, BigDecimal emi, int tenure, BigDecimal totalFees) {
        BigDecimal netDisbursed = principal.subtract(totalFees);
        if (netDisbursed.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        double low = 0.0, high = 100.0;
        for (int iter = 0; iter < 100; iter++) {
            double mid = (low + high) / 2;
            double monthlyRate = mid / 1200.0;
            double pv = 0;
            for (int i = 1; i <= tenure; i++) {
                pv += emi.doubleValue() / Math.pow(1 + monthlyRate, i);
            }
            if (pv > netDisbursed.doubleValue()) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return BigDecimal.valueOf((low + high) / 2).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal extractCharge(Map<String, Object> charges, String key, BigDecimal defaultValue) {
        if (charges == null || !charges.containsKey(key)) return defaultValue;
        Object val = charges.get(key);
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(val.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String installmentLabelFor(String tenureUnit) {
        if (tenureUnit == null) {
            return "EMI";
        }
        return switch (tenureUnit.trim().toLowerCase()) {
            case "day" -> "EDI";
            case "week" -> "EWI";
            default -> "EMI";
        };
    }
}
