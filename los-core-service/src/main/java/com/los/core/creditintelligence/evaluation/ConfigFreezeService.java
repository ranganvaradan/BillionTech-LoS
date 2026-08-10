package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.evaluation.domain.CiConfigFreeze;
import com.los.core.creditintelligence.evaluation.repository.CiConfigFreezeRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfigFreezeService {

    public static final String SCHEMA_VERSION = "CONFIG_FREEZE_V1";
    public static final String CONFIG_VERSION = "CI_THRESHOLDS_V1";

    private final CreditIntelligenceProperties properties;
    private final CiConfigFreezeRepository configFreezeRepository;
    private final ContentHasher contentHasher;

    @Transactional
    public CiConfigFreeze freezeCurrent(UUID tenantId) {
        Map<String, Object> content = snapshotContent();
        String hash = contentHasher.hashMap(content);
        return configFreezeRepository.findByTenantIdAndContentHash(tenantId, hash)
                .orElseGet(() -> configFreezeRepository.save(CiConfigFreeze.builder()
                        .tenantId(tenantId)
                        .configVersion(CONFIG_VERSION)
                        .content(content)
                        .contentHash(hash)
                        .schemaVersion(SCHEMA_VERSION)
                        .build()));
    }

    public Map<String, Object> snapshotContent() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("schemaVersion", SCHEMA_VERSION);
        content.put("configVersion", CONFIG_VERSION);

        CreditIntelligenceProperties.Canonicalization canon = properties.getCanonicalization();
        Map<String, Object> canonicalization = new LinkedHashMap<>();
        canonicalization.put("bureau", bureauMap(canon.getBureau()));
        canonicalization.put("gst", gstMap(canon.getGst()));
        canonicalization.put("banking", bankingMap(canon.getBanking()));
        canonicalization.put("tax", taxMap(canon.getTax()));
        content.put("canonicalization", canonicalization);

        CreditIntelligenceProperties.Reconciliation recon = properties.getReconciliation();
        Map<String, Object> reconciliation = new LinkedHashMap<>();
        reconciliation.put("enabled", recon.isEnabled());
        reconciliation.put("useForShadowRules", recon.isUseForShadowRules());
        reconciliation.put("turnover", toleranceMap(
                recon.getTurnover().getMatchPct(),
                recon.getTurnover().getWarningPct(),
                recon.getTurnover().getMaterialPct(),
                recon.getTurnover().getToleranceVersion()));
        reconciliation.put("obligation", toleranceMap(
                recon.getObligation().getMatchPct(),
                recon.getObligation().getWarningPct(),
                recon.getObligation().getMaterialPct(),
                recon.getObligation().getToleranceVersion()));
        reconciliation.put("tax", toleranceMap(
                recon.getTax().getMatchPct(),
                recon.getTax().getWarningPct(),
                recon.getTax().getMaterialPct(),
                recon.getTax().getToleranceVersion()));
        content.put("reconciliation", reconciliation);

        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("evaluationContext", Map.of("enabled", properties.getEvaluationContext().isEnabled()));
        flags.put("configFreeze", Map.of("enabled", properties.getConfigFreeze().isEnabled()));
        flags.put("frozenPolicyExecution", Map.of("enabled", properties.getFrozenPolicyExecution().isEnabled()));
        flags.put("providerSpi", Map.of("enabled", properties.getProviderSpi().isEnabled()));
        flags.put("providerObservations", Map.of("enabled", properties.getProviderObservations().isEnabled()));
        content.put("flags", flags);

        return content;
    }

    private static Map<String, Object> bureauMap(CreditIntelligenceProperties.Canonicalization.Bureau b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", b.isEnabled());
        m.put("useForShadowRules", b.isUseForShadowRules());
        m.put("persistTradelines", b.isPersistTradelines());
        m.put("freshnessDays", b.getFreshnessDays());
        m.put("liveUnsecuredThreshold", b.getLiveUnsecuredThreshold());
        return m;
    }

    private static Map<String, Object> gstMap(CreditIntelligenceProperties.Canonicalization.Gst g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", g.isEnabled());
        m.put("useForShadowRules", g.isUseForShadowRules());
        m.put("persistPeriods", g.isPersistPeriods());
        m.put("minMonthsForAnnualization", g.getMinMonthsForAnnualization());
        m.put("gstr1Gstr3bVarianceWarningPct", g.getGstr1Gstr3bVarianceWarningPct());
        m.put("gstr1Gstr3bVarianceMaterialPct", g.getGstr1Gstr3bVarianceMaterialPct());
        m.put("minCompletenessForTrailing12m", g.getMinCompletenessForTrailing12m());
        m.put("filingLagDays", g.getFilingLagDays());
        m.put("turnoverEligibilityThreshold",
                g.getTurnoverEligibilityThreshold() != null ? g.getTurnoverEligibilityThreshold().toPlainString() : null);
        return m;
    }

    private static Map<String, Object> bankingMap(CreditIntelligenceProperties.Canonicalization.Banking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", b.isEnabled());
        m.put("useForShadowRules", b.isUseForShadowRules());
        m.put("persistTransactions", b.isPersistTransactions());
        m.put("minStatementCompleteness", b.getMinStatementCompleteness());
        m.put("minClassificationCoverage", b.getMinClassificationCoverage());
        m.put("emiMinOccurrences", b.getEmiMinOccurrences());
        m.put("emiRegularityThreshold", b.getEmiRegularityThreshold());
        m.put("odUtilisationWarningPct", b.getOdUtilisationWarningPct());
        m.put("cashDepositRatioWarningPct", b.getCashDepositRatioWarningPct());
        m.put("abbMinimum", b.getAbbMinimum() != null ? b.getAbbMinimum().toPlainString() : null);
        m.put("chequeReturnMax3m", b.getChequeReturnMax3m());
        m.put("nachReturnMax3m", b.getNachReturnMax3m());
        return m;
    }

    private static Map<String, Object> taxMap(CreditIntelligenceProperties.Canonicalization.Tax t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", t.isEnabled());
        m.put("useForShadowRules", t.isUseForShadowRules());
        m.put("persistDetail", t.isPersistDetail());
        m.put("minYearsForGrowth", t.getMinYearsForGrowth());
        m.put("itr26asVarianceWarningPct", t.getItr26asVarianceWarningPct());
        m.put("itr26asVarianceMaterialPct", t.getItr26asVarianceMaterialPct());
        m.put("itrAisVarianceWarningPct", t.getItrAisVarianceWarningPct());
        m.put("itrAisVarianceMaterialPct", t.getItrAisVarianceMaterialPct());
        m.put("minIncomeThreshold",
                t.getMinIncomeThreshold() != null ? t.getMinIncomeThreshold().toPlainString() : null);
        m.put("minTurnoverThreshold",
                t.getMinTurnoverThreshold() != null ? t.getMinTurnoverThreshold().toPlainString() : null);
        m.put("minPatPositive", t.isMinPatPositive());
        return m;
    }

    private static Map<String, Object> toleranceMap(double match, double warning, double material, String version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matchPct", match);
        m.put("warningPct", warning);
        m.put("materialPct", material);
        m.put("toleranceVersion", version);
        return m;
    }
}
