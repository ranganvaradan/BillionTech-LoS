package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiLegacyDefaultDefinition {
    private UUID id;
    private String legacyKey;
    private String component;
    private String filePath;
    private String methodName;
    private String defaultValue;
    private String valueType;
    private String triggerCondition;
    @Builder.Default
    private List<String> products = new ArrayList<>();
    @Builder.Default
    private List<String> tenants = new ArrayList<>();
    @Builder.Default
    private List<String> rulesImpacted = new ArrayList<>();
    @Builder.Default
    private List<String> scorecardsImpacted = new ArrayList<>();
    @Builder.Default
    private List<String> decisionDimensionsImpacted = new ArrayList<>();
    private String severity;
    private String classification;
    private String canonicalReplacement;
    private String missingDataBehavior;
    private String status;
    private Instant createdAt;
}
