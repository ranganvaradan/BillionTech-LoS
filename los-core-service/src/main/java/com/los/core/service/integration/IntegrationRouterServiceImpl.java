package com.los.core.service.integration;

import com.los.core.model.entity.AggregatorConfig;
import com.los.core.model.entity.schema.los2.EsignRequest;
import com.los.core.model.enums.IntegrationCategory;
import com.los.core.model.enums.KycStepType;
import com.los.core.repository.AggregatorConfigRepository;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.providers.IBureauProvider;
import com.los.core.service.integration.providers.IESignProvider;
import com.los.core.service.integration.providers.IKycProvider;
import com.los.core.service.integration.webhook.IntegrationCallbackProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class IntegrationRouterServiceImpl implements IIntegrationRouterService {

    private final Map<String, IKycProvider> kycProvidersByName;
    private final Map<String, IBureauProvider> bureauProvidersByName;
    private final Map<String, IESignProvider> eSignProvidersByName;
    private final AggregatorConfigRepository aggregatorConfigRepository;
    private final IntegrationCallbackProcessor integrationCallbackProcessor;
    private final EsignRequestTrackingService esignRequestTrackingService;

    public IntegrationRouterServiceImpl(
            @Qualifier("kycProvidersByName") Map<String, IKycProvider> kycProvidersByName,
            @Qualifier("bureauProvidersByName") Map<String, IBureauProvider> bureauProvidersByName,
            @Qualifier("eSignProvidersByName") Map<String, IESignProvider> eSignProvidersByName,
            AggregatorConfigRepository aggregatorConfigRepository,
            IntegrationCallbackProcessor integrationCallbackProcessor,
            EsignRequestTrackingService esignRequestTrackingService) {
        this.kycProvidersByName = kycProvidersByName;
        this.bureauProvidersByName = bureauProvidersByName;
        this.eSignProvidersByName = eSignProvidersByName;
        this.aggregatorConfigRepository = aggregatorConfigRepository;
        this.integrationCallbackProcessor = integrationCallbackProcessor;
        this.esignRequestTrackingService = esignRequestTrackingService;
    }

    @Override
    public KycRouteResult routeKycRequest(
            UUID applicationId,
            KycStepType stepType,
            Map<String, Object> payload,
            String preferredProviderName) {
        Map<String, Object> effective = new HashMap<>();
        if (payload != null) {
            effective.putAll(payload);
        }
        if (applicationId != null) {
            effective.putIfAbsent("applicationId", applicationId.toString());
        }

        Set<String> alreadyTried = new LinkedHashSet<>();
        List<AggregatorConfig> order = aggregatorConfigRepository.findActiveKycRoutings(
                IntegrationCategory.KYC, stepType.name());
        KycRouteResult lastFailure = null;

        String normPref = normalizeOptional(preferredProviderName);
        if (normPref != null) {
            KycRouteResult run = tryWorkflowPreferredKyc(
                    applicationId, stepType, effective, order, normPref, alreadyTried);
            if (run != null) {
                if (run.success()) {
                    return run;
                }
                lastFailure = run;
            }
        }

        if (order.isEmpty()) {
            return routeKycLegacy(stepType, effective, alreadyTried, lastFailure);
        }

        for (AggregatorConfig rule : order) {
            String key = normalizeKey(rule.getProviderName());
            if (alreadyTried.contains(key)) {
                log.debug("Skipping {} in aggregator chain (already attempted)", key);
                continue;
            }
            IKycProvider provider = kycProvidersByName.get(key);
            if (provider == null) {
                log.warn("KYC routing references unknown provider bean: {} — skipping", rule.getProviderName());
                KycRouteResult err = new KycRouteResult(false, null, null, "No registered provider: " + rule.getProviderName());
                lastFailure = err;
                if (!rule.isAllowFallback()) {
                    return err;
                }
                continue;
            }
            if (!provider.supports(stepType)) {
                log.debug("Provider {} does not support step {} — try next in chain", key, stepType);
                continue;
            }

            log.info("KYC step {} -> provider {} (appId={})", stepType, provider.getProviderName(), applicationId);
            alreadyTried.add(key);
            IKycProvider.KycVerificationResult result = provider.verify(stepType, effective);
            KycRouteResult route = toKycRoute(provider.getProviderName(), result);
            if (result.success()) {
                return route;
            }
            lastFailure = route;
            if (!rule.isAllowFallback()) {
                return lastFailure;
            }
        }
        if (lastFailure != null) {
            return lastFailure;
        }
        return new KycRouteResult(false, null, null, "No KYC provider could handle step: " + stepType);
    }

    /**
     * @return a failed attempt to record as lastFailure, null if preference skipped or not applicable
     */
    private KycRouteResult tryWorkflowPreferredKyc(
            UUID applicationId,
            KycStepType stepType,
            Map<String, Object> effective,
            List<AggregatorConfig> order,
            String normPref,
            Set<String> alreadyTried) {
        IKycProvider p = kycProvidersByName.get(normPref);
        if (p == null) {
            log.debug("Workflow preferred KYC provider {} is not registered — ignoring", normPref);
            return null;
        }
        if (!p.supports(stepType)) {
            log.debug("Workflow preferred provider {} does not support step {} — ignoring", normPref, stepType);
            return null;
        }
        if (!order.isEmpty() && order.stream()
                .noneMatch(r -> normPref.equalsIgnoreCase(normalizeKey(r.getProviderName())))) {
            log.warn("Workflow preferred provider {} is not in active KYC routing for step {} — ignoring preference",
                    normPref, stepType);
            return null;
        }
        if (alreadyTried.contains(normPref)) {
            return null;
        }
        log.info("KYC workflow preference {} for step {} (appId={}) — first attempt", normPref, stepType, applicationId);
        alreadyTried.add(normPref);
        IKycProvider.KycVerificationResult result = p.verify(stepType, effective);
        return toKycRoute(p.getProviderName(), result);
    }

    private KycRouteResult routeKycLegacy(
            KycStepType stepType,
            Map<String, Object> effective,
            Set<String> alreadyTried,
            KycRouteResult previousFailure) {
        Optional<IKycProvider> best = kycProvidersByName.values().stream()
                .sorted(Comparator.comparingInt(IKycProvider::getPriority).reversed())
                .filter(p -> p.supports(stepType))
                .filter(p -> !alreadyTried.contains(normalizeKey(p.getProviderName())))
                .findFirst();
        if (best.isEmpty()) {
            if (previousFailure != null) {
                return previousFailure;
            }
            return new KycRouteResult(false, null, null, "No provider found for step: " + stepType);
        }
        IKycProvider provider = best.get();
        log.info("KYC (legacy) step {} -> provider {}", stepType, provider.getProviderName());
        IKycProvider.KycVerificationResult result = provider.verify(stepType, effective);
        return toKycRoute(provider.getProviderName(), result);
    }

    private static KycRouteResult toKycRoute(String name, IKycProvider.KycVerificationResult result) {
        return new KycRouteResult(
                result.success(),
                name,
                Map.of(
                        "confidenceScore", result.confidenceScore(),
                        "parsedData", result.parsedData() != null ? result.parsedData() : Map.of(),
                        "transactionId", result.transactionId() != null ? result.transactionId() : ""
                ),
                result.errorMessage()
        );
    }

    @Override
    public BureauRouteResult routeBureauRequest(UUID applicationId, Map<String, Object> payload) {
        Map<String, Object> effective = new HashMap<>();
        if (payload != null) {
            effective.putAll(payload);
        }
        if (applicationId != null) {
            effective.putIfAbsent("applicationId", applicationId.toString());
        }

        List<AggregatorConfig> order = aggregatorConfigRepository.findActiveByType(IntegrationCategory.BUREAU);
        if (order.isEmpty()) {
            Optional<IBureauProvider> p = pickSingleBureauFromBeans();
            if (p.isEmpty()) {
                return new BureauRouteResult(false, 0, null, null, "No bureau provider registered");
            }
            return invokeBureau(p.get(), effective);
        }

        BureauRouteResult lastFailure = null;
        for (AggregatorConfig rule : order) {
            String key = normalizeKey(rule.getProviderName());
            IBureauProvider provider = bureauProvidersByName.get(key);
            if (provider == null) {
                lastFailure = new BureauRouteResult(false, 0, null, null, "No registered bureau: " + rule.getProviderName());
                if (!rule.isAllowFallback()) {
                    return lastFailure;
                }
                continue;
            }
            BureauRouteResult r = invokeBureau(provider, effective);
            if (r.success()) {
                return r;
            }
            lastFailure = r;
            if (!rule.isAllowFallback()) {
                return r;
            }
        }
        return lastFailure != null
                ? lastFailure
                : new BureauRouteResult(false, 0, null, null, "Bureau pull failed: no active routing");
    }

    private Optional<IBureauProvider> pickSingleBureauFromBeans() {
        IBureauProvider equifax = bureauProvidersByName.get("EQUIFAX");
        if (equifax != null) {
            return Optional.of(equifax);
        }
        return bureauProvidersByName.values().stream().findFirst();
    }

    private BureauRouteResult invokeBureau(IBureauProvider provider, Map<String, Object> effective) {
        log.info("Bureau -> provider {} (appId={})", provider.getProviderName(), effective.get("applicationId"));
        IBureauProvider.BureauPullResult result = provider.pullReport(effective);
        return new BureauRouteResult(
                result.success(),
                result.creditScore(),
                result.reportData(),
                result.transactionId(),
                result.errorMessage()
        );
    }

    @Override
    public ESignRouteResult routeESignRequest(UUID applicationId, Map<String, Object> payload) {
        if (payload == null) {
            return new ESignRouteResult(false, null, null, "Missing payload for eSign", null);
        }
        Object doc = payload.get("documentKey");
        if (doc == null) {
            doc = payload.get("documentStorageKey");
        }
        String documentKey = doc != null ? doc.toString() : "KFS_AGREEMENT";
        @SuppressWarnings("unchecked")
        Map<String, Object> signerInfo = (Map<String, Object>) payload.getOrDefault("signerInfo", Map.of());
        Object returnUrlRaw = payload.get("returnUrl");
        String returnUrl = returnUrlRaw != null && !String.valueOf(returnUrlRaw).isBlank()
                ? String.valueOf(returnUrlRaw).trim() : null;

        Object rawEsignStep = payload.get("esignStepType");
        if (rawEsignStep == null) {
            rawEsignStep = payload.get("esignStep");
        }
        String esignStep = rawEsignStep != null && !String.valueOf(rawEsignStep).isBlank()
                ? String.valueOf(rawEsignStep).trim() : "ESIGN_AGREEMENT";

        boolean forceRegenerate = isTruthy(signerInfo.get("regenerateSigningUrl"))
                || isTruthy(signerInfo.get("regenerateUrl"))
                || isTruthy(signerInfo.get("forceNewEsignUrl"))
                || isTruthy(payload.get("regenerateSigningUrl"))
                || isTruthy(payload.get("regenerateUrl"))
                || isTruthy(payload.get("forceNewEsignUrl"));
        if (forceRegenerate) {
            log.info("[ESIGN_REGENERATE] New URL forced by request flag (appId={} documentKey={})", applicationId, documentKey);
        }
        Map<String, Object> signerForProvider = copySignerInfoForProvider(signerInfo);

        List<AggregatorConfig> order = aggregatorConfigRepository
                .findActiveEsignRoutings(IntegrationCategory.ESIGN, esignStep);
        if (order.isEmpty()) {
            order = aggregatorConfigRepository.findActiveByType(IntegrationCategory.ESIGN);
        }
        if (order.isEmpty()) {
            IESignProvider p = eSignProvidersByName.values().stream()
                    .findFirst()
                    .orElse(null);
            if (p == null) {
                return new ESignRouteResult(false, null, null, "No eSign provider registered", null);
            }
            return invokeEsign(p, applicationId, documentKey, signerForProvider, returnUrl);
        }

        if (!forceRegenerate && applicationId != null) {
            AggregatorConfig primary = pickFirstApplicableEsignRule(order);
            if (primary != null) {
                String firstKey = normalizeKey(primary.getProviderName());
                IESignProvider firstProviderBean = eSignProvidersByName.get(firstKey);
                if (firstProviderBean != null) {
                    String normEmail = EsignRequestTrackingService.normalizeSignerEmailForTracking(signerInfo);
                    if (normEmail != null) {
                        Optional<EsignRequest> reuse = esignRequestTrackingService.findReusableSigningSession(
                                applicationId, documentKey, firstKey, normEmail, esignStep);
                        if (reuse.isPresent()) {
                            EsignRequest row = reuse.get();
                            Map<String, Object> meta = new LinkedHashMap<>();
                            meta.put("reusedSigningUrl", true);
                            meta.put("reusedEsignRequestId", row.getId() != null ? row.getId().toString() : null);
                            log.info("[ESIGN_REUSE] returning stored signing URL appId={} documentKey={} provider={} workflowId={} esignStep={}",
                                    applicationId, documentKey, row.getProvider(), row.getProviderRequestId(), esignStep);
                            return new ESignRouteResult(
                                    true,
                                    row.getProviderRequestId(),
                                    row.getSigningUrl(),
                                    null,
                                    row.getProvider(),
                                    meta);
                        }
                        log.debug("[ESIGN_REUSE] no reusable row appId={} documentKey={} provider={}", applicationId, documentKey, firstKey);
                    } else {
                        log.debug("[ESIGN_REUSE] skipped — signer email absent (appId={})", applicationId);
                    }
                }
            }
        }

        ESignRouteResult lastFailure = null;
        for (AggregatorConfig rule : order) {
            String key = normalizeKey(rule.getProviderName());
            IESignProvider provider = eSignProvidersByName.get(key);
            if (provider == null) {
                String rn = rule.getProviderName();
                lastFailure = new ESignRouteResult(false, null, null, "No registered eSign: " + rn, rn);
                if (!rule.isAllowFallback()) {
                    return lastFailure;
                }
                continue;
            }
            ESignRouteResult r = invokeEsign(provider, applicationId, documentKey, signerForProvider, returnUrl);
            if (r.success()) {
                return r;
            }
            lastFailure = r;
            if (rule.isAllowFallback()) {
                log.warn("[Emsigner][FALLBACK] Switching to next eSign provider because {} failed: {}",
                        provider.getProviderName(), r.errorMessage());
            }
            if (!rule.isAllowFallback()) {
                return r;
            }
        }
        return lastFailure != null
                ? lastFailure
                : new ESignRouteResult(false, null, null, "eSign failed: no routing", null);
    }

    private ESignRouteResult invokeEsign(
            IESignProvider provider,
            UUID applicationId,
            String documentKey,
            Map<String, Object> signerInfo,
            String returnUrl) {
        log.info("eSign -> provider {} (appId={})", provider.getProviderName(), applicationId);
        IESignProvider.ESignInitResult result = provider.initiateSigningRequest(
                new IESignProvider.ESignInitRequest(applicationId, documentKey, signerInfo, returnUrl));
        return new ESignRouteResult(
                result.success(),
                result.transactionId(),
                result.signingUrl(),
                result.errorMessage(),
                provider.getProviderName(),
                result.providerMetadata()
        );
    }

    @Override
    public Map<String, Object> handleWebhookCallback(String providerName, Map<String, Object> payload) {
        String n = normalizeKey(providerName);
        if (eSignProvidersByName.containsKey(n)) {
            return integrationCallbackProcessor.handleEsignLikeCallback(n, payload != null ? payload : Map.of());
        }
        if (kycProvidersByName.containsKey(n)) {
            return Map.of(
                    "received", true,
                    "provider", n,
                    "handled", false,
                    "message", "KYC webhooks (e.g. video VKYC) use /api/v1/vkyc/webhook/...; send provider-specific callbacks to those paths"
            );
        }
        if (bureauProvidersByName.containsKey(n)) {
            return Map.of("received", true, "provider", n, "handled", false, "message", "Bureau callbacks not centralized yet");
        }
        return Map.of(
                "received", true,
                "provider", n,
                "handled", false,
                "message", "Unknown provider for webhook: " + n
        );
    }

    @Override
    public Map<String, Object> testConnectivity(String providerName) {
        String n = normalizeKey(providerName);
        if (eSignProvidersByName.containsKey(n) || kycProvidersByName.containsKey(n) || bureauProvidersByName.containsKey(n)) {
            return Map.of(
                    "provider", n,
                    "status", "REACHABLE",
                    "latencyMs", 45,
                    "timestamp", java.time.Instant.now().toString()
            );
        }
        return Map.of(
                "provider", n,
                "status", "UNKNOWN",
                "message", "No provider bean registered for name: " + n
        );
    }

    private AggregatorConfig pickFirstApplicableEsignRule(List<AggregatorConfig> order) {
        if (order == null) {
            return null;
        }
        for (AggregatorConfig rule : order) {
            String key = normalizeKey(rule.getProviderName());
            if (eSignProvidersByName.containsKey(key)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean isTruthy(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        String s = o.toString().trim();
        return "true".equalsIgnoreCase(s)
                || "1".equals(s)
                || "yes".equalsIgnoreCase(s);
    }

    private static Map<String, Object> copySignerInfoForProvider(Map<String, Object> signerInfo) {
        if (signerInfo == null || signerInfo.isEmpty()) {
            return signerInfo != null ? new HashMap<>(signerInfo) : Map.of();
        }
        Map<String, Object> m = new HashMap<>(signerInfo);
        m.remove("regenerateSigningUrl");
        m.remove("regenerateUrl");
        m.remove("forceNewEsignUrl");
        return m;
    }

    private static String normalizeKey(String name) {
        return name == null ? "UNKNOWN" : name.trim().toUpperCase();
    }

    /** Trims; empty → null; else uppercased for map lookup. */
    private static String normalizeOptional(String name) {
        if (name == null) {
            return null;
        }
        String t = name.trim();
        return t.isEmpty() ? null : t.toUpperCase();
    }
}
