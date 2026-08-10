package com.los.core.service.auth;

import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowerAccountProvisioningServiceTest {

    @Mock
    private LosUserRepository losUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private BorrowerAccountProvisioningService service;

    @Test
    void findOrCreateBorrower_createsNewUserWhenMobileBelongsToDifferentEmail() {
        LosUser existing = LosUser.builder()
                .id(java.util.UUID.randomUUID())
                .email("vignesht@bl.com")
                .mobile("9876598765")
                .active(true)
                .build();
        when(losUserRepository.findByEmailIgnoreCase("shivasales@bl.com")).thenReturn(Optional.empty());
        when(losUserRepository.findByMobileIsNotNull()).thenReturn(List.of(existing));
        when(passwordEncoder.encode("9876598765")).thenReturn("hash");
        when(losUserRepository.save(any(LosUser.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<LosUser> created = service.findOrCreateBorrower("Shiva Sales", "shivasales@bl.com", "9876598765");

        assertThat(created).isPresent();
        assertThat(created.get().getEmail()).isEqualTo("shivasales@bl.com");
        verify(losUserRepository).save(any(LosUser.class));
    }

    @Test
    void findOrCreateBorrower_reusesUserWhenEmailMatches() {
        LosUser existing = LosUser.builder()
                .id(java.util.UUID.randomUUID())
                .email("shivasales@bl.com")
                .mobile("9876598765")
                .active(true)
                .build();
        when(losUserRepository.findByEmailIgnoreCase("shivasales@bl.com")).thenReturn(Optional.of(existing));

        Optional<LosUser> result = service.findOrCreateBorrower("Shiva Sales", "shivasales@bl.com", "9876598765");

        assertThat(result).contains(existing);
        verify(losUserRepository, never()).save(any(LosUser.class));
    }
}
