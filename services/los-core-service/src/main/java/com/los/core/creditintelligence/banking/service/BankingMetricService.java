package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankAccountType;
import com.los.core.creditintelligence.banking.domain.BankingConstants;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.domain.CiBankRecurringObligation;
import com.los.core.creditintelligence.banking.domain.CiBankStatementQuality;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.core.domain.CiEvidenceGroup;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.repository.CiBankRecurringObligationRepository;
import com.los.core.creditintelligence.banking.repository.CiBankStatementQualityRepository;
import com.los.core.creditintelligence.core.repository.CiEvidenceGroupRepository;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persist banking metrics into shared {@link CiMetricResult}. Uses EvidenceGroup when member count &gt; 20.
 */
@Service
@RequiredArgsConstructor
public class BankingMetricService {

    public static final String METRIC_VERSION = "V1";
    public static final int EVIDENCE_GROUP_THRESHOLD = 20;

    public static final String ADB_3M = "banking.avg_daily_balance_3m";
    public static final String ADB_6M = "banking.avg_daily_balance_6m";
    public static final String ADB_12M = "banking.avg_daily_balance_12m";
    public static final String ADJ_3M = "banking.adjusted_business_credits_3m";
    public static final String ADJ_6M = "banking.adjusted_business_credits_6m";
    public static final String ADJ_12M = "banking.adjusted_business_credits_12m";
    public static final String AVG_M_6M = "banking.average_monthly_business_credits_6m";
    public static final String AVG_M_12M = "banking.average_monthly_business_credits_12m";
    public static final String MIN_BAL_3M = "banking.minimum_balance_3m";
    public static final String NEG_DAYS_6M = "banking.negative_balance_days_6m";
    public static final String CASH_RATIO_12M = "banking.cash_deposit_ratio_12m";
    public static final String CHEQUE_3M = "banking.cheque_return_count_3m";
    public static final String CHEQUE_6M = "banking.cheque_return_count_6m";
    public static final String NACH_3M = "banking.nach_return_count_3m";
    public static final String NACH_6M = "banking.nach_return_count_6m";
    public static final String MONTHLY_OBL = "banking.monthly_obligation";
    public static final String OD_AVG_6M = "banking.od_cc_average_utilisation_6m";
    public static final String OD_PEAK_6M = "banking.od_cc_peak_utilisation_6m";
    public static final String OD_DAYS_90 = "banking.od_cc_days_above_90pct_6m";
    public static final String COMPLETENESS = "banking.statement_completeness";

    private final CiMetricResultRepository metricResultRepository;
    private final CiEvidenceGroupRepository evidenceGroupRepository;
    private final CiBankRecurringObligationRepository obligationRepository;
    private final CiBankStatementQualityRepository qualityRepository;
    private final BankAverageDailyBalanceCalculator adbCalculator;
    private final BankAdjustedTurnoverCalculator turnoverCalculator;
    private final BankEmiObligationDetector emiDetector;
    private final BankOdUtilisationCalculator odCalculator;
    private final CreditIntelligenceProperties properties;

