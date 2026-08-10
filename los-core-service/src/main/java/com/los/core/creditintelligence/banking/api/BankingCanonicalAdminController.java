package com.los.core.creditintelligence.banking.api;

import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.domain.CiBankRecurringObligation;
import com.los.core.creditintelligence.banking.domain.CiBankStatementQuality;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.repository.CiBankAccountRepository;
import com.los.core.creditintelligence.banking.repository.CiBankRecurringObligationRepository;
import com.los.core.creditintelligence.banking.repository.CiBankStatementQualityRepository;
import com.los.core.creditintelligence.banking.repository.CiBankTransactionRepository;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
import com.los.core.creditintelligence.banking.service.CanonicalBankingRuleEvaluator;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal admin APIs for Phase C3 banking canonicalization.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/banking")
@RequiredArgsConstructor
public class BankingCanonicalAdminController {

    private final CiBankAccountRepository accountRepository;
    private final CiBankTransactionRepository transactionRepository;
    private final CiBankRecurringObligationRepository obligationRepository;
    private final CiBankStatementQualityRepository qualityRepository;
    private final CiMetricResultRepository metricResultRepository;
    private final CreditIntelligenceProperties properties;
    private final CanonicalBankingRuleEvaluator ruleEvaluator;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @GetMapping("/applications/{applicationId}/accounts")
    public List<Map<String, Object>> getAccounts(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        return accountRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(a -> tenantMatches(tenantHeader, a.getTenantId()))
                .map(this::accountSummary)
                .toList();
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public Map<String, Object> getTransactions(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        CiBankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        assertTenant(tenantHeader, account.getTenantId());
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)));
        Page<CiBankTransaction> result;
        if (from != null && to != null) {
            result = transactionRepository.findByBankAccountIdAndTransactionDateBetween(accountId, from, to, pr);
        } else {
            result = transactionRepository.findByBankAccountId(accountId, pr);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountId", accountId.toString());
        out.put("page", result.getNumber());
        out.put("size", result.getSize());
        out.put("totalElements", result.getTotalElements());
        out.put("transactions", result.getContent().stream().map(this::txnSummary).toList());
        return out;
    }

    @GetMapping("/applications/{applicationId}/obligations")
    public List<Map<String, Object>> getObligations(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return obligationRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(this::obligationSummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/quality")
    public List<Map<String, Object>> getQuality(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return qualityRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(this::qualitySummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/metrics")
    public List<Map<String, Object>> getMetrics(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        return metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(m -> m.getMetricCode() != null && m.getMetricCode().startsWith("banking."))
                .map(this::metricSummary)
                .toList();
    }

    @GetMapping("/applications/{applicationId}/legacy-vs-canonical")
    public Map<String, Object> legacyVsCanonical(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternalToken(token);
        List<CiBankAccount> accounts = accountRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        Map<String, CiMetricResult> byCode = new LinkedHashMap<>();
        for (CiMetricResult m : metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
            if (m.getMetricCode() != null && m.getMetricCode().startsWith("banking.")) {
                byCode.putIfAbsent(m.getMetricCode(), m);
            }
        }
        var cfg = properties.getCanonicalization().getBanking();
        List<CanonicalBankingRuleEvaluator.RuleEvalResult> rules = ruleEvaluator.evaluateAll(
                accounts, byCode, cfg.getAbbMinimum(), null,
                cfg.getChequeReturnMax3m(), cfg.getNachReturnMax3m(),
                cfg.getCashDepositRatioWarningPct(), cfg.getOdUtilisationWarningPct());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId.toString());
        out.put("accountCount", accounts.size());
        out.put("canonicalMetrics", byCode.keySet());
        out.put("compatKeys", List.of(
                "AVERAGE_BANK_BALANCE", "ANNUAL_BANKING_TURNOVER", "EMI_OBLIGATION", "CHEQUE_BOUNCES_*"));
        out.put("note", "Production scorecard values unchanged; canonical is shadow-only");
        out.put("rules", rules.stream().map(r -> Map.of(
                "ruleId", r.ruleId(),
                "outcome", r.outcome(),
                "value", r.value() != null ? r.value() : "",
                "threshold", r.threshold() != null ? r.threshold() : "")).toList());
        out.put("adb3m", metricBrief(byCode.get(BankingMetricService.ADB_3M)));
        out.put("adjustedCredits12m", metricBrief(byCode.get(BankingMetricService.ADJ_12M)));
        out.put("monthlyObligation", metricBrief(byCode.get(BankingMetricService.MONTHLY_OBL)));
        return out;
    }

    private Map<String, Object> accountSummary(CiBankAccount a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("institutionName", a.getInstitutionName());
        m.put("accountType", a.getAccountType());
        m.put("accountNumberLast4", a.getAccountNumberLast4());
        m.put("holderNameMatchStatus", a.getHolderNameMatchStatus());
        m.put("aggregationEligible", a.isAggregationEligible());
        m.put("closingBalance", a.getClosingBalance());
        m.put("providerCode", a.getProviderCode());
        m.put("sourceReference", a.getSourceReference());
        return m;
    }

    private Map<String, Object> txnSummary(CiBankTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("date", t.getTransactionDate() != null ? t.getTransactionDate().toString() : null);
        m.put("direction", t.getDirection());
        m.put("amount", t.getAmount());
        m.put("balanceAfter", t.getBalanceAfter());
        m.put("category", t.getCategory());
        m.put("mode", t.getMode());
        m.put("narration", t.getDescriptionRaw());
        m.put("duplicateStatus", t.getDuplicateStatus());
        m.put("emiFlag", t.isEmiFlag());
        return m;
    }

    private Map<String, Object> obligationSummary(CiBankRecurringObligation o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId().toString());
        m.put("lenderName", o.getLenderName());
        m.put("detectedAmount", o.getDetectedAmount());
        m.put("occurrenceCount", o.getOccurrenceCount());
        m.put("regularityScore", o.getRegularityScore());
        m.put("method", o.getMethod());
        m.put("qualityStatus", o.getQualityStatus());
        return m;
    }

    private Map<String, Object> qualitySummary(CiBankStatementQuality q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId().toString());
        m.put("bankAccountId", q.getBankAccountId() != null ? q.getBankAccountId().toString() : null);
        m.put("integrityStatus", q.getIntegrityStatus());
        m.put("completenessRatio", q.getCompletenessRatio());
        m.put("balanceBreaks", q.getBalanceBreaks());
        m.put("duplicateCount", q.getDuplicateCount());
        m.put("classificationCoverage", q.getClassificationCoverage());
        return m;
    }

    private Map<String, Object> metricSummary(CiMetricResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("metricCode", r.getMetricCode());
        m.put("outcome", r.getOutcome());
        m.put("value", r.getValue());
        m.put("dataQualityStatus", r.getDataQualityStatus());
        m.put("evidence", r.getEvidence());
        return m;
    }

    private Map<String, Object> metricBrief(CiMetricResult r) {
        if (r == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", r.getOutcome());
        m.put("value", r.getValue());
        m.put("quality", r.getDataQualityStatus());
        return m;
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("credit-intelligence.internal-token blank — allowing banking admin request (local)");
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid X-Internal-Token");
        }
    }

    private boolean tenantMatches(String header, UUID tenantId) {
        if (header == null || header.isBlank() || tenantId == null) {
            return true;
        }
        return header.equalsIgnoreCase(tenantId.toString());
    }

    private void assertTenant(String header, UUID tenantId) {
        if (!tenantMatches(header, tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant mismatch");
        }
    }
}
