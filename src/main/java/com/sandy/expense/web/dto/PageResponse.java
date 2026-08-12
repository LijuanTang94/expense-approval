package com.sandy.expense.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable pagination envelope so the frontend doesn't depend on Spring's Page serialization. */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, java.util.function.Function<E, T> map) {
        return new PageResponse<>(
                page.getContent().stream().map(map).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
