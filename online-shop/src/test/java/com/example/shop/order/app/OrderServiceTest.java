package com.example.shop.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.shop.common.config.ShopProperties;
import com.example.shop.common.exception.BusinessRuleException;
import com.example.shop.common.exception.ConflictException;
import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.order.domain.InvalidOrderStatusException;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderLine;
import com.example.shop.order.domain.OrderRepository;
import com.example.shop.order.domain.OrderStatus;
import com.example.shop.product.domain.InsufficientStockException;

/**
 * Unit test of the application service with Mockito (week 8): the ports are mocks,
 * so we check what the service asks them to do. No Spring context and no database.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final OrderLine TWO_MACBOOKS = new OrderLine(1, "MacBook Air 13", new BigDecimal("649990.00"), 2);
    private static final OrderLine ONE_IPHONE = new OrderLine(3, "iPhone 16", new BigDecimal("459990.00"), 1);

    @Mock
    private OrderRepository orders;
    @Mock
    private OrderQueries queries;
    @Mock
    private Inventory inventory;

    private final ShopProperties properties = new ShopProperties();
    private OrderService service;

    @BeforeEach
    void setUp() {
        properties.setMaxItemsPerOrder(5);
        service = new OrderService(orders, queries, inventory, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CreateOrderCommand command(CreateOrderCommand.Line... lines) {
        return new CreateOrderCommand("Nazym", "nazym@example.com", "Almaty", List.of(lines));
    }

    private Order savedOrder(long id, OrderStatus status) {
        return Order.restore(id, "Nazym", "nazym@example.com", "Almaty", status,
                List.of(TWO_MACBOOKS, ONE_IPHONE), NOW, NOW, 0);
    }

    @Test
    void reservesEveryLineAndSavesTheOrder() {
        when(inventory.reserve(1, 2)).thenReturn(TWO_MACBOOKS);
        when(inventory.reserve(3, 1)).thenReturn(ONE_IPHONE);
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        service.create(command(new CreateOrderCommand.Line(1, 2), new CreateOrderCommand.Line(3, 1)));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getLines()).containsExactly(TWO_MACBOOKS, ONE_IPHONE);
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void notEnoughStockSavesNothing() {
        when(inventory.reserve(1, 2)).thenReturn(TWO_MACBOOKS);
        when(inventory.reserve(4, 1)).thenThrow(new InsufficientStockException(4L, "Samsung Galaxy S25", 1, 0));

        assertThatThrownBy(() -> service.create(
                command(new CreateOrderCommand.Line(1, 2), new CreateOrderCommand.Line(4, 1))))
                .isInstanceOf(InsufficientStockException.class);

        // The exception leaves the @Transactional method, so Spring rolls back the first reservation too
        verify(orders, never()).save(any());
    }

    @Test
    void tooManyLinesIsRejectedBeforeTouchingStock() {
        CreateOrderCommand.Line line = new CreateOrderCommand.Line(1, 1);

        assertThatThrownBy(() -> service.create(command(line, line, line, line, line, line)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most 5 lines");
        verify(inventory, never()).reserve(anyLong(), anyInt());
    }

    @Test
    void cancellingReturnsEveryLineToStock() {
        when(orders.findById(7)).thenReturn(Optional.of(savedOrder(7, OrderStatus.PAID)));
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        Order cancelled = service.changeStatus(7, OrderStatus.CANCELLED);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventory).release(1, 2);
        verify(inventory).release(3, 1);
    }

    @Test
    void payingDoesNotTouchStock() {
        when(orders.findById(7)).thenReturn(Optional.of(savedOrder(7, OrderStatus.NEW)));
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        service.changeStatus(7, OrderStatus.PAID);

        verify(inventory, never()).release(anyLong(), anyInt());
    }

    @Test
    void illegalJumpIsRejectedAndNothingIsSaved() {
        when(orders.findById(7)).thenReturn(Optional.of(savedOrder(7, OrderStatus.NEW)));

        assertThatThrownBy(() -> service.changeStatus(7, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStatusException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void activeOrderCannotBeDeleted() {
        when(orders.findById(7)).thenReturn(Optional.of(savedOrder(7, OrderStatus.NEW)));

        assertThatThrownBy(() -> service.delete(7)).isInstanceOf(ConflictException.class);
        verify(orders, never()).deleteById(anyLong());
    }

    @Test
    void deliveredOrderCanBeDeleted() {
        when(orders.findById(7)).thenReturn(Optional.of(savedOrder(7, OrderStatus.DELIVERED)));

        service.delete(7);

        verify(orders).deleteById(7);
    }

    @Test
    void unknownOrderIsNotFound() {
        when(orders.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99)).isInstanceOf(ResourceNotFoundException.class);
    }
}
