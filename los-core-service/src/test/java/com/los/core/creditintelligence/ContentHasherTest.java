package com.los.core.creditintelligence;

import com.los.core.creditintelligence.support.ContentHasher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentHasherTest {

    private final ContentHasher hasher = new ContentHasher();

    @Test
    void hashFacts_isIndependentOfInsertionOrder() {
        var a = List.of(
                new ContentHasher.FactHashInput("compat.BUREAU_SCORE", Map.of("v", 720), "VERIFIED"),
                new ContentHasher.FactHashInput("compat.MONTHLY_INCOME", Map.of("v", "80000"), "DEFAULTED"));
        var b = List.of(
                new ContentHasher.FactHashInput("compat.MONTHLY_INCOME", Map.of("v", "80000"), "DEFAULTED"),
                new ContentHasher.FactHashInput("compat.BUREAU_SCORE", Map.of("v", 720), "VERIFIED"));
        assertEquals(hasher.hashFacts(a), hasher.hashFacts(b));
    }

    @Test
    void hashMap_ordersKeysDeterministically() {
        String h1 = hasher.hashMap(Map.of("b", 2, "a", 1));
        String h2 = hasher.hashMap(Map.of("a", 1, "b", 2));
        assertEquals(h1, h2);
    }
}
