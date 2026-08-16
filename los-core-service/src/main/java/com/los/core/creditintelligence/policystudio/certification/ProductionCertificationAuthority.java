package com.los.core.creditintelligence.policystudio.certification;

/**
 * Process-wide optional install of {@link ProductionCertificationService}
 * (mirrors ExecutionCapabilityAuthority). Does not alter CPES capability semantics.
 */
public final class ProductionCertificationAuthority {

    private static volatile ProductionCertificationService installed;

    private ProductionCertificationAuthority() {}

    public static void install(ProductionCertificationService service) {
        installed = service;
    }

    public static void clear() {
        installed = null;
    }

    public static boolean isInstalled() {
        return installed != null;
    }

    public static ProductionCertificationService get() {
        return installed;
    }
}
