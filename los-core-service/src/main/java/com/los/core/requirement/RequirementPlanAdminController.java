package com.los.core.requirement;

import com.los.core.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W3 admin/debug APIs for Requirement Plan staging/UAT.
 * No dynamic customer UI; no source execution.
 */
@RestController
@RequestMapping("/api/v1/internal/requirement-plans")
@RequiredArgsConstructor
public class RequirementPlanAdminController {

    private final RequirementPlanService planService;
    private final RequirementItemTransitionService transitionService;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @PostMapping
    public RequirementDtos.PlanResponse create(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RequirementDtos.CreatePlanRequest body) {
        assertToken(token);
        try {
            return planService.createPlan(body);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public RequirementDtos.PlanResponse get(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        try {
            return planService.getPlan(id);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/by-application/{applicationId}")
    public List<RequirementDtos.PlanResponse> byApplication(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        return planService.listByApplication(applicationId);
    }

    @GetMapping("/{id}/summary")
    public RequirementDtos.PlanSummary summary(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertToken(token);
        try {
            return planService.summarize(id);
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id}/items/{itemId}/document-uploaded")
    public Map<String, Object> documentUploaded(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RequirementDtos.DocumentUploadedRequest body) {
        assertToken(token);
        try {
            String docRef = body != null ? body.documentRef() : null;
            List<UUID> extras = body != null ? body.itemIds() : null;
            String actor = body != null ? body.actor() : null;
            String reason = body != null ? body.reason() : null;
            List<RequirementItemEntity> updated = transitionService.markDocumentUploaded(
                    id, itemId, docRef, extras, actor, reason);
            return Map.of(
                    "updatedCount", updated.size(),
                    "items", updated.stream().map(planService::toItemResponse).toList());
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/items/{itemId}/direct-input")
    public RequirementDtos.ItemResponse directInput(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) RequirementDtos.DirectInputRequest body) {
        assertToken(token);
        try {
            boolean verified = body != null && Boolean.TRUE.equals(body.verified());
            String valueRef = body != null ? body.valueRef() : null;
            String actor = body != null ? body.actor() : null;
            String reason = body != null ? body.reason() : null;
            return planService.toItemResponse(
                    transitionService.markDirectInput(id, itemId, valueRef, verified, actor, reason));
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/items/{itemId}/readiness")
    public RequirementDtos.ItemResponse readiness(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RequirementDtos.StateAdvanceRequest body) {
        assertToken(token);
        try {
            DataReadinessState state = DataReadinessState.valueOf(body.state());
            return planService.toItemResponse(
                    transitionService.advanceReadiness(id, itemId, state, body.actor(), body.reason()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid readiness state");
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/items/{itemId}/source")
    public RequirementDtos.ItemResponse source(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RequirementDtos.StateAdvanceRequest body) {
        assertToken(token);
        try {
            SourceAcquisitionState state = SourceAcquisitionState.valueOf(body.state());
            return planService.toItemResponse(
                    transitionService.advanceSource(id, itemId, state, body.actor(), body.reason()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source state");
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void assertToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
