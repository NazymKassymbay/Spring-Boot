package com.example.shop.product.web;

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
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.shop.common.web.ApiAdminOnly;
import com.example.shop.common.web.ErrorResponse;
import com.example.shop.common.web.PageResponse;
import com.example.shop.product.app.ProductService;
import com.example.shop.product.domain.Product;

/**
 * Web layer (driving adapter, lecture weeks 6-7, slide 26): HTTP in, use case call, DTO out.
 * No business rules, no repositories, no entities.
 *
 * <p>SIS 3 (lecture week 10, slides 9-10): every operation documents its meaning and the error statuses that
 * really happen on it, each with the shared {@link ErrorResponse} body.
 */
@Tag(name = "Products", description = "Catalogue products with price and stock")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final ProductApiMapper mapper;

    public ProductController(ProductService productService, ProductApiMapper mapper) {
        this.productService = productService;
        this.mapper = mapper;
    }

    // GET /api/v1/products?categoryId=1&page=0&size=10&sort=price,desc
    // Spring builds the Pageable from page, size and sort. Pages start at 0;
    // size is capped at 100 by spring.data.web.pageable.max-page-size.
    // @ParameterObject shows page, size and sort as three query parameters in Swagger UI, not one object.
    @Operation(summary = "List products, paged and sorted, optionally of one category")
    @ApiResponse(responseCode = "200", description = "One page of products")
    @ApiResponse(responseCode = "400", description = "Unknown sort field or a malformed parameter",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<ProductResponse> findAll(
            @Parameter(description = "Only products of this category", example = "1")
            @RequestParam(required = false) Long categoryId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(productService.findAll(categoryId, pageable).map(mapper::toResponse));
    }

    @Operation(summary = "Get one product by id")
    @ApiResponse(responseCode = "200", description = "The product")
    @ApiResponse(responseCode = "400", description = "The id is not a number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No product with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return mapper.toResponse(productService.findById(id));
    }

    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new product")
    @ApiResponse(responseCode = "400", description = "Invalid body; the violations list names each bad field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Duplicate SKU or unknown category",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(mapper.toDetails(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @Operation(summary = "Replace a product's details")
    @ApiResponse(responseCode = "200", description = "The updated product")
    @ApiResponse(responseCode = "400", description = "Invalid body or id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No product with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Duplicate SKU, unknown category or a concurrent change",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return mapper.toResponse(productService.update(id, mapper.toDetails(request)));
    }

    // Partial update of one field: stock arrives or is written off
    @Operation(summary = "Add stock (positive delta) or write it off (negative delta)")
    @ApiResponse(responseCode = "200", description = "The product with its new stock")
    @ApiResponse(responseCode = "400", description = "Invalid body or id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No product with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Stock would fall below zero, or a concurrent change",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @PatchMapping("/{id}/stock")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return mapper.toResponse(productService.adjustStock(id, request.delta(), request.reason()));
    }

    @Operation(summary = "Delete a product that is in no active order")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No product with that id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The product is in a NEW or PAID order",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiAdminOnly
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
