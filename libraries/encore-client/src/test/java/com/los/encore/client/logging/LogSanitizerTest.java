package com.los.encore.client.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {

    @Test
    void masksPasswordLikeKeys() {
        String raw = "{\"apiPassword\":\"secret123\",\"x\":1}";
        String masked = LogSanitizer.maskForLog(raw, 500);
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("secret123"));
    }
}
