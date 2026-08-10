package com.los.core.service.notification;

import com.los.core.config.RabbitMQConfig;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnchorKfsSignedNotifier {

    private final LoanApplicationRepository applicationRepository;
    private final KfsDocumentRepository kfsDocumentRepository;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final RabbitTemplate rabbitTemplate;

    public void notifyAnchorAfterBorrowerKfsSigned(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId).orElse(null);
        if (app == null || !InvoiceDiscountingApplicationRules.isBorrowerFlow(app)) {
            return;
        }
        Optional<String> anchorEmail = resolveAnchorEmail(app);
        if (anchorEmail.isEmpty()) {
            log.warn("[ANCHOR_KFS_EMAIL] no anchor email for application {}", applicationId);
            return;
        }

        KfsDocument kfs = kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElse(null);
        byte[] attachment = null;
        if (kfs != null) {
            try {
                attachment = kfsPdfGenerationService.generateKfsPdf(kfs);
            } catch (Exception e) {
                log.warn("[ANCHOR_KFS_EMAIL] PDF generation failed, sending without attachment: {}", e.getMessage());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("anchorName", resolveAnchorName(app));
        data.put("borrowerName", ApplicationPartyResolver.resolveDisplayName(app));
        data.put("applicationNumber", app.getApplicationNumber());
        if (attachment != null) {
            data.put("attachmentBase64", java.util.Base64.getEncoder().encodeToString(attachment));
            data.put("attachmentFileName", "signed-kfs-" + app.getApplicationNumber() + ".pdf");
        }

        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(anchorEmail.get())
                .templateCode("ANCHOR_KFS_SIGNED_COPY")
                .eventType("ANCHOR_KFS_SIGNED")
                .applicationId(applicationId)
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "notification.email.anchor_kfs", event);
        } catch (Exception e) {
            log.error("[ANCHOR_KFS_EMAIL] publish failed for {}: {}", applicationId, e.getMessage());
        }
    }

    private Optional<String> resolveAnchorEmail(LoanApplication app) {
        if (app.getSubProgramId() == null) {
            return Optional.empty();
        }
        return subProgramMasterRepository.findById(app.getSubProgramId())
                .flatMap(sp -> anchorMasterRepository.findById(sp.getAnchorId()))
                .map(AnchorMaster::getEmail)
                .filter(e -> e != null && !e.isBlank());
    }

    private String resolveAnchorName(LoanApplication app) {
        if (app.getSubProgramId() == null) {
            return "Anchor";
        }
        return subProgramMasterRepository.findById(app.getSubProgramId())
                .flatMap(sp -> anchorMasterRepository.findById(sp.getAnchorId()))
                .map(AnchorMaster::getName)
                .orElse("Anchor");
    }

    @Data
    @Builder
    private static final class RoutingEmailEvent {
        private String channel;
        private String recipient;
        private String templateCode;
        private String eventType;
        private Map<String, Object> templateData;
        private UUID applicationId;
    }
}
