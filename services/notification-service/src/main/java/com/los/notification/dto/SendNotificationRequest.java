package com.los.notification.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class SendNotificationRequest {

    private String channel;
    private String recipient;
    private String templateCode;
    private String eventType;
    private UUID applicationId;
    private Map<String, Object> templateData;
}
