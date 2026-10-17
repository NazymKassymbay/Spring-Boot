package com.example.shop.product.app;

import java.util.Optional;

import com.example.shop.product.domain.CategoryRef;

// Port to the category context (like RoomCatalog in the lecture sample).
// The product context describes a category in its own terms; product.infra.JpaCategoryCatalog translates.
public interface CategoryCatalog {

    Optional<CategoryRef> find(long categoryId);
}
