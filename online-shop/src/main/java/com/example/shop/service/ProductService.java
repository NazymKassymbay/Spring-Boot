package com.example.shop.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    // Orders in these states still "hold" their products, so such products cannot be deleted
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = EnumSet.of(OrderStatus.NEW, OrderStatus.PAID);

    // Public sort names of the API -> entity properties
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "name", "name",
            "price", "price",
            "stock", "stockQuantity",
            "createdat", "createdAt");

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

    // Filtering, sorting and paging are done by PostgreSQL: only one page of rows is loaded
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductFilter filter, int page, int size, String sort) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }

        Specification<Product> spec = toSpecification(filter);
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        // JPA takes the row offset as an int; a page that starts beyond it is empty anyway
        Page<Product> result = pageable.getOffset() > Integer.MAX_VALUE
                ? new PageImpl<>(List.of(), pageable, productRepository.count(spec))
                : productRepository.findAll(spec, pageable);
        return PageResponse.of(result, productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(getProduct(id));
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

    // PUT replaces stockQuantity too, so the row is locked like in orders and stock adjustments
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProductForUpdate(id);
        Category category = getCategoryForProduct(request.categoryId());
        ensureSkuIsFree(request.sku(), id);

        productMapper.updateDomain(product, request, category);
        product.setUpdatedAt(Instant.now(clock));
        return productMapper.toResponse(productRepository.save(product));
    }

    // Stock is shared with OrderService; the row lock makes concurrent changes wait for each other
    public ProductResponse adjustStock(Long id, StockAdjustmentRequest request) {
        Product product = getProductForUpdate(id);
        int delta = request.delta();
        if (delta == 0) {
            throw new BadRequestException("delta must not be 0");
        }
        if (delta > 0) {
            product.release(delta);
        } else {
            int newStock = product.getStockQuantity() + delta;
            if (newStock < 0) {
                throw new BusinessRuleException("Stock of product " + id + " cannot go below 0 (current "
                        + product.getStockQuantity() + ", delta " + delta + ")");
            }
            product.setStockQuantity(newStock);
        }
        product.setUpdatedAt(Instant.now(clock));
        log.info("Stock adjusted: product={}, delta={}, reason={}, now={}",
                id, delta, request.reason(), product.getStockQuantity());
        return productMapper.toResponse(productRepository.save(product));
    }

    // The row lock makes a parallel order for this product finish first, so the check below sees it
    public void delete(Long id) {
        Product product = getProductForUpdate(id);
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

    private Product getProductForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
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

    // Builds the WHERE clause from the filters that are present; absent filters add nothing
    private Specification<Product> toSpecification(ProductFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String pattern = "%" + escapeLike(filter.query().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sku")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
            }
            if (filter.inStock() != null) {
                predicates.add(filter.inStock()
                        ? cb.greaterThan(root.get("stockQuantity"), 0)
                        : cb.equal(root.get("stockQuantity"), 0));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // "%" and "_" typed by the user are searched literally, not as LIKE wildcards
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // Accepts "field" or "field,asc|desc", same format as Spring Data's sort parameter.
    // id is always added last, so rows with equal values keep a stable order between pages.
    private Sort parseSort(String sort) {
        Sort byId = Sort.by("id");
        if (sort == null || sort.isBlank()) {
            return byId;
        }
        String[] parts = sort.split(",");
        String property = SORT_FIELDS.get(parts[0].trim().toLowerCase(Locale.ROOT));
        if (property == null) {
            throw new BadRequestException("Unknown sort field '" + parts[0].trim()
                    + "'; allowed: id, name, price, stock, createdAt");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String value = parts[1].trim().toLowerCase(Locale.ROOT);
            if (value.equals("desc")) {
                direction = Sort.Direction.DESC;
            } else if (!value.equals("asc")) {
                throw new BadRequestException("Sort direction must be 'asc' or 'desc'");
            }
        }
        Sort.Order order = new Sort.Order(direction, property);
        // Names are compared case-insensitively, as before
        if (property.equals("name")) {
            order = order.ignoreCase();
        }
        return property.equals("id") ? Sort.by(order) : Sort.by(order).and(byId);
    }
}
