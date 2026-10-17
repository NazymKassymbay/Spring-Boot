package com.example.shop.product.web;

import java.net.URI;

import jakarta.validation.Valid;

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

import com.example.shop.common.web.PageResponse;
import com.example.shop.product.app.ProductService;
import com.example.shop.product.domain.Product;

/**
 * Web layer (driving adapter, lecture weeks 6-7, slide 26): HTTP in, use case call, DTO out.
 * No business rules, no repositories, no entities.
 */
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
    @GetMapping
    public PageResponse<ProductResponse> findAll(
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(productService.findAll(categoryId, pageable).map(mapper::toResponse));
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return mapper.toResponse(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(mapper.toDetails(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return mapper.toResponse(productService.update(id, mapper.toDetails(request)));
    }

    // Partial update of one field: stock arrives or is written off
    @PatchMapping("/{id}/stock")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return mapper.toResponse(productService.adjustStock(id, request.delta(), request.reason()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
