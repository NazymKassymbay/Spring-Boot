package com.example.shop.mapper;

import org.springframework.stereotype.Component;

import com.example.shop.domain.Category;
import com.example.shop.web.dto.CategoryRequest;
import com.example.shop.web.dto.CategoryResponse;

@Component
public class CategoryMapper {

    public Category toDomain(CategoryRequest request) {
        return new Category(request.name().trim(), request.description());
    }

    public void updateDomain(Category category, CategoryRequest request) {
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
