package com.los.notification.service;

import com.los.notification.dto.WorkflowEventTemplateMappingResponse;
import com.los.notification.entity.WorkflowEventTemplateMapping;
import com.los.notification.repository.WorkflowEventTemplateMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WorkflowEventTemplateMappingService {

    private final WorkflowEventTemplateMappingRepository repository;

    public List<WorkflowEventTemplateMappingResponse> list(String workflowEvent, String channel) {
        boolean hasEvent = StringUtils.hasText(workflowEvent);
        boolean hasChannel = StringUtils.hasText(channel);

        List<WorkflowEventTemplateMapping> rows;
        if (hasEvent && hasChannel) {
            rows = repository.findByWorkflowEventAndChannelAndActiveTrueOrderBySortOrderAscTemplateCodeAsc(
                    normalizeEvent(workflowEvent), normalizeChannel(channel));
        } else if (hasEvent) {
            rows = repository.findByWorkflowEventAndActiveTrueOrderBySortOrderAscTemplateCodeAsc(
                    normalizeEvent(workflowEvent));
        } else {
            rows = repository.findByActiveTrueOrderByWorkflowEventAscChannelAscSortOrderAscTemplateCodeAsc();
        }

        return rows.stream()
                .filter(WorkflowEventTemplateMapping::isActive)
                .map(this::toResponse)
                .toList();
    }

    private WorkflowEventTemplateMappingResponse toResponse(WorkflowEventTemplateMapping e) {
        return WorkflowEventTemplateMappingResponse.builder()
                .id(e.getId())
                .workflowEvent(e.getWorkflowEvent())
                .channel(e.getChannel())
                .templateCode(e.getTemplateCode())
                .defaultMapping(e.isDefaultMapping())
                .active(e.isActive())
                .sortOrder(e.getSortOrder())
                .build();
    }

    private static String normalizeEvent(String workflowEvent) {
        return workflowEvent == null ? "" : workflowEvent.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
    }
}
