package com.los.core.creditintelligence.core.clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Fixed instant + zone clock for deterministic evaluation / replay.
 */
public class FixedEvaluationClock implements EvaluationClock {

    private final Instant instant;
    private final ZoneId zone;

    public FixedEvaluationClock(Instant instant, ZoneId zone) {
        this.instant = Objects.requireNonNull(instant, "instant");
        this.zone = zone != null ? zone : SystemEvaluationClock.DEFAULT_ZONE;
    }

    public FixedEvaluationClock(Instant instant) {
        this(instant, SystemEvaluationClock.DEFAULT_ZONE);
    }

    public FixedEvaluationClock(LocalDate date, ZoneId zone) {
        ZoneId z = zone != null ? zone : SystemEvaluationClock.DEFAULT_ZONE;
        this.zone = z;
        this.instant = Objects.requireNonNull(date, "date").atStartOfDay(z).toInstant();
    }

    public FixedEvaluationClock(LocalDate date) {
        this(date, SystemEvaluationClock.DEFAULT_ZONE);
    }

    /** Noon local time on the given date (useful when start-of-day is undesirable). */
    public static FixedEvaluationClock atLocalNoon(LocalDate date, ZoneId zone) {
        ZoneId z = zone != null ? zone : SystemEvaluationClock.DEFAULT_ZONE;
        return new FixedEvaluationClock(date.atTime(LocalTime.NOON).atZone(z).toInstant(), z);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId zone() {
        return zone;
    }
}
