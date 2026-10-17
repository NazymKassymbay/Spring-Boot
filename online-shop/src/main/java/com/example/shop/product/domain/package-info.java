/**
 * Domain model of the product context (lecture weeks 6-7, slides 18-22).
 *
 * <p>Plain Java: no Spring, no JPA, no Jackson. The rules (stock never below zero, price above zero)
 * live in {@link com.example.shop.product.domain.Product} and are unit-tested with JUnit alone.
 * Persistence lives in {@code product.infra} and depends on this package, never the reverse (slide 26).
 */
package com.example.shop.product.domain;
