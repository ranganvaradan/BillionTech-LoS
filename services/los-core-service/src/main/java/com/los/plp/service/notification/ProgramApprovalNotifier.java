package com.los.plp.service.notification;

import com.los.core.config.RabbitMQConfig;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramApprovalNotifier {

    private final RabbitTemplate rabbitTemplate;
    private final LosUserRepository losUserRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final AnchorMasterRepository anchorMasterRepository;

    @Value("${los.ui.base-url:http://localhost:5173}")
    private String losUiBaseUrl;

    public void notifyPendingL2(ProgramMaster program) {
        send(program, program.getAssignedL2UserId(), "PROGRAM_PENDING_L2", "Program pending your approval");
    }

    public void notifySentBack(ProgramMaster program, String notes) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("notes", notes != null ? notes : "");
        send(program, program.getAssignedL1UserId(), "PROGRAM_SENT_BACK", "Program sent back for revision", extra);
    }

    public void notifyApproved(ProgramMaster program) {
        send(program, program.getAssignedL1UserId(), "PROGRAM_APPROVED", "Program approved");
        resolveAnchorContactEmail(program).ifPresent(email ->
                sendToEmail(program, email, "PROGRAM_APPROVED", "Program terms ready for next steps"));
    }

    private void send(ProgramMaster program, UUID userId, String templateCode, String subjectHint) {
        send(program, userId, templateCode, subjectHint, Map.of());
    }

    private void send(
            ProgramMaster program,
            UUID userId,
            String templateCode,
            String subjectHint,
            Map<String, Object> extra) {
        if (userId == null) {
            return;
        }
        losUserRepository.findById(userId).map(LosUser::getEmail).ifPresent(email ->
                sendToEmail(program, email, templateCode, subjectHint, extra));
    }

    private void sendToEmail(ProgramMaster program, String email, String templateCode, String subjectHint) {
        sendToEmail(program, email, templateCode, subjectHint, Map.of());
    }

    private void sendToEmail(
            ProgramMaster program,
            String email,
            String templateCode,
            String subjectHint,
            Map<String, Object> extra) {
        if (email == null || email.isBlank()) {
            return;
        }
        Map<String, Object> data = buildTemplateData(program, subjectHint);
        data.putAll(extra);
        String routingKey = "notification.email.program_approval";
        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(email.trim())
                .templateCode(templateCode)
                .eventType("PROGRAM_APPROVAL")
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event);
        } catch (Exception e) {
            log.error("Program approval email failed programId={} template={}: {}",
                    program.getId(), templateCode, e.getMessage());
        }
    }

    private Map<String, Object> buildTemplateData(ProgramMaster program, String subjectHint) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("programName", program.getProgramName());
        data.put("programCode", program.getProgramCode());
        data.put("anchorName", resolveAnchorName(program));
        data.put("approvalLink", losUiBaseUrl + "/plp/programs/" + program.getId());
        data.put("subjectHint", subjectHint);
        return data;
    }

    private String resolveAnchorName(ProgramMaster program) {
        return subProgramMasterRepository.findByProgramId(program.getId()).stream()
                .findFirst()
                .flatMap(sp -> anchorMasterRepository.findById(sp.getAnchorId()))
                .map(AnchorMaster::getName)
                .orElse("");
    }

    private java.util.Optional<String> resolveAnchorContactEmail(ProgramMaster program) {
        return subProgramMasterRepository.findByProgramId(program.getId()).stream()
                .findFirst()
                .flatMap(sp -> anchorMasterRepository.findById(sp.getAnchorId()))
                .map(AnchorMaster::getEmail)
                .filter(e -> e != null && !e.isBlank());
    }

    @Data
    @Builder
    private static final class RoutingEmailEvent {
        private String channel;
        private String recipient;
        private String templateCode;
        private String eventType;
        private Map<String, Object> templateData;
    }
}
