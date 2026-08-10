package com.los.encore.client.jar;

/**
 * Placeholder for legacy {@code com.sensei.encore:encoreWebService} (EncoreWebServiceFacade) operations
 * that were not HTTP-based in bl-core (freeze/activate account, processRepayment via facade, etc.).
 * <p>
 * Parity options: add the proprietary JAR as an optional dependency and implement this bridge, or
 * replace each call with Encore REST when specifications are available.
 */
public interface EncoreLegacyJarBridge {

    default boolean isAvailable() {
        return false;
    }
}
