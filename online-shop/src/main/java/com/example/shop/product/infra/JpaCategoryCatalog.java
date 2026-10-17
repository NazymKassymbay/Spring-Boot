package com.example.shop.product.infra;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.shop.category.CategoryRepository;
import com.example.shop.product.app.CategoryCatalog;
import com.example.shop.product.domain.CategoryRef;

/**
 * Translates the category model (Category entity) into the product model (CategoryRef).
 * The only class of the product context that reads categories (like FacilitiesRoomCatalog in the lecture).
 */
@Component
class JpaCategoryCatalog implements CategoryCatalog {

    private final CategoryRepository categories;

    JpaCategoryCatalog(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    public Optional<CategoryRef> find(long categoryId) {
        return categories.findById(categoryId)
                .map(category -> new CategoryRef(category.getId(), category.getName()));
    }
}
