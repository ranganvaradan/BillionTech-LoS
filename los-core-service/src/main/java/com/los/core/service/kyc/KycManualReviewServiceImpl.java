package com.los.core.service.kyc;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.ManualKycReviewRequest;
import com.los.core.model.dto.response.DocumentResponse;
import com.los.core.model.dto.response.ManualKycReviewResponse;
import com.los.core.model.entity.Document;
import com.los.core.model.entity.ManualKycReview;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.ManualKycReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycManualReviewServiceImpl implements IKycManualReviewService {

    private final ManualKycReviewRepository manualKycReviewRepository;
    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public ManualKycReviewResponse save(UUID applicationId, KycStepType stepType, ManualKycReviewRequest request, UUID reviewedBy) {
        ManualKycReview existing = manualKycReviewRepository
                .findByApplicationIdAndStepType(applicationId, stepType)
                .orElse(null);

        ManualKycReview entity = existing != null ? existing : ManualKycReview.builder()
                .applicationId(applicationId)
                .stepType(stepType)
                .build();

        entity.setData(request != null ? request.getData() : null);
        entity.setDecision(request != null ? request.getDecision() : null);
        entity.setRemarks(request != null ? request.getRemarks() : null);
        entity.setReviewedBy(reviewedBy);
        entity.setReviewedAt(Instant.now());

        entity = manualKycReviewRepository.save(entity);

        List<DocumentResponse> docs = getDocumentsForStep(applicationId, stepType);

        return ManualKycReviewResponse.builder()
                .id(entity.getId())
                .applicationId(entity.getApplicationId())
                .stepType(entity.getStepType())
                .data(entity.getData())
                .decision(entity.getDecision())
                .remarks(entity.getRemarks())
                .reviewedBy(entity.getReviewedBy())
                .reviewedAt(entity.getReviewedAt())
                .updatedAt(entity.getUpdatedAt())
                .documents(docs)
                .build();
    }

    @Override
    public List<ManualKycReviewResponse> getAll(UUID applicationId) {
        List<ManualKycReview> rows = manualKycReviewRepository.findByApplicationIdOrderByUpdatedAtDesc(applicationId);
        Map<KycStepType, List<DocumentResponse>> docsByStep = new EnumMap<>(KycStepType.class);

        List<Document> docs = documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        for (Document d : docs) {
            if (d.getKycStepType() == null) continue;
            docsByStep.computeIfAbsent(d.getKycStepType(), k -> new ArrayList<>()).add(toDocumentResponse(d));
        }

        return rows.stream().map(r -> ManualKycReviewResponse.builder()
                .id(r.getId())
                .applicationId(r.getApplicationId())
                .stepType(r.getStepType())
                .data(r.getData())
                .decision(r.getDecision())
                .remarks(r.getRemarks())
                .reviewedBy(r.getReviewedBy())
                .reviewedAt(r.getReviewedAt())
                .updatedAt(r.getUpdatedAt())
                .documents(docsByStep.getOrDefault(r.getStepType(), List.of()))
                .build()).collect(Collectors.toList());
    }

    @Override
    public ManualKycReviewResponse getByStep(UUID applicationId, KycStepType stepType) {
        ManualKycReview row = manualKycReviewRepository.findByApplicationIdAndStepType(applicationId, stepType)
                .orElseThrow(() -> new ResourceNotFoundException("Manual KYC review not found for step: " + stepType));

        List<DocumentResponse> docs = getDocumentsForStep(applicationId, stepType);

        return ManualKycReviewResponse.builder()
                .id(row.getId())
                .applicationId(row.getApplicationId())
                .stepType(row.getStepType())
                .data(row.getData())
                .decision(row.getDecision())
                .remarks(row.getRemarks())
                .reviewedBy(row.getReviewedBy())
                .reviewedAt(row.getReviewedAt())
                .updatedAt(row.getUpdatedAt())
                .documents(docs)
                .build();
    }

    private List<DocumentResponse> getDocumentsForStep(UUID applicationId, KycStepType stepType) {
        return documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .filter(d -> stepType.equals(d.getKycStepType()))
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());
    }

    private DocumentResponse toDocumentResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .applicationId(doc.getApplicationId())
                .documentType(doc.getDocumentType())
                .kycStepType(doc.getKycStepType())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .checksum(doc.getChecksum())
                .uploadedBy(doc.getUploadedBy())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
