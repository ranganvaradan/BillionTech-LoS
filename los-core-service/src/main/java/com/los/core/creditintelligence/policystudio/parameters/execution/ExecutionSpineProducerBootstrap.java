package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Registers Phase-1 producers. Exact BMS IDs only; RAW fact readers; authored dynamic;
 * MANUAL; banking EMI/ADB adapters. Does not register PolicyBureauMetricService stubs
 * for overdue.age / related overdue amount IDs.
 */
@Component
@RequiredArgsConstructor
public class ExecutionSpineProducerBootstrap {

    /** RAW IDs with a known EvaluationContext fact key (= exact canonical ID). */
    public static final Set<String> RAW_FACT_IDS = Set.of(
            "bureau.score",
            "bureau.tradeline.suit_filed",
            "bureau.tradeline.payment_history",
            "bureau.tradeline.dpd_month",
            "application.proposed_edi",
            "application.business_vintage_months",
            "application.foir",
            "obligation.ratio",
            "collateral.ltv",
            "banking.txn_count_3m",
            "banking.settlement.count_monthly_avg_3m",
            "banking.inward_return_count_3m",
            "reconciliation.bank_gst_ratio",
            "bureau.inquiries.current_month",
            "bureau.inquiries.current_month_count"
    );

    private final ProducerRegistry registry;
    private final DerivedCalculationDefinitionService definitionService;

    @PostConstruct
    public void registerProducers() {
        registerDefaults(registry, definitionService);
    }

    /** Shared registration for Spring boot and focused unit tests. */
    public static void registerDefaults(
            ProducerRegistry registry, DerivedCalculationDefinitionService definitionService) {
        registerDefaults(registry, definitionService == null
                ? (id, t) -> Optional.empty()
                : definitionService::latestFor);
    }

    public static void registerDefaults(
            ProducerRegistry registry, AuthoredDerivedProducer.AuthoredDefinitionSource authored) {
        RawFactProducer raw = new RawFactProducer(RAW_FACT_IDS);
        for (String id : RAW_FACT_IDS) {
            registry.registerExact(id, raw);
        }

        BuiltInBureauMetricProducer bms = new BuiltInBureauMetricProducer();
        for (String id : BuiltInBureauMetricProducer.EMITTED_IDS) {
            registry.registerExact(id, bms);
        }

        BuiltInBankingMetricProducer banking = new BuiltInBankingMetricProducer();
        registry.registerExact(BuiltInBankingMetricProducer.EMI_BOUNCE, banking);
        registry.registerExact(BuiltInBankingMetricProducer.ADB_3M, banking);

        registry.registerDynamic(new AuthoredDerivedProducer(authored));
        registry.registerDynamic(new ManualInputProducer());
    }

    public static CanonicalParameterExecutionService standalone(
            DerivedCalculationDefinitionService definitionService) {
        return standalone(definitionService == null
                ? (id, t) -> Optional.empty()
                : definitionService::latestFor);
    }

    public static CanonicalParameterExecutionService standalone(
            AuthoredDerivedProducer.AuthoredDefinitionSource authored) {
        ProducerRegistry registry = new ProducerRegistry();
        registerDefaults(registry, authored);
        return new CanonicalParameterExecutionService(registry);
    }
}
