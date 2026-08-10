package com.los.core.creditintelligence.banking;

import com.los.core.creditintelligence.banking.domain.BankingMetricOutcome;
import com.los.core.creditintelligence.banking.domain.CiBankAccount;
import com.los.core.creditintelligence.banking.domain.CiBankTransaction;
import com.los.core.creditintelligence.banking.domain.TxnCategory;
import com.los.core.creditintelligence.banking.repository.CiBankAccountRepository;
import com.los.core.creditintelligence.banking.repository.CiBankDuplicateGroupRepository;
import com.los.core.creditintelligence.banking.repository.CiBankRecurringObligationRepository;
import com.los.core.creditintelligence.banking.repository.CiBankStatementQualityRepository;
import com.los.core.creditintelligence.banking.repository.CiBankTransactionClassificationRepository;
import com.los.core.creditintelligence.banking.repository.CiBankTransactionRepository;
import com.los.core.creditintelligence.core.repository.CiEvidenceGroupRepository;
import com.los.core.creditintelligence.banking.service.BankAdjustedTurnoverCalculator;
import com.los.core.creditintelligence.banking.service.BankAverageDailyBalanceCalculator;
import com.los.core.creditintelligence.banking.service.BankDuplicateDetector;
import com.los.core.creditintelligence.banking.service.BankEmiObligationDetector;
import com.los.core.creditintelligence.banking.service.BankOdUtilisationCalculator;
import com.los.core.creditintelligence.banking.service.BankOwnershipResolver;
import com.los.core.creditintelligence.banking.service.BankSourcePrecedence;
import com.los.core.creditintelligence.banking.service.BankStatementIntegrityChecker;
import com.los.core.creditintelligence.banking.service.BankTransactionClassifier;
import com.los.core.creditintelligence.banking.service.BankingMetricService;
import com.los.core.creditintelligence.banking.service.BankingNormalizationService;
import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiSourceArtifact;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.service.aa.providers.AaFiDataParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration-style unit test: simulated AA summary → BankingNormalizationService.
 */
@ExtendWith(MockitoExtension.class)
class BankingNormalizationE2ETest {

    @Mock private CiBankAccountRepository accountRepository;
    @Mock private CiBankTransactionRepository transactionRepository;
    @Mock private CiBankTransactionClassificationRepository classificationRepository;
    @Mock private CiBankRecurringObligationRepository obligationRepository;
    @Mock private CiBankDuplicateGroupRepository duplicateGroupRepository;
    @Mock private CiBankStatementQualityRepository qualityRepository;
    @Mock private CiEvidenceGroupRepository evidenceGroupRepository;
    @Mock private SourceRegistryService sourceRegistryService;
    @Mock private CiMetricResultRepository metricResultRepository;

    private BankingNormalizationService normalizationService;
    private final List<CiBankAccount> savedAccounts = new ArrayList<>();
    private final List<CiBankTransaction> savedTxns = new ArrayList<>();
    private final List<CiMetricResult> savedMetrics = new ArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getCanonicalization().getBanking().setPersistTransactions(true);
        props.getCanonicalization().getBanking().setMinClassificationCoverage(0.3);
        props.getCanonicalization().getBanking().setEmiMinOccurrences(3);
        props.getCanonicalization().getBanking().setEmiRegularityThreshold(0.5);

        BankingMetricService metricService = new BankingMetricService(
                metricResultRepository,
                evidenceGroupRepository,
                obligationRepository,
                qualityRepository,
                new BankAverageDailyBalanceCalculator(),
                new BankAdjustedTurnoverCalculator(),
                new BankEmiObligationDetector(),
                new BankOdUtilisationCalculator(),
                props);

        normalizationService = new BankingNormalizationService(
                accountRepository,
                transactionRepository,
                classificationRepository,
                obligationRepository,
                duplicateGroupRepository,
                qualityRepository,
                evidenceGroupRepository,
                sourceRegistryService,
                new BankTransactionClassifier(),
                new BankDuplicateDetector(),
                new BankStatementIntegrityChecker(),
                new BankOwnershipResolver(),
                new BankSourcePrecedence(),
                metricService,
                new ContentHasher(),
                props);

