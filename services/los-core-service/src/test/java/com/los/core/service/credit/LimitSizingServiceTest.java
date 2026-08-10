package com.los.core.service.credit;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.UnderwritingRuleSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimitSizingServiceTest {

    @Mock
    private UnderwritingRuleSetRepository ruleSetRepository;

    @InjectMocks
    private LimitSizingService limitSizingService;

    private static LoanApplication borrowerApp(BigDecimal requested) {
        return LoanApplication.builder()
                .applicationNumber("T")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.PROPRIETOR)
                .loanProduct("BUSINESS_WC_INVOICE_DISCOUNTING")
                .intakeSegment(IntakeSegment.BORROWER)
                .requestedAmount(requested)
                .build();
    }

    private static LimitSizingConfig scfConfig() {
        return new LimitSizingConfig(
                true,
                "ANNUAL_GST_TURNOVER",
                new BigDecimal("0.25"),
                new BigDecimal("5000000"),
                new BigDecimal("10000000"),
                LimitSizingConfig.StandardCapMode.MIN_OF_BOTH,
                LimitSizingConfig.MaxDeviationMode.FIXED,
                null,
                null,
                true,
                true);
    }

    @Test
    void compute_standardLimitIsMinOfTurnoverPctAndCap() {
        var cfg = scfConfig();
        var r = LimitSizingService.compute(borrowerApp(new BigDecimal("4000000")), new BigDecimal("52000000"), cfg);
        assertThat(r).isNotNull();
        assertThat(r.standardLimit()).isEqualByComparingTo("5000000");
        assertThat(r.cappedRecommendedAmount()).isEqualByComparingTo("4000000");
        assertThat(r.band()).isEqualTo(LimitSizingService.BAND_WITHIN_STANDARD);
    }

    @Test
    void compute_turnoverPercentModesIgnoreFixedCaps() {
        LimitSizingConfig cfg = LimitSizingConfig.fromRulesJson(Map.of(
                "limitSizing", Map.of(
                        "enabled", true,
                        "standardCapMode", "TURNOVER_PERCENT",
                        "turnoverLimitPercent", "0.20",
                        "standardTicketCap", "1000000",
                        "maxDeviationMode", "TURNOVER_PERCENT",
                        "maxDeviationPercent", "0.35")));

        var result = LimitSizingService.compute(
                borrowerApp(new BigDecimal("8000000")), new BigDecimal("30000000"), cfg);

        assertThat(result).isNotNull();
        assertThat(result.standardLimit()).isEqualByComparingTo("6000000");
        assertThat(result.maxDeviationLimit()).isEqualByComparingTo("10500000");
        assertThat(result.band()).isEqualTo(LimitSizingService.BAND_SPECIAL_DEVIATION);
    }

    @Test
    void compute_fixedModeDoesNotRequireTurnover() {
        LimitSizingConfig cfg = LimitSizingConfig.fromRulesJson(Map.of(
                "limitSizing", Map.of(
                        "enabled", true,
                        "standardCapMode", "FIXED",
                        "standardTicketCap", "4500000",
                        "maxDeviationMode", "FIXED",
                        "maxDeviationCap", "7000000")));

        var result = LimitSizingService.compute(
                borrowerApp(new BigDecimal("7500000")), null, cfg);

        assertThat(result).isNotNull();
        assertThat(result.turnoverBasedLimit()).isNull();
        assertThat(result.standardLimit()).isEqualByComparingTo("4500000");
        assertThat(result.maxDeviationLimit()).isEqualByComparingTo("7000000");
        assertThat(result.band()).isEqualTo(LimitSizingService.BAND_OVER_ABSOLUTE_CAP);
    }

    @Test
    void applySanctionCap_onlyWhenConfigured() {
        LoanApplication app = borrowerApp(new BigDecimal("6000000"));
        app.setSanctionedAmount(new BigDecimal("6000000"));
        UnderwritingRuleSet rule = new UnderwritingRuleSet();
        rule.setPriority(100);
        rule.setRulesJson(Map.of(
                "limitSizing", Map.of(
                        "enabled", true,
                        "turnoverLimitPercent", 0.25,
                        "standardTicketCap", 5000000,
                        "maxDeviationCap", 10000000,
                        "sanctionCapEnabled", true)));
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(anyString(), anyString()))
                .thenReturn(List.of(rule));

        Map<String, Object> sc = new HashMap<>();
        sc.put("ANNUAL_GST_TURNOVER", new BigDecimal("52000000"));
        Map<String, Object> cc = new HashMap<>();
        cc.put("scorecard", sc);
        Map<String, Object> fi = new HashMap<>();
        fi.put("creditControl", cc);
        app.setFinancialInfo(fi);

        assertThat(limitSizingService.applySanctionCapIfConfigured(app)).isTrue();
        assertThat(app.getSanctionedAmount()).isEqualByComparingTo("5000000");
    }

    @Test
    void computeForApp_usesScorecardHintWhenFinancialInfoMissingTurnover() {
        LoanApplication app = borrowerApp(new BigDecimal("6000000"));
        UnderwritingRuleSet rule = new UnderwritingRuleSet();
        rule.setPriority(100);
        rule.setRulesJson(Map.of(
                "limitSizing", Map.of(
                        "enabled", true,
                        "turnoverLimitPercent", 0.25,
                        "standardTicketCap", 5000000,
                        "maxDeviationCap", 10000000,
                        "sanctionCapEnabled", true)));
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(anyString(), anyString()))
                .thenReturn(List.of(rule));

        Map<String, BigDecimal> hint = Map.of("ANNUAL_GST_TURNOVER", new BigDecimal("52000000"));
        assertThat(limitSizingService.computeForApp(app)).isEmpty();
        var result = limitSizingService.computeForApp(app, hint);
        assertThat(result).isPresent();
        assertThat(result.get().standardLimit()).isEqualByComparingTo("5000000");
        assertThat(result.get().band()).isEqualTo(LimitSizingService.BAND_SPECIAL_DEVIATION);
    }

    @Test
    void applySanctionCap_readsTurnoverFromUnderwritingMetaSnapshot() {
        LoanApplication app = borrowerApp(new BigDecimal("6000000"));
        app.setSanctionedAmount(new BigDecimal("6000000"));
        UnderwritingRuleSet rule = new UnderwritingRuleSet();
        rule.setPriority(100);
        rule.setRulesJson(Map.of(
                "limitSizing", Map.of(
                        "enabled", true,
                        "turnoverLimitPercent", 0.25,
                        "standardTicketCap", 5000000,
                        "maxDeviationCap", 10000000,
                        "sanctionCapEnabled", true)));
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(anyString(), anyString()))
                .thenReturn(List.of(rule));

        Map<String, Object> policy = new HashMap<>();
        policy.put("annualGstTurnover", "52000000");
        policy.put("standardLimit", "5000000");
        Map<String, Object> meta = new HashMap<>();
        meta.put("limitSizingPolicy", policy);
        Map<String, Object> fi = new HashMap<>();
        fi.put("underwritingMeta", meta);
        app.setFinancialInfo(fi);

        assertThat(limitSizingService.applySanctionCapIfConfigured(app)).isTrue();
        assertThat(app.getSanctionedAmount()).isEqualByComparingTo("5000000");
    }

    @Test
    void fromRulesJson_acceptsListAndUsesFirstEnabled() {
        LimitSizingConfig cfg = LimitSizingConfig.fromRulesJson(Map.of(
                "limitSizing", List.of(
                        Map.of("enabled", false, "turnoverLimitPercent", 0.1, "standardTicketCap", 1, "maxDeviationCap", 2),
                        Map.of(
                                "enabled", true,
                                "turnoverParameter", "ANNUAL_GST_TURNOVER",
                                "turnoverLimitPercent", 0.25,
                                "standardTicketCap", 5000000,
                                "maxDeviationCap", 10000000,
                                "sanctionCapEnabled", true))));
        assertThat(cfg).isNotNull();
        assertThat(cfg.turnoverLimitPercent()).isEqualByComparingTo("0.25");
        assertThat(cfg.standardCapMode()).isEqualTo(LimitSizingConfig.StandardCapMode.MIN_OF_BOTH);
        assertThat(cfg.maxDeviationMode()).isEqualTo(LimitSizingConfig.MaxDeviationMode.FIXED);
        assertThat(cfg.sanctionCapEnabled()).isTrue();
    }

    @Test
    void fromRulesJson_skipsEntriesMissingFieldsRequiredByModes() {
        LimitSizingConfig cfg = LimitSizingConfig.fromRulesJson(Map.of(
                "limitSizing", List.of(
                        Map.of(
                                "enabled", true,
                                "standardCapMode", "TURNOVER_PERCENT",
                                "standardTicketCap", 1000000,
                                "maxDeviationCap", 2000000),
                        Map.of(
                                "enabled", true,
                                "standardCapMode", "FIXED",
                                "standardTicketCap", 3000000,
                                "maxDeviationMode", "TURNOVER_PERCENT",
                                "maxDeviationPercent", "0.40"))));

        assertThat(cfg).isNotNull();
        assertThat(cfg.standardCapMode()).isEqualTo(LimitSizingConfig.StandardCapMode.FIXED);
        assertThat(cfg.maxDeviationMode()).isEqualTo(LimitSizingConfig.MaxDeviationMode.TURNOVER_PERCENT);
    }

    @Test
    void applyComputedMetrics_selectsFirstMatchingDependency() {
        LoanApplication app = borrowerApp(new BigDecimal("3000000"));
        UnderwritingRuleSet rule = new UnderwritingRuleSet();
        rule.setPriority(100);
        rule.setRulesJson(Map.of(
                "limitSizing", List.of(
                        Map.of(
                                "enabled", true,
                                "standardCapMode", "FIXED",
                                "standardTicketCap", 1000000,
                                "maxDeviationCap", 2000000,
                                "dependsOn", Map.of(
                                        "logic", "ALL",
                                        "conditions", List.of(Map.of(
                                                "source", "OTHER",
                                                "parameter", "RISK_SCORE",
                                                "condition", "GTE:80")))),
                        Map.of(
                                "enabled", true,
                                "standardCapMode", "FIXED",
                                "standardTicketCap", 4000000,
                                "maxDeviationCap", 6000000))));
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        anyString(), anyString()))
                .thenReturn(List.of(rule));
        Map<String, BigDecimal> scorecard = new HashMap<>();
        scorecard.put("RISK_SCORE", new BigDecimal("70"));

        limitSizingService.applyComputedMetrics(app, scorecard);

        assertThat(scorecard.get("SCF_STANDARD_LIMIT")).isEqualByComparingTo("4000000");
    }
}
