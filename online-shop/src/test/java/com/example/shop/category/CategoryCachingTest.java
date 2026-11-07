package com.example.shop.category;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Practice 5: proves the caching works. The first list call hits the repository and is cached;
 * the second is served from the cache; a write evicts it so the next call hits the repository again.
 * No database and no Docker — a ConcurrentMap cache stands in for Caffeine, because the behaviour
 * under test is the @Cacheable / @CacheEvict advice, not the cache provider.
 */
@SpringJUnitConfig(CategoryCachingTest.Config.class)
class CategoryCachingTest {

    @EnableCaching
    @Configuration
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("categories");
        }

        @Bean
        CategoryMapper categoryMapper() {
            return new CategoryMapper();
        }

        @Bean
        CategoryService categoryService(CategoryRepository repository, CategoryUsage usage, CategoryMapper mapper) {
            return new CategoryService(repository, usage, mapper, Clock.systemUTC());
        }
    }

    @MockitoBean
    private CategoryRepository repository;

    @MockitoBean
    private CategoryUsage usage;

    @Autowired
    private CategoryService service;

    @Test
    void listIsServedFromCacheUntilAWriteEvictsIt() {
        given(repository.findAll(any(Sort.class))).willReturn(List.of(category(1L, "Books")));
        given(usage.countProducts(anyLong())).willReturn(0L);

        service.findAll();
        service.findAll();
        // The second call was a cache hit, so the repository was queried only once
        verify(repository, times(1)).findAll(any(Sort.class));

        // A write evicts the whole "categories" cache
        given(repository.findByNameIgnoreCase(any())).willReturn(Optional.empty());
        given(repository.save(any())).willAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        service.create(new CategoryRequest("Audio", null));

        service.findAll();
        // The cache was empty after the evict, so the repository is queried a second time
        verify(repository, times(2)).findAll(any(Sort.class));
    }

    private Category category(Long id, String name) {
        Category category = new Category(name, null);
        category.setId(id);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return category;
    }
}
