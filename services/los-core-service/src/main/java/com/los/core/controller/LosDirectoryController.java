package com.los.core.controller;

import com.los.core.model.dto.request.LosUserRequest;
import com.los.core.model.dto.request.UserRoleMappingRequest;
import com.los.core.model.dto.response.LosUserResponse;
import com.los.core.model.dto.response.UserRoleMappingResponse;
import com.los.core.service.assignment.LosUserService;
import com.los.core.service.assignment.UserRoleMappingAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Local LOS directory (demo) — under /api/v1/los to avoid clashing with IAM /api/v1/users.
 */
@RestController
@RequestMapping("/api/v1/los")
@RequiredArgsConstructor
@Tag(name = "LOS directory", description = "Local users and role-to-segment mappings for assignment")
public class LosDirectoryController {

    private final LosUserService losUserService;
    private final UserRoleMappingAdminService userRoleMappingAdminService;

    @GetMapping("/users")
    @Operation(summary = "List directory users; optional role filters to users with an active mapping for that role")
    public ResponseEntity<List<LosUserResponse>> listUsers(@RequestParam(required = false) String role) {
        return ResponseEntity.ok(losUserService.list(role));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<LosUserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(losUserService.get(id));
    }

    @PostMapping("/users")
    @Operation(summary = "Create directory user")
    public ResponseEntity<LosUserResponse> createUser(@Valid @RequestBody LosUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(losUserService.create(request));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<LosUserResponse> updateUser(@PathVariable UUID id, @Valid @RequestBody LosUserRequest request) {
        return ResponseEntity.ok(losUserService.update(id, request));
    }

    @PostMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        losUserService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user-role-mappings")
    public ResponseEntity<List<UserRoleMappingResponse>> listMappings() {
        return ResponseEntity.ok(userRoleMappingAdminService.list());
    }

    @GetMapping("/user-role-mappings/{id}")
    public ResponseEntity<UserRoleMappingResponse> getMapping(@PathVariable UUID id) {
        return ResponseEntity.ok(userRoleMappingAdminService.get(id));
    }

    @PostMapping("/user-role-mappings")
    public ResponseEntity<UserRoleMappingResponse> createMapping(@Valid @RequestBody UserRoleMappingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userRoleMappingAdminService.create(request));
    }

    @PutMapping("/user-role-mappings/{id}")
    public ResponseEntity<UserRoleMappingResponse> updateMapping(
            @PathVariable UUID id, @Valid @RequestBody UserRoleMappingRequest request) {
        return ResponseEntity.ok(userRoleMappingAdminService.update(id, request));
    }

    @DeleteMapping("/user-role-mappings/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        userRoleMappingAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
