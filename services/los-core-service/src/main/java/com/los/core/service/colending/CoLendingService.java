package com.los.core.service.colending;

import com.los.core.model.entity.CoLendingAllocation;
import com.los.core.model.entity.CoLendingPartner;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.CoLendingAllocationRepository;
import com.los.core.repository.CoLendingPartnerRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BR-17: Co-Lending Module — partner management, apportionment, disbursement orchestration,
 * settlement/reconciliation, and co-lending MIS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoLendingService {

    private final CoLendingPartnerRepository partnerRepository;
    private final CoLendingAllocationRepository allocationRepository;
    private final LoanApplicationRepository applicationRepository;
    private final AuditService auditService;

    // BR-17.1: Partner management

    public CoLendingPartner createPartner(CoLendingPartner partner) {
        partner = partnerRepository.save(partner);
        log.info("Co-lending partner created: {} ({})", partner.getPartnerName(), partner.getPartnerCode());
        return partner;
    }

    public List<CoLendingPartner> listActivePartners() {
        return partnerRepository.findByActiveTrue();
    }

    public CoLendingPartner updatePartner(UUID partnerId, CoLendingPartner updated) {
        CoLendingPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Partner not found: " + partnerId));
        partner.setPartnerName(updated.getPartnerName());
        partner.setDefaultApportionmentPercent(updated.getDefaultApportionmentPercent());
        partner.setMaxExposureLimit(updated.getMaxExposureLimit());
        partner.setInterestRateSpread(updated.getInterestRateSpread());
        partner.setApiEndpoint(updated.getApiEndpoint());
        partner.setContactEmail(updated.getContactEmail());
        partner.setContactPhone(updated.getContactPhone());
        partner.setActive(updated.isActive());
        partner.setConfig(updated.getConfig());
        partner.setUpdatedAt(Instant.now());
        return partnerRepository.save(partner);
    }

    // BR-17.2: Automatic apportionment calculation

    @Transactional
    public List<CoLendingAllocation> calculateApportionment(UUID applicationId, UUID partnerId,
                                                             BigDecimal partnerSharePercent) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
        CoLendingPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Partner not found: " + partnerId));

        BigDecimal totalAmount = app.getRequestedAmount();
        BigDecimal partnerAmount = totalAmount.multiply(partnerSharePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal lenderAmount = totalAmount.subtract(partnerAmount);

        List<CoLendingAllocation> allocations = new ArrayList<>();

        // Partner allocation
        CoLendingAllocation partnerAlloc = CoLendingAllocation.builder()
                .applicationId(applicationId)
                .partnerId(partnerId)
                .partnerName(partner.getPartnerName())
                .sharePercent(partnerSharePercent)
                .shareAmount(partnerAmount)
                .partnerInterestRate(partner.getInterestRateSpread())
                .status("PENDING")
                .build();
        allocations.add(allocationRepository.save(partnerAlloc));

        // Lender (self) allocation
        CoLendingAllocation selfAlloc = CoLendingAllocation.builder()
                .applicationId(applicationId)
                .partnerId(null)
                .partnerName("SELF")
                .sharePercent(BigDecimal.valueOf(100).subtract(partnerSharePercent))
                .shareAmount(lenderAmount)
                .partnerInterestRate(app.getInterestRate())
                .status("PENDING")
                .build();
        allocations.add(allocationRepository.save(selfAlloc));

        auditService.logEvent(applicationId, "CO_LENDING_APPORTIONED",
                Map.of("partnerId", partnerId.toString(), "partnerShare", partnerSharePercent,
                       "partnerAmount", partnerAmount, "lenderAmount", lenderAmount));

        log.info("Co-lending apportionment for {}: partner {}% ({}), self {}% ({})",
                applicationId, partnerSharePercent, partnerAmount,
                BigDecimal.valueOf(100).subtract(partnerSharePercent), lenderAmount);

        return allocations;
    }

    // BR-17.3: Co-lender disbursement orchestration

    @Transactional
    public Map<String, Object> triggerCoLendingDisbursement(UUID applicationId) {
        List<CoLendingAllocation> allocations = allocationRepository.findByApplicationId(applicationId);
        if (allocations.isEmpty()) {
            throw new RuntimeException("No co-lending allocations found for: " + applicationId);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (CoLendingAllocation alloc : allocations) {
            alloc.setStatus("DISBURSED");
            alloc.setUpdatedAt(Instant.now());
            alloc.setPartnerReferenceId("COLEND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            allocationRepository.save(alloc);

            results.add(Map.of(
                    "partner", alloc.getPartnerName(),
                    "amount", alloc.getShareAmount(),
                    "status", "DISBURSED",
                    "referenceId", alloc.getPartnerReferenceId()
            ));
        }

        auditService.logEvent(applicationId, "CO_LENDING_DISBURSED",
                Map.of("allocations", results.size()));

        return Map.of("applicationId", applicationId.toString(), "disbursements", results);
    }

    // BR-17.4: Settlement/reconciliation

    public Map<String, Object> getSettlementReport(UUID partnerId) {
        List<CoLendingAllocation> allocations = allocationRepository.findByPartnerId(partnerId);

        BigDecimal totalDisbursed = allocations.stream()
                .filter(a -> "DISBURSED".equals(a.getStatus()))
                .map(CoLendingAllocation::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSettled = allocations.stream()
                .filter(a -> "SETTLED".equals(a.getStatus()))
                .map(CoLendingAllocation::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalLoans = allocations.size();
        long activeLoans = allocations.stream().filter(a -> "DISBURSED".equals(a.getStatus())).count();

        return Map.of(
                "partnerId", partnerId.toString(),
                "totalLoans", totalLoans,
                "activeLoans", activeLoans,
                "totalDisbursed", totalDisbursed,
                "totalSettled", totalSettled,
                "pendingSettlement", totalDisbursed.subtract(totalSettled)
        );
    }

    // BR-17.5: Co-lending MIS

    public Map<String, Object> getCoLendingMis() {
        List<CoLendingPartner> partners = partnerRepository.findByActiveTrue();
        List<CoLendingAllocation> allAllocations = allocationRepository.findAll();

        BigDecimal totalCoLentAmount = allAllocations.stream()
                .filter(a -> !"SELF".equals(a.getPartnerName()))
                .map(CoLendingAllocation::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> partnerBreakdown = new LinkedHashMap<>();
        for (CoLendingPartner partner : partners) {
            List<CoLendingAllocation> partnerAllocs = allAllocations.stream()
                    .filter(a -> partner.getId().equals(a.getPartnerId()))
                    .collect(Collectors.toList());
            BigDecimal partnerTotal = partnerAllocs.stream()
                    .map(CoLendingAllocation::getShareAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            partnerBreakdown.put(partner.getPartnerName(), Map.of(
                    "totalLoans", partnerAllocs.size(),
                    "totalAmount", partnerTotal,
                    "averageShare", partnerAllocs.isEmpty() ? BigDecimal.ZERO :
                            partnerAllocs.stream().map(CoLendingAllocation::getSharePercent)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .divide(BigDecimal.valueOf(partnerAllocs.size()), 2, RoundingMode.HALF_UP)
            ));
        }

        return Map.of(
                "totalPartners", partners.size(),
                "totalCoLentAmount", totalCoLentAmount,
                "totalAllocations", allAllocations.size(),
                "partnerBreakdown", partnerBreakdown
        );
    }

    public List<CoLendingAllocation> getAllocationsByApplication(UUID applicationId) {
        return allocationRepository.findByApplicationId(applicationId);
    }
}
