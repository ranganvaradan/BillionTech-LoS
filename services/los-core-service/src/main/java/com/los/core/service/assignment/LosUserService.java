package com.los.core.service.assignment;

import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.config.LosAuthProperties;
import com.los.core.model.dto.request.LosUserRequest;
import com.los.core.model.dto.response.LosUserResponse;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LosUserService {

    private final LosUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final LosAuthProperties losAuthProperties;

    public List<LosUserResponse> list(String role) {
        if (role != null && !role.isBlank()) {
            return repository.findActiveUsersForRole(role.trim().toUpperCase()).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }
        return repository.findAll(Sort.by("name").ascending()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public LosUserResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public LosUserResponse create(LosUserRequest r) {
        if (repository.findByEmailIgnoreCase(r.getEmail().trim()).isPresent()) {
            throw new BusinessRuleException(
                    "A user with this email already exists", "EMAIL_DUPLICATE", "CHOOSE_EMAIL", null);
        }
        LosUser e = new LosUser();
        copy(r, e);
        e.setPasswordHash(passwordEncoder.encode(losAuthProperties.getDefaultTemporaryPassword()));
        e.setPasswordResetRequired(true);
        return toResponse(repository.save(e));
    }

    @Transactional
    public LosUserResponse update(UUID id, LosUserRequest r) {
        LosUser e = find(id);
        Optional<LosUser> other = repository.findByEmailIgnoreCase(r.getEmail().trim());
        if (other.isPresent() && !other.get().getId().equals(id)) {
            throw new BusinessRuleException(
                    "Another user has this email", "EMAIL_DUPLICATE", "CHOOSE_EMAIL", null);
        }
        copy(r, e);
        return toResponse(repository.save(e));
    }

    @Transactional
    public void deactivate(UUID id) {
        LosUser e = find(id);
        e.setActive(false);
        repository.save(e);
    }

    private void copy(LosUserRequest r, LosUser e) {
        e.setName(r.getName().trim());
        e.setEmail(r.getEmail().trim().toLowerCase());
        e.setMobile(r.getMobile() != null ? r.getMobile().trim() : null);
        e.setActive(r.isActive());
    }

    private LosUser find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private LosUserResponse toResponse(LosUser e) {
        return LosUserResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .email(e.getEmail())
                .mobile(e.getMobile())
                .active(e.isActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
