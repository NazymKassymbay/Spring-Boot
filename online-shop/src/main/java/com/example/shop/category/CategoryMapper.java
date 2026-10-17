package com.example.shop.category;

import org.springframework.stereotype.Component;

// DTO <-> entity mapper. A CRUD context may map the entity straight to the DTO (slide 28).
@Component
public class CategoryMapper {

    public Category toEntity(CategoryRequest request) {
        return new Category(request.name().trim(), request.description());
    }

    public void updateEntity(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(request.description());
    }

    public CategoryResponse toResponse(Category category, long productCount) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                productCount,
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
