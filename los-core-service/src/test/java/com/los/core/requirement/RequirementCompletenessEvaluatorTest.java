package com.los.core.requirement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused pure unit tests for completeness + customer-unresolved invariants.
 */
class RequirementCompletenessEvaluatorTest {

    private final RequirementCompletenessEvaluator evaluator = new RequirementCompletenessEvaluator();

    private RequirementItemEntity item(String key, RequirementClass clazz, boolean required,
                                       CustomerFulfilmentState f, DataReadinessState r,
                                       FulfilmentMode... modes) {
        return RequirementItemEntity.builder()
                .itemKey(key)
                .requirementType(RequirementType.CANONICAL_PARAMETER)
                .requirementClass(clazz)
                .required(required)
                .customerFulfilmentState(f)
                .dataReadinessState(r)
                .sourceAcquisitionState(SourceAcquisitionState.NOT_STARTED)
                .allowedFulfilmentModes(List.of(modes))
                .build();
    }

    @Test
    void allReady_complete() {
        var r = evaluator.evaluate(List.of(
                item("a", RequirementClass.ALREADY_AVAILABLE, true,
                        CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY,
                        FulfilmentMode.DERIVATION)));
        assertEquals(CompletenessStatus.COMPLETE_FOR_NEXT_STAGE, r.status());
        assertEquals(0, r.customerUnresolvedCount());
    }

    @Test
    void providedButProcessing_processingStatus() {
        var r = evaluator.evaluate(List.of(
                item("b", RequirementClass.CUSTOMER_PROVIDED, true,
                        CustomerFulfilmentState.PROVIDED, DataReadinessState.PROCESSING,
                        FulfilmentMode.DOCUMENT_UPLOAD)));
        assertEquals(CompletenessStatus.PROCESSING, r.status());
        assertEquals(0, r.customerUnresolvedCount());
    }

    @Test
    void waivedRequired_countsAsSatisfied() {
        var r = evaluator.evaluate(List.of(
                item("w", RequirementClass.CUSTOMER_PROVIDED, true,
                        CustomerFulfilmentState.WAIVED, DataReadinessState.NOT_AVAILABLE,
                        FulfilmentMode.DIRECT_INPUT)));
        assertEquals(CompletenessStatus.COMPLETE_FOR_NEXT_STAGE, r.status());
    }

    @Test
    void failedBlocksEvenIfOthersReady() {
        var r = evaluator.evaluate(List.of(
                item("ok", RequirementClass.ALREADY_AVAILABLE, true,
                        CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.READY_FOR_POLICY,
                        FulfilmentMode.DERIVATION),
                item("bad", RequirementClass.AUTO_SOURCE, true,
                        CustomerFulfilmentState.NOT_APPLICABLE, DataReadinessState.FAILED,
                        FulfilmentMode.AUTOMATIC_SOURCE)));
        assertEquals(CompletenessStatus.BLOCKED, r.status());
    }
}
