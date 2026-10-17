package com.example.shop.product.app;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.common.exception.BusinessRuleException;
import com.example.shop.common.exception.ConflictException;
import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.common.web.Paging;
import com.example.shop.product.domain.CategoryRef;
import com.example.shop.product.domain.Product;
import com.example.shop.product.domain.ProductRepository;

/**
 * Application service (lecture weeks 6-7, slide 22): one method per use case.
 * It loads the aggregate, asks it to change, and saves it. The rules themselves live in Product.
 * It works only with ports, so a unit test can give it in-memory versions instead of the database.
 */
@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final ProductQueries queries;
    private final CategoryCatalog categories;
    private final ProductUsage usage;
    private final Clock clock;

    public ProductService(ProductRepository products,
                          ProductQueries queries,
                          CategoryCatalog categories,
                          ProductUsage usage,
                          Clock clock) {
        this.products = products;
        this.queries = queries;
        this.categories = categories;
        this.usage = usage;
        this.clock = clock;
    }

    // One page of products, optionally only one category. PostgreSQL does the sorting and paging.
    @Transactional(readOnly = true)
    public Page<Product> findAll(Long categoryId, Pageable pageable) {
        Paging.checkOffset(pageable);
        return queries.page(categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Product findById(long id) {
        return load(id);
    }

    public Product create(ProductDetails details) {
        CategoryRef category = category(details.categoryId());
        ensureSkuIsFree(details.sku(), null);

        Product product = Product.create(details.sku(), details.name(), details.description(),
                details.price(), details.stockQuantity(), category, Instant.now(clock));

        Product saved = products.save(product);
        log.info("Product created: id={}, sku={}", saved.getId(), saved.getSku());
        return saved;
    }

    public Product update(long id, ProductDetails details) {
        Product product = load(id);
        CategoryRef category = category(details.categoryId());
        ensureSkuIsFree(details.sku(), id);

        product.changeDetails(details.sku(), details.name(), details.description(), details.price(),
                details.stockQuantity(), category, Instant.now(clock));
        return products.save(product);
    }

    // Positive delta = goods arrived, negative delta = write-off
    public Product adjustStock(long id, int delta, String reason) {
        Product product = load(id);
        if (delta == 0) {
            throw new BadRequestException("delta must not be 0");
        }
        Instant now = Instant.now(clock);
        if (delta > 0) {
            product.release(delta, now);
        } else {
            product.reserve(-delta, now);   // throws InsufficientStockException -> 409 if stock would go below 0
        }
        log.info("Stock adjusted: product={}, delta={}, reason={}, now={}",
                id, delta, reason, product.getStockQuantity());
        return products.save(product);
    }

    public void delete(long id) {
        load(id);
        if (usage.isInActiveOrder(id)) {
            throw new ConflictException("Product " + id + " is part of an active order and cannot be deleted");
        }
        products.deleteById(id);
        log.info("Product deleted: id={}", id);
    }

    private Product load(long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private CategoryRef category(long categoryId) {
        return categories.find(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Category " + categoryId + " does not exist"));
    }

    private void ensureSkuIsFree(String sku, Long ownId) {
        products.findIdBySku(sku)
                .filter(existingId -> !existingId.equals(ownId))
                .ifPresent(existingId -> {
                    throw new ConflictException("Product with sku '" + sku + "' already exists");
                });
    }
}
