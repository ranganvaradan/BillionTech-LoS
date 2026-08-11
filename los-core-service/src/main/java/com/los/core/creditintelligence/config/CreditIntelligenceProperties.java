package com.los.core.creditintelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "credit-intelligence")
@Data
public class CreditIntelligenceProperties {

    private Foundation foundation = new Foundation();
    private ShadowEvaluation shadowEvaluation = new ShadowEvaluation();
    private SourceRegistry sourceRegistry = new SourceRegistry();
    private Canonicalization canonicalization = new Canonicalization();
    private Reconciliation reconciliation = new Reconciliation();
    /** C5.1 — immutable EvaluationContext for shadow/replay. */
    private EvaluationContext evaluationContext = new EvaluationContext();
    /** C5.1 — execute rules from frozen policy_content, not live DB. */
    private FrozenPolicyExecution frozenPolicyExecution = new FrozenPolicyExecution();
    /** C5.1 — snapshot CI thresholds into ci_config_freeze. */
    private ConfigFreeze configFreeze = new ConfigFreeze();
    /** C5.1 — provider adapter SPI (not wired to production flows). */
    private ProviderSpi providerSpi = new ProviderSpi();
    /** C5.1 — persist non-authoritative provider observations. */
    private ProviderObservations providerObservations = new ProviderObservations();
    private Tenant tenant = new Tenant();
    /** C6 — non-authoritative multi-source validation harness. */
    private Validation validation = new Validation();
    /** P0 — AI Policy Studio authoring (never production-active). */
    private PolicyStudio policyStudio = new PolicyStudio();
    /** P1 — Shadow Policy Engine (never production ACTIVE authority). */
    private PolicyEngine policyEngine = new PolicyEngine();
    /** P2 — Shadow Decision Engine (recommendation only, never authoritative). */
    private DecisionEngine decisionEngine = new DecisionEngine();
    /** A1 — Assistive AI underwriter (suggestions only, never authoritative). */
    private AiUnderwriter aiUnderwriter = new AiUnderwriter();
    /** G0 — Production cutover readiness (never ACTIVE / CANONICAL authority). */
    private Cutover cutover = new Cutover();
    /** Staging CEO review demo APIs — never production-authoritative. */
    private StagingDemo stagingDemo = new StagingDemo();
    /** GACAT-PERSISTENCE-1 — DB-backed canonical parameter catalogue. */
    private Gacat gacat = new Gacat();
    private UUID defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** When non-blank, internal CI APIs require matching X-Internal-Token header. */
    private String internalToken = "";
    /**
     * When true, blank internal token fails closed (startup + every internal call).
     * Must be true for production.
     */
    private boolean internalTokenRequired = false;

    @Data
    public static class Foundation {
        private boolean enabled = false;
    }

    @Data
    public static class ShadowEvaluation {
        private boolean enabled = false;
        /** Empty = all tenants when enabled. */
        private List<String> tenantIds = List.of();
        private List<String> productCodes = List.of();
        /** false = sync for deterministic tests */
        private boolean async = false;
        /** KYC-5 — after-KYC Decision Policy shadow evaluation (never authoritative). */
        private boolean kycShadowEnabled = false;
    }

    @Data
    public static class SourceRegistry {
        private boolean enabled = false;
    }

    @Data
    public static class Canonicalization {
        private Bureau bureau = new Bureau();
        private Gst gst = new Gst();
        private Banking banking = new Banking();
        private Tax tax = new Tax();

        @Data
        public static class Bureau {
            private boolean enabled = false;
            private List<String> tenantIds = List.of();
            private List<String> productCodes = List.of();
            private boolean useForShadowRules = false;
            private boolean persistTradelines = true;
            private int freshnessDays = 365;
            private int liveUnsecuredThreshold = 6;
        }

        @Data
        public static class Gst {
            private boolean enabled = false;
            private List<String> tenantIds = List.of();
            private List<String> productCodes = List.of();
            private boolean useForShadowRules = false;
            private boolean persistPeriods = true;
            private int minMonthsForAnnualization = 6;
            private double gstr1Gstr3bVarianceWarningPct = 5.0;
            private double gstr1Gstr3bVarianceMaterialPct = 15.0;
            private double minCompletenessForTrailing12m = 0.75;
            private int filingLagDays = 20;
            /** SCF shadow eligibility threshold (production gap default remains in CreditControl). */
            private BigDecimal turnoverEligibilityThreshold = new BigDecimal("50000000");
        }

        @Data
        public static class Banking {
            private boolean enabled = false;
            private List<String> tenantIds = List.of();
            private List<String> productCodes = List.of();
            private boolean useForShadowRules = false;
            private boolean persistTransactions = true;
            private double minStatementCompleteness = 0.7;
            private double minClassificationCoverage = 0.5;
            private int emiMinOccurrences = 3;
            private double emiRegularityThreshold = 0.7;
            private double odUtilisationWarningPct = 90.0;
            private double cashDepositRatioWarningPct = 0.3;
            /** Optional shadow ABB minimum; null = rule REFER (threshold not configured). */
            private BigDecimal abbMinimum = null;
            private int chequeReturnMax3m = 0;
            private int nachReturnMax3m = 0;
        }

