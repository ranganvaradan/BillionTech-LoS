package com.los.core.creditintelligence.policystudio.sourceintegration;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.GacatSourceFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Equifax Bureau Retail source-card membership.
 * <p>
 * Bureau Retail GACAT is a shared retail-bureau family. The live Equifax card must
 * count only Equifax-capable RAW IDs plus Bureau Retail DERIVED parameters.
 * {@code bureau.reason_code} remains a catalogue parameter (other-provider Score/ReasonCode)
 * but must not count against Equifax readiness.
 */
public final class EquifaxRetailSourceCardUniverse {

    private EquifaxRetailSourceCardUniverse() {}

    public static boolean isEquifaxRetailFamily(String family) {
        return GacatSourceFamily.isBureauRetail(family);
    }

    public static boolean belongsOnEquifaxCard(CanonicalParameterDefinition def) {
        if (def == null || def.id() == null || def.id().isBlank()) {
            return false;
        }
        if (!isEquifaxRetailFamily(def.evaluatedFrom())) {
            return false;
        }
        if (CanonicalParameterDefinition.RAW.equalsIgnoreCase(def.type())) {
            return EquifaxRetailRawIds.ALL.contains(def.id());
        }
        return true;
    }

    public static List<CanonicalParameterDefinition> scope(List<CanonicalParameterDefinition> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        List<CanonicalParameterDefinition> out = new ArrayList<>();
        for (CanonicalParameterDefinition def : parameters) {
            if (belongsOnEquifaxCard(def)) {
                out.add(def);
            }
        }
        return List.copyOf(out);
    }

    public static List<CanonicalParameterDefinition> scopeIfEquifaxFamily(
            String family, List<CanonicalParameterDefinition> parameters) {
        if (!isEquifaxRetailFamily(family)) {
            return parameters == null ? List.of() : parameters;
        }
        return scope(parameters);
    }

    public static List<CanonicalParameterDefinition> fromRegistry(Iterable<CanonicalParameterDefinition> all) {
        if (all == null) {
            return List.of();
        }
        List<CanonicalParameterDefinition> out = new ArrayList<>();
        for (CanonicalParameterDefinition def : all) {
            if (belongsOnEquifaxCard(def)) {
                out.add(def);
            }
        }
        return List.copyOf(out);
    }

    public static boolean sameMembership(
            CanonicalParameterDefinition a, CanonicalParameterDefinition b) {
        return Objects.equals(a == null ? null : a.id(), b == null ? null : b.id());
    }
}
