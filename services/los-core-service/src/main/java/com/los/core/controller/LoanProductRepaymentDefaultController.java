package com.los.core.controller;

import com.los.core.model.dto.request.LoanProductRepaymentDefaultUpdateRequest;
import com.los.core.model.dto.response.LoanProductRepaymentDefaultResponse;
import com.los.core.service.repayment.LoanProductRepaymentDefaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/repayment-defaults")
@RequiredArgsConstructor
@Tag(name = "Repayment defaults", description = "LOS global repayment mechanism per loan product")
public class LoanProductRepaymentDefaultController {

    private final LoanProductRepaymentDefaultService service;

    @GetMapping
    @Operation(summary = "List loan product repayment defaults")
    public ResponseEntity<List<LoanProductRepaymentDefaultResponse>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    @PutMapping("/{loanProduct}")
    @Operation(summary = "Update repayment default for a loan product")
    public ResponseEntity<LoanProductRepaymentDefaultResponse> update(
            @PathVariable String loanProduct,
            @RequestBody LoanProductRepaymentDefaultUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(service.update(loanProduct, request, userId));
    }
}
