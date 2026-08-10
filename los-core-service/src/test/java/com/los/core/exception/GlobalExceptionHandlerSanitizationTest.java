package com.los.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerSanitizationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void classCastException_notExposedVerbatim() {
        ClassCastException ex = new ClassCastException(
                "class java.util.HashMap cannot be cast to class java.lang.String");
        ResponseEntity<Map<String, Object>> res = handler.handleGeneral(ex);
        assertEquals(500, res.getStatusCode().value());
        assertEquals("An unexpected error occurred", res.getBody().get("message"));
        assertFalse(String.valueOf(res.getBody().get("message")).contains("HashMap"));
        assertEquals("ClassCastException", res.getBody().get("errorClass"));
    }

    @Test
    void genericException_usesSafeBusinessMessage() {
        ResponseEntity<Map<String, Object>> res = handler.handleGeneral(new RuntimeException("secret stack"));
        assertEquals("An unexpected error occurred", res.getBody().get("message"));
        assertTrue(res.getBody().containsKey("errorClass"));
    }
}
