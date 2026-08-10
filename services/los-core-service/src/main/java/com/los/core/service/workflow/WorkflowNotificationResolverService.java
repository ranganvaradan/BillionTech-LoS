package com.los.core.service.workflow;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.LoanApplicationRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowNotificationResolverService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ActiveWorkflowConfigService activeWorkflowConfigService;

    public List<ResolvedNotificationAction> resolveForApplication(
            UUID applicationId,
            String stepCode,
            String eventType,
            List<String> defaultRecipients,
            String defaultTemplateCode,
            String defaultChannel) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null) {
            return List.of(defaultAction(defaultRecipients, defaultTemplateCode, defaultChannel, eventType));
        }
        WorkflowConfig workflowConfig = activeWorkflowConfigService.findActiveForApplication(app).orElse(null);
        if (workflowConfig == null) {
            return List.of(defaultAction(defaultRecipients, defaultTemplateCode, defaultChannel, eventType));
        }

        List<ResolvedNotificationAction> processActions = resolveFromProcessMappings(
                workflowConfig, stepCode, eventType, defaultRecipients, defaultTemplateCode, defaultChannel);
        if (!processActions.isEmpty()) {
            return processActions;
        }

        if (workflowConfig.getSteps() == null || workflowConfig.getSteps().isEmpty()) {
            return List.of(defaultAction(defaultRecipients, defaultTemplateCode, defaultChannel, eventType));
        }

        String normalizedStep = normalize(stepCode);
        String normalizedEvent = normalize(eventType);
        for (Map<String, Object> step : workflowConfig.getSteps()) {
            String rawStep = String.valueOf(step.getOrDefault("step", step.getOrDefault("stepType", "")));
            if (!normalize(rawStep).equals(normalizedStep)) {
                continue;
            }
            Object notificationsRaw = step.get("notifications");
            if (!(notificationsRaw instanceof List<?> notifications)) {
                break;
            }
            List<ResolvedNotificationAction> actions = new ArrayList<>();
            for (Object entry : notifications) {
                if (!(entry instanceof Map<?, ?> notificationMap)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> cfg = (Map<String, Object>) notificationMap;
                boolean active = !Boolean.FALSE.equals(cfg.get("enabled"));
                if (!active) {
                    continue;
                }
                String cfgEvent = normalize(String.valueOf(cfg.getOrDefault("eventType", eventType)));
                if (!cfgEvent.equals(normalizedEvent)) {
                    continue;
                }
                String channel = normalize(String.valueOf(cfg.getOrDefault("channel", defaultChannel)));
                String templateCode = String.valueOf(cfg.getOrDefault("templateCode", defaultTemplateCode));
                List<String> recipients = resolveRecipients(cfg.get("recipientType"), defaultRecipients);
                if (recipients.isEmpty()) {
                    continue;
                }
                actions.add(ResolvedNotificationAction.builder()
                        .channel(channel)
                        .templateCode(templateCode)
                        .eventType(eventType)
                        .recipientType(String.valueOf(cfg.getOrDefault("recipientType", "BORROWER_EMAIL")))
                        .delaySeconds(intValue(cfg.get("delaySeconds")))
                        .recipients(recipients)
                        .build());
            }
            if (!actions.isEmpty()) {
                return actions;
            }
            break;
        }

        return List.of(defaultAction(defaultRecipients, defaultTemplateCode, defaultChannel, eventType));
    }

    /**
     * Resolves notification actions only when the workflow explicitly configures this
     * {@code eventType} (process-level mappings or step {@code notifications} array).
     * Never falls back to synthetic defaults — used for optional supplemental events.
     */
    public List<ResolvedNotificationAction> resolveExplicitOnlyForEvent(
            UUID applicationId,
            String stepCode,
            String eventType,
            List<String> defaultRecipients,
            String defaultTemplateCode,
            String defaultChannel) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null) {
            return List.of();
        }
        WorkflowConfig workflowConfig = activeWorkflowConfigService.findActiveForApplication(app).orElse(null);
        if (workflowConfig == null) {
            return List.of();
        }
        List<ResolvedNotificationAction> processActions = resolveFromProcessMappings(
                workflowConfig, stepCode, eventType, defaultRecipients, defaultTemplateCode, defaultChannel);
        if (!processActions.isEmpty()) {
            return processActions;
        }

        if (workflowConfig.getSteps() == null || workflowConfig.getSteps().isEmpty()) {
            return List.of();
        }

        String normalizedStep = normalize(stepCode);
        String normalizedEvent = normalize(eventType);
        for (Map<String, Object> step : workflowConfig.getSteps()) {
            String rawStep = String.valueOf(step.getOrDefault("step", step.getOrDefault("stepType", "")));
            if (!normalize(rawStep).equals(normalizedStep)) {
                continue;
            }
            Object notificationsRaw = step.get("notifications");
            if (!(notificationsRaw instanceof List<?>)) {
                return List.of();
            }
            List<?> notifications = (List<?>) notificationsRaw;
            List<ResolvedNotificationAction> actions = new ArrayList<>();
            for (Object entry : notifications) {
                if (!(entry instanceof Map<?, ?> notificationMap)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> cfg = (Map<String, Object>) notificationMap;
                boolean active = !Boolean.FALSE.equals(cfg.get("enabled"));
                if (!active) {
                    continue;
                }
                String cfgEvent = normalize(String.valueOf(cfg.getOrDefault("eventType", eventType)));
                if (!cfgEvent.equals(normalizedEvent)) {
                    continue;
                }
                String channel = normalize(String.valueOf(cfg.getOrDefault("channel", defaultChannel)));
                String templateCode = String.valueOf(cfg.getOrDefault("templateCode", defaultTemplateCode));
                List<String> recipients = resolveRecipients(cfg.get("recipientType"), defaultRecipients);
                if (recipients.isEmpty()) {
                    continue;
                }
                actions.add(ResolvedNotificationAction.builder()
                        .channel(channel)
                        .templateCode(templateCode)
                        .eventType(eventType)
                        .recipientType(String.valueOf(cfg.getOrDefault("recipientType", "BORROWER_EMAIL")))
                        .delaySeconds(intValue(cfg.get("delaySeconds")))
                        .recipients(recipients)
                        .build());
            }
            return actions;
        }

        return List.of();
    }

    private List<ResolvedNotificationAction> resolveFromProcessMappings(
            WorkflowConfig workflowConfig,
            String stepCode,
            String eventType,
            List<String> defaultRecipients,
            String defaultTemplateCode,
            String defaultChannel) {
        List<Map<String, Object>> mappings = workflowConfig.getProcessNotificationMappings();
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        String processCode = WorkflowNotificationProcessMapper.processCodeForStep(stepCode);
        String normalizedEvent = normalize(eventType);
        List<ResolvedNotificationAction> actions = new ArrayList<>();
        for (Map<String, Object> cfg : mappings) {
            boolean active = !Boolean.FALSE.equals(cfg.get("enabled"));
            if (!active) {
                continue;
            }
            String cfgProcess = normalize(String.valueOf(cfg.getOrDefault("processCode", "")));
            if (!cfgProcess.equals(processCode)) {
                continue;
            }
            String cfgEvent = normalize(String.valueOf(cfg.getOrDefault("eventType", eventType)));
            if (!cfgEvent.equals(normalizedEvent)) {
                continue;
            }
            String channel = normalize(String.valueOf(cfg.getOrDefault("channel", defaultChannel)));
            String templateCode = String.valueOf(cfg.getOrDefault("templateCode", defaultTemplateCode));
            List<String> recipients = resolveRecipients(cfg.get("recipientType"), defaultRecipients);
            if (recipients.isEmpty()) {
                continue;
            }
            actions.add(ResolvedNotificationAction.builder()
                    .channel(channel)
                    .templateCode(templateCode)
                    .eventType(eventType)
                    .recipientType(String.valueOf(cfg.getOrDefault("recipientType", "BORROWER_EMAIL")))
                    .delaySeconds(intValue(cfg.get("delaySeconds")))
                    .recipients(recipients)
                    .build());
        }
        return actions;
    }

    private static ResolvedNotificationAction defaultAction(
            List<String> defaultRecipients,
            String defaultTemplateCode,
            String defaultChannel,
            String eventType) {
        return ResolvedNotificationAction.builder()
                .channel(normalize(defaultChannel))
                .templateCode(defaultTemplateCode)
                .eventType(eventType)
                .recipientType("BORROWER_EMAIL")
                .delaySeconds(0)
                .recipients(sanitize(defaultRecipients))
                .build();
    }

    private static List<String> resolveRecipients(Object recipientType, List<String> defaultRecipients) {
        String type = String.valueOf(recipientType == null ? "BORROWER_EMAIL" : recipientType);
        if ("BORROWER_EMAIL".equalsIgnoreCase(type)) {
            return sanitize(defaultRecipients);
        }
        // Safety-first fallback until additional recipient resolvers are introduced.
        return sanitize(defaultRecipients);
    }

    private static List<String> sanitize(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        return recipients.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static int intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    @Data
    @Builder
    public static class ResolvedNotificationAction {
        private String channel;
        private String templateCode;
        private String eventType;
        private String recipientType;
        private int delaySeconds;
        private List<String> recipients;
    }
}
