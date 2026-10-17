package com.example.shop.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.example.shop.common.exception.ConflictException;

/**
 * Aggregate root of the order context (lecture weeks 6-7, slides 18 and 21).
 * Outside code changes an order only through these methods, so these rules always hold:
 * <ul>
 *   <li>an order has at least one line, and its lines never change after it is placed;</li>
 *   <li>the status moves only along NEW -> PAID -> SHIPPED -> DELIVERED, or to CANCELLED from NEW or PAID;</li>
 *   <li>only CANCELLED or DELIVERED orders can be deleted.</li>
 * </ul>
 * There are getters for reading and no setters. The table mapping is OrderEntity in order.infra.
 */
public final class Order {

    private final Long id;                   // null until the first save
    private final String customerName;
    private final String customerEmail;
    private final String shippingAddress;
    private OrderStatus status;
    private final List<OrderLine> lines;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;              // optimistic locking, carried through the mapper

    private Order(Long id, String customerName, String customerEmail, String shippingAddress,
                  OrderStatus status, List<OrderLine> lines, Instant createdAt, Instant updatedAt,
                  long version) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
        this.id = id;
        this.customerName = Objects.requireNonNull(customerName, "customerName");
        this.customerEmail = Objects.requireNonNull(customerEmail, "customerEmail");
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "shippingAddress");
        this.status = Objects.requireNonNull(status, "status");
        this.lines = List.copyOf(lines);     // immutable copy: nobody can add a line from outside
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    // Factory for a new order. Stock for the lines has already been reserved by the service.
    public static Order place(String customerName, String customerEmail, String shippingAddress,
                              List<OrderLine> lines, Instant now) {
        return new Order(null, customerName, customerEmail, shippingAddress, OrderStatus.NEW, lines,
                now, now, 0);
    }

    // For the persistence mapper only: rebuilds an order that was saved earlier
    public static Order restore(Long id, String customerName, String customerEmail, String shippingAddress,
                                OrderStatus status, List<OrderLine> lines, Instant createdAt,
                                Instant updatedAt, long version) {
        return new Order(id, customerName, customerEmail, shippingAddress, status, lines, createdAt,
                updatedAt, version);
    }

    // Moves the order along its life cycle, rejecting jumps such as NEW -> DELIVERED
    public void changeStatus(OrderStatus next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new InvalidOrderStatusException(id, status, next);
        }
        status = next;
        updatedAt = now;
    }

    // Only finished orders (cancelled or delivered) can be removed from history
    public void ensureCanBeDeleted() {
        if (status != OrderStatus.CANCELLED && status != OrderStatus.DELIVERED) {
            throw new ConflictException("Order " + id + " is " + status
                    + "; only CANCELLED or DELIVERED orders can be deleted");
        }
    }

    public BigDecimal getTotalAmount() {
        return lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalQuantity() {
        return lines.stream().mapToInt(OrderLine::quantity).sum();
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
