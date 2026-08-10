package com.los.core.service.assignment;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.request.UserRoleMappingRequest;
import com.los.core.model.dto.response.UserRoleMappingResponse;
import com.los.core.model.entity.LosUser;
import com.los.core.model.entity.UserRoleMapping;
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.UserRoleMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRoleMappingAdminService {

    private final UserRoleMappingRepository repository;
    private final LosUserRepository losUserRepository;

    public List<UserRoleMappingResponse> list() {
        return repository.findAll(Sort.by("priority").descending()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserRoleMappingResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public UserRoleMappingResponse create(UserRoleMappingRequest r) {
        LosUser u = losUserRepository.findById(r.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + r.getUserId()));
        if (!u.isActive()) {
            throw new BusinessRuleException(
                    "User is inactive; activate the user or pick another",
                    "USER_INACTIVE",
                    "ACTIVATE_USER",
                    Map.of("userId", r.getUserId().toString()));
        }
        UserRoleMapping m = new UserRoleMapping();
        apply(r, m);
        assertNoDuplicateActiveScope(m, null);
        return toResponse(repository.save(m));
    }

    @Transactional
    public UserRoleMappingResponse update(UUID id, UserRoleMappingRequest r) {
        UserRoleMapping m = find(id);
        LosUser u = losUserRepository.findById(r.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + r.getUserId()));
        if (!u.isActive() && r.isActive()) {
            throw new BusinessRuleException(
                    "Cannot attach an active mapping to an inactive user",
                    "USER_INACTIVE",
                    "ACTIVATE_USER",
                    null);
        }
        apply(r, m);
        assertNoDuplicateActiveScope(m, id);
        return toResponse(repository.save(m));
    }

    private void assertNoDuplicateActiveScope(UserRoleMapping candidate, UUID excludeId) {
        if (!candidate.isActive()) {
            return;
        }
        List<UserRoleMapping> peers = repository.findByActiveIsTrueAndLosRoleOrderByPriorityDesc(candidate.getLosRole());
        for (UserRoleMapping o : peers) {
            if (excludeId != null && o.getId().equals(excludeId)) {
                continue;
            }
            if (sameScopeKey(o, candidate)) {
                throw new BusinessRuleException(
                        "An active mapping with the same role, product (including All/NULL), borrower, amount range, and geography already exists",
                        "USER_ROLE_MAPPING_DUPLICATE_SCOPE",
                        "UPDATE_OR_DEACTIVATE",
                        Map.of("conflictsWithId", o.getId().toString()));
            }
        }
    }

    /**
     * Business key for "duplicate" — {@code product} and {@code borrower} NULL/blank = wildcard for that dimension
     * (must match the same on another row to be considered duplicate).
     */
    private static boolean sameScopeKey(UserRoleMapping a, UserRoleMapping b) {
        if (!a.getLosRole().equals(b.getLosRole())) {
            return false;
        }
        if (!sameStringDim(a.getLoanProduct(), b.getLoanProduct())) {
            return false;
        }
        if (!sameStringDim(a.getBorrowerType(), b.getBorrowerType())) {
            return false;
        }
        if (!Objects.equals(a.getMinAmount(), b.getMinAmount())) {
            return false;
        }
        if (!Objects.equals(a.getMaxAmount(), b.getMaxAmount())) {
            return false;
        }
        return sameGeography(a.getGeography(), b.getGeography());
    }

    private static boolean sameStringDim(String x, String y) {
        String nx = (x == null || x.isBlank()) ? null : x.trim();
        String ny = (y == null || y.isBlank()) ? null : y.trim();
        if (nx == null && ny == null) {
            return true;
        }
        if (nx == null || ny == null) {
            return false;
        }
        return nx.equalsIgnoreCase(ny);
    }

    private static boolean sameGeography(Map<String, Object> a, Map<String, Object> b) {
        boolean emptyA = a == null || a.isEmpty();
        boolean emptyB = b == null || b.isEmpty();
        if (emptyA && emptyB) {
            return true;
        }
        if (emptyA != emptyB) {
            return false;
        }
        return Objects.equals(a, b);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Mapping not found: " + id);
        }
        repository.deleteById(id);
    }

    private void apply(UserRoleMappingRequest r, UserRoleMapping m) {
        m.setUserId(r.getUserId());
        m.setLosRole(r.getRole().name());
        m.setLoanProduct(r.getLoanProduct() != null && !r.getLoanProduct().isBlank() ? r.getLoanProduct().trim() : null);
        m.setBorrowerType(
                r.getBorrowerType() != null && !r.getBorrowerType().isBlank() ? r.getBorrowerType().trim() : null);
        m.setMinAmount(r.getMinAmount());
        m.setMaxAmount(r.getMaxAmount());
        m.setGeography(r.getGeography());
        m.setPriority(r.getPriority());
        m.setActive(r.isActive());
    }

    private UserRoleMapping find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User-role mapping not found: " + id));
    }

    private UserRoleMappingResponse toResponse(UserRoleMapping m) {
        String userName = losUserRepository.findById(m.getUserId())
                .map(LosUser::getName)
                .orElse(null);
        return UserRoleMappingResponse.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .userName(userName)
                .role(m.getLosRole())
                .loanProduct(m.getLoanProduct())
                .borrowerType(m.getBorrowerType())
                .minAmount(m.getMinAmount())
                .maxAmount(m.getMaxAmount())
                .geography(m.getGeography())
                .priority(m.getPriority())
                .active(m.isActive())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
