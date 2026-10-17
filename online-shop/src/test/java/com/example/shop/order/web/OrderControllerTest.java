package com.example.shop.order.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.shop.common.config.AppConfig;
import com.example.shop.common.config.ShopProperties;
import com.example.shop.order.app.CreateOrderCommand;
import com.example.shop.order.app.OrderService;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderLine;
import com.example.shop.order.domain.OrderStatus;
import com.example.shop.product.domain.InsufficientStockException;

/**
 * Slice test of the order web layer (week 8): controller, JSON, validation and error handler only.
 * The service is a Mockito mock.
 */
@WebMvcTest(OrderController.class)
@Import({OrderApiMapper.class, AppConfig.class})
@EnableConfigurationProperties(ShopProperties.class)
class OrderControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orderService;

    private static final String ORDER_JSON = """
            {"customerName": " Nazym ", "customerEmail": "nazym@example.com", "shippingAddress": "Almaty",
             "items": [{"productId": 1, "quantity": 2}, {"productId": 3, "quantity": 1}]}""";

    @Test
    void createReturns201WithTotals() throws Exception {
        Order order = Order.restore(1L, "Nazym", "nazym@example.com", "Almaty", OrderStatus.NEW, List.of(
                new OrderLine(1, "MacBook Air 13", new BigDecimal("649990.00"), 2),
                new OrderLine(3, "iPhone 16", new BigDecimal("459990.00"), 1)), NOW, NOW, 0);
        when(orderService.create(any(CreateOrderCommand.class))).thenReturn(order);

        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(1299980.00))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.totalAmount").value(1759970.00))
                .andExpect(jsonPath("$.currency").value("KZT"));

        // The web mapper turned the request into a command: names trimmed, one line per item
        ArgumentCaptor<CreateOrderCommand> command = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService).create(command.capture());
        assertThat(command.getValue().customerName()).isEqualTo("Nazym");
        assertThat(command.getValue().lines()).containsExactly(
                new CreateOrderCommand.Line(1, 2), new CreateOrderCommand.Line(3, 1));
    }

    @Test
    void notEnoughStockIs409() throws Exception {
        when(orderService.create(any(CreateOrderCommand.class)))
                .thenThrow(new InsufficientStockException(4L, "Samsung Galaxy S25", 1, 0));

        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Not enough stock for product 4 (Samsung Galaxy S25): requested 1, available 0"));
    }

    @Test
    void emptyItemListIs400() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName": "Nazym", "customerEmail": "nazym@example.com",
                                 "shippingAddress": "Almaty", "items": []}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("items"));

        verify(orderService, never()).create(any());
    }

    @Test
    void unknownStatusValueIs400() throws Exception {
        mvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"FLYING\"}"))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).changeStatus(anyLong(), any());
    }
}
