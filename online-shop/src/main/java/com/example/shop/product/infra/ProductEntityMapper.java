package com.example.shop.product.infra;

import org.springframework.stereotype.Component;

import com.example.shop.category.Category;
import com.example.shop.product.domain.CategoryRef;
import com.example.shop.product.domain.Product;

/**
 * Domain <-> persistence mapper (lecture weeks 6-7, slide 28).
 * Written by hand: Product has no setters and a private constructor, so it is rebuilt through restore().
 */
@Component
public class ProductEntityMapper {

    // Reads the lazy category, so it must be called inside a transaction
    public Product toDomain(ProductEntity entity) {
        Category category = entity.getCategory();
        return Product.restore(
                entity.getId(),
                entity.getSku(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getStockQuantity(),
                new CategoryRef(category.getId(), category.getName()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    public ProductEntity toNewEntity(Product product, Category category) {
        ProductEntity entity = new ProductEntity(category);
        entity.setCreatedAt(product.getCreatedAt());
        copyState(product, entity, category);
        return entity;
    }

    // Copies every field that can change onto an entity that is already in the database
    public void copyState(Product product, ProductEntity entity, Category category) {
        entity.setSku(product.getSku());
        entity.setName(product.getName());
        entity.setDescription(product.getDescription());
        entity.setPrice(product.getPrice());
        entity.setStockQuantity(product.getStockQuantity());
        entity.setCategory(category);
        entity.setUpdatedAt(product.getUpdatedAt());
    }
}
