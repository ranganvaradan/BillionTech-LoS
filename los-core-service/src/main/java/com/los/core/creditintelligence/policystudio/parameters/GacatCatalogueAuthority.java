package com.los.core.creditintelligence.policystudio.parameters;

/**
 * GACAT-PERSISTENCE-1 — controls whether the Java seed may back the registry.
 * Production/staging: database only (no silent seed fallback).
 * Unit tests without Spring: seed-backed constructors remain available.
 */
public final class GacatCatalogueAuthority {

    public static final String AUTHORITY_DATABASE = "DATABASE";
    public static final String AUTHORITY_JAVA_SEED_TEST_ONLY = "JAVA_SEED_TEST_ONLY";
    public static final String AUTHORITY_UNLOADED = "UNLOADED";

    private static volatile boolean requireDatabase = false;
    private static volatile boolean seedFallbackAllowed = true;
    private static volatile String activeAuthority = AUTHORITY_UNLOADED;

    private GacatCatalogueAuthority() {}

    public static void configure(boolean requireDb, boolean allowSeedFallback) {
        requireDatabase = requireDb;
        seedFallbackAllowed = allowSeedFallback;
    }

    public static boolean requireDatabase() {
        return requireDatabase;
    }

    public static boolean seedFallbackAllowed() {
        return seedFallbackAllowed && !requireDatabase;
    }

    public static void markAuthority(String authority) {
        activeAuthority = authority == null ? AUTHORITY_UNLOADED : authority;
    }

    public static String activeAuthority() {
        return activeAuthority;
    }

    public static boolean isDatabaseAuthority() {
        return AUTHORITY_DATABASE.equals(activeAuthority);
    }
}