        lenient().when(accountRepository.findByTenantIdAndApplicationIdAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(accountRepository.save(any())).thenAnswer(inv -> {
            CiBankAccount a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            savedAccounts.add(a);
            return a;
        });
        lenient().when(transactionRepository.saveAll(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<CiBankTransaction> list = inv.getArgument(0);
            for (CiBankTransaction t : list) {
                if (t.getId() == null) {
                    t.setId(UUID.randomUUID());
                }
                savedTxns.add(t);
            }
            return list;
        });
        lenient().when(classificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(qualityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(qualityRepository.findByApplicationIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        lenient().when(obligationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(duplicateGroupRepository.save(any())).thenAnswer(inv -> {
            var g = inv.getArgument(0);
            return g;
        });
        lenient().when(evidenceGroupRepository.save(any())).thenAnswer(inv -> {
            var g = inv.getArgument(0);
            return g;
        });
        lenient().when(metricResultRepository.save(any())).thenAnswer(inv -> {
            CiMetricResult m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(UUID.randomUUID());
            }
            savedMetrics.add(m);
            return m;
        });

        CiSourceRecord source = CiSourceRecord.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .sourceType("ACCOUNT_AGGREGATOR")
                .providerCode("AA")
                .build();
        lenient().when(sourceRegistryService.createOrGet(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(source);
        lenient().when(sourceRegistryService.createArtifact(any(), any(), any(), any()))
                .thenReturn(CiSourceArtifact.builder().id(UUID.randomUUID()).build());
    }

    @Test
    void simulatedAaSummaryCreatesAccountsClassifiesTxnsDetectsEmiExcludesLoanAndSelfTransfer() {
        Map<String, Object> summary = AaFiDataParser.simulatedSummary();
        UUID appId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        var result = normalizationService.normalizeFromAaSummary(
                appId, tenantId, summary, "SIMULATED", "consent-1", "Test Borrower");

        assertThat(result.alreadyExisted()).isFalse();
        assertThat(savedAccounts).hasSizeGreaterThanOrEqualTo(2);
        assertThat(savedTxns).isNotEmpty();
        assertThat(savedTxns.stream().anyMatch(t -> TxnCategory.EMI.name().equals(t.getCategory()))).isTrue();
        assertThat(savedTxns.stream().anyMatch(t -> TxnCategory.LOAN_DISBURSEMENT.name().equals(t.getCategory()))).isTrue();
        assertThat(savedTxns.stream().anyMatch(t -> TxnCategory.SELF_TRANSFER.name().equals(t.getCategory()))).isTrue();

        Optional<CiMetricResult> adj = savedMetrics.stream()
                .filter(m -> BankingMetricService.ADJ_12M.equals(m.getMetricCode()))
                .findFirst();
        assertThat(adj).isPresent();
        assertThat(adj.get().getOutcome()).isEqualTo(BankingMetricOutcome.PASS.name());
        // Loan disbursement + self-transfer excluded from adjusted credits
        assertThat(adj.get().getEvidence().get("excludedCount")).isNotNull();
        int excluded = ((Number) adj.get().getEvidence().get("excludedCount")).intValue();
        assertThat(excluded).isGreaterThanOrEqualTo(1);

        Optional<CiMetricResult> emi = savedMetrics.stream()
                .filter(m -> BankingMetricService.MONTHLY_OBL.equals(m.getMetricCode()))
                .findFirst();
        assertThat(emi).isPresent();
        assertThat(emi.get().getOutcome()).isEqualTo(BankingMetricOutcome.PASS.name());
    }

    @Test
    void summaryWithoutTransactionsYieldsAdbDataInsufficient() {
        Map<String, Object> summary = AaFiDataParser.simulatedSummary();
        summary.remove("transactions");
        savedAccounts.clear();
        savedTxns.clear();
        savedMetrics.clear();

        normalizationService.normalizeFromAaSummary(
                UUID.randomUUID(), UUID.randomUUID(), summary, "SIMULATED", "consent-2", null);

        assertThat(savedAccounts).isNotEmpty();
        assertThat(savedTxns).isEmpty();
        Optional<CiMetricResult> adb = savedMetrics.stream()
                .filter(m -> BankingMetricService.ADB_3M.equals(m.getMetricCode()))
                .findFirst();
        assertThat(adb).isPresent();
        assertThat(adb.get().getOutcome()).isEqualTo(BankingMetricOutcome.DATA_INSUFFICIENT.name());
        assertThat(adb.get().getValue()).isNull();

        Optional<CiMetricResult> adj = savedMetrics.stream()
                .filter(m -> BankingMetricService.ADJ_12M.equals(m.getMetricCode()))
                .findFirst();
        assertThat(adj).isPresent();
        assertThat(adj.get().getOutcome()).isEqualTo(BankingMetricOutcome.DATA_INSUFFICIENT.name());
    }
}
