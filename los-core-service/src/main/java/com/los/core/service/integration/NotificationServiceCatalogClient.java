package com.los.core.service.integration;

import com.los.core.model.dto.response.WorkflowEventTemplateMappingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.util.List;

/**
 * Read-only calls to notification-service for configuration UIs (workflow editor).
 * Failures return an empty list so workflow execution and saving are never blocked.
 */
@Slf4j
@Service
public class NotificationServiceCatalogClient {

    private final RestClient notificationRestClient;

    public NotificationServiceCatalogClient(
            @Qualifier("notificationRestClient") RestClient notificationRestClient) {
        this.notificationRestClient = notificationRestClient;
    }

    public List<WorkflowEventTemplateMappingResponse> listWorkflowEventTemplateMappings(
            String workflowEvent, String channel) {
        try {
            List<WorkflowEventTemplateMappingResponse> body = notificationRestClient.get()
                    .uri(uri -> buildMappingUri(uri, workflowEvent, channel))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body != null ? body : List.of();
        } catch (RestClientException ex) {
            log.warn(
                    "[NOTIFICATION_CATALOG] failed to load workflow-event-template-mappings (workflowEvent={}, channel={}): {}",
                    workflowEvent,
                    channel,
                    ex.getMessage());
            return List.of();
        }
    }

    private static java.net.URI buildMappingUri(UriBuilder uriBuilder, String workflowEvent, String channel) {
        UriBuilder b = uriBuilder.path("/api/v1/workflow-event-template-mappings");
        if (StringUtils.hasText(workflowEvent)) {
            b.queryParam("workflowEvent", workflowEvent.trim());
        }
        if (StringUtils.hasText(channel)) {
            b.queryParam("channel", channel.trim());
        }
        return b.build();
    }
}
