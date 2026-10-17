package com.example.shop.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.shop.common.exception.ConflictException;

// The Order aggregate guards its own life cycle. Plain JUnit: no Spring, no database, no mocks.
class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final OrderLine TWO_MACBOOKS = new OrderLine(1, "MacBook Air 13", new BigDecimal("649990.00"), 2);
    private static final OrderLine ONE_IPHONE = new OrderLine(3, "iPhone 16", new BigDecimal("459990.00"), 1);

    private Order newOrder() {
        return Order.place("Nazym", "nazym@example.com", "Almaty", List.of(TWO_MACBOOKS, ONE_IPHONE), NOW);
    }

    @Test
    void newOrderStartsAsNewAndSumsItsLines() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getTotalQuantity()).isEqualTo(3);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1759970.00");
    }

    @Test
    void orderNeedsAtLeastOneLine() {
        assertThatThrownBy(() -> Order.place("Nazym", "nazym@example.com", "Almaty", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linesCannotBeChangedFromOutside() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.getLines().add(ONE_IPHONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void followsTheLifeCycle() {
        Order order = newOrder();
        Instant later = NOW.plusSeconds(3600);

        order.changeStatus(OrderStatus.PAID, later);
        order.changeStatus(OrderStatus.SHIPPED, later);
        order.changeStatus(OrderStatus.DELIVERED, later);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void rejectsJumpingFromNewToDelivered() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.DELIVERED, NOW))
                .isInstanceOf(InvalidOrderStatusException.class)
                .hasMessageContaining("cannot move from NEW to DELIVERED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void onlyFinishedOrdersCanBeDeleted() {
        Order order = newOrder();
        assertThatThrownBy(order::ensureCanBeDeleted).isInstanceOf(ConflictException.class);

        order.changeStatus(OrderStatus.CANCELLED, NOW);
        order.ensureCanBeDeleted();   // no exception
    }

    @Test
    void orderLinesAreEqualByValue() {
        OrderLine copy = new OrderLine(1, "MacBook Air 13", new BigDecimal("649990.00"), 2);

        assertThat(copy).isEqualTo(TWO_MACBOOKS);
        assertThat(copy.lineTotal()).isEqualByComparingTo("1299980.00");
    }

    @Test
    void orderLineRejectsZeroQuantity() {
        assertThatThrownBy(() -> new OrderLine(1, "MacBook", BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
