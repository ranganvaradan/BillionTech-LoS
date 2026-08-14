package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application-time bureau executability: Gate-3 {@link ParameterExecutabilitySupport} is authoritative;
 * {@link BureauDataReadinessProbe} supplies ingest evidence only.
 */
@Service
@RequiredArgsConstructor
public class BureauApplicationExecutabilityService {

    private final BureauDataReadinessProbe bureauDataReadinessProbe;

    @Transactional(readOnly = true)
    public Map<String, Object> evaluateForApplication(UUID applicationId, String parameterId) {
        Map<String, Object> exec = new LinkedHashMap<>(
                ParameterExecutabilitySupport.evaluate(parameterId));
        if (applicationId == null || parameterId == null || !bureauDataReadinessProbe.isBureauPath(parameterId)) {
            return exec;
        }

        BureauDataReadinessProbe.BureauReadiness evidence =
                bureauDataReadinessProbe.readinessForPath(applicationId, parameterId);
        exec.put("bureauEvidence", evidence.name());
        exec.put("bureauPullPresent", bureauDataReadinessProbe.hasAnyBureauIngest(applicationId));

        switch (evidence) {
            case NO_BUREAU_PULL -> exec.put("applicationDataQuality", "NO_BUREAU_PULL");
            case INGEST_INCOMPLETE -> exec.put("applicationDataQuality", "DATA_INSUFFICIENT");
            case READY -> exec.put("applicationDataQuality", "PASS");
            default -> { }
        }
        return exec;
    }
}