    @Transactional
    public List<CiMetricResult> computeAndPersist(
            UUID tenantId,
            UUID applicationId,
            UUID sourceRecordId,
            List<CiBankAccount> accounts,
            List<CiBankTransaction> transactions,
            Map<String, Object> summaryPayload,
            LocalDate asOf) {

        LocalDate date = asOf != null ? asOf : LocalDate.now();
        CreditIntelligenceProperties.Canonicalization.Banking cfg = bankingCfg();
        List<CiBankTransaction> eligibleTxns = filterEligible(accounts, transactions);
        boolean hasTxns = eligibleTxns != null && !eligibleTxns.isEmpty();
        List<CiMetricResult> results = new ArrayList<>();

        // Completeness
        results.add(persist(computeCompleteness(tenantId, applicationId, sourceRecordId, accounts, applicationId)));

        if (!hasTxns) {
            results.addAll(computeSummaryOnlyMetrics(
                    tenantId, applicationId, sourceRecordId, accounts, summaryPayload));
            return results;
        }

        // ADB windows
        for (int months : List.of(3, 6, 12)) {
            String code = months == 3 ? ADB_3M : months == 6 ? ADB_6M : ADB_12M;
            var adb = adbCalculator.calculate(eligibleTxns, months, date);
            Map<String, Object> evidence = new LinkedHashMap<>(adb.evidence());
            evidence.put("sourceRecordId", sourceRecordId != null ? sourceRecordId.toString() : null);
            results.add(persist(result(tenantId, applicationId, sourceRecordId, code,
                    adb.outcome(), valueOf(adb.averageDailyBalance()),
                    qualityOf(adb.outcome()), List.of(), List.of(), evidence)));
            if (months == 3 && adb.minimumBalance() != null) {
                results.add(persist(result(tenantId, applicationId, sourceRecordId, MIN_BAL_3M,
                        adb.outcome(), valueOf(adb.minimumBalance()),
                        qualityOf(adb.outcome()), List.of(), List.of(), evidence)));
            }
            if (months == 6) {
                results.add(persist(result(tenantId, applicationId, sourceRecordId, NEG_DAYS_6M,
                        adb.outcome(),
                        BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(adb.outcome())
                                ? null : Map.of("v", adb.negativeDays()),
                        qualityOf(adb.outcome()), List.of(), List.of(), evidence)));
            }
        }

        // Adjusted turnover
        for (int months : List.of(3, 6, 12)) {
            String code = months == 3 ? ADJ_3M : months == 6 ? ADJ_6M : ADJ_12M;
            var turn = turnoverCalculator.calculate(
                    eligibleTxns, months, date, cfg.getMinClassificationCoverage());
            Map<String, Object> evidence = packEvidenceIds(
                    tenantId, applicationId, turn.evidence(),
                    turn.includedIds(), turn.excludedIds(), "ADJUSTED_CREDITS_" + months + "M");
            results.add(persist(result(tenantId, applicationId, sourceRecordId, code,
                    turn.outcome(), valueOf(turn.adjustedCredits()),
                    qualityOf(turn.outcome()), List.of(), List.of(), evidence)));

            if (months == 6 || months == 12) {
                String avgCode = months == 6 ? AVG_M_6M : AVG_M_12M;
                BigDecimal monthly = turn.adjustedCredits() != null
                        ? turn.adjustedCredits().divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP)
                        : null;
                results.add(persist(result(tenantId, applicationId, sourceRecordId, avgCode,
                        turn.outcome(), valueOf(monthly),
                        qualityOf(turn.outcome()), List.of(), List.of(), evidence)));
            }
            if (months == 12) {
                results.add(persist(result(tenantId, applicationId, sourceRecordId, CASH_RATIO_12M,
                        turn.outcome(), valueOf(turn.cashDepositRatio()),
                        qualityOf(turn.outcome()), List.of(), List.of(), evidence)));
            }
        }

        // Bounce counts from txn classifications
        results.add(persist(countCategory(tenantId, applicationId, sourceRecordId, eligibleTxns,
                TxnCategory.CHEQUE_RETURN, 3, date, CHEQUE_3M)));
        results.add(persist(countCategory(tenantId, applicationId, sourceRecordId, eligibleTxns,
                TxnCategory.CHEQUE_RETURN, 6, date, CHEQUE_6M)));
        results.add(persist(countCategory(tenantId, applicationId, sourceRecordId, eligibleTxns,
                TxnCategory.NACH_RETURN, 3, date, NACH_3M)));
        results.add(persist(countCategory(tenantId, applicationId, sourceRecordId, eligibleTxns,
                TxnCategory.NACH_RETURN, 6, date, NACH_6M)));

        // EMI
        UUID primaryAcctId = accounts != null && !accounts.isEmpty() ? accounts.get(0).getId() : null;
        var emi = emiDetector.detect(tenantId, applicationId, primaryAcctId, eligibleTxns,
                cfg.getEmiMinOccurrences(), cfg.getEmiRegularityThreshold());
        for (CiBankRecurringObligation obl : emi.obligations()) {
            obligationRepository.save(obl);
        }
        Map<String, Object> emiEv = new LinkedHashMap<>(emi.evidence());
        results.add(persist(result(tenantId, applicationId, sourceRecordId, MONTHLY_OBL,
                emi.outcome(), valueOf(emi.monthlyObligation()),
                qualityOf(emi.outcome()), List.of(), List.of(), emiEv)));

