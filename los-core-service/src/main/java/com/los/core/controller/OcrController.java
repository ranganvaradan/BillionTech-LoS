package com.los.core.controller;

import com.los.core.service.document.OcrExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * BR-16.3: OCR auto-extraction API.
 */
@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
@Tag(name = "OCR Extraction", description = "Automated document data extraction via OCR")
public class OcrController {

    private final OcrExtractionService ocrService;

    @PostMapping("/extract")
    @Operation(summary = "BR-16.3: Extract data from uploaded document via OCR")
    public ResponseEntity<Map<String, Object>> extract(
            @RequestParam UUID documentId,
            @RequestParam String documentType,
            @RequestParam(required = false) String documentPath) {
        return ResponseEntity.ok(ocrService.extractFromDocument(documentId, documentType, documentPath));
    }
}
