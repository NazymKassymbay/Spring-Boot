package com.example.shop.repository;

import java.util.List;
import java.util.Optional;

import com.example.shop.domain.Product;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    Optional<Product> findBySkuIgnoreCase(String sku);

    long countByCategoryId(Long categoryId);

    void deleteById(Long id);
}
