package com.los.notification.service;

import com.los.notification.dto.NotificationTemplatePreviewRequest;
import com.los.notification.dto.NotificationTemplateUpsertRequest;
import com.los.notification.entity.NotificationTemplate;
import com.los.notification.repository.NotificationTemplateRepository;
import com.los.notification.template.NotificationTemplateEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationTemplateEngine notificationTemplateEngine;

    public List<NotificationTemplate> listTemplates() {
        return notificationTemplateRepository.findAllByOrderByTemplateCodeAscChannelAsc();
    }

    @Transactional
    public NotificationTemplate createTemplate(NotificationTemplateUpsertRequest request) {
        NotificationTemplate entity = NotificationTemplate.builder()
                .templateCode(normalize(request.getTemplateCode()))
                .channel(normalize(request.getChannel()))
                .subject(trimToEmpty(request.getSubject()))
                .bodyTemplate(trimToEmpty(request.getBodyTemplate()))
                .active(request.isActive())
                .build();
        return notificationTemplateRepository.save(entity);
    }

    @Transactional
    public NotificationTemplate updateTemplate(UUID templateId, NotificationTemplateUpsertRequest request) {
        NotificationTemplate existing = notificationTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        existing.setTemplateCode(normalize(request.getTemplateCode()));
        existing.setChannel(normalize(request.getChannel()));
        existing.setSubject(trimToEmpty(request.getSubject()));
        existing.setBodyTemplate(trimToEmpty(request.getBodyTemplate()));
        existing.setActive(request.isActive());
        existing.setUpdatedAt(Instant.now());
        return notificationTemplateRepository.save(existing);
    }

    @Transactional
    public NotificationTemplate toggleTemplate(UUID templateId, boolean active) {
        NotificationTemplate existing = notificationTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        existing.setActive(active);
        existing.setUpdatedAt(Instant.now());
        return notificationTemplateRepository.save(existing);
    }

    public Map<String, String> previewTemplate(NotificationTemplatePreviewRequest request) {
        String templateCode = normalize(request.getTemplateCode());
        String channel = normalize(request.getChannel());
        Map<String, Object> variables = request.getVariables() != null ? request.getVariables() : Map.of();
        return Map.of(
                "templateCode", templateCode,
                "channel", channel,
                "subject", notificationTemplateEngine.renderSubject(templateCode, variables),
                "body", notificationTemplateEngine.render(templateCode, channel, variables)
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
