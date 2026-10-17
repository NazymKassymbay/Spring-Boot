package com.example.shop.order.app;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shop.common.config.ShopProperties;
import com.example.shop.common.exception.BusinessRuleException;
import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.common.web.Paging;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderLine;
import com.example.shop.order.domain.OrderRepository;
import com.example.shop.order.domain.OrderStatus;

/**
 * Application service (lecture weeks 6-7, slide 22): orchestrates the order use cases and owns the transaction.
 * It holds no business rules itself: they live in Order (status life cycle) and Product (stock).
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OrderQueries queries;
    private final Inventory inventory;
    private final ShopProperties shopProperties;
    private final Clock clock;

    public OrderService(OrderRepository orders,
                        OrderQueries queries,
                        Inventory inventory,
                        ShopProperties shopProperties,
                        Clock clock) {
        this.orders = orders;
        this.queries = queries;
        this.inventory = inventory;
        this.shopProperties = shopProperties;
        this.clock = clock;
    }

    // One page of orders, optionally only one status
    @Transactional(readOnly = true)
    public Page<Order> findAll(OrderStatus status, Pageable pageable) {
        Paging.checkOffset(pageable);
        return queries.page(status, pageable);
    }

    @Transactional(readOnly = true)
    public Order findById(long id) {
        return load(id);
    }

    // Places the order and takes stock for every line in ONE transaction.
    // If any line fails (unknown product, not enough stock), an exception is thrown,
    // the whole transaction is rolled back and nothing is reserved.
    public Order create(CreateOrderCommand command) {
        int maxItems = shopProperties.getMaxItemsPerOrder();
        if (command.lines().size() > maxItems) {
            throw new BusinessRuleException("An order may contain at most " + maxItems
                    + " lines, got " + command.lines().size());
        }

        List<OrderLine> lines = command.lines().stream()
                .map(line -> inventory.reserve(line.productId(), line.quantity()))
                .toList();
        Order order = Order.place(command.customerName(), command.customerEmail(),
                command.shippingAddress(), lines, Instant.now(clock));

        Order saved = orders.save(order);
        log.info("Order created: id={}, items={}, total={}", saved.getId(), saved.getLines().size(),
                saved.getTotalAmount());
        return saved;
    }

    public Order changeStatus(long id, OrderStatus newStatus) {
        Order order = load(id);
        OrderStatus previous = order.getStatus();
        order.changeStatus(newStatus, Instant.now(clock));   // 409 for jumps like NEW -> DELIVERED

        if (newStatus == OrderStatus.CANCELLED) {
            order.getLines().forEach(line -> inventory.release(line.productId(), line.quantity()));
        }
        log.info("Order {} status: {} -> {}", id, previous, newStatus);
        return orders.save(order);
    }

    public void delete(long id) {
        Order order = load(id);
        order.ensureCanBeDeleted();        // 409 unless CANCELLED or DELIVERED
        orders.deleteById(id);
        log.info("Order deleted: id={}", id);
    }

    private Order load(long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }
}
