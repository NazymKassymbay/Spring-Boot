package com.example.shop.product.infra;

import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.example.shop.category.Category;
import com.example.shop.category.CategoryRepository;
import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.product.app.ProductQueries;
import com.example.shop.product.domain.Product;
import com.example.shop.product.domain.ProductRepository;

/**
 * Adapter (lecture weeks 6-7, slide 26): implements the domain port and the read port
 * with Spring Data JPA and the mapper. The service never sees ProductEntity.
 */
@Repository
class JpaProductRepository implements ProductRepository, ProductQueries {

    private final SpringDataProducts jpa;
    private final CategoryRepository categories;
    private final ProductEntityMapper mapper;

    JpaProductRepository(SpringDataProducts jpa, CategoryRepository categories, ProductEntityMapper mapper) {
        this.jpa = jpa;
        this.categories = categories;
        this.mapper = mapper;
    }

    @Override
    public Optional<Product> findById(long id) {
        return jpa.findByIdWithCategory(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Long> findIdBySku(String sku) {
        return jpa.findBySkuIgnoreCase(sku).map(ProductEntity::getId);
    }

    @Override
    public Product save(Product product) {
        // getReferenceById does not run a SELECT: it only needs the id for the category_id column
        Category category = categories.getReferenceById(product.getCategory().id());

        if (product.getId() == null) {
            // New product: INSERT, PostgreSQL gives the id (IDENTITY)
            ProductEntity saved = jpa.save(mapper.toNewEntity(product, category));
            return mapper.toDomain(saved);
        }

        // Existing product: the entity is MANAGED, so dirty checking sends the UPDATE at commit
        ProductEntity entity = jpa.findById(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", product.getId()));
        if (entity.getVersion() != product.getVersion()) {
            // The domain copy was loaded before someone else changed the row
            throw new OptimisticLockingFailureException("Product " + product.getId() + " was changed by another request");
        }
        mapper.copyState(product, entity, category);
        return mapper.toDomain(entity);
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    @Override
    public Page<Product> page(Long categoryId, Pageable pageable) {
        Page<ProductEntity> page = (categoryId == null)
                ? jpa.findAll(pageable)
                : jpa.findByCategoryId(categoryId, pageable);
        // Mapping reads the lazy categories; default_batch_fetch_size loads them with one IN (...) query
        return page.map(mapper::toDomain);
    }
}
