package com.example.shop.web.dto;

import java.util.List;

import org.springframework.data.domain.Page;

// Our own JSON shape for one page of results (lecture week 5, slide 27: "PagedModel or your own DTO").
// Spring Data's Page is not returned directly, because its JSON shape is not a stable API.
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
