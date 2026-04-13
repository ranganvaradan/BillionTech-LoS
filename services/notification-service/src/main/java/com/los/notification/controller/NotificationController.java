package com.los.notification.controller;

import com.los.notification.dto.MultiChannelRequest;
import com.los.notification.dto.NotificationEvent;
import com.los.notification.dto.SendNotificationRequest;
import com.los.notification.entity.NotificationLog;
import com.los.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification management and history")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @Operation(summary = "Send a notification through a specific channel")
    public ResponseEntity<NotificationLog> send(@RequestBody SendNotificationRequest request) {
        NotificationEvent event = new NotificationEvent();
        event.setChannel(request.getChannel());
        event.setRecipient(request.getRecipient());
        event.setTemplateCode(request.getTemplateCode());
        event.setEventType(request.getEventType());
        event.setApplicationId(request.getApplicationId());
        event.setTemplateData(request.getTemplateData());

        NotificationLog log = notificationService.sendNotification(event);
        return ResponseEntity.ok(log);
    }

    @PostMapping("/send-multi")
    @Operation(summary = "Send notification to all channels (SMS + Email + WhatsApp)")
    public ResponseEntity<Map<String, String>> sendMultiChannel(@RequestBody MultiChannelRequest request) {
        notificationService.sendMultiChannel(
                request.getTemplateCode(),
                request.getEventType(),
                request.getMobile(),
                request.getEmail(),
                request.getApplicationId(),
                request.getTemplateData()
        );
        return ResponseEntity.ok(Map.of("status", "QUEUED", "message", "Notifications queued for delivery"));
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get notification history for an application")
    public ResponseEntity<Page<NotificationLog>> getByApplication(
            @PathVariable UUID applicationId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getByApplication(applicationId, pageable));
    }

    @GetMapping("/recipient/{recipient}")
    @Operation(summary = "Get notification history for a recipient")
    public ResponseEntity<Page<NotificationLog>> getByRecipient(
            @PathVariable String recipient,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getByRecipient(recipient, pageable));
    }

    @GetMapping
    @Operation(summary = "Get all notifications with pagination")
    public ResponseEntity<Page<NotificationLog>> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getAll(pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get notification summary statistics")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(notificationService.getSummary());
    }

    @PostMapping("/{id}/resend")
    @Operation(summary = "Resend a specific notification")
    public ResponseEntity<NotificationLog> resend(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.resend(id));
    }
}
