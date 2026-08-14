package com.los.core.customercategory;

import com.los.core.audit.AdminAuditContext;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.customercategory.CustomerCategoryDtos.EligibleRuleSetView;
import com.los.core.customercategory.CustomerCategoryDtos.EligibleScorecardView;
import com.los.core.customercategory.CustomerCategoryDtos.LifecycleActionRequest;
import com.los.core.customercategory.CustomerCategoryDtos.SeedApplyResponse;
import com.los.core.customercategory.CustomerCategoryDtos.SeedPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Category governance APIs. Does not affect live underwriting routing.
 */
@RestController
@RequestMapping("/api/v1/customer-categories")
@RequiredArgsConstructor
@Tag(name = "Customer categories", description = "Governance configuration (not wired to live UW)")
public class CustomerCategoryController {

    private final CustomerCategoryService categoryService;
    private final CustomerCategorySeedService seedService;
    private final CustomerCategoryDay1SeedService day1SeedService;
    private final EligibleComponentCatalogueService catalogueService;
    private final CategoryPolicyBindService policyBindService;

    @GetMapping
    @Operation(summary = "List Customer Categories")
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.get(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<Map<String, Object>>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.history(id));
    }

    @GetMapping("/{id}/activation-readiness")
    @Operation(summary = "Non-mutating activation readiness checks for admin UI")
    public ResponseEntity<CustomerCategoryDtos.ActivationReadinessResponse> categoryActivationReadiness(
            @PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.activationReadiness(id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @RequestBody CategoryRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createDraft(request, actor(userId, userName, role)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @RequestBody CategoryRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.update(id, request, actor(userId, userName, role)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        categoryService.deleteDraft(id, actor(userId, userName, role));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<CategoryResponse> submit(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.submit(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<CategoryResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.approve(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<CategoryResponse> returnToDraft(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.returnToDraft(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate APPROVED category only (never DRAFT→ACTIVE)")
    public ResponseEntity<CategoryResponse> activate(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.activate(id, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<CategoryResponse> retire(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.retire(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<CategoryResponse> copyVersion(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.copyVersion(id, body, actor(userId, userName, role)));
    }

    @GetMapping("/meta/overlaps")
    public ResponseEntity<List<Map<String, Object>>> overlaps() {
        return ResponseEntity.ok(categoryService.overlapReport());
    }

    @GetMapping("/meta/eligible-policies")
    @Operation(summary = "List Policy Studio policies eligible for Category binding (config only; not live UW)")
    public ResponseEntity<List<CustomerCategoryDtos.EligiblePolicyView>> eligiblePolicies(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String borrowerType,
            @RequestParam(required = false) String loanProduct,
            @RequestParam(required = false) String customerRole,
            @RequestParam(required = false) String intakeSegment,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Instant effectiveFrom,
            @RequestParam(required = false) Instant effectiveUntil) {
        String et = entityType != null ? entityType : borrowerType;
        String role = customerRole != null ? customerRole : intakeSegment;
        return ResponseEntity.ok(policyBindService.listEligiblePolicies(
                et, loanProduct, role, minAmount, maxAmount, effectiveFrom, effectiveUntil));
    }

    @GetMapping("/meta/policy-scope-compatibility-report")
    @Operation(summary = "Read-only Category↔Policy scope compatibility counts (no auto-link; no mutation)")
    public ResponseEntity<List<CustomerCategoryDtos.CategoryPolicyCompatibilityReportRow>> compatibilityReport() {
        return ResponseEntity.ok(policyBindService.compatibilityReport(categoryService.listEntitiesForCompatibilityScan()));
    }

    @GetMapping("/meta/eligible-rule-sets")
    @Operation(summary = "List ACTIVE underwriting rule sets (transitional Policy Set composition)")
    public ResponseEntity<List<EligibleRuleSetView>> eligibleRuleSets(
            @RequestParam(required = false) String borrowerType,
            @RequestParam(required = false) String loanProduct,
            @RequestParam(required = false) BigDecimal amount) {
        return ResponseEntity.ok(catalogueService.eligibleRuleSets(borrowerType, loanProduct, amount));
    }

    @GetMapping("/meta/eligible-scorecards")
    @Operation(summary = "List ACTIVE scorecards eligible for Policy Set selection")
    public ResponseEntity<List<EligibleScorecardView>> eligibleScorecards(
            @RequestParam(required = false) String borrowerType,
            @RequestParam(required = false) String loanProduct,
            @RequestParam(required = false) BigDecimal amount) {
        return ResponseEntity.ok(catalogueService.eligibleScorecards(borrowerType, loanProduct, amount));
    }

    @PostMapping("/seed/preview")
    public ResponseEntity<SeedPreviewResponse> seedPreview() {
        return ResponseEntity.ok(seedService.preview());
    }

    @PostMapping("/seed/apply")
    public ResponseEntity<SeedApplyResponse> seedApply(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(seedService.applyDrafts(actor(userId, userName, role)));
    }

    @PostMapping("/seed/day1/validate")
    public ResponseEntity<Map<String, Object>> day1Validate() {
        day1SeedService.validateAllOrThrow();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "policySets", Day1ApprovedSeedCatalog.POLICY_SETS.size(),
                "categories", Day1ApprovedSeedCatalog.CATEGORIES.size(),
                "note", "Validation only — nothing persisted"));
    }

    @PostMapping("/seed/day1/apply")
    public ResponseEntity<CustomerCategoryDay1SeedService.Day1ApplyResult> day1Apply(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(day1SeedService.applyDrafts(actor(userId, userName, role)));
    }

    @GetMapping("/seed/day1/readback")
    public ResponseEntity<CustomerCategoryDay1SeedService.Day1ApplyResult> day1Readback() {
        return ResponseEntity.ok(day1SeedService.readBack(0, 0, 0));
    }

    private static Actor actor(String userId, String userName, String role) {
        return new Actor(
                userId == null ? "" : userId.trim(),
                userName == null || userName.isBlank() ? userId : userName.trim(),
                role == null ? "" : role.trim());
    }

    private static void withAudit(String userId, String role) {
        AdminAuditContext.clear();
        AdminAuditContext.set(userId, role);
    }
}
