package com.billiontech.bankstatement.service;

import com.billiontech.bankstatement.event.BankStatementEventPublisher;
import com.billiontech.bankstatement.exception.ResourceNotFoundException;
import com.billiontech.bankstatement.model.entity.*;
import com.billiontech.bankstatement.model.enums.ParsingStatus;
import com.billiontech.bankstatement.repository.*;
import com.billiontech.bankstatement.service.analysis.AnalysisEngine;
import com.billiontech.bankstatement.service.tamper.TamperDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncAnalysisService {

    private final BankStatementRepository statementRepository;
    private final BankTransactionRepository transactionRepository;
    private final StatementAnalysisRepository analysisRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final AnalysisEngine analysisEngine;
    private final TamperDetectionService tamperDetectionService;
    private final BankStatementEventPublisher eventPublisher;

    @Async
    public void runAnalysisAsync(Long statementId) {
        try {
            runAnalysis(statementId);
        } catch (Exception e) {
            log.error("Async analysis failed for statement {}: {}", statementId, e.getMessage(), e);
        }
    }

    @Transactional
    public void runAnalysis(Long statementId) {
        BankStatement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + statementId));

        statement.setParsingStatus(ParsingStatus.ANALYZING);
        statementRepository.save(statement);

        List<BankTransaction> transactions = transactionRepository.findByStatementIdOrderByTransactionDateAsc(statementId);

        // Tamper detection
        TamperDetectionService.TamperResult tamperResult = tamperDetectionService.check(transactions);
        statement.setTamperCheckStatus(tamperResult.getStatus());
        statement.setTamperCheckDetails(tamperResult.getDetails());

        // Run analysis
        StatementAnalysis analysis = analysisEngine.analyze(statement, transactions);
        analysisRepository.save(analysis);

        // Monthly summaries
        List<MonthlySummary> monthlySummaries = analysisEngine.computeMonthlySummaries(statement, transactions);
        monthlySummaryRepository.saveAll(monthlySummaries);

        statement.setParsingStatus(ParsingStatus.ANALYSIS_COMPLETE);
        statementRepository.save(statement);

        // Publish events
        eventPublisher.publishAnalysisComplete(statementId,
                Optional.ofNullable(analysis.getCreditworthinessScore()).orElse(BigDecimal.ZERO).doubleValue());

        if (analysis.getRedFlags() != null && !analysis.getRedFlags().isEmpty()) {
            eventPublisher.publishRedFlagDetected(statementId, statement.getApplicationId(), analysis.getRedFlags().size());
        }

        log.info("Analysis complete for statement {} — score: {}, flags: {}",
                statementId,
                analysis.getCreditworthinessScore(),
                analysis.getRedFlags() != null ? analysis.getRedFlags().size() : 0);
    }
}
