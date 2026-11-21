package com.example.shop.order.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.shop.common.web.PageResponse;
import com.example.shop.order.app.OrderService;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderStatus;

/**
 * Web layer (driving adapter, lecture weeks 6-7, slide 26): HTTP in, use case call, DTO out.
 * No business rules, no repositories, no entities.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderApiMapper mapper;

    public OrderController(OrderService orderService, OrderApiMapper mapper) {
        this.orderService = orderService;
        this.mapper = mapper;
    }

    // GET /api/v1/orders?status=NEW&page=0&size=10 — newest orders first unless sort is given
    @GetMapping
    public PageResponse<OrderResponse> findAll(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(orderService.findAll(status, pageable).map(mapper::toResponse));
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return mapper.toResponse(orderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Order created = orderService.create(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    // Pay, ship, deliver or cancel an order; illegal jumps return 409
    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return mapper.toResponse(orderService.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orderService.delete(id);
    }
}
