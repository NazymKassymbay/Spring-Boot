package com.example.shop.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.shop.service.CategoryService;
import com.example.shop.service.ProductService;
import com.example.shop.web.dto.CategoryRequest;
import com.example.shop.web.dto.CategoryResponse;
import com.example.shop.web.dto.ProductRequest;

// Fills the empty in-memory store with a small catalogue; only active when app.shop.seed-data=true (dev profile)
@Component
@ConditionalOnProperty(prefix = "app.shop", name = "seed-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final CategoryService categoryService;
    private final ProductService productService;

    public DemoDataLoader(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @Override
    public void run(ApplicationArguments args) {
        CategoryResponse laptops = categoryService.create(new CategoryRequest("Laptops", "Notebooks and ultrabooks"));
        CategoryResponse phones = categoryService.create(new CategoryRequest("Smartphones", "Android and iOS phones"));
        CategoryResponse books = categoryService.create(new CategoryRequest("Books", "Printed books"));

        productService.create(new ProductRequest("LAP-MBA-13", "MacBook Air 13", "Apple M3, 16 GB RAM, 512 GB SSD",
                new BigDecimal("649990.00"), 5, laptops.id()));
        productService.create(new ProductRequest("LAP-TP-X1", "Lenovo ThinkPad X1 Carbon", "Intel Core Ultra 7, 32 GB RAM",
                new BigDecimal("899990.00"), 3, laptops.id()));
        productService.create(new ProductRequest("PHN-IP-16", "iPhone 16", "128 GB, black",
                new BigDecimal("459990.00"), 10, phones.id()));
        productService.create(new ProductRequest("PHN-SGS-25", "Samsung Galaxy S25", "256 GB, silver",
                new BigDecimal("419990.00"), 0, phones.id()));
        productService.create(new ProductRequest("BK-SIA-5", "Spring in Action, 5th ed.", "Craig Walls, Manning",
                new BigDecimal("18500.00"), 25, books.id()));

        log.info("Demo data loaded: 3 categories, 5 products");
    }
}
