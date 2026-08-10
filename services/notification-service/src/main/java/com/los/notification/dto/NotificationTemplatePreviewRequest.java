package com.los.notification.dto;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationTemplatePreviewRequest {
    private String templateCode;
    private String channel;
    private Map<String, Object> variables;
}
