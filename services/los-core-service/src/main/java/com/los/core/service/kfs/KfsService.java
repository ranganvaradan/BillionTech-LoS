package com.los.core.service.kfs;

import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.KfsTemplate;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.KfsTemplateRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

        if (app.getRequestedAmount() == null || app.getInterestRate() == null || app.getTenureMonths() == null) {
            throw new RuntimeException("Loan terms (amount, rate, tenure) must be set before KFS generation");
        }

        BigDecimal principal = app.getRequestedAmount();
        BigDecimal annualRate = app.getInterestRate();
        int tenure = app.getTenureMonths();

        BigDecimal emi = calculateEmi(principal, annualRate, tenure);
        BigDecimal totalRepayment = emi.multiply(BigDecimal.valueOf(tenure));
        BigDecimal totalInterest = totalRepayment.subtract(principal);

        BigDecimal processingFee = extractCharge(charges, "processingFee", principal.multiply(new BigDecimal("0.02")));
        BigDecimal stampDuty = extractCharge(charges, "stampDuty", BigDecimal.ZERO);
        BigDecimal insurancePremium = extractCharge(charges, "insurancePremium", BigDecimal.ZERO);
        BigDecimal otherCharges = extractCharge(charges, "otherCharges", BigDecimal.ZERO);

        BigDecimal totalCost = totalInterest.add(processingFee).add(stampDuty).add(insurancePremium).add(otherCharges);

        BigDecimal apr = calculateApr(principal, emi, tenure, processingFee.add(stampDuty).add(insurancePremium).add(otherCharges));

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
     */
    public KfsDocument getLatestKfs(UUID applicationId) {
        return kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new RuntimeException("No KFS found for application: " + applicationId));
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
}
