package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data-contract tests for CI fact value envelopes.
 * Hibernate 6.5 ClassCastException (HashMap→String) occurs when {@code Object}-typed
 * JSON columns receive Map values; facts must persist as {@code Map<String,Object>}.
 */
class UnderwritingFactValueContractTest {

    @Test
    void wrapValue_scalarBecomesEnvelopeMap() {
        Map<String, Object> wrapped = UnderwritingFactSnapshotBuilder.wrapValue(new BigDecimal("85000"));
        assertEquals("85000", wrapped.get("v"));
        assertInstanceOf(Map.class, wrapped);
    }

    @Test
    void wrapValue_structuredMapPreserved() {
        Map<String, Object> structured = new HashMap<>();
        structured.put("monthlyIncome", 85000);
        structured.put("source", "manual");
        Map<String, Object> wrapped = UnderwritingFactSnapshotBuilder.wrapValue(structured);
        assertEquals(85000, wrapped.get("monthlyIncome"));
        assertEquals("manual", wrapped.get("source"));
        assertFalse(wrapped.containsKey("v"));
    }

    @Test
    void wrapValue_missingBecomesNullEnvelope() {
        Map<String, Object> wrapped = UnderwritingFactSnapshotBuilder.wrapValue(null);
        assertTrue(wrapped.containsKey("v"));
        assertNull(wrapped.get("v"));
    }

    @Test
    void wrapValue_malformedNonMapBecomesEnvelope() {
        Map<String, Object> wrapped = UnderwritingFactSnapshotBuilder.wrapValue(Listish.bad());
        assertEquals("not-a-number", wrapped.get("v"));
    }

    @Test
    void entityField_isTypedAsMapNotObject() throws Exception {
        var field = CiUnderwritingFact.class.getDeclaredField("value");
        assertEquals(Map.class, field.getType());
    }

    @Test
    void manualApplicationStyleEnvelope_matchesFixtureStyle() {
        // Manual intake scalars and fixture scalars both become {"v": ...}
        Map<String, Object> manual = UnderwritingFactSnapshotBuilder.wrapValue(85000);
        Map<String, Object> fixture = UnderwritingFactSnapshotBuilder.wrapValue("85000");
        assertEquals(85000, manual.get("v"));
        assertEquals("85000", fixture.get("v"));
        assertInstanceOf(Map.class, manual);
        assertInstanceOf(Map.class, fixture);
    }

    /** Stand-in for a non-Map malformed payload. */
    private static final class Listish {
        static Object bad() {
            return "not-a-number";
        }
    }
}
