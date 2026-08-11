package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScorecardConvergence1Test {

    @BeforeEach
    void seedRegistry() {
        GacatCatalogueAuthority.configure(false, true);
        CanonicalParameterRegistry.clearInstalledForTests();
        CanonicalParameterRegistry.install(
                CanonicalParameterRegistry.fromSeedForTestsOnly(),
                "JAVA_SEED_TEST_ONLY");
    }

    @Test
    void exactMappings_knownLiveScorecardParameters() {
        assertEquals("bureau.score", ScorecardCanonicalFactorMapper.resolve("BUREAU_SCORE").canonicalParameterId());
        assertEquals(ScorecardCanonicalFactorMapper.EXACT,
                ScorecardCanonicalFactorMapper.resolve("BUREAU_SCORE").mappingStatus());
        assertEquals("banking.avg_daily_balance_3m",
                ScorecardCanonicalFactorMapper.resolve("AVERAGE_BANK_BALANCE").canonicalParameterId());
        assertEquals("obligation.ratio",
                ScorecardCanonicalFactorMapper.resolve("OBLIGATION_RATIO").canonicalParameterId());
        assertEquals("kyc.quality",
                ScorecardCanonicalFactorMapper.resolve("KYC_QUALITY").canonicalParameterId());
        assertEquals("gst.turnover.trailing_12m",
                ScorecardCanonicalFactorMapper.resolve("GST_INCOME").canonicalParameterId());
    }

    @Test
    void safeAlias_and_ambiguous_and_noMatch() {
        assertEquals(ScorecardCanonicalFactorMapper.SAFE_ALIAS,
                ScorecardCanonicalFactorMapper.resolve("REQUESTED_AMOUNT").mappingStatus());
        assertEquals("application.requested_amount",
                ScorecardCanonicalFactorMapper.resolve("REQUESTED_AMOUNT").canonicalParameterId());
        assertEquals(ScorecardCanonicalFactorMapper.AMBIGUOUS,
                ScorecardCanonicalFactorMapper.resolve("MONTHLY_INCOME").mappingStatus());
        assertNull(ScorecardCanonicalFactorMapper.resolve("MONTHLY_INCOME").canonicalParameterId());
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH,
                ScorecardCanonicalFactorMapper.resolve("SCF_AMOUNT_OVER_STANDARD").mappingStatus());
    }

    @Test
    void stampScorecardJson_preservesPointsAndStampsExact() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "d1");
        row.put("parameter", "BUREAU_SCORE");
        row.put("source", "BUREAU");
        row.put("condition", "GTE:750");
        row.put("score", 35);
        Map<String, Object> scj = Map.of("rows", List.of(row));
        Map<String, Object> stamped = ScorecardCanonicalFactorMapper.stampScorecardJson(scj);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = ((List<Map<String, Object>>) stamped.get("rows")).get(0);
        assertEquals(35, out.get("score"));
        assertEquals("GTE:750", out.get("condition"));
        assertEquals("bureau.score", out.get("canonicalParameterId"));
        assertEquals(1, out.get("canonicalDefinitionVersion"));
        assertEquals(ScorecardCanonicalFactorMapper.EXACT, out.get("mappingStatus"));
    }

    @Test
    void v115StampHelper_idempotentExact() throws Exception {
        String json = "{\"rows\":[{\"id\":\"d1\",\"parameter\":\"BUREAU_SCORE\",\"condition\":\"GTE:750\",\"score\":35}]}";
        String once = db.migration.V115__ScorecardCanonicalFactorBindings.stampExactBindings(json);
        assertTrue(once.contains("bureau.score"));
        String twice = db.migration.V115__ScorecardCanonicalFactorBindings.stampExactBindings(once);
        assertEquals(once, twice);
        assertNotNull(twice);
    }

    @Test
    void policySuggestions_doNotAutoCreate() {
        ScorecardConvergenceService svc = new ScorecardConvergenceService(null);
        Map<String, Object> out = svc.suggestFromPolicy(List.of("bureau.score", "obligation.ratio", "application.business_vintage_months"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) out.get("suggestions");
        assertNotNull(suggestions);
        assertEquals(3, suggestions.size());
        assertEquals(false, suggestions.get(0).get("autoCreate"));
        assertEquals("ADD_OR_IGNORE", suggestions.get(0).get("actionRequired"));
    }

    @Test
    void inventoryClassification_knownKeys() {
        List<String> keys = List.of(
                "BUREAU_SCORE", "AVERAGE_BANK_BALANCE", "GST_INCOME", "ANNUAL_GST_TURNOVER",
                "KYC_QUALITY", "OBLIGATION_RATIO", "LIVE_UNSECURED_LOAN_COUNT", "CHEQUE_BOUNCES_3M",
                "NTC_FLAG", "REQUESTED_AMOUNT", "BUSINESS_VINTAGE_MONTHS", "ITR_INCOME", "PAT",
                "DSCR", "REPAYMENT_HISTORY", "MONTHLY_INCOME", "BANK_STATEMENT_INCOME",
                "EBITDA_PROXY", "LEVERAGE_RATIO", "LTV", "PROPERTY_VALUE", "INDUSTRY_RISK",
                "SCF_AMOUNT_OVER_STANDARD");
        Map<String, String> by = new LinkedHashMap<>();
        for (String k : keys) {
            by.put(k, ScorecardCanonicalFactorMapper.resolve(k).mappingStatus());
        }
        assertEquals(ScorecardCanonicalFactorMapper.EXACT, by.get("BUREAU_SCORE"));
        assertEquals(ScorecardCanonicalFactorMapper.EXACT, by.get("AVERAGE_BANK_BALANCE"));
        assertEquals(ScorecardCanonicalFactorMapper.EXACT, by.get("GST_INCOME"));
        assertEquals(ScorecardCanonicalFactorMapper.EXACT, by.get("KYC_QUALITY"));
        assertEquals(ScorecardCanonicalFactorMapper.EXACT, by.get("OBLIGATION_RATIO"));
        assertEquals(ScorecardCanonicalFactorMapper.EXACT, by.get("REPAYMENT_HISTORY"));
        assertEquals(ScorecardCanonicalFactorMapper.SAFE_ALIAS, by.get("ANNUAL_GST_TURNOVER"));
        assertEquals(ScorecardCanonicalFactorMapper.SAFE_ALIAS, by.get("REQUESTED_AMOUNT"));
        assertEquals(ScorecardCanonicalFactorMapper.SAFE_ALIAS, by.get("BUSINESS_VINTAGE_MONTHS"));
        assertEquals(ScorecardCanonicalFactorMapper.SAFE_ALIAS, by.get("CHEQUE_BOUNCES_3M"));
        assertEquals(ScorecardCanonicalFactorMapper.AMBIGUOUS, by.get("MONTHLY_INCOME"));
        assertEquals(ScorecardCanonicalFactorMapper.AMBIGUOUS, by.get("BANK_STATEMENT_INCOME"));
        assertEquals(ScorecardCanonicalFactorMapper.AMBIGUOUS, by.get("ITR_INCOME"));
        assertEquals(ScorecardCanonicalFactorMapper.AMBIGUOUS, by.get("PAT"));
        assertEquals(ScorecardCanonicalFactorMapper.AMBIGUOUS, by.get("DSCR"));
        assertEquals(ScorecardCanonicalFactorMapper.AMBIGUOUS, by.get("EBITDA_PROXY"));
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH, by.get("LIVE_UNSECURED_LOAN_COUNT"));
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH, by.get("NTC_FLAG"));
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH, by.get("LEVERAGE_RATIO"));
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH, by.get("LTV"));
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH, by.get("PROPERTY_VALUE"));
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH, by.get("INDUSTRY_RISK"));
        assertEquals(ScorecardCanonicalFactorMapper.NO_MATCH, by.get("SCF_AMOUNT_OVER_STANDARD"));
        System.out.println("SCORECARD-CONVERGENCE-1 mapping inventory: " + by);
    }

    @Test
    void adapterResolvesLegacyKey_notDuplicateFormula() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("parameter", "BUREAU_SCORE");
        row.put("source", "BUREAU");
        row.put("canonicalParameterId", "bureau.score");
        row.put("canonicalDefinitionVersion", 1);
        var app = new com.los.core.model.entity.LoanApplication();
        app.setBureauScore(760);
        var ctx = new com.los.core.service.credit.EffectiveUnderwritingContext(
                760, true, null, null, "MH", "Mumbai", "BUREAU", "T", "KYC",
                Map.of(), Map.of());
        var resolved = ScorecardFactorValueAdapter.resolveRow(row, app, ctx);
        assertEquals("bureau.score", resolved.canonicalParameterId());
        assertEquals(1, resolved.canonicalDefinitionVersion());
        assertEquals("BUREAU_SCORE", resolved.legacyParameterKey());
        assertEquals(0, resolved.numericValue().compareTo(new java.math.BigDecimal("760")));
    }

    @Test
    void allowCanonicalAuthority_remainsFalseInCatalogue() {
        ScorecardConvergenceService svc = new ScorecardConvergenceService(null);
        Map<String, Object> cat = svc.factorCatalogue("bureau");
        assertEquals(false, cat.get("allowCanonicalAuthority"));
    }
}
