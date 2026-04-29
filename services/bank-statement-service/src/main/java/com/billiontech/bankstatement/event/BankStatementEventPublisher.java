package com.billiontech.bankstatement.event;

import com.billiontech.bankstatement.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankStatementEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishParsed(Long statementId, String bankName, int transactionCount) {
        try {
            Map<String, Object> event = Map.of(
                    "event", "STATEMENT_PARSED",
                    "statementId", statementId,
                    "bankName", bankName,
                    "transactionCount", transactionCount);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_PARSED, event);
            log.debug("Published STATEMENT_PARSED event for statement {}", statementId);
        } catch (Exception e) {
            log.warn("Failed to publish STATEMENT_PARSED event: {}", e.getMessage());
        }
    }

    public void publishAnalysisComplete(Long statementId, double creditworthinessScore) {
        try {
            Map<String, Object> event = Map.of(
                    "event", "ANALYSIS_COMPLETE",
                    "statementId", statementId,
                    "creditworthinessScore", creditworthinessScore);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_ANALYSIS_COMPLETE, event);
            log.debug("Published ANALYSIS_COMPLETE event for statement {}", statementId);
        } catch (Exception e) {
            log.warn("Failed to publish ANALYSIS_COMPLETE event: {}", e.getMessage());
        }
    }

    public void publishRedFlagDetected(Long statementId, String applicationId, int redFlagCount) {
        try {
            Map<String, Object> event = Map.of(
                    "event", "RED_FLAG_DETECTED",
                    "statementId", statementId,
                    "applicationId", applicationId != null ? applicationId : "",
                    "redFlagCount", redFlagCount);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_RED_FLAG, event);
            log.debug("Published RED_FLAG_DETECTED event for statement {}", statementId);
        } catch (Exception e) {
            log.warn("Failed to publish RED_FLAG_DETECTED event: {}", e.getMessage());
        }
    }
}
