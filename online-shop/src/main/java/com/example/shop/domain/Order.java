package com.example.shop.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.shop.exception.InvalidOrderStatusException;

public class Order {

    private Long id;
    private String customerName;
    private String customerEmail;
    private String shippingAddress;
    private OrderStatus status = OrderStatus.NEW;
    private List<OrderItem> items = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public Order() {
    }

    public Order(String customerName, String customerEmail, String shippingAddress) {
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.shippingAddress = shippingAddress;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    // Moves the order along its life cycle, rejecting jumps such as NEW -> DELIVERED
    public void changeStatus(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new InvalidOrderStatusException(id, status, next);
        }
        status = next;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
