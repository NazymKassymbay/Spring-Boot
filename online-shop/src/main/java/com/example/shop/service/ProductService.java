package com.example.shop.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    // Orders in these states still "hold" their products, so such products cannot be deleted
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = EnumSet.of(OrderStatus.NEW, OrderStatus.PAID);

    private static final Map<String, Comparator<Product>> SORT_FIELDS = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER),
            "price", Comparator.comparing(Product::getPrice),
            "stock", Comparator.comparing(Product::getStockQuantity),
            "createdat", Comparator.comparing(Product::getCreatedAt));

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

    public PageResponse<ProductResponse> search(ProductFilter filter, int page, int size, String sort) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }

        List<Product> matching = productRepository.findAll().stream()
                .filter(p -> filter.categoryId() == null || Objects.equals(p.getCategoryId(), filter.categoryId()))
                .filter(p -> matchesQuery(p, filter.query()))
                .filter(p -> filter.minPrice() == null || p.getPrice().compareTo(filter.minPrice()) >= 0)
                .filter(p -> filter.maxPrice() == null || p.getPrice().compareTo(filter.maxPrice()) <= 0)
                .filter(p -> filter.inStock() == null || p.isInStock() == filter.inStock())
                .sorted(parseSort(sort))
                .toList();

        return PageResponse.of(matching, page, size, this::toResponse);
    }

    public ProductResponse findById(Long id) {
        return toResponse(getProduct(id));
    }

    public ProductResponse create(ProductRequest request) {
        ensureCategoryExists(request.categoryId());
        ensureSkuIsFree(request.sku(), null);

        Product product = productMapper.toDomain(request);
        Instant now = Instant.now(clock);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product saved = productRepository.save(product);
        log.info("Product created: id={}, sku={}", saved.getId(), saved.getSku());
        return toResponse(saved);
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        ensureCategoryExists(request.categoryId());
        ensureSkuIsFree(request.sku(), id);

        productMapper.updateDomain(product, request);
        product.setUpdatedAt(Instant.now(clock));
        return toResponse(productRepository.save(product));
    }

    // Stock is shared with OrderService, so changes are serialised on the repository
    public ProductResponse adjustStock(Long id, StockAdjustmentRequest request) {
        synchronized (productRepository) {
            Product product = getProduct(id);
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
            return toResponse(productRepository.save(product));
        }
    }

    public void delete(Long id) {
        getProduct(id);
        boolean usedInActiveOrder = orderRepository.findAll().stream()
                .filter(order -> ACTIVE_ORDER_STATUSES.contains(order.getStatus()))
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> item.getProductId().equals(id));
        if (usedInActiveOrder) {
            throw new ConflictException("Product " + id + " is part of an active order and cannot be deleted");
        }
        productRepository.deleteById(id);
        log.info("Product deleted: id={}", id);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private void ensureCategoryExists(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessRuleException("Category " + categoryId + " does not exist");
        }
    }

    private void ensureSkuIsFree(String sku, Long ownId) {
        productRepository.findBySkuIgnoreCase(sku)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("Product with sku '" + sku + "' already exists");
                });
    }

    private boolean matchesQuery(Product product, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return product.getName().toLowerCase(Locale.ROOT).contains(q)
                || product.getSku().toLowerCase(Locale.ROOT).contains(q)
                || (product.getDescription() != null && product.getDescription().toLowerCase(Locale.ROOT).contains(q));
    }

    // Accepts "field" or "field,asc|desc", same format as Spring Data's sort parameter
    private Comparator<Product> parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_FIELDS.get("id");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim().toLowerCase(Locale.ROOT);
        Comparator<Product> comparator = SORT_FIELDS.get(field);
        if (comparator == null) {
            throw new BadRequestException("Unknown sort field '" + parts[0].trim()
                    + "'; allowed: id, name, price, stock, createdAt");
        }
        if (parts.length > 1) {
            String direction = parts[1].trim().toLowerCase(Locale.ROOT);
            if (direction.equals("desc")) {
                return comparator.reversed();
            }
            if (!direction.equals("asc")) {
                throw new BadRequestException("Sort direction must be 'asc' or 'desc'");
            }
        }
        return comparator;
    }

    private ProductResponse toResponse(Product product) {
        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        return productMapper.toResponse(product, category);
    }
}
