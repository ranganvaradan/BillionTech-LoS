package com.los.core.service.integration.providers.impl;

import com.los.core.service.integration.providers.IBureauProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("equifaxBureauProvider")
public class EquifaxBureauProvider implements IBureauProvider {

    @Override
    public BureauPullResult pullReport(Map<String, Object> borrowerInfo) {
        log.info("[Equifax] Pulling credit bureau report for: {}", borrowerInfo.getOrDefault("name", "unknown"));

        String transactionId = "EQX-" + UUID.randomUUID().toString().substring(0, 8);

        String pan = (String) borrowerInfo.getOrDefault("panNumber", "");
        String name = (String) borrowerInfo.getOrDefault("name", "");

        if (pan.isEmpty()) {
            return new BureauPullResult(false, 0, null, transactionId, "PAN number is required for bureau pull");
        }

        // Simulated bureau response
        int creditScore = 720;
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("creditScore", creditScore);
        reportData.put("scoreVersion", "ERS 3.0");
        reportData.put("totalAccounts", 5);
        reportData.put("activeAccounts", 3);
        reportData.put("closedAccounts", 2);
        reportData.put("overdueAccounts", 0);
        reportData.put("totalOutstanding", 450000);
        reportData.put("totalSanctioned", 1500000);
        reportData.put("oldestAccount", "2018-06-15");
        reportData.put("recentEnquiries", 2);
        reportData.put("enquiryAge30Days", 1);
        reportData.put("dpd30Plus", 0);
        reportData.put("dpd60Plus", 0);
        reportData.put("dpd90Plus", 0);
        reportData.put("writtenOff", 0);
        reportData.put("settled", 0);
        reportData.put("suitFiled", false);
        reportData.put("willfulDefaulter", false);

        return new BureauPullResult(true, creditScore, reportData, transactionId, null);
    }
}
