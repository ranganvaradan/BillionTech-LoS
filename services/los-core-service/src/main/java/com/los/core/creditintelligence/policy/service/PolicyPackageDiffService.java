package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyPackageDiff;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PolicyPackageDiffService {

    private final ContentHasher hasher = new ContentHasher();

    public CiPolicyPackageDiff diff(CiExecutablePolicyPackage left, CiExecutablePolicyPackage right) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("leftPackageId", left == null ? null : left.getId());
        diff.put("rightPackageId", right == null ? null : right.getId());
        diff.put("leftHash", left == null ? null : left.getContentHash());
        diff.put("rightHash", right == null ? null : right.getContentHash());
        diff.put("statusChanged", !Objects.equals(
                left == null ? null : left.getStatus(),
                right == null ? null : right.getStatus()));

        Map<String, Object> leftContent = left == null || left.getContent() == null ? Map.of() : left.getContent();
        Map<String, Object> rightContent = right == null || right.getContent() == null ? Map.of() : right.getContent();

        Set<String> leftRules = ruleIds(leftContent);
        Set<String> rightRules = ruleIds(rightContent);
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (String id : rightRules) {
            if (!leftRules.contains(id)) {
                added.add(id);
            }
        }
        for (String id : leftRules) {
            if (!rightRules.contains(id)) {
                removed.add(id);
            }
        }
        diff.put("rulesAdded", added);
        diff.put("rulesRemoved", removed);
        diff.put("contentHashEqual", Objects.equals(
                left == null ? null : left.getContentHash(),
                right == null ? null : right.getContentHash()));
        diff.put("leftContentFingerprint", hasher.hashMap(leftContent));
        diff.put("rightContentFingerprint", hasher.hashMap(rightContent));

        return CiPolicyPackageDiff.builder()
                .id(UUID.randomUUID())
                .leftPackageId(left == null ? UUID.randomUUID() : left.getId())
                .rightPackageId(right == null ? UUID.randomUUID() : right.getId())
                .diff(diff)
                .createdAt(Instant.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Set<String> ruleIds(Map<String, Object> content) {
        Set<String> ids = new TreeSet<>();
        if (!(content.get("rules") instanceof List<?> rules)) {
            return ids;
        }
        for (Object r : rules) {
            if (r instanceof Map<?, ?> raw) {
                Object id = raw.get("ruleId") != null ? raw.get("ruleId") : raw.get("id");
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }
}
