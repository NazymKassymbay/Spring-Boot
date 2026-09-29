package com.example.shop.repository;

import java.util.List;
import java.util.Optional;

import com.example.shop.domain.Category;

public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(Long id);

    List<Category> findAll();

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsById(Long id);

    void deleteById(Long id);
}
