package com.los.core.service.kfs;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncorePreOpenKfsMapperTest {

  private static final String PRE_OPEN_JSON = """
      {
        "apr": "14.25",
        "totalFeeDue": "1100.00",
        "repaymentSchedule": [
          {
            "sequenceNum": 1,
            "amount1": "500.00",
            "amount2": "9500.00",
            "amount3": "500.00",
            "valueDateStr": "2026-06-01"
          },
          {
            "sequenceNum": 2,
            "amount1": "500.00",
            "amount2": "9000.00",
            "amount3": "500.00",
            "valueDateStr": "2026-06-02"
          }
        ]
      }
      """;

  @Test
  void fromCharges_mapsInstallmentTotalsAndApr() {
    Map<String, Object> charges = new LinkedHashMap<>();
    charges.put("encorePreOpenSummaryJson", PRE_OPEN_JSON);

    Optional<EncorePreOpenKfsMapper.Figures> figures =
        EncorePreOpenKfsMapper.fromCharges(charges, new BigDecimal("10000"));

    assertTrue(figures.isPresent());
    EncorePreOpenKfsMapper.Figures f = figures.get();
    assertEquals(new BigDecimal("500.00"), f.installmentAmount());
    assertEquals(new BigDecimal("1000.00"), f.totalRepayment());
    assertEquals(2, f.installmentCount());
    assertEquals(new BigDecimal("14.25"), f.apr());
    assertEquals(new BigDecimal("1100.00"), f.processingFee());
  }

  @Test
  void fromCharges_parsesAprWithPercentSuffix() {
    Map<String, Object> charges = new LinkedHashMap<>();
    charges.put("encorePreOpenSummaryJson", """
        {
          "apr": "14.25%",
          "repaymentSchedule": [
            {
              "sequenceNum": 1,
              "amount1": "733.00",
              "amount2": "64267.00",
              "amount3": "733.00",
              "valueDateStr": "2026-07-03"
            }
          ]
        }
        """);

    Optional<EncorePreOpenKfsMapper.Figures> figures =
        EncorePreOpenKfsMapper.fromCharges(charges, new BigDecimal("65000"));

    assertTrue(figures.isPresent());
    assertEquals(new BigDecimal("14.25"), figures.get().apr());
    assertEquals(new BigDecimal("733.00"), figures.get().installmentAmount());
  }

  @Test
  void fromCharges_usesStoredScheduleWhenSummaryHasNoRepaymentSchedule() {
    Map<String, Object> charges = new LinkedHashMap<>();
    charges.put("encorePreOpenSummaryJson", """
        {"apr":"12.50%","nextInstallmentAmount":"733.00","totalFeeDue":"1300.00"}
        """);
    charges.put("encoreRepaymentScheduleJson", """
        [
          {"sequenceNum":1,"installmentAmount":"733.00","valueDateStr":"2026-07-03"},
          {"sequenceNum":2,"installmentAmount":"733.00","valueDateStr":"2026-07-04"}
        ]
        """);

    Optional<EncorePreOpenKfsMapper.Figures> figures =
        EncorePreOpenKfsMapper.fromCharges(charges, new BigDecimal("65000"));

    assertTrue(figures.isPresent());
    EncorePreOpenKfsMapper.Figures f = figures.get();
    assertEquals(new BigDecimal("12.50"), f.apr());
    assertEquals(new BigDecimal("733.00"), f.installmentAmount());
    assertEquals(new BigDecimal("1466.00"), f.totalRepayment());
    assertEquals(new BigDecimal("1300.00"), f.processingFee());
    assertEquals(2, f.installmentCount());
  }
}
