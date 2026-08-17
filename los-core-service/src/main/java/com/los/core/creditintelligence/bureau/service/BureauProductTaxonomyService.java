package com.los.core.creditintelligence.bureau.service;

import com.los.core.creditintelligence.bureau.domain.BureauProductCategory;
import com.los.core.creditintelligence.bureau.domain.CiBureauProductMapping;
import com.los.core.creditintelligence.bureau.repository.CiBureauProductMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads provider product taxonomy mappings and resolves canonical category / secured / revolving.
 * Unknown products → UNKNOWN with secured=null (must not count as unsecured).
 */
@Service
@RequiredArgsConstructor
public class BureauProductTaxonomyService {

    public static final String EQUIFAX_TAXONOMY_V1 = "EQUIFAX_TAXONOMY_V1";

    private final CiBureauProductMappingRepository mappingRepository;

    private final ConcurrentHashMap<String, List<CiBureauProductMapping>> cache = new ConcurrentHashMap<>();

    public record TaxonomyResolution(
            BureauProductCategory category,
            Boolean secured,
            Boolean revolving,
            String mappingVersion,
            boolean known) {
    }

    public TaxonomyResolution resolve(String provider, String productCode, String productDesc) {
        return resolve(provider, productCode, productDesc, EQUIFAX_TAXONOMY_V1);
    }

    public TaxonomyResolution resolve(String provider, String productCode, String productDesc, String mappingVersion) {
        String providerCode = provider != null ? provider.toUpperCase(Locale.ROOT) : "EQUIFAX";
        String version = mappingVersion != null ? mappingVersion : EQUIFAX_TAXONOMY_V1;
        List<CiBureauProductMapping> mappings = loadMappings(providerCode, version);

        String code = normalize(productCode);
        if (code != null) {
            for (CiBureauProductMapping m : mappings) {
                if (m.getProviderProductCode() != null
                        && code.equalsIgnoreCase(m.getProviderProductCode().trim())) {
                    return toResolution(m, version);
                }
            }
        }

        String desc = normalize(productDesc);
        if (desc != null) {
            String descUpper = desc.toUpperCase(Locale.ROOT);
            for (CiBureauProductMapping m : mappings) {
                if (m.getProviderProductCode() == null
                        && m.getProviderProductDesc() != null) {
                    String md = normalize(m.getProviderProductDesc());
                    if (md != null && descUpper.equals(md.toUpperCase(Locale.ROOT))) {
                        return toResolution(m, version);
                    }
                }
            }
            // Contains match for free-text AccountType like "Consumer Loan"
            for (CiBureauProductMapping m : mappings) {
                String md = normalize(m.getProviderProductDesc());
                if (md != null) {
                    String mdUpper = md.toUpperCase(Locale.ROOT);
                    if (descUpper.contains(mdUpper) || mdUpper.contains(descUpper)) {
                        return toResolution(m, version);
                    }
                }
            }
        }

        return new TaxonomyResolution(BureauProductCategory.UNKNOWN, null, null, version, false);
    }

    /** Test/helper: seed cache without DB (unit tests). */
    public void seedCache(String provider, String version, List<CiBureauProductMapping> mappings) {
        cache.put(cacheKey(provider, version), new ArrayList<>(mappings));
    }

    public void clearCache() {
        cache.clear();
    }

    private List<CiBureauProductMapping> loadMappings(String provider, String version) {
        return cache.computeIfAbsent(cacheKey(provider, version), k ->
                new ArrayList<>(mappingRepository.findByProviderCodeAndMappingVersion(provider, version)));
    }

    private static String cacheKey(String provider, String version) {
        return provider + "|" + version;
    }

    private static TaxonomyResolution toResolution(CiBureauProductMapping m, String version) {
        BureauProductCategory cat;
        try {
            cat = BureauProductCategory.valueOf(m.getCanonicalCategory());
        } catch (Exception e) {
            cat = BureauProductCategory.UNKNOWN;
        }
        boolean known = cat != BureauProductCategory.UNKNOWN;
        return new TaxonomyResolution(cat, m.getSecured(), m.getRevolving(), version, known);
    }

    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim()
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-');
        t = t.replaceAll("\\s*-\\s*", "-");
        t = t.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }
}
