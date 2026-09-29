package com.example.shop.web;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

import com.example.shop.domain.OrderStatus;
import com.example.shop.service.OrderService;
import com.example.shop.web.dto.CreateOrderRequest;
import com.example.shop.web.dto.OrderResponse;
import com.example.shop.web.dto.OrderStatusUpdateRequest;
import com.example.shop.web.dto.PageResponse;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Newest orders first; optional filters by status and customer email
    @GetMapping
    public PageResponse<OrderResponse> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @Email(message = "customerEmail must be a valid email") String customerEmail,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or more") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be 1-100") @Max(value = 100, message = "size must be 1-100") int size) {
        return orderService.search(status, customerEmail, page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Pay, ship, deliver or cancel an order; illegal jumps return 409
    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orderService.delete(id);
    }
}
