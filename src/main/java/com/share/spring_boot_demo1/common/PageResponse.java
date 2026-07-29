package com.share.spring_boot_demo1.common;

import java.util.List;

/**
 * 统一分页响应；items 在构造时复制为不可变列表。
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public PageResponse {
        items = List.copyOf(items);
    }

    /**
     * 根据总元素数计算总页数并创建分页响应。
     */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }
}
