package com.example.shop.product.infra;

import org.springframework.stereotype.Component;

import com.example.shop.category.CategoryUsage;

// Answers the category context's question "how many products use this category?"
@Component
class JpaCategoryUsage implements CategoryUsage {

    private final SpringDataProducts products;

    JpaCategoryUsage(SpringDataProducts products) {
        this.products = products;
    }

    @Override
    public long countProducts(long categoryId) {
        return products.countByCategoryId(categoryId);
    }
}
