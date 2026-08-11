package com.los.core.service.underwriting;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.dto.request.UnderwritingScorecardRequest;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.UnderwritingScorecardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScorecardActiveImmutabilityTest {

    @Mock
    UnderwritingScorecardRepository repository;
    @Mock
    AdminConfigAuditSupport audit;

    @InjectMocks
    UnderwritingScorecardAdminService admin;

    @Test
    void activeCannotChangeBandsInPlace() {
        UUID id = UUID.randomUUID();
        UnderwritingScorecard active = UnderwritingScorecard.builder()
                .id(id)
                .name("Live")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .version(1)
                .priority(100)
                .active(true)
                .status("ACTIVE")
                .lineageId(id)
                .scorecardJson(Map.of("rows", List.of(
                        Map.of("id", "d1", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                                "condition", "GTE:750", "weight", 1, "score", 35))))
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 50))
                .hardRulesJson(Map.of("rules", List.of()))
                .safetyJson(Map.of())
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(active));

        UnderwritingScorecardRequest req = new UnderwritingScorecardRequest();
        req.setName("Live");
        req.setBorrowerType(BorrowerType.COMPANY);
        req.setLoanProduct("TERM_LOAN");
        req.setVersion(1);
        req.setPriority(100);
        req.setActive(true);
        req.setScorecardJson(Map.of("rows", List.of(
                Map.of("id", "d1", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                        "condition", "GTE:800", "weight", 1, "score", 35))));
        req.setThresholdsJson(active.getThresholdsJson());
        req.setHardRulesJson(active.getHardRulesJson());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> admin.update(id, req));
        assertEquals("SCORECARD_ACTIVE_IMMUTABLE", ex.getReason());
    }

    @Test
    void createNewVersionClonesDraftIndependently() {
        UUID id = UUID.randomUUID();
        UnderwritingScorecard active = UnderwritingScorecard.builder()
                .id(id)
                .name("Live")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .version(1)
                .priority(100)
                .active(true)
                .status("ACTIVE")
                .lineageId(id)
                .minAmount(new BigDecimal("50000"))
                .scorecardJson(Map.of("rows", List.of(
                        Map.of("id", "d1", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                                "condition", "GTE:750", "weight", 1, "score", 35))))
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 50))
                .hardRulesJson(Map.of("rules", List.of()))
                .safetyJson(Map.of())
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(active));
        when(repository.findAll()).thenReturn(List.of(active));
        when(repository.save(any())).thenAnswer(inv -> {
            UnderwritingScorecard s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        var draft = admin.createNewVersion(id);
        assertEquals(2, draft.getVersion());
        assertEquals("DRAFT", draft.getStatus());
        assertFalse(draft.isActive());
        assertEquals(id, draft.getParentScorecardId());
        assertEquals(id, draft.getLineageId());

        ArgumentCaptor<UnderwritingScorecard> cap = ArgumentCaptor.forClass(UnderwritingScorecard.class);
        verify(repository).save(cap.capture());
        assertTrue(active.isActive());
        assertEquals(1, active.getVersion());
    }
}
