package com.example.shop.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.shop.domain.Category;
import com.example.shop.exception.ConflictException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.CategoryMapper;
import com.example.shop.repository.CategoryRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.web.dto.CategoryRequest;
import com.example.shop.web.dto.CategoryResponse;

@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           CategoryMapper categoryMapper,
                           Clock clock) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.categoryMapper = categoryMapper;
        this.clock = clock;
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public CategoryResponse findById(Long id) {
        return toResponse(getCategory(id));
    }

    public CategoryResponse create(CategoryRequest request) {
        ensureNameIsFree(request.name(), null);

        Category category = categoryMapper.toDomain(request);
        Instant now = Instant.now(clock);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);

        Category saved = categoryRepository.save(category);
        log.info("Category created: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        ensureNameIsFree(request.name(), id);

        categoryMapper.updateDomain(category, request);
        category.setUpdatedAt(Instant.now(clock));
        return toResponse(categoryRepository.save(category));
    }

    public void delete(Long id) {
        Category category = getCategory(id);
        long products = productRepository.countByCategoryId(id);
        if (products > 0) {
            throw new ConflictException("Category " + id + " still has " + products
                    + " product(s); move or delete them first");
        }
        categoryRepository.deleteById(category.getId());
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
        return categoryMapper.toResponse(category, productRepository.countByCategoryId(category.getId()));
    }
}
