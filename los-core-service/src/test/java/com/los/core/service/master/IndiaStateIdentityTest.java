package com.los.core.service.master;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndiaStateIdentityTest {

    @Test
    void tamilNaduMatchesNameCodeAndIso() {
        List<String> allowed = List.of("Tamil Nadu");
        assertTrue(IndiaStateIdentity.matchesAllowed("Tamil Nadu", allowed));
        assertTrue(IndiaStateIdentity.matchesAllowed("TAMIL_NADU", allowed));
        assertTrue(IndiaStateIdentity.matchesAllowed("TN", allowed));
        assertTrue(IndiaStateIdentity.matchesAllowed("tn", allowed));
        assertFalse(IndiaStateIdentity.matchesAllowed("Kerala", allowed));
        assertFalse(IndiaStateIdentity.matchesAllowed("KA", allowed));
    }

    @Test
    void maharashtraMatchesMh() {
        assertTrue(IndiaStateIdentity.matchesAllowed("MH", List.of("Maharashtra")));
        assertTrue(IndiaStateIdentity.matchesAllowed("Maharashtra", List.of("MH")));
    }
}
