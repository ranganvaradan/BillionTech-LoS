package com.los.core.service.assignment;

import com.los.core.model.entity.AssignmentRuleSet;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UserRoleMapping;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Shared match logic for assignment rules and user-role mappings (amount, tenure, geo, product, borrower).
 */
@Component
public class AssignmentScopeMatcher {

    public boolean matchesRule(AssignmentRuleSet r, LoanApplication app, EffectiveUnderwritingContext ctx) {
        BigDecimal req = app.getRequestedAmount();
        if (r.getMinAmount() != null && (req == null || req.compareTo(r.getMinAmount()) < 0)) {
            return false;
        }
        if (r.getMaxAmount() != null && (req == null || req.compareTo(r.getMaxAmount()) > 0)) {
            return false;
        }
        Integer tm = app.getTenureMonths();
        if (r.getMinTenureMonths() != null && (tm == null || tm < r.getMinTenureMonths())) {
            return false;
        }
        if (r.getMaxTenureMonths() != null && (tm == null || tm > r.getMaxTenureMonths())) {
            return false;
        }
        if (r.getGeography() != null && !r.getGeography().isEmpty()) {
            if (!geographyMatches(r.getGeography(), app, ctx)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mappings use optional product/borrower filters; no tenure on mapping rows.
     * <p>When {@code loan_product} is null or blank, the mapping applies to <strong>all</strong> loan products
     * (not only a mismatch when application product is set).
     */
    public boolean matchesMapping(UserRoleMapping m, LoanApplication app, EffectiveUnderwritingContext ctx) {
        if (!productMatches(m.getLoanProduct(), app.getLoanProduct())) {
            return false;
        }
        if (m.getBorrowerType() != null && !m.getBorrowerType().isBlank()
                && !m.getBorrowerType().equalsIgnoreCase(app.getBorrowerType().name())) {
            return false;
        }
        BigDecimal req = app.getRequestedAmount();
        if (m.getMinAmount() != null && (req == null || req.compareTo(m.getMinAmount()) < 0)) {
            return false;
        }
        if (m.getMaxAmount() != null && (req == null || req.compareTo(m.getMaxAmount()) > 0)) {
            return false;
        }
        if (m.getGeography() != null && !m.getGeography().isEmpty()) {
            if (!geographyMatches(m.getGeography(), app, ctx)) {
                return false;
            }
        }
        return true;
    }

    static boolean productMatches(String mappingProduct, String applicationProduct) {
        if (mappingProduct == null || mappingProduct.isBlank()) {
            return true;
        }
        return applicationProduct != null
                && mappingProduct.trim().equalsIgnoreCase(applicationProduct.trim());
    }

    private boolean geographyMatches(
            java.util.Map<String, Object> want,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        if (want.containsKey("state") && want.get("state") != null) {
            String w = want.get("state").toString().trim();
            String have = firstNonBlank(
                    ctx.effectiveState(),
                    app.getPersonalInfo() != null ? str(app.getPersonalInfo().get("state")) : null);
            if (have == null || have.isEmpty() || !have.equalsIgnoreCase(w)) {
                return false;
            }
        }
        if (want.containsKey("city") && want.get("city") != null) {
            String w = want.get("city").toString().trim();
            String have = firstNonBlank(
                    ctx.effectiveCity(),
                    app.getPersonalInfo() != null ? str(app.getPersonalInfo().get("city")) : null);
            if (have == null || have.isEmpty() || !have.equalsIgnoreCase(w)) {
                return false;
            }
        }
        return true;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
