package com.los.core.service.workflow;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * SLA/TAT Tracking Service — monitors turnaround times per workflow step,
 * triggers escalations when SLA is breached. BR-6.6, BR-12.9.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaTatService {

    private final LoanApplicationRepository applicationRepository;
    private final WorkflowConfigRepository workflowConfigRepository;
    private final AuditService auditService;

    /**
     * Set SLA deadline for an application based on its current workflow step.
     */
    @Transactional
    public void setSlaForStep(UUID applicationId, String stepName) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        Optional<WorkflowConfig> wfOpt = workflowConfigRepository
                .findByBorrowerTypeAndLoanProductAndActiveTrue(
                        app.getBorrowerType().name(), app.getLoanProduct());

        int slaHours = 24; // default 24 hours
        if (wfOpt.isPresent() && wfOpt.get().getSlaHoursPerStep() != null) {
            slaHours = wfOpt.get().getSlaHoursPerStep().getOrDefault(stepName, 24);
        }

        app.setCurrentStepStartedAt(Instant.now());
        app.setSlaDeadline(Instant.now().plus(slaHours, ChronoUnit.HOURS));
        app.setEscalated(false);
        app.setEscalatedAt(null);
        applicationRepository.save(app);

        log.info("SLA set for application {} step {}: {} hours (deadline: {})",
                applicationId, stepName, slaHours, app.getSlaDeadline());
    }

    /**
     * Scheduled job to check for SLA breaches and escalate.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void checkSlaBreaches() {
        List<LoanApplication> breached = applicationRepository
                .findByEscalatedFalseAndSlaDeadlineBefore(Instant.now());

        for (LoanApplication app : breached) {
            app.setEscalated(true);
            app.setEscalatedAt(Instant.now());
            applicationRepository.save(app);

            auditService.logEvent(app.getId(), "SLA_BREACHED",
                    Map.of("deadline", app.getSlaDeadline().toString(),
                            "status", app.getStatus().name(),
                            "hoursOverdue", String.valueOf(
                                    ChronoUnit.HOURS.between(app.getSlaDeadline(), Instant.now()))));

            log.warn("SLA BREACHED for application {} — deadline was {}, current status: {}",
                    app.getApplicationNumber(), app.getSlaDeadline(), app.getStatus());
        }

        if (!breached.isEmpty()) {
            log.info("SLA check completed: {} applications escalated", breached.size());
        }
    }

    /**
     * Get TAT summary for an application.
     */
    public Map<String, Object> getTatSummary(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("applicationId", applicationId);
        summary.put("applicationNumber", app.getApplicationNumber());
        summary.put("currentStatus", app.getStatus().name());
        summary.put("createdAt", app.getCreatedAt().toString());

        long totalHours = ChronoUnit.HOURS.between(app.getCreatedAt(), Instant.now());
        summary.put("totalElapsedHours", totalHours);
        summary.put("totalElapsedDays", totalHours / 24);

        if (app.getCurrentStepStartedAt() != null) {
            long stepHours = ChronoUnit.HOURS.between(app.getCurrentStepStartedAt(), Instant.now());
            summary.put("currentStepHours", stepHours);
        }

        summary.put("slaDeadline", app.getSlaDeadline() != null ? app.getSlaDeadline().toString() : null);
        summary.put("escalated", app.isEscalated());
        summary.put("escalatedAt", app.getEscalatedAt() != null ? app.getEscalatedAt().toString() : null);

        if (app.getSlaDeadline() != null) {
            long remainingHours = ChronoUnit.HOURS.between(Instant.now(), app.getSlaDeadline());
            summary.put("slaRemainingHours", remainingHours);
            summary.put("slaBreached", remainingHours < 0);
        }

        return summary;
    }

    /**
     * Get TAT dashboard — aggregate TAT metrics across all active applications.
     */
    public Map<String, Object> getTatDashboard() {
        var activeStatuses = List.of(
                com.los.core.model.enums.ApplicationStatus.KYC_IN_PROGRESS,
                com.los.core.model.enums.ApplicationStatus.UNDERWRITING,
                com.los.core.model.enums.ApplicationStatus.ESIGN_PENDING,
                com.los.core.model.enums.ApplicationStatus.DISBURSEMENT_PENDING
        );

        List<LoanApplication> activeApps = applicationRepository.findByStatusIn(activeStatuses);

        long totalActive = activeApps.size();
        long breachedCount = activeApps.stream().filter(LoanApplication::isEscalated).count();
        long atRiskCount = activeApps.stream()
                .filter(a -> a.getSlaDeadline() != null && !a.isEscalated())
                .filter(a -> ChronoUnit.HOURS.between(Instant.now(), a.getSlaDeadline()) < 4)
                .count();

        double avgProcessingHours = activeApps.stream()
                .mapToLong(a -> ChronoUnit.HOURS.between(a.getCreatedAt(), Instant.now()))
                .average().orElse(0);

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalActiveApplications", totalActive);
        dashboard.put("slaBreachedCount", breachedCount);
        dashboard.put("atRiskCount", atRiskCount);
        dashboard.put("onTrackCount", totalActive - breachedCount - atRiskCount);
        dashboard.put("avgProcessingHours", Math.round(avgProcessingHours));
        dashboard.put("avgProcessingDays", Math.round(avgProcessingHours / 24.0));

        // Breakdown by status
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (LoanApplication app : activeApps) {
            statusCounts.merge(app.getStatus().name(), 1L, Long::sum);
        }
        dashboard.put("statusBreakdown", statusCounts);

        // Bottleneck identification
        String bottleneck = statusCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
        dashboard.put("bottleneckStage", bottleneck);

        return dashboard;
    }
}
