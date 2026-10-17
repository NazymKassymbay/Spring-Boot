package com.example.shop.product.app;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.shop.product.domain.Product;

// Read side: paged lists. Kept out of the domain port, because Page and Pageable are Spring types.
public interface ProductQueries {

    // One page of products; categoryId == null means all categories
    Page<Product> page(Long categoryId, Pageable pageable);
}
