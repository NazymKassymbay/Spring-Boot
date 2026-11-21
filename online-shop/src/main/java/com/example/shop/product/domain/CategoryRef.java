package com.example.shop.product.domain;

import java.util.Objects;

// Value object: what a product needs to know about its category. Immutable and equal by value.
// The product refers to the category by id; the name is carried only so responses can show it.
public record CategoryRef(long id, String name) {

    public CategoryRef {
        Objects.requireNonNull(name, "name");
    }
}
