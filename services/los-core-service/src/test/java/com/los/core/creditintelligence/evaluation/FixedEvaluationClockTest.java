package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.core.clock.SystemEvaluationClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FixedEvaluationClockTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void fixedInstantReturnsStableTodayAndInstant() {
        Instant fixed = Instant.parse("2026-03-15T06:30:00Z");
        FixedEvaluationClock clock = new FixedEvaluationClock(fixed, IST);

        assertThat(clock.instant()).isEqualTo(fixed);
        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(clock.zone()).isEqualTo(IST);

        // Second read is identical
        assertThat(clock.instant()).isEqualTo(fixed);
        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void fixedLocalDateReturnsStableToday() {
        LocalDate date = LocalDate.of(2025, 12, 1);
        FixedEvaluationClock clock = new FixedEvaluationClock(date, IST);

        assertThat(clock.today()).isEqualTo(date);
        assertThat(clock.instant()).isEqualTo(date.atStartOfDay(IST).toInstant());
    }

    @Test
    void advancingAnotherClockDoesNotAffectFixed() {
        Instant fixed = Instant.parse("2026-01-10T00:00:00Z");
        FixedEvaluationClock frozen = new FixedEvaluationClock(fixed, ZoneOffset.UTC);

        Clock mutable = Clock.fixed(Instant.parse("2026-01-10T00:00:00Z"), ZoneOffset.UTC);
        SystemEvaluationClock live = new SystemEvaluationClock(mutable);

        assertThat(frozen.instant()).isEqualTo(live.instant());

        Clock advanced = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
        SystemEvaluationClock liveLater = new SystemEvaluationClock(advanced);

        assertThat(liveLater.instant()).isAfter(frozen.instant());
        assertThat(frozen.instant()).isEqualTo(fixed);
        assertThat(frozen.today()).isEqualTo(LocalDate.of(2026, 1, 10));
    }
}
