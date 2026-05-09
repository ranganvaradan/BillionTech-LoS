package com.los.notification.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class MultiChannelRequest {

    private String templateCode;
    private String eventType;
    private String mobile;
    private String email;
    private UUID applicationId;
    private Map<String, Object> templateData;
}
