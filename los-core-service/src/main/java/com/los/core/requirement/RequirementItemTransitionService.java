package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies fulfilment / readiness / source transitions with W3 invariants.
 * PROVIDED ≠ READY_FOR_POLICY. Document upload ⇒ PROVIDED immediately.
 */
@Service
@RequiredArgsConstructor
public class RequirementItemTransitionService {

    private final RequirementItemRepository itemRepository;
    private final RequirementStateTransitionRepository transitionRepository;

    @Transactional
    public List<RequirementItemEntity> markDocumentUploaded(
            UUID planId,
            UUID itemId,
            String documentRef,
            List<UUID> extraItemIds,
            String actor,
            String reason) {

        if (documentRef == null || documentRef.isBlank()) {
            throw new BusinessRuleException("documentRef is required for document upload");
        }

        Set<UUID> targetIds = new LinkedHashSet<>();
        if (itemId != null) {
            targetIds.add(itemId);
        }
        if (extraItemIds != null) {
            targetIds.addAll(extraItemIds);
        }

        List<RequirementItemEntity> targets = new ArrayList<>();
        if (!targetIds.isEmpty()) {
            for (UUID id : targetIds) {
                RequirementItemEntity item = loadItem(planId, id);
                targets.add(item);
            }
            // Expand to siblings sharing the same pending document group (multi-parameter document).
            UUID effectivePlanId = planId;
            if (effectivePlanId == null && targets.get(0).getPlan() != null) {
                effectivePlanId = targets.get(0).getPlan().getId();
            }
            if (effectivePlanId != null) {
                String groupKey = documentRef;
                Object hint = targets.get(0).getSourceHints() != null
                        ? targets.get(0).getSourceHints().get("pendingDocumentGroup") : null;
                if (hint != null) {
                    groupKey = hint.toString();
                }
                for (RequirementItemEntity sibling : itemRepository.findByPlanIdOrderBySortOrderAscCreatedAtAsc(effectivePlanId)) {
                    if (targetIds.contains(sibling.getId())) {
                        continue;
                    }
                    if (matchesDocumentGroup(sibling, groupKey) && sibling.allows(FulfilmentMode.DOCUMENT_UPLOAD)) {
                        targets.add(sibling);
                    }
                }
            }
        } else {
            // Match pending document group by existing document_ref OR items that allow DOCUMENT_UPLOAD
            // and share the same pending document group key in sourceHints / provenance.
            List<RequirementItemEntity> byRef = planId != null
                    ? itemRepository.findByPlanIdAndDocumentRef(planId, documentRef)
                    : itemRepository.findByDocumentRef(documentRef);
            if (!byRef.isEmpty()) {
                targets.addAll(byRef);
            } else if (planId != null) {
                for (RequirementItemEntity item : itemRepository.findByPlanIdOrderBySortOrderAscCreatedAtAsc(planId)) {
                    if (matchesDocumentGroup(item, documentRef) && item.allows(FulfilmentMode.DOCUMENT_UPLOAD)) {
                        targets.add(item);
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            throw new BusinessRuleException("No requirement items matched for document upload");
        }

        Instant now = Instant.now();
        for (RequirementItemEntity item : targets) {
            if (!item.allows(FulfilmentMode.DOCUMENT_UPLOAD)) {
                throw new BusinessRuleException(
                        "Item " + item.getItemKey() + " does not allow DOCUMENT_UPLOAD",
                        "FULFILMENT_MODE_NOT_ALLOWED", "document-upload", null);
            }
            if (item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)) {
                throw new BusinessRuleException(
                        "Automatic-only item cannot be fulfilled by document upload: " + item.getItemKey(),
                        "AUTOMATIC_ONLY", "document-upload", null);
            }

            CustomerFulfilmentState fromFulfilment = item.getCustomerFulfilmentState();
            DataReadinessState fromReadiness = item.getDataReadinessState();

            item.setCustomerFulfilmentState(CustomerFulfilmentState.PROVIDED);
            item.setFulfilmentModeUsed(FulfilmentMode.DOCUMENT_UPLOAD);
            item.setDocumentRef(documentRef);
            item.setEvidenceRef(documentRef);
            item.setProvidedAt(now);

            // Never auto READY_FOR_POLICY on upload. Move NOT_AVAILABLE → PROCESSING.
            if (fromReadiness == DataReadinessState.NOT_AVAILABLE
                    || fromReadiness == null) {
                item.setDataReadinessState(DataReadinessState.PROCESSING);
                audit(item.getId(), RequirementStateTransitionEntity.FIELD_READINESS,
                        fromReadiness != null ? fromReadiness.name() : null,
                        DataReadinessState.PROCESSING.name(),
                        reason != null ? reason : "document uploaded — extraction pending",
                        actor, documentRef);
            }

            audit(item.getId(), RequirementStateTransitionEntity.FIELD_FULFILMENT,
                    fromFulfilment != null ? fromFulfilment.name() : null,
                    CustomerFulfilmentState.PROVIDED.name(),
                    reason != null ? reason : "document uploaded",
                    actor, documentRef);

            itemRepository.save(item);
        }
        return targets;
    }

    @Transactional
    public RequirementItemEntity markDirectInput(UUID planId, UUID itemId, String valueRef,
                                                 boolean verified, String actor, String reason) {
        RequirementItemEntity item = loadItem(planId, itemId);

        if (item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)) {
            throw new BusinessRuleException(
                    "Automatic-only requirement cannot use DIRECT_INPUT: " + item.getItemKey(),
                    "AUTOMATIC_ONLY_NO_DIRECT_INPUT", "direct-input", null);
        }
        if (!item.allows(FulfilmentMode.DIRECT_INPUT)) {
            throw new BusinessRuleException(
                    "Item " + item.getItemKey() + " does not allow DIRECT_INPUT",
                    "FULFILMENT_MODE_NOT_ALLOWED", "direct-input", null);
        }

        CustomerFulfilmentState fromFulfilment = item.getCustomerFulfilmentState();
        DataReadinessState fromReadiness = item.getDataReadinessState();

        item.setCustomerFulfilmentState(CustomerFulfilmentState.PROVIDED);
        item.setFulfilmentModeUsed(FulfilmentMode.DIRECT_INPUT);
        item.setProvidedAt(Instant.now());
        if (valueRef != null && !valueRef.isBlank()) {
            item.setEvidenceRef(valueRef);
        }

        DataReadinessState toReadiness;
        if (verified) {
            toReadiness = DataReadinessState.READY_FOR_POLICY;
        } else if (item.allows(FulfilmentMode.MANUAL_REVIEW)
                || item.getRequirementClass() == RequirementClass.MANUAL_REVIEW) {
            toReadiness = DataReadinessState.PROCESSING;
            item.setSourceAcquisitionState(SourceAcquisitionState.MANUAL_REVIEW);
            audit(item.getId(), RequirementStateTransitionEntity.FIELD_SOURCE,
                    null, SourceAcquisitionState.MANUAL_REVIEW.name(),
                    "direct input requires manual review", actor, valueRef);
        } else {
            toReadiness = DataReadinessState.PROCESSING;
        }
        item.setDataReadinessState(toReadiness);

        audit(item.getId(), RequirementStateTransitionEntity.FIELD_FULFILMENT,
                fromFulfilment != null ? fromFulfilment.name() : null,
                CustomerFulfilmentState.PROVIDED.name(),
                reason != null ? reason : "direct input",
                actor, valueRef);
        if (fromReadiness != toReadiness) {
            audit(item.getId(), RequirementStateTransitionEntity.FIELD_READINESS,
                    fromReadiness != null ? fromReadiness.name() : null,
                    toReadiness.name(),
                    verified ? "direct input verified" : "direct input pending verification",
                    actor, valueRef);
        }

        return itemRepository.save(item);
    }

    @Transactional
    public RequirementItemEntity advanceReadiness(UUID planId, UUID itemId,
                                                  DataReadinessState newState,
                                                  String actor, String reason) {
        if (newState == null) {
            throw new BusinessRuleException("readiness state is required");
        }
        RequirementItemEntity item = loadItem(planId, itemId);
        DataReadinessState from = item.getDataReadinessState();
        if (from == newState) {
            return item;
        }
        item.setDataReadinessState(newState);
        audit(item.getId(), RequirementStateTransitionEntity.FIELD_READINESS,
                from != null ? from.name() : null, newState.name(), reason, actor, item.getEvidenceRef());
        return itemRepository.save(item);
    }

    @Transactional
    public RequirementItemEntity advanceSource(UUID planId, UUID itemId,
                                               SourceAcquisitionState newState,
                                               String actor, String reason) {
        if (newState == null) {
            throw new BusinessRuleException("source state is required");
        }
        RequirementItemEntity item = loadItem(planId, itemId);

        if (newState == SourceAcquisitionState.CUSTOMER_FALLBACK
                && item.allowsOnly(FulfilmentMode.AUTOMATIC_SOURCE)) {
            throw new BusinessRuleException(
                    "Cannot set CUSTOMER_FALLBACK when AUTOMATIC_SOURCE is the sole allowed mode: "
                            + item.getItemKey(),
                    "AUTOMATIC_ONLY_NO_CUSTOMER_FALLBACK", "source-advance", null);
        }

        SourceAcquisitionState from = item.getSourceAcquisitionState();
        if (from == newState) {
            return item;
        }
        item.setSourceAcquisitionState(newState);

        if (newState == SourceAcquisitionState.UNAVAILABLE
                && (item.getDataReadinessState() == DataReadinessState.NOT_AVAILABLE
                || item.getDataReadinessState() == null)) {
            // Keep readiness NOT_AVAILABLE or bump to DATA_INSUFFICIENT for clarity — do not invent DIRECT_INPUT.
            item.setDataReadinessState(DataReadinessState.DATA_INSUFFICIENT);
            audit(item.getId(), RequirementStateTransitionEntity.FIELD_READINESS,
                    DataReadinessState.NOT_AVAILABLE.name(),
                    DataReadinessState.DATA_INSUFFICIENT.name(),
                    reason != null ? reason : "source unavailable",
                    actor, null);
        }

        audit(item.getId(), RequirementStateTransitionEntity.FIELD_SOURCE,
                from != null ? from.name() : null, newState.name(), reason, actor, null);
        return itemRepository.save(item);
    }

    @Transactional
    public RequirementItemEntity markWaived(UUID planId, UUID itemId, String actor, String reason) {
        RequirementItemEntity item = loadItem(planId, itemId);
        CustomerFulfilmentState from = item.getCustomerFulfilmentState();
        item.setCustomerFulfilmentState(CustomerFulfilmentState.WAIVED);
        audit(item.getId(), RequirementStateTransitionEntity.FIELD_FULFILMENT,
                from != null ? from.name() : null, CustomerFulfilmentState.WAIVED.name(),
                reason != null ? reason : "waived", actor, null);
        return itemRepository.save(item);
    }

    @Transactional
    public RequirementItemEntity markNotApplicable(UUID planId, UUID itemId, String actor, String reason) {
        RequirementItemEntity item = loadItem(planId, itemId);
        if (item.isRequired()) {
            throw new BusinessRuleException(
                    "NOT_APPLICABLE is only allowed for optional requirements: " + item.getItemKey(),
                    "REQUIRED_CANNOT_BE_NA", "mark-na", null);
        }
        CustomerFulfilmentState from = item.getCustomerFulfilmentState();
        item.setCustomerFulfilmentState(CustomerFulfilmentState.NOT_APPLICABLE);
        audit(item.getId(), RequirementStateTransitionEntity.FIELD_FULFILMENT,
                from != null ? from.name() : null, CustomerFulfilmentState.NOT_APPLICABLE.name(),
                reason != null ? reason : "not applicable", actor, null);
        return itemRepository.save(item);
    }

    private RequirementItemEntity loadItem(UUID planId, UUID itemId) {
        if (itemId == null) {
            throw new BusinessRuleException("itemId is required");
        }
        if (planId != null) {
            return itemRepository.findByIdAndPlanId(itemId, planId)
                    .orElseThrow(() -> new BusinessRuleException("Requirement item not found: " + itemId));
        }
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessRuleException("Requirement item not found: " + itemId));
    }

    private void audit(UUID itemId, String field, String from, String to,
                       String reason, String actor, String evidenceRef) {
        transitionRepository.save(RequirementStateTransitionEntity.builder()
                .itemId(itemId)
                .fieldName(field)
                .fromState(from)
                .toState(to)
                .reason(reason)
                .actor(actor != null ? actor : "system")
                .evidenceRef(evidenceRef)
                .build());
    }

    private static boolean matchesDocumentGroup(RequirementItemEntity item, String documentRef) {
        if (documentRef.equals(item.getDocumentRef())) {
            return true;
        }
        Object group = item.getSourceHints() != null ? item.getSourceHints().get("pendingDocumentGroup") : null;
        if (documentRef.equals(group)) {
            return true;
        }
        Object prov = item.getProvenance() != null ? item.getProvenance().get("documentGroup") : null;
        return documentRef.equals(prov);
    }
}
