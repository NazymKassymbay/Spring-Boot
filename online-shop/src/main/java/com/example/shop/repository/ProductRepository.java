package com.example.shop.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shop.domain.Product;

// Spring Data JPA generates the implementation at startup; we only declare the methods
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Derived queries: the method name is the query
    Optional<Product> findBySkuIgnoreCase(String sku);

    long countByCategoryId(Long categoryId);

    // where p.category.id = ?1 ... order by ... offset ... limit ... (+ a COUNT query for the Page)
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    // JPQL with join fetch: the product and its category come back in ONE select
    @Query("select p from Product p join fetch p.category where p.id = :id")
    Optional<Product> findByIdWithCategory(@Param("id") Long id);
}
