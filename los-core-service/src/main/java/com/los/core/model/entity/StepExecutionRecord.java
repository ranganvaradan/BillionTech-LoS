package com.los.core.model.entity;

import com.los.core.model.enums.StepExecutionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_execution_record", indexes = {
        @Index(name = "idx_step_exec_application_id", columnList = "application_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "step_type", nullable = false, length = 100)
    private String stepType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StepExecutionStatus status;

    @Column(name = "input_json", columnDefinition = "text")
    private String inputJson;

    @Column(name = "output_json", columnDefinition = "text")
    private String outputJson;

    @Column(name = "error_code", length = 200)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
