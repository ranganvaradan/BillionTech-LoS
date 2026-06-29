package com.los.core.controller;

import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.GoldValuationResult;
import com.los.core.model.entity.CollateralValuation;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.CollateralValuationRepository;
import com.los.core.service.collateral.CollateralValuationService;
import com.los.core.service.collateral.GoldValuationService;
import com.los.core.service.collateral.PropertyGeoTagService;
import com.los.core.service.integration.providers.IKycProvider;
import com.los.core.service.integration.providers.impl.PropertyEcProvider;
import com.los.core.service.integration.providers.impl.VahanRcProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BR-4.9: Collateral valuation API.
 */
@RestController
@RequestMapping("/api/v1/collateral")
@RequiredArgsConstructor
@Tag(name = "Collateral Valuation", description = "Collateral valuation management for secured lending")
public class CollateralController {

    private final CollateralValuationService valuationService;
    private final CollateralValuationRepository valuationRepository;
    private final VahanRcProvider vahanRcProvider;
    private final PropertyEcProvider propertyEcProvider;
    private final PropertyGeoTagService propertyGeoTagService;
    private final GoldValuationService goldValuationService;

    @PostMapping
    @Operation(summary = "Create a collateral valuation request")
    public ResponseEntity<CollateralValuation> createValuation(@RequestBody CollateralValuation valuation) {
        return ResponseEntity.status(HttpStatus.CREATED).body(valuationService.createValuation(valuation));
    }

    @PostMapping("/{valuationId}/complete")
    @Operation(summary = "Complete a collateral valuation with results")
    public ResponseEntity<CollateralValuation> completeValuation(
            @PathVariable UUID valuationId,
            @RequestParam BigDecimal marketValue,
            @RequestParam(required = false) BigDecimal forcedSaleValue,
            @RequestParam String valuerId,
            @RequestParam String valuerName) {
        return ResponseEntity.ok(valuationService.completeValuation(
                valuationId, marketValue, forcedSaleValue, valuerId, valuerName));
    }

    @PostMapping("/{valuationId}/rc-verify")
    @Operation(summary = "Verify vehicle RC via Vahan/Karza for a VEHICLE collateral valuation")
    public ResponseEntity<Map<String, Object>> verifyVehicleRc(
            @PathVariable UUID valuationId,
            @RequestParam(required = false) String rcNumber,
            @RequestBody(required = false) Map<String, Object> payload) {
        CollateralValuation valuation = requireValuation(valuationId);
        requireCollateralType(valuation, "VEHICLE");

        Map<String, Object> verifyPayload = buildVerifyPayload(valuation, payload);
        if (rcNumber != null && !rcNumber.isBlank()) {
            verifyPayload.put("rcNumber", rcNumber.trim());
            verifyPayload.put("registrationNumber", rcNumber.trim());
        }
        mergeDetailsIntoPayload(valuation, verifyPayload);

        IKycProvider.KycVerificationResult result = vahanRcProvider.verify(
                KycStepType.VEHICLE_RC_VERIFY, verifyPayload);

        return buildVerificationResponse(valuation, "rcVerification", result);
    }

