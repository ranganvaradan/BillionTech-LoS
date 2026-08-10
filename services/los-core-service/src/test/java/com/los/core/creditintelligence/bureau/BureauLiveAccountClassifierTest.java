package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.domain.LiveAccountDefinition;
import com.los.core.creditintelligence.bureau.service.BureauLiveAccountClassifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BureauLiveAccountClassifierTest {

    private final BureauLiveAccountClassifier classifier = new BureauLiveAccountClassifier();
    private final LocalDate asOf = LocalDate.of(2026, 8, 6);

    @Test
    void liveWhenOpenWithBalance() {
        var r = classifier.classify(new BureauLiveAccountClassifier.LiveInput(
                "Current Account", new BigDecimal("10000"), false, false,
                asOf.minusDays(30), true), 365, asOf);
        assertTrue(r.live());
        assertEquals(LiveAccountDefinition.BUREAU_LIVE_ACCOUNT_DEFINITION_V1, r.definitionVersion());
    }

    @Test
    void notLiveWhenClosedZeroBalance() {
        var r = classifier.classify(new BureauLiveAccountClassifier.LiveInput(
                "Closed", BigDecimal.ZERO, false, false,
                asOf.minusDays(10), false), 365, asOf);
        assertFalse(r.live());
    }

    @Test
    void notLiveWhenWrittenOffZeroBalance() {
        var r = classifier.classify(new BureauLiveAccountClassifier.LiveInput(
                "Written-off", BigDecimal.ZERO, true, false,
                asOf.minusDays(10), false), 365, asOf);
        assertFalse(r.live());
        assertEquals("WRITTEN_OFF_ZERO_BALANCE", r.reason());
    }

    @Test
    void notLiveWhenSettledZeroBalance() {
        var r = classifier.classify(new BureauLiveAccountClassifier.LiveInput(
                "Settled", BigDecimal.ZERO, false, true,
                asOf.minusDays(10), false), 365, asOf);
        assertFalse(r.live());
    }

    @Test
    void notLiveWhenStale() {
        var r = classifier.classify(new BureauLiveAccountClassifier.LiveInput(
                "Active", new BigDecimal("5000"), false, false,
                asOf.minusDays(400), true), 365, asOf);
        assertFalse(r.live());
        assertEquals("STALE_LAST_REPORTED", r.reason());
    }

    @Test
    void liveWithWarningWhenNullLastReported() {
        var r = classifier.classify(new BureauLiveAccountClassifier.LiveInput(
                "Active", new BigDecimal("5000"), false, false,
                null, true), 365, asOf);
        assertTrue(r.live());
        assertEquals("WARNING", r.qualityFlag());
    }
}
