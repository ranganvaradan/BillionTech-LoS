package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.loan.ApplicantIdentityResolver;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.plp.config.PlpProperties;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpAnchorSanctionHookService {

    private final PlpProperties plpProperties;
    private final LoanApplicationRepository loanApplicationRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final PlpAnchorSyncService plpAnchorSyncService;

    public void onAnchorCreditRatingCompleted(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null || app.getIntakeSegment() != IntakeSegment.ANCHOR) {
            return;
        }
        log.info("PLP anchor sync after credit rating completion for application: {}", applicationId);
        pushAnchorToPlp(applicationId);
    }

    /** @deprecated Anchor ID flow uses {@link #onAnchorCreditRatingCompleted}; kept for legacy CAM-reviewed rows. */
    public void onAnchorApplicationCamReviewed(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null || app.getIntakeSegment() != IntakeSegment.ANCHOR) {
            return;
        }
        log.info("PLP anchor sync after CAM approval for anchor application: {}", applicationId);
        pushAnchorToPlp(applicationId);
    }

    /** Refreshes anchor master from application after sanction (e.g. limit). Initial PLP push is on credit rating. */
    public void onAnchorApplicationSanctioned(UUID applicationId) {
        if (!plpProperties.isEnabled()) {
            return;
        }
        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null || app.getIntakeSegment() != IntakeSegment.ANCHOR) {
            return;
        }
        log.info("PLP anchor refresh after sanction for application: {}", applicationId);
        pushAnchorToPlp(applicationId);
    }

    private void pushAnchorToPlp(UUID applicationId) {
        if (!plpProperties.isEnabled()) {
            return;
        }

        LoanApplication app = loanApplicationRepository.findById(applicationId).orElse(null);
        if (app == null) {
            log.warn("PLP anchor sync skipped — application not found: {}", applicationId);
            return;
        }
        if (app.getIntakeSegment() != IntakeSegment.ANCHOR) {
            return;
        }
        if (!StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct())) {
            return;
        }

        AnchorMaster anchor = anchorMasterRepository.findBySourceAnchorApplicationId(applicationId)
                .orElseGet(() -> createAnchorFromApplication(app));

        refreshAnchorFromApplication(anchor, app);
        anchor = anchorMasterRepository.save(anchor);

        UUID anchorMasterId = anchor.getId();
        if (anchorMasterId == null) {
            log.warn("PLP anchor sync skipped — no anchor linked to application: {}", applicationId);
            return;
        }

        Runnable runSync = () -> {
            try {
                plpAnchorSyncService.sync(anchorMasterId);
            } catch (Exception e) {
                log.error("PLP anchor sync failed for application {}: {}",
                        applicationId, e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSync.run();
                }
            });
        } else {
            runSync.run();
        }
    }

    private AnchorMaster createAnchorFromApplication(LoanApplication app) {
        String name = ApplicationPartyResolver.resolveDisplayName(app);
        if (name.isBlank()) {
            name = "Anchor";
        }
        String code = app.getApplicationNumber();
        if (code == null || code.isBlank()) {
            code = "ANCHOR-" + app.getId().toString().substring(0, 8).toUpperCase();
        }
        return AnchorMaster.builder()
                .code(code)
                .name(name)
                .pan(nullIfBlank(ApplicantIdentityResolver.resolvePanNumber(app)))
                .gstin(nullIfBlank(stringValue(app.getBusinessInfo(), "gstin")))
                .email(nullIfBlank(ApplicationPartyResolver.resolveEmail(app)))
                .mobile(nullIfBlank(ApplicationPartyResolver.resolveMobile(app)))
                .address(nullIfBlank(buildAddress(app)))
                .sourceAnchorApplicationId(app.getId())
                .plpAnchorSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();
    }

    private void refreshAnchorFromApplication(AnchorMaster anchor, LoanApplication app) {
        String name = ApplicationPartyResolver.resolveDisplayName(app);
        if (!name.isBlank()) {
            anchor.setName(name);
        }
        String pan = ApplicantIdentityResolver.resolvePanNumber(app);
        if (!pan.isBlank()) {
            anchor.setPan(pan);
        }
        String gstin = stringValue(app.getBusinessInfo(), "gstin");
        if (!gstin.isBlank()) {
            anchor.setGstin(gstin.toUpperCase());
        }
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (!email.isBlank()) {
            anchor.setEmail(email);
        }
        String mobile = ApplicationPartyResolver.resolveMobile(app);
        if (!mobile.isBlank()) {
            anchor.setMobile(mobile);
        }
        String address = buildAddress(app);
        if (!address.isBlank()) {
            anchor.setAddress(address);
        }
    }

    private static String buildAddress(LoanApplication app) {
        String line = ApplicationPartyResolver.resolveAddressLine(app);
        if (line.isBlank()) {
            return "";
        }
        Map<String, Object> bi = app.getBusinessInfo();
        if (bi == null) {
            return line;
        }
        String city = stringValue(bi, "city");
        String state = stringValue(bi, "state");
        String pin = ApplicationPartyResolver.resolvePincode(app);
        StringBuilder sb = new StringBuilder(line);
        if (!city.isBlank()) {
            sb.append(", ").append(city);
        }
        if (!state.isBlank()) {
            sb.append(", ").append(state);
        }
        if (!pin.isBlank()) {
            sb.append(" ").append(pin);
        }
        return sb.toString();
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
