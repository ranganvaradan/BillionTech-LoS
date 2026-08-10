package com.los.core.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.payment.PayuHashUtil;
import com.los.core.payment.config.LosPayuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class LosPayuVerificationService {

    private final LosPayuProperties payuProperties;
    private final RestTemplate externalRestTemplate;
    private final ObjectMapper objectMapper;

    public LosPayuVerificationService(
            LosPayuProperties payuProperties,
            @Qualifier("payuExternalRestTemplate") RestTemplate externalRestTemplate,
            ObjectMapper objectMapper) {
        this.payuProperties = payuProperties;
        this.externalRestTemplate = externalRestTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isSuccessful(String txnid) {
        if (txnid == null || txnid.isBlank()) {
            return false;
        }
        try {
            String key = payuProperties.getMerchantKey();
            String command = payuProperties.getVerifyCommand();
            String hash = PayuHashUtil.commandHash(key, command, txnid, payuProperties.getMerchantSalt());
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("key", key);
            form.add("command", command);
            form.add("var1", txnid);
            form.add("hash", hash);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String body = externalRestTemplate.postForObject(
                    resolvePostServiceUrl(), new HttpEntity<>(form, headers), String.class);
            if (body == null || body.isBlank()) {
                log.warn("PayU verify_payment empty response for txn {}", txnid);
                return false;
            }
            JsonNode root = objectMapper.readTree(body);
            if (root.path("status").asInt(0) != 1) {
                log.warn("PayU verify_payment status!=1 for txn {}: {}", txnid, body);
                return false;
            }
            JsonNode txnNode = root.path("transaction_details").path(txnid);
            if (txnNode.isMissingNode() || txnNode.isNull()) {
                JsonNode details = root.path("transaction_details");
                if (details.isObject() && details.size() == 1) {
                    txnNode = details.elements().next();
                }
            }
            if (txnNode.isMissingNode() || txnNode.isNull()) {
                log.warn("PayU verify_payment missing transaction_details for txn {}: {}", txnid, body);
                return false;
            }
            String status = txnNode.path("status").asText("");
            String unmapped = txnNode.path("unmappedstatus").asText("");
            return "success".equalsIgnoreCase(status) || "success".equalsIgnoreCase(unmapped);
        } catch (Exception e) {
            log.warn("PayU verify_payment failed for txn {}: {}", txnid, e.getMessage());
            return false;
        }
    }

    private String resolvePostServiceUrl() {
        String configured = payuProperties.getPostServiceUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String gateway = payuProperties.getGatewayUrl();
        if (gateway != null && gateway.contains("test.payu.in")) {
            return "https://test.payu.in/merchant/postservice?form=2";
        }
        return "https://info.payu.in/merchant/postservice?form=2";
    }
}
