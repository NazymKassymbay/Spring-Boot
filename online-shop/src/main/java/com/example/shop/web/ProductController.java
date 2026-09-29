package com.example.shop.web;

import java.math.BigDecimal;
import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

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

import com.example.shop.service.ProductFilter;
import com.example.shop.service.ProductService;
import com.example.shop.web.dto.PageResponse;
import com.example.shop.web.dto.ProductRequest;
import com.example.shop.web.dto.ProductResponse;
import com.example.shop.web.dto.StockAdjustmentRequest;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET /api/v1/products?categoryId=1&q=phone&minPrice=100&maxPrice=500&inStock=true&page=0&size=10&sort=price,desc
    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(name = "q", required = false) @Size(max = 100, message = "q must be at most 100 characters") String query,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "minPrice must be 0 or more") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "maxPrice must be 0 or more") BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or more") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be 1-100") @Max(value = 100, message = "size must be 1-100") int size,
            @RequestParam(required = false) String sort) {
        ProductFilter filter = new ProductFilter(categoryId, query, minPrice, maxPrice, inStock);
        return productService.search(filter, page, size, sort);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    // Partial update of one field: stock arrives or is written off
    @PatchMapping("/{id}/stock")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return productService.adjustStock(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
