package com.los.core.controller;

import com.los.lms.entity.ExternalProductMapping;
import com.los.lms.service.ExternalProductMappingAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/live-readiness/external-product-mappings")
@RequiredArgsConstructor
@Tag(name = "External Product Mapping Admin", description = "Administration CRUD for canonical external_product_mapping authority")
public class ExternalProductMappingAdminController {

    private final ExternalProductMappingAdminService adminService;

    public record ExternalProductMappingView(
            UUID id,
            String losProductCode,
            String externalSystem,
            String externalProductCode,
            Integer version,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean effectiveToday,
            boolean activeToday) {}

    @GetMapping
    @Operation(summary = "List external_product_mapping rows for a LOS Product + External System")
    public ResponseEntity<List<ExternalProductMappingView>> list(
            @RequestParam String losProductCode,
            @RequestParam(required = false) String externalSystem,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {

        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        List<ExternalProductMapping> rows = adminService.listMappings(losProductCode, externalSystem)
                .stream()
                .sorted(Comparator.comparing(ExternalProductMapping::getVersion).reversed())
                .toList();

        return ResponseEntity.ok(rows.stream().map(r -> toView(r, date)).toList());
    }

    @GetMapping("/systems")
    @Operation(summary = "Distinct external_system values present for a LOS Product")
    public ResponseEntity<List<String>> externalSystems(@RequestParam String losProductCode) {
        List<String> systems = adminService.listMappings(losProductCode, null)
                .stream()
                .map(ExternalProductMapping::getExternalSystem)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        return ResponseEntity.ok(systems);
    }

    @PostMapping
    @Operation(summary = "Create new external_product_mapping version (immutable evidence row)")
    public ResponseEntity<ExternalProductMappingView> create(@RequestBody ExternalProductMappingAdminService.CreateMappingRequest req) {
        ExternalProductMapping created = adminService.createMapping(req);
        return ResponseEntity.ok(toView(created, LocalDate.now()));
    }

    @PatchMapping("/{mappingId}/status")
    @Operation(summary = "Activate / deactivate / retire an existing mapping row (status-only; evidence immutable)")
    public ResponseEntity<ExternalProductMappingView> patchStatus(
            @PathVariable UUID mappingId,
            @RequestBody Map<String, Object> body) {
        Object s = body == null ? null : body.get("status");
        String status = s == null ? null : String.valueOf(s);
        ExternalProductMapping updated = adminService.patchStatus(mappingId, status);
        return ResponseEntity.ok(toView(updated, LocalDate.now()));
    }

    private static ExternalProductMappingView toView(ExternalProductMapping r, LocalDate asOf) {
        boolean effective = !asOf.isBefore(r.getEffectiveFrom()) && !asOf.isAfter(r.getEffectiveTo());
        boolean activeToday = effective && r.getStatus() != null && "ACTIVE".equalsIgnoreCase(r.getStatus());
        return new ExternalProductMappingView(
                r.getId(),
                r.getLosProductCode(),
                r.getExternalSystem(),
                r.getExternalProductCode(),
                r.getVersion(),
                r.getStatus(),
                r.getEffectiveFrom(),
                r.getEffectiveTo(),
                effective,
                activeToday);
    }
}

