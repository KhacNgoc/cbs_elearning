package com.bidv.coretraining.training.dto;

import java.util.List;
import java.util.function.Function;

public record PagedResult<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    public static <E, T> PagedResult<T> of(org.springframework.data.domain.Page<E> page, Function<E, T> mapper) {
        return new PagedResult<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
