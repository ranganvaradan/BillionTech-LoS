package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.*;
import com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor;
import com.los.core.creditintelligence.bureau.repository.*;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.support.ContentHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Normalizes provider reportData into CiBureauReport / tradelines / payment history / inquiries.
 * Idempotent on tenant|app|provider|txnId|checksum. Never stores raw bureau XML.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BureauNormalizationService {

    public static final String NORMALIZER_VERSION = "BUREAU_NORMALIZER_V1";

    private final CiBureauReportRepository reportRepository;
    private final CiBureauTradelineRepository tradelineRepository;
    private final CiBureauPaymentHistoryRepository paymentHistoryRepository;
    private final CiBureauInquiryRepository inquiryRepository;
    private final CiBureauReportSummaryRepository reportSummaryRepository;
    private final CiBureauScoringElementRepository scoringElementRepository;
    private final CiBureauDuplicateGroupRepository duplicateGroupRepository;
    private final SourceRegistryService sourceRegistryService;
    private final BureauProductTaxonomyService taxonomyService;
    private final BureauLiveAccountClassifier liveAccountClassifier;
    private final BureauMetricService metricService;
    private final ContentHasher contentHasher;
    private final CreditIntelligenceProperties properties;

    public record NormalizationResult(
            CiBureauReport report,
            List<CiBureauTradeline> tradelines,
            List<CiMetricResult> metrics,
            boolean alreadyExisted) {
    }

    @Transactional
    public NormalizationResult normalize(
            UUID applicationId,
            UUID tenantId,
            String provider,
            Map<String, Object> reportData,
            String transactionId,
            UUID kycStepResultId) {

        UUID tid = tenantId != null ? tenantId : properties.getDefaultTenantId();
        String providerCode = provider != null ? provider.toUpperCase(Locale.ROOT) : "EQUIFAX";
        Map<String, Object> data = reportData != null ? reportData : Map.of();

        String idempotencyKey = buildIdempotencyKey(tid, applicationId, providerCode, transactionId, data);
        Optional<CiBureauReport> existing = reportRepository
                .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, idempotencyKey);
        if (existing.isPresent()) {
            CiBureauReport report = existing.get();
            List<CiBureauTradeline> tls = tradelineRepository.findByBureauReportId(report.getId());
            List<CiMetricResult> metrics = metricService.findForReport(report.getId());
            return new NormalizationResult(report, tls, metrics, true);
        }

        String checksum = contentHasher.hashMap(checksumPayload(data, transactionId));
        var source = sourceRegistryService.createOrGet(
                tid, applicationId, SourceType.CONSUMER_BUREAU.name(), providerCode,
                "UNDERWRITING",
                "bureau-canon-" + idempotencyKey,
                Map.of(
                        "transactionId", transactionId != null ? transactionId : "",
                        "parserVersion", str(data.get("parserVersion")),
                        "referenceOnly", true),
                "BureauNormalizationService");

        String contentRef = kycStepResultId != null
                ? "kyc_step_result:" + kycStepResultId
                : "api_audit:" + (transactionId != null ? transactionId : "unknown");
        sourceRegistryService.createArtifact(source.getId(), contentRef, "application/json", checksum);

        String extractionStatus = str(data.get("tradelineExtractionStatus"));
        if (extractionStatus == null || extractionStatus.isBlank()) {
            if (Boolean.TRUE.equals(data.get("simulated"))) {
                extractionStatus = "MISSING";
            } else if (data.get("accounts") instanceof List<?> list) {
                extractionStatus = list.isEmpty() ? "EMPTY" : "OK";
            } else {
                extractionStatus = "ABSENT";
            }
        }
        boolean tradelinesPresent = data.get("accounts") instanceof List<?> list && !list.isEmpty();
        if (data.containsKey("tradelinesPresent")) {
            tradelinesPresent = Boolean.TRUE.equals(data.get("tradelinesPresent")) || tradelinesPresent;
        }

        LocalDate reportDate = parseDate(str(data.get("reportDate")));

        Integer score = null;
        Object scoreObj = data.get("creditScore");
        if (scoreObj instanceof Number n) {
            score = n.intValue();
        }

        Map<String, Object> reportMeta = new LinkedHashMap<>();
        reportMeta.put("transactionId", transactionId);
        if (data.get("dpd30Plus") != null) {
            reportMeta.put("dpd30Plus", data.get("dpd30Plus"));
        }
        if (data.get("dpd60Plus") != null) {
            reportMeta.put("dpd60Plus", data.get("dpd60Plus"));
        }
        if (data.get("dpd90Plus") != null) {
            reportMeta.put("dpd90Plus", data.get("dpd90Plus"));
        }
        if (data.get("enquiryAge30Days") != null) {
            reportMeta.put("enquiryAge30Days", data.get("enquiryAge30Days"));
        }
        // Preserve Equifax no-hit / NTC signals for canonical metric writer (do not invent).
        if (Boolean.TRUE.equals(data.get("noRecordFound"))) {
            reportMeta.put("noRecordFound", true);
        }
        if (score != null && score == -1) {
            reportMeta.put("scoreSentinel", -1);
        }
        if (data.get("statusNtc") instanceof Boolean statusNtc) {
            reportMeta.put("statusNtc", statusNtc);
        }
        reportMeta.put("checksum", checksum);

        CiBureauReport report = reportRepository.save(CiBureauReport.builder()
                .tenantId(tid)
                .applicationId(applicationId)
                .sourceRecordId(source.getId())
                .subjectType(BureauSubjectType.CONSUMER.name())
                .providerCode(providerCode)
                .providerReportRef(transactionId)
                .reportDate(reportDate)
                .score(score)
                .scoreType(firstNonBlank(str(data.get("scoreVersion")), str(data.get("scoreName"))))
                .qualityStatus("OK")
                .parserVersion(str(data.getOrDefault("parserVersion", EquifaxBureauAccountExtractor.PARSER_VERSION)))
                .normalizerVersion(NORMALIZER_VERSION)
                .tradelinesPresent(tradelinesPresent)
                .tradelineExtractionStatus(extractionStatus)
                .idempotencyKey(idempotencyKey)
                .metadata(reportMeta)
                .build());

        List<CiBureauTradeline> tradelines = new ArrayList<>();
        boolean persistTradelines = properties.getCanonicalization().getBureau().isPersistTradelines();

        if (tradelinesPresent && data.get("accounts") instanceof List<?> accounts) {
            int freshnessDays = properties.getCanonicalization().getBureau().getFreshnessDays();
            List<PendingTradeline> pending = new ArrayList<>();
            int idx = 0;
            for (Object acctObj : accounts) {
                if (!(acctObj instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> acct = (Map<String, Object>) raw;
                pending.add(buildPendingTradeline(tid, report, acct, idx++, freshnessDays, reportDate));
            }

            markDuplicates(pending);

            if (persistTradelines) {
                for (PendingTradeline p : pending) {
                    CiBureauTradeline saved = tradelineRepository.save(p.tradeline());
                    tradelines.add(saved);
                    persistPaymentHistory(saved, p.historyMonths(), p.paymentHistoryRaw());
                }
                persistDuplicateGroups(tid, report.getId(), tradelines, pending);
            } else {
                for (PendingTradeline p : pending) {
                    if (p.tradeline().getId() == null) {
                        p.tradeline().setId(UUID.randomUUID());
                    }
                    tradelines.add(p.tradeline());
                }
            }
        }

        if (persistTradelines) {
            persistInquiries(report.getId(), data);
            persistScoringElements(report.getId(), data);
            persistProviderSummary(report, data);
        }

        List<CiMetricResult> metrics = metricService.computeAndPersist(report, tradelines, data);
        return new NormalizationResult(report, tradelines, metrics, false);
    }

    private void persistInquiries(UUID reportId, Map<String, Object> data) {
        if (!(data.get("inquiries") instanceof List<?> inquiries)) {
            return;
        }
        for (Object inqObj : inquiries) {
            if (!(inqObj instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> inq = (Map<String, Object>) raw;
            inquiryRepository.save(CiBureauInquiry.builder()
                    .bureauReportId(reportId)
                    .inquiryDate(parseDate(str(inq.get("inquiryDate"))))
                    .memberName(str(inq.get("memberName")))
                    .purpose(str(inq.get("purpose")))
                    .amount(toBd(inq.get("amount")))
                    .inquiryTime(str(inq.get("inquiryTime")))
                    .sourceReference("report:" + reportId)
                    .metadata(Map.of())
                    .build());
        }
    }

    private void persistScoringElements(UUID reportId, Map<String, Object> data) {
        if (!(data.get("scoringElements") instanceof List<?> elements)) {
            return;
        }
        int seq = 0;
        for (Object elObj : elements) {
            if (!(elObj instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> el = (Map<String, Object>) raw;
            scoringElementRepository.save(CiBureauScoringElement.builder()
                    .bureauReportId(reportId)
                    .seqNo(seq++)
                    .code(str(el.get("code")))
                    .description(str(el.get("description")))
                    .build());
        }
    }

    private void persistProviderSummary(CiBureauReport report, Map<String, Object> data) {
        Map<String, String> accounts = stringMap(data.get("nativeAccountSummary"));
        Map<String, String> enquiry = stringMap(data.get("nativeEnquirySummary"));
        Map<String, String> recent = stringMap(data.get("nativeRecentActivities"));
        Map<String, String> other = stringMap(data.get("nativeOtherKeyInd"));
        String scoreName = firstNonBlank(str(data.get("scoreName")), str(data.get("scoreVersion")));
        CiBureauReportSummary summary = CiBureauReportSummary.builder()
                .bureauReportId(report.getId())
                .hitCode(str(data.get("hitCode")))
                .successCode(str(data.get("successCode")))
                .reportOrderNo(str(data.get("reportOrderNo")))
                .scoreName(scoreName)
                .accountCount(toInt(firstNonBlank(accounts.get("NoOfAccounts"), accounts.get("noOfAccounts"))))
                .activeAccountCount(toInt(accounts.get("NoOfActiveAccounts")))
                .writeoffCount(toInt(accounts.get("NoOfWriteOffs")))
                .totalPastDue(toBd(accounts.get("TotalPastDue")))
                .mostSevereStatus24m(firstNonBlank(accounts.get("MostSevereStatusWithIn24Months"),
                        accounts.get("MostSevereStatusWithin24Months")))
                .totalBalance(toBd(accounts.get("TotalBalanceAmount")))
                .totalSanction(toBd(accounts.get("TotalSanctionAmount")))
                .totalCreditLimit(toBd(accounts.get("TotalCreditLimit")))
                .totalMonthlyPayment(toBd(accounts.get("TotalMonthlyPaymentAmount")))
                .highestSanction(toBd(accounts.get("SingleHighestSanctionAmount")))
                .highestBalance(toBd(accounts.get("SingleHighestBalance")))
                .averageOpenBalance(toBd(accounts.get("AverageOpenBalance")))
                .ageOfOldestTradeMonths(toInt(other.get("AgeOfOldestTrade")))
                .openTradeCount(toInt(other.get("NumberOfOpenTrades")))
                .pastDueAccountCount(toInt(accounts.get("NoOfPastDueAccounts")))
                .zeroBalanceAccountCount(toInt(accounts.get("NoOfZeroBalanceAccounts")))
                .highestCredit(toBd(accounts.get("SingleHighestCredit")))
                .totalHighCredit(toBd(accounts.get("TotalHighCredit")))
                .enquiryTotal(toInt(enquiry.get("Total")))
                .enquiryPast30d(toInt(enquiry.get("Past30Days")))
                .enquiryPast12m(toInt(enquiry.get("Past12Months")))
                .enquiryPast24m(toInt(enquiry.get("Past24Months")))
                .enquiryRecentDate(parseDate(enquiry.get("Recent")))
                .recentAccountsOpened90d(toInt(recent.get("AccountsOpened")))
                .recentAccountsUpdated90d(toInt(recent.get("AccountsUpdated")))
                .recentAccountsDelinquent90d(toInt(firstNonBlank(
                        recent.get("AccountsDeliquent"), recent.get("AccountsDelinquent"))))
                .recentInquiries90d(toInt(recent.get("TotalInquiries")))
                .reportTime(str(data.get("reportTime")))
                .enquirySummaryPurpose(enquiry.get("Purpose"))
                .allLinesEverWritten(toBd(other.get("AllLinesEVERWritten")))
                .allLinesEverWritten9m(toBd(other.get("AllLinesEVERWrittenIn9Months")))
                .allLinesEverWritten6m(toBd(other.get("AllLinesEVERWrittenIn6Months")))
                .recentAccountNarrative(accounts.get("RecentAccount"))
                .oldestAccountNarrative(accounts.get("OldestAccount"))
                .build();
        reportSummaryRepository.save(summary);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> m) || m.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String v = String.valueOf(e.getValue()).trim();
            if (!v.isEmpty()) {
                out.put(String.valueOf(e.getKey()), v);
            }
        }
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private PendingTradeline buildPendingTradeline(
            UUID tenantId,
            CiBureauReport report,
            Map<String, Object> acct,
            int index,
            int freshnessDays,
            LocalDate reportDate) {

        String productCode = str(acct.get("AccountTypeCode"));
        String productDesc = str(acct.get("AccountType"));
        var tax = taxonomyService.resolve(report.getProviderCode(), productCode, productDesc);

        BigDecimal balance = toBd(acct.get("Balance"));
        String status = str(acct.get("AccountStatus"));
        Boolean openInd = parseOpen(acct.get("Open"));
        BigDecimal writtenOffAmt = toBd(acct.get("WrittenOffAmount"));
        BigDecimal settlementAmt = toBd(acct.get("SettlementAmount"));
        boolean writtenOff = writtenOffAmt != null && writtenOffAmt.compareTo(BigDecimal.ZERO) > 0
                || statusContains(status, "WRITE");
        boolean settled = settlementAmt != null && settlementAmt.compareTo(BigDecimal.ZERO) > 0
                || statusContains(status, "SETTLE");

        LocalDate lastReported = parseDate(str(acct.get("DateReported")));
        var live = liveAccountClassifier.classify(
                new BureauLiveAccountClassifier.LiveInput(
                        status, balance, writtenOff, settled, lastReported, openInd),
                freshnessDays,
                reportDate != null ? reportDate : LocalDate.now());

        String hash = str(acct.get("AccountNumberHash"));
        String last4 = str(acct.get("AccountNumberLast4"));
        String providerRef = hash != null ? hash : ("idx-" + index);
        if (acct.get("seq") != null) {
            providerRef = providerRef + "|seq=" + acct.get("seq");
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (last4 != null) {
            meta.put("accountNumberLast4", last4);
        }
        meta.put("liveReason", live.reason());
        if (live.qualityFlag() != null) {
            meta.put("liveQualityFlag", live.qualityFlag());
        }
        meta.put("taxonomyKnown", tax.known());

        Boolean secured = tax.secured();
        String collateralType = str(acct.get("CollateralType"));
        BigDecimal collateralValue = toBd(acct.get("CollateralValue"));
        if (collateralType != null && collateralValue != null && collateralValue.compareTo(BigDecimal.ZERO) > 0) {
            if (!Boolean.TRUE.equals(secured)) {
                meta.put("securedOverride", "COLLATERAL_EVIDENCE");
            }
            secured = true;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> historyMonths = acct.get("HistoryMonths") instanceof List<?> hl
                ? (List<Map<String, Object>>) hl : List.of();
        Boolean wilful = parseTriStateYesNo(str(acct.get("WilfulDefault")));
        if (wilful == null) {
            wilful = wilfulFromHistory(historyMonths);
        }

        String quality = "OK";
        if ("WARNING".equals(live.qualityFlag())) {
            quality = "WARNING";
        }
        if (!tax.known()) {
            quality = "PARTIAL";
        }

        String paymentHistoryRaw = str(acct.get("PaymentHistory"));
        if (historyMonths.isEmpty() && paymentHistoryRaw != null) {
            quality = "PARTIAL";
            meta.put("paymentHistoryRawPresent", true);
        }

        CiBureauTradeline tl = CiBureauTradeline.builder()
                .tenantId(tenantId)
                .bureauReportId(report.getId())
                .providerTradelineRef(providerRef)
                .lenderName(str(acct.get("MemberName")))
                .accountTypeRaw(productDesc != null ? productDesc : productCode)
                .productCategory(tax.category().name())
                .ownershipType(str(acct.get("Ownership")))
                .secured(secured)
                .revolving(tax.revolving())
                .openedDate(parseDate(str(acct.get("DateOpened"))))
                .closedDate(parseDate(str(acct.get("DateClosed"))))
                .lastReportedDate(lastReported)
                .sanctionedAmount(toBd(acct.get("SanctionAmount")))
                .highCredit(firstBd(acct, "HighCredit", "CreditLimit"))
                .currentBalance(balance)
                .overdueAmount(toBd(acct.get("PastDueAmount")))
                .emiAmount(firstBd(acct, "InstallmentAmount", "EMI"))
                .interestRate(toBd(acct.get("InterestRate")))
                .tenureMonths(toInt(acct.get("RepaymentTenure")))
                .assetClassification(str(acct.get("AssetClassification")))
                .suitFiled(parseTriStateYesNo(str(acct.get("SuitFiledStatus"))))
                .wilfulDefault(wilful)
                .writtenOffAmount(writtenOffAmt)
                .settlementAmount(settlementAmt)
                .settled(settled)
                .writtenOff(writtenOff)
                .collateralType(collateralType)
                .collateralValue(collateralValue)
                .lastPaymentAmount(toBd(acct.get("LastPayment")))
                .lastPaymentDate(parseDate(str(acct.get("LastPaymentDate"))))
                .termFrequency(str(acct.get("TermFrequency")))
                .disputeCode(str(acct.get("DisputeCode")))
                .closureReason(str(acct.get("Reason")))
                .accountStatus(status)
                .dataQualityStatus(quality)
                .isLive(live.live())
                .liveDefinitionVersion(live.definitionVersion())
                .sourceReference("account[" + index + "]")
                .metadata(meta)
                .build();

        return new PendingTradeline(tl, historyMonths, paymentHistoryRaw, index);
    }

    private void markDuplicates(List<PendingTradeline> pending) {
        Map<String, PendingTradeline> byRef = new HashMap<>();
        Map<String, PendingTradeline> byComposite = new HashMap<>();
        for (PendingTradeline p : pending) {
            CiBureauTradeline t = p.tradeline();
            String ref = t.getProviderTradelineRef();
            if (ref != null && byRef.containsKey(ref)) {
                PendingTradeline first = byRef.get(ref);
                t.setDuplicateOfTradelineId(null); // will set after persist via index
                p.markDuplicateOf(first, "PROVIDER_TRADELINE_REF");
                continue;
            }
            if (ref != null) {
                byRef.put(ref, p);
            }
            String composite = compositeKey(t);
            if (composite != null && byComposite.containsKey(composite)) {
                PendingTradeline first = byComposite.get(composite);
                p.markDuplicateOf(first, "LENDER_OPENED_SANCTION_TYPE");
                continue;
            }
            if (composite != null) {
                byComposite.put(composite, p);
            }
        }
    }

    private void persistDuplicateGroups(
            UUID tenantId, UUID reportId, List<CiBureauTradeline> saved, List<PendingTradeline> pending) {
        // After save, wire duplicate_of ids and groups
        Map<Integer, UUID> indexToId = new HashMap<>();
        for (int i = 0; i < pending.size() && i < saved.size(); i++) {
            indexToId.put(pending.get(i).index(), saved.get(i).getId());
        }
        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (PendingTradeline p : pending) {
            if (p.duplicateOfIndex() != null) {
                groups.computeIfAbsent(p.duplicateOfIndex(), k -> new ArrayList<>()).add(p.index());
                UUID selectedId = indexToId.get(p.duplicateOfIndex());
                UUID dupId = indexToId.get(p.index());
                if (selectedId != null && dupId != null) {
                    CiBureauTradeline dup = saved.stream().filter(t -> t.getId().equals(dupId)).findFirst().orElse(null);
                    if (dup != null) {
                        dup.setDuplicateOfTradelineId(selectedId);
                        tradelineRepository.save(dup);
                    }
                }
            }
        }
        for (Map.Entry<Integer, List<Integer>> e : groups.entrySet()) {
            UUID selectedId = indexToId.get(e.getKey());
            List<Object> members = new ArrayList<>();
            members.add(selectedId != null ? selectedId.toString() : null);
            for (Integer idx : e.getValue()) {
                UUID id = indexToId.get(idx);
                if (id != null) {
                    members.add(id.toString());
                }
            }
            String basis = pending.stream()
                    .filter(p -> Objects.equals(p.index(), e.getValue().get(0)))
                    .map(PendingTradeline::duplicateBasis)
                    .findFirst().orElse("COMPOSITE");
            CiBureauDuplicateGroup group = duplicateGroupRepository.save(CiBureauDuplicateGroup.builder()
                    .tenantId(tenantId)
                    .bureauReportId(reportId)
                    .selectedTradelineId(selectedId)
                    .matchingBasis(basis != null ? basis : "COMPOSITE")
                    .confidence(new BigDecimal("0.9000"))
                    .memberTradelineIds(members)
                    .metadata(Map.of())
                    .build());
            for (Object mid : members) {
                if (mid == null) {
                    continue;
                }
                UUID tid = UUID.fromString(String.valueOf(mid));
                saved.stream().filter(t -> t.getId().equals(tid)).findFirst().ifPresent(t -> {
                    t.setDuplicateGroupId(group.getId());
                    tradelineRepository.save(t);
                });
            }
        }
    }

    private void persistPaymentHistory(
            CiBureauTradeline tradeline,
            List<Map<String, Object>> historyMonths,
            String paymentHistoryRaw) {
        if (historyMonths != null && !historyMonths.isEmpty()) {
            for (Map<String, Object> m : historyMonths) {
                LocalDate month = EquifaxBureauAccountExtractor.parseHistoryMonthKey(str(m.get("key")));
                if (month == null) {
                    continue;
                }
                Integer dpd = toInt(m.get("DaysPastDue"));
                String rawStatus = str(m.get("PaymentStatus"));
                paymentHistoryRepository.save(CiBureauPaymentHistory.builder()
                        .tradelineId(tradeline.getId())
                        .month(month)
                        .dpd(dpd)
                        .status(dpd != null && dpd > 0 ? "DPD" : "CURRENT")
                        .providerRawStatus(rawStatus)
                        .estimated(false)
                        .suitFiledStatus(blankToNull(str(m.get("SuitFiledStatus"))))
                        .assetClassificationStatus(blankToNull(str(m.get("AssetClassificationStatus"))))
                        .sourceReference("History48Months")
                        .build());
            }
            return;
        }
        // Coded string present but not reliably parseable — store flag in metadata only (already done)
        if (paymentHistoryRaw != null && !paymentHistoryRaw.isBlank()) {
            Map<String, Object> meta = new LinkedHashMap<>(
                    tradeline.getMetadata() != null ? tradeline.getMetadata() : Map.of());
            meta.put("paymentHistoryRawLength", paymentHistoryRaw.length());
            meta.put("paymentHistoryParse", "SKIPPED_UNRELIABLE");
            tradeline.setMetadata(meta);
            tradeline.setDataQualityStatus("PARTIAL");
            tradelineRepository.save(tradeline);
        }
    }

    private static String buildIdempotencyKey(
            UUID tenantId, UUID applicationId, String provider, String txnId, Map<String, Object> data) {
        int accountCount = 0;
        if (data.get("accounts") instanceof List<?> list) {
            accountCount = list.size();
        }
        Object score = data.get("creditScore");
        return String.join("|",
                tenantId != null ? tenantId.toString() : "",
                applicationId != null ? applicationId.toString() : "",
                provider != null ? provider : "",
                txnId != null ? txnId : "",
                String.valueOf(score),
                String.valueOf(accountCount));
    }

    private static Map<String, Object> checksumPayload(Map<String, Object> data, String txnId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", txnId);
        payload.put("creditScore", data.get("creditScore"));
        int accountCount = data.get("accounts") instanceof List<?> list ? list.size() : 0;
        payload.put("accountCount", accountCount);
        payload.put("tradelineExtractionStatus", data.get("tradelineExtractionStatus"));
        return payload;
    }

    private static String compositeKey(CiBureauTradeline t) {
        if (t.getLenderName() == null || t.getOpenedDate() == null || t.getSanctionedAmount() == null
                || t.getAccountTypeRaw() == null) {
            return null;
        }
        return String.join("|",
                t.getLenderName().toUpperCase(Locale.ROOT).trim(),
                t.getOpenedDate().toString(),
                t.getSanctionedAmount().toPlainString(),
                t.getAccountTypeRaw().toUpperCase(Locale.ROOT).trim());
    }

    private static Boolean parseOpen(Object open) {
        if (open == null) {
            return null;
        }
        String s = String.valueOf(open).trim();
        if (s.equalsIgnoreCase("Yes") || s.equalsIgnoreCase("Y") || s.equalsIgnoreCase("true") || s.equals("1")) {
            return true;
        }
        if (s.equalsIgnoreCase("No") || s.equalsIgnoreCase("N") || s.equalsIgnoreCase("false") || s.equals("0")) {
            return false;
        }
        return null;
    }

    private static boolean statusContains(String status, String token) {
        return status != null && status.toUpperCase(Locale.ROOT).contains(token);
    }

    private static boolean isYes(String s) {
        return s != null && (s.equalsIgnoreCase("Yes") || s.equalsIgnoreCase("Y") || s.equalsIgnoreCase("true"));
    }

    /** Yes → true, No → false, missing / * / unknown → null. Never invent false. */
    private static Boolean parseTriStateYesNo(String s) {
        if (s == null || s.isBlank() || "*".equals(s.trim())) {
            return null;
        }
        if (isYes(s)) {
            return true;
        }
        if (s.equalsIgnoreCase("No") || s.equalsIgnoreCase("N") || s.equalsIgnoreCase("false")) {
            return false;
        }
        return null;
    }

    private static Boolean wilfulFromHistory(List<Map<String, Object>> historyMonths) {
        if (historyMonths == null || historyMonths.isEmpty()) {
            return null;
        }
        boolean sawToken = false;
        for (Map<String, Object> m : historyMonths) {
            String st = str(m.get("PaymentStatus"));
            if (st == null) {
                continue;
            }
            String u = st.toUpperCase(Locale.ROOT);
            if (u.contains("WDF") || u.contains("WILFUL")) {
                sawToken = true;
                if (!u.contains("N") && !u.equals("000")) {
                    return true;
                }
            }
        }
        return sawToken ? false : null;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank() || "*".equals(s.trim())) {
            return s != null && "*".equals(s.trim()) ? "*" : null;
        }
        return s;
    }

    private static LocalDate parseDate(String raw) {
        return EquifaxBureauAccountExtractor.parseFlexibleDate(raw);
    }

    private static BigDecimal firstBd(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            BigDecimal v = toBd(m.get(k));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }

    private static final class PendingTradeline {
        private final CiBureauTradeline tradeline;
        private final List<Map<String, Object>> historyMonths;
        private final String paymentHistoryRaw;
        private final int index;
        private Integer duplicateOfIndex;
        private String duplicateBasis;

        PendingTradeline(
                CiBureauTradeline tradeline,
                List<Map<String, Object>> historyMonths,
                String paymentHistoryRaw,
                int index) {
            this.tradeline = tradeline;
            this.historyMonths = historyMonths;
            this.paymentHistoryRaw = paymentHistoryRaw;
            this.index = index;
        }

        void markDuplicateOf(PendingTradeline first, String basis) {
            this.duplicateOfIndex = first.index;
            this.duplicateBasis = basis;
        }

        CiBureauTradeline tradeline() {
            return tradeline;
        }

        List<Map<String, Object>> historyMonths() {
            return historyMonths;
        }

        String paymentHistoryRaw() {
            return paymentHistoryRaw;
        }

        int index() {
            return index;
        }

        Integer duplicateOfIndex() {
            return duplicateOfIndex;
        }

        String duplicateBasis() {
            return duplicateBasis;
        }
    }
}
