package com.billiontech.bankstatement.service;

import com.billiontech.bankstatement.event.BankStatementEventPublisher;
import com.billiontech.bankstatement.exception.ResourceNotFoundException;
import com.billiontech.bankstatement.exception.StatementProcessingException;
import com.billiontech.bankstatement.model.dto.response.*;
import com.billiontech.bankstatement.model.entity.*;
import com.billiontech.bankstatement.model.enums.ParsingStatus;
import com.billiontech.bankstatement.repository.*;
import com.billiontech.bankstatement.service.analysis.AnalysisEngine;
import com.billiontech.bankstatement.service.categorization.TransactionCategorizer;
import com.billiontech.bankstatement.service.extraction.*;
import com.billiontech.bankstatement.service.tamper.TamperDetectionService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankStatementService {

    private final BankStatementRepository statementRepository;
    private final BankTransactionRepository transactionRepository;
    private final StatementAnalysisRepository analysisRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final ExtractionEngine extractionEngine;
    private final TransactionCategorizer categorizer;
    private final AnalysisEngine analysisEngine;
    private final TamperDetectionService tamperDetectionService;
    private final BankStatementEventPublisher eventPublisher;
    private final MinioClient minioClient;
    private final AsyncAnalysisService asyncAnalysisService;

    @Value("${minio.bucket-name:los-bank-statements}")
    private String bucketName;

    @Transactional
    public UploadResponse uploadAndProcess(MultipartFile file, String password, String applicationId, String uploadedBy) {
        // Save initial record
        BankStatement statement = BankStatement.builder()
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .parsingStatus(ParsingStatus.UPLOADED)
                .applicationId(applicationId)
                .uploadedBy(uploadedBy)
                .build();
        statement = statementRepository.save(statement);

        try {
            // Upload to MinIO
            String filePath = uploadToMinio(file, statement.getId());
            statement.setFilePath(filePath);
            statement.setFileHash(computeHash(file));

            // Extract
            statement.setParsingStatus(ParsingStatus.PROCESSING);
            statementRepository.save(statement);

            ParsedStatement parsed = extractionEngine.extract(file, password);

            // Update statement metadata
            statement.setAccountHolderName(parsed.getAccountHolderName());
            statement.setAccountNumberMasked(parsed.getAccountNumber());
            statement.setBankName(parsed.getBankName());
            statement.setBankCode(parsed.getBankCode());
            statement.setIfscCode(parsed.getIfscCode());
            statement.setBranchName(parsed.getBranchName());
            statement.setStatementFromDate(parsed.getStatementFromDate());
            statement.setStatementToDate(parsed.getStatementToDate());
            statement.setOpeningBalance(parsed.getOpeningBalance());
            statement.setClosingBalance(parsed.getClosingBalance());
            statement.setTotalTransactions(parsed.getTransactions().size());

            // Save transactions
            BigDecimal totalCredits = BigDecimal.ZERO;
            BigDecimal totalDebits = BigDecimal.ZERO;
            List<BankTransaction> savedTransactions = new ArrayList<>();

            for (ParsedTransaction pt : parsed.getTransactions()) {
                BankTransaction txn = BankTransaction.builder()
                        .statement(statement)
                        .transactionDate(pt.getTransactionDate())
                        .valueDate(pt.getValueDate())
                        .narration(pt.getNarration())
                        .referenceNumber(pt.getReferenceNumber())
                        .debitAmount(Optional.ofNullable(pt.getDebitAmount()).orElse(BigDecimal.ZERO))
                        .creditAmount(Optional.ofNullable(pt.getCreditAmount()).orElse(BigDecimal.ZERO))
                        .runningBalance(pt.getRunningBalance())
                        .build();

                categorizer.categorize(txn);
                savedTransactions.add(txn);

                totalCredits = totalCredits.add(Optional.ofNullable(txn.getCreditAmount()).orElse(BigDecimal.ZERO));
                totalDebits = totalDebits.add(Optional.ofNullable(txn.getDebitAmount()).orElse(BigDecimal.ZERO));
            }

            transactionRepository.saveAll(savedTransactions);

            statement.setTotalCreditAmount(totalCredits);
            statement.setTotalDebitAmount(totalDebits);
            statement.setParsingStatus(ParsingStatus.PARSED);
            statementRepository.save(statement);

            // Publish parsed event
            eventPublisher.publishParsed(statement.getId(), statement.getBankName(), savedTransactions.size());

            // Defer async analysis until after this transaction commits,
            // so the async thread can see the committed transactions
            Long stmtId = statement.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncAnalysisService.runAnalysisAsync(stmtId);
                }
            });

            return UploadResponse.builder()
                    .statementId(statement.getId())
                    .fileName(file.getOriginalFilename())
                    .status(ParsingStatus.PARSED)
                    .message("Statement uploaded and parsed successfully. Analysis in progress.")
                    .build();

        } catch (Exception e) {
            statement.setParsingStatus(ParsingStatus.FAILED);
            statement.setParsingError(e.getMessage());
            statementRepository.save(statement);
            log.error("Failed to process statement {}: {}", statement.getId(), e.getMessage(), e);
            throw new StatementProcessingException("Failed to process statement: " + e.getMessage(), e);
        }
    }



    @Transactional(readOnly = true)
    public StatementResponse getStatement(Long id) {
        BankStatement s = statementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + id));
        return mapToResponse(s);
    }

    @Transactional(readOnly = true)
    public Page<StatementResponse> listStatements(Pageable pageable) {
        return statementRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(Long statementId, Pageable pageable) {
        if (!statementRepository.existsById(statementId)) {
            throw new ResourceNotFoundException("Statement not found: " + statementId);
        }
        return transactionRepository.findByStatementIdOrderByTransactionDateAsc(statementId, pageable)
                .map(this::mapToTransactionResponse);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long statementId) {
        StatementAnalysis a = analysisRepository.findByStatementId(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found for statement: " + statementId));
        return mapToAnalysisResponse(a);
    }

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(Long statementId) {
        BankStatement s = statementRepository.findById(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + statementId));
        StatementAnalysis a = analysisRepository.findByStatementId(statementId).orElse(null);
        List<MonthlySummary> monthlies = monthlySummaryRepository.findByStatementIdOrderByYearAscMonthAsc(statementId);
        List<BankTransaction> transactions = transactionRepository.findByStatementIdOrderByTransactionDateAsc(statementId);

        String period = "";
        if (s.getStatementFromDate() != null && s.getStatementToDate() != null) {
            period = s.getStatementFromDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    + " - " + s.getStatementToDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        }

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = transactions.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO)
                                        .max(Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO)),
                                BigDecimal::add)));

        return SummaryResponse.builder()
                .statementId(s.getId())
                .bankName(s.getBankName())
                .accountHolderName(s.getAccountHolderName())
                .accountNumberMasked(s.getAccountNumberMasked())
                .statementPeriod(period)
                .avgBankBalance(a != null ? a.getAvgBankBalance() : null)
                .detectedSalaryAmount(a != null ? a.getDetectedSalaryAmount() : null)
                .totalIncome(a != null ? a.getTotalIncome() : null)
                .foir(a != null ? a.getFoir() : null)
                .creditworthinessScore(a != null ? a.getCreditworthinessScore() : null)
                .repaymentCapacityScore(a != null ? a.getRepaymentCapacityScore() : null)
                .totalTransactions(s.getTotalTransactions())
                .totalCredits(s.getTotalCreditAmount())
                .totalDebits(s.getTotalDebitAmount())
                .bounceCount(a != null ? a.getBounceCount() : 0)
                .redFlagCount(a != null && a.getRedFlags() != null ? a.getRedFlags().size() : 0)
                .monthlySummaries(monthlies.stream().map(this::mapToMonthlySummaryResponse).toList())
                .categoryBreakdown(categoryBreakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MonthlySummaryResponse> getMonthlySummaries(Long statementId) {
        if (!statementRepository.existsById(statementId)) {
            throw new ResourceNotFoundException("Statement not found: " + statementId);
        }
        return monthlySummaryRepository.findByStatementIdOrderByYearAscMonthAsc(statementId)
                .stream().map(this::mapToMonthlySummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRedFlags(Long statementId) {
        StatementAnalysis a = analysisRepository.findByStatementId(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found for statement: " + statementId));
        return a.getRedFlags() != null ? a.getRedFlags() : List.of();
    }

    @Transactional(readOnly = true)
    public List<StatementResponse> getStatementsByApplication(String applicationId) {
        return statementRepository.findByApplicationId(applicationId)
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void deleteStatement(Long id) {
        if (!statementRepository.existsById(id)) {
            throw new ResourceNotFoundException("Statement not found: " + id);
        }
        statementRepository.deleteById(id);
    }

    private String uploadToMinio(MultipartFile file, Long statementId) {
        try {
            String objectName = "statements/" + statementId + "/" + file.getOriginalFilename();
            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(is, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            return objectName;
        } catch (Exception e) {
            log.warn("Failed to upload to MinIO: {}", e.getMessage());
            return null;
        }
    }

    private String computeHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private StatementResponse mapToResponse(BankStatement s) {
        return StatementResponse.builder()
                .id(s.getId())
                .fileName(s.getFileName())
                .accountHolderName(s.getAccountHolderName())
                .accountNumberMasked(s.getAccountNumberMasked())
                .bankName(s.getBankName())
                .bankCode(s.getBankCode())
                .ifscCode(s.getIfscCode())
                .branchName(s.getBranchName())
                .accountType(s.getAccountType() != null ? s.getAccountType().name() : null)
                .statementFromDate(s.getStatementFromDate())
                .statementToDate(s.getStatementToDate())
                .openingBalance(s.getOpeningBalance())
                .closingBalance(s.getClosingBalance())
                .totalTransactions(s.getTotalTransactions())
                .totalCreditAmount(s.getTotalCreditAmount())
                .totalDebitAmount(s.getTotalDebitAmount())
                .parsingStatus(s.getParsingStatus())
                .parsingError(s.getParsingError())
                .tamperCheckStatus(s.getTamperCheckStatus())
                .applicationId(s.getApplicationId())
                .batchId(s.getBatchId())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private TransactionResponse mapToTransactionResponse(BankTransaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionDate(t.getTransactionDate())
                .valueDate(t.getValueDate())
                .narration(t.getNarration())
                .referenceNumber(t.getReferenceNumber())
                .debitAmount(t.getDebitAmount())
                .creditAmount(t.getCreditAmount())
                .runningBalance(t.getRunningBalance())
                .category(t.getCategory() != null ? t.getCategory().name() : null)
                .subCategory(t.getSubCategory())
                .channel(t.getChannel() != null ? t.getChannel().name() : null)
                .counterpartyName(t.getCounterpartyName())
                .isBounce(t.getIsBounce())
                .isReversal(t.getIsReversal())
                .isCircular(t.getIsCircular())
                .build();
    }

    private AnalysisResponse mapToAnalysisResponse(StatementAnalysis a) {
        return AnalysisResponse.builder()
                .id(a.getId())
                .statementId(a.getStatement().getId())
                .avgBankBalance(a.getAvgBankBalance())
                .minBalance(a.getMinBalance())
                .maxBalance(a.getMaxBalance())
                .balanceVolatility(a.getBalanceVolatility())
                .detectedSalaryAmount(a.getDetectedSalaryAmount())
                .salaryFrequency(a.getSalaryFrequency())
                .salaryDayOfMonth(a.getSalaryDayOfMonth())
                .salaryConfidence(a.getSalaryConfidence())
                .totalIncome(a.getTotalIncome())
                .nonSalaryIncome(a.getNonSalaryIncome())
                .imputedIncome(a.getImputedIncome())
                .incomeStabilityScore(a.getIncomeStabilityScore())
                .emiCount(a.getEmiCount())
                .totalEmiAmount(a.getTotalEmiAmount())
                .foir(a.getFoir())
                .totalObligations(a.getTotalObligations())
                .rentAmount(a.getRentAmount())
                .insuranceAmount(a.getInsuranceAmount())
                .totalCredits(a.getTotalCredits())
                .totalDebits(a.getTotalDebits())
                .netCashFlow(a.getNetCashFlow())
                .creditDebitRatio(a.getCreditDebitRatio())
                .cashFlowStability(a.getCashFlowStability())
                .bounceCount(a.getBounceCount())
                .bounceAmount(a.getBounceAmount())
                .circularTxnCount(a.getCircularTxnCount())
                .cashDepositRatio(a.getCashDepositRatio())
                .cashWithdrawalRatio(a.getCashWithdrawalRatio())
                .redFlags(a.getRedFlags())
                .creditworthinessScore(a.getCreditworthinessScore())
                .incomeConfidenceScore(a.getIncomeConfidenceScore())
                .repaymentCapacityScore(a.getRepaymentCapacityScore())
                .topCreditSources(a.getTopCreditSources())
                .topDebitDestinations(a.getTopDebitDestinations())
                .analysisCompletedAt(a.getAnalysisCompletedAt())
                .analysisVersion(a.getAnalysisVersion())
                .build();
    }

    private MonthlySummaryResponse mapToMonthlySummaryResponse(MonthlySummary m) {
        String monthLabel = java.time.Month.of(m.getMonth()).name().substring(0, 3) + " " + m.getYear();
        return MonthlySummaryResponse.builder()
                .id(m.getId())
                .year(m.getYear())
                .month(m.getMonth())
                .monthLabel(monthLabel)
                .openingBalance(m.getOpeningBalance())
                .closingBalance(m.getClosingBalance())
                .avgEodBalance(m.getAvgEodBalance())
                .minEodBalance(m.getMinEodBalance())
                .maxEodBalance(m.getMaxEodBalance())
                .totalCredits(m.getTotalCredits())
                .totalDebits(m.getTotalDebits())
                .netCashFlow(m.getNetCashFlow())
                .creditCount(m.getCreditCount())
                .debitCount(m.getDebitCount())
                .salaryAmount(m.getSalaryAmount())
                .emiAmount(m.getEmiAmount())
                .bounceCount(m.getBounceCount())
                .categorySummary(m.getCategorySummary())
                .build();
    }
}
