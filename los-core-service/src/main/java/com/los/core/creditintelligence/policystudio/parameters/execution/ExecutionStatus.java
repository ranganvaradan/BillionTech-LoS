package com.los.core.creditintelligence.policystudio.parameters.execution;

/**
 * Canonical parameter <b>execution</b> status vocabulary (Wave 1).
 *
 * <p>Not acquisition status, not rule acceptance, not certification, not authoring disposition.
 *
 * <ul>
 *   <li>{@link #VALUE_AVAILABLE} — exact canonical value exists (incl. 0 / false / empty valid collection)</li>
 *   <li>{@link #DATA_NOT_AVAILABLE} — producer capable; required source/fact absent</li>
 *   <li>{@link #DEPENDENCY_NOT_AVAILABLE} — producer capable; canonical dependency unresolved</li>
 *   <li>{@link #INPUT_REQUIRED} — manual/config input required</li>
 *   <li>{@link #NOT_EXECUTABLE} — no valid producer for this exact ID/mode</li>
 *   <li>{@link #CALCULATION_NOT_DEFINED} — derived expected but no valid executable definition</li>
 *   <li>{@link #ERROR} — unexpected failure during execution</li>
 * </ul>
 */
public enum ExecutionStatus {
    VALUE_AVAILABLE,
    INPUT_REQUIRED,
    DATA_NOT_AVAILABLE,
    CALCULATION_NOT_DEFINED,
    DEPENDENCY_NOT_AVAILABLE,
    NOT_EXECUTABLE,
    ERROR
}
