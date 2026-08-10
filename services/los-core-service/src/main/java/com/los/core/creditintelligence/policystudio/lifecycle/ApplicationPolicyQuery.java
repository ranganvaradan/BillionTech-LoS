package com.los.core.creditintelligence.policystudio.lifecycle;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Query inputs for single-policy resolution — uses evaluation/business date, not wall-clock. */
public record ApplicationPolicyQuery(
        String applicationCode,
        String productCode,
        String facilityType,
        String customerSegment,
        String borrowerType,
        String securedUnsecured,
        String programScheme,
        BigDecimal loanAmount,
        LocalDate evaluationDate
) {}
