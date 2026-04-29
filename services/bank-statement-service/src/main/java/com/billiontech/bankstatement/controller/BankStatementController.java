package com.billiontech.bankstatement.controller;

import com.billiontech.bankstatement.model.dto.response.*;
import com.billiontech.bankstatement.service.BankStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bank-statements")
@RequiredArgsConstructor
@Tag(name = "Bank Statements", description = "Bank Statement upload, extraction, and analysis APIs")
public class BankStatementController {

    private final BankStatementService service;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a bank statement for extraction and analysis")
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "applicationId", required = false) String applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UploadResponse response = service.uploadAndProcess(file, password, applicationId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all uploaded statements")
    public ResponseEntity<Page<StatementResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(service.listStatements(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get statement metadata")
    public ResponseEntity<StatementResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getStatement(id));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get parsed transactions for a statement")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(service.getTransactions(id, pageable));
    }

    @GetMapping("/{id}/analysis")
    @Operation(summary = "Get full analysis report")
    public ResponseEntity<AnalysisResponse> getAnalysis(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAnalysis(id));
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Get summary metrics for a statement")
    public ResponseEntity<SummaryResponse> getSummary(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSummary(id));
    }

    @GetMapping("/{id}/monthly")
    @Operation(summary = "Get month-wise breakdown")
    public ResponseEntity<List<MonthlySummaryResponse>> getMonthly(@PathVariable Long id) {
        return ResponseEntity.ok(service.getMonthlySummaries(id));
    }

    @GetMapping("/{id}/red-flags")
    @Operation(summary = "Get detected red flags")
    public ResponseEntity<List<Map<String, Object>>> getRedFlags(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRedFlags(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a statement and all associated data")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteStatement(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get all statements linked to a loan application")
    public ResponseEntity<List<StatementResponse>> getByApplication(@PathVariable String applicationId) {
        return ResponseEntity.ok(service.getStatementsByApplication(applicationId));
    }

    @GetMapping("/supported-banks")
    @Operation(summary = "List all supported bank formats")
    public ResponseEntity<List<Map<String, Object>>> getSupportedBanks() {
        List<Map<String, Object>> banks = List.of(
                Map.of("code", "SBI", "name", "State Bank of India", "formats", List.of("PDF", "XLS")),
                Map.of("code", "HDFC", "name", "HDFC Bank", "formats", List.of("PDF", "XLS", "CSV")),
                Map.of("code", "ICICI", "name", "ICICI Bank", "formats", List.of("PDF", "XLS")),
                Map.of("code", "AXIS", "name", "Axis Bank", "formats", List.of("PDF", "XLS")),
                Map.of("code", "PNB", "name", "Punjab National Bank", "formats", List.of("PDF")),
                Map.of("code", "KOTAK", "name", "Kotak Mahindra Bank", "formats", List.of("PDF", "XLS")),
                Map.of("code", "YES", "name", "Yes Bank", "formats", List.of("PDF", "CSV")),
                Map.of("code", "INDUSIND", "name", "IndusInd Bank", "formats", List.of("PDF")),
                Map.of("code", "BOB", "name", "Bank of Baroda", "formats", List.of("PDF")),
                Map.of("code", "CANARA", "name", "Canara Bank", "formats", List.of("PDF")),
                Map.of("code", "UNION", "name", "Union Bank of India", "formats", List.of("PDF")),
                Map.of("code", "IDBI", "name", "IDBI Bank", "formats", List.of("PDF")),
                Map.of("code", "FEDERAL", "name", "Federal Bank", "formats", List.of("PDF")),
                Map.of("code", "RBL", "name", "RBL Bank", "formats", List.of("PDF")),
                Map.of("code", "BANDHAN", "name", "Bandhan Bank", "formats", List.of("PDF")),
                Map.of("code", "IDFC", "name", "IDFC First Bank", "formats", List.of("PDF")),
                Map.of("code", "AU_SFB", "name", "AU Small Finance Bank", "formats", List.of("PDF")),
                Map.of("code", "INDIAN", "name", "Indian Bank", "formats", List.of("PDF")),
                Map.of("code", "BOI", "name", "Bank of India", "formats", List.of("PDF")),
                Map.of("code", "CBI", "name", "Central Bank of India", "formats", List.of("PDF")),
                Map.of("code", "UCO", "name", "UCO Bank", "formats", List.of("PDF")),
                Map.of("code", "PSB", "name", "Punjab & Sind Bank", "formats", List.of("PDF")),
                Map.of("code", "IOB", "name", "Indian Overseas Bank", "formats", List.of("PDF")),
                Map.of("code", "KARNATAKA", "name", "Karnataka Bank", "formats", List.of("PDF")),
                Map.of("code", "SIB", "name", "South Indian Bank", "formats", List.of("PDF"))
        );
        return ResponseEntity.ok(banks);
    }
}
