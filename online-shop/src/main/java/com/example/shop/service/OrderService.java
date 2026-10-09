package com.example.shop.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shop.config.ShopProperties;
import com.example.shop.domain.Order;
import com.example.shop.domain.OrderItem;
import com.example.shop.domain.OrderStatus;
import com.example.shop.domain.Product;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ConflictException;
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

    // One page of orders, optionally only one status
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> findAll(OrderStatus status, Pageable pageable) {
        Paging.checkOffset(pageable);
        Page<Order> orders = (status == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return PageResponse.from(orders.map(orderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return orderMapper.toResponse(getOrder(id));
    }

    // Creates the order and takes stock for every line in ONE transaction.
    // If any line fails (unknown product, not enough stock), an exception is thrown,
    // the whole transaction is rolled back and nothing is reserved.
    public OrderResponse create(CreateOrderRequest request) {
        int maxItems = shopProperties.getMaxItemsPerOrder();
        if (request.items().size() > maxItems) {
            throw new BusinessRuleException("An order may contain at most " + maxItems
                    + " lines, got " + request.items().size());
        }

        Order order = new Order(request.customerName().trim(), request.customerEmail().trim(),
                request.shippingAddress().trim());
        Instant now = Instant.now(clock);

        for (OrderItemRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new BusinessRuleException("Product " + line.productId() + " does not exist"));
            product.reserve(line.quantity());   // throws InsufficientStockException -> rollback -> 409
            product.setUpdatedAt(now);
            order.addItem(new OrderItem(product.getId(), product.getName(), product.getPrice(), line.quantity()));
        }
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // cascade = ALL: saving the order also inserts its lines.
        // The changed products are updated at commit by dirty checking (with a @Version check).
        Order saved = orderRepository.save(order);
        log.info("Order created: id={}, items={}, total={}", saved.getId(), saved.getItems().size(),
                saved.getTotalAmount());
        return orderMapper.toResponse(saved);
    }

    public OrderResponse changeStatus(Long id, OrderStatus newStatus) {
        Order order = getOrder(id);
        OrderStatus previous = order.getStatus();
        order.changeStatus(newStatus);   // throws InvalidOrderStatusException -> 409 for jumps like NEW -> DELIVERED

        if (newStatus == OrderStatus.CANCELLED) {
            returnItemsToStock(order);
        }
        order.setUpdatedAt(Instant.now(clock));
        log.info("Order {} status: {} -> {}", id, previous, newStatus);
        return orderMapper.toResponse(order);
    }

    // Only finished orders (cancelled or delivered) can be removed from history
    public void delete(Long id) {
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("Order " + id + " is " + order.getStatus()
                    + "; only CANCELLED or DELIVERED orders can be deleted");
        }
        orderRepository.delete(order);   // cascade = ALL also deletes the lines
        log.info("Order deleted: id={}", id);
    }

    private Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    // The product may have been deleted in the meantime; then there is nothing to return
    private void returnItemsToStock(Order order) {
        Instant now = Instant.now(clock);
        for (OrderItem item : order.getItems()) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.release(item.getQuantity());
                product.setUpdatedAt(now);
            });
        }
    }
}
