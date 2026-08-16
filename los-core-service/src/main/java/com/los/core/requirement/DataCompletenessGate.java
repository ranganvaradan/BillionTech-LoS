package com.los.core.requirement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative application-level Data Completeness Gate (W6).
 * Evaluates RequirementPlan semantics — not item-count alone.
 * Does not invoke Policy or Scorecard.
 */
@Component
@RequiredArgsConstructor
public class DataCompletenessGate {

    private static final Set<SourceAcquisitionState> IN_FLIGHT_SOURCE = EnumSet.of(
            SourceAcquisitionState.QUEUED,
            SourceAcquisitionState.IN_PROGRESS
    );

    private static final Set<DataReadinessState> IN_FLIGHT_READINESS = EnumSet.of(
            DataReadinessState.PROCESSING,
            DataReadinessState.EXTRACTED,
            DataReadinessState.VERIFIED
    );

    private final RequirementCompletenessEvaluator legacyEvaluator;

    public AcquisitionDtos.GateResult evaluate(RequirementPlanEntity plan) {
        List<RequirementItemEntity> items = plan != null && plan.getItems() != null
                ? plan.getItems() : List.of();
        return evaluate(items);
    }

    public AcquisitionDtos.GateResult evaluate(List<RequirementItemEntity> items) {
        List<RequirementItemEntity> list = items != null ? items : List.of();
        List<String> reasons = new ArrayList<>();

        int autoTotal = 0, autoCompleted = 0, autoProcessing = 0, autoFailed = 0;
        int custTotal = 0, custProvided = 0, custOutstanding = 0;
        int derTotal = 0, derReady = 0, derWaiting = 0;
        int blocked = 0, manualReview = 0;
        int requiredMissing = 0;
        boolean noFulfilmentPath = false;
        boolean acquisitionInProgress = false;
        boolean waitingCustomer = false;
        boolean hasManual = false;

        for (RequirementItemEntity item : list) {
            FulfilmentMode mode = AcquisitionSourceResolver.effectiveMode(item);
            SourceAcquisitionState src = item.getSourceAcquisitionState();
            DataReadinessState ready = item.getDataReadinessState();

            if ("NO_FULFILMENT_PATH".equals(String.valueOf(
                    item.getSourceHints() != null ? item.getSourceHints().get("blockingReason") : null))
                    && item.isRequired()) {
                noFulfilmentPath = true;
                blocked++;
                reasons.add(item.getItemKey() + ": NO_FULFILMENT_PATH");
            }

            if (mode == FulfilmentMode.AUTOMATIC_SOURCE
                    || item.getRequirementClass() == RequirementClass.AUTO_SOURCE) {
                autoTotal++;
                if (src != null && src.isTerminalSuccess() && ready == DataReadinessState.READY_FOR_POLICY) {
                    autoCompleted++;
                } else if (src != null && (src.isInFlight() || IN_FLIGHT_READINESS.contains(ready))) {
                    autoProcessing++;
                    acquisitionInProgress = true;
                } else if (src != null && (src.isTerminalFailure() || src.isRetryableFailure())) {
                    autoFailed++;
                } else if (src != null && src.requiresCustomerAction()) {
                    waitingCustomer = true;
                }
            }

            if (mode == FulfilmentMode.DERIVATION
                    || item.getRequirementClass() == RequirementClass.DERIVABLE) {
                derTotal++;
                if (ready == DataReadinessState.READY_FOR_POLICY) {
                    derReady++;
                } else if (!AcquisitionSourceResolver.dependencyParameterIds(item).isEmpty()
                        && ready != DataReadinessState.READY_FOR_POLICY) {
                    derWaiting++;
                }
            }

            boolean customerFacing = item.getRequirementClass() == RequirementClass.CUSTOMER_PROVIDED
                    || ((item.allows(FulfilmentMode.DIRECT_INPUT) || item.allows(FulfilmentMode.DOCUMENT_UPLOAD))
                    && !item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)
                    && !item.allowsOnly(FulfilmentMode.DERIVATION)
                    && item.getRequirementClass() != RequirementClass.ALREADY_AVAILABLE
                    && item.getRequirementClass() != RequirementClass.AUTO_SOURCE
                    && item.getRequirementClass() != RequirementClass.DERIVABLE);
            if (customerFacing) {
                custTotal++;
                if (item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                        || item.getCustomerFulfilmentState() == CustomerFulfilmentState.WAIVED) {
                    custProvided++;
                }
                if (RequirementCompletenessEvaluator.isCustomerUnresolved(item)
                        || (src != null && src.requiresCustomerAction()
                        && item.getDataReadinessState() != DataReadinessState.READY_FOR_POLICY)) {
                    custOutstanding++;
                    waitingCustomer = true;
                }
            }

            if (src != null && src.requiresManualReview()) {
                hasManual = true;
                manualReview++;
                reasons.add(item.getItemKey() + ": MANUAL_REVIEW_REQUIRED");
            }
            if (src != null && IN_FLIGHT_SOURCE.contains(src)) {
                acquisitionInProgress = true;
            }
            if (IN_FLIGHT_READINESS.contains(ready)
                    || item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                    && ready != DataReadinessState.READY_FOR_POLICY
                    && ready != DataReadinessState.FAILED
                    && ready != DataReadinessState.DATA_INSUFFICIENT) {
                if (item.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                        && ready != DataReadinessState.READY_FOR_POLICY) {
                    acquisitionInProgress = true;
                }
            }

            if (item.isRequired() && !isSatisfied(item)) {
                requiredMissing++;
                if (ready == DataReadinessState.FAILED
                        && item.getCustomerFulfilmentState() != CustomerFulfilmentState.WAIVED) {
                    blocked++;
                    reasons.add(item.getItemKey() + ": required FAILED");
                } else if (ready == DataReadinessState.DATA_INSUFFICIENT) {
                    Object spineStatus = item.getProvenance() != null
                            ? item.getProvenance().get("spineExecutionStatus") : null;
                    Object acqVs = item.getProvenance() != null
                            ? item.getProvenance().get("acquisitionVsParameter") : null;
                    if (acqVs != null || spineStatus != null) {
                        reasons.add(item.getItemKey() + ": SOURCE_ACQUIRED≠PARAMETER_RESOLVED"
                                + (spineStatus != null ? " (" + spineStatus + ")" : ""));
                    } else {
                        reasons.add(item.getItemKey() + ": DATA_INSUFFICIENT (source success ≠ ready)");
                    }
                } else if (RequirementCompletenessEvaluator.isCustomerUnresolved(item)
                        || (src != null && src.requiresCustomerAction())) {
                    reasons.add(item.getItemKey() + ": waiting for customer");
                } else if (src != null && src.isInFlight()) {
                    reasons.add(item.getItemKey() + ": acquisition in progress");
                } else {
                    reasons.add(item.getItemKey() + ": not READY_FOR_POLICY (" + ready + ")");
                }
            }
        }

