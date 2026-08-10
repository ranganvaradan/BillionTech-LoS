package com.los.notification.dto;

import lombok.Data;

@Data
public class NotificationTemplateUpsertRequest {
    private String templateCode;
    private String channel;
    private String subject;
    private String bodyTemplate;
    private boolean active = true;
}
