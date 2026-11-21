package com.example.shop.category;

// Port: categories need to know how many products use them, but products live in another context.
// The product context implements it (product.infra.JpaCategoryUsage), so the dependency
// points from product to category and never back.
public interface CategoryUsage {

    long countProducts(long categoryId);
}
