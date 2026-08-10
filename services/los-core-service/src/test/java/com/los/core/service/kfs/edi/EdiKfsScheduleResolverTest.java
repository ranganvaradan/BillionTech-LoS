package com.los.core.service.kfs.edi;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdiKfsScheduleResolverTest {

    @Test
    void mapRows_usesCachedEncoreShape() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sequenceNum", 1);
        row.put("valueDateStr", "2026-06-01");
        row.put("installmentAmount", "500");
        row.put("interestAmount", 50);
        row.put("principalAmount", 450);
        row.put("balance", "9500");

        List<EdiKfsScheduleRow> rows = EdiKfsScheduleResolver.mapRows(List.of(row));
        assertEquals(1, rows.size());
        assertEquals("2026-06-01", rows.get(0).demandDate());
        assertEquals(new BigDecimal("500"), rows.get(0).installmentAmount());
    }

    @Test
    void mapRows_fallsBackToNormalInterestAndPrincipalRate() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sequenceNum", 2);
        row.put("valueDateStr", "2026-07-01");
        row.put("installmentAmount", "600");
        row.put("normalInterestRate", 50.0);
        row.put("principalRate", 550.0);
        row.put("balance", "49400");

        List<EdiKfsScheduleRow> rows = EdiKfsScheduleResolver.mapRows(List.of(row));
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("50.0"), rows.get(0).normalInterestAmount());
        assertEquals(new BigDecimal("550.0"), rows.get(0).principalAmount());
    }

    @Test
    void resolveTotalInterest_fromScheduleSum() {
        EdiKfsScheduleRow r1 = new EdiKfsScheduleRow(
                1, "2026-06-01", new BigDecimal("500"), new BigDecimal("50"), new BigDecimal("450"), new BigDecimal("9500"));
        EdiKfsScheduleRow r2 = new EdiKfsScheduleRow(
                2, "2026-06-02", new BigDecimal("500"), new BigDecimal("50"), new BigDecimal("450"), new BigDecimal("9000"));
        BigDecimal total = EdiKfsScheduleResolver.resolveTotalInterest(
                List.of(r1, r2), new BigDecimal("10000"), new BigDecimal("500"), 2);
        assertEquals(new BigDecimal("100"), total);
    }

    @Test
    void resolveTotalInterest_flatRateFallbackWhenNoSchedule() {
        BigDecimal total = EdiKfsScheduleResolver.resolveTotalInterest(
                List.of(), new BigDecimal("10000"), new BigDecimal("1500"), 10);
        assertEquals(new BigDecimal("5000"), total);
    }
}