        // OD/CC
        CiBankAccount odAcct = findOdCc(accounts);
        if (odAcct != null) {
            List<CiBankTransaction> odTxns = eligibleTxns.stream()
                    .filter(t -> odAcct.getId().equals(t.getBankAccountId()))
                    .toList();
            var od = odCalculator.calculate(odAcct, odTxns, 6, date, cfg.getOdUtilisationWarningPct());
            Map<String, Object> odEv = new LinkedHashMap<>(od.evidence());
            results.add(persist(result(tenantId, applicationId, sourceRecordId, OD_AVG_6M,
                    od.outcome(), valueOf(od.averageUtilisationPct()), qualityOf(od.outcome()),
                    List.of(), List.of(), odEv)));
            results.add(persist(result(tenantId, applicationId, sourceRecordId, OD_PEAK_6M,
                    od.outcome(), valueOf(od.peakUtilisationPct()), qualityOf(od.outcome()),
                    List.of(), List.of(), odEv)));
            results.add(persist(result(tenantId, applicationId, sourceRecordId, OD_DAYS_90,
                    od.outcome(),
                    BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(od.outcome())
                            ? null : Map.of("v", od.daysAbove90Pct()),
                    qualityOf(od.outcome()), List.of(), List.of(), odEv)));
        } else {
            Map<String, Object> odEv = Map.of("reason", "NO_OD_CC_ACCOUNT");
            results.add(persist(di(tenantId, applicationId, sourceRecordId, OD_AVG_6M, odEv)));
            results.add(persist(di(tenantId, applicationId, sourceRecordId, OD_PEAK_6M, odEv)));
            results.add(persist(di(tenantId, applicationId, sourceRecordId, OD_DAYS_90, odEv)));
        }

