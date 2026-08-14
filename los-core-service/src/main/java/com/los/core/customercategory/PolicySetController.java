package com.los.core.customercategory;

import com.los.core.audit.AdminAuditContext;
import com.los.core.customercategory.CustomerCategoryDtos.Actor;
import com.los.core.customercategory.CustomerCategoryDtos.LifecycleActionRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetRequest;
import com.los.core.customercategory.CustomerCategoryDtos.PolicySetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policy-sets")
@RequiredArgsConstructor
@Tag(name = "Policy sets", description = "Governance composition of live rule sets/scorecards")
public class PolicySetController {

    private final PolicySetService policySetService;

    @GetMapping
    public ResponseEntity<List<PolicySetResponse>> list() {
        return ResponseEntity.ok(policySetService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicySetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(policySetService.get(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<Map<String, Object>>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(policySetService.history(id));
    }

    @PostMapping
    public ResponseEntity<PolicySetResponse> create(
            @RequestBody PolicySetRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policySetService.createDraft(request, actor(userId, userName, role)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PolicySetResponse> update(
            @PathVariable UUID id,
            @RequestBody PolicySetRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(policySetService.updateDraft(id, request, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<PolicySetResponse> submit(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(policySetService.submit(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PolicySetResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(policySetService.approve(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<PolicySetResponse> returnToDraft(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(policySetService.returnToDraft(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate APPROVED Policy Set only")
    public ResponseEntity<PolicySetResponse> activate(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(policySetService.activate(id, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<PolicySetResponse> retire(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.ok(policySetService.retire(id, body, actor(userId, userName, role)));
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<PolicySetResponse> copy(
            @PathVariable UUID id,
            @RequestBody(required = false) LifecycleActionRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        withAudit(userId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policySetService.copyVersion(id, body, actor(userId, userName, role)));
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
