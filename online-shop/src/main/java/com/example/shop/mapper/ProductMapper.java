package com.example.shop.mapper;

import org.springframework.stereotype.Component;

import com.example.shop.config.ShopProperties;
import com.example.shop.domain.Category;
import com.example.shop.domain.Product;
import com.example.shop.web.dto.ProductRequest;
import com.example.shop.web.dto.ProductResponse;

@Component
public class ProductMapper {

    private final ShopProperties shopProperties;

    public ProductMapper(ShopProperties shopProperties) {
        this.shopProperties = shopProperties;
    }

    public Product toDomain(ProductRequest request) {
        return new Product(
                request.sku(),
                request.name().trim(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.categoryId());
    }

    public void updateDomain(Product product, ProductRequest request) {
        product.setSku(request.sku());
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setCategoryId(request.categoryId());
    }

    public ProductResponse toResponse(Product product, Category category) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                shopProperties.getCurrency(),
                product.getStockQuantity(),
                product.isInStock(),
                product.getCategoryId(),
                category != null ? category.getName() : null,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