        return results;
    }

    /**
     * When AA summary has no transactions: DI for ADB/adjusted; soft PARTIAL for bounce/EMI from summary.
     */
    private List<CiMetricResult> computeSummaryOnlyMetrics(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<CiBankAccount> accounts, Map<String, Object> summary) {

        List<CiMetricResult> results = new ArrayList<>();
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("reason", "NO_TRANSACTION_SERIES");
        base.put("sourceRecordId", sourceRecordId != null ? sourceRecordId.toString() : null);

        // Prefer DATA_INSUFFICIENT for ADB; put summary avgMonthlyBalance in evidence only
        BigDecimal summaryAmb = extractSummaryAvgMonthlyBalance(accounts, summary);
        for (String code : List.of(ADB_3M, ADB_6M, ADB_12M, MIN_BAL_3M, NEG_DAYS_6M)) {
            Map<String, Object> ev = new LinkedHashMap<>(base);
            ev.put("method", BankingConstants.BANK_AVERAGE_DAILY_BALANCE_V1);
            if (summaryAmb != null) {
                ev.put("summaryAvgMonthlyBalance", summaryAmb.toPlainString());
                ev.put("note", "SUMMARY_PROXY_NOT_USED_FOR_ADB_VALUE");
            }
            results.add(persist(di(tenantId, applicationId, sourceRecordId, code, ev)));
        }

        for (String code : List.of(ADJ_3M, ADJ_6M, ADJ_12M, AVG_M_6M, AVG_M_12M, CASH_RATIO_12M)) {
            Map<String, Object> ev = new LinkedHashMap<>(base);
            ev.put("method", BankingConstants.ADJUSTED_BANKING_TURNOVER_V1);
            results.add(persist(di(tenantId, applicationId, sourceRecordId, code, ev)));
        }

        // Bounce from summary bounceCount6Months as EXTRACTED soft / PARTIAL
        Object bounceRaw = summary != null ? summary.get("bounceCount6Months") : null;
        Integer bounce = bounceRaw instanceof Number n ? n.intValue() : null;
        Map<String, Object> bounceEv = new LinkedHashMap<>(base);
        bounceEv.put("summaryBounceCount6Months", bounce);
        bounceEv.put("qualityNote", "SUMMARY_EXTRACTED_NO_TXN_EVIDENCE");
        String bounceOutcome = bounce != null ? BankingMetricOutcome.REFER.name()
                : BankingMetricOutcome.DATA_INSUFFICIENT.name();
        results.add(persist(result(tenantId, applicationId, sourceRecordId, CHEQUE_6M,
                bounceOutcome, bounce != null ? Map.of("v", bounce) : null,
                "PARTIAL", List.of(), List.of(), bounceEv)));
        results.add(persist(di(tenantId, applicationId, sourceRecordId, CHEQUE_3M,
                Map.of("reason", "NO_TXN_LEVEL_BOUNCE_3M"))));
        results.add(persist(di(tenantId, applicationId, sourceRecordId, NACH_3M,
                Map.of("reason", "NO_TXN_LEVEL_NACH"))));
        results.add(persist(di(tenantId, applicationId, sourceRecordId, NACH_6M,
                Map.of("reason", "NO_TXN_LEVEL_NACH"))));

        // EMI from regularEmiOutflows as PARTIAL/REFER — not high-confidence DERIVED
        Object emiRaw = summary != null ? summary.get("regularEmiOutflows") : null;
        BigDecimal emi = toBd(emiRaw);
        Map<String, Object> emiEv = new LinkedHashMap<>(base);
        emiEv.put("summaryRegularEmiOutflows", emi != null ? emi.toPlainString() : null);
        emiEv.put("qualityNote", "SUMMARY_PARTIAL_NOT_TXN_CONFIRMED");
        String emiOutcome = emi != null ? BankingMetricOutcome.REFER.name()
                : BankingMetricOutcome.DATA_INSUFFICIENT.name();
        results.add(persist(result(tenantId, applicationId, sourceRecordId, MONTHLY_OBL,
                emiOutcome, valueOf(emi), "PARTIAL", List.of(), List.of(), emiEv)));

        Map<String, Object> odEv = Map.of("reason", "NO_TRANSACTIONS");
        results.add(persist(di(tenantId, applicationId, sourceRecordId, OD_AVG_6M, odEv)));
        results.add(persist(di(tenantId, applicationId, sourceRecordId, OD_PEAK_6M, odEv)));
        results.add(persist(di(tenantId, applicationId, sourceRecordId, OD_DAYS_90, odEv)));

        return results;
    }

    private CiMetricResult computeCompleteness(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<CiBankAccount> accounts, UUID appId) {
        List<CiBankStatementQuality> qualities =
                qualityRepository.findByApplicationIdOrderByCreatedAtDesc(appId);
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (qualities.isEmpty()) {
            evidence.put("reason", "NO_QUALITY_ROWS");
            return di(tenantId, applicationId, sourceRecordId, COMPLETENESS, evidence);
        }
        BigDecimal avg = qualities.stream()
                .map(CiBankStatementQuality::getCompletenessRatio)
                .filter(r -> r != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long n = qualities.stream().filter(q -> q.getCompletenessRatio() != null).count();
        if (n == 0) {
            // No txns → completeness 0 is known
            evidence.put("accountCount", accounts != null ? accounts.size() : 0);
            evidence.put("note", "NO_TXN_COMPLETENESS_ZERO");
            return result(tenantId, applicationId, sourceRecordId, COMPLETENESS,
                    BankingMetricOutcome.REFER.name(), valueOf(BigDecimal.ZERO),
                    "PARTIAL", List.of(), List.of(), evidence);
        }
        BigDecimal ratio = avg.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        evidence.put("completenessRatio", ratio.toPlainString());
        evidence.put("qualityRows", n);
        String outcome = ratio.compareTo(BigDecimal.valueOf(bankingCfg().getMinStatementCompleteness())) >= 0
                ? BankingMetricOutcome.PASS.name()
                : BankingMetricOutcome.REFER.name();
        return result(tenantId, applicationId, sourceRecordId, COMPLETENESS,
                outcome, valueOf(ratio), qualityOf(outcome), List.of(), List.of(), evidence);
    }

    private CiMetricResult countCategory(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            List<CiBankTransaction> txns, TxnCategory category, int months,
            LocalDate asOf, String code) {
        LocalDate start = asOf.minusMonths(months).plusDays(1);
        List<CiBankTransaction> matches = txns.stream()
                .filter(t -> !"DUPLICATE".equalsIgnoreCase(t.getDuplicateStatus()))
                .filter(t -> category.name().equalsIgnoreCase(t.getCategory()) || t.isBounceFlag()
                        && category == TxnCategory.CHEQUE_RETURN
                        && TxnCategory.CHEQUE_RETURN.name().equalsIgnoreCase(t.getCategory()))
                .filter(t -> t.getTransactionDate() != null
                        && !t.getTransactionDate().isBefore(start)
                        && !t.getTransactionDate().isAfter(asOf))
                .filter(t -> category.name().equalsIgnoreCase(t.getCategory()))
                .toList();
        // Valid zero with complete txns is PASS
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("category", category.name());
        evidence.put("months", months);
        evidence.put("count", matches.size());
        List<UUID> ids = matches.stream().map(CiBankTransaction::getId).toList();
        Map<String, Object> packed = packEvidenceIds(
                tenantId, applicationId, evidence, ids, List.of(), code);
        return result(tenantId, applicationId, sourceRecordId, code,
                BankingMetricOutcome.PASS.name(), Map.of("v", matches.size()),
                "OK", List.of(), List.of(), packed);
    }

    private Map<String, Object> packEvidenceIds(
            UUID tenantId, UUID applicationId,
            Map<String, Object> evidence,
            List<UUID> included,
            List<UUID> excluded,
            String groupType) {
        Map<String, Object> out = new LinkedHashMap<>(evidence != null ? evidence : Map.of());
        attachIds(tenantId, applicationId, out, "included", included, groupType + "_INCLUDED");
        attachIds(tenantId, applicationId, out, "excluded", excluded, groupType + "_EXCLUDED");
        return out;
    }

    private void attachIds(
            UUID tenantId, UUID applicationId,
            Map<String, Object> evidence,
            String key,
            List<UUID> ids,
            String groupType) {
        if (ids == null || ids.isEmpty()) {
            evidence.put(key + "Count", 0);
            return;
        }
        if (ids.size() <= EVIDENCE_GROUP_THRESHOLD) {
            evidence.put(key + "Ids", ids.stream().map(UUID::toString).toList());
            evidence.put(key + "Count", ids.size());
            return;
        }
        List<Object> sample = ids.stream().limit(10).map(UUID::toString).map(s -> (Object) s).toList();
        CiEvidenceGroup group = evidenceGroupRepository.save(CiEvidenceGroup.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .groupType(groupType)
                .memberCount(ids.size())
                .memberIds(ids.stream().map(UUID::toString).map(s -> (Object) s).toList())
                .summary(Map.of("sampleIds", sample, "count", ids.size()))
                .build());
        evidence.put(key + "EvidenceGroupId", group.getId().toString());
        evidence.put(key + "SampleIds", sample);
        evidence.put(key + "Count", ids.size());
    }

    private List<CiBankTransaction> filterEligible(
            List<CiBankAccount> accounts, List<CiBankTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return List.of();
        }
        if (accounts == null || accounts.isEmpty()) {
            return transactions;
        }
        Map<UUID, CiBankAccount> byId = accounts.stream()
                .collect(Collectors.toMap(CiBankAccount::getId, a -> a, (a, b) -> a));
        return transactions.stream()
                .filter(t -> {
                    CiBankAccount a = byId.get(t.getBankAccountId());
                    return a == null || a.isAggregationEligible();
                })
                .toList();
    }

    private CiBankAccount findOdCc(List<CiBankAccount> accounts) {
        if (accounts == null) {
            return null;
        }
        return accounts.stream()
                .filter(a -> BankAccountType.OVERDRAFT.name().equalsIgnoreCase(a.getAccountType())
                        || BankAccountType.CASH_CREDIT.name().equalsIgnoreCase(a.getAccountType()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal extractSummaryAvgMonthlyBalance(
            List<CiBankAccount> accounts, Map<String, Object> summary) {
        if (accounts != null) {
            BigDecimal sum = BigDecimal.ZERO;
            int n = 0;
            for (CiBankAccount a : accounts) {
                if (a.getMetadata() != null && a.getMetadata().get("avgMonthlyBalance") != null) {
                    BigDecimal v = toBd(a.getMetadata().get("avgMonthlyBalance"));
                    if (v != null) {
                        sum = sum.add(v);
                        n++;
                    }
                }
            }
            if (n > 0) {
                return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    public List<CiMetricResult> findForApplication(UUID applicationId) {
        return metricResultRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(m -> m.getMetricCode() != null && m.getMetricCode().startsWith("banking."))
                .toList();
    }

    private CiMetricResult persist(CiMetricResult r) {
        return metricResultRepository.save(r);
    }

    private CiMetricResult di(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            String code, Map<String, Object> evidence) {
        return result(tenantId, applicationId, sourceRecordId, code,
                BankingMetricOutcome.DATA_INSUFFICIENT.name(), null, "DATA_INSUFFICIENT",
                List.of(), List.of(), evidence);
    }

    private CiMetricResult result(
            UUID tenantId, UUID applicationId, UUID sourceRecordId,
            String code, String outcome, Map<String, Object> value,
            String quality, List<Object> included, List<Object> excluded,
            Map<String, Object> evidence) {
        List<Object> sourceIds = sourceRecordId != null
                ? List.of(sourceRecordId.toString()) : List.of();
        return CiMetricResult.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .metricCode(code)
                .metricVersion(METRIC_VERSION)
                .outcome(outcome)
                .value(value)
                .dataQualityStatus(quality != null ? quality : "OK")
                .includedReferences(included)
                .excludedReferences(excluded)
                .sourceRecordIds(sourceIds)
                .evidence(evidence != null ? evidence : Map.of())
                .metadata(Map.of("domain", "BANKING"))
                .build();
    }

    private static Map<String, Object> valueOf(BigDecimal v) {
        return v == null ? null : Map.of("v", v);
    }

    private static String qualityOf(String outcome) {
        if (BankingMetricOutcome.DATA_INSUFFICIENT.name().equals(outcome)) {
            return "DATA_INSUFFICIENT";
        }
        if (BankingMetricOutcome.REFER.name().equals(outcome)) {
            return "PARTIAL";
        }
        return "OK";
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private CreditIntelligenceProperties.Canonicalization.Banking bankingCfg() {
        return properties.getCanonicalization().getBanking();
    }
}
