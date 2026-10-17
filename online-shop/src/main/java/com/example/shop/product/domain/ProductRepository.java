package com.example.shop.product.domain;

import java.util.Optional;

/**
 * Port (lecture weeks 6-7, slide 26): the domain says what it needs, and
 * product.infra.JpaProductRepository provides it with Spring Data JPA.
 * Plain Java types only, so services can be tested with an in-memory version.
 */
public interface ProductRepository {

    Optional<Product> findById(long id);

    // Id of the product that already uses this SKU (letter case ignored), if any
    Optional<Long> findIdBySku(String sku);

    Product save(Product product);

    void deleteById(long id);
}
