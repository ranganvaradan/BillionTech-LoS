package com.billiontech.bankstatement.service.analysis;

import com.billiontech.bankstatement.model.entity.*;
import com.billiontech.bankstatement.model.enums.RedFlagType;
import com.billiontech.bankstatement.model.enums.TransactionCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    public StatementAnalysis analyze(BankStatement statement, List<BankTransaction> transactions) {
        StatementAnalysis analysis = StatementAnalysis.builder()
                .statement(statement)
                .analysisVersion("1.0")
                .build();

        if (transactions.isEmpty()) {
            analysis.setCreditworthinessScore(BigDecimal.ZERO);
            analysis.setAnalysisCompletedAt(LocalDateTime.now());
            return analysis;
        }

        analyzeBalance(analysis, transactions);
        analyzeIncome(analysis, transactions);
        analyzeObligations(analysis, transactions);
        analyzeCashFlow(analysis, transactions);
        analyzeRisk(analysis, transactions, statement);
        computeScores(analysis);

        analysis.setAnalysisCompletedAt(LocalDateTime.now());
        return analysis;
    }

    public List<MonthlySummary> computeMonthlySummaries(BankStatement statement, List<BankTransaction> transactions) {
        Map<YearMonth, List<BankTransaction>> byMonth = transactions.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getTransactionDate()),
                        TreeMap::new, Collectors.toList()));

        List<MonthlySummary> summaries = new ArrayList<>();

        for (Map.Entry<YearMonth, List<BankTransaction>> entry : byMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            List<BankTransaction> monthTxns = entry.getValue();

            BigDecimal totalCredits = BigDecimal.ZERO;
            BigDecimal totalDebits = BigDecimal.ZERO;
            int creditCount = 0;
            int debitCount = 0;
            BigDecimal salaryAmount = BigDecimal.ZERO;
            BigDecimal emiAmount = BigDecimal.ZERO;
            int bounceCount = 0;
            BigDecimal minBalance = null;
            BigDecimal maxBalance = null;
            BigDecimal balanceSum = BigDecimal.ZERO;
            int balanceCount = 0;
            BigDecimal openingBalance = null;
            BigDecimal closingBalance = null;

            Map<String, Object> categorySummary = new HashMap<>();

            for (BankTransaction txn : monthTxns) {
                BigDecimal credit = Optional.ofNullable(txn.getCreditAmount()).orElse(BigDecimal.ZERO);
                BigDecimal debit = Optional.ofNullable(txn.getDebitAmount()).orElse(BigDecimal.ZERO);

                totalCredits = totalCredits.add(credit);
                totalDebits = totalDebits.add(debit);
                if (credit.compareTo(BigDecimal.ZERO) > 0) creditCount++;
                if (debit.compareTo(BigDecimal.ZERO) > 0) debitCount++;

                if (txn.getCategory() == TransactionCategory.SALARY) {
                    salaryAmount = salaryAmount.add(credit);
                }
                if (txn.getCategory() == TransactionCategory.EMI_LOAN) {
                    emiAmount = emiAmount.add(debit);
                }
                if (Boolean.TRUE.equals(txn.getIsBounce())) {
                    bounceCount++;
                }

                if (txn.getRunningBalance() != null) {
                    if (openingBalance == null) openingBalance = txn.getRunningBalance();
                    closingBalance = txn.getRunningBalance();
                    balanceSum = balanceSum.add(txn.getRunningBalance());
                    balanceCount++;
                    if (minBalance == null || txn.getRunningBalance().compareTo(minBalance) < 0) {
                        minBalance = txn.getRunningBalance();
                    }
                    if (maxBalance == null || txn.getRunningBalance().compareTo(maxBalance) > 0) {
                        maxBalance = txn.getRunningBalance();
                    }
                }

                // Category summary
                String cat = txn.getCategory() != null ? txn.getCategory().name() : "OTHER";
                BigDecimal catAmount = debit.compareTo(BigDecimal.ZERO) > 0 ? debit : credit;
                categorySummary.merge(cat, catAmount, (a, b) -> ((BigDecimal) a).add((BigDecimal) b));
            }

            BigDecimal avgEod = balanceCount > 0
                    ? balanceSum.divide(BigDecimal.valueOf(balanceCount), SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            MonthlySummary summary = MonthlySummary.builder()
                    .statement(statement)
                    .year(ym.getYear())
                    .month(ym.getMonthValue())
                    .openingBalance(openingBalance)
                    .closingBalance(closingBalance)
                    .avgEodBalance(avgEod)
                    .minEodBalance(minBalance)
                    .maxEodBalance(maxBalance)
                    .totalCredits(totalCredits)
                    .totalDebits(totalDebits)
                    .netCashFlow(totalCredits.subtract(totalDebits))
                    .creditCount(creditCount)
                    .debitCount(debitCount)
                    .salaryAmount(salaryAmount)
                    .emiAmount(emiAmount)
                    .bounceCount(bounceCount)
                    .categorySummary(categorySummary)
                    .build();

            summaries.add(summary);
        }

        return summaries;
    }

    private void analyzeBalance(StatementAnalysis analysis, List<BankTransaction> transactions) {
        List<BigDecimal> balances = transactions.stream()
                .map(BankTransaction::getRunningBalance)
                .filter(Objects::nonNull)
                .toList();

        if (balances.isEmpty()) return;

        BigDecimal sum = balances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(balances.size()), SCALE, RoundingMode.HALF_UP);
        BigDecimal min = balances.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal max = balances.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        // Balance volatility (coefficient of variation)
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal b : balances) {
            BigDecimal diff = b.subtract(avg);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(balances.size()), 10, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal volatility = avg.compareTo(BigDecimal.ZERO) != 0
                ? stdDev.divide(avg.abs(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        analysis.setAvgBankBalance(avg);
        analysis.setMinBalance(min);
        analysis.setMaxBalance(max);
        analysis.setBalanceVolatility(volatility);
    }

    private void analyzeIncome(StatementAnalysis analysis, List<BankTransaction> transactions) {
        List<BankTransaction> salaryTxns = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.SALARY)
                .toList();

        if (!salaryTxns.isEmpty()) {
            BigDecimal totalSalary = salaryTxns.stream()
                    .map(t -> Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Group by month to find average salary
            Map<YearMonth, BigDecimal> salaryByMonth = salaryTxns.stream()
                    .collect(Collectors.groupingBy(
                            t -> YearMonth.from(t.getTransactionDate()),
                            Collectors.reducing(BigDecimal.ZERO,
                                    t -> Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO),
                                    BigDecimal::add)));

            BigDecimal avgSalary = totalSalary.divide(
                    BigDecimal.valueOf(Math.max(1, salaryByMonth.size())), SCALE, RoundingMode.HALF_UP);

            analysis.setDetectedSalaryAmount(avgSalary);
            analysis.setSalaryFrequency("MONTHLY");

            // Detect salary day
            Map<Integer, Long> dayFrequency = salaryTxns.stream()
                    .collect(Collectors.groupingBy(t -> t.getTransactionDate().getDayOfMonth(), Collectors.counting()));
            analysis.setSalaryDayOfMonth(dayFrequency.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null));

            // Salary confidence based on regularity
            long months = salaryByMonth.size();
            LocalDate earliest = salaryTxns.stream().map(BankTransaction::getTransactionDate).min(Comparator.naturalOrder()).orElse(LocalDate.now());
            LocalDate latest = salaryTxns.stream().map(BankTransaction::getTransactionDate).max(Comparator.naturalOrder()).orElse(LocalDate.now());
            long expectedMonths = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(earliest), YearMonth.from(latest)) + 1;
            BigDecimal confidence = expectedMonths > 0
                    ? BigDecimal.valueOf(months).divide(BigDecimal.valueOf(expectedMonths), SCALE, RoundingMode.HALF_UP).multiply(HUNDRED)
                    : BigDecimal.ZERO;
            analysis.setSalaryConfidence(confidence.min(HUNDRED));

            // Income stability: coefficient of variation of monthly salaries
            List<BigDecimal> monthlySalaries = new ArrayList<>(salaryByMonth.values());
            BigDecimal salaryStability = computeStabilityScore(monthlySalaries);
            analysis.setIncomeStabilityScore(salaryStability);
        } else {
            analysis.setSalaryConfidence(BigDecimal.ZERO);
            analysis.setIncomeStabilityScore(BigDecimal.ZERO);
        }

        // Total income (all credits)
        BigDecimal totalIncome = transactions.stream()
                .filter(t -> t.getCreditAmount() != null && t.getCreditAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(t -> t.getCategory() != TransactionCategory.BOUNCE_RETURN && t.getCategory() != TransactionCategory.REVERSAL)
                .map(BankTransaction::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        analysis.setTotalIncome(totalIncome);
        BigDecimal totalSalaryCredits = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.SALARY)
                .map(t -> Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        analysis.setNonSalaryIncome(totalIncome.subtract(totalSalaryCredits));

        // Imputed monthly income
        if (analysis.getDetectedSalaryAmount() != null && analysis.getDetectedSalaryAmount().compareTo(BigDecimal.ZERO) > 0) {
            analysis.setImputedIncome(analysis.getDetectedSalaryAmount());
        } else {
            // For non-salaried: estimate from average monthly credits
            Map<YearMonth, BigDecimal> creditsByMonth = transactions.stream()
                    .filter(t -> t.getCreditAmount() != null && t.getCreditAmount().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.groupingBy(
                            t -> YearMonth.from(t.getTransactionDate()),
                            Collectors.reducing(BigDecimal.ZERO, BankTransaction::getCreditAmount, BigDecimal::add)));
            if (!creditsByMonth.isEmpty()) {
                BigDecimal avgMonthlyCredits = creditsByMonth.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(creditsByMonth.size()), SCALE, RoundingMode.HALF_UP);
                analysis.setImputedIncome(avgMonthlyCredits);
            }
        }
    }

    private void analyzeObligations(StatementAnalysis analysis, List<BankTransaction> transactions) {
        List<BankTransaction> emiTxns = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.EMI_LOAN)
                .toList();

        BigDecimal totalEmi = emiTxns.stream()
                .map(t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Detect unique EMIs by grouping similar amounts
        Map<YearMonth, BigDecimal> emiByMonth = emiTxns.stream()
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getTransactionDate()),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO),
                                BigDecimal::add)));

        int months = Math.max(1, emiByMonth.size());
        BigDecimal avgMonthlyEmi = totalEmi.divide(BigDecimal.valueOf(months), SCALE, RoundingMode.HALF_UP);
        analysis.setEmiCount(emiTxns.size());
        analysis.setTotalEmiAmount(avgMonthlyEmi);

        // Rent
        BigDecimal rentTotal = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.RENT)
                .map(t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<YearMonth, BigDecimal> rentByMonth = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.RENT)
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getTransactionDate()),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO),
                                BigDecimal::add)));
        BigDecimal avgRent = !rentByMonth.isEmpty()
                ? rentTotal.divide(BigDecimal.valueOf(rentByMonth.size()), SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        analysis.setRentAmount(avgRent);

        // Insurance (monthly average)
        BigDecimal insuranceTotal = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.INSURANCE)
                .map(t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<YearMonth, BigDecimal> insuranceByMonth = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.INSURANCE)
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getTransactionDate()),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO),
                                BigDecimal::add)));
        BigDecimal avgInsurance = !insuranceByMonth.isEmpty()
                ? insuranceTotal.divide(BigDecimal.valueOf(insuranceByMonth.size()), SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        analysis.setInsuranceAmount(avgInsurance);

        // Total obligations (all monthly averages)
        BigDecimal totalObligations = avgMonthlyEmi.add(avgRent).add(avgInsurance);
        analysis.setTotalObligations(totalObligations);

        // FOIR
        BigDecimal income = Optional.ofNullable(analysis.getImputedIncome()).orElse(BigDecimal.ZERO);
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal foir = totalObligations.divide(income, 4, RoundingMode.HALF_UP).multiply(HUNDRED);
            analysis.setFoir(foir.setScale(SCALE, RoundingMode.HALF_UP));
        } else {
            analysis.setFoir(BigDecimal.ZERO);
        }
    }

    private void analyzeCashFlow(StatementAnalysis analysis, List<BankTransaction> transactions) {
        BigDecimal totalCredits = transactions.stream()
                .map(t -> Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebits = transactions.stream()
                .map(t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        analysis.setTotalCredits(totalCredits);
        analysis.setTotalDebits(totalDebits);
        analysis.setNetCashFlow(totalCredits.subtract(totalDebits));
        analysis.setCreditDebitRatio(totalDebits.compareTo(BigDecimal.ZERO) > 0
                ? totalCredits.divide(totalDebits, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // Cash flow stability
        Map<YearMonth, BigDecimal> netByMonth = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getTransactionDate()),
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO)
                                        .subtract(Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO)),
                                BigDecimal::add)));
        analysis.setCashFlowStability(computeStabilityScore(new ArrayList<>(netByMonth.values())));

        // Top credit sources
        Map<String, BigDecimal> creditSources = transactions.stream()
                .filter(t -> t.getCounterpartyName() != null && !t.getCounterpartyName().isBlank()
                        && t.getCreditAmount() != null && t.getCreditAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(BankTransaction::getCounterpartyName,
                        Collectors.reducing(BigDecimal.ZERO, BankTransaction::getCreditAmount, BigDecimal::add)));
        List<Map<String, Object>> topCredits = creditSources.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "amount", e.getValue()))
                .toList();
        analysis.setTopCreditSources(topCredits);

        // Top debit destinations
        Map<String, BigDecimal> debitDests = transactions.stream()
                .filter(t -> t.getCounterpartyName() != null && !t.getCounterpartyName().isBlank()
                        && t.getDebitAmount() != null && t.getDebitAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(BankTransaction::getCounterpartyName,
                        Collectors.reducing(BigDecimal.ZERO, BankTransaction::getDebitAmount, BigDecimal::add)));
        List<Map<String, Object>> topDebits = debitDests.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "amount", e.getValue()))
                .toList();
        analysis.setTopDebitDestinations(topDebits);
    }

    private void analyzeRisk(StatementAnalysis analysis, List<BankTransaction> transactions, BankStatement statement) {
        List<Map<String, Object>> redFlags = new ArrayList<>();

        // Bounce analysis
        long bounceCount = transactions.stream().filter(t -> Boolean.TRUE.equals(t.getIsBounce())).count();
        BigDecimal bounceAmount = transactions.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsBounce()))
                .map(t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        analysis.setBounceCount((int) bounceCount);
        analysis.setBounceAmount(bounceAmount);

        if (bounceCount > 3) {
            redFlags.add(Map.of("type", RedFlagType.HIGH_BOUNCE_RATE.name(),
                    "severity", "HIGH", "description", "High bounce rate: " + bounceCount + " bounces detected"));
        }

        // Circular transactions
        int circularCount = detectCircularTransactions(transactions);
        analysis.setCircularTxnCount(circularCount);
        if (circularCount > 0) {
            redFlags.add(Map.of("type", RedFlagType.CIRCULAR_TRANSACTIONS.name(),
                    "severity", "HIGH", "description", circularCount + " circular transactions detected"));
        }

        // Cash deposit ratio
        BigDecimal cashDeposits = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.CASH_DEPOSIT)
                .map(t -> Optional.ofNullable(t.getCreditAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = Optional.ofNullable(analysis.getTotalCredits()).orElse(BigDecimal.ZERO);
        BigDecimal cashDepositRatio = totalCredits.compareTo(BigDecimal.ZERO) > 0
                ? cashDeposits.divide(totalCredits, 4, RoundingMode.HALF_UP).multiply(HUNDRED)
                : BigDecimal.ZERO;
        analysis.setCashDepositRatio(cashDepositRatio.setScale(SCALE, RoundingMode.HALF_UP));

        if (cashDepositRatio.compareTo(new BigDecimal("40")) > 0) {
            redFlags.add(Map.of("type", RedFlagType.CASH_DEPOSIT_SPIKE.name(),
                    "severity", "MEDIUM", "description",
                    "High cash deposit ratio: " + cashDepositRatio.setScale(1, RoundingMode.HALF_UP) + "%"));
        }

        // Cash withdrawal ratio
        BigDecimal cashWithdrawals = transactions.stream()
                .filter(t -> t.getCategory() == TransactionCategory.CASH_WITHDRAWAL)
                .map(t -> Optional.ofNullable(t.getDebitAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebits = Optional.ofNullable(analysis.getTotalDebits()).orElse(BigDecimal.ZERO);
        BigDecimal cashWithdrawalRatio = totalDebits.compareTo(BigDecimal.ZERO) > 0
                ? cashWithdrawals.divide(totalDebits, 4, RoundingMode.HALF_UP).multiply(HUNDRED)
                : BigDecimal.ZERO;
        analysis.setCashWithdrawalRatio(cashWithdrawalRatio.setScale(SCALE, RoundingMode.HALF_UP));

        if (cashWithdrawalRatio.compareTo(new BigDecimal("50")) > 0) {
            redFlags.add(Map.of("type", RedFlagType.HIGH_CASH_WITHDRAWAL.name(),
                    "severity", "MEDIUM", "description",
                    "High cash withdrawal ratio: " + cashWithdrawalRatio.setScale(1, RoundingMode.HALF_UP) + "%"));
        }

        // Minimum balance breaches
        long minBalanceBreaches = transactions.stream()
                .filter(t -> t.getRunningBalance() != null && t.getRunningBalance().compareTo(BigDecimal.ZERO) < 0)
                .count();
        if (minBalanceBreaches > 5) {
            redFlags.add(Map.of("type", RedFlagType.MINIMUM_BALANCE_BREACH.name(),
                    "severity", "MEDIUM", "description", minBalanceBreaches + " instances of negative balance"));
        }

        // High FOIR
        if (analysis.getFoir() != null && analysis.getFoir().compareTo(new BigDecimal("60")) > 0) {
            redFlags.add(Map.of("type", RedFlagType.HIGH_FOIR.name(),
                    "severity", "HIGH", "description", "High FOIR: " + analysis.getFoir() + "%"));
        }

        // Declining balance trend
        if (statement.getOpeningBalance() != null && statement.getClosingBalance() != null) {
            if (statement.getClosingBalance().compareTo(statement.getOpeningBalance().multiply(new BigDecimal("0.5"))) < 0) {
                redFlags.add(Map.of("type", RedFlagType.DECLINING_BALANCE_TREND.name(),
                        "severity", "MEDIUM", "description", "Closing balance less than 50% of opening balance"));
            }
        }

        // Penal charges
        long penalCount = transactions.stream()
                .filter(t -> {
                    String nar = t.getNarration() != null ? t.getNarration().toUpperCase() : "";
                    return nar.contains("PENALTY") || nar.contains("PENAL") || nar.contains("FINE");
                }).count();
        if (penalCount > 2) {
            redFlags.add(Map.of("type", RedFlagType.PENAL_CHARGES.name(),
                    "severity", "MEDIUM", "description", penalCount + " penal charges detected"));
        }

        // Frequent reversals
        long reversalCount = transactions.stream().filter(t -> Boolean.TRUE.equals(t.getIsReversal())).count();
        if (reversalCount > 5) {
            redFlags.add(Map.of("type", RedFlagType.FREQUENT_REVERSALS.name(),
                    "severity", "LOW", "description", reversalCount + " reversals detected"));
        }

        // Salary discontinuity
        if (analysis.getSalaryConfidence() != null && analysis.getSalaryConfidence().compareTo(new BigDecimal("70")) < 0
                && analysis.getDetectedSalaryAmount() != null && analysis.getDetectedSalaryAmount().compareTo(BigDecimal.ZERO) > 0) {
            redFlags.add(Map.of("type", RedFlagType.SALARY_DISCONTINUITY.name(),
                    "severity", "MEDIUM", "description",
                    "Salary not consistently credited. Confidence: " + analysis.getSalaryConfidence() + "%"));
        }

        analysis.setRedFlags(redFlags);
    }

    private int detectCircularTransactions(List<BankTransaction> transactions) {
        int count = 0;
        Map<LocalDate, List<BankTransaction>> byDate = transactions.stream()
                .collect(Collectors.groupingBy(BankTransaction::getTransactionDate));

        for (List<BankTransaction> dayTxns : byDate.values()) {
            for (int i = 0; i < dayTxns.size(); i++) {
                for (int j = i + 1; j < dayTxns.size(); j++) {
                    BankTransaction a = dayTxns.get(i);
                    BankTransaction b = dayTxns.get(j);
                    BigDecimal aCredit = Optional.ofNullable(a.getCreditAmount()).orElse(BigDecimal.ZERO);
                    BigDecimal aDebit = Optional.ofNullable(a.getDebitAmount()).orElse(BigDecimal.ZERO);
                    BigDecimal bCredit = Optional.ofNullable(b.getCreditAmount()).orElse(BigDecimal.ZERO);
                    BigDecimal bDebit = Optional.ofNullable(b.getDebitAmount()).orElse(BigDecimal.ZERO);

                    // Check if one is credit and the other is debit of same/similar amount
                    if (aCredit.compareTo(BigDecimal.ZERO) > 0 && bDebit.compareTo(BigDecimal.ZERO) > 0
                            && aCredit.subtract(bDebit).abs().compareTo(new BigDecimal("100")) < 0) {
                        a.setIsCircular(true);
                        b.setIsCircular(true);
                        count++;
                    } else if (aDebit.compareTo(BigDecimal.ZERO) > 0 && bCredit.compareTo(BigDecimal.ZERO) > 0
                            && aDebit.subtract(bCredit).abs().compareTo(new BigDecimal("100")) < 0) {
                        a.setIsCircular(true);
                        b.setIsCircular(true);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private void computeScores(StatementAnalysis analysis) {
        double score = 50.0;

        // Balance factors
        if (analysis.getAvgBankBalance() != null && analysis.getAvgBankBalance().compareTo(new BigDecimal("10000")) > 0) {
            score += 10;
        }
        if (analysis.getBalanceVolatility() != null && analysis.getBalanceVolatility().compareTo(new BigDecimal("0.5")) < 0) {
            score += 5;
        }

        // Income factors
        if (analysis.getSalaryConfidence() != null && analysis.getSalaryConfidence().compareTo(new BigDecimal("80")) > 0) {
            score += 15;
        } else if (analysis.getSalaryConfidence() != null && analysis.getSalaryConfidence().compareTo(new BigDecimal("50")) > 0) {
            score += 8;
        }
        if (analysis.getIncomeStabilityScore() != null && analysis.getIncomeStabilityScore().compareTo(new BigDecimal("70")) > 0) {
            score += 5;
        }

        // FOIR
        if (analysis.getFoir() != null) {
            if (analysis.getFoir().compareTo(new BigDecimal("40")) < 0) score += 10;
            else if (analysis.getFoir().compareTo(new BigDecimal("60")) < 0) score += 5;
            else score -= 10;
        }

        // Bounce penalty
        if (analysis.getBounceCount() != null) {
            score -= analysis.getBounceCount() * 3.0;
        }

        // Circular transaction penalty
        if (analysis.getCircularTxnCount() != null) {
            score -= analysis.getCircularTxnCount() * 5.0;
        }

        // Red flag penalty
        if (analysis.getRedFlags() != null) {
            for (Map<String, Object> flag : analysis.getRedFlags()) {
                String severity = (String) flag.get("severity");
                if ("HIGH".equals(severity)) score -= 5;
                else if ("MEDIUM".equals(severity)) score -= 3;
                else score -= 1;
            }
        }

        score = Math.max(0, Math.min(100, score));
        analysis.setCreditworthinessScore(BigDecimal.valueOf(score).setScale(SCALE, RoundingMode.HALF_UP));

        // Income confidence score
        double incomeScore = 50.0;
        if (analysis.getSalaryConfidence() != null) {
            incomeScore = analysis.getSalaryConfidence().doubleValue();
        }
        analysis.setIncomeConfidenceScore(BigDecimal.valueOf(Math.max(0, Math.min(100, incomeScore)))
                .setScale(SCALE, RoundingMode.HALF_UP));

        // Repayment capacity score
        double repaymentScore = 50.0;
        if (analysis.getFoir() != null) {
            repaymentScore = Math.max(0, 100 - analysis.getFoir().doubleValue());
        }
        if (analysis.getNetCashFlow() != null && analysis.getNetCashFlow().compareTo(BigDecimal.ZERO) > 0) {
            repaymentScore += 10;
        }
        repaymentScore = Math.max(0, Math.min(100, repaymentScore));
        analysis.setRepaymentCapacityScore(BigDecimal.valueOf(repaymentScore).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal computeStabilityScore(List<BigDecimal> values) {
        if (values == null || values.size() < 2) return new BigDecimal("50.00");

        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP);

        if (avg.compareTo(BigDecimal.ZERO) == 0) return new BigDecimal("50.00");

        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal diff = v.subtract(avg);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP);
        double cv = Math.sqrt(variance.doubleValue()) / Math.abs(avg.doubleValue());

        // Convert CV to stability score (lower CV = higher stability)
        double stability = Math.max(0, 100 - (cv * 100));
        return BigDecimal.valueOf(stability).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
