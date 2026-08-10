package com.los.core.service.aa.providers;

import java.util.Map;

public record AaFetchResponse(
        Map<String, Object> fetchedDataSummary,
        Map<String, Object> rawResponse
) {
}
