package com.los.core.controller;

import com.los.core.creditintelligence.policystudio.parameters.manualoverride.ApplicationParameterManualOverride;
import com.los.core.creditintelligence.policystudio.parameters.manualoverride.ApplicationParameterOverrideService;
import com.los.core.creditintelligence.policystudio.parameters.manualoverride.ManualParameterOverridePolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scoped, auditable manual override of one canonical GACAT parameter on one application — only
 * for a parameter whose real source is confirmed unavailable (see
 * {@link ApplicationParameterOverrideService}). Never a blanket bureau-score bypass.
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/parameter-overrides")
@RequiredArgsConstructor
@Tag(name = "Parameter Overrides", description = "Scoped manual override of a single canonical parameter, source-unavailable only")
public class ApplicationParameterOverrideController {

    private final ApplicationParameterOverrideService overrideService;

    public record CreateOverrideRequest(String canonicalParameterId, String value, String reason) {}

    @GetMapping("/eligible-parameters")
    @Operation(summary = "List canonical parameters eligible for manual override (allow-list)")
    public ResponseEntity<Set<String>> eligibleParameters() {
        return ResponseEntity.ok(ManualParameterOverridePolicy.OVERRIDABLE_PARAMETER_IDS);
    }

    @GetMapping
    @Operation(summary = "List manual parameter overrides (active and superseded) for this application")
    public ResponseEntity<List<ApplicationParameterManualOverride>> list(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(overrideService.listOverrides(applicationId));
    }

    @PostMapping
    @Operation(summary = "Create (or supersede) a manual override for one allow-listed parameter, "
            + "permitted only when its real source is confirmed unavailable")
    public ResponseEntity<ApplicationParameterManualOverride> create(
            @PathVariable UUID applicationId,
            @RequestBody CreateOverrideRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        String enteredBy = userName != null && !userName.isBlank() ? userName
                : (userId != null && !userId.isBlank() ? userId : "unknown");
        ApplicationParameterManualOverride created = overrideService.createOverride(
                applicationId, request.canonicalParameterId(), request.value(), request.reason(), enteredBy);
        return ResponseEntity.ok(created);
    }
}
