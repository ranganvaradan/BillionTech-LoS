package com.los.core.creditintelligence.core.clock;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * System-backed {@link EvaluationClock}. Default zone is Asia/Kolkata.
 */
@Component
public class SystemEvaluationClock implements EvaluationClock {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private final Clock clock;

    public SystemEvaluationClock() {
        this(DEFAULT_ZONE);
    }

    public SystemEvaluationClock(ZoneId zone) {
        this.clock = Clock.system(zone != null ? zone : DEFAULT_ZONE);
    }

    public SystemEvaluationClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(DEFAULT_ZONE);
    }

    @Override
    public Instant instant() {
        return clock.instant();
    }

    @Override
    public ZoneId zone() {
        return clock.getZone();
    }
}
