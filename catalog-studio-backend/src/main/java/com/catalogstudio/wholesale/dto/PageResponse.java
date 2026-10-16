package com.catalogstudio.wholesale.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, List<T> mapped) {
        return new PageResponse<>(
                mapped, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public static <S, T> PageResponse<T> map(Page<S> page, Function<S, T> mapper) {
        return of(page, page.getContent().stream().map(mapper).toList());
    }
}