package com.los.core.service.loan.intake;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.CreateApplicationRequest;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Picks the {@code customer_id} stored on a new loan application so the borrower portal
 * (and later LMS handoff) can identify the end-customer, while staff creation flows
 * can prefer a registered borrower matched from email/phone in the request.
 */
@Component
@RequiredArgsConstructor
public class ApplicationCustomerIdResolver {

    private static final String ROLE_BORROWER = "BORROWER";

    private final LosUserRepository losUserRepository;

    public UUID resolveCustomerId(
            CreateApplicationRequest request,
            UUID actingUserId,
            String actingUserRole) {

        String role = actingUserRole != null ? actingUserRole.trim() : "";
        if (ROLE_BORROWER.equalsIgnoreCase(role)) {
            if (actingUserId == null) {
                throw new BusinessRuleException(
                        "Borrower sign-in is required to start an application.",
                        "AUTH_REQUIRED",
                        "CREATE_APPLICATION",
                        Map.of("role", "BORROWER"));
            }
            return actingUserId;
        }

        Optional<UUID> fromBorrowerIdentity = findBorrowerUserIdByContact(request);
        if (fromBorrowerIdentity.isPresent()) {
            return fromBorrowerIdentity.get();
        }

        if (actingUserId != null) {
            // Staff initiated app but no registered borrower found — do not use staff id as customer.
            return UUID.randomUUID();
        }

        return UUID.randomUUID();
    }

    private Optional<UUID> findBorrowerUserIdByContact(CreateApplicationRequest request) {
        Map<String, Object> pi = request.getPersonalInfo() != null ? request.getPersonalInfo() : Map.of();
        for (String key : new String[] {"email", "contactEmail", "borrowerEmail"}) {
            String email = str(pi.get(key));
            if (email != null) {
                Optional<LosUser> u = losUserRepository.findByEmailIgnoreCase(email);
                if (u.isPresent()) {
                    return Optional.of(u.get().getId());
                }
            }
        }
        for (String key : new String[] {"phone", "mobile", "borrowerMobile", "contactPhone"}) {
            String phone = str(pi.get(key));
            String digits = digitsOnly(phone);
            if (digits.length() < 10) {
                continue;
            }
            Optional<LosUser> u = findByMobileDigits(digits);
            if (u.isPresent()) {
                return Optional.of(u.get().getId());
            }
        }
        return Optional.empty();
    }

    private Optional<LosUser> findByMobileDigits(String tenPlusDigits) {
        List<LosUser> withMobile = losUserRepository.findByMobileIsNotNull();
        for (LosUser u : withMobile) {
            if (tenPlusDigits.equals(digitsOnly(u.getMobile()))) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String digitsOnly(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\D", "");
    }
}
