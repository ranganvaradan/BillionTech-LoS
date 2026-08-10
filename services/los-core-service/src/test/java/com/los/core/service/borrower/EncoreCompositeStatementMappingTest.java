package com.los.core.service.borrower;

import com.los.core.model.dto.response.BorrowerStatementLineResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EncoreCompositeStatementMappingTest {

    @Test
    void mapsCompositeStatementRowsWithNestedAccountEntryDto() throws Exception {
        Map<String, Object> amount = Map.of("magnitude", 600000.00, "displayValue", "₹600,000.00");
        Map<String, Object> balance = Map.of("magnitude", 600000.00);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("valueDate", "2026-06-12");
        dto.put("accountEntryType", "DEBIT");
        dto.put("amount", amount);
        dto.put("description", "");
        dto.put("transactionName", "Disbursement");
        Map<String, Object> row = Map.of("accountEntryDto", dto, "balance", balance);

        List<BorrowerStatementLineResponse> lines = invokeMap(List.of(row));
        assertEquals(1, lines.size());
        BorrowerStatementLineResponse line = lines.get(0);
        assertEquals("Disbursement", line.getDescription());
        assertNull(line.getCredit());
        assertEquals(new BigDecimal("600000.00"), line.getDebit());
        assertEquals(new BigDecimal("600000.00"), line.getBalance());
        assertNotNull(line.getValueDate());
    }

    @SuppressWarnings("unchecked")
    private static List<BorrowerStatementLineResponse> invokeMap(List<Map<String, Object>> entries) throws Exception {
        Method m = BorrowerPortalService.class.getDeclaredMethod("mapEncoreStatementEntries", List.class);
        m.setAccessible(true);
        return (List<BorrowerStatementLineResponse>) m.invoke(null, entries);
    }
}
