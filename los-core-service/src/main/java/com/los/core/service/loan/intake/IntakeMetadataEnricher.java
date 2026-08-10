package com.los.core.service.loan.intake;

import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Merges intake / ownership metadata into {@code personal_info} for audit and borrower portal consistency.
 */
@Component
@RequiredArgsConstructor
public class IntakeMetadataEnricher {

    private static final String ROLE_BORROWER = "BORROWER";

    private final LosUserRepository losUserRepository;

    public Map<String, Object> enrichPersonalInfo(
            CreateApplicationRequest request,
            UUID customerId,
            UUID actingUserId,
            String actingUserRole) {

        Map<String, Object> out = new LinkedHashMap<>();
        if (request.getPersonalInfo() != null) {
            out.putAll(request.getPersonalInfo());
        }

        out.put("borrowerUserId", customerId.toString());

        Optional<LosUser> customer = losUserRepository.findById(customerId);
        if (customer.isPresent()) {
            LosUser u = customer.get();
            if (str(out.get("borrowerEmail")) == null && u.getEmail() != null) {
                out.put("borrowerEmail", u.getEmail());
            }
            if (str(out.get("borrowerMobile")) == null && u.getMobile() != null) {
                out.put("borrowerMobile", u.getMobile());
            }
        } else {
            if (str(out.get("borrowerEmail")) == null) {
                String e = str(out.get("email"));
                if (e != null) {
                    out.put("borrowerEmail", e);
                }
            }
            if (str(out.get("borrowerMobile")) == null) {
                String m = str(out.get("mobile"));
                if (m == null) {
                    m = str(out.get("phone"));
                }
                if (m == null) {
                    m = str(out.get("borrowerMobile"));
                }
                if (m != null) {
                    out.put("borrowerMobile", m);
                }
            }
        }

        if (actingUserId != null) {
            out.put("createdByUserId", actingUserId.toString());
        }
        if (actingUserRole != null && !actingUserRole.isBlank()) {
            out.put("createdByRole", actingUserRole.trim());
        }

        String mode = str(out.get("intakeMode"));
        if (mode == null) {
            mode = inferIntakeMode(out, actingUserRole);
            out.put("intakeMode", mode);
        }

        boolean sales = "SALES_ASSISTED".equalsIgnoreCase(mode)
                || "SALES_ASSISTED".equalsIgnoreCase(str(out.get("journeyChannel")))
                || "SALES_ASSISTED".equalsIgnoreCase(str(out.get("assistedChannel")));
        if (sales && actingUserId != null) {
            out.put("assistedByUserId", actingUserId.toString());
            if (actingUserRole != null && !actingUserRole.isBlank()) {
                out.put("assistedByRole", actingUserRole.trim());
            }
        }

        return out;
    }

    private static String inferIntakeMode(Map<String, Object> pi, String actingUserRole) {
        if (actingUserRole != null && ROLE_BORROWER.equalsIgnoreCase(actingUserRole.trim())) {
            return "BORROWER_SELF_SERVICE";
        }
        if ("SALES_ASSISTED".equalsIgnoreCase(str(pi.get("journeyChannel")))) {
            return "SALES_ASSISTED";
        }
        if ("SALES_ASSISTED".equalsIgnoreCase(str(pi.get("assistedChannel")))) {
            return "SALES_ASSISTED";
        }
        return "ADMIN_INTERNAL";
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
