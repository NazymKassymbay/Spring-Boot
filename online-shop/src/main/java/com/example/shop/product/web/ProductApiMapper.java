package com.example.shop.product.web;

import org.springframework.stereotype.Component;

import com.example.shop.common.config.ShopProperties;
import com.example.shop.product.app.ProductDetails;
import com.example.shop.product.domain.Product;

/**
 * API <-> domain mapper (lecture weeks 6-7, slide 28).
 * The request becomes use-case input, and the domain object becomes the response.
 * The API contract (ProductRequest, ProductResponse) does not change when the domain or the schema does.
 */
@Component
public class ProductApiMapper {

    private final ShopProperties shopProperties;

    public ProductApiMapper(ShopProperties shopProperties) {
        this.shopProperties = shopProperties;
    }

    public ProductDetails toDetails(ProductRequest request) {
        return new ProductDetails(
                request.sku(),
                request.name().trim(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.categoryId());
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                shopProperties.getCurrency(),
                product.getStockQuantity(),
                product.isInStock(),
                product.getCategory().id(),
                product.getCategory().name(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
