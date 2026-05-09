package com.los.core.service.collateral;

import com.los.core.model.entity.CollateralValuation;
import com.los.core.repository.CollateralValuationRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * BR-4.9: Collateral valuation module.
 * Manages property/asset valuations for secured lending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollateralValuationService {

    private final CollateralValuationRepository valuationRepository;
    private final AuditService auditService;

    private static final BigDecimal DEFAULT_HAIRCUT = new BigDecimal("0.20"); // 20% haircut

    public CollateralValuation createValuation(CollateralValuation valuation) {
        valuation.setStatus("PENDING");
        valuation = valuationRepository.save(valuation);
        log.info("Collateral valuation created for application: {} type: {}",
                valuation.getApplicationId(), valuation.getCollateralType());
        return valuation;
    }

    public CollateralValuation completeValuation(UUID valuationId, BigDecimal marketValue,
                                                  BigDecimal forcedSaleValue, String valuerId,
                                                  String valuerName) {
        CollateralValuation valuation = valuationRepository.findById(valuationId)
                .orElseThrow(() -> new RuntimeException("Valuation not found: " + valuationId));

        valuation.setMarketValue(marketValue);
        valuation.setForcedSaleValue(forcedSaleValue);
        valuation.setValuationAmount(forcedSaleValue != null ? forcedSaleValue :
                marketValue.multiply(BigDecimal.ONE.subtract(DEFAULT_HAIRCUT)).setScale(2, RoundingMode.HALF_UP));
        valuation.setValuerId(valuerId);
        valuation.setValuerName(valuerName);
        valuation.setValuationDate(Instant.now());
        valuation.setValuationExpiry(Instant.now().plus(180, ChronoUnit.DAYS)); // 6-month validity
        valuation.setStatus("COMPLETED");

        valuation = valuationRepository.save(valuation);

        auditService.logEvent(valuation.getApplicationId(), "COLLATERAL_VALUED",
                Map.of("valuationId", valuationId.toString(), "marketValue", marketValue,
                       "forcedSaleValue", forcedSaleValue != null ? forcedSaleValue : "N/A",
                       "valuationAmount", valuation.getValuationAmount()));

        log.info("Collateral valuation completed: {} market={} fsv={} accepted={}",
                valuationId, marketValue, forcedSaleValue, valuation.getValuationAmount());

        return valuation;
    }

    public List<CollateralValuation> getValuationsByApplication(UUID applicationId) {
        return valuationRepository.findByApplicationId(applicationId);
    }

    /**
     * Calculate LTV ratio for an application based on total collateral value.
     */
    public Map<String, Object> calculateLtv(UUID applicationId, BigDecimal loanAmount) {
        List<CollateralValuation> valuations = valuationRepository.findByApplicationId(applicationId);

        BigDecimal totalCollateralValue = valuations.stream()
                .filter(v -> "COMPLETED".equals(v.getStatus()))
                .map(CollateralValuation::getValuationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ltvRatio = totalCollateralValue.compareTo(BigDecimal.ZERO) > 0
                ? loanAmount.divide(totalCollateralValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        boolean ltvAcceptable = ltvRatio.compareTo(new BigDecimal("80")) <= 0; // Max 80% LTV

        return Map.of(
                "applicationId", applicationId.toString(),
                "loanAmount", loanAmount,
                "totalCollateralValue", totalCollateralValue,
                "ltvRatio", ltvRatio,
                "ltvAcceptable", ltvAcceptable,
                "maxAllowedLtv", 80,
                "valuationCount", valuations.size()
        );
    }
}
