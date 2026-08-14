package com.los.core.customercategory;

import com.los.core.audit.AdminAuditContext;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryRequest;
import com.los.core.customercategory.CustomerCategoryDtos.CategoryResponse;
import com.los.core.customercategory.CustomerCategoryDtos.SeedApplyResponse;
import com.los.core.customercategory.CustomerCategoryDtos.SeedPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step-1 admin/read APIs. Does not affect live underwriting routing.
 */
@RestController
@RequestMapping("/api/v1/customer-categories")
@RequiredArgsConstructor
@Tag(name = "Customer categories", description = "Phase-1 configuration (not wired to live UW yet)")
public class CustomerCategoryController {

    private final CustomerCategoryService categoryService;
    private final CustomerCategorySeedService seedService;
    private final CustomerCategoryDay1SeedService day1SeedService;

    @GetMapping
    @Operation(summary = "List Customer Categories")
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Customer Category by id")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create DRAFT Customer Category")
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
    @Operation(summary = "Update category (ACTIVE matching fields blocked)")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @RequestBody CategoryRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.update(id, request, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate DRAFT category (overlap = WARNING only)")
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
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(categoryService.retire(id, actor(userId, userName, role)));
    }

    @GetMapping("/meta/overlaps")
    @Operation(summary = "Overlap WARNING report for DRAFT+ACTIVE categories")
    public ResponseEntity<List<Map<String, Object>>> overlaps() {
        return ResponseEntity.ok(categoryService.overlapReport());
    }

    @PostMapping("/seed/preview")
    @Operation(summary = "Preview seed candidates from live underwriting_rule_sets (no writes)")
    public ResponseEntity<SeedPreviewResponse> seedPreview() {
        return ResponseEntity.ok(seedService.preview());
    }

    @PostMapping("/seed/apply")
    @Operation(summary = "Apply raw rule-set seed as DRAFT only (idempotent; never ACTIVE)")
    public ResponseEntity<SeedApplyResponse> seedApply(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(seedService.applyDrafts(actor(userId, userName, role)));
    }

    @PostMapping("/seed/day1/validate")
    @Operation(summary = "Validate approved Day-1 15×12 matrix against live refs (no writes)")
    public ResponseEntity<Map<String, Object>> day1Validate() {
        day1SeedService.validateAllOrThrow();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "policySets", Day1ApprovedSeedCatalog.POLICY_SETS.size(),
                "categories", Day1ApprovedSeedCatalog.CATEGORIES.size(),
                "note", "Validation only — nothing persisted"));
    }

    @PostMapping("/seed/day1/apply")
    @Operation(summary = "Apply approved Day-1 seed as DRAFT only (15 categories, 12 Policy Sets)")
    public ResponseEntity<CustomerCategoryDay1SeedService.Day1ApplyResult> day1Apply(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(day1SeedService.applyDrafts(actor(userId, userName, role)));
    }

    @GetMapping("/seed/day1/readback")
    @Operation(summary = "Read back persisted Day-1 DRAFT seed rows")
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
