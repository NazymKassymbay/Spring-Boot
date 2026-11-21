package com.example.shop.category;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA generates the implementation; method names below become SQL (derived queries)
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);
}
