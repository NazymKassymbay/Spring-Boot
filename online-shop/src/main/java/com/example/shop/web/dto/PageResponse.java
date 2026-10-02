package com.example.shop.web.dto;

import java.util.List;
import java.util.function.Function;

// Simple page wrapper; will map 1:1 onto Spring Data's Page once JPA is added
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <S, T> PageResponse<T> of(List<S> all, int page, int size, Function<S, T> mapper) {
        int total = all.size();
        // long arithmetic: page * size must not overflow int for huge page numbers
        int from = (int) Math.min((long) page * size, total);
        int to = (int) Math.min((long) from + size, total);
        List<T> content = all.subList(from, to).stream().map(mapper).toList();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }
}
