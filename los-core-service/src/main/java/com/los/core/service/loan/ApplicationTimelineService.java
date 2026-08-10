package com.los.core.service.loan;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.ApplicationTimelineEntryView;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.model.entity.ApplicationStatusHistory;
import com.los.core.model.entity.AuditEvent;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.StepExecutionRecord;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.repository.ApplicationStatusHistoryRepository;
import com.los.core.repository.AuditEventRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.StepExecutionRecordRepository;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationTimelineService {

    /** Canonical lifecycle order for flow-map rendering (subset may appear per product). */
    public static final List<String> LIFECYCLE_STAGES = List.of(
            "DRAFT",
            "CONSENT_PENDING",
            "BORROWER_SUBMITTED",
            "BORROWER_SENT_BACK",
            "PENDING_CREDIT_OFFICER",
            "SENT_BACK_TO_RM",
            "KYC_IN_PROGRESS",
            "KYC_FAILED",
            "UNDERWRITING",
            "UNDERWRITING_COMPLETED",
            "CAM_READY",
            "CAM_SENT_BACK",
            "CAM_REVIEWED",
            "SANCTION_PENDING",
            "SANCTIONED",
            "KFS_GENERATED",
            "ESIGN_PENDING",
            "ESIGN_COMPLETED",
            "READY_FOR_DISBURSEMENT",
            "DISBURSEMENT_PENDING",
            "DISBURSED",
            "REJECTED",
            "WITHDRAWN",
            "APPROVED"
    );

    private final LoanApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final AuditEventRepository auditEventRepository;
    private final StepExecutionRecordRepository stepExecutionRecordRepository;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;

    @Transactional
    public Map<String, Object> getTimeline(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));

        // Best-effort link of invoice-discounting programs created for this anchor application.
        backfillProgramAnchorApplicationIds(applicationId);

        List<ApplicationStatusHistory> statusRows =
                statusHistoryRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        boolean hasStatusHistory = !statusRows.isEmpty();

        List<ApplicationTimelineEntryView> entries = new ArrayList<>();

        for (ApplicationStatusHistory h : statusRows) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fromStatus", h.getFromStatus());
            details.put("toStatus", h.getToStatus());
            if (h.getRemarks() != null) {
                details.put("remarks", h.getRemarks());
            }
            entries.add(ApplicationTimelineEntryView.of(
                    h.getId(),
                    h.getCreatedAt(),
                    h.getCreatedAt(),
                    "STATUS",
                    statusTitle(h.getFromStatus(), h.getToStatus()),
                    actorLabel(h.getChangedBy()),
                    h.getToStatus(),
                    h.getRemarks() != null ? h.getRemarks() : ("Moved to " + h.getToStatus()),
                    details));
        }

        String lastKycOutcome = null;
        // Process audits oldest→newest so we can collapse repeated KYC_OUTCOME_COMPUTED noise
        List<AuditEvent> audits = new ArrayList<>(
                auditEventRepository.findAllByApplicationIdOrderByCreatedAtDesc(applicationId));
        audits.sort(Comparator.comparing(AuditEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        for (AuditEvent e : audits) {
            if (hasStatusHistory && "STATUS_CHANGE".equalsIgnoreCase(e.getEventType())) {
                continue;
            }
            if ("KYC_OUTCOME_COMPUTED".equalsIgnoreCase(e.getEventType())) {
                String outcome = outcomeFromState(e.getNewState());
                if (outcome != null && outcome.equalsIgnoreCase(lastKycOutcome)) {
                    continue;
                }
                lastKycOutcome = outcome;
            }

            Map<String, Object> details = new LinkedHashMap<>();
            if (e.getPreviousState() != null) {
                details.put("previousState", e.getPreviousState());
            }
            if (e.getNewState() != null) {
                details.put("newState", e.getNewState());
            }
            entries.add(ApplicationTimelineEntryView.of(
                    e.getId(),
                    e.getCreatedAt(),
                    e.getCreatedAt(),
                    "ACTION",
                    actionTitle(e.getEventType(), e.getAction()),
                    actorLabel(e.getPerformedBy()),
                    e.getAction(),
                    e.getDescription() != null ? e.getDescription() : e.getAction(),
                    details));
        }

        for (StepExecutionRecord s : stepExecutionRecordRepository.findByApplicationIdOrderByStartedAtDesc(applicationId)) {
            Map<String, Object> details = new LinkedHashMap<>();
            if (s.getErrorCode() != null) {
                details.put("errorCode", s.getErrorCode());
            }
            if (s.getErrorMessage() != null) {
                details.put("errorMessage", s.getErrorMessage());
            }
            if (s.getOutputJson() != null) {
                details.put("outputJson", s.getOutputJson());
            }
            entries.add(ApplicationTimelineEntryView.of(
                    s.getId(),
                    s.getStartedAt(),
                    s.getCompletedAt(),
                    "PROCESS",
                    s.getStepType(),
                    null,
                    s.getStatus() != null ? s.getStatus().name() : null,
                    s.getErrorMessage() != null ? s.getErrorMessage() : ("Process " + s.getStepType()),
                    details));
        }

        for (ApiAuditLog api : apiAuditLogRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("provider", api.getProviderName());
            details.put("apiName", api.getApiName());
            if (api.getHttpStatusCode() != null) {
                details.put("httpStatusCode", api.getHttpStatusCode());
            }
            if (api.getDurationMs() != null) {
                details.put("durationMs", api.getDurationMs());
            }
            if (api.getTransactionId() != null) {
                details.put("transactionId", api.getTransactionId());
            }
            if (api.getErrorMessage() != null) {
                details.put("errorMessage", api.getErrorMessage());
            }
            details.put("requestPayload", api.getRequestPayload());
            details.put("responsePayload", api.getResponsePayload());
            entries.add(ApplicationTimelineEntryView.of(
                    api.getId(),
                    api.getCreatedAt() != null ? api.getCreatedAt() : api.getRequestTime(),
                    api.getResponseTime(),
                    "INTEGRATION",
                    api.getProviderName() + " · " + api.getApiName(),
                    null,
                    api.getStatus(),
                    "External API call: " + api.getProviderName() + " / " + api.getApiName(),
                    details));
        }

        entries.sort(Comparator
                .comparing(ApplicationTimelineEntryView::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<String> visitedStatuses = statusHistoryRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .stream()
                .map(ApplicationStatusHistory::getToStatus)
                .filter(st -> st != null && !st.isBlank())
                .distinct()
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationId", applicationId.toString());
        body.put("applicationNumber", app.getApplicationNumber());
        body.put("currentStatus", app.getStatus() != null ? app.getStatus().name() : null);
        body.put("lifecycleStages", LIFECYCLE_STAGES);
        body.put("visitedStatuses", visitedStatuses);
        body.put("entries", entries);
        return body;
    }

    private void backfillProgramAnchorApplicationIds(UUID applicationId) {
        try {
            anchorMasterRepository.findBySourceAnchorApplicationId(applicationId).ifPresent(anchor -> {
                for (var sub : subProgramMasterRepository.findByAnchorId(anchor.getId())) {
                    ProgramMaster program = programMasterRepository.findById(sub.getProgramId()).orElse(null);
                    if (program != null && program.getAnchorApplicationId() == null) {
                        program.setAnchorApplicationId(applicationId);
                        programMasterRepository.save(program);
                    }
                }
            });
        } catch (Exception ignored) {
            // Timeline read must not fail if PLP entities are unavailable.
        }
    }

    private static String outcomeFromState(Map<String, Object> state) {
        if (state == null || state.get("outcome") == null) {
            return null;
        }
        return String.valueOf(state.get("outcome"));
    }

    private static String statusTitle(String from, String to) {
        if (from == null || from.isBlank()) {
            return "Created as " + to;
        }
        return from + " → " + to;
    }

    private static String actionTitle(String eventType, String action) {
        if (eventType == null || eventType.isBlank()) {
            return action;
        }
        if (action == null || action.isBlank()) {
            return eventType;
        }
        return eventType + " · " + action;
    }

    private static String actorLabel(UUID id) {
        return id == null ? null : id.toString();
    }
}
