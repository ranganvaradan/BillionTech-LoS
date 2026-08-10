package com.los.core.service.integration;

import com.los.core.model.dto.response.IntegrationProviderMatrixRow;
import com.los.core.model.entity.AggregatorConfig;
import com.los.core.repository.AggregatorConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IntegrationProviderMatrixService {

    private final AggregatorConfigRepository aggregatorConfigRepository;

    @Transactional(readOnly = true)
    public List<IntegrationProviderMatrixRow> listAllRows() {
        return aggregatorConfigRepository.findAll().stream()
                .filter(AggregatorConfig::isActive)
                .sorted(Comparator
                        .comparing(AggregatorConfig::getIntegrationType, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AggregatorConfig::getKycStepType, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AggregatorConfig::getEsignStepType, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AggregatorConfig::isActive)
                        .thenComparing(Comparator.comparingInt(AggregatorConfig::getPriority).reversed()))
                .map(this::toRow)
                .toList();
    }

    private IntegrationProviderMatrixRow toRow(AggregatorConfig a) {
        return new IntegrationProviderMatrixRow(
                a.getId(),
                a.getIntegrationType() != null ? a.getIntegrationType().name() : null,
                a.getKycStepType(),
                a.getEsignStepType(),
                a.getProviderName(),
                a.getPriority(),
                a.isAllowFallback(),
                a.isActive(),
                a.getMetadataJson());
    }
}
