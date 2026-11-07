package com.example.shop.category;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shop.common.exception.ConflictException;
import com.example.shop.common.exception.ResourceNotFoundException;

@Service
@Transactional
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final CategoryUsage categoryUsage;
    private final CategoryMapper categoryMapper;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository,
                           CategoryUsage categoryUsage,
                           CategoryMapper categoryMapper,
                           Clock clock) {
        this.categoryRepository = categoryRepository;
        this.categoryUsage = categoryUsage;
        this.categoryMapper = categoryMapper;
        this.clock = clock;
    }

    // Practice 5: the category list is the shop's "menu" — read on almost every page, changed rarely.
    // It is cached under one key and the whole cache is dropped on any write (see the @CacheEvict methods),
    // so a client never sees a stale list. The value is an immutable record DTO, never a JPA entity (slide 26).
    @Cacheable("categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("id")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return toResponse(getCategory(id));
    }

    // Evict on every write so the cached list cannot go stale. allEntries clears the whole "categories"
    // cache; it is cheap because the cache holds only this one list.
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        ensureNameIsFree(request.name(), null);

        Category category = categoryMapper.toEntity(request);
        Instant now = Instant.now(clock);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);

        Category saved = categoryRepository.save(category);
        log.info("Category created: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    // No save() call: the category is MANAGED, so dirty checking writes the UPDATE at commit
    @CacheEvict(cacheNames = "categories", allEntries = true)
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        ensureNameIsFree(request.name(), id);

        categoryMapper.updateEntity(category, request);
        category.setUpdatedAt(Instant.now(clock));
        return toResponse(category);
    }

    @CacheEvict(cacheNames = "categories", allEntries = true)
    public void delete(Long id) {
        Category category = getCategory(id);
        long products = categoryUsage.countProducts(id);
        if (products > 0) {
            throw new ConflictException("Category " + id + " still has " + products
                    + " product(s); move or delete them first");
        }
        categoryRepository.delete(category);
        log.info("Category deleted: id={}", id);
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private void ensureNameIsFree(String name, Long ownId) {
        categoryRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("Category with name '" + name.trim() + "' already exists");
                });
    }

    private CategoryResponse toResponse(Category category) {
        return categoryMapper.toResponse(category, categoryUsage.countProducts(category.getId()));
    }
}
