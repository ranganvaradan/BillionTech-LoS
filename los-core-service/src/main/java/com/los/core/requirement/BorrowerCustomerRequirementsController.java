package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import com.los.core.service.borrower.BorrowerPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * W5 — borrower-facing customer requirements from W4 RequirementPlan.
 */
@RestController
@RequestMapping("/api/v1/borrower/applications/{applicationId}/customer-requirements")
@RequiredArgsConstructor
public class BorrowerCustomerRequirementsController {

    private final CustomerRequirementsViewService viewService;
    private final RequirementPlanRepository planRepository;
    private final RequirementItemTransitionService transitionService;
    private final BorrowerPortalService borrowerPortalService;

    @GetMapping
    public CustomerRequirementDtos.CustomerRequirementsView get(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = parseUser(userId);
        borrowerPortalService.requireBorrower(role);
        borrowerPortalService.applicationDetail(uid, applicationId, false);
        return viewService.viewForApplication(applicationId);
    }

    @PostMapping("/actions/{itemId}/direct-input")
    public CustomerRequirementDtos.CustomerRequirementsView submitDirect(
            @PathVariable UUID applicationId,
            @PathVariable UUID itemId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody CustomerRequirementDtos.DirectInputSubmitRequest body) {
        UUID uid = parseUser(userId);
        borrowerPortalService.requireBorrower(role);
        borrowerPortalService.applicationDetail(uid, applicationId, false);
        try {
            RequirementPlanEntity plan = requirePlan(applicationId);
            String actor = uid + "|CUSTOMER";
            if (body != null && Boolean.TRUE.equals(body.saveDraftOnly())) {
                transitionService.saveDirectInputDraft(plan.getId(), itemId, body.value(), actor, "CUSTOMER");
            } else {
                transitionService.markDirectInput(plan.getId(), itemId,
                        body != null ? body.value() : null, false, actor,
                        body != null ? body.reason() : null);
            }
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/actions/{itemId}/document")
    public CustomerRequirementDtos.CustomerRequirementsView submitDocument(
            @PathVariable UUID applicationId,
            @PathVariable UUID itemId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody CustomerRequirementDtos.DocumentFulfilRequest body) {
        UUID uid = parseUser(userId);
        borrowerPortalService.requireBorrower(role);
        borrowerPortalService.applicationDetail(uid, applicationId, false);
        try {
            RequirementPlanEntity plan = requirePlan(applicationId);
            if (body == null || body.documentRef() == null || body.documentRef().isBlank()) {
                throw new BusinessRuleException("documentRef is required");
            }
            String actor = uid + "|CUSTOMER";
            transitionService.markDocumentUploaded(plan.getId(), itemId, body.documentRef(),
                    null, actor, body.reason());
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/actions/{itemId}/choose-mode")
    public CustomerRequirementDtos.CustomerRequirementsView chooseMode(
            @PathVariable UUID applicationId,
            @PathVariable UUID itemId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody CustomerRequirementDtos.ChooseModeRequest body) {
        UUID uid = parseUser(userId);
        borrowerPortalService.requireBorrower(role);
        borrowerPortalService.applicationDetail(uid, applicationId, false);
        try {
            RequirementPlanEntity plan = requirePlan(applicationId);
            if (body == null || body.mode() == null) {
                throw new BusinessRuleException("mode is required");
            }
            String actor = uid + "|CUSTOMER";
            transitionService.chooseFulfilmentMode(plan.getId(), itemId, body.mode(), actor, "CUSTOMER");
            return viewService.viewForApplication(applicationId);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private RequirementPlanEntity requirePlan(UUID applicationId) {
        return planRepository.findByApplicationIdWithItems(applicationId).stream()
                .filter(p -> p.getStatus() == RequirementPlanStatus.ACTIVE
                        || p.getStatus() == RequirementPlanStatus.DRAFT)
                .max(java.util.Comparator.comparingInt(RequirementPlanEntity::getPlanVersion))
                .orElseThrow(() -> new BusinessRuleException("No active RequirementPlan for application"));
    }

    private static UUID parseUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id required");
        }
        try {
            return UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-User-Id");
        }
    }
}
