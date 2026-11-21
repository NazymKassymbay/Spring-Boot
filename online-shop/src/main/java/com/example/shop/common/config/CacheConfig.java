package com.example.shop.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Practice 5: turns on Spring's caching abstraction (lecture weeks 8-9, slide 26).
 *
 * <p>The backend and its bounds are configured in application.properties:
 * {@code spring.cache.type=caffeine} with a {@code maximumSize} and {@code expireAfterWrite},
 * so the cache is bounded in size and time instead of the default map that grows forever.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
