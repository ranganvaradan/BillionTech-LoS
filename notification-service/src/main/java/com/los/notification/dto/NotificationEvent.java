package com.los.notification.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class NotificationEvent {

    private String channel;
    private String recipient;
    private String templateCode;
    private String eventType;
    private Map<String, Object> templateData;
    private UUID applicationId;
}
