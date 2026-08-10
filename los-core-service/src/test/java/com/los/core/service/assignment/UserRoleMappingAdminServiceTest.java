package com.los.core.service.assignment;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.UserRoleMappingRequest;
import com.los.core.model.entity.LosUser;
import com.los.core.model.entity.UserRoleMapping;
import com.los.core.model.enums.AssignmentRole;
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.UserRoleMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleMappingAdminServiceTest {

    @Mock
    private UserRoleMappingRepository repository;
    @Mock
    private LosUserRepository losUserRepository;

    private UserRoleMappingAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserRoleMappingAdminService(repository, losUserRepository);
    }

    @Test
    void createRejectsDuplicateActiveScope() {
        UUID newUser = UUID.randomUUID();
        UUID existingUser = UUID.randomUUID();
        when(losUserRepository.findById(newUser))
                .thenReturn(Optional.of(LosUser.builder().id(newUser).name("A").active(true).build()));
        when(repository.findByActiveIsTrueAndLosRoleOrderByPriorityDesc("CREDIT_OFFICER"))
                .thenReturn(List.of(
                        UserRoleMapping.builder()
                                .id(UUID.randomUUID())
                                .userId(existingUser)
                                .losRole("CREDIT_OFFICER")
                                .loanProduct(null)
                                .borrowerType(null)
                                .active(true)
                                .priority(10)
                                .build()
                ));

        UserRoleMappingRequest r = new UserRoleMappingRequest();
        r.setUserId(newUser);
        r.setRole(AssignmentRole.CREDIT_OFFICER);
        r.setLoanProduct(null);
        r.setPriority(5);
        r.setActive(true);

        assertThrows(BusinessRuleException.class, () -> service.create(r));
        verify(repository, never()).save(any());
    }
}
