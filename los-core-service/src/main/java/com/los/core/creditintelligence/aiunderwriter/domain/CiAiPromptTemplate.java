package com.los.core.creditintelligence.aiunderwriter.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ci_ai_prompt_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiAiPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_code", nullable = false, length = 120)
    private String templateCode;

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "model_provider", length = 80)
    private String modelProvider;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "temperature", precision = 6, scale = 4)
    private BigDecimal temperature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> settings = new LinkedHashMap<>();

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
