package com.example.shop.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shop.domain.Category;
import com.example.shop.domain.OrderStatus;
import com.example.shop.domain.Product;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ConflictException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.ProductMapper;
import com.example.shop.repository.CategoryRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.web.dto.PageResponse;
import com.example.shop.web.dto.ProductRequest;
import com.example.shop.web.dto.ProductResponse;
import com.example.shop.web.dto.StockAdjustmentRequest;

// Every public method runs in a transaction (class-level @Transactional); reads are readOnly
@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    // Orders in these states still "hold" their products, so such products cannot be deleted
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = Set.of(OrderStatus.NEW, OrderStatus.PAID);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final ProductMapper productMapper;
    private final Clock clock;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          OrderRepository orderRepository,
                          ProductMapper productMapper,
                          Clock clock) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.productMapper = productMapper;
        this.clock = clock;
    }

    // One page of products, optionally only one category. PostgreSQL does the sorting and paging.
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(Long categoryId, Pageable pageable) {
        Paging.checkOffset(pageable);
        Page<Product> products = (categoryId == null)
                ? productRepository.findAll(pageable)
                : productRepository.findByCategoryId(categoryId, pageable);
        // Mapping reads the lazy category, so it happens here, inside the transaction
        return PageResponse.from(products.map(productMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toResponse(product);
    }

    public ProductResponse create(ProductRequest request) {
        Category category = getCategoryForProduct(request.categoryId());
        ensureSkuIsFree(request.sku(), null);

        Product product = productMapper.toDomain(request, category);
        Instant now = Instant.now(clock);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product saved = productRepository.save(product);
        log.info("Product created: id={}, sku={}", saved.getId(), saved.getSku());
        return productMapper.toResponse(saved);
    }

    // No save() call is needed: the product is MANAGED, so Hibernate finds the changes
    // at commit (dirty checking) and sends the UPDATE itself
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        Category category = getCategoryForProduct(request.categoryId());
        ensureSkuIsFree(request.sku(), id);

        productMapper.updateDomain(product, request, category);
        product.setUpdatedAt(Instant.now(clock));
        return productMapper.toResponse(product);
    }

    // Positive delta = goods arrived, negative delta = write-off
    public ProductResponse adjustStock(Long id, StockAdjustmentRequest request) {
        Product product = getProduct(id);
        int delta = request.delta();
        if (delta == 0) {
            throw new BadRequestException("delta must not be 0");
        }
        if (delta > 0) {
            product.release(delta);
        } else {
            product.reserve(-delta);   // throws InsufficientStockException -> 409 if stock would go below 0
        }
        product.setUpdatedAt(Instant.now(clock));
        log.info("Stock adjusted: product={}, delta={}, reason={}, now={}",
                id, delta, request.reason(), product.getStockQuantity());
        return productMapper.toResponse(product);
    }

    public void delete(Long id) {
        Product product = getProduct(id);
        if (orderRepository.existsByStatusInAndProductId(ACTIVE_ORDER_STATUSES, id)) {
            throw new ConflictException("Product " + id + " is part of an active order and cannot be deleted");
        }
        productRepository.delete(product);
        log.info("Product deleted: id={}", id);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category getCategoryForProduct(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Category " + categoryId + " does not exist"));
    }

    private void ensureSkuIsFree(String sku, Long ownId) {
        productRepository.findBySkuIgnoreCase(sku)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("Product with sku '" + sku + "' already exists");
                });
    }
}