        // Optional vs required: optional NOT_APPLICABLE does not block
        DataCompletenessGateStatus status;
        if (noFulfilmentPath || blocked > 0) {
            status = DataCompletenessGateStatus.BLOCKED;
        } else if (hasManual && requiredMissing > 0) {
            status = DataCompletenessGateStatus.MANUAL_REVIEW_REQUIRED;
        } else if (requiredMissing == 0) {
            status = DataCompletenessGateStatus.READY_FOR_POLICY;
            if (reasons.isEmpty()) {
                reasons.add("All required Policy parameters have usable canonical facts");
            }
        } else if (waitingCustomer && !acquisitionInProgress) {
            status = DataCompletenessGateStatus.WAITING_FOR_CUSTOMER;
        } else if (waitingCustomer && acquisitionInProgress) {
            // Customer outstanding but autos still running — overall wait for customer once autos done;
            // while autos run, expose ACQUISITION_IN_PROGRESS if any required auto in flight,
            // else WAITING_FOR_CUSTOMER (12-item golden: autos done + 1 customer ⇒ WAITING)
            boolean requiredAutoInFlight = list.stream().anyMatch(i ->
                    i.isRequired()
                            && i.getDataReadinessState() != DataReadinessState.READY_FOR_POLICY
                            && i.getSourceAcquisitionState() != null
                            && i.getSourceAcquisitionState().isInFlight());
            boolean requiredDocProcessing = list.stream().anyMatch(i ->
                    i.isRequired()
                            && i.getCustomerFulfilmentState() == CustomerFulfilmentState.PROVIDED
                            && i.getDataReadinessState() != DataReadinessState.READY_FOR_POLICY
                            && i.getDataReadinessState() != DataReadinessState.DATA_INSUFFICIENT
                            && i.getDataReadinessState() != DataReadinessState.FAILED);
            if (requiredAutoInFlight || requiredDocProcessing) {
                status = DataCompletenessGateStatus.ACQUISITION_IN_PROGRESS;
            } else {
                status = DataCompletenessGateStatus.WAITING_FOR_CUSTOMER;
            }
        } else if (acquisitionInProgress) {
            status = DataCompletenessGateStatus.ACQUISITION_IN_PROGRESS;
        } else if (hasManual) {
            status = DataCompletenessGateStatus.MANUAL_REVIEW_REQUIRED;
        } else {
            status = DataCompletenessGateStatus.BLOCKED;
            if (reasons.isEmpty()) {
                reasons.add("Required data incomplete without customer or in-flight acquisition");
            }
        }

        AcquisitionDtos.OrchestrationCounters counters = new AcquisitionDtos.OrchestrationCounters(
                autoTotal, autoCompleted, autoProcessing, autoFailed,
                custTotal, custProvided, custOutstanding,
                derTotal, derReady, derWaiting,
                blocked, manualReview);

        // Retain legacy evaluator diagnostics without driving gate alone
        RequirementDtos.CompletenessResult legacy = legacyEvaluator.evaluate(list);
        Map<String, Object> ignored = new LinkedHashMap<>();
        ignored.put("legacyCompleteness", legacy.status().name());

        return new AcquisitionDtos.GateResult(
                status,
                List.copyOf(reasons),
                counters,
                false,
                false);
    }

    private static boolean isSatisfied(RequirementItemEntity item) {
        CustomerFulfilmentState f = item.getCustomerFulfilmentState();
        if (f == CustomerFulfilmentState.WAIVED) {
            return true;
        }
        // Optional factors marked N/A may later renormalize in Scorecard — do not block W6 gate
        if (f == CustomerFulfilmentState.NOT_APPLICABLE && !item.isRequired()) {
            return true;
        }
        // Required auto/derivation items use NOT_APPLICABLE fulfilment (customer not asked)
        // but still need usable canonical facts — for GACAT IDs that means spine VALUE_AVAILABLE
        // (READY_FOR_POLICY), not mere source acquisition success.
        return item.getDataReadinessState() == DataReadinessState.READY_FOR_POLICY;
    }
}
