package com.los.core.customercategory;

import com.los.core.audit.AdminAuditContext;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policy-sets")
@RequiredArgsConstructor
@Tag(name = "Policy sets", description = "Phase-1 Policy Set references to live rule sets/scorecards")
public class PolicySetController {

    private final PolicySetService policySetService;

    @GetMapping
    @Operation(summary = "List Policy Sets")
    public ResponseEntity<List<PolicySetResponse>> list() {
        return ResponseEntity.ok(policySetService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicySetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(policySetService.get(id));
    }

    @PostMapping
    public ResponseEntity<PolicySetResponse> create(
            @RequestBody PolicySetRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        AdminAuditContext.clear();
        AdminAuditContext.set(userId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policySetService.createDraft(request, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<PolicySetResponse> activate(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        AdminAuditContext.clear();
        AdminAuditContext.set(userId, role);
        return ResponseEntity.ok(policySetService.activate(id, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<PolicySetResponse> retire(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        AdminAuditContext.clear();
        AdminAuditContext.set(userId, role);
        return ResponseEntity.ok(policySetService.retire(id, actor(userId, userName, role)));
    }

    private static Actor actor(String userId, String userName, String role) {
        return new Actor(
                userId == null ? "" : userId.trim(),
                userName == null || userName.isBlank() ? userId : userName.trim(),
                role == null ? "" : role.trim());
    }
}
