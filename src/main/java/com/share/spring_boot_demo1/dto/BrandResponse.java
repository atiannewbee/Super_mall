package com.share.spring_boot_demo1.dto;

/**
 * 品牌公开信息。
 */
public record BrandResponse(
        Long id,
        String code,
        String name,
        String logoUrl,
        String description
) {
}
