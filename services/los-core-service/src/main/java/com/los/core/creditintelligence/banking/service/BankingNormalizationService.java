package com.los.core.creditintelligence.banking.service;

import com.los.core.creditintelligence.banking.domain.BankAccountType;
import com.los.core.creditintelligence.banking.domain.BankingConstants;
import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.domain.CiBankDuplicateGroup;
import com.los.core.creditintelligence.banking.domain.CiBankRecurringObligation;
import com.los.core.creditintelligence.banking.domain.CiBankStatementQuality;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.CiBankTransactionClassification;
import com.los.core.creditintelligence.core.domain.CiEvidenceGroup;
import com.los.core.creditintelligence.banking.domain.OwnershipMatchStatus;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.domain.TxnDirection;
import com.los.core.creditintelligence.banking.domain.TxnMode;
import com.los.core.creditintelligence.banking.repository.CiBankAccountRepository;
import com.los.core.creditintelligence.banking.repository.CiBankDuplicateGroupRepository;
import com.los.core.creditintelligence.banking.repository.CiBankRecurringObligationRepository;
import com.los.core.creditintelligence.banking.repository.CiBankStatementQualityRepository;
import com.los.core.creditintelligence.banking.repository.CiBankTransactionClassificationRepository;
import com.los.core.creditintelligence.banking.repository.CiBankTransactionRepository;
import com.los.core.creditintelligence.core.repository.CiEvidenceGroupRepository;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.support.ContentHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Normalize AA summary / OCR extract / explicit txn payload into accounts + transactions.
 * Idempotent on tenant|app|provider|consentOrDocRef|checksum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankingNormalizationService {

    public static final String NORMALIZER_VERSION = BankingConstants.NORMALIZER_VERSION;

    private final CiBankAccountRepository accountRepository;
    private final CiBankTransactionRepository transactionRepository;
    private final CiBankTransactionClassificationRepository classificationRepository;
    private final CiBankRecurringObligationRepository obligationRepository;
    private final CiBankDuplicateGroupRepository duplicateGroupRepository;
    private final CiBankStatementQualityRepository qualityRepository;
    private final CiEvidenceGroupRepository evidenceGroupRepository;
    private final SourceRegistryService sourceRegistryService;
    private final BankTransactionClassifier classifier;
    private final BankDuplicateDetector duplicateDetector;
    private final BankStatementIntegrityChecker integrityChecker;
    private final BankOwnershipResolver ownershipResolver;
    private final BankSourcePrecedence sourcePrecedence;
    private final BankingMetricService metricService;
    private final ContentHasher contentHasher;
    private final CreditIntelligenceProperties properties;

    public record NormalizationResult(
            List<CiBankAccount> accounts,
            List<CiBankTransaction> transactions,
            List<CiMetricResult> metrics,
            boolean alreadyExisted) {
    }

    public record IncomingTxn(
            LocalDate date,
            BigDecimal amount,
            TxnDirection direction,
            String narration,
            TxnMode mode,
            BigDecimal balanceAfter,
            String txnId,
            String utr,
            String accountKey) {
    }

    @Transactional
    public NormalizationResult normalizeFromAaSummary(
            UUID applicationId,
            UUID tenantId,
            Map<String, Object> summary,
            String providerCode,
            String consentRef,
            String borrowerName) {
        return normalize(applicationId, tenantId, SourceType.ACCOUNT_AGGREGATOR.name(),
                providerCode != null ? providerCode : "AA",
                consentRef,
                "aa_consent:" + (consentRef != null ? consentRef : "unknown"),
                summary,
                extractTxnsFromSummary(summary),
                borrowerName,
                BankingConstants.PARSER_VERSION_AA);
    }

    @Transactional
    public NormalizationResult normalizeFromOcr(
            UUID applicationId,
            UUID tenantId,
            Map<String, Object> extracted,
            UUID documentId,
            String borrowerName) {
        Map<String, Object> payload = extracted != null ? extracted : Map.of();
        List<IncomingTxn> txns = extractTxnsFromOcr(payload);
        return normalize(applicationId, tenantId, SourceType.BANK_STATEMENT.name(),
                "OCR",
                documentId != null ? documentId.toString() : "unknown",
                documentId != null ? "document:" + documentId : "ocr:BANK_STATEMENT:" + applicationId,
                payload,
                txns,
                borrowerName,
                BankingConstants.PARSER_VERSION_OCR);
    }

    @Transactional
    public NormalizationResult normalize(
            UUID applicationId,
            UUID tenantId,
            String sourceType,
            String providerCode,
            String consentOrDocRef,
            String artifactRef,
            Map<String, Object> payload,
            List<IncomingTxn> incomingTxns,
            String borrowerName,
            String parserVersion) {

        UUID tid = tenantId != null ? tenantId : properties.getDefaultTenantId();
        Map<String, Object> data = payload != null ? payload : Map.of();
        String checksum = contentHasher.hashMap(checksumPayload(data, consentOrDocRef));
        String idempotencyKey = String.join("|",
                tid.toString(),
                applicationId.toString(),
                providerCode != null ? providerCode : "",
                consentOrDocRef != null ? consentOrDocRef : "",
                checksum);

        Optional<CiBankAccount> existing = accountRepository
                .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, idempotencyKey);
        if (existing.isPresent()) {
            List<CiBankAccount> accounts = accountRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
            List<CiMetricResult> metrics = metricService.findForApplication(applicationId);
            return new NormalizationResult(accounts, List.of(), metrics, true);
        }

        var source = sourceRegistryService.createOrGet(
                tid, applicationId, sourceType, providerCode,
                "UNDERWRITING",
                "bank-canon-" + idempotencyKey,
                Map.of(
                        "consentOrDocRef", consentOrDocRef != null ? consentOrDocRef : "",
                        "parserVersion", parserVersion,
                        "sourcePrecedence", sourcePrecedence.resolve(sourceType, providerCode).name(),
                        "referenceOnly", true),
                "BankingNormalizationService");
        sourceRegistryService.createArtifact(source.getId(), artifactRef, "application/json", checksum);

        boolean persistTxns = bankingCfg().isPersistTransactions();
        List<Map<String, Object>> accountMaps = extractAccountMaps(data);
        if (accountMaps.isEmpty()) {
            accountMaps = List.of(Map.of("type", "UNKNOWN", "bank", "UNKNOWN"));
        }

        List<CiBankAccount> savedAccounts = new ArrayList<>();
        List<CiBankTransaction> allTxns = new ArrayList<>();
        Map<String, CiBankAccount> accountByKey = new HashMap<>();

        int idx = 0;
        for (Map<String, Object> am : accountMaps) {
            String accountKey = accountKey(am, idx);
            String accountIdem = idempotencyKey + "|acct|" + accountKey;
            Optional<CiBankAccount> already = accountRepository
                    .findByTenantIdAndApplicationIdAndIdempotencyKey(tid, applicationId, accountIdem);
            if (already.isPresent()) {
                savedAccounts.add(already.get());
                accountByKey.put(accountKey, already.get());
                idx++;
                continue;
            }

            BankAccountType type = mapAccountType(str(am.get("type")));
            String holder = str(am.get("holderName"));
            if (holder == null || holder.isBlank()) {
                holder = str(am.get("accountHolder"));
            }
            var ownership = ownershipResolver.resolve(holder, borrowerName);
            boolean aggregationEligible = isAggregationEligible(type, ownership.status());

            String rawAcct = str(am.get("accountNumber"));
            if (rawAcct == null || rawAcct.isBlank()) {
                rawAcct = str(am.get("maskedAccNumber"));
            }
            String hash = hashAccount(rawAcct);
            String last4 = last4(rawAcct);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("checksum", checksum);
            meta.put("accountKey", accountKey);
            if (am.get("avgMonthlyBalance") != null) {
                meta.put("avgMonthlyBalance", am.get("avgMonthlyBalance"));
            }
            if (am.get("balance") != null) {
                meta.put("summaryBalance", am.get("balance"));
            }
            // Never store AA secrets / full account numbers
            meta.put("sourceRank", sourcePrecedence.resolve(sourceType, providerCode).name());

            CiBankAccount account = accountRepository.save(CiBankAccount.builder()
                    .tenantId(tid)
                    .applicationId(applicationId)
                    .sourceRecordId(source.getId())
                    .providerCode(providerCode)
                    .institutionName(str(am.get("bank")))
                    .accountNumberHash(hash)
                    .accountNumberLast4(last4)
                    .accountType(type.name())
                    .holderName(holder)
                    .holderNameMatchStatus(ownership.status().name())
                    .holderNameMatchScore(ownership.score())
                    .closingBalance(toBd(am.get("balance")))
                    .sanctionedLimit(toBd(am.get("sanctionedLimit")))
                    .drawingPower(toBd(am.get("drawingPower")))
                    .overdraftLimit(toBd(am.get("overdraftLimit")))
                    .aggregationEligible(aggregationEligible)
                    .parserVersion(parserVersion)
                    .normalizerVersion(NORMALIZER_VERSION)
                    .idempotencyKey(accountIdem)
                    .sourceReference(artifactRef)
                    .metadata(meta)
                    .build());
            savedAccounts.add(account);
            accountByKey.put(accountKey, account);
            // Also map by index and bank+type for txn routing
            accountByKey.put(String.valueOf(idx), account);
            accountByKey.put(type.name() + "|" + str(am.get("bank")), account);
            idx++;
        }

        if (persistTxns && incomingTxns != null && !incomingTxns.isEmpty()) {
            // Default account for txns without key
            CiBankAccount defaultAcct = savedAccounts.isEmpty() ? null : savedAccounts.get(0);
            Map<UUID, List<CiBankTransaction>> byAccount = new LinkedHashMap<>();

            for (IncomingTxn it : incomingTxns) {
                CiBankAccount acct = resolveAccount(accountByKey, it.accountKey(), defaultAcct);
                if (acct == null) {
                    continue;
                }
                TxnDirection dir = it.direction() != null ? it.direction() : TxnDirection.CREDIT;
                TxnMode mode = it.mode() != null ? it.mode() : TxnMode.UNKNOWN;
                var cls = classifier.classify(it.narration(), dir, mode);

                CiBankTransaction txn = CiBankTransaction.builder()
                        .tenantId(tid)
                        .bankAccountId(acct.getId())
                        .sourceRecordId(source.getId())
                        .providerTransactionId(it.txnId())
                        .transactionDate(it.date() != null ? it.date() : LocalDate.now())
                        .descriptionRaw(it.narration())
                        .descriptionNormalized(BankTransactionClassifier.normalize(it.narration()))
                        .direction(dir.name())
                        .amount(it.amount() != null ? it.amount() : BigDecimal.ZERO)
                        .balanceAfter(it.balanceAfter())
                        .mode(mode.name())
                        .utrReference(it.utr())
                        .category(cls.category().name())
                        .cashFlowClass(cls.cashFlowClass().name())
                        .emiFlag(cls.emiFlag())
                        .bounceFlag(cls.bounceFlag())
                        .returnFlag(cls.returnFlag())
                        .cashFlag(cls.cashFlag())
                        .selfTransferFlag(cls.selfTransferFlag())
                        .lenderFlag(cls.lenderFlag())
                        .taxPaymentFlag(cls.taxPaymentFlag())
                        .businessReceiptFlag(cls.businessReceiptFlag())
                        .businessPaymentFlag(cls.businessPaymentFlag())
                        .confidence(cls.confidence())
                        .classificationMethod(cls.method())
                        .classifierVersion(cls.classifierVersion())
                        .duplicateStatus("UNIQUE")
                        .sourceReference(artifactRef)
                        .metadata(Map.of("classifierEvidence", cls.evidence()))
                        .build();
                byAccount.computeIfAbsent(acct.getId(), k -> new ArrayList<>()).add(txn);
            }

            for (Map.Entry<UUID, List<CiBankTransaction>> e : byAccount.entrySet()) {
                List<CiBankTransaction> batch = e.getValue();
                // Save first to get IDs, then detect duplicates
                List<CiBankTransaction> saved = transactionRepository.saveAll(batch);
                var groups = duplicateDetector.detectIndexed(saved);
                for (var g : groups) {
                    int canonicalIdx = g.canonicalIndex();
                    CiBankTransaction canonical = saved.get(canonicalIdx);
                    List<Object> memberIds = new ArrayList<>();
                    for (Integer mi : g.memberIndexes()) {
                        CiBankTransaction m = saved.get(mi);
                        memberIds.add(m.getId().toString());
                        if (!mi.equals(canonicalIdx)) {
                            m.setDuplicateStatus("DUPLICATE");
                            m.setDuplicateOfTransactionId(canonical.getId());
                        }
                    }
                    CiBankDuplicateGroup dg = duplicateGroupRepository.save(CiBankDuplicateGroup.builder()
                            .tenantId(tid)
                            .bankAccountId(e.getKey())
                            .canonicalTransactionId(canonical.getId())
                            .matchingBasis(g.matchingBasis())
                            .confidence(g.confidence())
                            .memberTransactionIds(memberIds)
                            .build());
                    for (Integer mi : g.memberIndexes()) {
                        saved.get(mi).setDuplicateGroupId(dg.getId());
                    }
                    transactionRepository.saveAll(saved);
                }

                for (CiBankTransaction t : saved) {
                    classificationRepository.save(CiBankTransactionClassification.builder()
                            .transactionId(t.getId())
                            .category(t.getCategory())
                            .cashFlowClass(t.getCashFlowClass())
                            .method(t.getClassificationMethod() != null
                                    ? t.getClassificationMethod()
                                    : BankingConstants.BANK_TXN_CLASSIFIER_V1)
                            .classifierVersion(t.getClassifierVersion() != null
                                    ? t.getClassifierVersion()
                                    : BankingConstants.BANK_TXN_CLASSIFIER_V1)
                            .confidence(t.getConfidence())
                            .evidence(t.getMetadata() != null && t.getMetadata().get("classifierEvidence") instanceof Map<?, ?> ev
                                    ? castMap(ev) : Map.of())
                            .build());
                }

                LocalDate from = saved.stream().map(CiBankTransaction::getTransactionDate)
                        .filter(d -> d != null).min(LocalDate::compareTo).orElse(null);
                LocalDate to = saved.stream().map(CiBankTransaction::getTransactionDate)
                        .filter(d -> d != null).max(LocalDate::compareTo).orElse(null);
                CiBankStatementQuality quality = integrityChecker.check(
                        tid, applicationId, e.getKey(), source.getId(), saved, from, to);
                qualityRepository.save(quality);
                allTxns.addAll(saved);
            }
        } else {
            // No transactions — still record quality as NO_TRANSACTIONS for each account
            for (CiBankAccount acct : savedAccounts) {
                qualityRepository.save(integrityChecker.check(
                        tid, applicationId, acct.getId(), source.getId(), List.of(), null, null));
            }
        }

        List<CiMetricResult> metrics = metricService.computeAndPersist(
                tid, applicationId, source.getId(), savedAccounts, allTxns, data, LocalDate.now());

        log.info("Banking canonicalization normalized applicationId={} accounts={} txns={}",
                applicationId, savedAccounts.size(), allTxns.size());
        return new NormalizationResult(savedAccounts, allTxns, metrics, false);
    }

    @SuppressWarnings("unchecked")
    public List<IncomingTxn> extractTxnsFromSummary(Map<String, Object> summary) {
        if (summary == null) {
            return List.of();
        }
        Object raw = summary.get("transactions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<IncomingTxn> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) m;
            out.add(toIncoming(map));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<IncomingTxn> extractTxnsFromOcr(Map<String, Object> extracted) {
        Object data = extracted.get("extractedData");
        Map<String, Object> inner = data instanceof Map<?, ?> m ? (Map<String, Object>) m : extracted;
        Object raw = inner.get("transactions");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<IncomingTxn> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add(toIncoming((Map<String, Object>) m));
            }
        }
        return out;
    }

    private IncomingTxn toIncoming(Map<String, Object> map) {
        String dirStr = str(map.get("type"));
        if (dirStr == null || dirStr.isBlank()) {
            dirStr = str(map.get("direction"));
        }
        TxnDirection dir = "DEBIT".equalsIgnoreCase(dirStr) ? TxnDirection.DEBIT : TxnDirection.CREDIT;
        TxnMode mode = mapMode(str(map.get("mode")));
        LocalDate date = parseDate(map.get("date"));
        return new IncomingTxn(
                date,
                toBd(map.get("amount")),
                dir,
                str(map.get("narration")) != null ? str(map.get("narration")) : str(map.get("description")),
                mode,
                toBd(map.get("balance")),
                str(map.get("txnId")) != null ? str(map.get("txnId")) : str(map.get("transactionId")),
                str(map.get("utr")),
                str(map.get("accountKey")) != null ? str(map.get("accountKey")) : str(map.get("account")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAccountMaps(Map<String, Object> data) {
        Object accounts = data.get("accounts");
        if (accounts instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        Object extracted = data.get("extractedData");
        if (extracted instanceof Map<?, ?> em) {
            Map<String, Object> inner = (Map<String, Object>) em;
            // Single account from OCR
            if (inner.containsKey("accountNumber") || inner.containsKey("bankName")) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("type", inner.getOrDefault("accountType", "CURRENT"));
                one.put("bank", inner.getOrDefault("bankName", "UNKNOWN"));
                one.put("accountNumber", inner.get("accountNumber"));
                one.put("holderName", inner.get("accountHolder"));
                one.put("balance", inner.get("closingBalance"));
                one.put("avgMonthlyBalance", inner.get("averageBalance"));
                return List.of(one);
            }
        }
        return List.of();
    }

    private boolean isAggregationEligible(BankAccountType type, OwnershipMatchStatus ownership) {
        if (ownership == OwnershipMatchStatus.MISMATCH) {
            return false;
        }
        return type == BankAccountType.CURRENT
                || type == BankAccountType.SAVINGS
                || type == BankAccountType.UNKNOWN
                || type == BankAccountType.OTHER;
    }

    private CiBankAccount resolveAccount(
            Map<String, CiBankAccount> byKey, String key, CiBankAccount fallback) {
        if (key != null && byKey.containsKey(key)) {
            return byKey.get(key);
        }
        return fallback;
    }

    private Map<String, Object> checksumPayload(Map<String, Object> data, String ref) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ref", ref);
        m.put("accountCount", data.get("accountCount"));
        m.put("accounts", data.get("accounts"));
        Object txns = data.get("transactions");
        if (txns instanceof List<?> list) {
            m.put("txnCount", list.size());
        }
        m.put("totalBalance", data.get("totalBalance"));
        return m;
    }

    private static BankAccountType mapAccountType(String raw) {
        if (raw == null || raw.isBlank()) {
            return BankAccountType.UNKNOWN;
        }
        String u = raw.toUpperCase(Locale.ROOT);
        if (u.contains("SAVING")) {
            return BankAccountType.SAVINGS;
        }
        if (u.contains("CURRENT") || u.contains("CA")) {
            return BankAccountType.CURRENT;
        }
        if (u.contains("OVERDRAFT") || u.equals("OD")) {
            return BankAccountType.OVERDRAFT;
        }
        if (u.contains("CASH") && u.contains("CREDIT") || u.equals("CC")) {
            return BankAccountType.CASH_CREDIT;
        }
        if (u.contains("LOAN")) {
            return BankAccountType.LOAN;
        }
        try {
            return BankAccountType.valueOf(u);
        } catch (Exception e) {
            return BankAccountType.UNKNOWN;
        }
    }

    private static TxnMode mapMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return TxnMode.UNKNOWN;
        }
        try {
            return TxnMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            String u = raw.toUpperCase(Locale.ROOT);
            if (u.contains("UPI")) return TxnMode.UPI;
            if (u.contains("NEFT")) return TxnMode.NEFT;
            if (u.contains("RTGS")) return TxnMode.RTGS;
            if (u.contains("IMPS")) return TxnMode.IMPS;
            if (u.contains("NACH")) return TxnMode.NACH;
            if (u.contains("CHEQUE") || u.contains("CHQ")) return TxnMode.CHEQUE;
            if (u.contains("CASH")) return TxnMode.CASH;
            return TxnMode.OTHER;
        }
    }

    private static String accountKey(Map<String, Object> am, int idx) {
        String last4 = last4(str(am.get("accountNumber")));
        if (last4 != null) {
            return str(am.get("bank")) + "|" + last4;
        }
        return str(am.get("type")) + "|" + str(am.get("bank")) + "|" + idx;
    }

    private static String hashAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accountNumber.replaceAll("\\s", "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String last4(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String digits = accountNumber.replaceAll("\\D", "");
        if (digits.length() < 4) {
            String cleaned = accountNumber.replaceAll("[^A-Za-z0-9]", "");
            return cleaned.length() >= 4 ? cleaned.substring(cleaned.length() - 4) : cleaned;
        }
        return digits.substring(digits.length() - 4);
    }

    private static LocalDate parseDate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDate ld) {
            return ld;
        }
        String s = String.valueOf(raw).trim();
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length())));
        } catch (Exception e) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private CreditIntelligenceProperties.Canonicalization.Banking bankingCfg() {
        return properties.getCanonicalization().getBanking();
    }
}
