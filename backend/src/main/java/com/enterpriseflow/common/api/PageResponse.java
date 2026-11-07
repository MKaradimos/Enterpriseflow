package com.enterpriseflow.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable pagination contract. Spring's {@code Page} serializes internal details that change between
 * versions, so the API exposes this explicit shape instead.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return from(page, Function.identity());
    }
}