        @Data
        public static class Tax {
            private boolean enabled = false;
            private List<String> tenantIds = List.of();
            private List<String> productCodes = List.of();
            private boolean useForShadowRules = false;
            private boolean persistDetail = true;
            private int minYearsForGrowth = 2;
            private double itr26asVarianceWarningPct = 10.0;
            private double itr26asVarianceMaterialPct = 25.0;
            private double itrAisVarianceWarningPct = 10.0;
            private double itrAisVarianceMaterialPct = 25.0;
            /** Shadow ITR_MIN_ANNUAL_INCOME threshold; production SCF_GAP_ITR_INCOME unchanged. */
            private BigDecimal minIncomeThreshold = new BigDecimal("300000");
            /** Optional; null = rule REFER (threshold not configured). */
            private BigDecimal minTurnoverThreshold = null;
            private boolean minPatPositive = true;
        }
    }

    @Data
    public static class Reconciliation {
        private boolean enabled = false;
        private List<String> tenantIds = List.of();
        private List<String> productCodes = List.of();
        private boolean useForShadowRules = false;
        private EvidenceStrength evidenceStrength = new EvidenceStrength();
        private TurnoverTolerances turnover = new TurnoverTolerances();
        private ObligationTolerances obligation = new ObligationTolerances();
        private TaxTolerances tax = new TaxTolerances();

        @Data
        public static class EvidenceStrength {
            private boolean enabled = false;
        }

        @Data
        public static class TurnoverTolerances {
            private double matchPct = 1.0;
            private double warningPct = 5.0;
            private double materialPct = 15.0;
            private String toleranceVersion = "TURNOVER_TOLERANCE_V1";
        }

        @Data
        public static class ObligationTolerances {
            private double matchPct = 2.0;
            private double warningPct = 5.0;
            private double materialPct = 20.0;
            private String toleranceVersion = "OBLIGATION_TOLERANCE_V1";
        }

        @Data
        public static class TaxTolerances {
            private double matchPct = 1.0;
            private double warningPct = 10.0;
            private double materialPct = 25.0;
            private String toleranceVersion = "TAX_TOLERANCE_V1";
        }
    }

    @Data
    public static class EvaluationContext {
        private boolean enabled = false;
    }

    @Data
    public static class FrozenPolicyExecution {
        private boolean enabled = false;
    }

    @Data
    public static class ConfigFreeze {
        private boolean enabled = false;
    }

    @Data
    public static class ProviderSpi {
        private boolean enabled = false;
    }

    @Data
    public static class ProviderObservations {
        private boolean enabled = false;
    }

    @Data
    public static class Tenant {
        /** When true, never silently default an unknown/null tenant. */
        private boolean requireExplicit = false;
        /** When true (local/tests), allow falling back to defaultTenantId. */
        private boolean devMode = true;
    }

    @Data
    public static class Validation {
        private boolean enabled = false;
        private boolean persistRuns = true;
        private boolean runPerformanceHarness = false;
    }

    @Data
    public static class PolicyStudio {
        private boolean enabled = false;
        private boolean aiEnabled = false;
        private List<String> tenantIds = List.of();
        private boolean requireMakerChecker = true;
    }

    @Data
    public static class PolicyEngine {
        private boolean enabled = false;
        private boolean dslShadowEnabled = false;
        private List<String> tenantIds = List.of();
        private List<String> productCodes = List.of();
        private boolean historicalReplayEnabled = false;
    }

    @Data
    public static class DecisionEngine {
        private boolean enabled = false;
        private boolean shadowEnabled = false;
        private List<String> tenantIds = List.of();
        private List<String> productCodes = List.of();
        private boolean historicalReplayEnabled = false;
    }

    @Data
    public static class AiUnderwriter {
        private boolean enabled = false;
        private List<String> tenantIds = List.of();
        private boolean camDraftEnabled = false;
        private boolean scenarioEnabled = false;
        /** stub | http — default stub so LOS works without AI-LOS up. */
        private String provider = "stub";
        private String httpBaseUrl = "";
    }

    @Data
    public static class Cutover {
        private boolean enabled = false;
        private boolean dualRunEnabled = false;
        private boolean quarantineEnabled = false;
        private List<String> tenantIds = List.of();
        private List<String> productCodes = List.of();
        /** MUST stay false in G0 / G0.1. */
        private boolean allowCanonicalAuthority = false;
        /** Minimum REAL_DEV + STORED_PROVIDER cases for LIMITED_PILOT_READY (§4/§34). */
        private int minRealOrStoredCases = 20;
        /** Apply CUTOVER_VALIDATION_EVIDENCE_V1 weights when scoring evidence. */
        private boolean evidenceWeightingEnabled = true;
    }

    @Data
    public static class StagingDemo {
        /** When true, expose /staging-demo CEO review APIs (isolated staging only). */
        private boolean enabled = false;
    }

    @Data
    public static class Gacat {
        /**
         * When true, startup fails if catalogue cannot be loaded from DB.
         * Staging/prod force this behaviour regardless; never silent Java-seed fallback.
         */
        private boolean requireDatabase = false;
        /**
         * Allow in-memory Java seed only for unit tests / local profiles when DB absent.
         * Ignored when requireDatabase=true or staging/prod profiles are active.
         */
        private boolean allowSeedFallback = true;
    }
}
