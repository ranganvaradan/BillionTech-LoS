package com.los.core.service.loan.intake;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;

import java.util.Map;
import java.util.Set;

/**
 * Rules for {@link IntakeSegment#ANCHOR} (invoice discounting anchor onboarding).
 */
public final class AnchorIntakeValidation {

    private static final Set<BorrowerType> ANCHOR_ENTITY_TYPES = Set.of(
            BorrowerType.PROPRIETOR, BorrowerType.PARTNERSHIP, BorrowerType.COMPANY);

    private AnchorIntakeValidation() {
    }

    public static IntakeSegment resolveSegment(CreateApplicationRequest request) {
        return request.getIntakeSegment() != null ? request.getIntakeSegment() : IntakeSegment.BORROWER;
    }

    public static void validateCreate(CreateApplicationRequest request) {
        IntakeSegment seg = resolveSegment(request);
        if (seg != IntakeSegment.ANCHOR) {
            return;
        }
        if (!StandardLoanProduct.ANCHOR_INTAKE_ALLOWED_PRODUCTS.contains(request.getLoanProduct())) {
            throw new BusinessRuleException(
                    "Anchor onboarding is only allowed for invoice discounting loan products.",
                    "ANCHOR_PRODUCT_NOT_ALLOWED",
                    "CREATE_APPLICATION",
                    Map.of("loanProduct", request.getLoanProduct()));
        }
        if (!ANCHOR_ENTITY_TYPES.contains(request.getBorrowerType())) {
            throw new BusinessRuleException(
                    "Anchor onboarding requires a business entity type (proprietor, partnership, or company).",
                    "ANCHOR_BORROWER_TYPE_INVALID",
                    "CREATE_APPLICATION",
                    Map.of("borrowerType", String.valueOf(request.getBorrowerType())));
        }
    }

    public static void rejectBorrowerSelfServiceAnchor(CreateApplicationRequest request) {
        if (resolveSegment(request) != IntakeSegment.ANCHOR) {
            return;
        }
        Map<String, Object> pi = request.getPersonalInfo() != null ? request.getPersonalInfo() : Map.of();
        Object im = pi.get("intakeMode");
        if (im != null && "BORROWER_SELF_SERVICE".equalsIgnoreCase(String.valueOf(im).trim())) {
            throw new BusinessRuleException(
                    "Anchor applications cannot use borrower self-service intake mode.",
                    "ANCHOR_INTAKE_FORBIDDEN",
                    "CREATE_APPLICATION",
                    Map.of("intakeMode", "BORROWER_SELF_SERVICE"));
        }
    }
}
