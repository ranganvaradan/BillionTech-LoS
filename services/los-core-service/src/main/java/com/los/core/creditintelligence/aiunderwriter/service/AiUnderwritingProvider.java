package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;

import java.util.List;
import java.util.Map;

/**
 * Provider abstraction — stub local or HTTP remote. Never binds production sanction.
 */
public interface AiUnderwritingProvider {

    ProviderResponse analyze(AiUnderwritingContext context, List<String> requestedOutputTypes,
                             Map<String, Object> promptVersions);

    record ProviderResponse(
            boolean available,
            String failureCode,
            String modelProvider,
            String modelName,
            String modelVersion,
            List<RawSuggestion> suggestions
    ) {
        public static ProviderResponse unavailable(String code) {
            return new ProviderResponse(false, code, null, null, null, List.of());
        }
    }

    record RawSuggestion(
            String type,
            String title,
            String content,
            Map<String, Object> structuredPayload,
            List<Object> evidenceRefs,
            List<Object> sourceRefs,
            List<Object> limitations,
            String promptVersion,
            Double modelConfidence
    ) {
    }
}
