package com.example.shop.common.web;

import org.springframework.data.domain.Pageable;

import com.example.shop.common.exception.BadRequestException;

// Small helper shared by the list use cases
public final class Paging {

    private Paging() {
    }

    // JPA takes the row offset (page * size) as an int. page=30000000&size=100 does not fit,
    // so we answer 400 instead of letting it fail later as a 500.
    public static void checkOffset(Pageable pageable) {
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new BadRequestException("page is too large");
        }
    }
}
