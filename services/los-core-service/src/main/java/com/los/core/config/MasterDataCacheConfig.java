package com.los.core.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableCaching
public class MasterDataCacheConfig {

    public static final String GEO_STATES = "geoStates";
    public static final String GEO_CITIES_BY_STATE = "geoCitiesByStateId";

    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(GEO_STATES, GEO_CITIES_BY_STATE);
    }
}
