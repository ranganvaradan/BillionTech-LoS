package com.los.core.creditintelligence.validation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.surepass.SurePassItrAdapter;
import com.los.core.creditintelligence.tax.provider.KarzaItrCanonicalExtractor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Asserts ITR turnover / GTI / total income / business income remain distinct.
 */
@Component
public class ItrSemanticsValidator {

    private final ObjectMapper objectMapper;
    private final SurePassItrAdapter surePassItrAdapter;

    public ItrSemanticsValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.surePassItrAdapter = new SurePassItrAdapter(this.objectMapper);
    }

    public ItrSemanticsValidator() {
        this(new ObjectMapper());
    }

    public record ItrSemanticsReport(
            boolean distinct,
            BigDecimal businessTurnover,
            BigDecimal businessProfessionIncome,
            BigDecimal grossTotalIncome,
            BigDecimal totalIncome,
            String message,
            Map<String, Object> detail
    ) {
    }

    public ItrSemanticsReport validateSurePassFixture(String classpath) {
        try (InputStream in = cl().getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing: " + classpath);
            }
            JsonNode payload = objectMapper.readTree(in);
            ProviderExtractionResult result = surePassItrAdapter.extract(
                    payload, new ExtractionRequest(UUID.randomUUID(), UUID.randomUUID(), null, Map.of()));
            BigDecimal turnover = null;
            BigDecimal gti = null;
            BigDecimal total = null;
            BigDecimal bizIncome = null;
            for (Map<String, Object> fact : result.facts()) {
                Object data = fact.get("data");
                if (!(data instanceof Map<?, ?> m)) {
                    continue;
                }
                String type = String.valueOf(fact.get("factType"));
                if ("ITR_FILING".equals(type) || "ITR_RETURN".equals(type) || m.containsKey("parsed_data")
                        || m.containsKey("GrossTotalIncome") || m.containsKey("total_revenue_from_operations")) {
                    turnover = firstBd(turnover, dig(m, "total_revenue_from_operations"), dig(m, "salesTurnover"));
                    gti = firstBd(gti, dig(m, "GrossTotalIncome"), dig(m, "grossTotalIncome"));
                    total = firstBd(total, dig(m, "TotalIncome"), dig(m, "totalIncome"));
                    bizIncome = firstBd(bizIncome, dig(m, "businessProfessionIncome"));
                }
                // nested parsed_data
                Object parsed = dig(m, "parsed_data");
                if (parsed instanceof Map<?, ?> p) {
                    turnover = firstBd(turnover, deepFind(p, "total_revenue_from_operations"));
                    gti = firstBd(gti, deepFind(p, "GrossTotalIncome"));
                    total = firstBd(total, deepFind(p, "TotalIncome"));
                }
            }
            return buildReport(turnover, bizIncome, gti, total);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ITR semantics validation failed for " + classpath, e);
        }
    }

    public ItrSemanticsReport validateKarzaMap(Map<String, Object> payload) {
        var extracted = KarzaItrCanonicalExtractor.extract(payload);
        if (extracted.returns().isEmpty()) {
            return new ItrSemanticsReport(false, null, null, null, null, "No ITR returns", Map.of());
        }
        var r = extracted.returns().get(0);
        BigDecimal turnover = r.business() != null ? r.business().salesTurnover() : null;
        BigDecimal biz = r.income() != null ? r.income().businessProfessionIncome() : null;
        BigDecimal gti = r.income() != null ? r.income().grossTotalIncome() : null;
        BigDecimal total = r.income() != null ? r.income().totalIncome() : null;
        return buildReport(turnover, biz, gti, total);
    }

    private ItrSemanticsReport buildReport(
            BigDecimal turnover, BigDecimal biz, BigDecimal gti, BigDecimal total) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("businessTurnover", turnover);
        detail.put("businessProfessionIncome", biz);
        detail.put("grossTotalIncome", gti);
        detail.put("totalIncome", total);
        boolean collapsed = false;
        if (turnover != null && biz != null && turnover.compareTo(biz) == 0
                && gti != null && turnover.compareTo(gti) == 0
                && total != null && turnover.compareTo(total) == 0) {
            collapsed = true;
        }
        if (turnover != null && gti != null && total != null
                && turnover.compareTo(gti) == 0 && gti.compareTo(total) == 0
                && (biz == null || turnover.compareTo(biz) == 0)) {
            // all present and equal — suspicious single-field collapse
            if (biz != null) {
                collapsed = true;
            }
        }
        boolean distinct = !collapsed;
        if (gti != null && total != null && gti.compareTo(total) == 0 && turnover != null
                && turnover.compareTo(gti) != 0) {
            distinct = true; // GTI==total is allowed; turnover must differ when present
        }
        if (turnover != null && total != null && turnover.compareTo(total) != 0) {
            distinct = true;
        }
        String msg = distinct
                ? "ITR income heads remain distinct (no single totalRevenue collapse)"
                : "FAIL: ITR heads appear collapsed to a single revenue value";
        return new ItrSemanticsReport(distinct, turnover, biz, gti, total, msg, detail);
    }

    private static BigDecimal firstBd(BigDecimal current, Object... candidates) {
        if (current != null) {
            return current;
        }
        for (Object c : candidates) {
            BigDecimal bd = toBd(c);
            if (bd != null) {
                return bd;
            }
        }
        return null;
    }

    private static Object dig(Map<?, ?> m, String key) {
        if (m.containsKey(key)) {
            return m.get(key);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object deepFind(Map<?, ?> m, String key) {
        if (m.containsKey(key)) {
            return m.get(key);
        }
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?> child) {
                Object found = deepFind(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static ClassLoader cl() {
        ClassLoader c = Thread.currentThread().getContextClassLoader();
        return c != null ? c : ItrSemanticsValidator.class.getClassLoader();
    }
}
