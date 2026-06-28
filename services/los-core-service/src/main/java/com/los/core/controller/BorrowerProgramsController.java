package com.los.core.controller;

import com.los.core.model.dto.response.BorrowerProgramsResponse;
import com.los.core.service.borrower.BorrowerProgramsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/borrower/programs")
@RequiredArgsConstructor
@Tag(name = "Borrower programs", description = "PLP program memberships for invoice-discounting borrowers")
public class BorrowerProgramsController {

    private final BorrowerProgramsService borrowerProgramsService;

    @GetMapping
    @Operation(summary = "Program and sub-program memberships with limits (PLP-backed)")
    public ResponseEntity<BorrowerProgramsResponse> listPrograms(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        UUID uid = UUID.fromString(userId);
        borrowerProgramsService.requireBorrower(role);
        return ResponseEntity.ok(borrowerProgramsService.listPrograms(uid));
    }
}
