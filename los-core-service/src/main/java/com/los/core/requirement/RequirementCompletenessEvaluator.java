package com.los.core.requirement;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates plan completeness for the next stage gate (no Policy invocation).
 */
@Component
public class RequirementCompletenessEvaluator {

    private static final Set<DataReadinessState> IN_FLIGHT = EnumSet.of(
            DataReadinessState.PROCESSING,
            DataReadinessState.EXTRACTED,
            DataReadinessState.VERIFIED
    );

    public RequirementDtos.CompletenessResult evaluate(RequirementPlanEntity plan) {
        return evaluate(plan != null ? plan.getItems() : List.of());
    }

    public RequirementDtos.CompletenessResult evaluate(List<RequirementItemEntity> items) {
        List<RequirementItemEntity> list = items != null ? items : List.of();
        List<String> reasons = new ArrayList<>();
        int blocked = 0;
        int notReady = 0;
        int processing = 0;
        int unresolvedCustomer = 0;

        for (RequirementItemEntity item : list) {
            if (isCustomerUnresolved(item)) {
                unresolvedCustomer++;
            }
            if (!item.isRequired()) {
                continue;
            }
            DataReadinessState readiness = item.getDataReadinessState();
            // Required FAILED blocks even when fulfilment is NOT_APPLICABLE (never asked / auto path).
            // WAIVED is the only fulfilment that clears a failed required item.
            if (readiness == DataReadinessState.FAILED
                    && item.getCustomerFulfilmentState() != CustomerFulfilmentState.WAIVED) {
                blocked++;
                reasons.add(item.getItemKey() + ": required readiness FAILED");
                continue;
            }
            if (isSatisfiedWithoutReadiness(item)) {
                continue;
            }
            if (item.getRequirementClass() == RequirementClass.UNAVAILABLE_BLOCKER
                    && item.getCustomerFulfilmentState() != CustomerFulfilmentState.WAIVED
                    && item.getCustomerFulfilmentState() != CustomerFulfilmentState.NOT_APPLICABLE) {
                blocked++;
                reasons.add(item.getItemKey() + ": UNAVAILABLE_BLOCKER");
            } else if (readiness != DataReadinessState.READY_FOR_POLICY) {
                notReady++;
                if (IN_FLIGHT.contains(readiness)
                        || item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED) {
                    processing++;
                    reasons.add(item.getItemKey() + ": readiness " + readiness);
                } else if (isCustomerUnresolved(item)) {
                    reasons.add(item.getItemKey() + ": customer unresolved (" + item.getCustomerFulfilmentState() + ")");
                } else {
                    reasons.add(item.getItemKey() + ": not READY_FOR_POLICY (" + readiness + ")");
                }
            }
        }

        CompletenessStatus status;
        if (blocked > 0) {
            status = CompletenessStatus.BLOCKED;
        } else if (notReady == 0) {
            status = CompletenessStatus.COMPLETE_FOR_NEXT_STAGE;
            if (reasons.isEmpty()) {
                reasons.add("All required items READY_FOR_POLICY or waived/N/A");
            }
        } else if (processing > 0 && unresolvedCustomer == 0) {
            status = CompletenessStatus.PROCESSING;
        } else if (unresolvedCustomer > 0) {
            status = CompletenessStatus.INCOMPLETE;
        } else {
            status = CompletenessStatus.INCOMPLETE;
        }

        return new RequirementDtos.CompletenessResult(
                status, List.copyOf(reasons), unresolvedCustomer, notReady, blocked);
    }

    /**
     * Customer still needs to act — excludes already-satisfied canonicals never asked.
     */
    public static boolean isCustomerUnresolved(RequirementItemEntity item) {
        if (item == null) {
            return false;
        }
        CustomerFulfilmentState f = item.getCustomerFulfilmentState();
        if (f == CustomerFulfilmentState.PROVIDED
                || f == CustomerFulfilmentState.WAIVED
                || f == CustomerFulfilmentState.NOT_APPLICABLE) {
            return false;
        }
        if (item.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY
                && (item.getRequirementClass() == RequirementClass.ALREADY_AVAILABLE
                || item.getRequirementClass() == RequirementClass.DERIVABLE
                || item.getRequirementClass() == RequirementClass.AUTO_SOURCE)) {
            return false;
        }
        if (item.getRequirementClass() == RequirementClass.CUSTOMER_PROVIDED) {
            return f == CustomerFulfilmentState.REQUIRED
                    || f == CustomerFulfilmentState.REQUESTED
                    || f == CustomerFulfilmentState.REUPLOAD_REQUIRED;
        }
        // Direct/document modes pending without CUSTOMER_PROVIDED class still count if asked
        boolean customerMode = item.allows(FulfilmentMode.DIRECT_INPUT) || item.allows(FulfilmentMode.DOCUMENT_UPLOAD);
        boolean autoOnly = item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)
                || item.allowsOnly(FulfilmentMode.DERIVATION);
        if (autoOnly) {
            return false;
        }
        return customerMode && (f == CustomerFulfilmentState.REQUIRED
                || f == CustomerFulfilmentState.REQUESTED
                || f == CustomerFulfilmentState.REUPLOAD_REQUIRED);
    }

    private static boolean isSatisfiedWithoutReadiness(RequirementItemEntity item) {
        CustomerFulfilmentState f = item.getCustomerFulfilmentState();
        return f == CustomerFulfilmentState.WAIVED || f == CustomerFulfilmentState.NOT_APPLICABLE;
    }
}
