package com.example.shop.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

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

import com.example.shop.config.ShopProperties;
import com.example.shop.domain.Order;
import com.example.shop.domain.OrderItem;
import com.example.shop.domain.OrderStatus;
import com.example.shop.domain.Product;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ConflictException;
import com.example.shop.exception.InsufficientStockException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.web.dto.CreateOrderRequest;
import com.example.shop.web.dto.OrderItemRequest;
import com.example.shop.web.dto.OrderResponse;
import com.example.shop.web.dto.PageResponse;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final ShopProperties shopProperties;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        OrderMapper orderMapper,
                        ShopProperties shopProperties,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.shopProperties = shopProperties;
        this.clock = clock;
    }

    // Newest orders first; filtering and paging are done by PostgreSQL
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> search(OrderStatus status, String customerEmail, int page, int size) {
        Specification<Order> spec = toSpecification(status, customerEmail);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        // JPA takes the row offset as an int; a page that starts beyond it is empty anyway
        Page<Order> result = pageable.getOffset() > Integer.MAX_VALUE
                ? new PageImpl<>(List.of(), pageable, orderRepository.count(spec))
                : orderRepository.findAll(spec, pageable);
        return PageResponse.of(result, orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return orderMapper.toResponse(getOrder(id));
    }

    // Creates an order and reserves stock for every line in one transaction:
    // if any line fails, the whole transaction is rolled back and nothing is reserved.
    public OrderResponse create(CreateOrderRequest request) {
        Map<Long, Integer> quantities = mergeQuantities(request.items());
        if (quantities.size() > shopProperties.getMaxItemsPerOrder()) {
            throw new BusinessRuleException("An order may contain at most " + shopProperties.getMaxItemsPerOrder()
                    + " different products, got " + quantities.size());
        }

        Map<Long, Product> products = lockProducts(quantities.keySet());
        Order order = new Order(request.customerName().trim(), request.customerEmail().trim(),
                request.shippingAddress().trim());
        Instant now = Instant.now(clock);
        quantities.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product == null) {
                throw new BusinessRuleException("Product " + productId + " does not exist");
            }
            if (product.getStockQuantity() < quantity) {
                throw new InsufficientStockException(productId, product.getName(), quantity,
                        product.getStockQuantity());
            }
            product.reserve(quantity);
            product.setUpdatedAt(now);
            order.addItem(new OrderItem(product.getId(), product.getName(), product.getPrice(), quantity));
        });
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        Order saved = orderRepository.save(order);
        log.info("Order created: id={}, items={}, total={}", saved.getId(), saved.getItems().size(),
                saved.getTotalAmount());
        return orderMapper.toResponse(saved);
    }

    public OrderResponse changeStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        OrderStatus previous = order.getStatus();
        order.changeStatus(newStatus);

        if (newStatus == OrderStatus.CANCELLED) {
            returnItemsToStock(order);
        }
        order.setUpdatedAt(Instant.now(clock));
        log.info("Order {} status: {} -> {}", id, previous, newStatus);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    // Only finished orders (cancelled or delivered) can be removed from history
    public void delete(Long id) {
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("Order " + id + " is " + order.getStatus()
                    + "; only CANCELLED or DELIVERED orders can be deleted");
        }
        orderRepository.delete(order);
        log.info("Order deleted: id={}", id);
    }

    private Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    // The same product listed twice becomes one line with the summed quantity
    private Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            result.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return result;
    }

    // Locks the product rows (SELECT ... FOR UPDATE) in ascending id order. A fixed order means
    // two transactions never wait for each other's locks in a circle, so they cannot deadlock.
    // Products that do not exist are simply missing from the result.
    private Map<Long, Product> lockProducts(Collection<Long> productIds) {
        Map<Long, Product> locked = new LinkedHashMap<>();
        for (Long productId : new TreeSet<>(productIds)) {
            productRepository.findByIdForUpdate(productId).ifPresent(product -> locked.put(productId, product));
        }
        return locked;
    }

    private void returnItemsToStock(Order order) {
        Instant now = Instant.now(clock);
        // The product may have been deleted after the order was finished; nothing to return then
        Map<Long, Product> products = lockProducts(order.getItems().stream().map(OrderItem::getProductId).toList());
        for (OrderItem item : order.getItems()) {
            Product product = products.get(item.getProductId());
            if (product != null) {
                product.release(item.getQuantity());
                product.setUpdatedAt(now);
            }
        }
    }

    private Specification<Order> toSpecification(OrderStatus status, String customerEmail) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (customerEmail != null) {
                predicates.add(cb.equal(cb.lower(root.get("customerEmail")),
                        customerEmail.trim().toLowerCase(Locale.ROOT)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
