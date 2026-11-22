package com.example.shop.order.web;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
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

import com.example.shop.common.web.ApiAdminOnly;
import com.example.shop.common.web.ApiSignedIn;
import com.example.shop.common.web.ErrorResponse;
import com.example.shop.common.web.PageResponse;
import com.example.shop.order.app.OrderService;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderStatus;

/**
 * Web layer (driving adapter, lecture weeks 6-7, slide 26): HTTP in, use case call, DTO out.
 * No business rules, no repositories, no entities.
 *
 * <p>SIS 3 (lecture week 10, slides 9-10): every operation documents its meaning and the error statuses that
 * really happen on it, each with the shared {@link ErrorResponse} body. Placing an order also publishes the
 * {@code OrderPlaced} event to Kafka after commit (lecture week 11).
 */
@Tag(name = "Orders", description = "Customer orders and their status life cycle")
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
    @Operation(summary = "List orders, newest first, optionally of one status")
    @ApiResponse(responseCode = "200", description = "One page of orders")
    @ApiResponse(responseCode = "400", description = "Unknown status, unknown sort field or a malformed parameter",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @GetMapping
    public PageResponse<OrderResponse> findAll(
            @Parameter(description = "Only orders in this status", example = "NEW")
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(orderService.findAll(status, pageable).map(mapper::toResponse));
    }

    @Operation(summary = "Get one order by id")
    @ApiResponse(responseCode = "200", description = "The order with its lines")
    @ApiResponse(responseCode = "400", description = "The id is not a number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No order with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return mapper.toResponse(orderService.findById(id));
    }

    @Operation(summary = "Place an order: reserves stock for every line and publishes OrderPlaced")
    @ApiResponse(responseCode = "201", description = "Created in status NEW; the Location header points at it")
    @ApiResponse(responseCode = "400", description = "Invalid body; the violations list names each bad field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Not enough stock, unknown product or too many lines",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiSignedIn
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Order created = orderService.create(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    // Pay, ship, deliver or cancel an order; illegal jumps return 409
    @Operation(summary = "Move an order along NEW -> PAID -> SHIPPED -> DELIVERED, or cancel it")
    @ApiResponse(responseCode = "200", description = "The order in its new status")
    @ApiResponse(responseCode = "400", description = "Invalid body or unknown status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No order with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Illegal status transition, or a concurrent change",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return mapper.toResponse(orderService.changeStatus(id, request.status()));
    }

    @Operation(summary = "Delete a finished (CANCELLED or DELIVERED) order")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No order with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The order is still active",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orderService.delete(id);
    }
}
