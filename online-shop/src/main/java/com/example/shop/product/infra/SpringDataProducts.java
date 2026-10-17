package com.example.shop.product.infra;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The Spring Data repository from Practice 4, now an implementation detail of the adapters
 * (lecture weeks 6-7, slide 26). Only classes in product.infra use it.
 */
public interface SpringDataProducts extends JpaRepository<ProductEntity, Long> {

    // Derived queries: the method name is the query
    Optional<ProductEntity> findBySkuIgnoreCase(String sku);

    long countByCategoryId(Long categoryId);

    // where p.category.id = ?1 ... order by ... offset ... limit ... (+ a COUNT query for the Page)
    Page<ProductEntity> findByCategoryId(Long categoryId, Pageable pageable);

    // JPQL with join fetch: the product and its category come back in ONE select
    @Query("select p from ProductEntity p join fetch p.category where p.id = :id")
    Optional<ProductEntity> findByIdWithCategory(@Param("id") Long id);
}
