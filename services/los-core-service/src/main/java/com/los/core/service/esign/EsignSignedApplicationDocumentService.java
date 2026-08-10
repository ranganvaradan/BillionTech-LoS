package com.los.core.service.esign;

import com.los.core.model.entity.KfsDocument;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * On eSign simulate / mark-complete, registers the unsigned KFS / program terms / sanction terms
 * PDF under signed document types so they appear in the application Documents section.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsignSignedApplicationDocumentService {

    public static final String SIGNED_KFS = "SIGNED_KFS";
    public static final String SIGNED_PROGRAM_TERMS = "SIGNED_PROGRAM_TERMS";
    public static final String SIGNED_SANCTION_TERMS = "SIGNED_SANCTION_TERMS";
    public static final String SIGNED_AGREEMENT = "SIGNED_AGREEMENT";

    private final KfsDocumentRepository kfsDocumentRepository;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final IDocumentService documentService;

    /**
     * Generate the current unsigned terms PDF and store it as the matching SIGNED_* document type.
     */
    public void registerSignedDocumentsFromKfs(UUID applicationId) {
        if (applicationId == null) {
            return;
        }
        try {
            KfsDocument kfs = kfsDocumentRepository
                    .findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                    .orElse(null);
            if (kfs != null) {
                byte[] unsignedPdf = kfsPdfGenerationService.generateKfsPdf(kfs);
                String signedType = resolveSignedDocumentType(kfs);
                String fileName = fileNameFor(signedType);
                documentService.storeDocumentBytes(
                        applicationId,
                        signedType,
                        fileName,
                        "application/pdf",
                        unsignedPdf);
                log.info("[eSign docs] Registered {} for application {}", signedType, applicationId);
            } else {
                log.warn("[eSign docs] No KFS row for application {} — skipping SIGNED_* registration", applicationId);
            }
        } catch (Exception e) {
            log.warn("[eSign docs] Failed to register signed KFS/terms docs for {}: {}",
                    applicationId, e.getMessage());
        }
    }

    public void registerProviderSignedAgreement(UUID applicationId, byte[] providerSignedPdfBytes) {
        if (applicationId == null || providerSignedPdfBytes == null || providerSignedPdfBytes.length == 0) {
            return;
        }
        try {
            documentService.storeDocumentBytes(
                    applicationId,
                    SIGNED_AGREEMENT,
                    "signed-agreement.pdf",
                    "application/pdf",
                    providerSignedPdfBytes);
            log.info("[eSign docs] Registered {} for application {}", SIGNED_AGREEMENT, applicationId);
        } catch (Exception e) {
            log.warn("[eSign docs] Failed to register signed agreement PDF for {}: {}",
                    applicationId, e.getMessage());
        }
    }

    private static String resolveSignedDocumentType(KfsDocument kfs) {
        Map<String, Object> additional = kfs.getAdditionalTerms();
        Object kind = additional != null ? additional.get("documentKind") : null;
        String k = kind != null ? kind.toString().trim().toUpperCase(Locale.ROOT) : "";
        if (KfsService.DOCUMENT_KIND_ANCHOR_PROGRAM_TERMS.equals(k)) {
            return SIGNED_PROGRAM_TERMS;
        }
        if (KfsService.DOCUMENT_KIND_INVOICE_DISCOUNTING_TERMS.equals(k)
                || k.contains("SANCTION")) {
            return SIGNED_SANCTION_TERMS;
        }
        return SIGNED_KFS;
    }

    private static String fileNameFor(String signedType) {
        return switch (signedType) {
            case SIGNED_PROGRAM_TERMS -> "signed-program-terms.pdf";
            case SIGNED_SANCTION_TERMS -> "signed-sanction-terms.pdf";
            default -> "signed-kfs.pdf";
        };
    }
}
