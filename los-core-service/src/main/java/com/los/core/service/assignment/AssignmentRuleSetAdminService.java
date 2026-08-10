package com.los.core.service.assignment;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.AssignmentRuleSetRequest;
import com.los.core.model.dto.response.AssignmentRuleSetResponse;
import com.los.core.model.entity.AssignmentRuleSet;
import com.los.core.model.enums.AssignmentRole;
import com.los.core.repository.AssignmentRuleSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentRuleSetAdminService {

    private final AssignmentRuleSetRepository repository;

    public List<AssignmentRuleSetResponse> list() {
        return repository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public AssignmentRuleSetResponse create(AssignmentRuleSetRequest r) {
        AssignmentRuleSet e = new AssignmentRuleSet();
        apply(e, r);
        e.setActive(false);
        e.setUpdatedAt(Instant.now());
        return toResponse(repository.save(e));
    }

    @Transactional
    public AssignmentRuleSetResponse update(UUID id, AssignmentRuleSetRequest r) {
        AssignmentRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment rule set not found: " + id));
        apply(e, r);
        e.setUpdatedAt(Instant.now());
        return toResponse(repository.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        AssignmentRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment rule set not found: " + id));
        if (e.isActive()) {
            throw new BusinessRuleException(
                    "Cannot delete an active assignment rule. Deactivate it first.",
                    "ASSIGNMENT_RULE_ACTIVE_DELETE_FORBIDDEN",
                    "DEACTIVATE_FIRST",
                    null);
        }
        repository.delete(e);
    }

    @Transactional
    public void activate(UUID id) {
        AssignmentRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment rule set not found: " + id));
        e.setActive(true);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
    }

    @Transactional
    public void deactivate(UUID id) {
        AssignmentRuleSet e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment rule set not found: " + id));
        e.setActive(false);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
    }

    private void apply(AssignmentRuleSet e, AssignmentRuleSetRequest r) {
        String ar = r.getAssignedRole() != null ? r.getAssignedRole().trim() : null;
        if (ar == null || ar.isBlank()) {
            throw new BusinessRuleException(
                    "assignedRole is required",
                    "ASSIGNMENT_ROLE_INVALID",
                    "CHOOSE_ROLE",
                    null);
        }
        AssignmentRole role = AssignmentRole.fromString(ar);
        if (role == null) {
            throw new BusinessRuleException(
                    "Invalid assignment role. Allowed: SALES_OFFICER, RELATIONSHIP_MANAGER, CREDIT_OFFICER, "
                            + "CREDIT_MANAGER, OPERATIONS, ADMINISTRATOR, ACCOUNTS",
                    "ASSIGNMENT_ROLE_INVALID",
                    "CHOOSE_ROLE",
                    null);
        }
        e.setName(r.getName());
        e.setBorrowerType(r.getBorrowerType().name());
        e.setLoanProduct(r.getLoanProduct().trim());
        e.setMinAmount(r.getMinAmount());
        e.setMaxAmount(r.getMaxAmount());
        e.setGeography(r.getGeography());
        e.setMinTenureMonths(r.getMinTenureMonths());
        e.setMaxTenureMonths(r.getMaxTenureMonths());
        e.setAssignedRole(role.name());
        e.setAssignedUserId(r.getAssignedUserId());
        e.setPriority(r.getPriority() != null ? r.getPriority() : 0);
    }

    private AssignmentRuleSetResponse toResponse(AssignmentRuleSet e) {
        return AssignmentRuleSetResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .borrowerType(e.getBorrowerType())
                .loanProduct(e.getLoanProduct())
                .minAmount(e.getMinAmount())
                .maxAmount(e.getMaxAmount())
                .geography(e.getGeography())
                .minTenureMonths(e.getMinTenureMonths())
                .maxTenureMonths(e.getMaxTenureMonths())
                .assignedRole(e.getAssignedRole())
                .assignedUserId(e.getAssignedUserId())
                .priority(e.getPriority())
                .active(e.isActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
