package com.los.core.creditintelligence.core.clock;

import java.time.*;

public interface EvaluationClock {
    Instant instant();

    ZoneId zone();

    default LocalDate today() {
        return LocalDate.ofInstant(instant(), zone());
    }

    default ZonedDateTime now() {
        return instant().atZone(zone());
    }
}