    @PostMapping("/{valuationId}/ec-verify")
    @Operation(summary = "Verify property encumbrance certificate for a PROPERTY collateral valuation")
    public ResponseEntity<Map<String, Object>> verifyEncumbrance(
            @PathVariable UUID valuationId,
            @RequestBody(required = false) Map<String, String> propertyDetails) {
        CollateralValuation valuation = requireValuation(valuationId);
        requireCollateralType(valuation, "PROPERTY");

        Map<String, Object> verifyPayload = new LinkedHashMap<>();
        if (propertyDetails != null) {
            propertyDetails.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    verifyPayload.put(key, value);
                }
            });
        }
        mergeDetailsIntoPayload(valuation, verifyPayload);

        IKycProvider.KycVerificationResult result = propertyEcProvider.verify(
                KycStepType.PROPERTY_EC_VERIFY, verifyPayload);

        return buildVerificationResponse(valuation, "ecVerification", result);
    }

    @PostMapping("/{valuationId}/geo-tag")
    @Operation(summary = "Geo-tag a property collateral valuation using Google Maps geocoding")
    public ResponseEntity<Map<String, Object>> geoTagProperty(
            @PathVariable UUID valuationId,
            @RequestParam String address) {
        CollateralValuation valuation = requireValuation(valuationId);
        requireCollateralType(valuation, "PROPERTY");

        Map<String, Object> geoResult = propertyGeoTagService.geoTagProperty(address, valuationId);
        CollateralValuation updated = requireValuation(valuationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("valuation", updated);
        response.put("geo", geoResult);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{valuationId}/gold-value")
    @Operation(summary = "Calculate gold collateral value from weight and purity; auto-completes valuation")
    public ResponseEntity<Map<String, Object>> calculateGoldValue(
            @PathVariable UUID valuationId,
            @RequestParam BigDecimal weightGrams,
            @RequestParam BigDecimal purityPercent,
            @RequestParam(required = false) String articleDescription) {
        CollateralValuation valuation = requireValuation(valuationId);
        requireCollateralType(valuation, "GOLD");

        GoldValuationResult calculation = goldValuationService.calculateGoldValue(
                weightGrams, purityPercent, articleDescription);

        mergeGoldCalculationDetails(valuationId, calculation);

        CollateralValuation completed = valuationService.completeValuation(
                valuationId,
                calculation.marketValue(),
                calculation.acceptedValue(),
                "GOLD_CALC",
                "Live Gold Rate");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("calculation", calculation);
        response.put("valuation", completed);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get all valuations for an application")
    public ResponseEntity<List<CollateralValuation>> listByApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(valuationService.getValuationsByApplication(applicationId));
    }

    @GetMapping("/ltv/{applicationId}")
    @Operation(summary = "Calculate LTV ratio for an application")
    public ResponseEntity<Map<String, Object>> calculateLtv(
            @PathVariable UUID applicationId,
            @RequestParam BigDecimal loanAmount) {
        return ResponseEntity.ok(valuationService.calculateLtv(applicationId, loanAmount));
    }

    private CollateralValuation requireValuation(UUID valuationId) {
        return valuationRepository.findById(valuationId)
                .orElseThrow(() -> new ResourceNotFoundException("Valuation not found: " + valuationId));
    }

    private static void requireCollateralType(CollateralValuation valuation, String expectedType) {
        if (valuation.getCollateralType() == null
                || !expectedType.equalsIgnoreCase(valuation.getCollateralType())) {
            throw new IllegalArgumentException(
                    expectedType + " collateral required. Found: " + valuation.getCollateralType());
        }
    }

    private ResponseEntity<Map<String, Object>> buildVerificationResponse(
            CollateralValuation valuation,
            String detailsKey,
            IKycProvider.KycVerificationResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("confidenceScore", result.confidenceScore());
        response.put("transactionId", result.transactionId());

        if (result.parsedData() != null && !result.parsedData().isEmpty()) {
            persistDetails(valuation, detailsKey, result.parsedData());
            response.put("parsedData", result.parsedData());
        }

        CollateralValuation updated = requireValuation(valuation.getId());
        response.put("valuation", updated);

        if (!result.success()) {
            response.put("errorMessage", result.errorMessage());
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    private void mergeGoldCalculationDetails(UUID valuationId, GoldValuationResult calculation) {
        CollateralValuation valuation = requireValuation(valuationId);
        Map<String, Object> goldDetails = new LinkedHashMap<>();
        goldDetails.put("weightGrams", calculation.weightGrams());
        goldDetails.put("purityPercent", calculation.purityPercent());
        goldDetails.put("liveRatePerGram", calculation.liveRatePerGram());
        goldDetails.put("pureGoldWeight", calculation.pureGoldWeight());
        goldDetails.put("marketValue", calculation.marketValue());
        goldDetails.put("haircut", calculation.haircut());
        goldDetails.put("acceptedValue", calculation.acceptedValue());
        goldDetails.put("maxLtvPercent", calculation.maxLtvPercent());
        goldDetails.put("rateTimestamp", calculation.rateTimestamp() != null
                ? calculation.rateTimestamp().toString() : null);
        persistDetails(valuation, "goldValuation", goldDetails);
    }

    private void persistDetails(CollateralValuation valuation, String key, Map<String, Object> data) {
        Map<String, Object> details = valuation.getDetails() != null
                ? new HashMap<>(valuation.getDetails())
                : new HashMap<>();
        details.put(key, data);
        valuation.setDetails(details);
        valuationRepository.save(valuation);
    }

    private static Map<String, Object> buildVerifyPayload(
            CollateralValuation valuation,
            Map<String, Object> payload) {
        Map<String, Object> verifyPayload = new LinkedHashMap<>();
        if (payload != null) {
            verifyPayload.putAll(payload);
        }
        if (valuation.getDescription() != null && !valuation.getDescription().isBlank()) {
            verifyPayload.putIfAbsent("description", valuation.getDescription());
        }
        if (valuation.getAddress() != null && !valuation.getAddress().isBlank()) {
            verifyPayload.putIfAbsent("address", valuation.getAddress());
        }
        return verifyPayload;
    }

    @SuppressWarnings("unchecked")
    private static void mergeDetailsIntoPayload(CollateralValuation valuation, Map<String, Object> verifyPayload) {
        if (valuation.getDetails() == null || valuation.getDetails().isEmpty()) {
            return;
        }
        Map<String, Object> details = valuation.getDetails();
        copyAliasIfAbsent(verifyPayload, details,
                new String[]{"rcNumber", "registrationNumber"},
                new String[]{"rcNumber", "registrationNumber", "vehicleRegistrationNumber"});
        copyAliasIfAbsent(verifyPayload, details,
                new String[]{"propertyRegNumber"},
                new String[]{"propertyRegNumber", "registrationNumber"});
        copyScalarIfAbsent(verifyPayload, details, "district");
        copyScalarIfAbsent(verifyPayload, details, "state");
        copyScalarIfAbsent(verifyPayload, details, "surveyNumber");

        Object nested = details.get("intake");
        if (nested instanceof Map<?, ?> intakeMap) {
            Map<String, Object> intake = (Map<String, Object>) intakeMap;
            copyAliasIfAbsent(verifyPayload, intake,
                    new String[]{"rcNumber", "registrationNumber"},
                    new String[]{"rcNumber", "registrationNumber", "vehicleRegistrationNumber"});
            copyAliasIfAbsent(verifyPayload, intake,
                    new String[]{"propertyRegNumber"},
                    new String[]{"propertyRegNumber", "registrationNumber"});
            copyScalarIfAbsent(verifyPayload, intake, "district");
            copyScalarIfAbsent(verifyPayload, intake, "state");
            copyScalarIfAbsent(verifyPayload, intake, "surveyNumber");
        }
    }

    private static void copyScalarIfAbsent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (target.get(key) != null && !String.valueOf(target.get(key)).isBlank()) {
            return;
        }
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    private static void copyAliasIfAbsent(Map<String, Object> target, Map<String, Object> source,
                                          String[] targetKeys, String[] sourceKeys) {
        for (String targetKey : targetKeys) {
            if (target.get(targetKey) != null && !String.valueOf(target.get(targetKey)).isBlank()) {
                return;
            }
        }
        for (String sourceKey : sourceKeys) {
            Object value = source.get(sourceKey);
            if (value != null && !String.valueOf(value).isBlank()) {
                for (String targetKey : targetKeys) {
                    target.putIfAbsent(targetKey, value);
                }
                return;
            }
        }
    }
}
